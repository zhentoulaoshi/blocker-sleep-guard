package com.cylldby.blocker.ui.apps

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.cylldby.blocker.R
import com.cylldby.blocker.databinding.FragmentAppsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class AppsFragment : Fragment() {

    private var _binding: FragmentAppsBinding? = null
    private val binding get() = _binding!!
    private val vm: AppsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAppsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = AppRuleAdapter(onToggle = { vm.toggleRule(it) }, onDelete = { vm.deleteRule(it) })
        binding.rvApps.layoutManager = LinearLayoutManager(requireContext())
        binding.rvApps.adapter = adapter

        lifecycleScope.launch { vm.appRules.collect { adapter.submitList(it) } }

        binding.fabAddApp.setOnClickListener { showPickAppDialog() }
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
            .setTitle("Select app to block")
            .setAdapter(adapter) { _, i ->
                showLimitDialog(apps[i].packageName, pm.getApplicationLabel(apps[i]).toString())
            }
            .show()
    }

    private fun showLimitDialog(pkg: String, name: String) {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.daily_limit_label)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(48, 24, 48, 0)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(name)
            .setMessage("Daily limit in minutes (0 = no quota, schedule only)")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val limit = input.text.toString().toIntOrNull() ?: 0
                vm.setQuotaForPackage(pkg, name, limit)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
