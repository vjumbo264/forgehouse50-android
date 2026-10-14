# forgebuild-engine
ForgeBuild Engine — reusable Android base (Kotlin + Jetpack Compose, Material 3 dynamic color, Material Symbols, conditional permissions module, adaptive-icon generator, lean Gradle, release-APK GitHub Actions). Template repository.


## ForgeHouse 50 Release Updates (batch_fixes_oct12_v1)
- Auto-fill Book/Chapter/Verse in new notes from active reader passage and verse highlights.
- Offline Bible translations list in Profile screen is display-only.
- Admin dashboard redesign: scrollable tab bar, participant cards with full names and #xxxx tags, search/sort, overflow menu.
- Batch user deletion with DELETE keyword verification.
- Single user deletion without re-typing participant name.


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
