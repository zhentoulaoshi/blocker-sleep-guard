package com.cylldby.blocker.ui.sites

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.cylldby.blocker.R
import com.cylldby.blocker.databinding.FragmentSitesBinding
import com.cylldby.blocker.service.BlockerVpnService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class SitesFragment : Fragment() {

    private var _binding: FragmentSitesBinding? = null
    private val binding get() = _binding!!
    private val vm: SitesViewModel by viewModels()

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) startVpn()
            else binding.switchVpn.isChecked = false
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSitesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = SiteRuleAdapter { vm.deleteSite(it) }
        binding.rvSites.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSites.adapter = adapter

        lifecycleScope.launch { vm.siteRules.collect { adapter.submitList(it) } }

        val prefs = requireContext().getSharedPreferences("blocker_prefs", 0)
        binding.switchVpn.isChecked = prefs.getBoolean("vpn_enabled", false)
        binding.switchVpn.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("vpn_enabled", checked).apply()
            if (checked) requestVpnPermission() else stopVpn()
        }

        binding.fabAddSite.setOnClickListener { showAddSiteDialog() }
    }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(requireContext())
        if (intent != null) vpnPermissionLauncher.launch(intent) else startVpn()
    }

    private fun startVpn() {
        requireContext().startForegroundService(
            Intent(requireContext(), BlockerVpnService::class.java).apply {
                action = BlockerVpnService.ACTION_START
            }
        )
    }

    private fun stopVpn() {
        requireContext().startService(
            Intent(requireContext(), BlockerVpnService::class.java).apply {
                action = BlockerVpnService.ACTION_STOP
            }
        )
    }

    private fun showAddSiteDialog() {
        val domainInput = EditText(requireContext()).apply {
            hint = getString(R.string.add_site_hint)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Block a website")
            .setMessage("Enter a domain — all subdomains will also be blocked")
            .setView(domainInput)
            .setPositiveButton("Next") { _, _ ->
                val domain = domainInput.text.toString().trim().lowercase()
                    .removePrefix("https://").removePrefix("http://").removePrefix("www.")
                if (domain.isNotEmpty()) showLimitDialog(domain)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLimitDialog(domain: String) {
        val limitInput = EditText(requireContext()).apply {
            hint = getString(R.string.daily_limit_label)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("0")
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Daily limit for $domain")
            .setMessage("How many minutes per day before blocking?\n0 = block immediately, always.")
            .setView(limitInput)
            .setPositiveButton("Block") { _, _ ->
                val limit = limitInput.text.toString().toIntOrNull() ?: 0
                vm.addSite(domain, limit)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
