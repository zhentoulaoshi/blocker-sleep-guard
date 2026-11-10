package com.cylldby.blocker.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.cylldby.blocker.BlockerApp
import com.cylldby.blocker.MainActivity
import com.cylldby.blocker.util.QuickBlockManager
import kotlinx.coroutines.*

class QuickBlockService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        const val ACTION_START = "com.cylldby.blocker.QB_START"
        const val ACTION_STOP  = "com.cylldby.blocker.QB_STOP"
        const val EXTRA_DURATION_MS = "duration_ms"
        private const val NOTIFICATION_ID = 2
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            QuickBlockManager.stop(this)
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        startCountdown()
        return START_NOT_STICKY
    }

    private fun startCountdown() {
        serviceScope.launch {
            while (isActive) {
                val remaining = QuickBlockManager.getRemainingMs(this@QuickBlockService)
                if (remaining <= 0) {
                    QuickBlockManager.stop(this@QuickBlockService)
                    stopSelf()
                    break
                }
                updateNotification(remaining)
                delay(30_000)
            }
        }
    }

    private fun buildNotification(remainingMs: Long = QuickBlockManager.getRemainingMs(this)): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val mins = (remainingMs / 60_000).toInt()
        return NotificationCompat.Builder(this, BlockerApp.CHANNEL_ID)
            .setContentTitle("Quick Block active")
            .setContentText("${mins}m remaining")
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(remainingMs: Long) {
        val nm = getSystemService(android.app.NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(remainingMs))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
