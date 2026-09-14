package com.forgebuild.engine.permissions

import android.Manifest
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * ForgeBuild permission wiring — the FULL practical Android permission surface
 * for a personal app-builder (Correction Round 2, Fix 4).
 *
 * Every capability remains STRICTLY OPT-IN per app: the generating AI declares
 * the matching <uses-permission> entries in AndroidManifest.xml and calls the
 * matching [PermissionWiring] request ONLY when the app's description
 * genuinely needs it. Nothing is declared or requested by default. Special/
 * signature-tier capabilities (all-files access, overlay, accessibility,
 * notification listener, VPN, device admin, install-unknown-apps, exact
 * alarms) each have a dedicated settings/policy flow here — never a silent
 * grant assumption.
 *
 * EXTENSIBILITY: adding one more permission later = one new [EnginePermission]
 * enum entry (its manifest strings, gated by Build.VERSION where the
 * permission is version-split) plus, for special-tier ones, one small
 * settings-intent helper in [PermissionWiring]. No redesign required.
 */
enum class EnginePermission(val manifests: List<String>) {
    /** Media/file reads. Scoped-media model on 13+, legacy read below that. */
    STORAGE(
        if (Build.VERSION.SDK_INT >= 33)
            listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
        else listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    ),
    CAMERA(listOf(Manifest.permission.CAMERA)),
    NOTIFICATIONS(
        if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()
    ),
    FOREGROUND_SERVICE(
        buildList {
            add(Manifest.permission.FOREGROUND_SERVICE)
            if (Build.VERSION.SDK_INT >= 34) add(Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE)
        }
    ),
    /** Background location = foreground grant first, then a Settings flow (see requestBackgroundLocation). */
    BACKGROUND_LOCATION(listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)),
    /** Foreground location only (fine + coarse). */
    LOCATION(listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)),
    MICROPHONE(listOf(Manifest.permission.RECORD_AUDIO)),
    CONTACTS(listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)),
    CALENDAR(listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)),
    /** Call log + phone state (READ_CALL_LOG / READ_PHONE_STATE; WRITE_CALL_LOG rarely justified). */
    PHONE(listOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG)),
    SMS(listOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS)),
    BODY_SENSORS(listOf(Manifest.permission.BODY_SENSORS)),
    ACTIVITY_RECOGNITION(
        if (Build.VERSION.SDK_INT >= 29) listOf(Manifest.permission.ACTIVITY_RECOGNITION) else emptyList()
    ),
    /** Bluetooth incl. the Android 12+ (31+) never-for-location scan/connect split. */
    BLUETOOTH(
        if (Build.VERSION.SDK_INT >= 31)
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
        else listOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
    ),
    NFC(listOf(Manifest.permission.NFC)),
    /** BiometricPrompt — no manifest entry; gate on BiometricManager.canAuthenticate (see canUseBiometric). */
    BIOMETRIC(emptyList()),
    /** Exact alarms — Android 12+ special access, Settings flow (see requestExactAlarm). */
    EXACT_ALARM(
        if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.USE_EXACT_ALARM)
        else if (Build.VERSION.SDK_INT >= 31) listOf(Manifest.permission.SCHEDULE_EXACT_ALARM)
        else emptyList()
    ),
    /** MANAGE_EXTERNAL_STORAGE — all-files access. SPARINGLY: only when the app's description genuinely
     *  needs whole-storage management (file-manager class). Settings flow, never default. See requestAllFilesAccess. */
    ALL_FILES_ACCESS(emptyList()),
    /** Install-unknown-apps — Settings flow (see requestInstallUnknownApps). */
    INSTALL_UNKNOWN_APPS(listOf(Manifest.permission.REQUEST_INSTALL_PACKAGES)),
    /** System-alert-window / overlay — Settings flow (see requestOverlay). */
    OVERLAY(listOf(Manifest.permission.SYSTEM_ALERT_WINDOW)),
    /** AccessibilityService — policy-driven; service declared in manifest + res/xml config, enabled by user in Settings (see requestAccessibility). */
    ACCESSIBILITY_SERVICE(emptyList()),
    /** NotificationListenerService — policy-driven; enabled by user in Settings (see requestNotificationListener). */
    NOTIFICATION_LISTENER(emptyList()),
    /** VpnService — no manifest permission; user consent via VpnService.prepare() (see requestVpn). */
    VPN_SERVICE(emptyList()),
    // DEVICE_ADMIN — policy-driven, not a runtime permission; receiver wired to res/xml/device_admin.xml.
    DEVICE_ADMIN(emptyList())
}

