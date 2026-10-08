package com.forgebuild.forgehouse50.data

import android.content.Context
import android.content.SharedPreferences
import com.forgebuild.forgehouse50.ui.AppJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * Synchronous, offline-first cache store for ForgeHouse 50 application state.
 *
 * All screens read from here synchronously in remember { mutableStateOf(AppCache.get...(context)) }
 * before first frame composition, guaranteeing zero flicker, zero placeholder/zero flash,
 * and immediate real data rendering even in airplane mode or with no network connection.
 */
object AppCache {
    private const val PREFS_NAME = "fh50_cache"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getToday(context: Context): TodayResponse? =
        prefs(context).getString("today", null)?.let {
            runCatching { AppJson.decodeFromString<TodayResponse>(it) }.getOrNull()
        }

    fun saveToday(context: Context, today: TodayResponse) {
        val json = runCatching { AppJson.encodeToString(TodayResponse.serializer(), today) }.getOrNull() ?: return
        prefs(context).edit().putString("today", json).apply()
    }

    fun getMe(context: Context): MeResponse? =
        prefs(context).getString("me", null)?.let {
            runCatching { AppJson.decodeFromString<MeResponse>(it) }.getOrNull()
        }

    fun saveMe(context: Context, me: MeResponse) {
        val json = runCatching { AppJson.encodeToString(MeResponse.serializer(), me) }.getOrNull() ?: return
        prefs(context).edit().putString("me", json).apply()
    }

    fun getProgress(context: Context): ProgressResponse? =
        prefs(context).getString("progress", null)?.let {
            runCatching { AppJson.decodeFromString<ProgressResponse>(it) }.getOrNull()
        }

    fun saveProgress(context: Context, progress: ProgressResponse) {
        val json = runCatching { AppJson.encodeToString(ProgressResponse.serializer(), progress) }.getOrNull() ?: return
        prefs(context).edit().putString("progress", json).apply()
    }

    fun getLeaderboard(context: Context, category: String): LeaderboardResponse? =
        prefs(context).getString("lb2_$category", null)?.let {
            runCatching { AppJson.decodeFromString<LeaderboardResponse>(it) }.getOrNull()
        }

    fun saveLeaderboard(context: Context, category: String, data: LeaderboardResponse) {
        val json = runCatching { AppJson.encodeToString(LeaderboardResponse.serializer(), data) }.getOrNull() ?: return
        prefs(context).edit().putString("lb2_$category", json).apply()
    }

    fun getNotes(context: Context): NotesResponse? =
        prefs(context).getString("notes", null)?.let {
            runCatching { AppJson.decodeFromString<NotesResponse>(it) }.getOrNull()
        }

    fun saveNotes(context: Context, notes: NotesResponse) {
        val json = runCatching { AppJson.encodeToString(NotesResponse.serializer(), notes) }.getOrNull() ?: return
        prefs(context).edit().putString("notes", json).apply()
    }

    fun getDay(context: Context, day: Int): DayResponse? =
        prefs(context).getString("day_$day", null)?.let {
            runCatching { AppJson.decodeFromString<DayResponse>(it) }.getOrNull()
        }

    fun saveDay(context: Context, day: Int, data: DayResponse) {
        val json = runCatching { AppJson.encodeToString(DayResponse.serializer(), data) }.getOrNull() ?: return
        prefs(context).edit().putString("day_$day", json).apply()
    }

    fun getQuiz(context: Context, day: Int): QuizResponse? =
        prefs(context).getString("quiz_$day", null)?.let {
            runCatching { AppJson.decodeFromString<QuizResponse>(it) }.getOrNull()
        }

    fun saveQuiz(context: Context, day: Int, data: QuizResponse) {
        val json = runCatching { AppJson.encodeToString(QuizResponse.serializer(), data) }.getOrNull() ?: return
        prefs(context).edit().putString("quiz_$day", json).apply()
    }

    fun isQuizDone(context: Context, day: Int): Boolean? =
        if (prefs(context).contains("quiz_done_$day")) prefs(context).getBoolean("quiz_done_$day", false) else null

    fun setQuizDone(context: Context, day: Int, done: Boolean) {
        prefs(context).edit().putBoolean("quiz_done_$day", done).apply()
    }

    
    fun getAdminStats(context: Context): AdminStats? =
        prefs(context).getString("admin_stats", null)?.let {
            runCatching { AppJson.decodeFromString<AdminStats>(it) }.getOrNull()
        }

    fun saveAdminStats(context: Context, stats: AdminStats) {
        val json = runCatching { AppJson.encodeToString(AdminStats.serializer(), stats) }.getOrNull() ?: return
        prefs(context).edit().putString("admin_stats", json).apply()
    }

    fun getAdminProgramme(context: Context): AdminProgramme? =
        prefs(context).getString("admin_programme", null)?.let {
            runCatching { AppJson.decodeFromString<AdminProgramme>(it) }.getOrNull()
        }

    fun saveAdminProgramme(context: Context, programme: AdminProgramme) {
        val json = runCatching { AppJson.encodeToString(AdminProgramme.serializer(), programme) }.getOrNull() ?: return
        prefs(context).edit().putString("admin_programme", json).apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
