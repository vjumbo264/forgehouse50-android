# ForgeBuild Engine — Architecture

The Engine is a **GitHub template repository**. Each new ForgeBuild app repo is
created from it via GitHub's "generate from template" API. The Engine is a
component/theme library — the generating AI writes real Kotlin/Compose code
against it. There is **no fixed layout schema**: a keyboard app
(`InputMethodService`), a chat app, a gallery, a browser, a foreground-service
app are all expressible.

## Structure
- `app/` — Android application module (Kotlin + Jetpack Compose).
- `app/src/main/java/com/forgebuild/engine/ui/theme/` — `ForgeBuildTheme`:
  Material 3, dynamic color (Android 12+), proper light/dark. All generated
  apps must use it.
- `app/src/main/java/com/forgebuild/engine/ui/icons/` — Material Symbols icons
  bundled as local Compose `ImageVector`s (offline, lean). Extend with
  `tools/gen_icons.py` — never emoji, never mixed icon sets.
- `app/src/main/java/com/forgebuild/engine/permissions/` — permission wiring.
  **Nothing is declared or requested by default**; the generating AI enables
  only what the app legitimately needs (manifest entries + `PermissionWiring`
  calls). See the full permission catalog below.
- `app/src/main/java/com/forgebuild/engine/data/CacheFirstStore.kt` — the
  standing cache-first data layer (see below).
- `app/src/main/java/com/forgebuild/engine/security/ScreenSecurity.kt` —
  per-screen screenshot/recording prevention via `FLAG_SECURE` (see below).
- `app/src/main/java/com/forgebuild/engine/files/SafeSave.kt` — explicit,
  permission-scoped save/download flow via the Storage Access Framework
  (see below).
- `tools/make_adaptive_icon.py` — foreground artwork in → safe-zoned adaptive
  icon layers out (72dp safe zone inside the 108dp canvas, background layer,
  monochrome layer for themed icons). Never a padded white square.
- `tools/make_keystore.sh` — one-time keystore generation; secrets go to the
  app repo's GitHub Actions secrets, never into git.
- `.github/workflows/release.yml` — builds the signed release APK on tag push
  (`v*`) or manual `workflow_dispatch`, writes `forgebuild-manifest.json`
  (name, package, permissions, notes) and attaches both to a GitHub Release.

## Full permission catalog (`PermissionWiring`)
The permissions module covers the full practical Android permission surface.
**Every capability is opt-in per app**: declare the manifest entries + call the
matching `PermissionWiring` request ONLY when the app's description genuinely
needs it; nothing is declared by default. Adding a new permission later = one
`EnginePermission` enum entry (version-split via `Build.VERSION` where needed)
plus one small settings-intent helper for special-tier ones — not a redesign.

