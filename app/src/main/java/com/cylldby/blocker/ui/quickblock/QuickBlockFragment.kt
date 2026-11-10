package com.cylldby.blocker.ui.quickblock

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.cylldby.blocker.R
import com.cylldby.blocker.databinding.FragmentQuickBlockBinding
import com.cylldby.blocker.util.EssentialApps
import com.cylldby.blocker.util.QuickBlockManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class QuickBlockFragment : Fragment() {

    private var _binding: FragmentQuickBlockBinding? = null
    private val binding get() = _binding!!
    private val vm: QuickBlockViewModel by viewModels()

    private val tickHandler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            updateRemainingTime()
            tickHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQuickBlockBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupDurationChips()
        setupExceptionAdapter()

        binding.btnAddException.setOnClickListener { showPickAppDialog() }
        binding.btnStartBlock.setOnClickListener {
            vm.startBlock(requireContext())
            showActiveState()
        }
        binding.btnEmergencyStop.setOnClickListener { showEmergencyStopDialog() }
    }

    private fun setupDurationChips() {
        binding.chip15m.setOnCheckedChangeListener { _, checked -> if (checked) vm.setDuration(15) }
        binding.chip30m.setOnCheckedChangeListener { _, checked -> if (checked) vm.setDuration(30) }
        binding.chip1h.setOnCheckedChangeListener  { _, checked -> if (checked) vm.setDuration(60) }
        binding.chip2h.setOnCheckedChangeListener  { _, checked -> if (checked) vm.setDuration(120) }
        binding.chipCustom.setOnCheckedChangeListener { _, checked ->
            if (checked) showCustomDurationDialog()
        }
    }

    private fun showCustomDurationDialog() {
        val input = EditText(requireContext()).apply {
            hint = "Minutes"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Custom duration")
            .setMessage("Enter duration in minutes")
            .setView(input)
            .setPositiveButton("Set") { _, _ ->
                val mins = input.text.toString().toIntOrNull() ?: 30
                vm.setDuration(mins)
            }
            .setNegativeButton("Cancel") { _, _ ->
                binding.chip30m.isChecked = true
            }
            .show()
    }

    private fun setupExceptionAdapter() {
        binding.rvExceptions.layoutManager = LinearLayoutManager(requireContext())
        val adapter = ExceptionAppAdapter { pkg -> vm.toggleAllowedPackage(pkg) }
        binding.rvExceptions.adapter = adapter

        lifecycleScope.launch {
            vm.allowedPackages.collect { packages ->
                adapter.submitList(packages.toList())
            }
        }
    }

    private fun showPickAppDialog() {
        val pm = requireContext().packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val launcherPkgs = try {
            pm.queryIntentActivities(launcherIntent, 0)
                .map { it.activityInfo.packageName }.toSet()
        } catch (_: Exception) { emptySet() }
        val essential = EssentialApps.resolvedPackages(requireContext())

        // Include the system dialer / SMS / launcher apps. The old filter
        // (FLAG_SYSTEM == 0) hid exactly the apps a night-time exception needs.
        val apps = pm.getInstalledApplications(0)
            .filter { it.packageName in launcherPkgs || it.packageName in essential }
            .filter { it.packageName != requireContext().packageName }
            .sortedBy { pm.getApplicationLabel(it).toString() }

        val labels = apps.map { pm.getApplicationLabel(it).toString() }.toTypedArray()
        val pkgs = apps.map { it.packageName }
        val allowed = vm.allowedPackages.value
        val checked = BooleanArray(pkgs.size) { pkgs[it] in allowed }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Allow during Quick Block")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("Done") { _, _ ->
                for (i in pkgs.indices) {
                    val pkg = pkgs[i]
                    val inAllowed = pkg in vm.allowedPackages.value
                    if (checked[i] && !inAllowed) vm.toggleAllowedPackage(pkg)
                    else if (!checked[i] && inAllowed) vm.toggleAllowedPackage(pkg)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEmergencyStopDialog() {
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Emergency Stop?")
            .setMessage("Stop Quick Block now? This requires a 10-second wait.")
            .setPositiveButton("Stop (10s)") { _, _ ->
                vm.stopBlock(requireContext())
                showInactiveState()
            }
            .setNegativeButton("Keep blocking", null)
            .create()
        dialog.show()

        val btn = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
        btn.isEnabled = false

        val timer = object : android.os.CountDownTimer(10_000, 1000) {
            override fun onTick(ms: Long) {
                val s = ms / 1000 + 1
                btn.text = "Stop (${s}s)"
            }
            override fun onFinish() {
                btn.text = "Stop"
                btn.isEnabled = true
            }
        }
        timer.start()
        dialog.setOnDismissListener { timer.cancel() }
    }

    private fun showActiveState() {
        binding.layoutInactive.visibility = View.GONE
        binding.layoutActive.visibility = View.VISIBLE
        updateRemainingTime()
        updateAllowedAppsLabel()
        tickHandler.post(tickRunnable)
    }

    private fun showInactiveState() {
        tickHandler.removeCallbacks(tickRunnable)
        binding.layoutActive.visibility = View.GONE
        binding.layoutInactive.visibility = View.VISIBLE
    }

    private fun updateRemainingTime() {
        val ms = vm.getRemainingMs()
        if (ms <= 0) {
            showInactiveState()
            return
        }
        val totalSecs = (ms / 1000).toInt()
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        binding.tvRemainingTime.text = String.format("%d:%02d", mins, secs)
    }

    private fun updateAllowedAppsLabel() {
        val allowed = QuickBlockManager.getAllowedPackages(requireContext())
        val pm = requireContext().packageManager
        binding.tvAllowedApps.text = if (allowed.isEmpty()) {
            "All apps blocked"
        } else {
            val names = allowed.mapNotNull { pkg ->
                try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
                catch (_: Exception) { pkg }
            }
            "Allowed: ${names.joinToString(", ")}"
        }
    }

    override fun onResume() {
        super.onResume()
        if (vm.isActive) showActiveState() else showInactiveState()
    }

    override fun onPause() {
        super.onPause()
        tickHandler.removeCallbacks(tickRunnable)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        tickHandler.removeCallbacks(tickRunnable)
        _binding = null
    }
}
