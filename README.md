# Blocker · 睡眠守护版

> **中文说明（本次改造）**：目标是「晚上 23:00 之后手机只剩通话和短信，微信只能聊天，视频号 / 朋友圈 / 小程序 / 直播等一律打不开」。原项目只能按包名整包封杀，做不到定时自动生效，也做不到微信内部的页面级放行，因此新增了「守护」页与一套页面级判定。下面的英文部分是原项目文档，仍然有效。

## 一、改造后的行为

| 项目 | 默认行为 |
|---|---|
| 生效时段 | 每天 23:00 – 次日 07:00，可在「守护」页改，跨天自动处理 |
| 触发方式 | 不需要手动点开始，也不依赖闹钟；每次切换应用时按当前时间实时判定，**到点 / 解锁窗口结束时还会主动复核当前前台应用**，重启手机后依然有效 |
| 自动放行 | 系统电话 / 来电界面 / 短信 / 联系人 / 闹钟 / 桌面 / 系统界面（按 Intent 自动识别，兼容小米、华为、OPPO、vivo、三星等不同包名） |
| 微信 | 默认「仅聊天」：保留会话列表与聊天页；视频号、朋友圈、看一看、小程序、公众号 / 外链、直播、视频播放、语音视频通话、发现页与「我」页全部拦下。也可切换为「夜间完全禁用微信」 |
| 其它应用 | 一律拦截，弹全屏提示并送回桌面；重复打开会重复拦截 |
| 防绕过 | 守护时段内锁定设置（不能关守护、改时间、改白名单），并屏蔽系统设置、安装器、应用市场等可用来关权限 / 卸载的入口 |
| 急事通道 | **两种密码解锁**：30 位纸条密码（一次性、放别的房间）与 6 位亲友紧急密码（可重复、每次留痕）。夜间不能设定或改密码，解锁只临时放行应用，设置仍然锁定 |
| 第二层 | 若在「Sites」页开启过网站拦截 VPN，夜间会额外屏蔽短视频 / 视频 / 资讯域名（DNS 层） |

## 一之二、两种解锁方式（防「半夜的自己」）

夜间配置全部锁死，**唯一还能按的按钮是「输入密码解锁」**。密码只能在白天设定，且只保存加盐 SHA-256 哈希——App 不存明文、界面不回显。

| | 30 位纸条密码 | 6 位紧急密码 |
|---|---|---|
| 谁掌握 | 机主自己 | 亲友 |
| 怎么来 | App 随机生成，**只显示一次**，抄在纸上 | 白天由亲友输入两次并记住 |
| 放哪里 | 另一个房间的纸上（想用就得起身走过去） | 只记在亲友脑子里 |
| 用几次 | **一次**：验证成功瞬间销毁哈希，白天必须重新生成新纸条 | 可重复使用 |
| 留痕 | 状态显示「已用完，白天必须重新生成」 | 累计次数 + 最近使用时间，白天可见 |
| 夜里能改吗 | 不能 | 不能 |

- 解锁只**临时放行应用**（默认 30 分钟，可切换 15 / 30 / 60 分钟 / 到天亮），**设置仍然锁定**，所以解锁后也无法削弱明晚的守护。
- 30 位密码按 5 位一组显示、去掉了 `0/O/1/I` 等易混淆字符，手抄不容易出错；输入时空格和连字符都会被忽略。
- 连续 5 次输错锁定 10 分钟，避免半夜硬猜 6 位数字。
- 原来的「无密码一键放行 5 分钟」**已移除**：那等于把钥匙放在床头，与这套设计冲突。
- 边界：Android 不允许第三方 App 阻止机主白天清除 App 数据或手动关掉无障碍服务。想更硬，可配合系统「数字健康」让家人设一个你不知道的 PIN。

## 二、实现逻辑（为什么这样做）