| Capability (`EnginePermission`) | Manifest entries (version-gated) | Grant flow |
|---|---|---|
| `STORAGE` | scoped media (13+): READ_MEDIA_IMAGES/VIDEO/AUDIO; legacy READ_EXTERNAL_STORAGE below | runtime |
| `CAMERA` | CAMERA | runtime |
| `NOTIFICATIONS` | POST_NOTIFICATIONS (33+) | runtime |
| `FOREGROUND_SERVICE` | FOREGROUND_SERVICE (+ SPECIAL_USE 34+) | manifest + runtime-free |
| `LOCATION` | ACCESS_FINE/COARSE_LOCATION | runtime (foreground) |
| `BACKGROUND_LOCATION` | fine+coarse first, then ACCESS_BACKGROUND_LOCATION | runtime + Settings flow (11+) |
| `MICROPHONE` | RECORD_AUDIO | runtime |
| `CONTACTS` | READ/WRITE_CONTACTS | runtime |
| `CALENDAR` | READ/WRITE_CALENDAR | runtime |
| `PHONE` | READ_PHONE_STATE, READ_CALL_LOG | runtime |
| `SMS` | READ/RECEIVE/SEND_SMS | runtime |
| `BODY_SENSORS` | BODY_SENSORS | runtime |
| `ACTIVITY_RECOGNITION` | ACTIVITY_RECOGNITION (29+) | runtime |
| `BLUETOOTH` | 31+: BLUETOOTH_SCAN/CONNECT/ADVERTISE; legacy BLUETOOTH/ADMIN below | runtime |
| `NFC` | NFC | manifest only |
| `BIOMETRIC` | none — gate on `BiometricManager.canAuthenticate` (`canUseBiometric`, reflection-based so no engine dependency), then `BiometricPrompt`; an app using it adds `androidx.biometric:biometric` itself | in-place prompt |
| `EXACT_ALARM` | USE_EXACT_ALARM (33+) / SCHEDULE_EXACT_ALARM (31–32) | Settings flow (`requestExactAlarm`) |
| `ALL_FILES_ACCESS` | MANAGE_EXTERNAL_STORAGE — **sparingly, file-manager-class needs only, never by default** | Settings flow (`requestAllFilesAccess`) |
| `INSTALL_UNKNOWN_APPS` | REQUEST_INSTALL_PACKAGES | Settings flow (`requestInstallUnknownApps`) |
| `OVERLAY` | SYSTEM_ALERT_WINDOW | Settings flow (`requestOverlay`) |
| `ACCESSIBILITY_SERVICE` | service + res/xml accessibility config | user enables in Settings (`requestAccessibility`) |
| `NOTIFICATION_LISTENER` | NotificationListenerService declaration | user enables in Settings (`requestNotificationListener`) |
| `VPN_SERVICE` | none | user consent via `VpnService.prepare()` (`requestVpn`) |
| `DEVICE_ADMIN` | receiver + res/xml/device_admin.xml | policy flow (`requestDeviceAdmin`) |

The building AI may add any further standard/signature-tier permission a
specific requested app genuinely needs, following the same enum-entry pattern.

## Cache-first, background-refresh data pattern (STANDING REQUIREMENT)
Any generated app whose functionality involves fetching/syncing from a live
remote source (a repository, an API, anything network-backed) MUST use
`com.forgebuild.engine.data.CacheFirstStore` — never a from-scratch
reload-everything-on-every-open behavior.

The contract:
1. **On screen/app open, render immediately from the local cache** — call
   `loadFromCache()` synchronously before first composition; there is zero
   network wait. First launch renders the empty state.
2. **Then refresh in the background** — launch `refresh()` in a coroutine. It
   fetches the live source, reconciles (additions appear, removals disappear,
   changed items update — remote is authoritative by default; override
   `reconcile` for id-aware merges via the `keyOf` parameter), persists the
   cache, and the UI recomposes in place.
3. **Never re-flash to a loading state.** The already-rendered cached UI must
   not drop back to a spinner because of a refresh. Use the `refreshing`
   StateFlow for a subtle non-blocking affordance and `lastError` for a
   dismissible banner; cached data stays on screen when the network fails.

The reference implementation uses a dependency-free on-disk JSON cache. A
building AI may substitute Room or DataStore where the data genuinely
warrants it (relational queries, large datasets) — that substitution and its
reason must be documented in the app's own ARCHITECTURE.md notes. What must
be preserved is the contract above, not the exact class. The engine ships
`kotlinx-coroutines-android` as a justified engine-level dependency for this
layer.

## Per-screen screenshot / recording prevention (`FLAG_SECURE`)
`com.forgebuild.engine.security.ScreenSecurity` applies
`WindowManager.LayoutParams.FLAG_SECURE` to a window, blocking screenshots,
screen recordings, and the recent-apps thumbnail for exactly that window.
Apply it **per screen, not app-wide**: the operator's need is "certain parts"
of an app (a vault screen, a private detail view), so blanket protection is
wrong — it also breaks the operator's own legitimate screenshots. In a
single-activity Compose app, call `ScreenSecurity.setSecure(activity, flag)`
from a `LaunchedEffect` keyed on whether the current route is sensitive; in a
multi-activity app, call `ScreenSecurity.apply(activity)` in `onCreate()` of
exactly the sensitive activities. Only protect the whole app if the operator
explicitly asks.

