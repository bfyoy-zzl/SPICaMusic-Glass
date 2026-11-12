package me.spica27.spicamusic.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import me.spica27.spicamusic.MainActivity
import me.spica27.spicamusic.R
import me.spica27.spicamusic.feature.sleep.domain.ISleepPlayer
import me.spica27.spicamusic.feature.sleep.domain.SleepPlaybackState
import org.koin.android.ext.android.inject
import timber.log.Timber

/**
 * 助眠混音的前台服务。
 *
 * 服务本身不做任何音频工作——播放器是进程级单例（[ISleepPlayer]），这里只负责两件事：
 * 1. 用 mediaPlayback 类型的通知栏前台服务把进程钉住，避免熄屏后被杀；
 * 2. 提供"停止"入口，并把当前场景显示在通知里。
 *
 * 唤醒锁由 ExoPlayer 的 `WAKE_MODE_LOCAL` 自己管理，不在这里手动持有。
 */
class SleepMixService : Service() {
    private val player: ISleepPlayer by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 见过一次"正在播放"之后，再变成停止才收工，避免服务启动瞬间的状态竞争。 */
    private var sawPlaying = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForegroundWith(player.state.value)

        scope.launch {
            // 通知里只显示分钟，所以把每秒变化的倒计时折算成分钟再交给 distinctUntilChanged，
            // 避免整夜播放时每秒刷一次通知（8 小时就是两万多次 notify）。
            combine(player.state, player.countdown) { state, countdown ->
                state to countdown?.let { ((it.remainingMs + 59_999L) / 60_000L).toInt() }
            }.distinctUntilChanged()
                .collect { (state, remainingMinutes) ->
                    if (state.isPlaying) {
                        sawPlaying = true
                    } else if (sawPlaying) {
                        // 定时结束或用户停止：音轨已释放，服务可以退场。
                        ServiceCompat.stopForeground(this@SleepMixService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                        return@collect
                    }
                    runCatching {
                        NotificationManagerCompat
                            .from(this@SleepMixService)
                            .notify(NOTIFICATION_ID, buildNotification(state, remainingMinutes))
                    }
                }
        }

        // 兜底：素材缺失等原因导致播放始终没起来时，不能让前台服务一直挂着。
        scope.launch {
            delay(ORPHAN_GRACE_MS)
            if (!sawPlaying) {
                Timber.w("助眠服务启动后始终未进入播放状态，自动退出")
                ServiceCompat.stopForeground(this@SleepMixService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACTION_STOP) {
            scope.launch { runCatching { player.stop() } }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundWith(state: SleepPlaybackState) {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(state, remainingMinutes = null),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
    }

    private fun buildNotification(
        state: SleepPlaybackState,
        remainingMinutes: Int?,
    ): Notification {
        val openApp =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val stopIntent =
            PendingIntent.getService(
                this,
                1,
                Intent(this, SleepMixService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val title = state.sceneName.ifBlank { getString(R.string.sleep_title) }
        val trackCount = state.tracks.size
        val text =
            when {
                trackCount == 0 -> getString(R.string.sleep_notification_idle)
                remainingMinutes == null -> getString(R.string.sleep_notification_tracks, trackCount)
                remainingMinutes <= 0 -> getString(R.string.sleep_notification_ending, trackCount)
                else -> getString(R.string.sleep_notification_remaining, trackCount, remainingMinutes)
            }

        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, getString(R.string.sleep_notification_stop), stopIntent)
            .build()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        // 低优先级 + 静音：助眠场景下通知本身不能发出任何声音。
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.sleep_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
        manager.createNotificationChannel(channel)
        Timber.d("助眠通知渠道已创建")
    }

    companion object {
        private const val CHANNEL_ID = "sleep_mix_playback"
        private const val NOTIFICATION_ID = 0x5EED

        /** 启动后多久仍未进入播放状态就自我了断。 */
        private const val ORPHAN_GRACE_MS = 3_000L

        const val ACTION_STOP = "me.spica27.spicamusic.action.STOP_SLEEP_MIX"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, SleepMixService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SleepMixService::class.java))
        }
    }
}
