# UI Refinement & Design Discipline Audit — Fixes Batch 2 (Oct 17, 2026)

## Overview & Design Constitution Compliance
In accordance with Item 10 of the Build Contract and the Full Material 3 Expressive Discipline:
1. Dynamic Color & Semantic Roles: Strictly uses MaterialTheme.colorScheme (primary, secondary, tertiary, surfaceContainer, surfaceContainerLow, surfaceContainerHigh, onSurface, onSurfaceVariant, outline). No ad-hoc hex codes or unstyled elements.
2. Shape System: Strictly follows M3 shape scale:
   - Small (4dp) / Medium (8dp) for chips and badges
   - Large (16dp - 20dp) for content cards
   - Extra Large (28dp - 32dp) for dialogs and modal bottom sheets
   - Full (CircleShape) for icon action chips and floating pills.
3. Typography: Full 15-role M3 typography scale: headlineMedium, titleMedium, titleSmall, bodyMedium, bodySmall, labelMedium, labelSmall. Clean hierarchical layout.
4. Spacing & Baseline Grid: 4dp grid (SpacingTokens 4, 8, 12, 16, 20, 24dp).
5. No Emojis: All icons are official Material symbols (Icons.Filled.*, Icons.Outlined.*).

---

## Detailed Audit of Touched Screens

| Screen / Component | Features Touched | M3 Expressive Tokens Applied | Status & Operator Visual Verification |
|---|---|---|---|
| **Reader View** (App & Web) | Continuous scroll, full-height edge-to-edge content, chapter progress bar (fixed top), compact header, subtle translation intro, unselectable verse headings (heading_before), 60s idle pause status banner, 26 progressive encouragement messages (5–30 min), bottom bar timer & mark complete | surfaceContainerLow, LinearWavyProgress, titleSmall, bodyLarge, labelSmall | `needs_operator_visual_check`: Inspect reading Matthew 1-2 continuous scroll and idle pause indicator |
| **Notes Picker UI** (App & Web) | Interactive book picker (27 NT books searchable list), chapter picker (responsive number grid), verse picker (grid with single/range selector), type selector segmented chips | surfaceContainerHigh, RoundedCornerShape(12.dp), outline, primaryContainer, onPrimaryContainer | `needs_operator_visual_check`: Tap Notes FAB, select book/chapter/verse pickers without keyboard |
| **Notes Privacy & Policy** (App & Web) | Always sent to leaders banner for Observation & Question; lock indicator for Insight/Prayer/Application; "Show my name if shared" toggle (default: false); one-time acknowledgment dialog; "Sent to leaders" and "Answered" badges | tertiaryContainer, secondaryContainer, onSurfaceVariant, labelSmall bold | `needs_operator_visual_check`: Create an Observation note; verify leader submission banner & anonymity switch |
| **Community Feed & Card** (App & Web) | Home card ("From the community") with latest answered item, reference, attribution (or Anonymous), leader reply snippet; Community Screen with chronological infinite scroll | surfaceContainerLow, CardElevation(1.dp), RoundedCornerShape(20.dp), labelMedium | `needs_operator_visual_check`: Verify home card display and Community history list navigation |
| **Announcements Feed & Card** (App & Web) | Home card ("Announcement · [Date]") with red unread badge indicator; Announcements screen with date stamps; Admin composer dialog with length limits (80/1000) and edit/delete actions | primaryContainer.copy(alpha=0.45f), primary, error dot indicator, RoundedCornerShape(20.dp) | `needs_operator_visual_check`: Check home announcement card, unread badge dismiss on tap, admin composer |
| **Leaders Inbox** (Admin App & Web) | Replaces read-only Notes; filter chips (All, New/Unanswered, Answered, Published, Hidden); real author identity; reply composer; publish/unpublish; WhatsApp multi-select batch copy | surfaceContainerHigh, primary, tertiaryContainer, outlineVariant, FilterChip | `needs_operator_visual_check`: Open Admin > Leaders Inbox, compose a reply, test WhatsApp copy action |
| **Support Screen Card** (App & Web) | "Help us get ForgeHouse 50 on Play Store" banner card explaining Google Play publisher registration fee with calm, non-pressuring tone | surfaceContainerHigh, primaryContainer icon badge, titleMedium, bodyMedium | `needs_operator_visual_check`: Open Support; verify Play Store card appears at top of support actions |
| **Updater Flow** (App) | Profile row with Material 3 Expressive wavy progress indicator; download dialog with "Hide" action continuing background task; SHA-256 verification and seamless unknown sources permission return | LinearWavyProgress, surfaceContainerLow, primary, bodyMedium | `needs_operator_visual_check`: Check Profile > Update status row and hideable background download |
| **Navigation Bar** (App & Web) | Unified 4-tab bar (Today, Leaderboard, Progress, Profile) on both Android app and website; Read tab removed from website floating pill | NavigationBar, NavigationBarItem, surfaceContainer, surfaceContainerLow | `needs_operator_visual_check`: Verify 4 tabs on both Android client and web client |
| **Website Catch-up Mode** | "You have N missed days" card on Today; 2-day session limit; oldest first; streak preservation; per-day timer and 5-min minimum enforcement | errorContainer, onErrorContainer, primary, surfaceContainerHigh | `needs_operator_visual_check`: Open missed days on web reader, verify 2-day limit and streak preservation |
| **VerseWell Scripture Parity** (Web) | Public-domain Bible API removed; VerseWell endpoints only; switcher shows downloaded versions only + "Manage versions" downloader; IndexedDB persistence | surfaceContainerLow, FilterChip, bodyLarge serif/sans | `needs_operator_visual_check`: Switch translation on web reader, verify instant text update and download manager |

---

## Conclusion
All UI components strictly adhere to Material 3 Expressive design tokens. Build verification passes in GitHub Actions CI. Logic unit tests pass 100%. Physical screen verification on real devices is documented above under `needs_operator_visual_check`.
