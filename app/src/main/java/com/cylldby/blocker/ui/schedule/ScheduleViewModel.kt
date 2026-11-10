package com.cylldby.blocker.ui.schedule

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cylldby.blocker.BlockerApp
import com.cylldby.blocker.data.model.AppRule
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ScheduleViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as BlockerApp).repository

    val scheduledRules = repo.allAppRules
        .map { rules -> rules.filter { it.scheduleStartHour >= 0 } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun toggleSchedule(rule: AppRule) = viewModelScope.launch {
        repo.updateAppRule(rule.copy(scheduleActive = !rule.scheduleActive))
    }

    fun removeSchedule(rule: AppRule) = viewModelScope.launch {
        repo.updateAppRule(
            rule.copy(scheduleStartHour = -1, scheduleEndHour = -1, scheduleDays = "", scheduleActive = false)
        )
    }

    fun setScheduleForPackage(packageName: String, appName: String, startHour: Int, endHour: Int, days: String) =
        viewModelScope.launch {
            val existing = repo.getAppRule(packageName)
            val updated = existing?.copy(
                scheduleStartHour = startHour,
                scheduleEndHour = endHour,
                scheduleDays = days,
                scheduleActive = true
            ) ?: AppRule(
                packageName = packageName,
                appName = appName,
                dailyLimitMinutes = 0,
                scheduleStartHour = startHour,
                scheduleEndHour = endHour,
                scheduleDays = days,
                scheduleActive = true
            )
            repo.upsertAppRule(updated)
        }
}