object PermissionWiring {
    fun isGranted(ctx: Context, p: String): Boolean =
        ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    fun missing(ctx: Context, perm: EnginePermission): List<String> =
        perm.manifests.filter { !isGranted(ctx, it) }

    /** Request runtime permissions for a capability; no-op if already granted. */
    fun request(activity: Activity, perm: EnginePermission, requestCode: Int = perm.ordinal + 100) {
        val toAsk = missing(activity, perm)
        if (toAsk.isNotEmpty()) ActivityCompat.requestPermissions(activity, toAsk.toTypedArray(), requestCode)
    }

    /** Android 11+: background location must be granted from Settings after the foreground grant. */
    fun requestBackgroundLocation(activity: Activity) {
        request(activity, EnginePermission.BACKGROUND_LOCATION)
        if (Build.VERSION.SDK_INT >= 30 &&
            !isGranted(activity, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        ) {
            activity.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(
                    Uri.fromParts("package", activity.packageName, null)
                )
            )
        }
    }

    /** Device admin: prompt to activate the app's DeviceAdminReceiver. */
    fun requestDeviceAdmin(activity: Activity, receiver: Class<*>, explanation: String) {
        val dpm = activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val comp = ComponentName(activity, receiver)
        if (!dpm.isAdminActive(comp)) {
            activity.startActivity(
                Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, comp)
                    .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, explanation)
            )
        }
    }

    /**
     * Biometric (BiometricPrompt): true if the device can strongly authenticate.
     * Reflection-based so the lean Engine carries NO biometric dependency by
     * default; an app that actually uses biometrics adds
     * `androidx.biometric:biometric` itself (justified in its BUILD_STATE.json)
     * and drives `BiometricPrompt` directly — BIOMETRIC_STRONG == 0x000f.
     */
    fun canUseBiometric(ctx: Context): Boolean = runCatching {
        val bm = Class.forName("androidx.biometric.BiometricManager")
        val from = bm.getMethod("from", Context::class.java).invoke(null, ctx)
        val can = bm.getMethod("canAuthenticate", Int::class.javaPrimitiveType).invoke(from, 0x000f) as Int
        can == 0 // BiometricManager.BIOMETRIC_SUCCESS
    }.getOrDefault(false)

    /** Exact alarms (Android 12+): opens the special-access screen if not already allowed. */
    fun requestExactAlarm(activity: Activity) {
        if (Build.VERSION.SDK_INT >= 31) {
            val am = activity.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            if (!am.canScheduleExactAlarms()) {
                activity.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                        .setData(Uri.fromParts("package", activity.packageName, null))
                )
            }
        }
    }

    /** All-files access (MANAGE_EXTERNAL_STORAGE): Settings flow. Use ONLY for file-manager-class needs. */
    fun requestAllFilesAccess(activity: Activity) {
        if (Build.VERSION.SDK_INT >= 30 && !android.os.Environment.isExternalStorageManager()) {
            activity.startActivity(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    .setData(Uri.fromParts("package", activity.packageName, null))
            )
        }
    }

    /** Install-unknown-apps: Settings flow (Android 8+). */
    fun requestInstallUnknownApps(activity: Activity) {
        if (Build.VERSION.SDK_INT >= 26 && !activity.packageManager.canRequestPackageInstalls()) {
            activity.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.fromParts("package", activity.packageName, null))
            )
        }
    }

    /** System-alert-window / overlay: Settings flow. */
    fun requestOverlay(activity: Activity) {
        if (!Settings.canDrawOverlays(activity)) {
            activity.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    .setData(Uri.fromParts("package", activity.packageName, null))
            )
        }
    }

    /** Accessibility service: user must enable it in Settings (no programmatic grant). */
    fun requestAccessibility(activity: Activity) {
        activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    /** Notification listener: user must enable it in Settings (no programmatic grant). */
    fun requestNotificationListener(activity: Activity) {
        activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    /**
     * VPN service: returns VpnService.prepare()'s consent Intent (launch it with
     * startActivityForResult / ActivityResultLauncher), or null if consent is
     * already held and the app may start its VpnService directly.
     */
    fun requestVpn(activity: Activity): Intent? = android.net.VpnService.prepare(activity)
}
