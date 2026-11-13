# FORGEHOUSE 50: NOV 13 ROUND, RESUME + AMENDMENTS (Redacted Copy)

This is the archived copy of the resume + amendment instructions applied on Nov 13 after the first session.
(Credentials redacted: `GITHUB_PAT`, `CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID`, `KEYSTORE_*`, `BREVO` live only in runtime/CI secrets.)

## Key Amendments Summary
- **A1 (k2 + k6)**: Fix private messages both ways — pastor cannot send to member (FH-007) and members cannot reach admin (FH-014 endless load/stop loop). Replace individual admin lookup with shared leadership inbox; failed/empty loads show one clear message with Retry + bounded backoff.
- **A2 (k7)**: No Firebase/FCM in this round. Reuse existing `KeepAliveService` for background chat sync (`/api/community/chat/sync?bg=1`), `MessagingStyle` grouped notifications, inline `RemoteInput` reply, Mark as read, 4 notification channels, foreground suppression, group mute in Profile, deleted-message safety, reboot/swipe-away/logout lifecycle, and OEM tip. Document tuned interval and ~150-member free-tier budget in `docs/CHAT_ARCHITECTURE.md`.
- **A3 (Out of scope)**: Firebase/FCM push, Telegram bot/backup/archive, separate chat DB, archiving jobs, FH-010, FH-012, FH-013, FH-003, website parity, running real ForgeBuild bridge, publishing `everyone`.
