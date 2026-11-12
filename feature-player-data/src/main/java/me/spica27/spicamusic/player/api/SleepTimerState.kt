package me.spica27.spicamusic.player.api

/**
 * 当前睡眠定时器状态。
 *
 * [deadlineElapsedRealtimeMs] 使用 [android.os.SystemClock.elapsedRealtime] 的时钟，
 * 不受用户修改系统时间影响。UI 通常只需要展示 [remainingMs]；deadline 暴露出来
 * 是为了让状态消费者可以在需要时基于单调时钟重新计算精确剩余时间。
 *
 * @property fadeOutMs 到期前的渐弱时长。定时器总在最后这一段剩余时间里把播放音量
 *   从 1 线性降到 0，避免整夜陪伴的音乐在到点瞬间突然静音把人吵醒。
 *   为 0 表示不做渐弱，到点直接暂停。
 */
data class SleepTimerState(
    val durationMs: Long,
    val remainingMs: Long,
    val deadlineElapsedRealtimeMs: Long,
    val fadeOutMs: Long = 0L,
) {
    init {
        require(durationMs > 0) { "Sleep timer duration must be greater than zero" }
        require(remainingMs in 0..durationMs) {
            "Sleep timer remaining time must be between zero and duration"
        }
        require(fadeOutMs in 0..durationMs) {
            "Sleep timer fade-out must be between zero and duration"
        }
    }

    /** 是否已进入渐弱阶段。 */
    val isFading: Boolean
        get() = fadeOutMs > 0L && remainingMs <= fadeOutMs

    /**
     * 当前应使用的播放音量系数：渐弱阶段从 1 线性降到 0，其余时间恒为 1。
     *
     * 播放器直接把这个值写入音量，因此 [remainingMs] 归零时音量也正好归零，
     * 之后的暂停是静音的，不会有可听见的尾音。
     */
    val fadeLevel: Float
        get() = if (!isFading) 1f else (remainingMs.toFloat() / fadeOutMs).coerceIn(0f, 1f)

    /** 根据当前单调时钟生成刷新后的状态；结果会限制在 [0, durationMs]。 */
    fun updatedAt(nowElapsedRealtimeMs: Long): SleepTimerState =
        copy(
            remainingMs =
                (deadlineElapsedRealtimeMs - nowElapsedRealtimeMs)
                    .coerceIn(0L, durationMs),
        )
}
