# FORGEHOUSE 50: FIX ROUND NOV 13 (chat, notifications, quotes, password reset, audio control, profile picture)

Section name in state: `fix_round_nov13`. Master state: `BUILD_STATE.json` in `vjumbo264/forgehouse50-android`.
(Secrets redacted from saved prompt copy: GITHUB_PAT, CLOUDFLARE_API_TOKEN, CLOUDFLARE_ACCOUNT_ID, BREVO are stored in runtime/CI secrets only.)

## Task Queue Summary
- k0: Reconcile with the last contract (`fixes_oct30_v1` and `fixes_oct31_v1`)
- k1: Chat messages arrive late even on good network (FH-008) [BUG]
- k2: Private messages broken both ways: pastor cannot send to member (FH-007) and members cannot reach admin (FH-014) [BUG]
- k3: Messages send instantly with a "sending" icon, then a tick (FH-006)
- k4: Swipe right on a message to reply (FH-005)
- k5: Members can delete their own messages (FH-004)
- k6: Members can start a direct chat with the leaders
- k7: WhatsApp-style notifications for chat, with reply from the notification (FH-009)
- k8: Hourly quotes to bring people back to the app
- k9: Forgot password
- k10: Ask members to add their own profile picture (FH-001)
- k11: Floating audio control with progress ring, replacing inline box and bottom player (FH-011)
- k12: Admin "push update" button for all admins, and first push reaching old ForgeBuild users (FH-002)
- k13: Build, release (held) and final report

## Out of scope
- FH-010 (chat bubble UI redesign), FH-012 (reader top header compaction), FH-013 (reader bottom completion bar alignment), FH-003 (admin Bible reading behavior), website feature parity, running real ForgeBuild bridge, paid push services, Firebase/FCM push, publishing an `everyone` release.

## Amendments, applied Nov 13 after the first session

1. **A1. Private messages: two symptoms, one root area (items k2 and k6).**
   - Item k2 covers both: pastor cannot send to a member (FH-007) AND members cannot reach an admin (FH-014 — screen loads, stops, loads, stops in an endless loop / "no admins found").
   - Fix the root cause AND remove the endless loop: a failed or empty load shows one clear message with a Retry button and bounded backoff, never silently re-fires.
   - k6 replaces any member-to-admin lookup: the member's Direct tab targets the shared leadership inbox itself (visible to every admin/leader; any leader can reply; leader replies show the leader's name and a `"Leader"` badge).

2. **A2. Notifications (item k7): NO Firebase in this round.**
   - Reuse and extend the existing `KeepAliveService` background mechanism for chat (do not add a second competing background service).
   - Single cheap cursor-based `/api/community/chat/sync?bg=1` call per cycle; grouped `MessagingStyle` notifications per conversation; tap opens exact thread; inline Reply via `RemoteInput` with offline retry; Mark as read action; separate channels (Chat, Private messages, Reminders, Updates); Android 13+ notification permission prompt at a sensible moment; suppress when that thread is open in foreground; mute group-chat notifications in Profile (private leader messages always notify); never show original text of deleted messages; tune background interval for Cloudflare free tier (~150 active members) and document in `docs/CHAT_ARCHITECTURE.md`; handle reboot (`BOOT_COMPLETED`), swipe-away, and logout (stop checking + clear state); keep one-time dismissible background-activity tip for aggressive OEMs (Tecno, Infinix, Itel, Xiaomi).
   - No Firebase/FCM code or `google-services.json` anywhere.

3. **A3. Out of scope additions:** Firebase/FCM push, any Telegram bot/backup/archive, separate chat database, message archiving jobs, FH-010, FH-012, FH-013, FH-003, website feature parity, running the real ForgeBuild bridge, publishing anything as `everyone`.