1. **时间判定放在无障碍服务里，而不是用 AlarmManager。** 每次窗口切换事件直接读当前时间，重启、杀后台、时区变化都不会漏；前台服务只负责常驻通知和抗杀，不负责拦截。**纯靠事件会有个漏洞：22:55 打开抖音一直刷到 23:30，中间没有任何窗口切换事件。** 因此服务内另有一个 20 秒的前台复核（只读取当前窗口的包名，不读内容），界面变化事件也会限流触发一次；这样"到点时已经开着的应用"和"宽限期结束后继续用"都会被收回桌面。
2. **基础通信用「Intent 反查 + 系统包关键字」双保险。** 国产 ROM 的拨号 / 短信包名各不相同，硬编码必然漏；先解析 `ACTION_DIAL`、`sms:`、`CATEGORY_HOME`、`AlarmClock.ACTION_SHOW_ALARMS` 与 `ACTION_SET_ALARM` 的真实处理者，再对系统包做保守关键字兜底，保证来电界面能接起、闹钟能响。
3. **微信按「页面」而不是按「应用」判断。** 微信四个页签共用 `LauncherUI`，只看包名无法区分。因此同时用：窗口事件里的 Activity 类名黑名单（`finder` 视频号、`sns` 朋友圈、`appbrand` 小程序、`webview` 公众号、`voip` 通话、`live/video` 视频…），以及被点击 / 被选中节点的标签精确匹配（「发现」「我」「视频号」…）。命中后自动返回聊天列表并提示，连续三次仍不退出就送回桌面。
4. **标签匹配刻意做得很紧，且「点击」与「选中态」走两套规则。** 点一条聊天消息时，事件文本就是消息本身，所以「我」「设置」「视频」「收藏」这类常见词一律不能在点击路径上拦截；「发现」「我」两个底部页签只有在节点确实位于屏幕底部 28% 的页签栏时才算命中，其余功能入口要求文本完全相等或只带数字角标。窗口树里的「已选中」节点则可以用较宽的标签匹配，因为聊天消息不会被标记为 selected。App 不读取聊天内容、不联网、不上传任何数据。
5. **防绕过的重点是「半夜的自己」。** 10 秒紧急停止按钮在守护时段隐藏；设置页、安装器、应用市场被拦截；配置开关与两种密码的设定入口在夜间全部置灰。急事只能凭纸条或找亲友要密码，且解锁期间仍锁定配置。详见下一节。

## 三、装到手机上

如果不方便在本机安装 Java / Android SDK / Gradle，下面两条路径都能拿到可安装的 APK：

| 路径 | 做法 |
|---|---|
| **A. 云端出 APK（推荐）** | 把本目录推到 GitHub 仓库 → Actions 里运行 `Build debug APK` → 下载 `blocker-night-guard-apk` 里的 `app-debug.apk` → 传到手机安装。流水线只用 GitHub 官方组件：装 JDK 17、复用构建机预装的 Android 34 SDK、固定下载 Gradle 8.6，**不依赖 wrapper jar** |
| **A′. 两个坑** | ① GitHub 网页上传会跳过 `.github` 等点开头目录，若 Actions 没触发，用 **Add file → Create new file** 补建 `.github/workflows/build-apk.yml`；② 流水线**不要引入第三方 action**（GitHub 已强制 Node 24，旧版第三方组件会直接失败） |
| **B. Android Studio** | 用 Ladybug 或更新版本打开本目录；若提示缺 `gradle-wrapper.jar`，IDE 会补齐，或先执行 `gradle wrapper --gradle-version 8.6 --distribution-type bin`。之后连手机点 Run，或 `./gradlew assembleDebug` |
| **C. 已装好 JDK 17 + Android 34 SDK 的机器** | 在项目目录执行 `gradle assembleDebug`，产物在 `app/build/outputs/apk/debug/app-debug.apk`（需 Gradle 8.6 / AGP 8.3.2） |

