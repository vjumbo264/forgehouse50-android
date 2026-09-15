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

    var keepAliveExplained: Boolean
        get() = prefs.getBoolean(KEY_KEEPALIVE_EXPLAINED, false)
        set(value) = prefs.edit().putBoolean(KEY_KEEPALIVE_EXPLAINED, value).apply()

    val isLoggedIn: Boolean get() = !sessionToken.isNullOrBlank()
    val isAdmin: Boolean get() = userRole == "admin"

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val KEY_TOKEN = "session_token"
        const val KEY_NAME = "user_name"
        const val KEY_ROLE = "user_role"
        const val KEY_TRANSLATION = "translation_id"
        const val KEY_UPDATE_PROMPTED = "update_prompted_for"
        const val KEY_UPDATE_SHOWN_AT = "update_prompt_shown_at"
        const val KEY_KEEPALIVE_EXPLAINED = "keepalive_explained"
    }
}
