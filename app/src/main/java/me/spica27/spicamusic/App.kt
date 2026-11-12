package me.spica27.spicamusic

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.spcia.lyric_core.di.extraInfoModule
import me.spica27.spicamusic.artwork.ArtworkLoader
import me.spica27.spicamusic.artwork.ArtworkTreeRoot
import me.spica27.spicamusic.audioeffects.AudioEffectsApplier
import me.spica27.spicamusic.crash.CrashHandler
import me.spica27.spicamusic.di.AppModule
import me.spica27.spicamusic.feature.library.domain.MusicScanUseCases
import me.spica27.spicamusic.feature.library.domain.PlayHistoryUseCases
import me.spica27.spicamusic.feature.library.domain.ScanFolderUseCases
import me.spica27.spicamusic.feature.library.domain.libraryDomainModule
import me.spica27.spicamusic.feature.lyrics.domain.lyricsDomainModule
import me.spica27.spicamusic.feature.player.domain.playerDomainModule
import me.spica27.spicamusic.feature.settings.domain.settingsDomainModule
import me.spica27.spicamusic.feature.sleep.data.di.sleepDataModule
import me.spica27.spicamusic.feature.sleep.domain.ISleepPlayer
import me.spica27.spicamusic.feature.sleep.domain.SleepUseCases
import me.spica27.spicamusic.feature.sleep.domain.sleepDomainModule
import me.spica27.spicamusic.playback.SleepMusicArbiter
import me.spica27.spicamusic.player.api.IMusicPlayer
import me.spica27.spicamusic.player.impl.SpicaPlayer
import me.spica27.spicamusic.service.PlaybackService
import me.spica27.spicamusic.storage.impl.di.storageModule
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext.startKoin
import timber.log.Timber

/**
 * 应用程序类
 * 负责初始化 Koin 依赖注入、ImageLoader 和其他全局配置
 */
class App : Application() {
    private val musicScanService: MusicScanUseCases by inject()

    private val musicPlayer: IMusicPlayer by inject()

    private val playHistoryUseCases: PlayHistoryUseCases by inject()

    private val audioEffectsApplier: AudioEffectsApplier by inject()

    private val scanFolderUseCases: ScanFolderUseCases by inject()

    private val sleepUseCases: SleepUseCases by inject()

    private val sleepPlayer: ISleepPlayer by inject()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        instance = this

        // 初始化日志
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // 进程唯一的封面加载器，Compose / 通知 / MediaSession 共用
        ArtworkLoader.install(this)

        // 初始化 Koin 依赖注入
        startKoin {
            androidLogger()
            androidContext(this@App)
            modules(
                storageModule, // 数据模块 (feature-library-data)
                SpicaPlayer.createModule(PlaybackService::class.java), // 数据模块 (feature-player-data)
                libraryDomainModule,
                playerDomainModule,
                settingsDomainModule,
                lyricsDomainModule,
                sleepDataModule,
                sleepDomainModule,
                AppModule.appModule, // 应用模块
                extraInfoModule,
            )
        }

        // 额外扫描目录的 SAF 权限供封面解析读取同目录图片
        appScope.launch {
            scanFolderUseCases
                .getExtraFoldersFlow()
                .map { folders ->
                    folders
                        .filter { it.isAccessible }
                        .mapNotNull { folder ->
                            folder.pathPrefix?.let { ArtworkTreeRoot(folder.uriString.toUri(), it.trimEnd('/')) }
                        }
                }.distinctUntilChanged()
                .collect(ArtworkLoader::updateTreeRoots)
        }

        // Koin 启动后：加载并应用持久化音效设置
        audioEffectsApplier.start(appScope)

        // 播放历史保留窗裁剪：启动后台跑一次，把原始明细钉在保留窗内（只删明细、不动全时段汇总）
        appScope.launch {
            runCatching { playHistoryUseCases.pruneHistory() }
                .onFailure { Timber.w(it, "播放历史裁剪失败") }
        }

        // 曲库检查：数据库升级后的重建、标签提取版本升级、解释规则变化时后台处理；无权限或已是最新则直接返回
        appScope.launch {
            runCatching { musicScanService.ensureLibraryUpToDate() }
                .onFailure { Timber.w(it, "曲库检查失败") }
        }

        // 助眠场景播种：只在空库时写入内置预设，用户改过之后不会被覆盖
        appScope.launch {
            runCatching { sleepUseCases.ensureCatalog() }
                .onFailure { Timber.w(it, "助眠场景初始化失败") }
        }

        // 音乐 ⇄ 助眠互斥：音乐从任何入口开始播放（通知栏、耳机、其他页面）都停掉助眠混音
        SleepMusicArbiter(music = musicPlayer, sleep = sleepPlayer, scope = appScope).start()

        // 启动 MediaStore 变更监听
        setupMediaStoreObserver()
        // FFT 采样跟随"应用前台 + 屏幕点亮"
        setupFftLifecycle()
        CrashHandler.init(this)
    }

    /**
     * FFT 频谱采样同时跟随"应用可见"和"屏幕点亮"两个条件。
     *
     * 只有前台才有界面消费频谱，退到后台停止采样；熄屏后音频仍在播放，但没有任何
     * 可视化在绘制，继续采样纯粹浪费电——整夜放歌或助眠音频时这部分开销会累计成
     * 可观的耗电量。处理器默认关闭，进程在后台被拉起（如媒体恢复）时不会采样。
     */
    private fun setupFftLifecycle() {
        val powerManager = getSystemService(PowerManager::class.java)
        var inForeground = false

        fun refreshFftSampling() {
            val interactive = powerManager?.isInteractive == true
            val shouldSample = inForeground && interactive
            if (shouldSample) {
                musicPlayer.fftProcessor.enable()
            } else {
                musicPlayer.fftProcessor.disable()
            }
            Timber.d("FFT 采样%s（前台=%s，亮屏=%s）", if (shouldSample) "已开启" else "已停止", inForeground, interactive)
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    inForeground = true
                    refreshFftSampling()
                }

                override fun onStop(owner: LifecycleOwner) {
                    inForeground = false
                    refreshFftSampling()
                }
            },
        )

        // 熄屏与亮屏不会触发进程生命周期回调，只走 onStart/onStop 会让整夜播放一直采样。
        ContextCompat.registerReceiver(
            this,
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) = refreshFftSampling()
            },
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    /**
     * 设置 MediaStore 变更监听
     * 绑定到应用生命周期，前台时监听，后台时停止（节省资源）
     */
    private fun setupMediaStoreObserver() {
        // 立即启动监听器
        musicScanService.startMediaStoreObserver()
        Timber.i("MediaStore 监听器已启动")

        // 监听应用前后台切换，优化资源使用
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    // 应用进入前台，启动监听
                    musicScanService.startMediaStoreObserver()
                    Timber.d("应用进入前台，MediaStore 监听器已启动")
                }

                override fun onStop(owner: LifecycleOwner) {
                    // 应用进入后台，停止监听
                    musicScanService.stopMediaStoreObserver()
                    Timber.d("应用进入后台，MediaStore 监听器已停止")
                }
            },
        )
    }

    companion object {
        private lateinit var instance: App

        fun getInstance(): App = instance
    }
}
