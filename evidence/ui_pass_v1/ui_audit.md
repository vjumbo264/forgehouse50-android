# UI Refinement Pass — Comprehensive Baseline Audit (Task h9b)
Date: 2026-10-17
Session: Dedicated Fresh Session (Task h9)
Baseline Release: forgehouse50-v31 (commit 8a42d9d / 7f0c45a)

---

## Executive Summary

Tasks h1 through h8 repaired the functional regressions (Bible translations format break, reader overlap, updater resilient state machine, prayer/application sharing, WAT timestamps, dynamic home ordering, changelog zero-admin CI check, and branded quote image generator). 

Following the strict protocol of Task h9, this audit catalogues every visual, structural, typographic, and motion defect across all 7 core screen groups in both the Android app and web client before beginning the refinement pass.

---

## 1. Reader Screen (`ReadScreen.kt` and Web Reader)

| Element / Area | Current Defect | Target Material 3 Expressive Standard |
| :--- | :--- | :--- |
| **Header Top Bar** | Action icons ('Aa' text size, translation switcher chip, note button) have uneven touch padding and lack cohesive tonal grounding. Title can feel crowded on small screens (320dp). | Standardize all action touch targets to 48dp minimum. Book & chapter title formatted in `titleMedium` / `headlineSmall` with balanced letter spacing. Compact translation chip enclosed in `surfaceContainerHigh` with subtle `outlineVariant` border. |
| **Chapter Progress Line** | Thin 2dp linear progress bar hugs the top edge with sharp squared caps; looks like a browser loading indicator rather than an organic reading component. | Expressive linear progress with 4dp height, rounded end caps, `surfaceContainerHighest` track, and `primary` indicator. |
| **Reading Counters** | The 'verses left · chapters left' chip has high contrast and draws focus from scripture. | Style with `surfaceContainerLow`, subtle `outlineVariant.copy(alpha=0.5f)`, and muted `labelSmall` typography in `onSurfaceVariant`. |
| **Bottom Dock** | The bottom dock is 98% opaque but uses a harsh 1dp solid divider line. During transition between reading state and selection toolbar, height jump can feel abrupt. | Softer tonal elevation with subtle `outlineVariant.copy(alpha=0.4f)` top border. Spring physics animation via `MotionTokens` for height changes. Timer display in reading state and selection tools aligned on 48dp touch grid. |
| **Verse Selection Toolbar** | Close ('X') button, Question, and Add Note buttons use disparate icon sizes (20dp, 24dp). | Standardize icon sizes to 20dp in 40dp rounded tonal containers (`surfaceContainerHigh`). |

---

## 2. Today / Home Dashboard (`HomeScreen.kt` and Web Home)

| Element / Area | Current Defect | Target Material 3 Expressive Standard |
| :--- | :--- | :--- |
| **Card Spacing & Margins** | Vertical spacing between cards varies (12dp, 16dp). Cards feel slightly compressed. | Apply strict `SpacingTokens.Medium` (16dp) between all cards and 16dp horizontal page margin (24dp on medium/expanded screens). |
| **Corner Radii** | Inconsistent radii: some cards use 16dp, others 20dp, others 24dp. | Standardize: Content cards = `RoundedCornerShape(20.dp)`; modal sheets = `RoundedCornerShape(28.dp)`; chips/pills = `RoundedCornerShape(8.dp)` or `CircleShape`. |
| **Today's Reading Skeleton** | Loading skeleton uses flat gray blocks that do not communicate subtle breathing space. | Shimmering tonal gradient between `surfaceContainer` and `surfaceContainerHigh`. |
| **Update Ready Card** | Linear wavy progress bar hugs card edges; Install button lacks strong primary emphasis. | Proper 16dp internal card padding. `ButtonDefaults.buttonColors` with `primary` container, and wavy progress in `primary`. |
| **Community & Announcement Cards** | Blockquote accent border on quote snippets is thin (1dp) with sharp corners; leader reply lacks tonal separation. | 3dp rounded accent border in `secondary`. Leader response container wrapped in `surfaceContainer` with `labelSmall` attribution. |

---

## 3. Bible Translations Screen (`TranslationsScreen.kt` and Web Manager)

