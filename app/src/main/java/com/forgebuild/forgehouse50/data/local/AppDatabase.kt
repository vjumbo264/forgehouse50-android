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
import kotlinx.coroutines.flow.Flow

/**
 * Persistent on-device store for the heaviest, most-reused assets in the
 * app: scripture text and downloaded chapter audio.
 *
 * This is deliberately stronger than the general cache-first UI pattern:
 * once a chapter's text (keyed by translation+book+chapter) or an audio
 * file is stored here, reopening it — even fully offline — loads from this
 * store with NO network call. Entries are only re-fetched when absent, or
 * when the user clears the store from Profile → Downloaded content.
 *
 * Audio binaries themselves live as files under filesDir/audio/; this table
 * tracks what is present, its size and source URL (a cheap re-validation
 * handle if the backend ever signals the underlying content changed).
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
    val audioPath: String? = null,
    val fetchedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "downloaded_audio")
data class DownloadedAudio(
    @PrimaryKey val key: String,          // "$translation|$book|$chapter"
    val translation: String,
    val book: String,
    val chapter: Int,
    val sourceUrl: String,
    val filePath: String,
    val sizeBytes: Long = 0,
    val downloadedAt: Long = System.currentTimeMillis(),
)

@Dao
interface ScriptureDao {
    @Query("SELECT * FROM scripture_chapters WHERE `key` = :key")
    suspend fun chapter(key: String): ScriptureChapter?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putChapter(chapter: ScriptureChapter)

    @Query("SELECT COUNT(*) FROM scripture_chapters")
    suspend fun chapterCount(): Int

    @Query("DELETE FROM scripture_chapters")
    suspend fun clearAll()
}

@Dao
interface AudioDao {
    @Query("SELECT * FROM downloaded_audio WHERE `key` = :key")
    suspend fun audio(key: String): DownloadedAudio?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAudio(audio: DownloadedAudio)

    @Query("SELECT * FROM downloaded_audio ORDER BY downloadedAt DESC")
    fun all(): Flow<List<DownloadedAudio>>

    @Query("SELECT COALESCE(SUM(sizeBytes),0) FROM downloaded_audio")
    suspend fun totalSizeBytes(): Long

    @Query("DELETE FROM downloaded_audio WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM downloaded_audio")
    suspend fun clearAll()
}

@Database(entities = [ScriptureChapter::class, DownloadedAudio::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scriptureDao(): ScriptureDao
    abstract fun audioDao(): AudioDao

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
