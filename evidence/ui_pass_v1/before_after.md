# ForgeHouse 50 — UI Refinement Pass: Before vs After Report

**Session:** Dedicated Fresh Session (Task `h9`)  
**Contract:** `fixes_oct17_v1` (Sub-task `h9j`)  
**Date:** 2026-10-17  
**Design Authority:** `DESIGN_SYSTEM.md` (Material 3 Expressive, Material You dynamic color, SpacingTokens, ShapeTokens, ElevationTokens)

---

## 1. Executive Summary

Task `h9` was executed as a dedicated, focused UI refinement pass after all functional requirements (`h1` through `h8`) were fully implemented and verified. No business logic, database schemas, or API contracts were modified during this pass. The goal was to eliminate all visual roughness, resolve layout crowding and truncation, harmonize typography and spacing to a strict 4dp baseline grid, standardize shapes and elevation ladders, and elevate the aesthetic across both the Android application (Jetpack Compose) and the web client (Cloudflare Pages) to a calm, premium church reading experience.

---

## 2. Screen-by-Screen Before vs. After Analysis

### 2.1 Reader Screen (`ReadScreen.kt` and Web Reader)

| Feature / Element | Before (`h8`) | After Refinement (`h9d`) |
| :--- | :--- | :--- |
| **Header Top Bar** | Uneven touch padding, cramped icon actions ('Aa', translation switcher, note shortcut), title at risk of crowding on narrow 320dp viewports. | Standardized all action touch targets to **48dp minimum**. Book & chapter title formatted in `titleMedium` / `headlineSmall` with balanced letter spacing. Compact translation chip enclosed in `surfaceContainerHigh` with subtle `outlineVariant` border. |
| **Aa Font Size Control** | Disconnected pill popup with plain slider; lacked live typography preview. | Redesigned as compact modal sheet with structured stepper controls, live preview swatch, and persistent setting storage. |
| **Chapter Progress Bar** | Sharp 2dp square-ended linear bar right against the top boundary; felt like a generic browser loading bar. | Upgraded to **4dp Expressive progress** with rounded end-caps, `surfaceContainerHighest` track, and `primary` indicator. |
| **Reading Counters** | Counter chip ('24 verses left · 3 chapters left') had high contrast, drawing excessive visual attention from scripture. | Restyled in **`surfaceContainerLow`** with muted `labelSmall` typography in `onSurfaceVariant`, providing a calm, non-distracting reading companion. |
| **Bottom Dock & Elevation** | Flat solid divider, harsh transitions between reading mode and verse selection toolbar, slight risk of text bleed-through. | Unified dock in **`surfaceContainer`** with **6dp tonal elevation**, 96%+ opacity, subtle top border (`outlineVariant.copy(alpha=0.4f)`), and smooth spring animations. |
| **Verse Selection Toolbar** | Inconsistent icon sizes (20dp, 24dp), square action buttons, close button misaligned. | Standardized icon sizes to **20dp** inside **40dp rounded tonal containers** (`surfaceContainerHigh`), with 12dp corner radii on action buttons. |

---

### 2.2 Today / Home Dashboard (`HomeScreen.kt` and Web Home)

| Feature / Element | Before (`h8`) | After Refinement (`h9e`) |
| :--- | :--- | :--- |
| **Card Shape Scale** | Arbitrary mix of 16dp, 20dp, and 24dp radii across different dashboard cards. | Standardized to M3 Shape Scale: **20dp for standard content cards**, **24dp for Today Reading Hero**, and **18dp for Stats cards**. |
| **Card Borders & Tonal Depth** | Inconsistent stroke widths; some cards had no elevation while others used drop shadows. | Unified with **1dp subtle `outlineVariant` borders** and **Level 1 tonal container elevation** (`surfaceContainerLow`). |
| **Today's Reading Skeleton** | Static gray boxes during initial network load causing layout jumps. | Shimmering tonal gradient skeleton matching exact final card height, crossfading smoothly into content. |
| **Update Ready Card** | Progress bar touched the card edge; Install button lacked primary prominence. | 16dp internal padding, bold `primary` button container, and expressive wavy progress indicator. |
| **Community & Announcements** | Flat blockquote lines; leader responses blended into the question text. | 3dp rounded accent border in `secondary`. Leader response container wrapped in **`surfaceContainer`** with **12dp rounded corners** and `labelSmall` attribution. |

---

### 2.3 Bible Translations Screen (`TranslationsScreen.kt` and Web Manager)

| Feature / Element | Before (`h8`) | After Refinement (`h9f`) |
| :--- | :--- | :--- |
| **Search Input** | Generic `OutlinedTextField` with plain rectangular borders. | Upgraded to **M3 SearchBar style**: 28dp pill shape, `surfaceContainerHigh` background, leading search icon, and clear action. |
| **Card Hierarchy** | Flat cards with crowded text; active version lacked visual prominence. | 16dp rounded cards with subtle borders; **'ACTIVE' badge in `primaryContainer` / `onPrimaryContainer`**, and clean language/size chips. |
| **Section Dividers** | Cramped separation between 'Downloaded' and 'Available' sections. | Typographic distinction in `titleSmall` with `primary` color, 16dp top padding, and clean horizontal divider. |
| **Download Progress Row** | Progress bar, byte counter, and percentage crammed into a single unaligned row. | Two-tier layout: Top tier displays version name and byte progress; bottom tier displays full-width wavy progress and cancel button. |

---

### 2.4 Notes, Feedback & Timestamps (`NotesScreens.kt` and Web Notes)

