package com.cylldby.blocker.ui.quickblock

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import com.cylldby.blocker.service.QuickBlockService
import com.cylldby.blocker.util.QuickBlockManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class QuickBlockViewModel(app: Application) : AndroidViewModel(app) {
    private val _allowedPackages = MutableStateFlow<Set<String>>(emptySet())
    val allowedPackages: StateFlow<Set<String>> = _allowedPackages

    private val _durationMinutes = MutableStateFlow(30)
    val durationMinutes: StateFlow<Int> = _durationMinutes

    init {
        // Persist the exception list across app restarts instead of silently
        // wiping it every time a Focus session ends.
        _allowedPackages.value = QuickBlockManager.getAllowedPackages(app)
    }

    val isActive get() = QuickBlockManager.isActive(getApplication())
    fun getRemainingMs() = QuickBlockManager.getRemainingMs(getApplication())

    fun setDuration(minutes: Int) { _durationMinutes.value = minutes }

    fun toggleAllowedPackage(pkg: String) {
        _allowedPackages.value = _allowedPackages.value.toMutableSet().also {
            if (pkg in it) it.remove(pkg) else it.add(pkg)
        }
    }

    fun startBlock(context: Context) {
        val durationMs = _durationMinutes.value * 60_000L
        QuickBlockManager.start(context, durationMs, _allowedPackages.value)
        context.startForegroundService(
            Intent(context, QuickBlockService::class.java).apply {
                action = QuickBlockService.ACTION_START
                putExtra(QuickBlockService.EXTRA_DURATION_MS, durationMs)
            }
        )
    }

    fun stopBlock(context: Context) {
        QuickBlockManager.stop(context)
        context.startService(Intent(context, QuickBlockService::class.java).apply {
            action = QuickBlockService.ACTION_STOP
        })
    }
}
