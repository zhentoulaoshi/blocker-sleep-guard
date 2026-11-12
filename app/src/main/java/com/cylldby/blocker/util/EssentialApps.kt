package com.cylldby.blocker.util

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.provider.AlarmClock

/**
 * The handful of apps that must keep working at night: dialer / in-call UI,
 * SMS, contacts, home screen, alarm clock, system UI.
 *
 * Package names differ a lot between AOSP, MIUI, EMUI, ColorOS, OneUI..., so
 * we resolve the real handlers through intents first and fall back to a
 * conservative keyword match that is limited to system packages only.
 */
object EssentialApps {

    private val SYSTEM_KEYWORDS = listOf(
        "dialer", "phone", "incall", "telecom", "telephony",
        "mms", "messaging", "sms", "contacts",
        "deskclock", ".clock", "clock.",
        "launcher", ".home", "home.", "systemui"
    )

    /** Packages that must never be treated as an ordinary whitelist entry. */
    private val NEVER_ESSENTIAL = setOf(
        "com.android.settings", "com.android.vending"
    )

    @Volatile
    private var cachedResolved: Set<String>? = null

    /** Call after boot / package changes so a newly installed dialer is picked up. */
    fun invalidateCache() {
        cachedResolved = null
    }

    fun resolvedPackages(context: Context): Set<String> {
        cachedResolved?.let { return it }
        val pm = context.packageManager
        val out = mutableSetOf<String>()

        fun collect(intent: Intent) {
            try {
                pm.resolveActivity(intent, 0)?.activityInfo?.let { out.add(it.packageName) }
                pm.queryIntentActivities(intent, 0).forEach { out.add(it.activityInfo.packageName) }
            } catch (_: Exception) {
                // Some ROMs throw for unusual intents; ignore and keep the others.
            }
        }

        collect(Intent(Intent.ACTION_DIAL))
        collect(Intent(Intent.ACTION_SENDTO, Uri.parse("sms:")))
        collect(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
        // ACTION_SHOW_ALARMS lives on AlarmClock, not on Intent. Also probe
        // ACTION_SET_ALARM because some ROMs only declare a handler for that one.
        collect(Intent(AlarmClock.ACTION_SHOW_ALARMS))
        collect(Intent(AlarmClock.ACTION_SET_ALARM))

        val result = out.toSet()
        cachedResolved = result
        return result
    }

    fun isEssential(context: Context, pkg: String): Boolean {
        if (pkg.isBlank() || pkg == "android" || pkg == context.packageName) return true
        if (pkg in NEVER_ESSENTIAL) return false
        if (pkg in resolvedPackages(context)) return true

        val isSystem = try {
            val info = context.packageManager.getApplicationInfo(pkg, 0)
            (info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
        } catch (_: Exception) {
            false
        }
        if (!isSystem) return false

        val lower = pkg.lowercase()
        return SYSTEM_KEYWORDS.any { lower.contains(it) }
    }
}
