package com.cylldby.blocker.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.cylldby.blocker.BlockerApp
import com.cylldby.blocker.MainActivity
import com.cylldby.blocker.util.NightGuardManager
import kotlinx.coroutines.*

/**
 * Persistent foreground service for the sleep guard.
 *
 * It does not decide what to block (the accessibility service does that on every
 * window event). Its job is to keep a visible, non-dismissible reminder on the
 * phone and to survive OEM battery managers that kill silent background apps.
 */
class NightGuardService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var tickerJob: Job? = null

    companion object {
        const val ACTION_START = "com.cylldby.blocker.GUARD_START"
        const val ACTION_STOP  = "com.cylldby.blocker.GUARD_STOP"
        private const val NOTIFICATION_ID = 3
        private const val TICK_MS = 60_000L

        fun start(context: Context) {
            try {
                context.startForegroundService(
                    Intent(context, NightGuardService::class.java).apply { action = ACTION_START }
                )
            } catch (_: Exception) {
                // Android 12+ may reject a background FGS start; the accessibility
                // service retries on bind and enforcement does not depend on this.
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (isActive) {
                delay(TICK_MS)
                getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, buildNotification())
            }
        }
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val (title, text) = statusText()
        return NotificationCompat.Builder(this, BlockerApp.CHANNEL_GUARD_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun statusText(): Pair<String, String> {
        if (!NightGuardManager.isEnabled(this)) {
            return "睡眠守护未启用" to "打开 App 可设置 23:00 后的守护"
        }
        val window = NightGuardManager.getWindowText(this)
        if (NightGuardManager.isGraceActive(this)) {
            val mins = (NightGuardManager.getGraceRemainingMs(this) / 60_000L).toInt() + 1
            return "临时宽限中" to "约 ${mins} 分钟后自动恢复守护"
        }
        if (NightGuardManager.isNightActive(this)) {
            val left = NightGuardManager.minutesUntilMorning(this)
            val h = left / 60
            val m = left % 60
            return "睡眠守护中" to "$window · 还有 ${h}小时${m}分 · 仅通话/短信/微信聊天"
        }
        return "睡眠守护已设防" to "$window 自动开始 · 白天可调整设置"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        tickerJob?.cancel()
        serviceScope.cancel()
    }
}
