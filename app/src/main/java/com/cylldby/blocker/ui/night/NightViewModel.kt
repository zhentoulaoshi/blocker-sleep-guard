package com.cylldby.blocker.ui.night

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.cylldby.blocker.service.NightGuardService
import com.cylldby.blocker.util.NightGuardManager
import com.cylldby.blocker.util.PermissionHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Calendar

data class NightUiState(
    val enabled: Boolean = false,
    val startMinute: Int = NightGuardManager.DEFAULT_START_MINUTE,
    val endMinute: Int = NightGuardManager.DEFAULT_END_MINUTE,
    val chatOnly: Boolean = true,
    val strict: Boolean = true,
    val locked: Boolean = false,
    val nightActive: Boolean = false,
    val graceActive: Boolean = false,
    val graceUsed: Boolean = false,
    val extraAllowed: Set<String> = emptySet(),
    val minutesLeft: Int = 0,
    val permissionsOk: Boolean = true
)

class NightViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(NightUiState())
    val state: StateFlow<NightUiState> = _state

    private val context get() = getApplication<Application>()

    fun refresh() {
        val c = context
        val now = Calendar.getInstance()
        _state.value = NightUiState(
            enabled = NightGuardManager.isEnabled(c),
            startMinute = NightGuardManager.getStartMinute(c),
            endMinute = NightGuardManager.getEndMinute(c),
            chatOnly = NightGuardManager.isWechatChatOnly(c),
            strict = NightGuardManager.isStrict(c),
            locked = NightGuardManager.isSettingsLocked(c, now),
            nightActive = NightGuardManager.isNightActive(c, now),
            graceActive = NightGuardManager.isGraceActive(c),
            graceUsed = NightGuardManager.graceUsedToday(c),
            extraAllowed = NightGuardManager.getExtraAllowed(c),
            minutesLeft = NightGuardManager.minutesUntilMorning(c, now),
            permissionsOk = PermissionHelper.guardPermissionsGranted(c)
        )
    }

    fun setEnabled(value: Boolean): Boolean {
        val ok = NightGuardManager.setEnabled(context, value)
        if (ok && value) NightGuardService.start(context)
        refresh()
        return ok
    }

    /** which: true = start time, false = end time. */
    fun setWindow(which: Boolean, hour: Int, minute: Int): Boolean {
        val c = context
        val picked = hour * 60 + minute
        val start = if (which) picked else NightGuardManager.getStartMinute(c)
        val end = if (which) NightGuardManager.getEndMinute(c) else picked
        if (start == end) return false
        val ok = NightGuardManager.setWindow(c, start, end)
        refresh()
        return ok
    }

    fun setWechatChatOnly(value: Boolean): Boolean {
        val ok = NightGuardManager.setWechatChatOnly(context, value)
        refresh()
        return ok
    }

    fun setStrict(value: Boolean): Boolean {
        val ok = NightGuardManager.setStrict(context, value)
        refresh()
        return ok
    }

    fun setExtraAllowed(packages: Set<String>): Boolean {
        val ok = NightGuardManager.setExtraAllowed(context, packages)
        refresh()
        return ok
    }

    fun requestGrace(): Boolean {
        if (!NightGuardManager.isInNightWindow(context)) return false
        val ok = NightGuardManager.requestGrace(context)
        refresh()
        return ok
    }

    fun isInNightWindow() = NightGuardManager.isInNightWindow(context)
}
