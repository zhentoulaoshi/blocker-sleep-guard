package com.cylldby.blocker.ui.apps

import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cylldby.blocker.data.model.AppRule
import com.cylldby.blocker.databinding.ItemAppRuleBinding
import com.cylldby.blocker.util.FrictionDialog

class AppRuleAdapter(
    private val onToggle: (AppRule) -> Unit,
    private val onDelete: (AppRule) -> Unit
) : ListAdapter<AppRule, AppRuleAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemAppRuleBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemAppRuleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val rule = getItem(position)
        val pm = holder.itemView.context.packageManager

        try {
            val info = pm.getApplicationInfo(rule.packageName, 0)
            holder.binding.ivAppIcon.setImageDrawable(pm.getApplicationIcon(info))
        } catch (_: PackageManager.NameNotFoundException) {}

        holder.binding.tvAppName.text = rule.appName
        holder.binding.tvLimitInfo.text =
            if (rule.dailyLimitMinutes > 0) "${rule.dailyLimitMinutes} min/day" else "Schedule only"

        // Detach listener before setting state to avoid spurious callbacks.
        holder.binding.switchActive.setOnCheckedChangeListener(null)
        holder.binding.switchActive.isChecked = rule.isActive
        holder.binding.switchActive.setOnCheckedChangeListener { view, isChecked ->
            if (rule.isActive && !isChecked) {
                // Disabling — add friction.
                view.setOnCheckedChangeListener(null)
                view.isChecked = true                       // revert visually
                view.post { bindSwitch(holder, rule) }      // re-attach after revert
                FrictionDialog.show(
                    context = holder.itemView.context,
                    appName = rule.appName,
                    onConfirmed = { onToggle(rule) },
                    onCancelled = {}                        // switch already reverted
                )
            } else {
                onToggle(rule)
            }
        }

        holder.binding.btnDelete.setOnClickListener { onDelete(rule) }
    }

    private fun bindSwitch(holder: VH, rule: AppRule) {
        holder.binding.switchActive.setOnCheckedChangeListener(null)
        holder.binding.switchActive.isChecked = rule.isActive
        holder.binding.switchActive.setOnCheckedChangeListener { view, isChecked ->
            if (rule.isActive && !isChecked) {
                view.setOnCheckedChangeListener(null)
                view.isChecked = true
                view.post { bindSwitch(holder, rule) }
                FrictionDialog.show(
                    context = holder.itemView.context,
                    appName = rule.appName,
                    onConfirmed = { onToggle(rule) },
                    onCancelled = {}
                )
            } else {
                onToggle(rule)
            }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<AppRule>() {
            override fun areItemsTheSame(a: AppRule, b: AppRule) = a.packageName == b.packageName
            override fun areContentsTheSame(a: AppRule, b: AppRule) = a == b
        }
    }
}