## Explicit, permission-scoped saves/downloads (`SafeSave`, never DownloadManager)
`com.forgebuild.engine.files.SafeSave` is the only approved way for a
generated app to save/download files. The app keeps control: its own save UI
and confirmation, a destination the user explicitly picks. **Never** use
`android.app.DownloadManager` or let files silently land in the system
Downloads app outside the app's control.

Default: the **Storage Access Framework** — `registerCreateDocument`
(`ACTION_CREATE_DOCUMENT`) for save-as flows and `registerOpenTree`
(`ACTION_OPEN_DOCUMENT_TREE`) for a reusable user-chosen folder, with
`takePersistablePermission` for grants that must survive restarts. SAF is
user-directed and permission-scoped by design: no runtime storage permission
is needed on modern Android because the user grants exactly the file/tree
they pick. Fall back to the legacy `WRITE_EXTERNAL_STORAGE` flow (via
`PermissionWiring`, `EnginePermission.STORAGE`) only where an app's minSdk
genuinely requires pre-Android-10 direct-path saves. Never request
`MANAGE_EXTERNAL_STORAGE` just to save files.

## Extension points for the generating AI
1. Replace `namespace`/`applicationId` in `app/build.gradle.kts`.
2. Replace `app_name` in `res/values/strings.xml`.
3. Replace `StarterScreen()` in `MainActivity` with the real UI.
4. Add dependencies **only** when a feature requires them, with justification
   noted in the app repo. R8 minify + resource shrinking stay on.
5. Add icons via `tools/gen_icons.py`; permissions via the manifest +
   `PermissionWiring`; any service (foreground, IME, device admin) as ordinary
   Android components wired in the manifest.

## Signing & releases
Releases are GitHub Releases on the app's own repo (`v1`, `v2`, ...) — never
overwritten. Rollback/history come from the GitHub Releases API. Signing uses
the app repo's Actions secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
`KEY_ALIAS`, `KEY_PASSWORD`.


## Admin User Management & Batch Delete Architecture (batch_fixes_oct12_v1)
- **Participant Cards & Full Names**: Admin participants view renders full names with wrapping, subtitle with points, completed days, notes, and activity status badge. Includes short disambiguation tag `#xxxx` (last 4 characters of ID).
- **Single Delete Confirmation**: Simplified confirmation dialog requiring single-tap confirmation (no manual name re-typing needed) sending `{ confirm: true }`.
- **Batch Deletion**: Selection mode allowing multi-select, "Select all", and "Select inactive accounts" shortcut. Confirmation dialog enforces typing "DELETE" to prevent accidental data loss, sending `POST /api/admin/users/delete` with `{ user_ids: [...], confirm_word: "DELETE" }`.
- **Note Pre-fill**: Automatic contextual pre-fill of Book, Chapter, and Verse from current reader viewport and active verse highlights across all entry points.
- **Display-only Translations**: Offline downloaded Bible translations listed as display-only in Profile; management and removal consolidated in Manage Versions screen.


## Programme Window, End-Date Integrity & Reset Guard (ops_batch_oct14_v1)

### 1. Join Window Rule
- 10 calendar days in WAT (UTC+1) counted from start date, start day as Day 1.
- Started Mon 12 Oct 2026, closes Wed 21 Oct 2026 at 23:59:59 WAT.
- On Wed 14 Oct 2026, exactly 8 days remain including today.
- Signup screen shows "Joining closes on 21 Oct 2026" while open, and friendly closed state afterwards.
- Admin Programme tab shows "Started Mon 12 Oct 2026 · Joining closes Wed 21 Oct 2026 · 8 days left".

### 2. Programme End Date
- Latest possible joiner Day 1 on 2026-10-21 ends on 2026-12-28 (50 reading days skipping Tue/Fri).
- Programme end date is 2026-12-28.

### 3. Reset Guard
- End Testing Phase & Reset is disabled with "The programme has started; reset is disabled" (HTTP 409).
- Start Programme is idempotent.
