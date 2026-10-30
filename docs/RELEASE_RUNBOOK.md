# ForgeHouse 50 Release Runbook

## Overview
- **New App Repo**: `vjumbo264/forgehouse50-android`
- **Old App Host**: `vjumbo264/forgebuild` (releases held)
- **Application ID**: `com.forgebuild.forgehouse50`
- **Signing Key SHA-256 Fingerprint**: `80:DA:9D:1C:A9:4A:E7:94:2D:7D:9C:BA:C2:F6:B9:5D:85:68:60:43:C9:EA:B5:EE:04:78:BB:2A:B6:2C:03:F0`

## Release Hold Policy
All automated builds in `forgehouse50-android` create **pre-releases** in `forgehouse50-android` only.
No builds or tags are pushed to `forgebuild`.
Consequently, no user phone will see or receive an update until Victor explicitly approves.

## How to Test New Releases
Admins can test releases using the admin update channel (configured via `forgehouse50.pages.dev/api/app-release` with admin token or dashboard).

## How to Roll Out to All Users (The Bridge)
When Pastor Dave and Victor decide to ship the update to all members:
1. In `vjumbo264/forgehouse50-android`, navigate to **Actions** -> **Publish Bridge Release to Old Repo**.
2. Select the validated tag (e.g. `v33`).
3. Set `confirm_push` to `yes`.
4. Trigger the workflow.
This will publish `forgehouse50-v33` to `vjumbo264/forgebuild`, triggering the existing in-app updater on all member devices.