| Element / Area | Current Defect | Target Material 3 Expressive Standard |
| :--- | :--- | :--- |
| **Search Input** | Search field uses basic OutlinedTextField with generic placeholder styling. | Elevate to M3 SearchBar style: `surfaceContainerHigh` background, 28dp pill shape, leading search icon, clear button on input. |
| **Section Dividers** | 'Downloaded' and 'Available' sections lack typographic distinction and breathing room. | `titleSmall` in `primary`, 16dp top padding, clean `HorizontalDivider` in `outlineVariant.copy(alpha=0.4f)`. |
| **Download Progress Row** | Live progress row packs wavy bar, percentage, and bytes text into a crowded single line. | Two-tier layout: Top line with version title and bytes/percent counter; bottom line with full-width wavy progress bar and cancel button. |
| **Badges** | 'Built in' and 'Active' badges use disparate color schemes and font sizes. | Harmonized tonal badges: 'Active' in `primaryContainer` / `onPrimaryContainer`, 'Built in' in `surfaceVariant` / `onSurfaceVariant`. |

---

## 4. Notes, Feedback, & Modals (`NotesScreens.kt`, `SendFeedbackState`)

| Element / Area | Current Defect | Target Material 3 Expressive Standard |
| :--- | :--- | :--- |
| **Scripture Pickers** | Book, Chapter, and Verse dropdown menus have inconsistent padding and tight touch targets on small screens. | Standard 48dp touch targets with rounded exposed menus (`RoundedCornerShape(12.dp)`) and clean searchable sheet. |
| **Filter Chips Row** | Note type filter chips (All, Observation, Question, etc.) have cramped horizontal spacing on 320dp width. | Scrollable row with 8dp item spacing and `FilterChipDefaults` tonal elevation. |
| **Timestamps & Badges** | Note card timestamps and status badges ('Sent', 'Answered', 'Private') compete for space. | Clean footer layout: Left-aligned status badge with tonal pill; right-aligned WAT timestamp in `labelSmall` / `onSurfaceVariant`. |
| **Send Animation** | Button transition between 'Sending...' and 'Sent' checkmark can flicker on rapid network completion. | Enforce minimum 800ms display for 'Sent' checkmark with expressive spring scale-in. |

---

## 5. Scripture Quote Cards (`QuoteImageGenerator.kt` & Admin UI)

| Element / Area | Current Defect | Target Material 3 Expressive Standard |
| :--- | :--- | :--- |
| **Preview Dialog** | Image preview dialog on phone screens stretches card close to edges without elevation. | Rounded preview container (`RoundedCornerShape(16.dp)`) with subtle shadow and clear bottom action row. |
| **Action Buttons** | 'Share as image' in inbox cards is small and easily missed next to WhatsApp copy. | Standard icon button with `AssistChip` or `FilledTonalButton` styling. |

---

## 6. Profile & 'What's New' Changelog (`ProfileScreen.kt`, `ChangelogScreen.kt`)

| Element / Area | Current Defect | Target Material 3 Expressive Standard |
| :--- | :--- | :--- |
| **Profile Avatar** | Avatar lacks refined boundary accent. | 2dp subtle `primary` border ring with tonal container. |
| **'Since You Last Updated'** | Summary card at top of changelog uses plain card styling. | Highlighted banner card in `surfaceContainerHighest` with subtle brand accent border. |
| **Older Release Cards** | Expandable release cards lack animated chevron rotation. | Smooth 200ms spring rotation on expand/collapse chevron. |

---

## 7. Leaderboard, Progress, & Auth Screens

| Element / Area | Current Defect | Target Material 3 Expressive Standard |
| :--- | :--- | :--- |
| **Podium Cards** | Top 3 podium items (Gold, Silver, Bronze) need refined tonal container backgrounds. | Harmonized podium container colors (`PodiumColors`) with smooth contrast in both light and dark themes. |
| **Auth Inputs** | Login & register text fields have sharp edges and default focus rings. | M3 `OutlinedTextField` with `RoundedCornerShape(12.dp)` and expressive floating labels. |
| **Floating Nav Bar** | Floating pill nav bar needs refined blur/tonal surface and consistent active indicator pills. | `surfaceContainer` with 96% opacity, pill active indicator in `secondaryContainer`, no clipping on gesture nav. |

---

## Conclusion

Every screen has identifiable visual improvements that can be resolved without changing any business logic or API contracts. Proceed to sub-task `h9c` (DESIGN_SYSTEM.md and shared tokens/components).
