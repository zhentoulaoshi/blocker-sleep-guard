package com.cylldby.blocker.ui.night

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cylldby.blocker.databinding.FragmentNightBinding
import com.cylldby.blocker.util.EssentialApps
import com.cylldby.blocker.util.NightGuardManager
import com.cylldby.blocker.util.NightWindow
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class NightFragment : Fragment() {

    private var _binding: FragmentNightBinding? = null
    private val binding get() = _binding!!
    private val vm: NightViewModel by viewModels()

    private val tickHandler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            vm.refresh()
            tickHandler.postDelayed(this, 30_000L)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNightBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { render(it) }
            }
        }

        // OnClickListener (not OnCheckedChangeListener) avoids feedback loops
        // when render() writes isChecked back into the switches.
        binding.switchNight.setOnClickListener {
            if (!vm.setEnabled(binding.switchNight.isChecked)) {
                toast("夜间守护已锁定，明早自动解锁")
                vm.refresh()
            }
        }
        binding.switchWechatChatOnly.setOnClickListener {
            if (!vm.setWechatChatOnly(binding.switchWechatChatOnly.isChecked)) vm.refresh()
        }
        binding.switchStrict.setOnClickListener {
            if (!vm.setStrict(binding.switchStrict.isChecked)) vm.refresh()
        }

        binding.btnSetStart.setOnClickListener {
            showTimePicker(true, vm.state.value.startMinute)
        }
        binding.btnSetEnd.setOnClickListener {
            showTimePicker(false, vm.state.value.endMinute)
        }
        binding.btnEditWhitelist.setOnClickListener {
            showWhitelistDialog(vm.state.value.extraAllowed)
        }
        binding.btnGrace.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("临时放行 5 分钟？")
                .setMessage("今晚只能使用一次，到点自动恢复守护。设置本身仍然锁定。")
                .setPositiveButton("确认放行") { _, _ ->
                    if (!vm.requestGrace()) toast("今晚的一次性放行已用完，或当前不在守护时段")
                }
                .setNegativeButton("不用了", null)
                .show()
        }
        binding.btnCheckPermissions.setOnClickListener { showPermissionDialog() }

        vm.refresh()
    }

    private fun render(s: NightUiState) {
        binding.switchNight.isChecked = s.enabled
        binding.switchWechatChatOnly.isChecked = s.chatOnly
        binding.switchStrict.isChecked = s.strict

        val window = "${NightWindow.format(s.startMinute)} – ${NightWindow.format(s.endMinute)}"
        binding.btnSetStart.text = "开始 ${NightWindow.format(s.startMinute)}"
        binding.btnSetEnd.text = "结束 ${NightWindow.format(s.endMinute)}"

        val (status, detail) = when {
            !s.enabled -> "睡眠守护未启用" to "先设好时段与微信规则，再打开上面的开关"
            s.graceActive -> {
                val mins = (NightGuardManager.getGraceRemainingMs(requireContext()) / 60_000L).toInt() + 1
                "临时宽限中" to "约 ${mins} 分钟后自动恢复守护"
            }
            s.nightActive -> {
                val h = s.minutesLeft / 60
                val m = s.minutesLeft % 60
                "睡眠守护中" to "$window · 还有 ${h} 小时 ${m} 分 · 电话 / 短信 / 微信聊天可用"
            }
            else -> "已设防" to "$window 自动开始，白天可随时调整"
        }
        binding.tvNightStatus.text = status
        binding.tvNightDetail.text = detail

        binding.tvNightHint.text = when {
            !s.permissionsOk -> "注意：权限未齐，至少需要「无障碍服务」和「悬浮窗」，否则拦不住"
            s.locked -> "夜间设置已锁定，明早 ${NightWindow.format(s.endMinute)} 后自动解锁"
            else -> ""
        }

        binding.tvWhitelistSummary.text = if (s.extraAllowed.isEmpty())
            "当前：无额外应用" else "当前额外允许 ${s.extraAllowed.size} 个应用"

        val editable = !s.locked
        binding.switchNight.isEnabled = editable
        binding.switchWechatChatOnly.isEnabled = editable
        binding.switchStrict.isEnabled = editable
        binding.btnSetStart.isEnabled = editable
        binding.btnSetEnd.isEnabled = editable
        binding.btnEditWhitelist.isEnabled = editable
        // During the night window the system settings page is blocked on purpose,
        // so hide that route instead of sending the user into a block overlay.
        binding.btnCheckPermissions.isEnabled = editable

        binding.btnGrace.isEnabled = s.enabled && !s.graceUsed && vm.isInNightWindow()
        binding.tvLockHint.text = when {
            s.graceUsed -> "今晚的一次性放行已用完"
            s.locked -> "要改设置，请等天亮；急事可用上方一次性放行"
            else -> "一次性放行只在守护时段内可用，5 分钟后自动恢复"
        }
    }

    private fun showTimePicker(which: Boolean, currentMinute: Int) {
        TimePickerDialog(
            requireContext(),
            { _, hour, minute ->
                if (!vm.setWindow(which, hour, minute)) {
                    toast(
                        if (vm.state.value.locked) "夜间设置已锁定，明早自动解锁"
                        else "开始与结束时间不能相同"
                    )
                    vm.refresh()
                }
            },
            currentMinute / 60, currentMinute % 60, true
        ).show()
    }

    private fun showWhitelistDialog(current: Set<String>) {
        val ctx = requireContext()
        val pm = ctx.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val launcherPkgs = try {
            pm.queryIntentActivities(launcherIntent, 0)
                .map { it.activityInfo.packageName }.toSet()
        } catch (_: Exception) { emptySet() }
        val essential = EssentialApps.resolvedPackages(ctx)

        // Show launchable apps plus the system phone/SMS handlers. Earlier
        // versions filtered out all system apps, which made it impossible to
        // whitelist the dialer and Messages app.
        val apps = pm.getInstalledApplications(0)
            .filter { it.packageName in launcherPkgs || it.packageName in essential }
            .filter { it.packageName != ctx.packageName }
            .sortedBy { pm.getApplicationLabel(it).toString() }
        val pkgs = apps.map { it.packageName }
        val labels = apps.map { pm.getApplicationLabel(it).toString() }.toTypedArray()
        val checked = BooleanArray(pkgs.size) { pkgs[it] in current }

        MaterialAlertDialogBuilder(ctx)
            .setTitle("夜间额外允许的应用")
            .setMessage("电话、短信、联系人、闹钟、桌面已自动放行，无需勾选。微信单独按「仅聊天」处理。")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("保存") { _, _ ->
                val selected = pkgs.filterIndexed { i, _ -> checked[i] }.toSet()
                if (!vm.setExtraAllowed(selected)) vm.refresh()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showPermissionDialog() {
        val ctx = requireContext()
        val items = arrayOf("无障碍服务（必需）", "显示在其他应用上层（必需）", "使用情况访问（配额功能用）")
        MaterialAlertDialogBuilder(ctx)
            .setTitle("检查必要权限")
            .setItems(items) { _, which ->
                val intent = when (which) {
                    0 -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    1 -> Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${ctx.packageName}")
                    )
                    else -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                }
                try { startActivity(intent) } catch (_: Exception) {
                    toast("该设置页在本机不可用，请到系统设置里手动搜索")
                }
            }
            .show()
    }

    private fun toast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
        tickHandler.post(tickRunnable)
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
