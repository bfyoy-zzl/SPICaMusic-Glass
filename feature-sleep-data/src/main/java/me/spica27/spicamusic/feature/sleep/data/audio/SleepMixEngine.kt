package me.spica27.spicamusic.feature.sleep.data.audio

import android.content.Context
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.spica27.spicamusic.feature.sleep.domain.ISleepPlayer
import me.spica27.spicamusic.feature.sleep.domain.SleepCountdown
import me.spica27.spicamusic.feature.sleep.domain.SleepDefaults
import me.spica27.spicamusic.feature.sleep.domain.SleepLayerKind
import me.spica27.spicamusic.feature.sleep.domain.SleepPlaybackState
import me.spica27.spicamusic.feature.sleep.domain.SleepScene
import me.spica27.spicamusic.feature.sleep.domain.SleepStem
import me.spica27.spicamusic.feature.sleep.domain.SleepTrackState
import timber.log.Timber
import kotlin.math.min
import kotlin.random.Random

/**
 * 多轨助眠混音引擎。
 *
 * 与主播放器完全隔离：主播放器挂在 MediaSessionService 上，是单实例且只服务一首歌，
 * 没有"没有音乐时独立播环境音"的路径。这里每个音层一个 ExoPlayer。
 *
 * 两类音层：
 * - **连续层（LINE）**：一条长循环素材，`REPEAT_MODE_ONE` 一直播，构成底噪；
 * - **点状层（POINT）**：一组短片段，按随机间隔挑一个播一次，制造雷声、鸟叫这类偶发细节。
 *
 * 三个关键约束：
 * 1. `setAudioAttributes(attrs, handleAudioFocus = false)`——否则每层都会去抢音频焦点，
 *    多层互相打断，而且会跟主播放器打架。焦点仲裁交给调用方（UI 启动助眠前先暂停音乐）。
 * 2. 音量不直接跳变，一律走 50ms 步进的斜坡，避免 zipper noise。
 * 3. 所有 ExoPlayer 调用都固定在主线程，避免跨线程访问播放器内部状态。
 */
