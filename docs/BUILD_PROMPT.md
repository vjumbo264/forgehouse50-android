# FORGEHOUSE 50: OCT 30 BUILD PROMPT (audio + repo migration + pastor's requests)

Contract id: `fixes_oct30_v1`. Master state file: `BUILD_STATE.json` in the new repo `vjumbo264/forgehouse50-android`.

You are an autonomous AI builder with NO memory of any earlier session. Everything you need is in this document and in the repositories. If you die midway, another AI will pick this up from `BUILD_STATE.json`, so keep it current.

---

## STEP 0: CREDENTIALS (redacted in repository copy)

```
GITHUB_PAT = <REDACTED_SECRET>
CLOUDFLARE_API_TOKEN = <REDACTED_SECRET>
CLOUDFLARE_ACCOUNT_ID = <REDACTED_ACCOUNT_ID>
# Project-specific:
# Android signing secrets stored in GitHub Actions Secrets:
# KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD, GH_PAT
```

- Store anything the code needs at runtime as GitHub Actions secrets or Cloudflare secrets. **Never commit a secret. Never write a secret value into BUILD_STATE.json, logs, comments or commit messages** (names only).

---

## 1. Role, autonomy and ground rules

- Full autonomy. Pick sensible defaults and record assumptions in `BUILD_STATE.json` -> `decisions`.
- Finish each queue item completely before starting the next. Verify against acceptance criteria before marking `done`.
- Do NOT do any UI refinement or polish pass. Build only basic Material 3 UI with Material Symbols icons, no emoji.
- Commit and push frequently so `BUILD_STATE.json` always reflects true repository state.
- Free tiers only (`*.workers.dev` / `*.pages.dev`, GitHub Releases & Actions).

---

## 2. Queue Summary (fixes_oct30_v1)

- **i0**: Move Android app to `vjumbo264/forgehouse50-android`, verify identical APK signing certificate (`80:DA:9D:1C:A9:4A:E7:94:2D:7D:9C:BA:C2:F6:B9:5D:85:68:60:43:C9:EA:B5:EE:04:78:BB:2A:B6:2C:03:F0`), hold all releases as pre-releases, prepare `publish-bridge-release` (`CONFIRM_PUSH_TO_ALL_USERS=yes`, never run), write `docs/RELEASE_RUNBOOK.md`.
- **i1**: VerseWell data contract (`docs/DATA_CONTRACT.md`), clean separation of verse text, section intros/headings, and footnotes; CI validation; verify all 5 audio translations (KJV, NIV, TLB, GW, VOICE) have 260 NT chapters.
- **i2**: VerseWell audio generation pipeline (Edge TTS primary + Piper fallback, per-verse synthesis & timestamps, -16 LUFS mono 48kbps AAC `.m4a` faststart, strict ffprobe duration/silence verification, chained & cron-resumed matrix in `vjumbo264/versewell`). Document in `docs/AUDIO.md`.
- **i3**: VerseWell Worker audio API (`/api/v1/audio/{version}/{book}/{chapter}` with HTTP 206 Range + immutable cache headers, `/api/v1/audio/{version}/manifest`, `/api/v1/versions` `audio` metadata).
- **i4**: Prominent, persistent, non-truncated Announcements card on Today screen for all users (including admins/pastor), archive access, instant no-stale-cache delivery, admin post/edit/retire + member preview.
- **i5**: Reader timer fix: never leak `CancellationException` or raw exception strings into pause banner; unit test + catch-block audit.
- **i6**: Rest days (Tue/Fri): allow outstanding catch-up reading until 19:00 Africa/Lagos (admin-editable server setting); normal reading stays locked on rest days.
- **i7**: Streak verification & unit tests on server logic (daily reading grows streak; missed days reset; catch-up never adds; Tue/Fri rest days neither break nor add).
- **i8**: Admin-first update channels + controlled rollout (`hold` | `admins_only` | `everyone`) on FH50 backend + new Android updater + Admin Dashboard controls with audit logging.
- **i9**: Community chat (group chat with reply-to, pagination, moderation delete/mute, rate limit) + private threads between admin and individual member; cursor polling + unread badges; D1 migration.
- **i10**: Manual audio download (per translation & per chapter) + sha256 verification + offline Media3 playback (0.75x-1.5x, no auto-advance, no auto-scroll, never counts as reading, foreground-only default behind config flag).
- **i11**: Audio completeness audit (all 5 translations 260/260 verified + API spot-checks) + final handoff checklist with `release_hold: true`.
