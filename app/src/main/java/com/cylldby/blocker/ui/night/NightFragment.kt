package com.cylldby.blocker.ui.night

import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
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
import com.cylldby.blocker.util.UnlockManager
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

        binding.btnGenerateMaster.setOnClickListener { showGenerateMasterDialog() }
        binding.btnSetEmergency.setOnClickListener { showSetEmergencyDialog() }
        binding.btnUnlock.setOnClickListener { showUnlockDialog() }
        binding.btnUnlockDuration.setOnClickListener {
            if (!vm.cycleUnlockMinutes()) toast("夜间不能修改解锁时长，请白天再改")
        }

        binding.btnCheckPermissions.setOnClickListener { showPermissionDialog() }

        vm.refresh()
    }

    private fun render(s: NightUiState) {
        val ctx = requireContext()
        binding.switchNight.isChecked = s.enabled
        binding.switchWechatChatOnly.isChecked = s.chatOnly
        binding.switchStrict.isChecked = s.strict

        val window = "${NightWindow.format(s.startMinute)} – ${NightWindow.format(s.endMinute)}"
        binding.btnSetStart.text = "开始 ${NightWindow.format(s.startMinute)}"
        binding.btnSetEnd.text = "结束 ${NightWindow.format(s.endMinute)}"

        val (status, detail) = when {
            !s.enabled -> "睡眠守护未启用" to "先设好时段、微信规则和两种解锁密码，再打开上面的开关"
            s.unlockActive -> {
                val mins = (NightGuardManager.getUnlockRemainingMs(ctx) / 60_000L).toInt() + 1
                "已临时解锁" to "约 ${mins} 分钟后自动恢复守护 · 设置仍然锁定"
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

        // Passwords can only be created/changed in daylight. At night the ONLY
        // usable control on this page is "unlock with a password".
        binding.btnGenerateMaster.isEnabled = editable
        binding.btnSetEmergency.isEnabled = editable
        binding.btnUnlockDuration.isEnabled = editable
        binding.btnUnlock.isEnabled = s.enabled && vm.isInNightWindow() &&
            !s.unlockActive && !UnlockManager.isThrottled(ctx) &&
            (UnlockManager.hasMaster(ctx) || UnlockManager.hasEmergency(ctx))

        binding.tvMasterState.text = when {
            UnlockManager.isMasterSpent(ctx) -> "① 纸条密码：已用完，白天必须重新生成"
            UnlockManager.hasMaster(ctx) -> "① 纸条密码：已设定（放在别的房间）"
            else -> "① 纸条密码：尚未设定"
        }

        binding.tvEmergencyState.text = if (UnlockManager.hasEmergency(ctx)) {
            val last = UnlockManager.lastEmergencyUseText(ctx)
            "② 紧急密码：已由亲友设定 · 累计使用 ${UnlockManager.emergencyUseCount(ctx)} 次" +
                if (last.isBlank()) "" else " · 最近 $last"
        } else {
            "② 紧急密码：尚未设定"
        }

        binding.btnUnlockDuration.text = "解锁时长：${UnlockManager.unlockLabel(ctx)}（点击切换）"

        binding.tvLockHint.text = when {
            UnlockManager.isThrottled(ctx) ->
                "密码连续试错过多，${UnlockManager.throttleRemainingMinutes(ctx)} 分钟后才能再试"
            s.locked -> "守护时段内不能设定或修改密码，只能输入密码解锁"
            s.unlockActive -> "解锁期间设置仍然锁定，到点自动恢复守护"
            else -> "白天设定好两种密码：纸条放远一点，紧急密码交给亲友"
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

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    /** The 30-char note is shown exactly once and only saved after the owner
     *  confirms it has been copied. Cancelling leaves no usable password behind. */
    private fun showGenerateMasterDialog() {
        val ctx = requireContext()
        var raw = UnlockManager.generateMasterRaw()

        fun show() {
            val code = TextView(ctx).apply {
                text = UnlockManager.displayMaster(raw)
                textSize = 20f
                typeface = Typeface.MONOSPACE
                setTextIsSelectable(true)
            }
            val note = TextView(ctx).apply {
                text = "现在用笔抄在纸上，放到另一个房间。关掉这个窗口后不会再显示；夜里用一次立即作废，白天必须重新生成新纸条。"
                textSize = 14f
            }
            val box = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(16), dp(20), 0)
                addView(code)
                addView(note)
            }
            MaterialAlertDialogBuilder(ctx)
                .setTitle("抄下这张纸条")
                .setView(box)
                .setPositiveButton("我已抄好，启用") { _, _ ->
                    toast(
                        if (vm.saveMaster(raw)) "纸条密码已生效，请把纸放到别的房间"
                        else "夜间不能设定密码，请白天再来"
                    )
                }
                .setNeutralButton("换一张") { _, _ ->
                    raw = UnlockManager.generateMasterRaw()
                    show()
                }
                .setNegativeButton("取消", null)
                .show()
        }
        show()
    }

    private fun showSetEmergencyDialog() {
        val ctx = requireContext()
        fun field(hint: String) = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            this.hint = hint
            isSingleLine = true
        }
        val first = field("6 位数字")
        val second = field("再输一次")
        val tip = TextView(ctx).apply {
            text = "请交给亲友输入并由他 / 她记住。App 不保存明文，也不会在机主界面显示；每次使用都会记录次数与时间。"
            textSize = 14f
        }
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), 0)
            addView(tip)
            addView(first)
            addView(second)
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle("设定 6 位紧急密码")
            .setView(box)
            .setPositiveButton("保存") { _, _ ->
                val a = first.text.toString().trim()
                val b = second.text.toString().trim()
                val message = when {
                    a != b -> "两次输入不一致"
                    !UnlockManager.isEmergencyShape(a) -> "必须是 6 位数字"
                    vm.saveEmergency(a) -> "紧急密码已设定，请让亲友保管"
                    else -> "夜间不能改密码，请白天再来"
                }
                toast(message)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showUnlockDialog() {
        val ctx = requireContext()
        val input = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "30 位纸条密码 或 6 位紧急密码"
            isSingleLine = true
        }
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle("夜间解锁")
            .setMessage("解锁只临时放行应用，设置仍然锁定。")
            .setView(box)
            .setPositiveButton("解锁") { _, _ ->
                val message = when (vm.unlock(input.text.toString())) {
                    UnlockManager.Result.MASTER ->
                        "解锁成功。这张纸条已作废，明天白天请重新生成"
                    UnlockManager.Result.EMERGENCY ->
                        "已用紧急密码解锁，本次使用已记录"
                    UnlockManager.Result.WRONG ->
                        if (UnlockManager.isThrottled(ctx))
                            "连续试错过多，请 ${UnlockManager.throttleRemainingMinutes(ctx)} 分钟后再试"
                        else "密码不对，还可尝试 ${UnlockManager.attemptsLeft(ctx)} 次"
                    UnlockManager.Result.THROTTLED ->
                        "连续试错过多，请 ${UnlockManager.throttleRemainingMinutes(ctx)} 分钟后再试"
                    UnlockManager.Result.NO_KEYS ->
                        "还没有设定任何解锁密码，请白天先设定"
                    UnlockManager.Result.NOT_NIGHT ->
                        "当前不在守护时段，不需要解锁"
                }
                toast(message)
            }
            .setNegativeButton("取消", null)
            .show()
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
