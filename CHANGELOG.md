# Changelog

All notable changes to Blocker are documented here.

This project follows [Conventional Commits](https://www.conventionalcommits.org/) and [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added (Sleep Guard)
- Automatic night window (default 23:00–07:00, cross-midnight) evaluated on every window event plus a periodic foreground recheck — no alarms, survives reboot
- Essential-app whitelist resolved from intents (dialer, SMS, contacts, launcher, alarm clock, in-call UI) for OEM ROM compatibility
- WeChat "chat only" mode: activity-class blacklist plus clicked/selected-tab label matching for 视频号 / 朋友圈 / 小程序 / 公众号 / 直播 / 视频通话 / 发现 / 我
- Tamper resistance: night-locked settings, blocked settings/installer/app stores, hidden emergency stop, one 5-minute grace per night
- Persistent foreground guard notification and boot restore
- Night-time DNS layer for video / short-video domains (when the VPN is enabled)
- New "守护" tab with Chinese configuration UI, plus JVM unit tests for time windows and WeChat rules
- `gradlew` scripts and a GitHub Actions workflow that builds a sideloadable debug APK in the cloud

### Fixed
- Quick Block exception picker hid every system app, making the dialer and SMS impossible to whitelist; exception list now persists across restarts
- DNS upstream was hard-coded to 8.8.8.8 (unreachable in mainland China); now uses 223.5.5.5 / 119.29.29.29 with failover
- Onboarding no longer requires Usage Access, which the sleep guard does not use

### Fixed (second review pass)
- **Night guard did not fire for an app that was already open when 23:00 arrived** (or when a grace period expired), because only window-switch events were checked. Added a 20s foreground-package check plus rate-limited content-change rechecks and duplicate-block suppression
- **WeChat tap matching could block ordinary chat messages** whose text was 「我」/「发现」/「设置」/「视频」. Tap detection is now strict: bottom tabs require the tapped node to be in the bottom tab bar; other entries require an exact label or a numeric badge
- Emergency Stop dialog was shown from a Service context without an Activity token (`BadTokenException`); now uses a themed context and an application-overlay window type, with a safe fallback
- The once-per-night grace could be claimed twice across midnight; a night session now runs noon-to-noon
- Boot receiver could abort when Android 12+ rejected a background foreground-service start, preventing the guard notification from returning; wrapped in try/catch and invalidated the essential-app cache on boot
- `setWindow` accepted equal start/end times (silently disabling the guard); validated at the manager layer, with accurate UI error messages
- GitHub Actions no longer depends on generating the wrapper jar before building; it builds with Gradle directly and also publishes a complete project zip containing an official wrapper
- Added JVM tests for night-session keys, bottom-tab tap detection and chat-message false positives

### Added
- App blocking after daily usage quota via AccessibilityService
- Website blocking across all browsers via DNS-level VPN tunnel
- Schedule-based app blocking (time window + day of week)
- Site daily quota tracking (block after N minutes of DNS activity)
- Quick Block focus mode — timed all-app blocking with per-app exceptions
- Block overlay with full-screen card and home redirect on tap
- Friction dialogs (5-second countdown) on rule disable
- Emergency Stop for Quick Block (10-second countdown)
- Site-blocked system notification with per-domain rate limiting
- iOS glass-style UI (light and dark mode)
- Onboarding flow for all required permissions
- Room DB with schema migrations
- Unit tests: DNS blocking, schedule logic, repository CRUD
- Espresso UI tests: navigation, rule management, Quick Block tab
