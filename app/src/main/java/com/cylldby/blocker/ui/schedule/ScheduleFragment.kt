package com.cylldby.blocker.ui.schedule

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.cylldby.blocker.databinding.FragmentScheduleBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class ScheduleFragment : Fragment() {

    private var _binding: FragmentScheduleBinding? = null
    private val binding get() = _binding!!
    private val vm: ScheduleViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = ScheduleRuleAdapter(
            onToggle = { vm.toggleSchedule(it) },
            onDelete = { vm.removeSchedule(it) }
        )
        binding.rvSchedule.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSchedule.adapter = adapter

        lifecycleScope.launch { vm.scheduledRules.collect { adapter.submitList(it) } }

        binding.fabAddSchedule.setOnClickListener { showPickAppDialog() }
    }

    private fun showPickAppDialog() {
        val pm = requireContext().packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .sortedBy { pm.getApplicationLabel(it).toString() }

        val adapter = object : ArrayAdapter<ApplicationInfo>(
            requireContext(), android.R.layout.select_dialog_item, apps
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = convertView ?: LayoutInflater.from(context)
                    .inflate(android.R.layout.activity_list_item, parent, false)
                val info = apps[position]
                row.findViewById<TextView>(android.R.id.text1).text =
                    pm.getApplicationLabel(info).toString()
                row.findViewById<ImageView>(android.R.id.icon)
                    .setImageDrawable(pm.getApplicationIcon(info))
                return row
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select app to schedule")
            .setAdapter(adapter) { _, i ->
                showScheduleDialog(apps[i].packageName, pm.getApplicationLabel(apps[i]).toString())
            }
            .show()
    }

    private fun showScheduleDialog(packageName: String, appName: String) {
        val ctx = requireContext()
        val startPicker = NumberPicker(ctx).apply { minValue = 0; maxValue = 23; value = 22 }
        val endPicker   = NumberPicker(ctx).apply { minValue = 0; maxValue = 23; value = 6  }

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(48, 24, 48, 0)
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(ctx).apply { text = "From" })
                addView(startPicker)
            })
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 0, 0, 0)
                addView(TextView(ctx).apply { text = "Until" })
                addView(endPicker)
            })
        }

        MaterialAlertDialogBuilder(ctx)
            .setTitle("Block $appName")
            .setMessage("App will be blocked every day during this hour window (24h clock).")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                vm.setScheduleForPackage(packageName, appName, startPicker.value, endPicker.value, "")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