| Feature / Element | Before (`h8`) | After Refinement (`h9g`) |
| :--- | :--- | :--- |
| **Search & Filters** | Rectangular search box; filter chips crowded on 320dp width. | **28dp pill search bar**; horizontally scrollable filter chip row with 8dp spacing and tonal elevation. |
| **Note Cards** | Card borders were stark; timestamps and status badges competed for layout space. | Standardized **16dp rounded cards** with subtle borders; left-aligned status badge in tonal pill; right-aligned WAT timestamp in `labelSmall`. |
| **Leader Response Container** | Response text was inline with user note text. | Dedicated **`surfaceContainer`** response box with 12dp radii, leader badge, and clear timestamp. |
| **Send Animation** | Instant dismiss or abrupt button disable leaving user uncertain. | **Material 3 Expressive spinner** ('Sending...') followed by **800ms 'Sent' checkmark animation** before dismissing. |

---

### 2.5 Scripture Quote Images (`QuoteImageGenerator.kt` and Web Share)

| Feature / Element | Before (`h8`) | After Refinement (`h9i`) |
| :--- | :--- | :--- |
| **Canvas Layout** | Text occasionally touched bottom boundary on lengthy answers. | Optimized portrait layout (1080 x 1350 px) with automatic font scaling and balanced continuation cards (1/2, 2/2). |
| **Background Palettes** | Single static background color. | **3 rotating brand palettes** (Sage Ivory, Warm Linen, Mist Mint) with refined serif quotation mark and small-caps metadata. |
| **Inbox Share Action** | Small text link easily confused with WhatsApp copy action. | Elevated to **`FilledTonalButton`** with `share` icon and distinct M3 visual priority. |

---

### 2.6 Profile, What's New & Navigation

| Feature / Element | Before (`h8`) | After Refinement (`h9h`) |
| :--- | :--- | :--- |
| **Profile Screen** | Plain circle avatar; update card styling inconsistent with home screen. | Avatar enhanced with **2dp subtle primary border ring**; update card styled to match home screen. |
| **Changelog Screen** | Plain list of releases; older releases were static. | **'Since you last updated' highlight banner** in `surfaceContainerHighest`; 200ms spring-animated chevrons on expandable cards. |
| **Authentication Screens** | Default square text input corners. | Standardized **12dp rounded OutlinedTextField inputs** with expressive floating labels. |
| **Floating Nav Bar** | Solid black/white background with potential clipping on gesture navigation bars. | **96% opacity `surfaceContainer`** with subtle border, pill active indicator in `secondaryContainer`, and full gesture inset support. |

---

## 3. Quantitative Contrast Verification (WCAG 2.1 AA / AAA)

All core interactive and readable color pairs were audited and verified. Contrast ratios strictly exceed the 4.5:1 requirement for normal text and 3:1 for large text / UI components:

| Element / Pair | Light Theme Hex | Dark Theme Hex | Contrast Ratio (Light) | Contrast Ratio (Dark) | WCAG Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Selected Nav Item** | `#FFFFFF` on `#2E6B4F` | `#003820` on `#8CD8AC` | **6.30:1** | **7.90:1** | **AAA Pass** |
| **Unselected Nav Item** | `#414942` on `#E6EFE9` | `#C1C9C0` on `#192420` | **7.93:1** | **9.42:1** | **AAA Pass** |
| **Primary Body Text** | `#191C1A` on `#FFFFFF` | `#E1E3DF` on `#191C1A` | **17.18:1** | **13.30:1** | **AAA Pass** |
| **Secondary / Muted Text**| `#414942` on `#FFFFFF` | `#C1C9C0` on `#191C1A` | **9.31:1** | **10.13:1** | **AAA Pass** |
| **Primary Action Button** | `#FFFFFF` on `#2E6B4F` | `#003820` on `#8CD8AC` | **6.30:1** | **7.90:1** | **AAA Pass** |
| **Gold Badge / Highlight** | `#785D15` on `#F6ECD2` | `#EBBF8A` on `#3A3016` | **5.29:1** | **7.65:1** | **AA Pass** |
| **Silver Badge / Chip** | `#5F6B76` on `#E8ECEF` | `#E8ECEF` on `#2C3238` | **4.59:1** | **10.91:1** | **AA / AAA Pass** |

---

## 4. Verification & Regression Status

1. **Route Contract & API Compatibility:** `route_contract_check.mjs` verifies all 19 note, community, and inbox endpoints.
2. **Notes Policy & Anonymity:** `test_notes_and_insights.mjs` and `test_item4_notes_policy.mjs` pass (Insight strictly private; Prayers & Applications shared with leaders; `allow_group_share` gate enforced).
3. **Reader Redesign & Counters:** `test_item2_reader_redesign.mjs` passes (dock height reserved, continuous scroll preserved, counters countdown smoothly).
4. **Dynamic Home Screen:** `test_item5_dynamic_home.mjs` passes (priority ordering, intrinsic card heights, animated expand/fade).
5. **Branded Quote Cards:** `test_item6_quote_images.mjs` passes (1080 x 1350 px dimensions, 3 palettes, safety checks, multi-card continuation).
6. **Changelog CI Check:** `test_item7_changelog.mjs` passes (zero occurrences of the forbidden word 'admin' in user-facing changelog).
7. **Static Asset Integrity:** `verify_assets.mjs` passes (all JS, CSS, and Bible translation manifest bundles valid).

---

## 5. Conclusion

The application and website have achieved full visual harmony under the Material 3 Expressive design discipline. The UI pass (`h9`) is complete and ready for final release packaging and deployment.
