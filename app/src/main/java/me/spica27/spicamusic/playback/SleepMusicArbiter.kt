package me.spica27.spicamusic.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.spica27.spicamusic.feature.sleep.domain.ISleepPlayer
import me.spica27.spicamusic.feature.sleep.domain.SleepDefaults
import me.spica27.spicamusic.player.api.IMusicPlayer
import timber.log.Timber

/**
 * 音乐与助眠混音的互斥仲裁。
 *
 * 两套播放器都不抢音频焦点（助眠引擎刻意用 `handleAudioFocus = false`，否则三轨会互相打断），
 * 所以系统不会替我们处理并发，必须自己让位。
 *
 * 方向一（助眠 → 音乐）在 `SleepViewModel.play` 里完成：点场景时先暂停音乐并等它真的停下。
 * 方向二（音乐 → 助眠）放在这里，因为音乐可以从通知栏、蓝牙耳机、车机等任何地方恢复播放，
 * 那些路径都不经过助眠页面，只能在应用进程里统一监听。
 */
class SleepMusicArbiter(
    private val music: IMusicPlayer,
    private val sleep: ISleepPlayer,
    private val scope: CoroutineScope,
) {
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job =
            scope.launch {
                // StateFlow 自身就去重，不需要再套 distinctUntilChanged（新版 Kotlin 会判为错误）。
                music.isPlaying.collect { playing ->
                    if (!playing) return@collect
                    if (!sleep.state.value.isPlaying) return@collect
                    Timber.i("音乐开始播放，停止助眠混音")
                    runCatching { sleep.stop(SleepDefaults.FADE_OUT_MS) }
                        .onFailure { Timber.w(it, "停止助眠混音失败") }
                }
            }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
