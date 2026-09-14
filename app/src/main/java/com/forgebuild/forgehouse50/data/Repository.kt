package com.forgebuild.forgehouse50.data

import android.content.Context
import com.forgebuild.forgehouse50.data.local.AppDatabase
import com.forgebuild.forgehouse50.data.local.DownloadedAudio
import com.forgebuild.forgehouse50.data.local.ScriptureChapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.File
import java.net.URL

/**
 * Single app-wide repository. Wires the API client, the secure session
 * store and the Room offline store together and owns the two standing
 * ForgeBuild data rules for this app:
 *
 *  1. UI lists (today/progress/leaderboard/notes) are rendered cache-first
 *     by the ViewModels via the Engine CacheFirstStore pattern — this
 *     repository only does the live fetch side for them.
 *  2. Scripture text and chapter audio are PERSISTENT once fetched
 *     (Room / filesDir) — getPassage() and ensureAudio() below never hit
 *     the network for content already on-device, so a chapter reopens
 *     instantly and replays offline.
 */
class Repository(
    private val appContext: Context,
    val session: SessionStore,
) {
    val api = ApiClient(session)
    private val db = AppDatabase.get(appContext)
    private val json = Json { ignoreUnknownKeys = true }

    private val audioDir: File
        get() = File(appContext.filesDir, "audio").apply { mkdirs() }

    // ── Scripture text (persistent offline store) ─────────────────────────

    /**
     * Returns the full passage for [book] chapters [cs]..[ce]. Serves from
     * the Room store chapter-by-chapter; only chapters missing on-device are
     * fetched from the backend (and then permanently stored).
     */
    suspend fun getPassage(book: String, cs: Int, ce: Int, translation: String): PassageResponse =
        withContext(Dispatchers.IO) {
            val have = (cs..ce).mapNotNull { ch ->
                db.scriptureDao().chapter(AppDatabase.chapterKey(translation, book, ch))
            }
            if (have.size == (ce - cs + 1)) {
                // Fully cached — no network call at all.
                return@withContext assembleFromCache(book, cs, ce, translation, have)
            }
            // Some chapters missing: fetch the whole range once, then store
            // each chapter permanently for offline reuse.
            val fresh = api.passage(book, cs, ce, translation)
            storePassage(book, fresh)
            fresh
        }

    private suspend fun assembleFromCache(
        book: String, cs: Int, ce: Int, translation: String, rows: List<ScriptureChapter>
    ): PassageResponse {
        val verses = rows.sortedBy { it.chapter }.flatMap {
            runCatching { json.decodeFromString<List<Verse>>(it.versesJson) }.getOrDefault(emptyList())
        }
        val intros = rows.sortedBy { it.chapter }.flatMap {
            runCatching { json.decodeFromString<List<Intro>>(it.introsJson) }.getOrDefault(emptyList())
        }
        val reference = if (cs == ce) "$book $cs" else "$book $cs–$ce"
        return PassageResponse(
            reference = reference,
            verses = verses,
            intros = intros,
            audio_path = rows.firstNotNullOfOrNull { it.audioPath },
            attribution = rows.firstOrNull()?.attribution ?: "",
            translation = translation,
            source = "ondevice",
        )
    }

    private suspend fun storePassage(book: String, p: PassageResponse) {
        val byChapter = p.verses.groupBy { it.chapter }
        val introsByChapter = p.intros.groupBy { it.chapter }
        for ((ch, verses) in byChapter) {
            db.scriptureDao().putChapter(
                ScriptureChapter(
                    key = AppDatabase.chapterKey(p.translation, book, ch),
                    translation = p.translation,
                    book = book,
                    chapter = ch,
                    reference = if (byChapter.size == 1) p.reference else "$book $ch",
                    versesJson = json.encodeToString(verses),
                    introsJson = json.encodeToString(introsByChapter[ch] ?: emptyList()),
                    attribution = p.attribution,
                    audioPath = p.audio_path,
                )
            )
        }
    }

    // ── Chapter audio (persistent offline store) ──────────────────────────

    /** Local file for a downloaded chapter, or null if not on-device. */
    suspend fun localAudioFile(translation: String, book: String, chapter: Int): File? =
        withContext(Dispatchers.IO) {
            val row = db.audioDao().audio(AppDatabase.chapterKey(translation, book, chapter)) ?: return@withContext null
            val f = File(row.filePath)
            if (f.exists() && f.length() > 0) f else { db.audioDao().delete(row.key); null }
        }

    /**
     * Ensures the chapter's audio exists on-device. If already downloaded,
     * returns the existing local file with NO network call (offline replay).
     * Otherwise streams it from [sourceUrl] once and keeps it permanently.
     */
    suspend fun ensureAudio(translation: String, book: String, chapter: Int, sourceUrl: String): File =
        withContext(Dispatchers.IO) {
            localAudioFile(translation, book, chapter)?.let { return@withContext it }
            val safe = { s: String -> s.replace(Regex("[^A-Za-z0-9]+"), "-") }
            val out = File(audioDir, "${safe(translation)}_${safe(book)}_$chapter.m4a")
            URL(sourceUrl).openStream().use { input ->
                out.outputStream().use { input.copyTo(it) }
            }
            db.audioDao().putAudio(
                DownloadedAudio(
                    key = AppDatabase.chapterKey(translation, book, chapter),
                    translation = translation, book = book, chapter = chapter,
                    sourceUrl = sourceUrl, filePath = out.absolutePath, sizeBytes = out.length(),
                )
            )
            out
        }

    suspend fun audioTotalSizeBytes(): Long = withContext(Dispatchers.IO) { db.audioDao().totalSizeBytes() }
    suspend fun scriptureChapterCount(): Int = withContext(Dispatchers.IO) { db.scriptureDao().chapterCount() }

    /** Profile → Downloaded content → Clear: remove all offline text + audio. */
    suspend fun clearOfflineContent() = withContext(Dispatchers.IO) {
        db.audioDao().clearAll()
        db.scriptureDao().clearAll()
        audioDir.deleteRecursively()
    }
}
