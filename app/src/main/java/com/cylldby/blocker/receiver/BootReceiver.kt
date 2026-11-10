package com.cylldby.blocker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.cylldby.blocker.service.BlockerVpnService
import com.cylldby.blocker.service.NightGuardService
import com.cylldby.blocker.util.EssentialApps
import com.cylldby.blocker.util.NightGuardManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // The dialer / SMS / launcher handlers may have changed while the device
        // was off, so re-resolve them instead of reusing a stale process cache.
        EssentialApps.invalidateCache()

        // The guard is time-based, so no alarm needs to be re-armed after a
        // reboot — just bring the persistent notification back.
        if (NightGuardManager.isEnabled(context)) {
            NightGuardService.start(context)
        }

        val prefs = context.getSharedPreferences("blocker_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("vpn_enabled", false)) {
            // Android 12+ can reject a foreground-service start from a boot
            // broadcast. Never let that kill the whole receiver (and with it
            // the night guard start above).
            try {
                context.startForegroundService(
                    Intent(context, BlockerVpnService::class.java).apply {
                        action = BlockerVpnService.ACTION_START
                    }
                )
            } catch (e: Exception) {
                Log.w("BootReceiver", "VPN restart rejected at boot: ${e.message}")
            }
        }
    }
}
