package com.forgebuild.forgehouse50.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.forgebuild.forgehouse50.BuildConfig
import com.forgebuild.forgehouse50.data.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * In-app update prompt, skippable but recurring, with newer-update
 * supersession.
 *
 * Source of truth: apps/forgehouse50/version.json in the forgebuild repo
 * (raw URL below). Each release session bumps versionCode in
 * app/build.gradle.kts AND updates that manifest before dispatching the
 * release workflow — so the manifest ALWAYS names the newest available
 * build. The prompt therefore can never point at an older update than what
 * is actually available (supersession is structural), and "skip" is per
 * day, not forever: the dialog reappears on a later open.
 */
object UpdateChecker {

    private const val MANIFEST_URL =
        "https://raw.githubusercontent.com/vjumbo264/forgebuild/main/apps/forgehouse50/version.json"
    private const val RESHOW_AFTER_MS = 24 * 60 * 60 * 1000L   // at most once per day

    @Serializable
    data class VersionManifest(
        val latest_version_code: Int = 0,
        val latest_version_name: String = "",
        val tag: String = "",
        val apk_url: String = "",
        val notes: String = "",
    )

    data class PendingUpdate(
        val versionName: String,
        val tag: String,
        val apkUrl: String,
        val notes: String,
    )

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Returns a [PendingUpdate] if a prompt should be shown now: a newer
     * version exists AND (we never prompted for this version OR the
     * once-a-day cadence has elapsed). A skip only snoozes — and if an even
     * newer build appears meanwhile, the newer one is what gets prompted.
     */
    suspend fun check(context: Context, session: SessionStore): PendingUpdate? =
        withContext(Dispatchers.IO) {
            val manifest = runCatching {
                json.decodeFromString<VersionManifest>(URL(MANIFEST_URL).readText())
            }.getOrNull() ?: return@withContext null

            if (manifest.latest_version_code <= BuildConfig.VERSION_CODE) return@withContext null
            if (manifest.apk_url.isBlank()) return@withContext null

            val now = System.currentTimeMillis()
            val promptedForThis = session.updatePromptedFor == manifest.tag
            val snoozing = promptedForThis && (now - session.updatePromptShownAt) < RESHOW_AFTER_MS
            if (snoozing) return@withContext null

            session.updatePromptedFor = manifest.tag
            session.updatePromptShownAt = now
            PendingUpdate(manifest.latest_version_name, manifest.tag, manifest.apk_url, manifest.notes)
        }

    /** Hand the APK asset to the browser/package installer — user-directed, never forced. */
    fun openDownload(context: Context, apkUrl: String) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
