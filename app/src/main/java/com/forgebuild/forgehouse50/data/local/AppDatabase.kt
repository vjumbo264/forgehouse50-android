package com.forgebuild.forgehouse50.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Persistent on-device store for scripture text.
 *
 * leaderboard_audio_removal_offline_bible_v1 / ISSUE 5: this is now the
 * offline-Bible store. The bundled KJV New Testament is IMPORTED into this
 * table on first launch, and any other translation the user downloads is
 * stored here permanently — so once a translation is on-device it reads with
 * ZERO network. This is genuine persistent storage (Room/SQLite), never an
 * evictable cache.
 *
 * ISSUE 3: the audio tables are gone (audio removed upstream). Version bumped
 * 1 -> 2 with destructive migration so the old downloaded_audio table and any
 * previously cached audio-bearing rows are dropped.
 */

@Entity(tableName = "scripture_chapters")
data class ScriptureChapter(
    @PrimaryKey val key: String,          // "$translation|$book|$chapter"
    val translation: String,
    val book: String,
    val chapter: Int,
    val reference: String = "",
    val versesJson: String = "",          // serialized List<Verse>
    val introsJson: String = "",          // serialized List<Intro>
    val attribution: String = "",
    val fetchedAt: Long = System.currentTimeMillis(),
)

/** One row per downloaded translation (KJV is bundled, so not listed here). */
@Entity(tableName = "downloaded_translations")
data class DownloadedTranslation(
    @PrimaryKey val translation: String,  // e.g. "versewell-niv"
    val name: String = "",
    val chapterCount: Int = 0,
    val sizeBytes: Long = 0,
    val downloadedAt: Long = System.currentTimeMillis(),
)

@Dao
interface ScriptureDao {
    @Query("SELECT * FROM scripture_chapters WHERE `key` = :key")
    suspend fun chapter(key: String): ScriptureChapter?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putChapter(chapter: ScriptureChapter)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putChapters(chapters: List<ScriptureChapter>)

    @Query("SELECT COUNT(*) FROM scripture_chapters")
    suspend fun chapterCount(): Int

    @Query("SELECT COUNT(*) FROM scripture_chapters WHERE translation = :t")
    suspend fun chapterCountFor(t: String): Int

    /** Approximate on-device footprint of one translation, in bytes. */
    @Query("SELECT COALESCE(SUM(LENGTH(versesJson) + LENGTH(introsJson) + LENGTH(reference) + LENGTH(attribution)), 0) FROM scripture_chapters WHERE translation = :t")
    suspend fun sizeBytesFor(t: String): Long

    @Query("DELETE FROM scripture_chapters WHERE translation = :t")
    suspend fun clearTranslation(t: String)

    @Query("SELECT DISTINCT translation FROM scripture_chapters")
    suspend fun translationsPresent(): List<String>

    @Query("DELETE FROM scripture_chapters")
    suspend fun clearAll()
}

@Dao
interface TranslationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(t: DownloadedTranslation)

    @Query("SELECT * FROM downloaded_translations ORDER BY name")
    suspend fun all(): List<DownloadedTranslation>

    @Query("SELECT * FROM downloaded_translations WHERE translation = :t")
    suspend fun get(t: String): DownloadedTranslation?

    @Query("DELETE FROM downloaded_translations WHERE translation = :t")
    suspend fun delete(t: String)

    @Query("SELECT COALESCE(SUM(sizeBytes),0) FROM downloaded_translations")
    suspend fun totalSizeBytes(): Long

    @Query("DELETE FROM downloaded_translations")
    suspend fun clearAll()
}

@Database(
    entities = [ScriptureChapter::class, DownloadedTranslation::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scriptureDao(): ScriptureDao
    abstract fun translationDao(): TranslationDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "forgehouse50.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }

        fun chapterKey(translation: String, book: String, chapter: Int) =
            "$translation|$book|$chapter"
    }
}