安装后按引导授予**无障碍服务**和**显示在其他应用上层**两项必需权限（「使用情况访问」只给白天的配额功能用，可选）。然后：

1. 打开 App 的「守护」页 → **先设好起止时间、微信规则和两种解锁密码，再打开「每天自动开启守护」总开关**（打开后若正处于时段内，设置会立刻锁定）。
2. 建议白天先试一次：把开始时间临时设到当前时间前一小时，确认电话、短信、微信聊天可用，短视频 / 视频号会被拦下，再改回 23:00。
3. 国产 ROM 请把本 App 加入电池优化白名单 / 允许后台运行，避免系统杀掉前台服务。

> 每次重装或更新 App，Android 会**自动关闭无障碍服务**，必须去「设置 → 无障碍」重新打开一次；「守护」页的「检查必要权限」按钮可直接跳转。

## 四、能防住什么，防不住什么（请务必看清）

| 情况 | 结果 |
|---|---|
| 半夜手痒点开抖音 / B 站 / 视频号 / 朋友圈 | 拦住，弹提示并回桌面 |
| **23:00 前就打开抖音、到点后一直不切应用** | 拦住。20 秒前台复核会收回桌面，这是纯事件方案最容易漏的一种情况 |
| 从「设置」里关无障碍、关悬浮窗、卸载 App | 拦住（设置 / 安装器入口被屏蔽） |
| 系统杀掉后台进程 | 无障碍服务由系统绑定，开机自启前台通知；仍建议在电池优化里加白名单 |
| **手动去系统设置里关掉无障碍服务**（白天或绕过入口） | **防不住**。Android 不允许第三方应用阻止用户关闭无障碍 |
| **用 ADB / 电脑、恢复出厂、另一台设备、直接不碰手机以外的方式** | 防不住，也不在自用的设计范围内 |
| 微信未来大版本改了 Activity 命名或页签文案 | 可能漏判，需要更新 `WeChatGuard.kt` 里的关键词表；这是页面级方案固有的维护成本 |
| 通知栏里直接看到微信消息预览、锁屏直接回复 | 不属于「打开微信页面」，本方案不拦截；可在系统通知设置里关掉微信锁屏预览 |
| 微信语音 / 视频通话来电 | `voip` 类页面会被拦，但来电弹窗属于系统通知层，可能仍会响起；系统电话不受影响 |
| 网站拦截 VPN | 只作第二层。Chrome 的「安全 DNS」/ 系统「私人 DNS」开启时会绕过 DNS 拦截；微信大量走 IP 长连接，也不依赖这些域名。核心拦截始终由无障碍服务完成 |

想再加固一层，可考虑：让家人给「数字健康 / 屏幕使用时间」设置一个自己不知道的 PIN，或用 `adb shell dpm set-device-owner` 把本 App 设为设备所有者（后者门槛高、需未登录账号的设备，且会影响整机管理策略，请谨慎）。

## 五、本次同时修掉的原有缺陷

- 「快速屏蔽 / Focus」的例外应用列表原来过滤掉了所有系统应用，导致**电话、短信根本选不进白名单**；现在改为「可启动应用 + 系统通信处理者」，并让例外列表在 App 重启后保留。
- VPN 的 DNS 上游原来写死 `8.8.8.8`，在国内基本不可达，会让**所有网站解析超时**；现在改用阿里 `223.5.5.5` 与腾讯 `119.29.29.29` 双备，全失败才返回 SERVFAIL。
- 引导流程原来强制要求「使用情况访问」权限才让进入主界面，而睡眠守护并不需要它；现在只要求无障碍 + 悬浮窗两项。
- 补上了缺失的 `gradlew` / `gradlew.bat` 启动脚本和云端构建流水线；`gradle-wrapper.jar` 未随压缩包提供，由 Android Studio 自动补齐（云端构建不需要它）。
- 新增纯 JVM 单测：跨午夜时间窗、剩余时长、微信 Activity / 页签判定与聊天文本误判防护。

