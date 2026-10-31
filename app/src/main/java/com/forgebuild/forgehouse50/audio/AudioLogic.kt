package com.forgebuild.forgehouse50.audio

import com.forgebuild.forgehouse50.data.ScriptureReferenceUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

/**
 * Global configuration and integrity rules for ForgeHouse 50 Scripture Audio (Item i10).
 */
object AudioConfig {
    /**
     * Default playback policy (Contract Section 6.8): foreground-only.
     * When false, audio playback pauses immediately whenever the app goes to the
     * background, the screen locks, or the reader screen leaves composition.
     * Kept behind this single clearly named flag so Victor can flip it in a future build.
     */
    const val ALLOW_BACKGROUND_PLAYBACK: Boolean = false

    /** Only these 5 translations support audio narration. */
    val SUPPORTED_TRANSLATION_CODES: Set<String> = setOf("KJV", "NIV", "TLB", "GW", "VOICE")

    /** Allowed playback speed steps (0.75x - 1.5x). */
    val PLAYBACK_SPEEDS: List<Float> = listOf(0.75f, 1.0f, 1.25f, 1.5f)

    const val MIN_PLAYBACK_SPEED: Float = 0.75f
    const val MAX_PLAYBACK_SPEED: Float = 1.5f

    const val VERSEWELL_API_BASE: String = "https://versewell-api.vjumbo264.workers.dev"
    const val USER_AGENT: String = "ForgeHouse50-Android/1.0"
}

@Serializable
data class VerseWellVersionsResponse(
    val versions: List<VerseWellVersionItem> = emptyList(),
)

@Serializable
data class VerseWellVersionItem(
    val code: String = "",
    val name: String = "",
    val audio: VerseWellVersionAudio = VerseWellVersionAudio(),
)

@Serializable
data class VerseWellVersionAudio(
    val available: Boolean = false,
    val chapters: Int = 0,
    val total_bytes: Long = 0L,
    val voice: String = "",
    val manifest_url: String = "",
)

@Serializable
data class AudioManifest(
    val version: String = "",
    val voice: String = "",
    val total_chapters: Int = 0,
    val total_bytes: Long = 0L,
    val chapters: List<AudioChapterMeta> = emptyList(),
) {
    val effectiveTotalChapters: Int
        get() = if (total_chapters > 0) total_chapters else chapters.size

    val effectiveTotalBytes: Long
        get() = if (total_bytes > 0L) total_bytes else chapters.sumOf { it.bytes }
}

@Serializable
data class AudioChapterMeta(
    val file: String,
    val book: String,
    val book_slug: String = "",
    val chapter: Int,
    val bytes: Long = 0L,
    val sha256: String = "",
    val duration_ms: Long = 0L,
    val engine: String = "",
    val voice: String = "",
    val word_count: Int = 0,
    val verses: List<AudioVerseTiming> = emptyList(),
)

@Serializable
data class AudioVerseTiming(
    val verse: Int,
    val start_ms: Long = 0L,
    val end_ms: Long = 0L,
)

data class BulkAudioEstimate(
    val versionCode: String,
    val totalChapters: Int,
    val downloadedChapters: Int,
    val remainingChapters: Int,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val remainingBytes: Long,
)

