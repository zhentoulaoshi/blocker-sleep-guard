package com.cylldby.blocker.util

import android.content.Context

object QuickBlockManager {
    private const val PREFS = "quick_block"
    private const val KEY_END_MS = "end_ms"
    private const val KEY_ALLOWED = "allowed_pkgs"

    fun isActive(context: Context): Boolean =
        System.currentTimeMillis() < getEndMs(context)

    fun isAllowed(context: Context, pkg: String): Boolean {
        if (!isActive(context)) return true
        val allowed = getAllowedPackages(context)
        // empty set means all blocked; non-empty means only listed packages are allowed
        return allowed.isNotEmpty() && pkg in allowed
    }

    fun getRemainingMs(context: Context): Long =
        (getEndMs(context) - System.currentTimeMillis()).coerceAtLeast(0)

    fun start(context: Context, durationMs: Long, allowedPackages: Set<String>) {
        prefs(context).edit()
            .putLong(KEY_END_MS, System.currentTimeMillis() + durationMs)
            .putString(KEY_ALLOWED, allowedPackages.joinToString(","))
            .apply()
    }

    fun stop(context: Context) {
        prefs(context).edit().putLong(KEY_END_MS, 0).apply()
    }

    fun getAllowedPackages(context: Context): Set<String> {
        val s = prefs(context).getString(KEY_ALLOWED, "") ?: ""
        return if (s.isBlank()) emptySet() else s.split(",").toSet()
    }

    private fun getEndMs(context: Context) = prefs(context).getLong(KEY_END_MS, 0L)
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
