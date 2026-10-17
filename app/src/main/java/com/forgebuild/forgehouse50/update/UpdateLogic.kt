package com.forgebuild.forgehouse50.update

import java.io.File
import java.security.MessageDigest

data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val tag: String,
    val apkUrl: String,
    val sizeBytes: Long,
    val sha256: String?,
)

sealed interface UpdateUiState {
    data object UpToDate : UpdateUiState
    data class Available(val update: UpdateInfo) : UpdateUiState
    data class Downloading(val update: UpdateInfo, val progress: Float, val isHidden: Boolean = false) : UpdateUiState
    data class ReadyToInstall(val update: UpdateInfo, val apkFile: File) : UpdateUiState
    data class PermissionRequired(val update: UpdateInfo, val apkFile: File) : UpdateUiState
    data class Error(val update: UpdateInfo?, val message: String, val apkUrl: String? = null) : UpdateUiState
}

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

    /**
     * Resolves the initial UpdateUiState from current installed version, available remote update,
     * and cached downloaded file if present. Cleans up outdated files if installed version is current.
     */
    fun resolveInitialState(
        currentVersionCode: Int,
        remoteUpdate: UpdateInfo?,
        cachedFile: File?,
        cachedVersionCode: Int?,
        cachedSha256: String?,
    ): UpdateUiState {
        // If current app is already at or above remote update, or no remote update available:
        if (remoteUpdate == null || remoteUpdate.versionCode <= currentVersionCode) {
            if (cachedFile != null && cachedFile.exists()) {
                cleanupOutdatedApk(cachedFile, cachedVersionCode, currentVersionCode)
            }
            return UpdateUiState.UpToDate
        }

        // A remote update is available (remoteUpdate.versionCode > currentVersionCode).
        // Check if we already have a complete, verified download for this exact version:
        if (cachedFile != null && cachedFile.exists() && cachedVersionCode == remoteUpdate.versionCode) {
            val isValid = verifyApkFile(cachedFile, remoteUpdate.sizeBytes, remoteUpdate.sha256 ?: cachedSha256)
            if (isValid) {
                return UpdateUiState.ReadyToInstall(remoteUpdate, cachedFile)
            } else {
                // Corrupted or size mismatch: delete and require clean download
                cachedFile.delete()
            }
        } else if (cachedFile != null && cachedFile.exists() && (cachedVersionCode ?: 0) < remoteUpdate.versionCode) {
            // Outdated cached APK from an earlier version: clean up
            cachedFile.delete()
        }

        return UpdateUiState.Available(remoteUpdate)
    }

    /**
     * Transition when user initiates download.
     */
    fun onDownloadStarted(update: UpdateInfo, isHidden: Boolean = false): UpdateUiState {
        return UpdateUiState.Downloading(update, progress = 0f, isHidden = isHidden)
    }

    /**
     * Transition on download progress update.
     */
    fun onDownloadProgress(currentState: UpdateUiState, progress: Float): UpdateUiState {
        return when (currentState) {
            is UpdateUiState.Downloading -> currentState.copy(progress = progress.coerceIn(0f, 1f))
            else -> currentState
        }
    }

    /**
     * Transition when user taps "Hide" during download.
     */
    fun onDownloadHidden(currentState: UpdateUiState): UpdateUiState {
        return when (currentState) {
            is UpdateUiState.Downloading -> currentState.copy(isHidden = true)
            else -> currentState
        }
    }

    /**
     * Transition when download finishes. Verifies SHA-256 and size.
     */
    fun onDownloadCompleted(
        update: UpdateInfo,
        apkFile: File,
        expectedSizeBytes: Long?,
        expectedSha256: String?,
    ): UpdateUiState {
        val isValid = verifyApkFile(apkFile, expectedSizeBytes, expectedSha256)
        return if (isValid) {
            UpdateUiState.ReadyToInstall(update, apkFile)
        } else {
            apkFile.delete()
            UpdateUiState.Error(
                update = update,
                message = "Update package verification failed (checksum or size mismatch).",
                apkUrl = update.apkUrl,
            )
        }
    }

    /**
     * Transition when user triggers install.
     */
    fun onInstallRequested(
        currentState: UpdateUiState,
        canRequestPackageInstalls: Boolean,
    ): UpdateUiState {
        return when (currentState) {
            is UpdateUiState.ReadyToInstall -> {
                if (!canRequestPackageInstalls) {
                    UpdateUiState.PermissionRequired(currentState.update, currentState.apkFile)
                } else {
                    currentState
                }
            }
            is UpdateUiState.PermissionRequired -> {
                if (!canRequestPackageInstalls) {
                    currentState
                } else {
                    UpdateUiState.ReadyToInstall(currentState.update, currentState.apkFile)
                }
            }
            else -> currentState
        }
    }

    /**
     * Transition when returning from unknown app sources system settings.
     */
    fun onResumeFromPermissionSettings(
        currentState: UpdateUiState,
        canRequestPackageInstalls: Boolean,
    ): UpdateUiState {
        return when (currentState) {
            is UpdateUiState.PermissionRequired -> {
                if (canRequestPackageInstalls) {
                    UpdateUiState.ReadyToInstall(currentState.update, currentState.apkFile)
                } else {
                    currentState
                }
            }
            else -> currentState
        }
    }

    /**
     * Cleans up an APK if it is outdated compared to installed version.
     */
    fun cleanupOutdatedApk(file: File, cachedVersionCode: Int?, currentVersionCode: Int): Boolean {
        if (!file.exists()) return false
        if (cachedVersionCode == null || cachedVersionCode <= currentVersionCode) {
            return file.delete()
        }
        return false
    }
}
