package com.cylldby.blocker.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WeChatGuardTest {

    @Test
    fun chatActivitiesStayAllowed() {
        assertThat(WeChatGuard.isForbiddenActivity("com.tencent.mm.ui.LauncherUI")).isFalse()
        assertThat(WeChatGuard.isForbiddenActivity("com.tencent.mm.ui.chatting.ChattingUI")).isFalse()
        assertThat(WeChatGuard.isForbiddenActivity("com.tencent.mm.ui.contact.AddressUI")).isFalse()
    }

    @Test
    fun nonChatActivitiesAreBlocked() {
        assertThat(WeChatGuard.isForbiddenActivity("com.tencent.mm.plugin.finder.ui.FinderHomeUI")).isTrue()
        assertThat(WeChatGuard.isForbiddenActivity("com.tencent.mm.plugin.sns.ui.SnsTimeLineUI")).isTrue()
        assertThat(WeChatGuard.isForbiddenActivity("com.tencent.mm.plugin.appbrand.ui.AppBrandUI")).isTrue()
        assertThat(WeChatGuard.isForbiddenActivity("com.tencent.mm.plugin.webview.ui.tools.WebViewUI")).isTrue()
        assertThat(WeChatGuard.isForbiddenActivity("com.tencent.mm.plugin.voip.ui.VideoActivity")).isTrue()
    }

    @Test
    fun activitiesOfOtherAppsAreIgnored() {
        assertThat(WeChatGuard.isForbiddenActivity("com.android.settings.Settings")).isFalse()
        assertThat(WeChatGuard.isForbiddenActivity(null)).isFalse()
    }

    @Test
    fun forbiddenTabsAndEntriesAreDetected() {
        assertThat(WeChatGuard.isForbiddenLabel("发现")).isTrue()
        assertThat(WeChatGuard.isForbiddenLabel("发现，2 条新消息")).isTrue()
        assertThat(WeChatGuard.isForbiddenLabel("视频号")).isTrue()
        assertThat(WeChatGuard.isForbiddenLabel("我")).isTrue()
        assertThat(WeChatGuard.isForbiddenLabel("小程序")).isTrue()
    }

    @Test
    fun chatTabsAndOrdinaryMessagesAreNotMisread() {
        assertThat(WeChatGuard.isForbiddenLabel("微信")).isFalse()
        assertThat(WeChatGuard.isForbiddenLabel("通讯录")).isFalse()
        assertThat(WeChatGuard.isForbiddenLabel("我们明天几点睡")).isFalse()
        assertThat(WeChatGuard.isForbiddenLabel("今晚的作业写完了吗")).isFalse()
        assertThat(WeChatGuard.isForbiddenLabel("")).isFalse()
        assertThat(WeChatGuard.isForbiddenLabel(null)).isFalse()
    }

    @Test
    fun tappedBottomTabsAreOnlyBlockedWhenTheyReallyAreBottomTabs() {
        assertThat(WeChatGuard.isForbiddenClick("我", isBottomTabNode = true)).isTrue()
        assertThat(WeChatGuard.isForbiddenClick("发现", isBottomTabNode = true)).isTrue()
        assertThat(WeChatGuard.isForbiddenClick("发现 2", isBottomTabNode = true)).isTrue()
        assertThat(WeChatGuard.isForbiddenClick("我", isBottomTabNode = false)).isFalse()
        assertThat(WeChatGuard.isForbiddenClick("发现", isBottomTabNode = false)).isFalse()
    }

    @Test
    fun tappedChatMessagesAreNeverMistakenForFeatureEntries() {
        // These are all words that can legitimately be a whole chat message.
        assertThat(WeChatGuard.isForbiddenClick("设置", isBottomTabNode = true)).isFalse()
        assertThat(WeChatGuard.isForbiddenClick("视频", isBottomTabNode = true)).isFalse()
        assertThat(WeChatGuard.isForbiddenClick("收藏", isBottomTabNode = true)).isFalse()
        assertThat(WeChatGuard.isForbiddenClick("支付", isBottomTabNode = true)).isFalse()
        assertThat(WeChatGuard.isForbiddenClick("发现明天去哪儿玩", isBottomTabNode = true)).isFalse()
        assertThat(WeChatGuard.isForbiddenClick("我们明天几点睡", isBottomTabNode = true)).isFalse()
    }

    @Test
    fun explicitFeatureEntriesAreStillBlockedOnTap() {
        assertThat(WeChatGuard.isForbiddenClick("视频号")).isTrue()
        assertThat(WeChatGuard.isForbiddenClick("朋友圈")).isTrue()
        assertThat(WeChatGuard.isForbiddenClick("小程序")).isTrue()
        assertThat(WeChatGuard.isForbiddenClick("看一看 3")).isTrue()
    }
}
