package com.cylldby.blocker.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.cylldby.blocker.BuildConfig
import com.cylldby.blocker.BlockerApp
import com.cylldby.blocker.R
import com.cylldby.blocker.data.model.AppRule
import com.cylldby.blocker.util.EssentialApps
import com.cylldby.blocker.util.NightGuardManager
import com.cylldby.blocker.util.NightWindow
import com.cylldby.blocker.util.QuickBlockManager
import com.cylldby.blocker.util.ScheduleChecker
import com.cylldby.blocker.util.UsageStatsHelper
import com.cylldby.blocker.util.WeChatGuard
import kotlinx.coroutines.*

class BlockerAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var activeRules: List<AppRule> = emptyList()

    private var overlayView: View? = null
    private var dismissJob: Job? = null
    private var rulesJob: Job? = null
    private var guardTickerJob: Job? = null

    @Volatile private var lastWindowScanMs = 0L
    @Volatile private var lastForegroundCheckMs = 0L
    @Volatile private var lastWechatBounceMs = 0L
    @Volatile private var wechatBounceCount = 0
    @Volatile private var lastBlockedPkg = ""
    @Volatile private var lastBlockedMs = 0L

    companion object {
        const val EXTRA_REASON    = "reason"
        const val REASON_QUOTA    = "quota"
        const val REASON_SCHEDULE = "schedule"
        const val REASON_QUICK_BLOCK = "quick_block"
        const val REASON_NIGHT = "night"
        const val REASON_NIGHT_TAMPER = "night_tamper"
        const val REASON_WECHAT = "wechat"
        private const val TAG              = "BlockerService"
        private const val AUTO_DISMISS_MS  = 6_000L
        private const val WINDOW_SCAN_INTERVAL_MS = 500L
        private const val FOREGROUND_CHECK_INTERVAL_MS = 2_000L
        private const val GUARD_TICK_MS = 20_000L
        private const val REBLOCK_SUPPRESS_MS = 8_000L
        private const val WECHAT_BOUNCE_INTERVAL_MS = 600L
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        rulesJob?.cancel()
        rulesJob = serviceScope.launch {
            (application as BlockerApp).repository.allAppRules.collect { all ->
                activeRules = all.filter { it.isActive }
                Log.d(TAG, "Rules synced: ${activeRules.size} active")
            }
        }

        // Event-driven checks alone miss the case where the user is ALREADY
        // inside an app when 23:00 arrives (or when a grace period expires):
        // no window-switch event fires then. A cheap periodic foreground check
        // closes that hole. It only reads the package name of the active window.
        startGuardTicker()

        // Best-effort: bring back the persistent guard notification after a reboot
        // or after Android rebinds the accessibility service. May be rejected by
        // background-FGS rules on Android 12+; the UI also starts this service.
        if (NightGuardManager.isEnabled(this)) {
            try {
                startForegroundService(
                    Intent(this, NightGuardService::class.java).apply {
                        action = NightGuardService.ACTION_START
                    }
                )
            } catch (e: Exception) {
                Log.w(TAG, "Cannot start guard service from background: ${e.message}")
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val type = event.eventType
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg == "android") return

        // After we send the user home, the launcher event must not re-trigger a block.
        if (overlayView != null && EssentialApps.isEssential(this, pkg)) return

        if (NightGuardManager.isInNightWindow(this)) {
            handleNightEvent(event, type, pkg)
        } else {
            handleDayEvent(type, pkg)
        }
    }

    // ------------------------------------------------------------------
    // Night mode: whitelist for calls / SMS, page-level rules for WeChat
    // ------------------------------------------------------------------

    private fun handleNightEvent(event: AccessibilityEvent, type: Int, pkg: String) {
        // Grace period: everything is temporarily usable, but settings/installer
        // stay blocked so it cannot be turned into a permanent bypass.
        if (!NightGuardManager.isNightActive(this)) {
            if (isWindowEvent(type) && NightGuardManager.isTamperPackage(pkg)) {
                launchOverlay(appLabel(pkg), REASON_NIGHT_TAMPER)
            }
            return
        }

        if (pkg == WeChatGuard.PACKAGE) {
            handleWeChatEvent(event, type)
            return
        }

        // A content-change event means the user is still actively using this
        // app. Re-check the foreground package so an app opened BEFORE 23:00
        // cannot simply stay open all night.
        if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (canForegroundCheck()) mainHandler.post { enforceForegroundPackage() }
            return
        }

        if (!isWindowEvent(type)) return
        if (NightGuardManager.isPackageAllowed(this, pkg)) return

        val reason = if (NightGuardManager.isTamperPackage(pkg))
            REASON_NIGHT_TAMPER else REASON_NIGHT
        blockPackage(pkg, reason)
    }

    private fun handleWeChatEvent(event: AccessibilityEvent, type: Int) {
        if (!NightGuardManager.isWechatChatOnly(this)) {
            if (isWindowEvent(type)) blockPackage(WeChatGuard.PACKAGE, REASON_NIGHT)
            return
        }

        var forbidden = false
        var label = "该功能"

        when (type) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (WeChatGuard.isForbiddenActivity(event.className)) {
                    forbidden = true
                    label = "非聊天页面"
                } else if (canScanWindow() &&
                    WeChatGuard.hasForbiddenSelectedTab(safeRoot())
                ) {
                    forbidden = true
                    label = "发现 / 我"
                }
            }

            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val source = event.source
                val labels = mutableListOf<CharSequence>()
                event.text?.let { labels.addAll(it) }
                source?.let { node ->
                    node.text?.let { labels.add(it) }
                    node.contentDescription?.let { labels.add(it) }
                }
                // "发现" / "我" are ordinary chat messages too — only treat a
                // tap as a tab when the node sits in the bottom tab bar.
                val bottomTab = source?.let { isBottomTabNode(it) } ?: false
                val hit = labels.firstOrNull { WeChatGuard.isForbiddenClick(it, bottomTab) }
                if (hit != null) {
                    forbidden = true
                    label = hit.toString()
                }
                try { source?.recycle() } catch (_: Exception) {}
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (canScanWindow() && WeChatGuard.hasForbiddenSelectedTab(safeRoot())) {
                    forbidden = true
                    label = "发现 / 我"
                }
            }
        }

        if (!forbidden) {
            wechatBounceCount = 0
            return
        }
        bounceFromWeChat(label)
    }

    private fun bounceFromWeChat(label: String) {
        if (!canBounce()) return
        wechatBounceCount++
        if (wechatBounceCount >= 3) {
            // Back navigation did not leave the forbidden page — force home.
            launchOverlay("微信", REASON_WECHAT)
            goHome()
            wechatBounceCount = 0
        } else {
            performGlobalAction(GLOBAL_ACTION_BACK)
            showToast("该睡觉了 · 微信夜间仅可聊天（已拦下「$label」）")
        }
    }

    /**
     * Independent of window events: closes the "app was already open when the
     * night started / grace ended" hole. Called by a 20s ticker and on
     * rate-limited content-change events.
     */
    private fun enforceForegroundPackage() {
        if (!NightGuardManager.isNightActive(this)) return
        if (overlayView != null) return
        val pkg = currentForegroundPackage() ?: return
        if (pkg == packageName || pkg == "android") return
        if (NightGuardManager.isPackageAllowed(this, pkg)) return

        if (pkg == WeChatGuard.PACKAGE) {
            if (!NightGuardManager.isWechatChatOnly(this)) {
                blockPackage(pkg, REASON_NIGHT)
            } else if (WeChatGuard.hasForbiddenSelectedTab(safeRoot())) {
                bounceFromWeChat("发现 / 我")
            }
            return
        }
        blockPackage(
            pkg,
            if (NightGuardManager.isTamperPackage(pkg)) REASON_NIGHT_TAMPER else REASON_NIGHT
        )
    }

    private fun blockPackage(pkg: String, reason: String) {
        val now = System.currentTimeMillis()
        if (pkg == lastBlockedPkg && now - lastBlockedMs < REBLOCK_SUPPRESS_MS) return
        lastBlockedPkg = pkg
        lastBlockedMs = now
        launchOverlay(appLabel(pkg), reason)
    }

    private fun startGuardTicker() {
        guardTickerJob?.cancel()
        guardTickerJob = serviceScope.launch {
            while (isActive) {
                delay(GUARD_TICK_MS)
                if (NightGuardManager.isInNightWindow(this@BlockerAccessibilityService)) {
                    withContext(Dispatchers.Main) { enforceForegroundPackage() }
                }
            }
        }
    }

    private fun currentForegroundPackage(): String? {
        safeRoot()?.let { root ->
            val name = root.packageName?.toString()
            try { root.recycle() } catch (_: Exception) {}
            if (!name.isNullOrBlank()) return name
        }
        return try {
            windows.firstOrNull {
                it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isFocused
            }?.root?.let { root ->
                val name = root.packageName?.toString()
                try { root.recycle() } catch (_: Exception) {}
                name
            }
        } catch (_: Exception) { null }
    }

    private fun safeRoot(): AccessibilityNodeInfo? = try {
        rootInActiveWindow
    } catch (_: Exception) { null }

    private fun isBottomTabNode(node: AccessibilityNodeInfo): Boolean {
        if (!node.isClickable) return false
        return try {
            val r = Rect()
            node.getBoundsInScreen(r)
            val h = resources.displayMetrics.heightPixels
            h > 0 && r.height() > 0 && r.top >= h * 0.72f
        } catch (_: Exception) { false }
    }

    // ------------------------------------------------------------------
    // Daytime: the original quota / schedule / Quick Block behaviour
    // ------------------------------------------------------------------

    private fun handleDayEvent(type: Int, pkg: String) {
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val quickBlockActive = QuickBlockManager.isActive(this)
        if (!quickBlockActive && activeRules.none { it.packageName == pkg }) return

        serviceScope.launch {
            if (quickBlockActive && !QuickBlockManager.isAllowed(this@BlockerAccessibilityService, pkg)) {
                val remaining = QuickBlockManager.getRemainingMs(this@BlockerAccessibilityService)
                val mins = (remaining / 60_000).toInt()
                withContext(Dispatchers.Main) {
                    showBlockingOverlay(appLabel(pkg), REASON_QUICK_BLOCK, mins)
                }
                return@launch
            }

            val rule = activeRules.find { it.packageName == pkg } ?: return@launch
            val reason = checkBlocked(rule) ?: return@launch
            if (BuildConfig.DEBUG) Log.d(TAG, "Blocking $pkg — $reason")
            withContext(Dispatchers.Main) { showBlockingOverlay(rule.appName, reason) }
        }
    }

    private fun checkBlocked(rule: AppRule): String? {
        if (ScheduleChecker.isScheduleBlocked(rule)) return REASON_SCHEDULE
        val used = UsageStatsHelper.getTodayUsageMinutes(this, rule.packageName)
        if (BuildConfig.DEBUG) Log.d(TAG, "Usage for ${rule.packageName}: ${used}min / ${rule.dailyLimitMinutes}min limit")
        if (ScheduleChecker.isQuotaExceeded(rule, used)) return REASON_QUOTA
        return null
    }

    // ------------------------------------------------------------------
    // Overlay
    // ------------------------------------------------------------------

    private fun launchOverlay(appName: String, reason: String, remainingMins: Int = -1) {
        serviceScope.launch {
            withContext(Dispatchers.Main) {
                showBlockingOverlay(appName, reason, remainingMins)
            }
        }
    }

    private fun showBlockingOverlay(appName: String, reason: String, remainingMins: Int = -1) {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "No overlay permission — going home directly")
            goHome()
            return
        }

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        removeOverlay(wm)

        @SuppressLint("InflateParams") // null is correct for WindowManager — there is no parent ViewGroup
        val view = LayoutInflater.from(this).inflate(R.layout.overlay_block_warning, null)

        view.findViewById<TextView>(R.id.tvOverlayTitle).text = when (reason) {
            REASON_NIGHT         -> "该睡觉了"
            REASON_NIGHT_TAMPER  -> "夜间守护 · 设置已锁定"
            REASON_WECHAT        -> "微信夜间仅可聊天"
            REASON_SCHEDULE      -> "Schedule Limit"
            REASON_QUICK_BLOCK   -> "Quick Block"
            else                 -> "Daily Limit Reached"
        }
        view.findViewById<TextView>(R.id.tvOverlayMessage).text = when (reason) {
            REASON_NIGHT ->
                "$appName 在 ${NightGuardManager.getWindowText(this)} 期间不可用，仅保留通话与短信"
            REASON_NIGHT_TAMPER ->
                "本时段不能修改权限或卸载应用，明早 ${NightWindow.format(NightGuardManager.getEndMinute(this))} 后可调整"
            REASON_WECHAT ->
                "视频号、朋友圈、小程序、直播、视频通话等已关闭，只保留聊天"
            REASON_SCHEDULE -> "$appName is blocked right now"
            REASON_QUICK_BLOCK -> if (remainingMins > 0) "Quick Block — ${remainingMins}m remaining"
                                   else "Quick Block active"
            else -> "$appName — daily quota reached"
        }

        val btnEmergency = view.findViewById<Button>(R.id.btnEmergencyStop)
        // Never offer a "stop" button during the night window — that is exactly
        // the reflex a sleep guard has to remove.
        val showEmergency = reason == REASON_QUICK_BLOCK &&
            !NightGuardManager.isInNightWindow(this)
        if (showEmergency) {
            btnEmergency?.visibility = View.VISIBLE
            btnEmergency?.setOnClickListener { showEmergencyStopDialog(wm) }
        } else {
            btnEmergency?.visibility = View.GONE
        }

        view.setOnClickListener {
            dismissJob?.cancel()
            val wm2 = getSystemService(WINDOW_SERVICE) as WindowManager
            removeOverlay(wm2)
            goHome()
        }

        // Swallow the hardware back key so the overlay cannot be dismissed by it.
        view.isFocusableInTouchMode = true
        view.requestFocus()
        view.setOnKeyListener { _, keyCode, _ -> keyCode == KeyEvent.KEYCODE_BACK }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )

        wm.addView(view, params)
        overlayView = view

        dismissJob?.cancel()
        dismissJob = serviceScope.launch {
            delay(AUTO_DISMISS_MS)
            withContext(Dispatchers.Main) {
                val wm2 = getSystemService(WINDOW_SERVICE) as WindowManager
                removeOverlay(wm2)
                goHome()
            }
        }
    }

    /**
     * A Service has no Activity token, so a plain AlertDialog.show() throws
     * BadTokenException here. Give the dialog an application-overlay window
     * type (we already hold SYSTEM_ALERT_WINDOW at this point) and a theme.
     */
    private fun showEmergencyStopDialog(wm: WindowManager) {
        dismissJob?.cancel()
        val themed = ContextThemeWrapper(this, android.R.style.Theme_Material_Dialog_Alert)
        val dialog = AlertDialog.Builder(themed)
            .setTitle("Emergency Stop?")
            .setMessage("Stop Quick Block? (10s)")
            .setPositiveButton("Stop (10s)") { _, _ ->
                QuickBlockManager.stop(this)
                removeOverlay(wm)
                goHome()
            }
            .setNegativeButton("Keep blocking", null)
            .create()
        try {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            dialog.show()
        } catch (e: Exception) {
            Log.w(TAG, "Cannot show emergency dialog from service: ${e.message}")
            removeOverlay(wm)
            goHome()
            return
        }

        val btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        btn.isEnabled = false

        val timer = object : CountDownTimer(10_000, 1000) {
            override fun onTick(ms: Long) {
                val s = ms / 1000 + 1
                @SuppressLint("SetTextI18n")
                btn.text = "Stop (${s}s)"
            }
            override fun onFinish() {
                btn.isEnabled = true
                btn.setText(R.string.btn_emergency_stop)
            }
        }
        timer.start()
        dialog.setOnDismissListener { timer.cancel() }
    }

    private fun goHome() {
        try {
            startActivity(
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        } catch (_: Exception) {}
    }

    private fun removeOverlay(wm: WindowManager) {
        overlayView?.let {
            try { wm.removeView(it) } catch (_: Exception) {}
            overlayView = null
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun isWindowEvent(type: Int) =
        type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

    private fun canScanWindow(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastWindowScanMs < WINDOW_SCAN_INTERVAL_MS) return false
        lastWindowScanMs = now
        return true
    }

    private fun canForegroundCheck(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastForegroundCheckMs < FOREGROUND_CHECK_INTERVAL_MS) return false
        lastForegroundCheckMs = now
        return true
    }

    private fun canBounce(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastWechatBounceMs < WECHAT_BOUNCE_INTERVAL_MS) return false
        lastWechatBounceMs = now
        return true
    }

    private fun showToast(message: String) {
        mainHandler.post {
            Toast.makeText(this@BlockerAccessibilityService, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(
            packageManager.getApplicationInfo(pkg, 0)
        ).toString()
    } catch (_: Exception) { pkg }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        removeOverlay(wm)
        rulesJob?.cancel()
        guardTickerJob?.cancel()
        serviceScope.cancel()
    }
}