## 六、第二轮复查又修掉的问题

逐文件复查后又发现并修正了下面这些（都进过代码，不是只在文档里写）：

| 问题 | 后果 | 处理 |
|---|---|---|
| 拦截只挂在窗口切换事件上 | **22:55 打开抖音刷到 23:30 完全不拦**，核心需求失效 | 无障碍服务内加 20 秒前台复核 + 界面变化事件限流复核，并做 8 秒重复拦截抑制 |
| 「点击文本」用了同一套宽标签 | 点开内容为「我」「发现」「设置」「视频」的**聊天消息会被当成页签拦下**，连天都聊不了 | 点击路径换成严格匹配：两个底部页签要求节点位于屏幕底部页签栏，其余入口要求文本完全相等 / 只带数字角标 |
| 紧急停止对话框由 Service 直接 `AlertDialog.show()` | 无 Activity token，点一下就 `BadTokenException` 崩溃 | 补 `ContextThemeWrapper` + `TYPE_APPLICATION_OVERLAY` 窗口类型，并加失败兜底 |
| 「每晚一次」按自然日计数 | 23:50 用一次、00:10 又能用一次 | 改成「中午到中午」为一个睡眠夜，跨午夜同属一晚 |
| 开机广播里启动 VPN 没有异常保护 | Android 12+ 拒绝后台启动 FGS 时会拖垮整个广播接收器，连守护通知也恢复不了 | 包 try/catch；顺带刷新电话 / 短信处理者缓存 |
| `setWindow` 允许起止时间相同 | 守护被静默置为永不生效 | Manager 层也做范围与零长度校验，界面按「已锁定 / 时间相同」给不同提示 |
| 云端流水线必须先生成 wrapper jar 才能构建 | 生成步骤一旦失败就拿不到 APK | 构建直接用 `gradle assembleDebug`，不依赖 wrapper jar |

单测同步补充：跨午夜「同一睡眠夜」、底部页签位置判定、聊天消息文本不被误拦。

---

**A no-nonsense Android app that actually keeps you off your phone.**

Blocker lets you set hard limits on apps and websites — with deliberate friction to make undoing them feel like a conscious choice, not a reflex. It runs entirely on-device with no account, no cloud sync, no tracking.

---

## Features

### 🌙 Sleep Guard (night mode)
Automatically locks the phone every night (default 23:00–07:00). Only calls, in-call UI, SMS, contacts, alarms, the launcher and system UI stay available. WeChat runs in **chat-only** mode: 视频号 / 朋友圈 / 小程序 / 公众号 / 直播 / 视频通话 pages and the 发现 / 我 tabs are rejected at the activity and UI-node level. Settings, the package installer and app stores are blocked during the window, config edits are locked, and the only way out is one of two passwords: a single-use 30-character paper note (kept in another room) or a reusable 6-digit emergency PIN held by a relative. Both can only be configured in daylight. See the Chinese section at the top for the full rationale and limits.

### 📱 App Blocking
Block any installed app after a **daily usage quota** (e.g. Instagram after 30 minutes). The app monitors foreground usage via Android's `UsageStatsManager` and triggers a blocking overlay the moment the quota is hit.

### 🌐 Website Blocking
Block websites across **all browsers** — Chrome, Firefox, Safari, DuckDuckGo, anything — using a local VPN that intercepts DNS queries. Blocked domains return `NXDOMAIN`; all other traffic passes through unmodified. Supports **subdomain wildcarding** (blocking `reddit.com` also blocks `old.reddit.com`, `www.reddit.com`, etc.) and optional **daily quotas** per domain.

### 🗓 Schedule Blocking
Block apps during specific **time windows** and **days of the week** — independently of any daily quota. Useful for keeping work apps off during evenings, or social media off during mornings.

