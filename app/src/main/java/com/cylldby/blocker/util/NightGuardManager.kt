package com.cylldby.blocker.util

import android.content.Context
import java.util.Calendar

/**
 * Single source of truth for the sleep-time guard.
 *
 * Three distinct states, deliberately kept separate so a temporary unlock
 * can NEVER be used to unlock the settings:
 *  - isInNightWindow(): purely "is it between start and end right now"
 *  - isSettingsLocked(): night window + strict mode -> no config edits at all
 *  - isNightActive(): night window + no unlock window -> actually enforce blocking
 */
object NightGuardManager {

    private const val PREFS = "night_guard"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_START = "start_minute"
    private const val KEY_END = "end_minute"
    private const val KEY_WECHAT_CHAT_ONLY = "wechat_chat_only"
    private const val KEY_STRICT = "strict"
    private const val KEY_EXTRA_ALLOWED = "extra_allowed_pkgs"
    // Keep the historical key name so existing installs keep their state on upgrade.
    private const val KEY_UNLOCK_UNTIL = "grace_until_ms"

    const val DEFAULT_START_MINUTE = 23 * 60          // 23:00
    const val DEFAULT_END_MINUTE = 7 * 60             // 07:00

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context) = prefs(context).getBoolean(KEY_ENABLED, false)

    fun getStartMinute(context: Context) =
        prefs(context).getInt(KEY_START, DEFAULT_START_MINUTE)

    fun getEndMinute(context: Context) =
        prefs(context).getInt(KEY_END, DEFAULT_END_MINUTE)

    fun isWechatChatOnly(context: Context) =
        prefs(context).getBoolean(KEY_WECHAT_CHAT_ONLY, true)

    /** Strict = settings cannot be changed, and the guard cannot be switched off, during the night. */
    fun isStrict(context: Context) = prefs(context).getBoolean(KEY_STRICT, true)

    fun getExtraAllowed(context: Context): Set<String> {
        val raw = prefs(context).getString(KEY_EXTRA_ALLOWED, "") ?: ""
        return if (raw.isBlank()) emptySet() else raw.split(",").filter { it.isNotBlank() }.toSet()
    }

    fun getWindowText(context: Context) =
        "${NightWindow.format(getStartMinute(context))} – ${NightWindow.format(getEndMinute(context))}"

    /** Pure clock check — unaffected by unlock windows. */
    fun isInNightWindow(context: Context, now: Calendar = Calendar.getInstance()): Boolean {
        if (!isEnabled(context)) return false
        val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return NightWindow.isInWindow(minute, getStartMinute(context), getEndMinute(context))
    }

    fun isSettingsLocked(context: Context, now: Calendar = Calendar.getInstance()): Boolean =
        isStrict(context) && isInNightWindow(context, now)

    fun isNightActive(context: Context, now: Calendar = Calendar.getInstance()): Boolean =
        isInNightWindow(context, now) && !isUnlockActive(context)

    fun minutesUntilMorning(context: Context, now: Calendar = Calendar.getInstance()): Int {
        val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return NightWindow.minutesUntilEnd(minute, getStartMinute(context), getEndMinute(context))
    }

    /** WeChat is always handled by WeChatGuard, never by the ordinary whitelist. */
    fun isPackageAllowed(context: Context, pkg: String): Boolean {
        if (pkg == WeChatGuard.PACKAGE) return false
        return EssentialApps.isEssential(context, pkg) || pkg in getExtraAllowed(context)
    }

    /**
     * System areas that could be used to disable the guard at night:
     * settings (turn off accessibility / overlay), package installer (uninstall),
     * and common app stores (install a bypass tool).
     */
    fun isTamperPackage(pkg: String): Boolean {
        val p = pkg.lowercase()
        if (p == "com.android.settings") return true
        return listOf(
            "packageinstaller", "settings", "appstore",
            "appmarket", "market", "vending"
        ).any { p.contains(it) }
    }

    // ----- Setters. Every one of them refuses to run while locked. -----

    private fun editable(context: Context) = !isSettingsLocked(context)

    fun setEnabled(context: Context, value: Boolean): Boolean {
        // Turning the guard OFF at night is the one operation strict mode exists to stop.
        // Turning it ON is always allowed (it only makes the phone safer).
        if (!value && !editable(context)) return false
        prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()
        return true
    }

    fun setWindow(context: Context, startMinute: Int, endMinute: Int): Boolean {
        if (!editable(context)) return false
        // A zero-length window silently disables the guard — never allow it.
        if (startMinute == endMinute ||
            startMinute !in 0 until NightWindow.MINUTES_PER_DAY ||
            endMinute !in 0 until NightWindow.MINUTES_PER_DAY
        ) return false
        prefs(context).edit()
            .putInt(KEY_START, startMinute)
            .putInt(KEY_END, endMinute)
            .apply()
        return true
    }

    fun setWechatChatOnly(context: Context, value: Boolean): Boolean {
        if (!editable(context)) return false
        prefs(context).edit().putBoolean(KEY_WECHAT_CHAT_ONLY, value).apply()
        return true
    }

    fun setStrict(context: Context, value: Boolean): Boolean {
        if (!editable(context)) return false
        prefs(context).edit().putBoolean(KEY_STRICT, value).apply()
        return true
    }

    fun setExtraAllowed(context: Context, packages: Set<String>): Boolean {
        if (!editable(context)) return false
        prefs(context).edit()
            .putString(KEY_EXTRA_ALLOWED, packages.filter { it.isNotBlank() }.joinToString(","))
            .apply()
        return true
    }

    // ----- Temporary unlock window (opened ONLY by UnlockManager) -----

    fun isUnlockActive(context: Context) =
        System.currentTimeMillis() < prefs(context).getLong(KEY_UNLOCK_UNTIL, 0L)

    fun getUnlockRemainingMs(context: Context) =
        (prefs(context).getLong(KEY_UNLOCK_UNTIL, 0L) - System.currentTimeMillis()).coerceAtLeast(0L)

    /**
     * [minutes] < 0 means "until this night window ends". This deliberately
     * does not touch settings: strict mode stays on for the whole night, so
     * an unlocked phone still cannot be used to weaken tomorrow's guard.
     */
    fun activateUnlock(context: Context, minutes: Int) {
        val duration = if (minutes < 0) {
            (minutesUntilMorning(context) + 1).coerceAtLeast(1)
        } else {
            minutes.coerceIn(1, 24 * 60)
        }
        prefs(context).edit()
            .putLong(KEY_UNLOCK_UNTIL, System.currentTimeMillis() + duration * 60_000L)
            .apply()
    }
}
