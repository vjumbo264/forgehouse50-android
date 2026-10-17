# ForgeHouse 50 — Unified Material 3 Expressive Design System (Task h9c)

This document establishes the single authoritative design language for the ForgeHouse 50 Android app and web client, adhering strictly to the **Full Material 3 Expressive Discipline**.

---

## 1. Core Principles

1. **Calm, Scripture-First Experience**: Content always leads. Chrome, headers, timers, counters, and bottom docks recede into tonal backgrounds. Controls never draw focus away from reading God's Word.
2. **Zero-Pill Static Discipline**: Informational metadata (dates, chapters, statuses, counters) is presented as clean, unboxed text with typographic separators (`·`). Bordered and filled containers are reserved strictly for interactive controls (buttons, tabs, action chips).
3. **Strict Tonal Surfaces**: Elevation is communicated primarily through tone via `surfaceContainer` levels (0 to 5) rather than heavy drop shadows. High-elevation components (dialogs, sheets) use soft, subtle scrims and tonal elevation.
4. **Touch Target Standard**: All interactive touch targets strictly maintain a minimum `48dp x 48dp` hitbox (`44px x 44px` minimum on web).
5. **No Emojis**: Every indicator, category, and action utilizes official Material symbols and EngineIcons.
6. **Dark Mode Parity**: Complete visual parity across light and dark themes with WCAG AA (4.5:1 minimum) contrast for all body and interactive text.
7. **Accessibility & Reduced Motion**: All animations respect system animator duration and 'remove animations' settings, falling back to instant state changes.

---

## 2. Spacing Scale (4dp Baseline Grid)

All padding, margins, and gaps are derived strictly from the 4dp baseline grid via `SpacingTokens`:

| Token | Value | Primary Usage |
| :--- | :--- | :--- |
| `Spacing.xxs` | 4dp | Tight icon-to-label gaps, badge inner padding |
| `Spacing.xs` | 8dp | Chip padding, list item internal spacing, text-to-subtext gap |
| `Spacing.sm` | 12dp | Compact card padding, button horizontal spacing |
| `Spacing.md` | 16dp | Canonical page margin (compact <600dp), card interior padding, card-to-card vertical gap |
| `Spacing.lg` | 24dp | Canonical page margin (medium/expanded ≥600dp), section separation |
| `Spacing.xl` | 32dp | Hero element spacing, modal top/bottom margins |
| `Spacing.xxl` | 40dp | Auth screen vertical rhythm |
| `Spacing.xxxl` | 48dp | Minimum touch target bound, standard toolbar height |

---

## 3. Shape Scale

Applied strictly by component role via `ShapeTokens` and `MaterialTheme.shapes`:

| Tier | Radius | Component Roles |
| :--- | :--- | :--- |
| `extraSmall` | 4dp | Status dots, small indicators |
| `small` | 8dp | Chips, filter pills, small badges |
| `medium` | 12dp | Text fields, drop-down menus, inner response containers |
| `large` | 16dp | Standard cards, action rows |
| `largeIncreased` | 20dp | Featured cards (Today's Reading, Update Ready, Community) |
| `extraLarge` | 28dp | Search bars, bottom dock corner rounding |
| `extraLargeIncreased` | 32dp | Modal bottom sheets, full dialogs |
| `Full` (Circle) | 9999dp | Floating pill navigation, icon buttons, avatar rings |

---

## 4. Typography Hierarchy (Plus Jakarta Sans)

Configured with explicit `FontVariation.Settings` across all 15 official M3 typography roles:

| Role | Weight | Size | Line Height | Usage |
| :--- | :--- | :--- | :--- | :--- |
| `displayLarge` | 700 (Bold) | 57sp | 64sp | Splash & milestone screens |
| `displayMedium` | 700 (Bold) | 45sp | 52sp | Quiz completion scores |
| `displaySmall` | 700 (Bold) | 36sp | 44sp | Timer active display |
| `headlineLarge` | 700 (Bold) | 32sp | 40sp | Major screen titles (Today, Leaderboard) |
| `headlineMedium` | 700 (Bold) | 28sp | 36sp | Dialog headers |
| `headlineSmall` | 600 (SemiBold) | 24sp | 32sp | Section titles, chapter headers in reader |
| `titleLarge` | 700 (Bold) | 22sp | 28sp | Card titles (Today's Reading) |
| `titleMedium` | 600 (SemiBold) | 16sp | 24sp | List headers, passage reference |
| `titleSmall` | 600 (SemiBold) | 14sp | 20sp | Subsection labels, chip text |
| `bodyLarge` | 500 (Medium) | 16sp | 24sp | Scripture prose (reader text) |
| `bodyMedium` | 500 (Medium) | 14sp | 20sp | Card body descriptions, notes text |
| `bodySmall` | 500 (Medium) | 12sp | 16sp | Footnotes, secondary descriptions |
| `labelLarge` | 600 (SemiBold) | 14sp | 20sp | Buttons, prominent action labels |
| `labelMedium` | 600 (SemiBold) | 12sp | 16sp | Badges, counter chips, navigation labels |
| `labelSmall` | 600 (SemiBold) | 11sp | 16sp | Timestamps (WAT), unread indicators |

---

## 5. Color Palette & Semantic Tones

Dynamic Color (API 31+) with fallback brand tokens:

### Light Mode
- **Canvas / Background**: `#F9F9F6`
- **Surface**: `#FFFFFF`
- **SurfaceContainerLow**: `#F3F4F1` (Card backgrounds)
- **SurfaceContainer**: `#ECEEEA` (Dock surface, modal sheet)
- **SurfaceContainerHigh**: `#E6E8E4` (Search bars, elevated rows)
- **SurfaceContainerHighest**: `#DFE2DE` (Skeleton loaders, highlighted banners)
- **Primary**: `#2E6B4F` (Deep Forest Green)
- **PrimaryContainer**: `#D3EEDF` / **OnPrimaryContainer**: `#133B28`
- **Secondary**: `#4E6355` (Sage)
- **SecondaryContainer**: `#D1E8D7` / **OnSecondaryContainer**: `#0C1F15`
- **Tertiary**: `#7A5930` (Warm Ochre)
- **Outline**: `#727971` / **OutlineVariant**: `#C1C9C0`

### Dark Mode
- **Canvas / Background**: `#111412`
- **Surface**: `#191C1A`
- **SurfaceContainerLow**: `#1D211E`
- **SurfaceContainer**: `#212522`
- **SurfaceContainerHigh**: `#2B302C`
- **SurfaceContainerHighest**: `#363B37`
- **Primary**: `#8CD8AC` (Mint Emerald)
- **PrimaryContainer**: `#145235` / **OnPrimaryContainer**: `#A8F5C7`
- **Secondary**: `#B5CCBC` / **SecondaryContainer**: `#374B3E`
- **Tertiary**: `#EBBF8A`
- **Outline**: `#8B938A` / **OutlineVariant**: `#414942`

### Leaderboard Podium
- **Gold**: `#8A6D1F` (Light: `#F6ECD2`, Dark: `#3A3016`)
- **Silver**: `#5F6B76` (Light: `#E8ECEF`, Dark: `#2C3238`)
- **Bronze**: `#8A5A33` (Light: `#F1E2D3`, Dark: `#382718`)

---

## 6. Motion & Transitions

- **Global Scheme**: `MotionScheme.expressive()` with spring physics.
- **Card Insert / Remove**: Expand/collapse vertically with smooth fade (`expandVertically() + fadeIn()`).
- **Screen Transitions**: Subtlest spatial slide and crossfade.
- **Loading / Buttons**: Morphing shape progress via official `LoadingIndicator`.
