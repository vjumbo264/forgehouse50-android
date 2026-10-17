package com.forgebuild.engine.ui.theme

import androidx.compose.material3.Typography

/**
 * Engine typography discipline — OFFICIAL Material 3 type scale.
 *
 * RESEARCH BASIS (BUILD_STATE.json → forgebuild-material-discipline.step-01):
 * - developer.android.com/develop/ui/compose/designsystems/material3: the full
 *   15-role scale (Roboto size/line-height) —
 *   display 57/64, 45/52, 36/44 · headline 32/40, 28/36, 24/32 ·
 *   title 22/28(M), 16/24(M), 14/20(M) · body 16/24, 14/20, 12/16 ·
 *   label 14/20(M), 12/16(M), 11/16(M).
 * - Type is applied BY ROLE via MaterialTheme.typography — never per-screen
 *   ad-hoc font sizes. M3 Expressive additionally pairs emphasized variants
 *   with the motion system; those ride on this same scale.
 *
 * The library default [Typography()] implements exactly this scale. We declare
 * it explicitly so the Engine's type contract is pinned, auditable, and the
 * obvious single place to extend (e.g. a brand typeface) without breaking roles.
 */
object TypographyTokens {

    /**
     * The full official 15-role type scale. Pass to the theme as
     * `typography = TypographyTokens.scale`. Equivalent to [Typography] today;
     * explicit so future edits stay role-disciplined.
     */
    val scale: Typography = Typography()
}
