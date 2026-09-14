package com.forgebuild.engine.security

import android.app.Activity
import android.view.WindowManager

/**
 * ForgeBuild screenshot / screen-recording prevention (Correction Round 2, Fix 4).
 *
 * Applies [WindowManager.LayoutParams.FLAG_SECURE] to a window, which blocks:
 *   - screenshots (the capture comes out black),
 *   - screen recordings of the window,
 *   - the window's appearance in the recent-apps thumbnail.
 *
 * IMPORTANT — apply this PER SCREEN, not app-wide by default. The operator's
 * need is "certain parts" of an app (a vault screen, a private-entry detail
 * screen, a payment screen), not necessarily the whole app. Recipe:
 *
 *   - Single-activity Compose app: hoist a boolean (e.g. the current route's
 *     sensitivity) and call [ScreenSecurity.setSecure] from a LaunchedEffect
 *     keyed on that boolean, so navigating away re-enables screenshots:
 *
 *       LaunchedEffect(isSensitiveRoute) {
 *           ScreenSecurity.setSecure(activity, isSensitiveRoute)
 *       }
 *
 *   - Multi-activity app: call [ScreenSecurity.apply] in onCreate() of exactly
 *     the sensitive activities (and [ScreenSecurity.clear] if a screen can
 *     toggle sensitivity at runtime).
 *
 * Do NOT put FLAG_SECURE in a base activity or theme that every screen
 * inherits unless the operator explicitly asked for app-wide protection —
 * blanket FLAG_SECURE also breaks legitimate uses (the operator's own
 * screenshots for support, casting, accessibility screenshots).
 */
object ScreenSecurity {

    /** Make this activity's window non-capturable until cleared or destroyed. */
    fun apply(activity: Activity) {
        activity.window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
    }

    /** Re-allow screenshots/recordings of this activity's window. */
    fun clear(activity: Activity) {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    /** Toggle helper: secure when [secure] is true, clear otherwise. */
    fun setSecure(activity: Activity, secure: Boolean) {
        if (secure) apply(activity) else clear(activity)
    }
}
