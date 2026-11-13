# ForgeHouse 50 — Nov 13 Fix Round Progress Audit

Audited directly from git history, source code, GitHub Actions runs, and live Cloudflare D1/Pages state at resume handoff.

| Item | Real Status | Evidence (Prior Sessions) | What Is Left |
|---|---|---|---|
| k0 Reconcile with last contract | `done` | `forgehouse50-android` `fb00024`; `BUILD_STATE.json` `k0_reconciliation_table` | None |
| k1 Chat latency + free-tier budget (FH-008) | `done` | `forgehouse50` `c40c558` (deployed, `community_chat_test.mjs` pass); `forgehouse50-android` `2757650`/`579a32e` (Actions `43419942985` green, `CommunityChatLogicTest`) | None |
| k2 Private messages broken both ways (FH-007 & FH-014) | `done` | `forgehouse50` `c40c558` (`direct.mjs`, `sync.mjs`); `forgehouse50-android` `2757650`/`579a32e` (EPOCH_CURSOR + bounded 3-retry backoff + Retry UI, Actions `43419942985` green) | None |
| k3 Optimistic send & delivery ticks (FH-006) | `done` | `forgehouse50` `c40c558` (`client_msg_id` on remote D1); `forgehouse50-android` `2757650`/`579a32e` (`PendingChatStore`, ticks, Actions `43419942985` green) | None |
| k4 Swipe right to reply (FH-005 + j5) | `done` | `forgehouse50-android` `2757650`/`579a32e` (swipe-right, quoted reply bar, tap-to-jump highlight, Actions `43419942985` green) | None |
| k5 Members delete own messages (FH-004) | `done` | `forgehouse50` `c40c558` (`DELETE /api/community/chat/messages/[id]`, `deleted_at` on D1); `forgehouse50-android` `2757650`/`579a32e` (confirm dialog + tombstone masking) | None |
| k6 Member direct chat with shared leadership inbox | `done` | `forgehouse50` `c40c558` (`threads.mjs`/`direct.mjs` shared inbox); `forgehouse50-android` `2757650`/`579a32e` (unlocked composer, Leader badge) | None |
| k7 WhatsApp-style chat notifications + inline reply (FH-009, no Firebase) | `partial` | `forgehouse50-android` `00545a7`/`3ab35f5` (Actions `43421105829` green, `v42` held): `KeepAliveService` `/sync?bg=1`, `MessagingStyle`, `RemoteInput` reply, Mark Read, 4 channels, foreground suppression, group mute, deleted-msg scrub, reboot/swipe/logout, OEM tip, `ChatNotificationLogicTest` | Use dedicated `notificationPermissionPrompted` flag so upgrading Android 13+ users see the permission prompt even if `keepAliveExplained` was already set; mark `done` in `BUILD_STATE.json` |
| k8 Hourly reminder quotes | `partial` | `forgehouse50-android` `3ab35f5` (Actions `43421105829` green): `QuoteLibrary` (36 quotes), `QuoteWorker` (1h periodic, waking hours 07:00-21:59 Lagos), Profile toggle, `HourlyQuoteLogicTest` | Change `QuoteWorker.schedule()` from `UPDATE` to `KEEP` so app opens do not reset the 1h initial delay; mark `done` in `BUILD_STATE.json` |
| k9 Forgot password | `partial` | `forgehouse50` `53f8bd0` (deployed, `auth_reset_test.mjs` passes): `/api/auth/forgot_password` + `/api/auth/reset_password` | Fix `reset_password.mjs` returning hardcoded `role: "member"` instead of `user.role`; implement Android Forgot Password flow in `AuthScreens.kt` + `ApiClient.kt` + tests |
| k10 Profile picture reminder (FH-001) | `not_started` | None | Gentle dismissible prompt for members without custom photo + unit tests |
| k11 Floating audio control with progress ring (FH-011) | `not_started` | None | Replace inline audio box + bottom player in `ReadScreen` with compact floating button + circular progress ring + expandable controls + tests |
| k12 Admin push update button & first ForgeBuild bridge (FH-002) | `partial` | `forgehouse50` `ddfe29f` (deployed, `publish_to_forgebuild`/`bridged` on D1, `update_rollout_test.mjs` passes) | Verify all admins see App Releases controls, wire `publish_to_forgebuild` toggle/payload in `AdminReleasesTab.kt` + `ApiClient.kt` + tests |
| k13 Final build, held release & report | `not_started` | None | Update `changelog.json`, verify green Actions APK build (held pre-release, same cert SHA-256), verify deployed backend, update `BUILD_STATE.json` & `RELEASE_RUNBOOK.md`, final report |

## Red Flag Audit (Step A Resume Check)
- **Out-of-scope code**: None found. Zero Firebase/FCM dependencies or files, zero Telegram code, zero UI polish tasks touched, no FH-003 changes.
- **Releases & ForgeBuild**: All releases `v33`..`v42` are `prerelease: true` on GitHub; `app_releases` in remote D1 are all `rollout_state = 'hold'`, `publish_to_forgebuild = 0`, `bridged = 0`. `publish-bridge-release.yml` runs = `0`. `vjumbo264/forgebuild` remains frozen at `forgehouse50-v32`.
- **CI & Backend Status**: `forgehouse50-android` run `43421105829` (`v42`) = `success`. `forgehouse50` run `43421940224` (`ddfe29f`) = `success`, all 6 backend SQLite test suites pass locally.
