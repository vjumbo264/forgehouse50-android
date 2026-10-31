# ForgeHouse 50 — Release & Rollout Runbook

## Repository & Signing Summary
- **Primary Android Repo**: `vjumbo264/forgehouse50-android`
- **Legacy ForgeBuild Repo**: `vjumbo264/forgebuild` (frozen for FH50; last legacy tag `forgehouse50-v32`, `versionCode = 32`)
- **Application ID**: `com.forgebuild.forgehouse50`
- **Verified APK Signing Certificate (SHA-256)**:  
  `80:DA:9D:1C:A9:4A:E7:94:2D:7D:9C:BA:C2:F6:B9:5D:85:68:60:43:C9:EA:B5:EE:04:78:BB:2A:B6:2C:03:F0`

---

## IMPORTANT: Finding & Downloading Release APKs on Mobile (Why Victor Didn't See v39)

### Why GitHub Hides Pre-Releases on the Main Page
When you open `https://github.com/vjumbo264/forgehouse50-android` on a phone or desktop browser, the right-hand sidebar (or mobile section) labeled **Releases** only displays the latest *full/official release*. Because new builds are intentionally marked as **Pre-release** (under Release Hold), GitHub hides them from the repository front page. The front page may show "No releases published" or an old release.

### Where to Tap on a Phone
1. Open the repository: `https://github.com/vjumbo264/forgehouse50-android`
2. Scroll to the **Releases** section and tap the word **Releases** (or navigate to `https://github.com/vjumbo264/forgehouse50-android/releases`).
3. You will see the latest pre-release (e.g. `v39`, `v40`, etc.) with a `Pre-release` badge.
4. Under the release notes, tap **Assets** (it has a small dropdown arrow next to it).
5. Tap `forgehouse50-v<version>.apk` (e.g., `forgehouse50-v39.apk`) to download immediately.

### Direct Download URL Pattern
Every APK can be downloaded directly without navigating GitHub menus:
- Direct link format: `https://github.com/vjumbo264/forgehouse50-android/releases/download/<tag>/forgehouse50-<tag>.apk`
- **Latest v39 direct APK link**:  
  https://github.com/vjumbo264/forgehouse50-android/releases/download/v39/forgehouse50-v39.apk
- Asset tag page:  
  https://github.com/vjumbo264/forgehouse50-android/releases/tag/v39
- All releases listing:  
  https://github.com/vjumbo264/forgehouse50-android/releases

---

## Current Release Hold State (`release_hold: true`)
1. Every push to `main` touching Android code runs unit tests, builds a signed release APK (`versionCode >= 33`), verifies the SHA-256 signing certificate with `apksigner`, and publishes a **GitHub Pre-Release** in `vjumbo264/forgehouse50-android` only.
2. Nothing is published to `vjumbo264/forgebuild`.
3. On the FH50 backend (`forgehouse50.pages.dev/api/app-release`), new builds default to `hold` / `admins_only`. Unauthenticated/member phones on `v32` continue seeing `versionCode: 32` (no update prompt), and website visitors can still download `v32`.

---

## Step-by-Step: Testing as Admin First (Before Public Rollout)
1. Sign into the ForgeHouse 50 app (or web admin) with an Admin account.
2. Open **Admin Dashboard -> App Releases**.
3. Locate the target build (e.g. `v39`, `versionCode = 39`) and tap **Release to admins** (if currently `hold`).
4. Admin phones checking for updates will see the update, can test installing it over `v32` (same signing key), or tap **Not now** to dismiss.
5. If an issue is discovered, tap **Pull back to hold** in the Admin Dashboard immediately.

---

## Step-by-Step: First Rollout to All Existing Users (The Bridge + Everyone Channel)
When Pastor Dave and Victor confirm the update is ready for every member:
1. **Promote in Admin Dashboard (Primary Updater Path)**:
   - Open **Admin Dashboard -> App Releases** in ForgeHouse 50.
   - Select the verified release tag and tap **Release to everyone**.
   - Check the toggle: **"Also send this update to ForgeBuild so older versions update. Use this for the first rollout."**
   - Confirm the dialog (`confirm: 'RELEASE_TO_EVERYONE'`).
   - The automated bridge workflow in `forgehouse50-android` detects `rollout_state = 'everyone'` with `publish_to_forgebuild = true`, publishes the release to `forgebuild`, verifies the APK certificate fingerprint matches, and marks `bridged: true`.
   - All `v32`+ phones polling `https://forgehouse50.pages.dev/api/app-release` will immediately detect the new version and offer the update, AND the website download button (`/download/latest`) will serve the new APK.

2. **Subsequent Updates (After Everyone Has >= v34)**:
   - Once members are on the new app version, all updates are controlled 100% from **Admin Dashboard -> App Releases** (`hold` -> `admins_only` -> `everyone`, with instant `Pull back to hold` rollback) without ever touching `vjumbo264/forgebuild` again.
