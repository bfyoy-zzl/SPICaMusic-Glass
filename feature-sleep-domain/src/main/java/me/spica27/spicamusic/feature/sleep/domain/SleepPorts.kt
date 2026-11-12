package me.spica27.spicamusic.feature.sleep.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** 助眠场景的持久化入口。 */
interface ISleepRepository {
    fun observeScenes(): Flow<List<SleepScene>>

    suspend fun getScene(sceneId: Long): SleepScene?

    /** 首次启动时把内置场景写入数据库；已有数据则直接返回。 */
    suspend fun ensureSeeded()

    /**
     * "自选"混音可用的单层素材。
     *
     * 只包含可循环的连续层：点状音效（雷声、鸟叫）在自选里没有场景可依附的底噪，
     * 单独按间隔播出来只会显得突兀。
     */
    fun customStems(): List<SleepStem>

    /**
     * "自选"上次挑中的层，按素材名持久化（顺序即选择顺序）。
     *
     * 用名字而不是自增 id 做键：自选素材来自代码里的目录，id 是临时负数，
     * 一旦目录顺序调整就会错位；名字和素材文件一一对应，才是稳定的。
     */
    fun observeCustomPick(): Flow<List<String>>

    suspend fun saveCustomPick(stems: List<String>)
}

/**
 * 多轨混音播放器。
 *
 * 与主播放器完全独立：主播放器是单一 Media3 会话，不存在"没有音乐时播环境音"的路径，
 * 所以助眠走自己的 ExoPlayer 实例集合，也不去抢音频焦点。
 */
interface ISleepPlayer {
    val state: StateFlow<SleepPlaybackState>

    val countdown: StateFlow<SleepCountdown?>

    /** 淡入并开始播放一个场景，重复调用会先释放旧音轨。 */
    suspend fun play(
        scene: SleepScene,
        fadeInMs: Long = SleepDefaults.FADE_IN_MS,
    )

    /** 渐弱后停止并释放所有音轨。 */
    suspend fun stop(fadeOutMs: Long = SleepDefaults.FADE_OUT_MS)

    fun setTrackVolume(
        stemId: Long,
        volume: Float,
    )

    fun setMasterVolume(volume: Float)

    /** 设置定时器，到期前自动渐弱。 */
    fun setTimer(totalMs: Long)

    fun cancelTimer()
}
