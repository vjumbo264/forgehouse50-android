package com.forgebuild.forgehouse50.update

import android.content.Context
import com.forgebuild.forgehouse50.BuildConfig
import com.forgebuild.forgehouse50.data.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object UpdateManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.UpToDate)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    private var activeDownloadJob: Job? = null
    private var initialized = false

    /**
     * Initializes the manager on app startup from persisted session data and disk cache.
     * Restores verified downloaded APK state across process death and cleans up outdated APKs.
     */
    fun init(context: Context, session: SessionStore) {
        if (initialized) return
        initialized = true

        val cachedPath = session.downloadedUpdatePath
        val cachedFile = cachedPath?.let { File(it) }
        val cachedVc = session.downloadedUpdateVersionCode.takeIf { it > 0 }
        val cachedSha = session.downloadedUpdateSha256
        val cachedVn = session.downloadedUpdateVersionName ?: ""

        // Check if installed version has caught up with or superseded the cached download
        if (cachedVc != null && BuildConfig.VERSION_CODE >= cachedVc) {
            if (cachedFile != null && cachedFile.exists()) {
                cachedFile.delete()
            }
            session.clearDownloadedUpdate()
            _uiState.value = UpdateUiState.UpToDate
            return
        }

        // If file exists and is valid for a pending version
        if (cachedFile != null && cachedFile.exists() && cachedVc != null) {
            val updateInfo = UpdateInfo(
                versionName = cachedVn,
                versionCode = cachedVc,
                tag = "forgehouse50-$cachedVn",
                apkUrl = "",
                sizeBytes = cachedFile.length(),
                sha256 = cachedSha,
            )
            val state = UpdateLogic.resolveInitialState(
                currentVersionCode = BuildConfig.VERSION_CODE,
                remoteUpdate = updateInfo,
                cachedFile = cachedFile,
                cachedVersionCode = cachedVc,
                cachedSha256 = cachedSha,
            )
            _uiState.value = state
        } else {
            _uiState.value = UpdateUiState.UpToDate
        }
    }

    /**
     * Checks remote server for available updates.
     */
    fun check(
        context: Context,
        session: SessionStore,
        force: Boolean = false,
        isInUseCheck: Boolean = false,
        onComplete: ((UpdateUiState) -> Unit)? = null,
    ) {
        scope.launch {
            val update = UpdateChecker.check(context, session, force = force, isInUseCheck = isInUseCheck)
            val cachedPath = session.downloadedUpdatePath
            val cachedFile = cachedPath?.let { File(it) }
            val cachedVc = session.downloadedUpdateVersionCode.takeIf { it > 0 }
            val cachedSha = session.downloadedUpdateSha256

            val resolved = UpdateLogic.resolveInitialState(
                currentVersionCode = BuildConfig.VERSION_CODE,
                remoteUpdate = update,
                cachedFile = cachedFile,
                cachedVersionCode = cachedVc,
                cachedSha256 = cachedSha,
            )

            // Do not override an ongoing active download with Available state
            val current = _uiState.value
            if (current !is UpdateUiState.Downloading) {
                _uiState.value = resolved
            }
            onComplete?.invoke(_uiState.value)
        }
    }

    /**
     * Starts or resumes downloading the update APK to the app private files directory.
     * Supports HTTP Range resume and verifies SHA-256 upon completion.
     */
    fun startDownload(
        context: Context,
        session: SessionStore,
        update: UpdateInfo,
        isHidden: Boolean = false,
    ) {
        val current = _uiState.value
        if (current is UpdateUiState.Downloading && current.update.versionCode == update.versionCode) {
            if (current.isHidden != isHidden) {
                _uiState.value = current.copy(isHidden = isHidden)
            }
            return
        }

        activeDownloadJob?.cancel()
        _uiState.value = UpdateLogic.onDownloadStarted(update, isHidden)

        activeDownloadJob = scope.launch(Dispatchers.IO) {
            val updatesDir = File(context.filesDir, "updates")
            if (!updatesDir.exists()) updatesDir.mkdirs()
            val destFile = File(updatesDir, "forgehouse50-${update.versionName}.apk")

            // Check if already completed and valid
            if (destFile.exists() && destFile.length() == update.sizeBytes) {
                val valid = UpdateLogic.verifyApkFile(destFile, update.sizeBytes, update.sha256)
                if (valid) {
                    saveDownloadedSession(session, update, destFile)
                    _uiState.value = UpdateUiState.ReadyToInstall(update, destFile)
                    return@launch
                }
            }

            try {
                if (!UpdateChecker.isOnline(context)) {
                    _uiState.value = UpdateUiState.Error(
                        update = update,
                        message = "Connect to the internet to download update.",
                        apkUrl = update.apkUrl,
                    )
                    return@launch
                }

                val conn = URL(update.apkUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000

                val existingLength = if (destFile.exists()) destFile.length() else 0L
                var isResume = false

                if (existingLength in 1 until update.sizeBytes) {
                    conn.setRequestProperty("Range", "bytes=$existingLength-")
                    isResume = true
                }

                conn.connect()
                val responseCode = conn.responseCode

                val (input: InputStream, append: Boolean, startBytes: Long) = when (responseCode) {
                    HttpURLConnection.HTTP_PARTIAL -> {
                        Triple(conn.inputStream, true, existingLength)
                    }
                    HttpURLConnection.HTTP_OK -> {
                        Triple(conn.inputStream, false, 0L)
                    }
                    416 -> { // Range Not Satisfiable: restart from 0
                        conn.disconnect()
                        destFile.delete()
                        val freshConn = URL(update.apkUrl).openConnection() as HttpURLConnection
                        freshConn.connectTimeout = 15000
                        freshConn.readTimeout = 30000
                        freshConn.connect()
                        Triple(freshConn.inputStream, false, 0L)
                    }
                    else -> {
                        conn.disconnect()
                        _uiState.value = UpdateUiState.Error(
                            update = update,
                            message = "Download failed (server returned HTTP $responseCode).",
                            apkUrl = update.apkUrl,
                        )
                        return@launch
                    }
                }

                val totalBytes = if (update.sizeBytes > 0) update.sizeBytes else conn.contentLengthLong + startBytes
                var bytesReadTotal = startBytes

                input.use { inStream ->
                    FileOutputStream(destFile, append).use { outStream ->
                        val buffer = ByteArray(16384)
                        var read: Int
                        while (inStream.read(buffer).also { read = it } != -1) {
                            outStream.write(buffer, 0, read)
                            bytesReadTotal += read
                            if (totalBytes > 0) {
                                val progress = (bytesReadTotal.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                _uiState.value = UpdateLogic.onDownloadProgress(_uiState.value, progress)
                            }
                        }
                    }
                }
                conn.disconnect()

                val completedState = UpdateLogic.onDownloadCompleted(
                    update = update,
                    apkFile = destFile,
                    expectedSizeBytes = update.sizeBytes,
                    expectedSha256 = update.sha256,
                )

                if (completedState is UpdateUiState.ReadyToInstall) {
                    saveDownloadedSession(session, update, destFile)
                } else {
                    session.clearDownloadedUpdate()
                }

                _uiState.value = completedState
            } catch (e: Exception) {
                _uiState.value = UpdateUiState.Error(
                    update = update,
                    message = e.localizedMessage ?: "Failed to download update package.",
                    apkUrl = update.apkUrl,
                )
            }
        }
    }

    private fun saveDownloadedSession(session: SessionStore, update: UpdateInfo, destFile: File) {
        session.downloadedUpdateVersionCode = update.versionCode
        session.downloadedUpdatePath = destFile.absolutePath
        session.downloadedUpdateSha256 = update.sha256
        session.downloadedUpdateVersionName = update.versionName
    }

    /**
     * Hides the active download dialog while continuing background download.
     */
    fun hideDownload() {
        _uiState.value = UpdateLogic.onDownloadHidden(_uiState.value)
    }

    /**
     * Triggers package installer or requests unknown app sources permission if needed.
     */
    fun triggerInstall(context: Context) {
        val state = _uiState.value
        val ready = when (state) {
            is UpdateUiState.ReadyToInstall -> state
            is UpdateUiState.PermissionRequired -> UpdateUiState.ReadyToInstall(state.update, state.apkFile)
            else -> return
        }

        if (!UpdateChecker.canRequestPackageInstalls(context)) {
            _uiState.value = UpdateUiState.PermissionRequired(ready.update, ready.apkFile)
            UpdateChecker.openUnknownAppSourcesSettings(context)
        } else {
            _uiState.value = ready
            UpdateChecker.installApk(context, ready.apkFile)
        }
    }

    /**
     * Handles returning to the app from unknown app sources settings.
     * If permission is now granted, installs the already downloaded file immediately.
     */
    fun onResume(context: Context) {
        val state = _uiState.value
        if (state is UpdateUiState.PermissionRequired) {
            if (UpdateChecker.canRequestPackageInstalls(context)) {
                _uiState.value = UpdateUiState.ReadyToInstall(state.update, state.apkFile)
                UpdateChecker.installApk(context, state.apkFile)
            }
        }
    }

    /**
     * Dismisses the error state and reverts to available update if present.
     */
    fun dismissError() {
        val state = _uiState.value
        if (state is UpdateUiState.Error) {
            _uiState.value = if (state.update != null) {
                UpdateUiState.Available(state.update)
            } else {
                UpdateUiState.UpToDate
            }
        }
    }
}
