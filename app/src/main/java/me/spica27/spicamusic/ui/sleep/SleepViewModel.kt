package me.spica27.spicamusic.ui.sleep

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.spica27.spicamusic.R
import me.spica27.spicamusic.feature.player.domain.PlayerUseCases
import me.spica27.spicamusic.feature.sleep.domain.SleepCountdown
import me.spica27.spicamusic.feature.sleep.domain.SleepDefaults
import me.spica27.spicamusic.feature.sleep.domain.SleepPlaybackState
import me.spica27.spicamusic.feature.sleep.domain.SleepScene
import me.spica27.spicamusic.feature.sleep.domain.SleepStem
import me.spica27.spicamusic.feature.sleep.domain.SleepUseCases
import me.spica27.spicamusic.player.api.PlayerAction
import me.spica27.spicamusic.service.SleepMixService
import timber.log.Timber

class SleepViewModel(
    private val sleep: SleepUseCases,
    private val musicPlayer: PlayerUseCases,
    private val appContext: Context,
) : ViewModel() {
    val scenes: StateFlow<List<SleepScene>> =
        sleep
            .scenes()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS),
                initialValue = emptyList(),
            )

    val playback: StateFlow<SleepPlaybackState> = sleep.playback

    val countdown: StateFlow<SleepCountdown?> = sleep.countdown

    /** "自选"可挑的单层素材，顺序即展示顺序。 */
    val customStems: List<SleepStem> = sleep.customStems()

    /**
     * "自选"已挑中的层（素材 id，顺序即选择顺序）。
     *
     * 内存态是唯一真源：点一下立刻反映到界面，同时异步写库；
     * 首次进入时从库里补一次，之后不再回读，避免写库的回流把用户的连续点击覆盖掉。
     */
    private val _customPick = MutableStateFlow<List<Long>>(emptyList())
    val customPick: StateFlow<List<Long>> = _customPick.asStateFlow()

    /** 用户是否已经动过自选；动过就不再被首次加载的结果覆盖。 */
    private var customPickTouched = false

    init {
        viewModelScope.launch {
            runCatching { sleep.customPick().first() }
                .onSuccess { stems ->
                    if (!customPickTouched) _customPick.value = stems.map { it.id }
                }.onFailure { Timber.e(it, "读取自选混音配置失败") }
        }
    }

    /** 选中/取消一层；达到上限后继续点其它层不生效（界面已经置灰，这里再兜一次）。 */
    fun toggleCustomStem(stemId: Long) {
        val current = _customPick.value
        val next =
            when {
                stemId in current -> current - stemId
                current.size >= SleepDefaults.MAX_CUSTOM_TRACKS -> return
                else -> current + stemId
            }
        persistCustomPick(next)
    }

    fun clearCustomPick() {
        if (_customPick.value.isEmpty()) return
        persistCustomPick(emptyList())
    }

    fun playPickedCustom() {
        val stems =
            customStems
                .filter { it.id in _customPick.value }
                .sortedBy { stem -> _customPick.value.indexOf(stem.id) }
        playCustom(stems)
    }

    private fun persistCustomPick(ids: List<Long>) {
        customPickTouched = true
        _customPick.value = ids
        viewModelScope.launch {
            runCatching { sleep.saveCustomPick(ids) }
                .onFailure { Timber.e(it, "保存自选混音配置失败") }
        }
    }

    /**
     * 还没开始播放时选定的定时时长（分钟，0 表示关闭）。
     *
     * 允许"先选时长、再点场景"：选中后先存在这里，[play] 成功启动混音时才真正下发给引擎，
     * 下发后立即归零，之后以 [countdown] 为准，避免出现"计时已经结束但界面还显示选中的时长"。
     */
    private val _pendingTimerMinutes = MutableStateFlow(0)
    val pendingTimerMinutes: StateFlow<Int> = _pendingTimerMinutes.asStateFlow()

    private var playJob: Job? = null

    fun play(scene: SleepScene) {
        // 已经有一路在放时切场景：用短淡入，否则旧场景已停、新场景要 4 秒才起来，听感是"切了没声"。
        val fadeInMs = switchFadeInMs()
        launchPlayback { sleep.play(scene, fadeInMs) }
    }

    /** 点正在播放的卡片 = 停止，避免"图标是停止、行为却是重新淡入"的误触。 */
    fun toggleScene(scene: SleepScene) {
        val current = playback.value
        if (current.isPlaying && current.sceneId == scene.id) stop() else play(scene)
    }

    fun playCustom(stems: List<SleepStem>) {
        if (stems.isEmpty()) return
        val fadeInMs = switchFadeInMs()
        launchPlayback {
            sleep.playCustom(stems, appContext.getString(R.string.sleep_custom), fadeInMs)
        }
    }

    /** 从"没有声音"起播用长淡入，从"正在放"切过去用短淡入。 */
    private fun switchFadeInMs(): Long = if (playback.value.isPlaying) SleepDefaults.SWITCH_FADE_IN_MS else SleepDefaults.FADE_IN_MS

    fun stop() {
        playJob?.cancel()
        viewModelScope.launch {
            runCatching { sleep.stop() }
                .onFailure { Timber.e(it, "停止助眠场景失败") }
        }
    }

    fun setTrackVolume(
        stemId: Long,
        volume: Float,
    ) = sleep.setTrackVolume(stemId, volume)

    fun setMasterVolume(volume: Float) = sleep.setMasterVolume(volume)

    /**
     * [minutes] 为 0 表示关闭定时器。
     *
     * 播放中直接下发给引擎；未播放时只记下来，等 [launchPlayback] 启动混音时再生效——
     * 否则会出现"没声音却在倒计时"的空转状态。
     */
    fun setTimerMinutes(minutes: Int) {
        val normalized = minutes.coerceAtLeast(0)
        if (playback.value.isPlaying) {
            sleep.setTimerMinutes(normalized)
        } else {
            _pendingTimerMinutes.value = normalized
        }
    }

    fun clearPendingTimer() {
        _pendingTimerMinutes.value = 0
    }

    /**
     * 统一的起播流程：先让主播放器真正停下，再启动助眠混音。
     *
     * 主播放器走 MediaSessionService，助眠走自己的 ExoPlayer 集合，两者都不抢音频焦点，
     * 所以必须显式让位。`Pause` 是跨进程命令，调用后音乐不会立刻停，因此要等
     * [isPlaying] 真的变成 false 再起混音——否则开头的几秒两路会叠在一起。
     * 反向（音乐 → 助眠）由 `SleepMusicArbiter` 在应用层处理。
     */
    private fun launchPlayback(start: suspend () -> Unit) {
        playJob?.cancel()
        // 已经在放助眠时切换场景：音乐早就让过位了，没必要再走一遍"暂停 + 等它真停"，
        // 那一步最长要等 800ms，翻页切场景时会被听成"滑过去半天才有声"。
        val alreadyPlaying = playback.value.isPlaying
        playJob =
            viewModelScope.launch {
                runCatching {
                    if (!alreadyPlaying) {
                        musicPlayer.doAction(PlayerAction.Pause)
                        val paused =
                            withTimeoutOrNull(MUSIC_PAUSE_WAIT_MS) {
                                musicPlayer.isPlaying.first { !it }
                            }
                        if (paused == null) {
                            // 跨进程命令没落地（例如服务刚被系统重建）：仍然启动混音，
                            // 但记一条日志，方便排查"两路一起响"的现场。
                            Timber.w("主播放器未在 %dms 内暂停，助眠可能与其短暂重叠", MUSIC_PAUSE_WAIT_MS)
                        }
                    }
                    start()
                    SleepMixService.start(appContext)
                    val pending = _pendingTimerMinutes.value
                    if (pending > 0) {
                        _pendingTimerMinutes.value = 0
                        sleep.setTimerMinutes(pending)
                    }
                }.onFailure { Timber.e(it, "启动助眠混音失败") }
            }
    }

    private companion object {
        const val SUBSCRIBE_TIMEOUT_MS = 5_000L

        /** 等主播放器真正暂停的上限；正常情况 <100ms，超时也照常启动混音。 */
        const val MUSIC_PAUSE_WAIT_MS = 800L
    }
}