### ⏱ Quick Block (Focus Mode)
Hit **Start Blocking** and everything is blocked for a chosen duration (15m, 30m, 1h, 2h, or custom). Optionally whitelist specific apps (e.g. Maps, Phone) as exceptions. A persistent notification counts down the remaining time. An **Emergency Stop** requires a 10-second countdown confirmation so it can't be tapped away by accident.

### 🔒 Friction on Disable
Disabling any active rule — whether a daily quota, a schedule, or a site block — shows a **5-second countdown confirmation dialog**. You can cancel during the countdown. This small amount of friction is enough to break the "disable and forget" reflex.

### 🔔 Site-Blocked Notifications
When the VPN intercepts a DNS query for a blocked site, a system notification appears: _"reddit.com is blocked by Blocker"_. Rate-limited to once per domain per 30 seconds to avoid noise. Lets you know the block is working rather than leaving you staring at a cryptic browser error.

---

## Screenshots

> _Add screenshots here once the app is running on device._

---

## Architecture

Blocker is a pure native Android app — no cross-platform framework, no Compose, no external analytics or crash reporting.

```
┌─────────────────────────────────────────────────────┐
│                     UI Layer                        │
│  Apps · Sites · Schedule · Focus  (Fragments/MVVM)  │
└────────────────────┬────────────────────────────────┘
                     │ StateFlow / LiveData
┌────────────────────▼────────────────────────────────┐
│                  Data Layer                         │
│  Room DB  ·  AppRule  ·  SiteRule  ·  SiteUsage    │
│  BlockerRepository  (single source of truth)        │
└──────────┬──────────────────────────┬───────────────┘
           │ Flow<List<AppRule>>       │ Flow<List<SiteRule>>
┌──────────▼──────────┐   ┌───────────▼───────────────┐
│ AccessibilityService│   │     VPN Service            │
│                     │   │                            │
│ Detects foreground  │   │ TUN interface intercepts   │
│ app changes via     │   │ DNS (UDP port 53).         │
│ TYPE_WINDOW_STATE_  │   │ Blocked domains → NXDOMAIN │
│ CHANGED events.     │   │ Others → forwarded to      │
│                     │   │ 8.8.8.8 via protected      │
│ Checks quota +      │   │ DatagramSocket.            │
│ schedule + Quick    │   │                            │
│ Block state.        │   │ Quota domains tracked by   │
│                     │   │ minute-of-day stamps.      │
│ Shows WindowManager │   │                            │
│ overlay on block.   │   │ All other traffic          │
└─────────────────────┘   │ (HTTP/HTTPS) is untouched. │
                          └────────────────────────────┘
```

**Key design decisions:**
- Rules are observed via `Flow` — both services always have the latest state without polling or manual refresh.
- The VPN only routes a `/24` subnet containing our fake DNS server through the tunnel. All other traffic (actual HTTP/HTTPS) uses the real network.
- DNS forwarding is async — each packet is handled in its own coroutine so one slow upstream lookup doesn't block others.
- Blocking uses a full-screen `WindowManager` overlay (`TYPE_APPLICATION_OVERLAY`) rather than launching an activity, so the blocked app's back-stack is untouched.

**Tech stack:**
- Kotlin · Coroutines · StateFlow
- Room (SQLite) · DAO pattern
- AccessibilityService · VpnService
- ViewBinding · Fragment + ViewModel
- Material 3 · iOS-inspired glass aesthetic
- Robolectric · Espresso (tests)

---

## Permissions

| Permission | Why |
|---|---|
| `BIND_ACCESSIBILITY_SERVICE` | Detect which app is in the foreground |
| `PACKAGE_USAGE_STATS` | Read per-app daily usage time |
| `BIND_VPN_SERVICE` | Create the local DNS-intercept tunnel |
| `SYSTEM_ALERT_WINDOW` | Show the blocking overlay above all apps |
| `POST_NOTIFICATIONS` | Site-blocked and Quick Block countdown notifications |
| `FOREGROUND_SERVICE` | Keep the VPN and Quick Block services alive |
| `RECEIVE_BOOT_COMPLETED` | Restart services after reboot |

