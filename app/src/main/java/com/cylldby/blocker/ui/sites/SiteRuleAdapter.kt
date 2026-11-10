package com.cylldby.blocker.ui.sites

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cylldby.blocker.data.model.SiteRule
import com.cylldby.blocker.databinding.ItemSiteRuleBinding

class SiteRuleAdapter(
    private val onDelete: (SiteRule) -> Unit
) : ListAdapter<SiteRule, SiteRuleAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemSiteRuleBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemSiteRuleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val rule = getItem(position)
        holder.binding.tvDomain.text = rule.domain
        holder.binding.tvLimitInfo.text = if (rule.dailyLimitMinutes == 0) {
            "Always blocked"
        } else {
            "Max ${rule.dailyLimitMinutes} min/day"
        }
        holder.binding.btnDelete.setOnClickListener { onDelete(rule) }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<SiteRule>() {
            override fun areItemsTheSame(a: SiteRule, b: SiteRule) = a.domain == b.domain
            override fun areContentsTheSame(a: SiteRule, b: SiteRule) = a == b
        }
    }
}
