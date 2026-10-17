package com.forgebuild.forgehouse50.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Secure on-device storage for the ForgeHouse 50 session token
 * (the backend's `fh50_session` cookie value) plus small user prefs.
 *
 * The token lives in EncryptedSharedPreferences (AES-256 via the Android
 * Keystore master key) — never plain SharedPreferences, per the operator's
 * requirement. The token is sent to the API as a `Cookie: fh50_session=...`
 * header on every authenticated request.
 */
class SessionStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "fh50_secure_session",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var sessionToken: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_TOKEN) else putString(KEY_TOKEN, value)
        }.apply()

    var userId: String?
        get() = prefs.getString(KEY_USER_ID, null)
        set(value) = prefs.edit().putString(KEY_USER_ID, value).apply()
    var userName: String?
        get() = prefs.getString(KEY_NAME, null)
        set(value) = prefs.edit().putString(KEY_NAME, value).apply()

    var userRole: String?
        get() = prefs.getString(KEY_ROLE, null)
        set(value) = prefs.edit().putString(KEY_ROLE, value).apply()

    /** Currently selected VerseWell translation id (e.g. "versewell-kjv"). */
    var translationId: String?
        get() = prefs.getString(KEY_TRANSLATION, null)
        set(value) = prefs.edit().putString(KEY_TRANSLATION, value).apply()

    /** Last version manifest value we prompted about (update supersession). */
    var updatePromptedFor: String?
        get() = prefs.getString(KEY_UPDATE_PROMPTED, null)
        set(value) = prefs.edit().putString(KEY_UPDATE_PROMPTED, value).apply()

    /** Epoch millis when the update prompt was last shown (per-day cadence). */
    var updatePromptShownAt: Long
        get() = prefs.getLong(KEY_UPDATE_SHOWN_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_UPDATE_SHOWN_AT, value).apply()

    /** Reader font-size scale (0.85..1.6), persisted across app restarts. */
    var fontScale: Float
        get() = prefs.getFloat(KEY_FONT_SCALE, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_FONT_SCALE, value).apply()

    /** Epoch millis when we last checked the network for an update (throttled to 30 min). */
    var lastUpdateCheckAt: Long
        get() = prefs.getLong(KEY_LAST_UPDATE_CHECK_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_UPDATE_CHECK_AT, value).apply()

    /** Epoch millis when the system notification for update was last posted (at most once/day). */
    var lastUpdateNotifAt: Long
        get() = prefs.getLong(KEY_LAST_UPDATE_NOTIF_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_UPDATE_NOTIF_AT, value).apply()

    /** Epoch millis when the user tapped "Later" on the update prompt in the current session. */
    var updateLaterDismissedAt: Long
        get() = prefs.getLong(KEY_UPDATE_LATER_DISMISSED_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_UPDATE_LATER_DISMISSED_AT, value).apply()

    /** Cached JSON string of the latest AppRelease response for offline checks. */
    var cachedReleaseJson: String?
        get() = prefs.getString(KEY_CACHED_RELEASE_JSON, null)
        set(value) = prefs.edit().putString(KEY_CACHED_RELEASE_JSON, value).apply()

    var keepAliveExplained: Boolean
        get() = prefs.getBoolean(KEY_KEEPALIVE_EXPLAINED, false)
        set(value) = prefs.edit().putBoolean(KEY_KEEPALIVE_EXPLAINED, value).apply()

    /** Downloaded update metadata persisted across app restarts */
    var downloadedUpdateVersionCode: Int
        get() = prefs.getInt(KEY_DOWNLOADED_UPDATE_VC, 0)
        set(value) = prefs.edit().putInt(KEY_DOWNLOADED_UPDATE_VC, value).apply()

    var downloadedUpdatePath: String?
        get() = prefs.getString(KEY_DOWNLOADED_UPDATE_PATH, null)
        set(value) = prefs.edit().putString(KEY_DOWNLOADED_UPDATE_PATH, value).apply()

    var downloadedUpdateSha256: String?
        get() = prefs.getString(KEY_DOWNLOADED_UPDATE_SHA256, null)
        set(value) = prefs.edit().putString(KEY_DOWNLOADED_UPDATE_SHA256, value).apply()

    var downloadedUpdateVersionName: String?
        get() = prefs.getString(KEY_DOWNLOADED_UPDATE_VN, null)
        set(value) = prefs.edit().putString(KEY_DOWNLOADED_UPDATE_VN, value).apply()

    fun clearDownloadedUpdate() {
        prefs.edit()
            .remove(KEY_DOWNLOADED_UPDATE_VC)
            .remove(KEY_DOWNLOADED_UPDATE_PATH)
            .remove(KEY_DOWNLOADED_UPDATE_SHA256)
            .remove(KEY_DOWNLOADED_UPDATE_VN)
            .apply()
    }

    val isLoggedIn: Boolean get() = !sessionToken.isNullOrBlank()
    val isAdmin: Boolean get() = userRole == "admin"

    fun hasNotesPrivacyNoticeBeenAcknowledged(uid: String? = userId): Boolean {
        val id = uid ?: return false
        return prefs.getBoolean("notes_notice_ack_$id", false)
    }

    fun acknowledgeNotesPrivacyNotice(uid: String? = userId) {
        val id = uid ?: return
        prefs.edit().putBoolean("notes_notice_ack_$id", true).apply()
    }

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val KEY_TOKEN = "session_token"
        const val KEY_USER_ID = "user_id"
        const val KEY_NAME = "user_name"
        const val KEY_ROLE = "user_role"
        const val KEY_TRANSLATION = "translation_id"
        const val KEY_UPDATE_PROMPTED = "update_prompted_for"
        const val KEY_UPDATE_SHOWN_AT = "update_prompt_shown_at"
        const val KEY_KEEPALIVE_EXPLAINED = "keepalive_explained"
        const val KEY_FONT_SCALE = "reader_font_scale"
        const val KEY_LAST_UPDATE_CHECK_AT = "last_update_check_at"
        const val KEY_LAST_UPDATE_NOTIF_AT = "last_update_notif_at"
        const val KEY_UPDATE_LATER_DISMISSED_AT = "update_later_dismissed_at"
        const val KEY_CACHED_RELEASE_JSON = "cached_release_json"
        const val KEY_DOWNLOADED_UPDATE_VC = "downloaded_update_vc"
        const val KEY_DOWNLOADED_UPDATE_PATH = "downloaded_update_path"
        const val KEY_DOWNLOADED_UPDATE_SHA256 = "downloaded_update_sha256"
        const val KEY_DOWNLOADED_UPDATE_VN = "downloaded_update_vn"
    }
}