@OptIn(UnstableApi::class)
class SleepMixEngine(
    private val context: Context,
) : ISleepPlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(SleepPlaybackState())
    override val state: StateFlow<SleepPlaybackState> = _state.asStateFlow()

    private val _countdown = MutableStateFlow<SleepCountdown?>(null)
    override val countdown: StateFlow<SleepCountdown?> = _countdown.asStateFlow()

    /** 连续层：始终在播的循环轨，带用户滑杆。 */
    private class LineTrack(
        val stem: SleepStem,
        val player: ExoPlayer,
    )

    /** 点状层：按间隔随机播放短片段，不给单独滑杆。 */
    private class PointLayer(
        val stem: SleepStem,
        val player: ExoPlayer,
    ) {
        var job: Job? = null
    }

    private var tracks: List<LineTrack> = emptyList()
    private var pointLayers: List<PointLayer> = emptyList()

    /** stemId -> 用户滑杆音量（0..1），只对连续层有意义。 */
    private val trackVolumes = mutableMapOf<Long, Float>()

    /** 单轨音量的快速斜坡任务，防止滑杆拖动时抖出爆音。 */
    private val rampJobs = mutableMapOf<Long, Job>()

    private var masterVolume = SleepPlaybackState().masterVolume

    /** 定时器渐弱系数，与用户设置的总音量相乘。 */
    private var timerScale = 1f

    private var fadeJob: Job? = null

    /**
     * 停止流程（渐弱 + 释放）单独一个 job。
     *
     * 不能和 [fadeJob] 共用：用户拖总音量会 cancel [fadeJob]，若释放动作挂在那里，
     * 就会出现"渐弱被打断、音轨永远不释放"的僵尸播放。
     */
    private var stopJob: Job? = null
    private var timerJob: Job? = null

    override suspend fun play(
        scene: SleepScene,
        fadeInMs: Long,
    ) {
        withContext(Dispatchers.Main.immediate) {
            // 切换场景不应该丢掉已经设好的定时器：用户常常先选 30 分钟，
            // 再换几个场景挑声音。定时器循环读的是 tracks 字段，换轨后自动作用于新音轨。
            val timerActive = timerJob?.isActive == true
            fadeJob?.cancel()
            // 上一次停止可能还在渐弱：这里必须取消，否则它会在新音轨上继续把音量往 0 写。
            stopJob?.cancel()
            cancelRamps()
            releaseTracks()
            trackVolumes.clear()
            if (!timerActive) {
                timerScale = 1f
                _countdown.value = null
            }

            val lineStems = scene.stems.filter { it.layer == SleepLayerKind.LINE }.take(SleepDefaults.MAX_LINE_TRACKS)
            val pointStems = scene.stems.filter { it.layer == SleepLayerKind.POINT }

            tracks =
                lineStems.map { stem ->
                    trackVolumes[stem.id] = 1f
                    LineTrack(stem, createLoopPlayer(stem.assetPath))
                }
            pointLayers = pointStems.map { stem -> PointLayer(stem, createOneShotPlayer()) }

            tracks.forEach { it.player.play() }
            pointLayers.forEach { startPointLayer(it) }

            _state.value =
                SleepPlaybackState(
                    isPlaying = tracks.isNotEmpty() || pointLayers.isNotEmpty(),
                    sceneId = scene.id,
                    sceneName = scene.name,
                    sceneKey = scene.key,
                    masterVolume = masterVolume,
                    tracks =
                        lineStems.map {
                            SleepTrackState(
                                stemId = it.id,
                                name = it.name,
                                kind = it.kind,
                                volume = 1f,
                            )
                        },
                )

            if (tracks.isEmpty() && pointLayers.isEmpty()) {
                Timber.w("助眠场景 %s 没有可用音层", scene.key)
                return@withContext
            }
            // 正处在定时渐弱中时不做淡入：渐弱循环每 100ms 写一次目标音量，
            // 与淡入的插值会互相覆盖，导致音量抖动。直接沿用当前渐弱系数。
            // 淡入放到独立 job 里跑：play() 必须立刻返回，否则前台服务要等 4 秒才启动，
            // 这段时间没有任何保活机制。
            if (timerScale < 1f) {
                applyVolumes()
            } else {
                val targets = targetVolumeMap()
                fadeJob = scope.launch { rampAll(targets, fadeInMs) }
            }
        }
    }

    override suspend fun stop(fadeOutMs: Long) {
        withContext(Dispatchers.Main.immediate) {
            timerJob?.cancel()
            timerJob = null
            _countdown.value = null
            cancelRamps()
            // 点状层没有淡出概念，先停掉，避免渐弱期间突然蹦出一声鸟叫。
            stopPointLayers()
            // 上一段渐入/渐弱必须先停掉，否则它会继续往即将释放的 player 写音量。
            fadeJob?.cancel()
            fadeJob = null
            if (tracks.isEmpty()) {
                _state.value = SleepPlaybackState(masterVolume = masterVolume)
                return@withContext
            }
            val targets = tracks.associate { it.stem.id to 0f }
            // 渐弱与释放一起放到独立 job：停止要立即返回，不能让调用方等 1.2 秒，
            // 否则用户连点几下会排队等待。
            stopJob?.cancel()
            stopJob =
                scope.launch {
                    rampAll(targets, fadeOutMs)
                    finishInternal()
                }
        }
    }

    override fun setTrackVolume(
        stemId: Long,
        volume: Float,
    ) {
        val clamped = volume.coerceIn(0f, 1f)
        trackVolumes[stemId] = clamped
        _state.update { current ->
            current.copy(
                tracks = current.tracks.map { if (it.stemId == stemId) it.copy(volume = clamped) else it },
            )
        }
        quickRamp(stemId)
    }

    override fun setMasterVolume(volume: Float) {
        masterVolume = volume.coerceIn(0f, 1f)
        _state.update { it.copy(masterVolume = masterVolume) }
        if (tracks.isEmpty()) return
        // 正在停止时不要插手：否则会把渐弱到一半的音量又抬回去。
        if (stopJob?.isActive == true) return
        cancelRamps()
        val targets = targetVolumeMap()
        fadeJob?.cancel()
        fadeJob = scope.launch { rampAll(targets, MASTER_RAMP_MS) }
    }

    override fun setTimer(totalMs: Long) {
        if (totalMs <= 0L) {
            cancelTimer()
            return
        }
        // 没有音轨时不计时：否则会留下一个"没声音却在倒计时"的空转状态，
        // 到点后还会无谓地走一遍停止流程。
        if (tracks.isEmpty() && pointLayers.isEmpty()) {
            _countdown.value = null
            return
        }
        timerJob?.cancel()
        // 上一次定时可能正处在渐弱中：重置系数后必须把音量抬回来，
        // 否则新定时器要到自己的渐弱窗口（可能是半小时后）才会重新写音量。
        val wasFading = timerScale < 1f
        timerScale = 1f
        // 渐弱窗口最多 30s；定时很短时按四分之一收，避免一设定就开始降音量。
        val fadeOutMs = min(SleepDefaults.MAX_TIMER_FADE_MS, totalMs / 4)
        _countdown.value = SleepCountdown(totalMs, totalMs, fadeOutMs)
        if (wasFading) {
            cancelRamps()
            val targets = targetVolumeMap()
            fadeJob?.cancel()
            fadeJob = scope.launch { rampAll(targets, TIMER_CANCEL_RAMP_MS) }
        }
        timerJob =
            scope.launch {
                val startAt = SystemClock.elapsedRealtime()
                // 倒计时只在整秒变化时发布：UI 每秒重组一次即可，100ms 一次纯属浪费。
                var publishedSecond = -1L
                while (true) {
                    val remaining = totalMs - (SystemClock.elapsedRealtime() - startAt)
                    if (remaining <= 0L) {
                        _countdown.value = null
                        timerScale = 1f
                        // 此时音量已被下面的渐弱逻辑降到 0，直接释放即可。
                        finishInternal()
                        return@launch
                    }
                    val second = remaining / 1_000L
                    if (second != publishedSecond) {
                        publishedSecond = second
                        _countdown.value = SleepCountdown(totalMs, remaining, fadeOutMs)
                    }
                    if (fadeOutMs > 0L && remaining <= fadeOutMs) {
                        cancelRamps()
                        timerScale = (remaining.toFloat() / fadeOutMs).coerceIn(0f, 1f)
                        applyVolumes()
                    }
                    delay(TIMER_TICK_MS)
                }
            }
    }

    override fun cancelTimer() {
        timerJob?.cancel()
        timerJob = null
        _countdown.value = null
        if (timerScale >= 1f) return
        timerScale = 1f
        if (tracks.isEmpty()) return
        cancelRamps()
        val targets = targetVolumeMap()
        fadeJob?.cancel()
        fadeJob = scope.launch { rampAll(targets, TIMER_CANCEL_RAMP_MS) }
    }

    // ── 内部实现 ────────────────────────────────────────────────────────────

    private fun newPlayer(): ExoPlayer =
        ExoPlayer
            .Builder(context)
            // 交给 ExoPlayer 按播放状态持有/释放局部唤醒锁。助眠基本是熄屏整夜播放，
            // 没有这个，CPU 一休眠音频就会断续——前台服务只保证进程不被回收，不保证不休眠。
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
            .apply {
                setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    // 不接管音频焦点：各层各自抢焦点会互相打断，也会把正在播的歌掐掉。
                    false,
                )
                volume = 0f
            }

    /** 连续层：单曲无限循环。 */
    private fun createLoopPlayer(assetPath: String): ExoPlayer {
        val player = newPlayer()
        player.repeatMode = Player.REPEAT_MODE_ONE
        player.addListener(
            object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Timber.e(error, "助眠音层解码失败: %s", assetPath)
                }
            },
        )
        player.setMediaItem(MediaItem.fromUri(ASSET_SCHEME + assetPath))
        player.prepare()
        return player
    }

    /** 点状层：每次由 [startPointLayer] 换曲，不循环。 */
    private fun createOneShotPlayer(): ExoPlayer = newPlayer().apply { repeatMode = Player.REPEAT_MODE_OFF }

    /**
     * 点状层的随机触发循环。
     *
     * 间隔带 ±35% 的抖动，否则几层点缀会以固定节奏轮流响起，比不放还假。
     * 首次触发额外延迟半个到一个间隔，避免开场第一秒所有音效一起爆。
     */
    private fun startPointLayer(layer: PointLayer) {
        val interval = layer.stem.intervalMs.coerceAtLeast(MIN_POINT_INTERVAL_MS)
        layer.job =
            scope.launch {
                delay(jittered(interval, 0.5f))
                while (isActive) {
                    val clip = layer.stem.clipPaths.randomOrNull()
                    if (clip == null) {
                        Timber.w("点状音层 %s 没有可用片段", layer.stem.name)
                        return@launch
                    }
                    runCatching {
                        layer.player.setMediaItem(MediaItem.fromUri(ASSET_SCHEME + clip))
                        layer.player.volume = pointVolume(layer)
                        layer.player.play()
                    }.onFailure { Timber.w(it, "点状音层播放失败: %s", clip) }
                    delay(jittered(interval, 1f))
                }
            }
    }

    private fun jittered(
        intervalMs: Long,
        scale: Float,
    ): Long = (intervalMs * scale * Random.nextDouble(0.65, 1.35)).toLong().coerceAtLeast(MIN_POINT_INTERVAL_MS / 2)

    /** 实际音量：总音量 × 素材校准 × 用户滑杆 × 定时器渐弱。 */
    private fun effectiveVolume(track: LineTrack): Float =
        (
            masterVolume *
                track.stem.calibratedGain *
                (trackVolumes[track.stem.id] ?: 1f) *
                timerScale
        ).coerceIn(0f, 1f)

    /** 点状层没有滑杆，只受总音量、素材增益和定时渐弱影响。 */
    private fun pointVolume(layer: PointLayer): Float =
        (masterVolume * layer.stem.calibratedGain * timerScale).coerceIn(0f, 1f)

    private fun targetVolumeMap(): Map<Long, Float> = tracks.associate { it.stem.id to effectiveVolume(it) }

    /** 立刻把计算出的目标音量写到各层，不做插值。仅用于渐弱这种每 tick 都要写的场景。 */
    private fun applyVolumes() {
        tracks.forEach { track -> track.player.volume = effectiveVolume(track) }
        pointLayers.forEach { layer -> layer.player.volume = pointVolume(layer) }
    }

    private suspend fun rampAll(
        targets: Map<Long, Float>,
        durationMs: Long,
    ) {
        // 固定住当前这批音轨：淡化过程中如果用户切了场景，tracks 会被整体替换，
        // 继续按下标去写新音轨会算出 0（新音轨没有旧 stemId 的目标值），直接变成静音。
        val active = tracks.toList()
        if (active.isEmpty()) return
        val starts = active.associate { it.stem.id to it.player.volume }
        val steps = (durationMs / RAMP_STEP_MS).toInt().coerceAtLeast(1)
        for (step in 1..steps) {
            val progress = step.toFloat() / steps
            active.forEach { track ->
                val from = starts[track.stem.id] ?: 0f
                val to = targets[track.stem.id] ?: 0f
                track.player.volume = (from + (to - from) * progress).coerceIn(0f, 1f)
            }
            delay(RAMP_STEP_MS)
        }
    }

    private fun quickRamp(stemId: Long) {
        val track = tracks.firstOrNull { it.stem.id == stemId } ?: return
        val start = track.player.volume
        val target = effectiveVolume(track)
        rampJobs[stemId]?.cancel()
        rampJobs[stemId] =
            scope.launch {
                for (step in 1..QUICK_RAMP_STEPS) {
                    track.player.volume = (start + (target - start) * step / QUICK_RAMP_STEPS).coerceIn(0f, 1f)
                    delay(RAMP_STEP_MS)
                }
            }
    }

    private fun cancelRamps() {
        rampJobs.values.forEach { it.cancel() }
        rampJobs.clear()
    }

    /** 释放所有音层并复位状态；不负责渐弱。 */
    private fun finishInternal() {
        // 所有"播放结束"的路径都从这里走，定时器必须一并清掉：
        // 否则停止渐弱期间新设的定时器会残留一个指向空音轨的倒计时。
        timerJob?.cancel()
        timerJob = null
        _countdown.value = null
        timerScale = 1f
        releaseTracks()
        _state.value = SleepPlaybackState(masterVolume = masterVolume)
    }

    private fun stopPointLayers() {
        pointLayers.forEach { layer ->
            layer.job?.cancel()
            layer.job = null
            runCatching { layer.player.pause() }
        }
    }

    private fun releaseTracks() {
        stopPointLayers()
        pointLayers.forEach { layer ->
            runCatching {
                layer.player.stop()
                layer.player.release()
            }
        }
        pointLayers = emptyList()
        tracks.forEach { track ->
            runCatching {
                track.player.stop()
                track.player.release()
            }
        }
        tracks = emptyList()
    }

    private companion object {
        const val RAMP_STEP_MS = 50L
        const val TIMER_TICK_MS = 100L
        const val MASTER_RAMP_MS = 180L
        const val TIMER_CANCEL_RAMP_MS = 300L
        const val QUICK_RAMP_STEPS = 5

        /** 点状层的最小间隔，避免素材标错时变成机关枪。 */
        const val MIN_POINT_INTERVAL_MS = 4_000L

        /** assets 里的文件用 asset:/// 前缀交给 Media3 的 AssetDataSource。 */
        const val ASSET_SCHEME = "asset:///"
    }
}
