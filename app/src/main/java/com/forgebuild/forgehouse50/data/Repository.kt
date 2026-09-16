package com.forgebuild.forgehouse50.data

import android.content.Context
import com.forgebuild.forgehouse50.data.local.AppDatabase
import com.forgebuild.forgehouse50.data.local.DownloadedTranslation
import com.forgebuild.forgehouse50.data.local.ScriptureChapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.util.zip.GZIPInputStream

/**
 * App-wide repository: wires the API client, the secure session store and the
 * Room offline-Bible store together.
 *
 * leaderboard_audio_removal_offline_bible_v1 / ISSUE 5 (Android-only):
 * scripture now resolves with ZERO read-time network for any available
 * translation. The KJV New Testament is bundled in assets (bible/kjv.json.gz)
 * and imported into Room on first launch; every other translation the user
 * downloads is fetched once and stored permanently. getPassage() serves from
 * the on-device store whenever the translation is present and only touches
 * the network for a translation that is neither bundled nor downloaded.
 *
 * ISSUE 3: all chapter-audio handling is removed (audio removed upstream).
 */
class Repository(
    private val appContext: Context,
    val session: SessionStore,
) {
    val api = ApiClient(session)
    private val db = AppDatabase.get(appContext)
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        const val KJV_ID = "versewell-kjv"
        private const val KJV_ASSET = "bible/kjv.json.gz"
        /** KJV NT canonical book -> chapter count (260 chapters). */
        private val NT_CHAPTERS: List<Pair<String, Int>> = listOf(
            "Matthew" to 28, "Mark" to 16, "Luke" to 24, "John" to 21,
            "Acts" to 28, "Romans" to 16, "1 Corinthians" to 16,
            "2 Corinthians" to 13, "Galatians" to 6, "Ephesians" to 6,
            "Philippians" to 4, "Colossians" to 4, "1 Thessalonians" to 5,
            "2 Thessalonians" to 3, "1 Timothy" to 6, "2 Timothy" to 4,
            "Titus" to 3, "Philemon" to 1, "Hebrews" to 13, "James" to 5,
            "1 Peter" to 5, "2 Peter" to 3, "1 John" to 5, "2 John" to 1,
            "3 John" to 1, "Jude" to 1, "Revelation" to 22,
        )
        val KJV_TOTAL_CHAPTERS: Int = NT_CHAPTERS.sumOf { it.second }
    }

    // ── Translation availability (offline-first) ──────────────────────────

    /** True when [translation] is fully on-device (bundled KJV or downloaded). */
    suspend fun isTranslationAvailable(translation: String): Boolean = withContext(Dispatchers.IO) {
        translation == KJV_ID || db.translationDao().get(translation) != null
    }

    /** Import the bundled KJV asset into Room once. No-op if already done. */
    suspend fun ensureBundledKjvImported(): Unit = withContext(Dispatchers.IO) {
        if (db.scriptureDao().chapterCountFor(KJV_ID) >= KJV_TOTAL_CHAPTERS) return@withContext
        runCatching {
            appContext.assets.open(KJV_ASSET).use { raw ->
                GZIPInputStream(raw).bufferedReader().use { reader ->
                    val books = json.decodeFromString<Map<String, Map<String, BundledChapter>>>(reader.readText())
                    importBundle(KJV_ID, books, "King James Version")
                }
            }
        }
    }

    // ── Scripture text (offline-first) ────────────────────────────────────

    /**
     * Full passage for [book] chapters [cs]..[ce]. Serves from the on-device
     * store with NO network call when the translation is bundled/downloaded.
     * Falls back to a one-time fetch+store only when it is not on-device.
     */
    suspend fun getPassage(book: String, cs: Int, ce: Int, translation: String): PassageResponse =
        withContext(Dispatchers.IO) {
            ensureBundledKjvImported()
            val have = (cs..ce).mapNotNull { ch ->
                db.scriptureDao().chapter(AppDatabase.chapterKey(translation, book, ch))
            }
            if (have.size == (ce - cs + 1)) return@withContext assembleFromCache(book, cs, ce, translation, have)
            val fresh = api.passage(book, cs, ce, translation)
            storePassage(book, fresh)
            fresh
        }

    private suspend fun assembleFromCache(
        book: String, cs: Int, ce: Int, translation: String, rows: List<ScriptureChapter>
    ): PassageResponse {
        val ordered = rows.sortedBy { it.chapter }
        val verses = ordered.flatMap {
            runCatching { json.decodeFromString<List<Verse>>(it.versesJson) }.getOrDefault(emptyList())
        }
        val intros = ordered.flatMap {
            runCatching { json.decodeFromString<List<Intro>>(it.introsJson) }.getOrDefault(emptyList())
        }
        val reference = if (cs == ce) "$book $cs" else "$book $cs–$ce"
        return PassageResponse(
            reference = reference, verses = verses, intros = intros,
            attribution = rows.firstOrNull()?.attribution ?: "",
            translation = translation, source = "ondevice",
        )
    }

    private suspend fun storePassage(book: String, p: PassageResponse) {
        val byChapter = p.verses.groupBy { it.chapter }
        val introsByChapter = p.intros.groupBy { it.chapter }
        for ((ch, verses) in byChapter) {
            db.scriptureDao().putChapter(
                ScriptureChapter(
                    key = AppDatabase.chapterKey(p.translation, book, ch),
                    translation = p.translation, book = book, chapter = ch,
                    reference = if (byChapter.size == 1) p.reference else "$book $ch",
                    versesJson = json.encodeToString(verses),
                    introsJson = json.encodeToString(introsByChapter[ch] ?: emptyList()),
                    attribution = p.attribution,
                )
            )
        }
    }

    // ── Downloadable translations (Issue 5b/c) ────────────────────────────

    /** Downloaded (non-KJV) translations, for the manage/storage UI. */
    suspend fun downloadedTranslations(): List<DownloadedTranslation> =
        withContext(Dispatchers.IO) { db.translationDao().all() }

    /** Total bytes used by all downloaded (non-KJV) translations. */
    suspend fun translationsTotalSizeBytes(): Long =
        withContext(Dispatchers.IO) { db.translationDao().totalSizeBytes() }

    /**
     * Estimated download size for a translation, derived from the bundled KJV
     * footprint scaled by its verse count relative to KJV. Lets the UI show a
     * size BEFORE the user commits to a download. ~2.5 MB per translation.
     */
    suspend fun estimateTranslationBytes(verseCount: Int): Long = withContext(Dispatchers.IO) {
        ensureBundledKjvImported()
        val kjvBytes = db.scriptureDao().sizeBytesFor(KJV_ID).coerceAtLeast(1)
        val kjvVerses = 7_957 // KJV NT verse count
        (kjvBytes.toDouble() * verseCount / kjvVerses).toLong()
    }

    /** Import one parsed bundle map (books -> chapter -> BundledChapter) into Room. */
    private suspend fun importBundle(translation: String, books: Map<String, Map<String, BundledChapter>>, name: String) {
        val rows = mutableListOf<ScriptureChapter>()
        for ((book, chapters) in books) for ((chStr, ch) in chapters) {
            val chNum = chStr.toIntOrNull() ?: continue
            rows.add(ScriptureChapter(
                key = AppDatabase.chapterKey(translation, book, chNum),
                translation = translation, book = book, chapter = chNum,
                reference = "$book $chNum",
                versesJson = json.encodeToString(ch.v.map { Verse(chNum, it.n, it.t, it.f) }),
                introsJson = json.encodeToString(ch.i.map { Intro(chNum, it.s, it.e, it.t) }),
                attribution = "Scripture text: $name, via VerseWell (versewell.pages.dev).",
            ))
        }
        db.scriptureDao().putChapters(rows)
    }

    /**
     * Download one translation as a SINGLE pre-built bundle file (Issue 3) —
     * one HTTP request instead of 260 per-chapter fetches. The gzipped-JSON
     * shape matches the bundled KJV asset, so the proven importer is reused.
     * onProgress reports (bytesRead, totalBytes).
     */
    suspend fun downloadTranslation(
        translation: String,
        name: String,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Unit = withContext(Dispatchers.IO) {
        val code = translation.removePrefix("versewell-").lowercase()
        val bytes = api.fetchBundleFile("/bundles/$code.json.gz") { r, t ->
            onProgress(r.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), t.coerceAtLeast(0).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        }
        val books = java.util.zip.GZIPInputStream(bytes.inputStream()).bufferedReader().use {
            json.decodeFromString<Map<String, Map<String, BundledChapter>>>(it.readText())
        }
        importBundle(translation, books, name)
        val size = db.scriptureDao().sizeBytesFor(translation)
        val count = db.scriptureDao().chapterCountFor(translation)
        db.translationDao().put(DownloadedTranslation(translation = translation, name = name, chapterCount = count, sizeBytes = size))
    }

    /** Remove a downloaded translation and free its storage. */
    suspend fun removeTranslation(translation: String): Unit = withContext(Dispatchers.IO) {
        db.scriptureDao().clearTranslation(translation)
        db.translationDao().delete(translation)
    }

    /** Profile → Downloaded content: per-translation bytes for the storage UI. */
    suspend fun translationSizeBytes(translation: String): Long =
        withContext(Dispatchers.IO) { db.scriptureDao().sizeBytesFor(translation) }

    suspend fun scriptureChapterCount(): Int = withContext(Dispatchers.IO) { db.scriptureDao().chapterCount() }

    /** Clear every downloaded translation (keeps the bundled KJV intact). */
    suspend fun clearDownloadedTranslations() = withContext(Dispatchers.IO) {
        for (t in db.translationDao().all()) db.scriptureDao().clearTranslation(t.translation)
        db.translationDao().clearAll()
    }
}
