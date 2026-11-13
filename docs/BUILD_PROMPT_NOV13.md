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
