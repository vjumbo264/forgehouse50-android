# ForgeHouse 50 — Nov 13 Fix Round Progress Audit

Audited directly from git history, code, GitHub Actions runs, and live Cloudflare D1/Pages state before continuing `fix_round_nov13`.

| Item | Real Status | Evidence (Previous Session) | What Is Left |
|---|---|---|---|
| k0 Reconcile with last contract | `done` | `forgehouse50-android` `fb00024`; `BUILD_STATE.json` `k0_reconciliation_table` | None |
| k1 Chat latency + free-tier budget (FH-008) | `partial` | `forgehouse50` `c40c558` (Actions `43412674104` green): `/api/community/chat/sync`, composite D1 indexes, 15m `last_seen_at` throttle, non-null cursor fallback, `docs/CHAT_ARCHITECTURE.md`, `test/community_chat_test.mjs` | Android `/sync` polling, 1.5s->3s->6s adaptive cadence, non-chat unread poll, remove null-cursor full-reload loop, tune & finalize budget for k7 interval, Android tests |
| k2 Private messages broken both ways (FH-007 & FH-014) | `partial` | `forgehouse50` `c40c558`: `direct.mjs`, `threads.mjs`, `sync.mjs`, `test/community_chat_test.mjs` | Android fix for member-to-admin infinite load/stop loop, bounded backoff + Retry UI, fix pastor-to-member & member-to-admin sending on Android, regression tests |
| k3 Optimistic send & delivery ticks (FH-006) | `partial` | `forgehouse50` `c40c558`: `client_msg_id` column + unique index (applied on remote D1) & dedupe in `messages.mjs`/`direct.mjs` | Android optimistic send (`client_msg_id`), sending icon -> tick -> failed retry/cancel, offline retry, unit tests |
| k4 Swipe right to reply (FH-005 + j5 tap-reply-jump) | `not_started` | None | Android swipe-right-to-reply gesture, reply preview bar, tap quoted reply to jump/highlight target, gesture/UI tests |
| k5 Members delete own messages (FH-004) | `partial` | `forgehouse50` `c40c558`: `DELETE /api/community/chat/messages/[id]`, `deleted_at` column on remote D1, `DELETED_MESSAGE_PLACEHOLDER` masking, tests | Android delete action on own messages with confirmation, optimistic/delta tombstone UI, never leak original text, unit tests |
| k6 Member direct chat with shared leadership inbox | `partial` | `forgehouse50` `c40c558`: `threads.mjs` & `direct.mjs` always return/allow member's shared `"ForgeHouse Leadership"` thread (`thread_id = user.id`) | Remove `threadStartedByAdmin` compose lock on Android, open member direct tab straight into leadership inbox, show leader name + `"Leader"` badge on leader replies, tests |
| k7 WhatsApp-style chat notifications + inline reply (FH-009, no Firebase) | `not_started` | Backend `sync.mjs?bg=1` & `read.mjs` exist in `c40c558`; zero Android code | Reuse `KeepAliveService` for periodic `/sync?bg=1`, `MessagingStyle` grouped notifications, inline `RemoteInput` reply + Mark Read actions, 4 notification channels, Android 13+ permission prompt, foreground suppression, group mute in Profile, deleted-message safety, reboot/swipe-away/logout lifecycle, OEM background tip, unit tests |
| k8 Hourly reminder quotes | `not_started` | None | Curated spiritual/encouraging quote library, hourly waking-hours WorkManager/alarm quotes (with quiet hours & toggle in Profile), tap opens Today, unit tests |
| k9 Forgot password | `not_started` | None | Backend `/api/auth/forgot-password` & `/api/auth/reset-password` (Brevo OTP/code), Android Forgot Password flow on Sign In screen, tests |
| k10 Profile picture reminder (FH-001) | `not_started` | None | Dismissible gentle prompt/banner for members without custom profile photo, tests |
| k11 Floating audio control with progress ring (FH-011) | `not_started` | None | Replace inline audio box + bottom audio bar in `ReadScreen` with compact floating audio button + circular progress ring + play/pause + expandable speed/chapter sheet, tests |
| k12 Admin push update button & first ForgeBuild bridge check (FH-002) | `not_started` | None | Verify/ensure all admins see Push Update controls in Admin Releases, verify/fix bridge trigger & copy for first rollout to `v32` ForgeBuild users, tests |
| k13 Final build, held release & report | `not_started` | None | Update `changelog.json`, verify green Actions APK build (held pre-release, same cert SHA-256), deploy/verify backend, update `BUILD_STATE.json` & `RELEASE_RUNBOOK.md`, final report |

## Red Flag Audit (Step A)
- **Out-of-scope code**: None found (no chat bubble redesign, no Reader header/bottom-bar changes, no FH-003 changes, no Firebase/FCM dependencies or code, no Telegram bot/backup code).
- **Releases & ForgeBuild**: All releases `v33`..`v40` are `prerelease: true` on GitHub and `rollout_state = 'hold'` in remote D1. `publish-bridge-release.yml` runs = `0`. `vjumbo264/forgebuild` remains frozen at `forgehouse50-v32`.
- **CI Status**: `forgehouse50` `Deploy to Cloudflare Pages` (`43412674104`) is `success` and `migrations/community_chat_v3.sql` columns (`client_msg_id`, `deleted_at`) are present on remote D1. `forgehouse50-android` latest run (`41208817912`, `v40`) is `success`.
