package com.forgebuild.engine.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Engine shape discipline — OFFICIAL Material 3 / M3 Expressive shape scale.
 *
 * RESEARCH BASIS (BUILD_STATE.json → forgebuild-material-discipline.step-01):
 * - developer.android.com/develop/ui/compose/designsystems/material3 + m3
 *   shape docs: the scale is extraSmall 4dp, small 8dp, medium 12dp,
 *   large 16dp, extraLarge 28dp. M3 Expressive adds largeIncreased 20dp,
 *   extraLargeIncreased 32dp, extraExtraLarge 48dp.
 * - Shape is applied BY COMPONENT ROLE through the [Shapes] theme token —
 *   components read their tier from MaterialTheme.shapes; you do NOT pass a
 *   per-component radius.
 * - For DECORATIVE shapes (avatars, image crops, hero elements) M3 Expressive
 *   ships the official 35-shape MaterialShapes RoundedPolygon library (cookie,
 *   clover, arch, gem, oval, pill, burst, flower, ...) plus Morph for
 *   shape-morph animation. Use those — never hand-roll polygons.
 *
 * ENGINE STATUS: ForgeBuildTheme already applies [Shapes()] defaults, which on
 * the pinned material3 1.5.0-alpha train INCLUDE the expressive increased
 * tiers. This file exists to (a) pin the exact scale in code for auditability
 * and (b) give future generated apps one obvious place to read the scale from.
 */
object ShapeTokens {

    /** Official corner radii by tier (dp). */
    val extraSmall = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val largeIncreased = 20.dp
    val extraLarge = 28.dp
    val extraLargeIncreased = 32.dp
    val extraExtraLarge = 48.dp

    /**
     * The full official expressive shape scale. Identical to the library
     * defaults on the pinned train; declared explicitly so the scale is
     * auditable and survives library refactors.
     */
    val expressive: Shapes = Shapes(
        extraSmall = RoundedCornerShape(extraSmall),
        small = RoundedCornerShape(small),
        medium = RoundedCornerShape(medium),
        large = RoundedCornerShape(large),
        largeIncreased = RoundedCornerShape(largeIncreased),
        extraLarge = RoundedCornerShape(extraLarge),
        extraLargeIncreased = RoundedCornerShape(extraLargeIncreased),
        extraExtraLarge = RoundedCornerShape(extraExtraLarge),
    )
}