The VPN service forwards non-blocked DNS queries to `223.5.5.5` / `119.29.29.29` (mainland-China reachable) using an OS-protected socket that bypasses the tunnel. `INTERNET` is declared for this forwarding.

---

## Known Limitations

**DNS over HTTPS (DoH):** If Chrome's _"Use secure DNS"_ setting is on, DNS queries are sent over HTTPS and bypass our VPN tunnel entirely. Website blocking will silently fail for those requests. Fix: in Chrome go to `Settings → Privacy and security → Security → Use secure DNS → Off`. Also set Android's Private DNS (`Settings → Network → Private DNS`) to **Off**.

**HTTPS block pages:** Because we block at the DNS level (not HTTP), the browser shows its built-in "DNS_PROBE_FINISHED_NXDOMAIN" error rather than a custom "blocked" page. A system notification appears instead to confirm the block is active.

**App blocking requires accessibility:** Android disables the accessibility service on every reinstall (a system security measure). After each update you need to re-enable it once in `Settings → Accessibility → Blocker App Monitor`.

**WeChat page detection is version-sensitive:** it relies on WeChat activity class names and tab labels. A major WeChat redesign may require refreshing the keyword tables in `WeChatGuard.kt`. It also cannot stop notification previews or the system-level incoming-call popup.

**Self-bypass:** no third-party app can prevent a user from manually switching the accessibility service off in system settings. Sleep Guard raises the friction (locked config, blocked settings/installer, hidden emergency stop, password-gated unlock) but is not a tamper-proof parental-control lock.

---

## Installing on Your Own Phone

### Option A — Build from source (Android Studio)

1. Clone this repository:
   ```bash
   git clone https://github.com/cylldby/blocker.git
   cd blocker
   ```
2. Open in **Android Studio** (Ladybug or later recommended).
3. Connect your Android phone via USB with **USB Debugging** enabled.
4. Click the green **Run** button (or press `Shift+F10`).
5. The app installs and launches. Follow the in-app onboarding to grant permissions.

### Option B — Build APK from the command line

```bash
git clone https://github.com/cylldby/blocker.git
cd blocker
./gradlew assembleDebug
```

The APK is produced at:
```
app/build/outputs/apk/debug/app-debug.apk
```

Transfer it to your phone (Google Drive, USB, etc.), tap it on the phone, and allow installation from unknown sources when prompted.

### Option C — Install via ADB

If your phone is connected via USB or wireless ADB:

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### After installing

1. Open the app — the onboarding screen will walk you through all required permissions.
2. Grant: **Usage Access**, **Accessibility Service**, **Display Over Other Apps**, **VPN**.
3. Done. Add your first rule in the **Apps** or **Sites** tab.

> **Note:** This is a debug build signed with the default Android debug keystore. It is not distributed through the Play Store and must be sideloaded. Android may show a warning about installing from unknown sources — this is expected.

---

## Running Tests

**Unit tests** (JVM, no device needed):
```bash
./gradlew test
```
Covers: DNS query parsing, NXDOMAIN generation, subdomain matching, schedule logic (overnight windows, day-of-week mapping, quota edge cases), Room DAO CRUD, ViewModel Flow behaviour.

**Instrumented UI tests** (requires connected device or emulator):
```bash
./gradlew connectedAndroidTest
```
Covers: bottom-nav navigation, adding rules, dialog flows, Quick Block tab state transitions.

> Full end-to-end tests for the accessibility service and VPN blocking cannot be fully automated — enabling the accessibility service requires a manual system settings change that Android does not allow automation scripts to perform. The services themselves are covered by the unit tests on their pure logic components (`DnsBlocker`, `ScheduleChecker`).

---

## License

MIT — do whatever you want with it.
