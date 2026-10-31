package com.forgebuild.forgehouse50.audio

import android.content.Context
import com.forgebuild.forgehouse50.data.ReaderErrorSanitizer
import kotlinx.coroutines.CancellationException
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
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * App-wide manager for manual Scripture audio metadata, downloads, resume,
 * SHA-256 verification, storage reporting, and deletion.
 *
 * Strictly manual: no `.m4a` file is ever downloaded unless the user explicitly
 * invokes [startTranslationDownload] or [startChapterDownload].
 */
class AudioDownloadManager(
    context: Context,
    private val dao: AudioChapterDao = AudioDatabase.get(context).audioChapterDao(),
    private val transport: AudioTransport = HttpUrlConnectionAudioTransport(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val appContext = context.applicationContext
    private val audioRootDir = File(appContext.filesDir, "audio").apply { mkdirs() }
    val engine = AudioDownloadEngine(
        audioRootDir = audioRootDir,
        indexStore = RoomAudioIndexStore(dao),
        transport = transport,
    )

    private val _versionsCatalog = MutableStateFlow<Map<String, VerseWellVersionAudio>>(loadCachedCatalog())
    val versionsCatalog: StateFlow<Map<String, VerseWellVersionAudio>> = _versionsCatalog.asStateFlow()

    private val _manifests = MutableStateFlow<Map<String, AudioManifest>>(emptyMap())
    val manifests: StateFlow<Map<String, AudioManifest>> = _manifests.asStateFlow()

    private val _translationStates = MutableStateFlow<Map<String, TranslationAudioUiState>>(emptyMap())
    val translationStates: StateFlow<Map<String, TranslationAudioUiState>> = _translationStates.asStateFlow()

    private val _chapterStates = MutableStateFlow<Map<String, ChapterAudioDownloadState>>(emptyMap())
    val chapterStates: StateFlow<Map<String, ChapterAudioDownloadState>> = _chapterStates.asStateFlow()

    private val _localChaptersByVersion = MutableStateFlow<Map<String, List<DownloadedAudioChapter>>>(emptyMap())
    val localChaptersByVersion: StateFlow<Map<String, List<DownloadedAudioChapter>>> = _localChaptersByVersion.asStateFlow()

    private val translationJobs = ConcurrentHashMap<String, Job>()
    private val chapterJobs = ConcurrentHashMap<String, Job>()
    private val pausedVersions = ConcurrentHashMap.newKeySet<String>()

    init {
        scope.launch {
            refreshLocalIndex()
            for (code in AudioConfig.SUPPORTED_TRANSLATION_CODES) {
                loadCachedManifestFromDisk(code)?.let { cached ->
                    putManifest(code, cached)
                }
            }
        }
    }

    suspend fun refreshLocalIndex() = withContext(Dispatchers.IO) {
        val all = dao.all().filter { row ->
            val f = engine.chapterFile(row.versionCode, row.fileName)
            val validOnDisk = f.exists() && f.isFile && f.length() > 0L && (row.sizeBytes <= 0L || f.length() == row.sizeBytes)
            if (!validOnDisk) {
                runCatching { dao.deleteChapter(row.key) }
            }
            validOnDisk
        }
        val grouped = all.groupBy { it.versionCode.uppercase(Locale.US) }
        _localChaptersByVersion.value = grouped
    }

    suspend fun refreshVersionsCatalog(): Map<String, VerseWellVersionAudio> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = transport.fetchText(AudioLogic.versionsCatalogUrl())
            val parsed = AudioLogic.parseVersionsCatalog(raw)
            File(audioRootDir, "versions_catalog.json").writeText(raw)
            _versionsCatalog.value = parsed
            parsed
        }.getOrElse { _versionsCatalog.value }
    }

    suspend fun loadManifestIfSupported(translationIdOrCode: String, forceNetwork: Boolean = false): AudioManifest? =
        withContext(Dispatchers.IO) {
            val code = AudioLogic.normalizeVersionCode(translationIdOrCode)
            if (code !in AudioConfig.SUPPORTED_TRANSLATION_CODES) return@withContext null

            val existingMem = _manifests.value[code]
            if (!forceNetwork && existingMem != null && existingMem.chapters.isNotEmpty()) {
                // Refresh quietly in background without blocking caller
                scope.launch { fetchAndCacheManifestQuietly(code) }
                return@withContext existingMem
            }

            loadCachedManifestFromDisk(code)?.let { diskCached ->
                putManifest(code, diskCached)
                if (!forceNetwork) {
                    scope.launch { fetchAndCacheManifestQuietly(code) }
                    return@withContext diskCached
                }
            }

            fetchAndCacheManifestQuietly(code)
        }

    private suspend fun fetchAndCacheManifestQuietly(code: String): AudioManifest? {
        return runCatching {
            val raw = transport.fetchText(AudioLogic.manifestUrl(code))
            val parsed = AudioLogic.parseManifest(raw)
            val dir = engine.versionDir(code).apply { mkdirs() }
            File(dir, "manifest.json").writeText(raw)
            putManifest(code, parsed)
            parsed
        }.getOrElse { _manifests.value[code] }
    }

    fun isTranslationAudioAvailable(translationIdOrCode: String): Boolean {
        val code = AudioLogic.normalizeVersionCode(translationIdOrCode)
        val localCount = _localChaptersByVersion.value[code]?.size ?: 0
        return AudioLogic.isTranslationAudioAvailable(
            translationIdOrCode = code,
            versionsCatalog = _versionsCatalog.value,
            cachedManifest = _manifests.value[code],
            localVerifiedChaptersCount = localCount,
        )
    }

    fun estimateForTranslation(translationIdOrCode: String): BulkAudioEstimate {
        val code = AudioLogic.normalizeVersionCode(translationIdOrCode)
        return AudioLogic.estimateBulkDownload(
            versionCodeOrId = code,
            manifest = _manifests.value[code],
            catalogAudio = _versionsCatalog.value[code],
            localChapters = _localChaptersByVersion.value[code].orEmpty(),
        )
    }

    suspend fun getVerifiedLocalChapterFile(
        translationIdOrCode: String,
        book: String,
        chapter: Int,
        verifyHashNow: Boolean = false,
    ): File? = withContext(Dispatchers.IO) {
        engine.resolveVerifiedLocalFile(translationIdOrCode, book, chapter, verifyHashNow)
    }

    fun startTranslationDownload(translationIdOrCode: String) {
        val code = AudioLogic.normalizeVersionCode(translationIdOrCode)
        if (code !in AudioConfig.SUPPORTED_TRANSLATION_CODES) return
        val existingState = _translationStates.value[code]
        if (existingState is TranslationAudioUiState.Connecting || existingState is TranslationAudioUiState.Downloading) {
            return
        }
        pausedVersions.remove(code)
        setTranslationState(code, TranslationAudioUiState.Connecting)

        val job = scope.launch {
            var latestProgress: BulkDownloadProgress? = null
            try {
                val manifest = loadManifestIfSupported(code, forceNetwork = _manifests.value[code] == null)
                    ?: throw IllegalStateException("Could not load audio manifest. Check your connection and retry.")
                engine.downloadTranslation(code, manifest) { prog ->
                    latestProgress = prog
                    setTranslationState(code, TranslationAudioUiState.Downloading(prog))
                }
                refreshLocalIndex()
                setTranslationState(code, TranslationAudioUiState.Completed)
            } catch (ce: CancellationException) {
                refreshLocalIndex()
                if (pausedVersions.remove(code)) {
                    val est = estimateForTranslation(code)
                    setTranslationState(
                        code,
                        TranslationAudioUiState.Paused(
                            chaptersDone = latestProgress?.chaptersDone ?: est.downloadedChapters,
                            totalChapters = latestProgress?.totalChapters ?: est.totalChapters,
                            bytesDownloaded = latestProgress?.bytesDownloaded ?: est.downloadedBytes,
                            totalBytes = latestProgress?.totalBytes ?: est.totalBytes,
                        )
                    )
                } else {
                    setTranslationState(code, TranslationAudioUiState.Idle)
                }
            } catch (t: Throwable) {
                refreshLocalIndex()
                val msg = ReaderErrorSanitizer.toUserMessage(
                    t,
                    fallback = "Audio download interrupted. Tap Resume to continue."
                ) ?: "Audio download interrupted. Tap Resume to continue."
                setTranslationState(code, TranslationAudioUiState.Failed(msg))
            } finally {
                translationJobs.remove(code)
            }
        }
        translationJobs[code] = job
    }

    fun pauseTranslationDownload(translationIdOrCode: String) {
        val code = AudioLogic.normalizeVersionCode(translationIdOrCode)
        pausedVersions.add(code)
        translationJobs.remove(code)?.cancel()
    }

    fun cancelTranslationDownload(translationIdOrCode: String) {
        val code = AudioLogic.normalizeVersionCode(translationIdOrCode)
        pausedVersions.remove(code)
        translationJobs.remove(code)?.cancel()
        scope.launch {
            engine.cleanPartialFiles(code)
            refreshLocalIndex()
            setTranslationState(code, TranslationAudioUiState.Idle)
        }
    }

    fun startChapterDownload(translationIdOrCode: String, meta: AudioChapterMeta) {
        val code = AudioLogic.normalizeVersionCode(translationIdOrCode)
        if (code !in AudioConfig.SUPPORTED_TRANSLATION_CODES) return
        val key = AudioLogic.chapterKey(code, meta.book, meta.chapter)
        val cur = _chapterStates.value[key]
        if (cur is ChapterAudioDownloadState.Connecting || cur is ChapterAudioDownloadState.Downloading) return

        setChapterState(key, ChapterAudioDownloadState.Connecting)
        val job = scope.launch {
            try {
                engine.downloadChapter(code, meta) { read, total ->
                    setChapterState(key, ChapterAudioDownloadState.Downloading(read, total))
                }
                refreshLocalIndex()
                setChapterState(key, ChapterAudioDownloadState.Downloaded)
            } catch (ce: CancellationException) {
                setChapterState(key, ChapterAudioDownloadState.Idle)
            } catch (t: Throwable) {
                val msg = ReaderErrorSanitizer.toUserMessage(
                    t,
                    fallback = "Could not download chapter audio. Check your connection and retry."
                ) ?: "Could not download chapter audio."
                setChapterState(key, ChapterAudioDownloadState.Failed(msg))
            } finally {
                chapterJobs.remove(key)
            }
        }
        chapterJobs[key] = job
    }

    fun cancelChapterDownload(translationIdOrCode: String, book: String, chapter: Int) {
        val key = AudioLogic.chapterKey(translationIdOrCode, book, chapter)
        chapterJobs.remove(key)?.cancel()
        scope.launch {
            val slug = AudioLogic.toBookSlug(book)
            val fallbackName = String.format(Locale.US, "%s-%03d.m4a", slug, chapter)
            engine.partFile(translationIdOrCode, fallbackName).delete()
            setChapterState(key, ChapterAudioDownloadState.Idle)
        }
    }

    suspend fun deleteChapterAudio(translationIdOrCode: String, book: String, chapter: Int) =
        withContext(Dispatchers.IO) {
            val key = AudioLogic.chapterKey(translationIdOrCode, book, chapter)
            chapterJobs.remove(key)?.cancel()
            engine.deleteChapterAudio(translationIdOrCode, book, chapter)
            setChapterState(key, ChapterAudioDownloadState.Idle)
            refreshLocalIndex()
        }

    suspend fun deleteTranslationAudio(translationIdOrCode: String) = withContext(Dispatchers.IO) {
        val code = AudioLogic.normalizeVersionCode(translationIdOrCode)
        pausedVersions.remove(code)
        translationJobs.remove(code)?.cancel()
        val prefix = "${code.lowercase(Locale.US)}|"
        chapterJobs.keys.filter { it.startsWith(prefix) }.forEach { k ->
            chapterJobs.remove(k)?.cancel()
        }
        engine.deleteTranslationAudio(code)
        setTranslationState(code, TranslationAudioUiState.Idle)
        val nextChapterStates = _chapterStates.value.filterKeys { !it.startsWith(prefix) }
        _chapterStates.value = nextChapterStates
        refreshLocalIndex()
    }

    private fun loadCachedCatalog(): Map<String, VerseWellVersionAudio> {
        val f = File(audioRootDir, "versions_catalog.json")
        if (!f.exists()) return emptyMap()
        return runCatching { AudioLogic.parseVersionsCatalog(f.readText()) }.getOrDefault(emptyMap())
    }

    private fun loadCachedManifestFromDisk(code: String): AudioManifest? {
        val f = File(engine.versionDir(code), "manifest.json")
        if (!f.exists()) return null
        return runCatching { AudioLogic.parseManifest(f.readText()) }.getOrNull()
    }

    private fun putManifest(code: String, manifest: AudioManifest) {
        val cur = _manifests.value.toMutableMap()
        cur[code] = manifest
        _manifests.value = cur
    }

    private fun setTranslationState(code: String, state: TranslationAudioUiState) {
        val cur = _translationStates.value.toMutableMap()
        cur[code] = state
        _translationStates.value = cur
    }

    private fun setChapterState(key: String, state: ChapterAudioDownloadState) {
        val cur = _chapterStates.value.toMutableMap()
        cur[key] = state
        _chapterStates.value = cur
    }
}
