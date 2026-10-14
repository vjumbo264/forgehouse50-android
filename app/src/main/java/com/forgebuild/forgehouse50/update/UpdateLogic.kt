package com.forgebuild.forgehouse50.update

import java.io.File
import java.security.MessageDigest

object UpdateLogic {
    const val NETWORK_CHECK_THROTTLE_MS = 30 * 60 * 1000L      // 30 minutes
    const val LATER_REAPPEAR_IN_USE_MS = 2 * 60 * 60 * 1000L    // 2 hours
    const val NOTIFICATION_INTERVAL_MS = 24 * 60 * 60 * 1000L   // 24 hours (once a day)

    /**
     * Determines whether an update is available given the installed version code
     * and the remote version / versionCode.
     */
    fun isUpdateAvailable(currentVersionCode: Int, remoteVersionCode: Int?, remoteVersion: Int?): Boolean {
        val effectiveRemote = remoteVersionCode ?: remoteVersion ?: return false
        return effectiveRemote > currentVersionCode
    }

    /**
     * Determines whether a network check should be performed, throttled to once every 30 minutes.
     */
    fun shouldCheckNetwork(lastCheckEpochMs: Long, nowEpochMs: Long): Boolean {
        if (lastCheckEpochMs <= 0L) return true
        return (nowEpochMs - lastCheckEpochMs) >= NETWORK_CHECK_THROTTLE_MS
    }

    /**
     * Determines whether the update dialog should reappear while the app is in use after user tapped "Later" (after 2 hours).
     */
    fun shouldReopenDialogInUse(laterDismissedAtEpochMs: Long, nowEpochMs: Long): Boolean {
        if (laterDismissedAtEpochMs <= 0L) return false
        return (nowEpochMs - laterDismissedAtEpochMs) >= LATER_REAPPEAR_IN_USE_MS
    }

    /**
     * Determines whether a daily system notification should be posted (at most once every 24 hours).
     */
    fun shouldPostDailyNotification(lastNotifEpochMs: Long, nowEpochMs: Long): Boolean {
        if (lastNotifEpochMs <= 0L) return true
        return (nowEpochMs - lastNotifEpochMs) >= NOTIFICATION_INTERVAL_MS
    }

    /**
     * Verifies downloaded APK file size and SHA-256 hash.
     */
    fun verifyApkFile(file: File, expectedSizeBytes: Long?, expectedSha256: String?): Boolean {
        if (!file.exists() || !file.isFile) return false
        if (expectedSizeBytes != null && expectedSizeBytes > 0 && file.length() != expectedSizeBytes) {
            return false
        }
        if (!expectedSha256.isNullOrBlank()) {
            val actualSha = computeSha256(file)
            if (!actualSha.equals(expectedSha256.trim(), ignoreCase = true)) {
                return false
            }
        }
        return true
    }

    /**
     * Computes lowercase SHA-256 hex digest for a file.
     */
    fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Safeguard filter at display time: hides any changelog item containing "admin" or "delete user" (case-insensitive).
     */
    fun filterChangelogItems(items: List<String>): List<String> {
        return items.filter { item ->
            val lower = item.lowercase()
            !lower.contains("admin") && !lower.contains("delete user")
        }
    }
}
