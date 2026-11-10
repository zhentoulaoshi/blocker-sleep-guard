package com.cylldby.blocker.ui.schedule

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cylldby.blocker.data.model.AppRule
import com.cylldby.blocker.databinding.ItemScheduleRuleBinding
import com.cylldby.blocker.util.FrictionDialog

class ScheduleRuleAdapter(
    private val onToggle: (AppRule) -> Unit,
    private val onDelete: (AppRule) -> Unit
) : ListAdapter<AppRule, ScheduleRuleAdapter.VH>(DIFF) {

    private val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    inner class VH(val binding: ItemScheduleRuleBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemScheduleRuleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val rule = getItem(position)
        holder.binding.tvAppName.text = rule.appName

        val daysLabel = if (rule.scheduleDays.isBlank()) "Every day" else {
            rule.scheduleDays.split(",")
                .mapNotNull { it.trim().toIntOrNull() }
                .joinToString(", ") { dayNames.getOrNull(it - 1) ?: it.toString() }
        }
        holder.binding.tvScheduleInfo.text =
            "%02d:00 – %02d:00  ·  %s".format(rule.scheduleStartHour, rule.scheduleEndHour, daysLabel)

        holder.binding.switchScheduleActive.setOnCheckedChangeListener(null)
        holder.binding.switchScheduleActive.isChecked = rule.scheduleActive
        holder.binding.switchScheduleActive.setOnCheckedChangeListener { view, isChecked ->
            if (rule.scheduleActive && !isChecked) {
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

        holder.binding.btnDeleteSchedule.setOnClickListener { onDelete(rule) }
    }

    private fun bindSwitch(holder: VH, rule: AppRule) {
        holder.binding.switchScheduleActive.setOnCheckedChangeListener(null)
        holder.binding.switchScheduleActive.isChecked = rule.scheduleActive
        holder.binding.switchScheduleActive.setOnCheckedChangeListener { view, isChecked ->
            if (rule.scheduleActive && !isChecked) {
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
