# ForgeHouse 50 — Release & Rollout Runbook

## Repository & Signing Summary
- **Primary Android Repo**: `vjumbo264/forgehouse50-android`
- **Legacy ForgeBuild Repo**: `vjumbo264/forgebuild` (frozen for FH50; last legacy tag `forgehouse50-v32`, `versionCode = 32`)
- **Application ID**: `com.forgebuild.forgehouse50`
- **Verified APK Signing Certificate (SHA-256)**:
  `80:DA:9D:1C:A9:4A:E7:94:2D:7D:9C:BA:C2:F6:B9:5D:85:68:60:43:C9:EA:B5:EE:04:78:BB:2A:B6:2C:03:F0`

## Current Release Hold State (`release_hold: true`)
1. Every push to `main` touching Android code runs unit tests, builds a signed release APK (`versionCode >= 33`), verifies the SHA-256 signing certificate with `apksigner`, and publishes a **GitHub Pre-Release** in `vjumbo264/forgehouse50-android` only.
2. Nothing is published to `vjumbo264/forgebuild`.
3. On the FH50 backend (`forgehouse50.pages.dev/api/app-release`), new builds default to `hold` / `admins_only`. Unauthenticated/member phones on `v32` continue seeing `versionCode: 32` (no update prompt), and website visitors can still download `v32`.

## Step-by-Step: Testing as Admin First (Before Public Rollout)
1. Sign into the ForgeHouse 50 app (or web admin) with an Admin account.
2. Open **Admin Dashboard -> App Releases**.
3. Locate the target build (e.g., `v35`) and tap **Release to admins** (if currently `hold`).
4. Admin phones checking for updates will see the update, can test installing it over `v32` (same signing key), or tap **Not now** to dismiss.
5. If an issue is discovered, tap **Pull back to hold** in the Admin Dashboard immediately.

## Step-by-Step: First Rollout to All Existing Users (The Bridge + Everyone Channel)
When Pastor Dave and Victor confirm the update is ready for every member:

1. **Promote in Admin Dashboard (Primary Updater Path)**:
   - Open **Admin Dashboard -> App Releases** in ForgeHouse 50.
   - Select the verified release tag (e.g., `v35`) and tap **Release to everyone**, then confirm the dialog.
   - All `v32`+ phones polling `https://forgehouse50.pages.dev/api/app-release` will immediately detect `versionCode > 32` and offer the update, AND the website download button will serve the new APK.

2. **Optional Legacy Repo Bridge (`publish-bridge-release` workflow)**:
   - If you also want the APK mirrored onto `vjumbo264/forgebuild` releases in legacy `forgehouse50-vN` format (`app-forgehouse50-vN.apk` + `release.json` + `forgebuild-manifest.json`):
   - Go to `https://github.com/vjumbo264/forgehouse50-android/actions/workflows/publish-bridge-release.yml`
   - Tap **Run workflow**
   - Enter `tag_name` (e.g., `v35`)
   - Enter `CONFIRM_PUSH_TO_ALL_USERS` = `yes`
   - Run the workflow.

## Subsequent Updates (After Everyone Has >= v34)
Once members are on the new app version, all updates are controlled 100% from **Admin Dashboard -> App Releases** (`hold` -> `admins_only` -> `everyone`, with instant `Pull back to hold` rollback) without ever touching `vjumbo264/forgebuild` again.
