package com.forgebuild.forgehouse50.audio

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
 * Persistent Room index of verified on-device audio chapters (.m4a).
 * Kept in its own dedicated SQLite database so upgrading from older app builds
 * never triggers destructive migration on [com.forgebuild.forgehouse50.data.local.AppDatabase]
 * (preserving all previously downloaded Scripture text translations).
 */
@Entity(tableName = "downloaded_audio_chapters")
data class DownloadedAudioChapter(
    @PrimaryKey val key: String,          // "${versionCode}|${book}|${chapter}"
    val versionCode: String,              // lowercase code: "kjv", "niv", "tlb", "gw", "voice"
    val book: String,                     // canonical NT book name, e.g. "Matthew"
    val bookSlug: String,                 // e.g. "matthew", "1-corinthians"
    val chapter: Int,
    val fileName: String,                 // e.g. "matthew-001.m4a"
    val sizeBytes: Long,
    val sha256: String,
    val durationMs: Long = 0L,
    val downloadedAt: Long = System.currentTimeMillis(),
)

@Dao
interface AudioChapterDao {
    @Query("SELECT * FROM downloaded_audio_chapters WHERE `key` = :key")
    suspend fun chapter(key: String): DownloadedAudioChapter?

    @Query("SELECT * FROM downloaded_audio_chapters WHERE versionCode = :versionCode ORDER BY book, chapter")
    suspend fun forVersion(versionCode: String): List<DownloadedAudioChapter>

    @Query("SELECT * FROM downloaded_audio_chapters")
    suspend fun all(): List<DownloadedAudioChapter>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(row: DownloadedAudioChapter)

    @Query("DELETE FROM downloaded_audio_chapters WHERE `key` = :key")
    suspend fun deleteChapter(key: String)

    @Query("DELETE FROM downloaded_audio_chapters WHERE versionCode = :versionCode")
    suspend fun deleteVersion(versionCode: String)

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM downloaded_audio_chapters WHERE versionCode = :versionCode")
    suspend fun sizeBytesForVersion(versionCode: String): Long

    @Query("SELECT COUNT(*) FROM downloaded_audio_chapters WHERE versionCode = :versionCode")
    suspend fun countForVersion(versionCode: String): Int

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM downloaded_audio_chapters")
    suspend fun totalSizeBytes(): Long

    @Query("DELETE FROM downloaded_audio_chapters")
    suspend fun clearAll()
}

@Database(
    entities = [DownloadedAudioChapter::class],
    version = 1,
    exportSchema = false,
)
abstract class AudioDatabase : RoomDatabase() {
    abstract fun audioChapterDao(): AudioChapterDao

    companion object {
        @Volatile private var instance: AudioDatabase? = null

        fun get(context: Context): AudioDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AudioDatabase::class.java,
                    "forgehouse50_audio.db",
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
