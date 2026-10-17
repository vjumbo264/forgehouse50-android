package com.forgebuild.forgehouse50.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.forgebuild.forgehouse50.BuildConfig
import com.forgebuild.forgehouse50.MainActivity
import com.forgebuild.forgehouse50.R
import com.forgebuild.forgehouse50.data.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class AppReleaseDto(
    val version: Int = 0,
    val tag: String = "",
    val apkUrl: String = "",
    val sizeBytes: Long = 0L,
    val publishedAt: String = "",
    val versionCode: Int? = null,
    val sha256: String? = null,
    val changelog: List<String>? = null,
)

typealias PendingUpdate = UpdateInfo

sealed interface DownloadResult {
    data class Success(val apkFile: File) : DownloadResult
    data class VerificationFailed(val reason: String, val apkUrl: String) : DownloadResult
    data class Failure(val message: String, val apkUrl: String) : DownloadResult
}

object UpdateChecker {
    private const val API_URL = "https://forgehouse50.pages.dev/api/app-release"
    const val CHANNEL_ID = "app_updates"
    const val NOTIFICATION_ID = 5001

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Checks whether an update is available.
     * Respects 30-minute throttling unless [force] is true.
     * Caches release info for offline availability.
     */
    suspend fun check(
        context: Context,
        session: SessionStore,
        force: Boolean = false,
        isInUseCheck: Boolean = false,
    ): PendingUpdate? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()

        // If in-use check, verify 2-hour interval since user clicked "Later"
        if (isInUseCheck && session.updateLaterDismissedAt > 0L) {
            if (!UpdateLogic.shouldReopenDialogInUse(session.updateLaterDismissedAt, now)) {
                return@withContext null
            }
        }

        var releaseDto: AppReleaseDto? = null

        val shouldFetchNetwork = force || UpdateLogic.shouldCheckNetwork(session.lastUpdateCheckAt, now)
        if (shouldFetchNetwork && isOnline(context)) {
            runCatching {
                val conn = URL(API_URL).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.setRequestProperty("User-Agent", "ForgeHouse50-Android/${BuildConfig.VERSION_NAME}")
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val parsed = json.decodeFromString<AppReleaseDto>(text)
                session.cachedReleaseJson = text
                session.lastUpdateCheckAt = now
                releaseDto = parsed
            }
        }

        if (releaseDto == null) {
            val cached = session.cachedReleaseJson
            if (!cached.isNullOrBlank()) {
                releaseDto = runCatching { json.decodeFromString<AppReleaseDto>(cached) }.getOrNull()
            }
        }

        val release = releaseDto ?: return@withContext null

        val effectiveCode = release.versionCode ?: release.version
        if (!UpdateLogic.isUpdateAvailable(BuildConfig.VERSION_CODE, release.versionCode, release.version)) {
            // Updated or current: clear dismissal and cached update
            session.updateLaterDismissedAt = 0L
            return@withContext null
        }

        if (release.apkUrl.isBlank()) return@withContext null

        val versionName = if (release.tag.isNotBlank()) release.tag.removePrefix("forgehouse50-") else "v${effectiveCode}"
        PendingUpdate(
            versionName = versionName,
            versionCode = effectiveCode,
            tag = release.tag,
            apkUrl = release.apkUrl,
            sizeBytes = release.sizeBytes,
            sha256 = release.sha256,
        )
    }

    /**
     * Download the APK with progress reporting and verify SHA-256 and size.
     */
    suspend fun downloadAndVerify(
        context: Context,
        update: PendingUpdate,
        onProgress: (Float) -> Unit,
    ): DownloadResult = withContext(Dispatchers.IO) {
        if (!isOnline(context)) {
            return@withContext DownloadResult.Failure("Connect to the internet to update.", update.apkUrl)
        }

        val updatesDir = File(context.cacheDir, "updates")
        if (!updatesDir.exists()) updatesDir.mkdirs()
        val destFile = File(updatesDir, "forgehouse50-${update.versionName}.apk")

        try {
            val conn = URL(update.apkUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.connect()

            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return@withContext DownloadResult.Failure(
                    "Download failed (server returned HTTP ${conn.responseCode}).",
                    update.apkUrl,
                )
            }

            val totalBytes = if (conn.contentLengthLong > 0) conn.contentLengthLong else update.sizeBytes
            var bytesReadTotal = 0L

            conn.inputStream.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesReadTotal += read
                        if (totalBytes > 0) {
                            val progress = (bytesReadTotal.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            onProgress(progress)
                        }
                    }
                }
            }
            conn.disconnect()

            val valid = UpdateLogic.verifyApkFile(destFile, update.sizeBytes, update.sha256)
            if (!valid) {
                destFile.delete()
                return@withContext DownloadResult.VerificationFailed(
                    "Update package verification failed (checksum or size mismatch).",
                    update.apkUrl,
                )
            }

            DownloadResult.Success(destFile)
        } catch (e: Exception) {
            DownloadResult.Failure(
                e.localizedMessage ?: "Failed to download update package.",
                update.apkUrl,
            )
        }
    }

    /**
     * Launch system package installer for the downloaded APK via FileProvider.
     */
    fun installApk(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Check if install from unknown sources is permitted (API 26+).
     */
    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Intent to open Settings for Unknown App Sources.
     */
    fun openUnknownAppSourcesSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    /**
     * Open APK directly in browser.
     */
    fun openInBrowser(context: Context, apkUrl: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Check background update and post daily notification if new version available.
     */
    suspend fun checkBackground(context: Context, session: SessionStore) = withContext(Dispatchers.IO) {
        val update = check(context, session, force = true) ?: return@withContext
        val now = System.currentTimeMillis()
        if (UpdateLogic.shouldPostDailyNotification(session.lastUpdateNotifAt, now)) {
            postNotification(context, update)
            session.lastUpdateNotifAt = now
        }
    }

    private fun postNotification(context: Context, update: PendingUpdate) {
        ensureNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Update available")
            .setContentText("Update available — tap to update")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "App updates",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Notifications when a new version of ForgeHouse 50 is available"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
