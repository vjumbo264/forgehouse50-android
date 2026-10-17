package com.forgebuild.forgehouse50.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

sealed class TranslationDownloadState {
    object Idle : TranslationDownloadState()
    object Connecting : TranslationDownloadState()
    data class Progressing(val bytesRead: Long, val totalBytes: Long, val progress: Float) : TranslationDownloadState()
    object Done : TranslationDownloadState()
    data class Failed(val message: String) : TranslationDownloadState()
}

class TranslationDownloadManager(
    private val repo: Repository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val _states = MutableStateFlow<Map<String, TranslationDownloadState>>(emptyMap())
    val states: StateFlow<Map<String, TranslationDownloadState>> = _states.asStateFlow()

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val queueSemaphore = Semaphore(2)

    fun stateFor(translationId: String): TranslationDownloadState =
        _states.value[repo.normalizeTranslation(translationId)] ?: TranslationDownloadState.Idle

    fun download(translationId: String, name: String) {
        val normId = repo.normalizeTranslation(translationId)
        val current = _states.value[normId]
        if (current is TranslationDownloadState.Connecting || current is TranslationDownloadState.Progressing) {
            return
        }

        updateState(normId, TranslationDownloadState.Connecting)

        val job = scope.launch {
            try {
                queueSemaphore.withPermit {
                    if (!isActive) return@withPermit
                    repo.downloadTranslation(normId, name) { done, total ->
                        val p = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
                        updateState(normId, TranslationDownloadState.Progressing(done.toLong(), total.toLong(), p))
                    }
                    updateState(normId, TranslationDownloadState.Done)
                }
            } catch (ce: CancellationException) {
                updateState(normId, TranslationDownloadState.Idle)
            } catch (t: Throwable) {
                val msg = t.message?.takeIf { it.isNotBlank() && !it.contains("Unexpected JSON token") }
                    ?: "Download failed. Please check your connection and retry."
                updateState(normId, TranslationDownloadState.Failed(msg))
            } finally {
                activeJobs.remove(normId)
            }
        }
        activeJobs[normId] = job
    }

    fun cancel(translationId: String) {
        val normId = repo.normalizeTranslation(translationId)
        activeJobs.remove(normId)?.cancel()
        updateState(normId, TranslationDownloadState.Idle)
    }

    fun retry(translationId: String, name: String) {
        download(translationId, name)
    }

    fun updateState(id: String, state: TranslationDownloadState) {
        val current = _states.value.toMutableMap()
        current[id] = state
        _states.value = current
    }
}