data class BulkDownloadProgress(
    val versionCode: String,
    val chaptersDone: Int,
    val totalChapters: Int,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val currentBook: String = "",
    val currentChapter: Int = 0,
) {
    val fraction: Float
        get() = when {
            totalBytes > 0L -> (bytesDownloaded.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
            totalChapters > 0 -> (chaptersDone.toFloat() / totalChapters.toFloat()).coerceIn(0f, 1f)
            else -> 0f
        }
}

sealed class TranslationAudioUiState {
    object Idle : TranslationAudioUiState()
    object Connecting : TranslationAudioUiState()
    data class Downloading(val progress: BulkDownloadProgress) : TranslationAudioUiState()
    data class Paused(
        val chaptersDone: Int,
        val totalChapters: Int,
        val bytesDownloaded: Long,
        val totalBytes: Long,
    ) : TranslationAudioUiState()
    object Completed : TranslationAudioUiState()
    data class Failed(val message: String) : TranslationAudioUiState()
}

sealed class ChapterAudioDownloadState {
    object Idle : ChapterAudioDownloadState()
    object Connecting : ChapterAudioDownloadState()
    data class Downloading(val bytesRead: Long, val totalBytes: Long) : ChapterAudioDownloadState() {
        val fraction: Float
            get() = if (totalBytes > 0L) (bytesRead.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f) else 0f
    }
    object Downloaded : ChapterAudioDownloadState()
    data class Failed(val message: String) : ChapterAudioDownloadState()
}

/**
 * Pure JVM logic for VerseWell audio manifest parsing, SHA-256 verification,
 * download estimation, playback rules, and strict reading-timer isolation.
 */
object AudioLogic {
    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    fun normalizeVersionCode(translationIdOrCode: String): String =
        translationIdOrCode.trim().removePrefix("versewell-").removePrefix("VERSEWELL-").uppercase(Locale.US)

    fun toBookSlug(book: String): String {
        val canon = ScriptureReferenceUtils.canonicalBook(book) ?: book.trim()
        return canon.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
    }

    fun chapterKey(versionCodeOrId: String, book: String, chapter: Int): String {
        val codeLower = normalizeVersionCode(versionCodeOrId).lowercase(Locale.US)
        val canonBook = ScriptureReferenceUtils.canonicalBook(book) ?: book.trim()
        return "$codeLower|$canonBook|$chapter"
    }

    fun versionsCatalogUrl(baseUrl: String = AudioConfig.VERSEWELL_API_BASE): String =
        "${baseUrl.trimEnd('/')}/api/v1/versions"

    fun manifestUrl(versionCodeOrId: String, baseUrl: String = AudioConfig.VERSEWELL_API_BASE): String {
        val codeLower = normalizeVersionCode(versionCodeOrId).lowercase(Locale.US)
        return "${baseUrl.trimEnd('/')}/api/v1/audio/$codeLower/manifest"
    }

    fun chapterAudioUrl(
        versionCodeOrId: String,
        chapterMeta: AudioChapterMeta,
        baseUrl: String = AudioConfig.VERSEWELL_API_BASE,
    ): String {
        val codeLower = normalizeVersionCode(versionCodeOrId).lowercase(Locale.US)
        val slug = chapterMeta.book_slug.ifBlank { toBookSlug(chapterMeta.book) }
        return "${baseUrl.trimEnd('/')}/api/v1/audio/$codeLower/$slug/${chapterMeta.chapter}"
    }

    fun parseVersionsCatalog(rawJson: String): Map<String, VerseWellVersionAudio> {
        val parsed = json.decodeFromString<VerseWellVersionsResponse>(rawJson)
        return parsed.versions.associate { item ->
            normalizeVersionCode(item.code) to item.audio
        }
    }

    fun parseManifest(rawJson: String): AudioManifest =
        json.decodeFromString<AudioManifest>(rawJson)

    /**
     * Audio is supported for a translation ONLY if:
     * 1. Its code is one of KJV, NIV, TLB, GW, VOICE; AND
     * 2. Either the live/cached versions catalog reports `available == true`,
     *    a non-empty manifest is present, or verified local chapters already exist on device
     *    (unless the catalog explicitly reports `available == false` with zero local files).
     */
    fun isTranslationAudioAvailable(
        translationIdOrCode: String,
        versionsCatalog: Map<String, VerseWellVersionAudio>,
        cachedManifest: AudioManifest? = null,
        localVerifiedChaptersCount: Int = 0,
    ): Boolean {
        val code = normalizeVersionCode(translationIdOrCode)
        if (code !in AudioConfig.SUPPORTED_TRANSLATION_CODES) return false
        if (localVerifiedChaptersCount > 0) return true
        if (cachedManifest != null && cachedManifest.chapters.isNotEmpty()) return true
        val catalogEntry = versionsCatalog[code]
        return catalogEntry?.available == true && catalogEntry.chapters > 0
    }

    /**
     * Resolves a chapter's audio metadata from the translation's manifest.
     * Returns null when the manifest is missing or the chapter is not yet published.
     */
    fun findChapterInManifest(manifest: AudioManifest?, book: String, chapter: Int): AudioChapterMeta? {
        if (manifest == null || chapter <= 0) return null
        val canonBook = ScriptureReferenceUtils.canonicalBook(book) ?: book.trim()
        val expectedSlug = toBookSlug(canonBook)
        return manifest.chapters.firstOrNull { meta ->
            meta.chapter == chapter && (
                meta.book.equals(canonBook, ignoreCase = true) ||
                    (meta.book_slug.isNotBlank() && meta.book_slug.equals(expectedSlug, ignoreCase = true))
                )
        }
    }

    /**
     * Streaming SHA-256 computation over [file] using a small 16 KB buffer.
     */
    fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buf = ByteArray(16_384)
            var read: Int
            while (input.read(buf).also { read = it } != -1) {
                digest.update(buf, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Verifies a downloaded `.m4a` file against expected byte size and SHA-256 hash.
     */
    fun verifyAudioFile(file: File, expectedBytes: Long, expectedSha256: String): Boolean {
        if (!file.exists() || !file.isFile) return false
        val len = file.length()
        if (len <= 0L) return false
        if (expectedBytes > 0L && len != expectedBytes) return false
        val cleanExpected = expectedSha256.trim()
        if (cleanExpected.length != 64) return false
        return runCatching {
            sha256Hex(file).equals(cleanExpected, ignoreCase = true)
        }.getOrDefault(false)
    }

    fun estimateBulkDownload(
        versionCodeOrId: String,
        manifest: AudioManifest?,
        catalogAudio: VerseWellVersionAudio?,
        localChapters: List<DownloadedAudioChapter>,
    ): BulkAudioEstimate {
        val code = normalizeVersionCode(versionCodeOrId)
        if (manifest != null && manifest.chapters.isNotEmpty()) {
            val byFile = localChapters.associateBy { it.fileName }
            var doneCount = 0
            var doneBytes = 0L
            for (ch in manifest.chapters) {
                val local = byFile[ch.file]
                if (local != null && local.sha256.equals(ch.sha256, ignoreCase = true) && local.sizeBytes == ch.bytes) {
                    doneCount++
                    doneBytes += ch.bytes
                }
            }
            val totalCh = manifest.chapters.size
            val totalB = manifest.chapters.sumOf { it.bytes }.takeIf { it > 0L } ?: manifest.effectiveTotalBytes
            val remB = (totalB - doneBytes).coerceAtLeast(0L)
            return BulkAudioEstimate(
                versionCode = code,
                totalChapters = totalCh,
                downloadedChapters = doneCount,
                remainingChapters = (totalCh - doneCount).coerceAtLeast(0),
                totalBytes = totalB,
                downloadedBytes = doneBytes,
                remainingBytes = remB,
            )
        }
        val totalCh = catalogAudio?.chapters?.takeIf { it > 0 } ?: 260
        val totalB = catalogAudio?.total_bytes?.takeIf { it > 0L } ?: 0L
        val doneCh = localChapters.size.coerceAtMost(totalCh)
        val doneB = localChapters.sumOf { it.sizeBytes }
        val remB = if (totalB > 0L) (totalB - doneB).coerceAtLeast(0L) else 0L
        return BulkAudioEstimate(
            versionCode = code,
            totalChapters = totalCh,
            downloadedChapters = doneCh,
            remainingChapters = (totalCh - doneCh).coerceAtLeast(0),
            totalBytes = totalB,
            downloadedBytes = doneB,
            remainingBytes = remB,
        )
    }

    // ── Playback & Reading Integrity Rules (Section 6.8) ──────────────────

    fun clampPlaybackSpeed(speed: Float): Float =
        speed.coerceIn(AudioConfig.MIN_PLAYBACK_SPEED, AudioConfig.MAX_PLAYBACK_SPEED)

    fun nextPlaybackSpeed(current: Float): Float {
        val speeds = AudioConfig.PLAYBACK_SPEEDS
        val idx = speeds.indexOfFirst { kotlin.math.abs(it - current) < 0.01f }
        return if (idx == -1 || idx == speeds.lastIndex) speeds.first() else speeds[idx + 1]
    }

    /** Playback pauses when app goes to background or screen locks unless config flag is flipped. */
    fun shouldPauseForBackground(allowBackgroundPlayback: Boolean = AudioConfig.ALLOW_BACKGROUND_PLAYBACK): Boolean =
        !allowBackgroundPlayback

    /** Playback stays on the current chapter and stops at the end (no auto-advance). */
    fun shouldAutoAdvanceOnChapterEnd(): Boolean = false

    /** Reader must never auto-scroll to follow audio. */
    fun shouldAutoScrollReader(): Boolean = false

    /** Audio playback never counts as reading interaction or reading time. */
    fun shouldCountPlaybackTowardsReadingTimer(): Boolean = false

    /**
     * Computes only the genuine active reading seconds delta accrued by [com.forgebuild.forgehouse50.data.ReadingTimer]
     * since the last server sync, ensuring passive audio listening sends 0 seconds to `/api/read/time`.
     */
    fun computeSyncedReadingDelta(currentTimerSeconds: Int, lastSyncedSeconds: Int): Int =
        (currentTimerSeconds - lastSyncedSeconds).coerceAtLeast(0)

    fun formatDurationMs(ms: Long): String {
        val totalSec = (ms.coerceAtLeast(0L) / 1000L).toInt()
        val m = totalSec / 60
        val s = totalSec % 60
        return String.format(Locale.US, "%d:%02d", m, s)
    }

    fun formatAudioBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        return when {
            bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes / gb)
            bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
            bytes >= kb -> String.format(Locale.US, "%.0f KB", bytes / kb)
            else -> "$bytes B"
        }
    }
}

/**
 * Pluggable transport interface so JUnit tests verify real file streaming,
 * HTTP Range resume, and SHA-256 enforcement offline on JVM.
 */
interface AudioTransport {
    suspend fun fetchText(url: String): String
    suspend fun streamToFile(
        url: String,
        destPartFile: File,
        expectedTotalBytes: Long,
        onProgress: (bytesOnDisk: Long, totalBytes: Long) -> Unit,
    )
}

class HttpUrlConnectionAudioTransport(
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 45_000,
) : AudioTransport {

    override suspend fun fetchText(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            requestMethod = "GET"
            setRequestProperty("User-Agent", AudioConfig.USER_AGENT)
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("Server returned HTTP $code")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    override suspend fun streamToFile(
        url: String,
        destPartFile: File,
        expectedTotalBytes: Long,
        onProgress: (bytesOnDisk: Long, totalBytes: Long) -> Unit,
    ) {
        destPartFile.parentFile?.mkdirs()
        val existingLen = if (destPartFile.exists()) destPartFile.length() else 0L
        if (expectedTotalBytes > 0L && existingLen == expectedTotalBytes) {
            onProgress(existingLen, expectedTotalBytes)
            return
        }
        if (expectedTotalBytes > 0L && existingLen > expectedTotalBytes) {
            destPartFile.delete()
        }
        val resumeOffset = if (destPartFile.exists()) destPartFile.length() else 0L

        var conn = openAudioConnection(url, resumeOffset)
        try {
            var code = conn.responseCode
            if (code == 416) {
                conn.disconnect()
                destPartFile.delete()
                conn = openAudioConnection(url, 0L)
                code = conn.responseCode
            }
            val (input: InputStream, append: Boolean, startBytes: Long) = when (code) {
                HttpURLConnection.HTTP_PARTIAL -> Triple(conn.inputStream, true, resumeOffset)
                HttpURLConnection.HTTP_OK -> Triple(conn.inputStream, false, 0L)
                404 -> throw IllegalStateException("Audio for this chapter is not available yet.")
                else -> throw IllegalStateException("Audio download failed (HTTP $code).")
            }
            val reportedTotal = conn.contentLengthLong
            val totalBytes = when {
                expectedTotalBytes > 0L -> expectedTotalBytes
                reportedTotal > 0L -> reportedTotal + startBytes
                else -> 0L
            }
            var writtenTotal = startBytes
            onProgress(writtenTotal, totalBytes)
            input.use { inStream ->
                FileOutputStream(destPartFile, append).use { outStream ->
                    val buffer = ByteArray(16_384)
                    var read: Int
                    while (inStream.read(buffer).also { read = it } != -1) {
                        currentCoroutineContext().ensureActive()
                        outStream.write(buffer, 0, read)
                        writtenTotal += read
                        onProgress(writtenTotal, totalBytes)
                    }
                    outStream.fd.sync()
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun openAudioConnection(url: String, resumeOffset: Long): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            requestMethod = "GET"
            setRequestProperty("User-Agent", AudioConfig.USER_AGENT)
            if (resumeOffset > 0L) {
                setRequestProperty("Range", "bytes=$resumeOffset-")
            }
        }
    }
}

interface AudioIndexStore {
    suspend fun getChapter(key: String): DownloadedAudioChapter?
    suspend fun getForVersion(versionCode: String): List<DownloadedAudioChapter>
    suspend fun getAll(): List<DownloadedAudioChapter>
    suspend fun put(row: DownloadedAudioChapter)
    suspend fun deleteChapter(key: String)
    suspend fun deleteVersion(versionCode: String)
}

class RoomAudioIndexStore(private val dao: AudioChapterDao) : AudioIndexStore {
    override suspend fun getChapter(key: String): DownloadedAudioChapter? = dao.chapter(key)
    override suspend fun getForVersion(versionCode: String): List<DownloadedAudioChapter> = dao.forVersion(versionCode)
    override suspend fun getAll(): List<DownloadedAudioChapter> = dao.all()
    override suspend fun put(row: DownloadedAudioChapter) = dao.put(row)
    override suspend fun deleteChapter(key: String) = dao.deleteChapter(key)
    override suspend fun deleteVersion(versionCode: String) = dao.deleteVersion(versionCode)
}

class InMemoryAudioIndexStore : AudioIndexStore {
    private val lock = Any()
    private val map = linkedMapOf<String, DownloadedAudioChapter>()

    override suspend fun getChapter(key: String): DownloadedAudioChapter? =
        synchronized(lock) { map[key] }

    override suspend fun getForVersion(versionCode: String): List<DownloadedAudioChapter> =
        synchronized(lock) { map.values.filter { it.versionCode.equals(versionCode, ignoreCase = true) } }

    override suspend fun getAll(): List<DownloadedAudioChapter> =
        synchronized(lock) { map.values.toList() }

    override suspend fun put(row: DownloadedAudioChapter) {
        synchronized(lock) { map[row.key] = row }
    }

    override suspend fun deleteChapter(key: String) {
        synchronized(lock) { map.remove(key) }
    }

    override suspend fun deleteVersion(versionCode: String) {
        synchronized(lock) {
            val toRemove = map.values.filter { it.versionCode.equals(versionCode, ignoreCase = true) }.map { it.key }
            toRemove.forEach { map.remove(it) }
        }
    }
}

/**
 * Pure JVM audio download & verification coordinator used by [AudioDownloadManager] and unit tests.
 */
class AudioDownloadEngine(
    private val audioRootDir: File,
    private val indexStore: AudioIndexStore,
    private val transport: AudioTransport,
    private val apiBaseUrl: String = AudioConfig.VERSEWELL_API_BASE,
) {
    fun versionDir(versionCodeOrId: String): File {
        val codeLower = AudioLogic.normalizeVersionCode(versionCodeOrId).lowercase(Locale.US)
        return File(audioRootDir, codeLower)
    }

    fun chapterFile(versionCodeOrId: String, fileName: String): File =
        File(versionDir(versionCodeOrId), fileName)

    fun partFile(versionCodeOrId: String, fileName: String): File =
        File(versionDir(versionCodeOrId), "$fileName.part")

    /**
     * Returns the verified local `.m4a` file if present on disk and matching recorded size
     * (re-verifying SHA-256 if requested), or null otherwise.
     */
    suspend fun resolveVerifiedLocalFile(
        versionCodeOrId: String,
        book: String,
        chapter: Int,
        verifyHashNow: Boolean = false,
    ): File? {
        val key = AudioLogic.chapterKey(versionCodeOrId, book, chapter)
        val indexed = indexStore.getChapter(key) ?: return null
        val file = chapterFile(versionCodeOrId, indexed.fileName)
        if (!file.exists() || !file.isFile || file.length() <= 0L) {
            indexStore.deleteChapter(key)
            return null
        }
        if (indexed.sizeBytes > 0L && file.length() != indexed.sizeBytes) {
            file.delete()
            indexStore.deleteChapter(key)
            return null
        }
        if (verifyHashNow && !AudioLogic.verifyAudioFile(file, indexed.sizeBytes, indexed.sha256)) {
            file.delete()
            indexStore.deleteChapter(key)
            return null
        }
        return file
    }

    /**
     * Downloads a single chapter manually, skipping network I/O if an existing local file
     * already passes SHA-256 verification, resuming `.part` via HTTP Range if interrupted,
     * and rejecting/deleting any file that fails SHA-256 verification.
     */
    suspend fun downloadChapter(
        versionCodeOrId: String,
        meta: AudioChapterMeta,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): File {
        val codeUpper = AudioLogic.normalizeVersionCode(versionCodeOrId)
        if (codeUpper !in AudioConfig.SUPPORTED_TRANSLATION_CODES) {
            throw IllegalArgumentException("Audio is not available for $codeUpper.")
        }
        val codeLower = codeUpper.lowercase(Locale.US)
        val canonBook = ScriptureReferenceUtils.canonicalBook(meta.book) ?: meta.book.trim()
        val key = AudioLogic.chapterKey(codeLower, canonBook, meta.chapter)
        val dir = versionDir(codeLower).apply { mkdirs() }
        val target = File(dir, meta.file)
        val part = File(dir, "${meta.file}.part")

        // Skip-if-valid (cryptographic SHA-256 check)
        if (target.exists() && AudioLogic.verifyAudioFile(target, meta.bytes, meta.sha256)) {
            part.delete()
            indexStore.put(
                DownloadedAudioChapter(
                    key = key,
                    versionCode = codeLower,
                    book = canonBook,
                    bookSlug = meta.book_slug.ifBlank { AudioLogic.toBookSlug(canonBook) },
                    chapter = meta.chapter,
                    fileName = meta.file,
                    sizeBytes = target.length(),
                    sha256 = meta.sha256.lowercase(Locale.US),
                    durationMs = meta.duration_ms,
                )
            )
            onProgress(target.length(), target.length())
            return target
        }

        if (target.exists()) {
            target.delete()
        }

        val url = AudioLogic.chapterAudioUrl(codeLower, meta, apiBaseUrl)
        transport.streamToFile(url, part, meta.bytes) { read, total ->
            onProgress(read, if (meta.bytes > 0L) meta.bytes else total)
        }

        currentCoroutineContext().ensureActive()

        if (!AudioLogic.verifyAudioFile(part, meta.bytes, meta.sha256)) {
            part.delete()
            target.delete()
            indexStore.deleteChapter(key)
            throw IllegalStateException("Downloaded audio failed SHA-256 verification for $canonBook ${meta.chapter}.")
        }

        if (target.exists()) target.delete()
        val moved = part.renameTo(target)
        if (!moved) {
            part.copyTo(target, overwrite = true)
            part.delete()
        }

        indexStore.put(
            DownloadedAudioChapter(
                key = key,
                versionCode = codeLower,
                book = canonBook,
                bookSlug = meta.book_slug.ifBlank { AudioLogic.toBookSlug(canonBook) },
                chapter = meta.chapter,
                fileName = meta.file,
                sizeBytes = target.length(),
                sha256 = meta.sha256.lowercase(Locale.US),
                durationMs = meta.duration_ms,
            )
        )
        onProgress(target.length(), target.length())
        return target
    }

    /**
     * Downloads all chapters listed in [manifest] for [versionCodeOrId], skipping any chapter
     * that already passes SHA-256 on disk and reporting live byte + chapter progress.
     */
    suspend fun downloadTranslation(
        versionCodeOrId: String,
        manifest: AudioManifest,
        onProgress: (BulkDownloadProgress) -> Unit,
    ) {
        val codeUpper = AudioLogic.normalizeVersionCode(versionCodeOrId)
        if (codeUpper !in AudioConfig.SUPPORTED_TRANSLATION_CODES) {
            throw IllegalArgumentException("Audio is not available for $codeUpper.")
        }
        if (manifest.chapters.isEmpty()) {
            throw IllegalStateException("No audio chapters are available in the manifest yet.")
        }

        val totalChapters = manifest.chapters.size
        val totalBytes = manifest.chapters.sumOf { it.bytes }.takeIf { it > 0L } ?: manifest.effectiveTotalBytes
        var completedChapters = 0
        var completedBytes = 0L

        val pending = mutableListOf<AudioChapterMeta>()
        val codeLower = codeUpper.lowercase(Locale.US)
        val dir = versionDir(codeLower).apply { mkdirs() }

        // First pass: skip-if-valid (SHA-256 verified) so resume starts immediately at real progress
        for (meta in manifest.chapters) {
            currentCoroutineContext().ensureActive()
            val target = File(dir, meta.file)
            if (target.exists() && AudioLogic.verifyAudioFile(target, meta.bytes, meta.sha256)) {
                val canonBook = ScriptureReferenceUtils.canonicalBook(meta.book) ?: meta.book.trim()
                val key = AudioLogic.chapterKey(codeLower, canonBook, meta.chapter)
                indexStore.put(
                    DownloadedAudioChapter(
                        key = key,
                        versionCode = codeLower,
                        book = canonBook,
                        bookSlug = meta.book_slug.ifBlank { AudioLogic.toBookSlug(canonBook) },
                        chapter = meta.chapter,
                        fileName = meta.file,
                        sizeBytes = target.length(),
                        sha256 = meta.sha256.lowercase(Locale.US),
                        durationMs = meta.duration_ms,
                    )
                )
                completedChapters++
                completedBytes += target.length()
            } else {
                pending.add(meta)
            }
        }

        onProgress(
            BulkDownloadProgress(
                versionCode = codeUpper,
                chaptersDone = completedChapters,
                totalChapters = totalChapters,
                bytesDownloaded = completedBytes,
                totalBytes = totalBytes,
            )
        )

        for (meta in pending) {
            currentCoroutineContext().ensureActive()
            val baseBytes = completedBytes
            val downloadedFile = downloadChapter(codeUpper, meta) { chRead, _ ->
                onProgress(
                    BulkDownloadProgress(
                        versionCode = codeUpper,
                        chaptersDone = completedChapters,
                        totalChapters = totalChapters,
                        bytesDownloaded = (baseBytes + chRead).coerceAtMost(maxOf(totalBytes, baseBytes + chRead)),
                        totalBytes = totalBytes,
                        currentBook = meta.book,
                        currentChapter = meta.chapter,
                    )
                )
            }
            completedChapters++
            completedBytes += downloadedFile.length()
            onProgress(
                BulkDownloadProgress(
                    versionCode = codeUpper,
                    chaptersDone = completedChapters,
                    totalChapters = totalChapters,
                    bytesDownloaded = completedBytes,
                    totalBytes = totalBytes,
                    currentBook = meta.book,
                    currentChapter = meta.chapter,
                )
            )
        }
    }

    /** Removes any `.part` temporary files for a translation when user explicitly cancels. */
    fun cleanPartialFiles(versionCodeOrId: String) {
        val dir = versionDir(versionCodeOrId)
        if (!dir.exists()) return
        dir.listFiles()?.filter { it.name.endsWith(".part") }?.forEach { it.delete() }
    }

    /** Deletes a single downloaded chapter's `.m4a` and `.part` files and removes its index entry. */
    suspend fun deleteChapterAudio(versionCodeOrId: String, book: String, chapter: Int) {
        val key = AudioLogic.chapterKey(versionCodeOrId, book, chapter)
        val indexed = indexStore.getChapter(key)
        if (indexed != null) {
            chapterFile(versionCodeOrId, indexed.fileName).delete()
            partFile(versionCodeOrId, indexed.fileName).delete()
            indexStore.deleteChapter(key)
        }
        val slug = AudioLogic.toBookSlug(book)
        val fallbackName = String.format(Locale.US, "%s-%03d.m4a", slug, chapter)
        chapterFile(versionCodeOrId, fallbackName).delete()
        partFile(versionCodeOrId, fallbackName).delete()
    }

    /** Deletes all downloaded `.m4a` and `.part` audio files for [versionCodeOrId] (keeps cached manifest if any, or deletes audio files). */
    suspend fun deleteTranslationAudio(versionCodeOrId: String) {
        val codeLower = AudioLogic.normalizeVersionCode(versionCodeOrId).lowercase(Locale.US)
        val dir = versionDir(codeLower)
        if (dir.exists()) {
            dir.listFiles()?.forEach { f ->
                if (f.name.endsWith(".m4a") || f.name.endsWith(".part")) {
                    f.delete()
                }
            }
        }
        indexStore.deleteVersion(codeLower)
    }
}
