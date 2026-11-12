package me.spica27.spicamusic.feature.sleep.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import org.koin.dsl.module

/**
 * 助眠模块用例入口。
 *
 * 与项目里其他 `*UseCases` 不同，这里只暴露接口（[ISleepRepository] / [ISleepPlayer]），
 * 实现全部留在 data 层，app 只依赖模型和这个类。
 */
class SleepUseCases(
    private val repository: ISleepRepository,
    private val player: ISleepPlayer,
) {
    val playback: StateFlow<SleepPlaybackState> get() = player.state

    val countdown: StateFlow<SleepCountdown?> get() = player.countdown

    fun scenes(): Flow<List<SleepScene>> = repository.observeScenes()

    /** "自选"混音可用的单层素材。 */
    fun customStems(): List<SleepStem> = repository.customStems()

    /** "自选"上次挑中的层；素材目录调整后找不到名字的会被静默丢掉。 */
    fun customPick(): Flow<List<SleepStem>> =
        repository.observeCustomPick().map { names ->
            val all = customStems()
            names.mapNotNull { name -> all.firstOrNull { it.name == name } }
        }

    /**
     * 保存"自选"的层，按 [SleepStem.id] 传入。
     *
     * 每次都整体写入：自选最多 4 层，写整行比维护增删差异简单，也不会出现半更新的中间态。
     */
    suspend fun saveCustomPick(stemIds: List<Long>) {
        val all = customStems()
        val names = stemIds.mapNotNull { id -> all.firstOrNull { it.id == id }?.name }
        repository.saveCustomPick(names)
    }

    /** 首次启动写入内置场景。 */
    suspend fun ensureCatalog() = repository.ensureSeeded()

    /** [fadeInMs] 传短值用于播放中切换场景：切场景要跟手，首次起播才需要慢慢淡入。 */
    suspend fun play(
        scene: SleepScene,
        fadeInMs: Long = SleepDefaults.FADE_IN_MS,
    ) = player.play(scene, fadeInMs)

    /**
     * 播放一次临时的自选混音。
     *
     * 场景本身不落库（避免场景列表越来越长），但"挑了哪几层"会通过 [saveCustomPick] 记住，
     * 下次打开自选面板时默认还是这一套。
     */
    suspend fun playCustom(
        stems: List<SleepStem>,
        name: String,
        fadeInMs: Long = SleepDefaults.FADE_IN_MS,
    ) {
        if (stems.isEmpty()) return
        player.play(
            SleepScene(
                id = SleepDefaults.CUSTOM_SCENE_ID,
                key = CUSTOM_SCENE_KEY,
                name = name,
                description = "",
                stems = stems.take(SleepDefaults.MAX_CUSTOM_TRACKS),
            ),
            fadeInMs = fadeInMs,
        )
    }

    suspend fun stop() = player.stop()

    fun setTrackVolume(
        stemId: Long,
        volume: Float,
    ) = player.setTrackVolume(stemId, volume)

    fun setMasterVolume(volume: Float) = player.setMasterVolume(volume)

    /** [minutes] 为 0 或负数时等同于取消定时器。 */
    fun setTimerMinutes(minutes: Int) {
        if (minutes <= 0) player.cancelTimer() else player.setTimer(minutes * 60_000L)
    }

    fun cancelTimer() = player.cancelTimer()

    private companion object {
        const val CUSTOM_SCENE_KEY = "custom"
    }
}

val sleepDomainModule =
    module {
        single { SleepUseCases(repository = get(), player = get()) }
    }
