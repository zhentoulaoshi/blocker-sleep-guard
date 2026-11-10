package com.cylldby.blocker.ui.sites

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cylldby.blocker.BlockerApp
import com.cylldby.blocker.data.model.SiteRule
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SitesViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as BlockerApp).repository

    val siteRules = repo.allSiteRules.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun addSite(domain: String, limitMinutes: Int) = viewModelScope.launch {
        repo.addSiteRule(SiteRule(domain, limitMinutes))
    }

    fun deleteSite(rule: SiteRule) = viewModelScope.launch { repo.deleteSiteRule(rule) }
}
