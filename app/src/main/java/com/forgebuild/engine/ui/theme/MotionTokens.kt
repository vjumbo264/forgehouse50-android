package com.forgebuild.engine.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Engine motion discipline — OFFICIAL Material 3 Expressive motion physics.
 *
 * RESEARCH BASIS (BUILD_STATE.json → forgebuild-material-discipline.step-01):
 * - androidx source MotionScheme.kt: two official schemes — standard()
 *   (utilitarian, linear feel) and expressive() (spring-based, for prominent
 *   UI). Each exposes SIX specs: default/fast/slow × SPATIAL (bounds/shape
 *   changes) and EFFECTS (color/alpha). Specs are springs built from the
 *   library's StandardMotionTokens / ExpressiveMotionTokens damping+stiffness.
 * - Transition PATTERNS (container transform, shared axis x/y/z, fade-through,
 *   fade) map onto these specs and are provided by the navigation/adaptive
 *   libraries, not material3 core — the Engine standardizes component-level
 *   motion on this scheme rather than hand-rolling transitions.
 *
 * ForgeBuildTheme already applies [MotionScheme.expressive()] globally, so
 * EVERY interactive component inherits official spring motion. These accessors
 * expose the same specs for app-level custom animations so custom motion
 * matches the system instead of inventing ad-hoc curves/durations.
 */
object MotionTokens {

    /** Spatial = animates position/size/shape/bounds. Effects = color/alpha. */
    val defaultSpatial: FiniteAnimationSpec<Any>
        @Composable @ReadOnlyComposable get() = MaterialTheme.motionScheme.defaultSpatialSpec()
    val fastSpatial: FiniteAnimationSpec<Any>
        @Composable @ReadOnlyComposable get() = MaterialTheme.motionScheme.fastSpatialSpec()
    val slowSpatial: FiniteAnimationSpec<Any>
        @Composable @ReadOnlyComposable get() = MaterialTheme.motionScheme.slowSpatialSpec()
    val defaultEffects: FiniteAnimationSpec<Any>
        @Composable @ReadOnlyComposable get() = MaterialTheme.motionScheme.defaultEffectsSpec()
    val fastEffects: FiniteAnimationSpec<Any>
        @Composable @ReadOnlyComposable get() = MaterialTheme.motionScheme.fastEffectsSpec()
    val slowEffects: FiniteAnimationSpec<Any>
        @Composable @ReadOnlyComposable get() = MaterialTheme.motionScheme.slowEffectsSpec()

    /** The active scheme (expressive under ForgeBuildTheme). */
    val scheme: MotionScheme
        @Composable @ReadOnlyComposable get() = MaterialTheme.motionScheme
}
