package com.cylldby.blocker.ui.apps

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cylldby.blocker.BlockerApp
import com.cylldby.blocker.data.model.AppRule
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as BlockerApp).repository

    val appRules = repo.allAppRules.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun setQuotaForPackage(packageName: String, appName: String, limitMinutes: Int) =
        viewModelScope.launch {
            val existing = repo.getAppRule(packageName)
            val updated = existing?.copy(dailyLimitMinutes = limitMinutes, appName = appName)
                ?: AppRule(packageName = packageName, appName = appName, dailyLimitMinutes = limitMinutes)
            repo.upsertAppRule(updated)
        }

    fun deleteRule(rule: AppRule) = viewModelScope.launch { repo.deleteAppRule(rule) }
    fun toggleRule(rule: AppRule) = viewModelScope.launch {
        repo.updateAppRule(rule.copy(isActive = !rule.isActive))
    }
}
