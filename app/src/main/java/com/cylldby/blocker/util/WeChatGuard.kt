package com.cylldby.blocker.util

import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

/**
 * "WeChat, chat only" enforcement.
 *
 * WeChat puts chat, Discover (视频号/朋友圈/看一看...), Me, mini-programs and
 * web articles inside a single app, so package-level blocking is useless here.
 * Three complementary signals are used:
 *
 *  1. Activity class name from TYPE_WINDOW_STATE_CHANGED. Non-chat features
 *     live in their own activities under com.tencent.mm.plugin.* — a keyword
 *     blacklist catches them (视频号 finder, 朋友圈 sns, 小程序 appbrand,
 *     公众号/外链 webview, 视频通话 voip, 直播 live, 视频播放 video ...).
 *  2. A *selected* forbidden tab in the window tree (发现 / 我 share LauncherUI
 *     with the chat list, so their selected state is the only reliable signal).
 *  3. A tapped node whose label is a non-chat feature entry. This path is
 *     deliberately stricter than (2): "我", "设置", "视频", "收藏" etc. are all
 *     perfectly normal chat messages, so a tap is only treated as a tab when
 *     the node physically sits in the bottom tab bar.
 *
 * No chat content is stored, sent anywhere, or used for anything else.
 */
object WeChatGuard {

    const val PACKAGE = "com.tencent.mm"

    private val FORBIDDEN_ACTIVITY_TOKENS = listOf(
        "finder",                       // 视频号
        "sns",                          // 朋友圈
        "appbrand",                     // 小程序
        "webview", "webpage", "web",    // 公众号文章 / H5 / 外部链接
        "voip", "talkroom", "multitalk",// 语音通话 / 视频通话
        "live", "video", "player", "sight", // 直播 / 视频播放 / 小视频
        "wallet", "pay", "game", "emoji",
        "shake", "scan", "qrcode", "search",
        "setting", "exdevice", "favorite", "nearby"
    )

    /** Used when scanning *selected* nodes in the window tree. */
    private val FORBIDDEN_LABELS = setOf(
        "发现", "视频号", "朋友圈", "看一看", "直播", "小程序",
        "扫一扫", "搜一搜", "摇一摇", "游戏", "购物", "频道",
        "视频", "我", "附近的人", "公众号", "表情", "收藏", "设置", "钱包", "支付"
    )

    /** Bottom tabs. Also common as standalone chat messages — see isForbiddenClick. */
    private val BOTTOM_TAB_LABELS = setOf("发现", "我")

    /** Explicit feature entries that are safe to match on a tap. */
    private val CLICK_FORBIDDEN_LABELS = setOf(
        "视频号", "朋友圈", "看一看", "直播", "小程序",
        "扫一扫", "搜一搜", "摇一摇", "游戏", "购物", "频道",
        "附近的人", "公众号", "钱包"
    )

    /** Tabs that are fine at night — kept here for documentation / future whitelist mode. */
    val ALLOWED_LABELS = setOf("微信", "聊天", "通讯录", "联系人")

    fun isForbiddenActivity(className: CharSequence?): Boolean {
        val name = className?.toString()?.lowercase() ?: return false
        if (!name.startsWith(PACKAGE)) return false
        return FORBIDDEN_ACTIVITY_TOKENS.any { name.contains(it) }
    }

    /**
     * Loose label matching, for *selected* tabs only. A single-character label
     * such as "我" must match exactly, otherwise a chat message starting with
     * "我" would be mistaken for the Me tab.
     */
    fun isForbiddenLabel(text: CharSequence?): Boolean {
        val s = text?.toString()?.trim() ?: return false
        if (s.isEmpty() || s.length > 20) return false
        return FORBIDDEN_LABELS.any { label ->
            s == label ||
                (label.length >= 2 && s.startsWith(label) && s.length <= label.length + 8)
        }
    }

    /**
     * Strict matching for TYPE_VIEW_CLICKED. Tapping a chat bubble produces an
     * event whose text IS the message, so words like "我" / "发现" / "视频" must
     * never block a normal conversation.
     *
     * [isBottomTabNode] is computed by the accessibility service from the
     * tapped node's screen bounds; it is only required for the two bottom tabs.
     */
    fun isForbiddenClick(text: CharSequence?, isBottomTabNode: Boolean = false): Boolean {
        val s = text?.toString()?.trim() ?: return false
        if (s.isEmpty() || s.length > 20) return false

        if (BOTTOM_TAB_LABELS.any { s == it || hasOnlyBadgeSuffix(s, it) }) {
            return isBottomTabNode
        }
        return CLICK_FORBIDDEN_LABELS.any { s == it || hasOnlyBadgeSuffix(s, it) }
    }

    /** Accept a short numeric badge such as "发现 2" / "视频号，3", not free text. */
    private fun hasOnlyBadgeSuffix(s: String, label: String): Boolean {
        if (s == label) return true
        if (!s.startsWith(label)) return false
        val tail = s.substring(label.length)
        if (tail.length > 6) return false
        return tail.all { c ->
            c.isDigit() || c.isWhitespace() || c in "，,、·:：.。()（）"
        }
    }

    /**
     * Fallback: walk the active window tree looking for a *selected* forbidden tab.
     * Bounded at 400 nodes so it stays cheap on a slow device.
     */
    fun hasForbiddenSelectedTab(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (!queue.isEmpty() && visited < 400) {
            val node = queue.poll() ?: break
            visited++
            if (node.isSelected &&
                (isForbiddenLabel(node.text) || isForbiddenLabel(node.contentDescription))
            ) return true
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return false
    }
}
