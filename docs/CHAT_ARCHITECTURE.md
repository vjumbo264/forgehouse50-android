# ForgeHouse 50 — Chat Latency & Free-Tier Architecture (`k1` / `k7`)

## 1. Root Cause of Slow Chat Delivery (FH-008)
1. **Fixed 4-second poll + double round-trip**: `CommunityScreen` polled every `4,000 ms` and issued two separate HTTP requests per tick (`/api/community/chat/messages?since=...` plus `/api/community/chat/threads`).
2. **Null-cursor full-reload loop**: When a conversation had 0 messages (`last_timestamp == null`), `pollGroupSince` and `pollDirectSince` fell back to full initial 50-row loads on every 4s tick.
3. **Unthrottled D1 `last_seen_at` writes**: `getUser()` in `functions/lib/auth.mjs` previously executed `UPDATE sessions SET last_seen_at = ?` on every single poll request, creating avoidable D1 write pressure.

## 2. Options Evaluated (Cloudflare Free Tier)
Cloudflare Free Tier hard limits for the entire project:
- **Workers / Pages Functions**: `100,000` requests/day
- **D1 Database**: `5,000,000` rows read/day, `100,000` rows written/day

### Option (a): Durable Objects + WebSockets
- `forgehouse50` is deployed as a **Cloudflare Pages** project (`functions/`), which cannot host/export Durable Object classes directly (would require a separate Worker deployment, cross-service auth plumbing, and separate CI/secrets).
- On the Cloudflare Workers Free plan, Durable Objects still share the `100,000` requests/day and `100,000` SQLite writes/day ceilings.
- Mobile devices on cellular networks in Nigeria frequently drop background WebSockets during Android Doze/network handoffs, causing reconnect storms that waste battery and Worker invocations.

### Option (b): Adaptive Incremental Cursor Polling + Unified `/api/community/chat/sync` (Chosen)
- Consolidates group messages, direct messages, soft-delete updates, mute status, and unread badges into a single zero-write `GET /api/community/chat/sync` endpoint backed by composite D1 indexes (`idx_chat_group_created`, `idx_chat_group_updated`, `idx_chat_direct_thread_created`, `idx_chat_direct_thread_updated`).
- Throttles `sessions.last_seen_at` and `admin_sessions.last_seen_at` updates to once per 15 minutes so idle polls execute **0 D1 writes**.
- Always returns a non-null cursor (`EPOCH_ISO` fallback when empty) so clients never re-run full initial loads on empty threads.
- **Foreground Chat Screen Cadence**:
  - `1,500 ms` (1.5s) active interval for 30s after opening chat or sending/receiving a message (achieves ~1–2s delivery on good network).
  - `3,000 ms` (3s) after 30s with no new activity.
  - `6,000 ms` (6s) after 2 minutes idle.
  - `0` requests when paused, backgrounded, or on another screen.
- **Foreground Other Screens (Today / Reader / Progress / Profile)**:
  - Polls `GET /api/community/chat/sync?unread_only=1` every `15,000 ms` (15s) while `ON_RESUME` so unread badges update promptly across the app.
- **Background Service (`KeepAliveService`, k7)**:
  - Reuses the existing single persistent foreground `KeepAliveService` (`foregroundServiceType="dataSync"`) — no second competing service.
  - Polls `GET /api/community/chat/sync?bg=1&group_since=...&direct_since=...`:
    - **06:00–23:00 Africa/Lagos (17h waking)**: every **5 minutes** (`300s`).
    - **23:00–06:00 Africa/Lagos (7h night)**: every **15 minutes** (`900s`).
    - **Active burst**: `30s` interval for 2 minutes right after a new notification arrives or user replies inline from a notification.

## 3. Daily Request, D1 Read, and D1 Write Budget (~150 Active Members)

| Traffic Category | Cadence | Daily Reqs / Member | Daily Reqs (150 Members) | Notes |
|---|---|---:|---:|---|
| Active Chat Screen | 1.5s active -> 3s -> 6s idle (~10 min/day open) | 150 | 22,500 | 1 req/tick via `/sync` or `/messages`/`/direct` |
| Foreground Non-Chat Screens | 15s (`?unread_only=1`, ~15 min/day active) | 60 | 9,000 | Updates Home & Nav unread badges |
| Background `KeepAliveService` (Day 06–23 Lagos) | 5 min (300s) over 17h | 204 | 30,600 | Single cheap cursor call |
| Background `KeepAliveService` (Night 23–06 Lagos) | 15 min (900s) over 7h | 28 | 4,200 | Quiet-hours battery/quota saving |
| Background Active Burst (post-notif/reply) | 30s for 2m (~2.5 events/day) | 10 | 1,500 | Fast follow-up replies |
| Non-Chat App APIs (`/today`, `/read/*`, `/quiz`, `/notes`, etc.) | Per reading session | 30 | 4,500 | Core programme traffic |
| **TOTAL WORKER REQUESTS** | | **482** | **72,300** | **27.7% headroom** below 100,000/day |

### D1 Rows Read & Written Budget (150 Active Members)
- **D1 Rows Read**: Each idle `/sync` call uses indexed lookups (`1` session row + `1` mute row + `1` member-read row + `0` scanned message rows when idle via `idx_chat_*_created`/`updated`) = ~4 rows read/poll.
  - `72,300 * 4` + `~100,000` non-chat reads = **~389,200 rows read/day** (**7.8%** of `5,000,000` free limit).
- **D1 Rows Written**: Idle polls perform **0 writes**.
  - Actual chat messages sent (~20/user/day * 150) = `3,000` writes/day
  - Read-cursor updates only when unread messages arrive = `~5,000` writes/day
  - `last_seen_at` session refreshes (throttled to >= 15 min) = `~2,500` writes/day
  - Reading/quiz/notes writes = `~1,500` writes/day
  - **Total D1 Writes = ~12,000 rows/day** (**12.0%** of `100,000` free limit).

## 4. Reconnect & Resilience Guarantees
- Foreground polling uses `while (isActive && isResumed)` tied to `Lifecycle.Event.ON_RESUME` / `ON_PAUSE` and triggers an immediate catch-up sync on resume or network recovery.
- `KeepAliveService` catches transient network/HTTP exceptions without crashing, preserves cursors in `SessionStore`, clears cursors/tokens and stops on logout, and restarts on `BOOT_COMPLETED` when logged in.
