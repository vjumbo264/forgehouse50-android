package com.forgebuild.forgehouse50.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.forgebuild.forgehouse50.R

/**
 * Android home-screen App Widget.
 *
 * Cache-first guarantee (Issue 1): renders instantly from [WidgetStateStore]
 * snapshot on the very first layout frame without any blocking network or disk wait.
 *
 * Visual redesign (Issue 3): Clean Material 3 widget layout with 28dp corners,
 * clear top day pill badge, bold assignment title, key verse snippet, and rounded
 * action bar.
 */
class ForgeHouseWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) updateOne(context, mgr, id)
        // Opportunistic background refresh so placing or updating stays fresh
        WidgetRefreshWorker.refreshNow(context)
    }

    companion object {
        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context) ?: return
            val ids = mgr.getAppWidgetIds(
                android.content.ComponentName(context, ForgeHouseWidgetProvider::class.java)
            )
            for (id in ids) updateOne(context, mgr, id)
        }

        private fun deepLink(context: Context, uri: String): PendingIntent {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                setClass(context, com.forgebuild.forgehouse50.MainActivity::class.java)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            return PendingIntent.getActivity(
                context, uri.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        fun updateOne(context: Context, mgr: AppWidgetManager, id: Int) {
            val s = WidgetStateStore(context).read()
            val v = RemoteViews(context.packageName, R.layout.forgehouse_widget)

            v.setTextViewText(R.id.w_title, "ForgeHouse 50")
            v.setTextViewText(
                R.id.w_day,
                when {
                    !s.loggedIn -> "Sign In"
                    s.day > 0 -> "Day ${s.day}/${s.totalDays}" + if (s.catchup) " · catch-up" else ""
                    else -> "Rest day"
                },
            )

            v.setTextViewText(
                R.id.w_assignment,
                if (s.assignment.isNotBlank()) s.assignment else "No reading scheduled today"
            )

            v.setTextViewText(
                R.id.w_verse,
                if (s.verse.isNotBlank()) "\u201C${s.verse}\u201D" else ""
            )

            // Overall completion progress
            val pct = if (s.chaptersTotal > 0)
                ((s.chaptersDone * 100) / s.chaptersTotal).coerceIn(0, 100) else 0
            v.setProgressBar(R.id.w_progress, 100, pct, false)
            v.setTextViewText(R.id.w_progress_text, "${s.chaptersDone}/${s.chaptersTotal} chapters ($pct%)")

            // Top container click opens app
            val mainIntent = if (s.day > 0) "fh50://read/${s.day}" else "fh50://home"
            v.setOnClickPendingIntent(R.id.w_root, deepLink(context, mainIntent))

            when {
                !s.loggedIn || s.day <= 0 -> {
                    v.setTextViewText(R.id.w_primary, "Open ForgeHouse 50")
                    v.setOnClickPendingIntent(R.id.w_primary, deepLink(context, "fh50://home"))
                    v.setViewVisibility(R.id.w_secondary, android.view.View.GONE)
                }
                s.completed -> {
                    v.setTextViewText(R.id.w_primary, "Completed")
                    v.setOnClickPendingIntent(R.id.w_primary, deepLink(context, "fh50://read/${s.day}"))
                    v.setViewVisibility(R.id.w_secondary, android.view.View.VISIBLE)
                    v.setTextViewText(R.id.w_secondary, "Reflect")
                    v.setOnClickPendingIntent(R.id.w_secondary, deepLink(context, "fh50://notes?day=${s.day}"))
                }
                s.started -> {
                    v.setTextViewText(R.id.w_primary, "Continue")
                    v.setOnClickPendingIntent(R.id.w_primary, deepLink(context, "fh50://read/${s.day}"))
                    v.setViewVisibility(R.id.w_secondary, android.view.View.GONE)
                }
                else -> {
                    v.setTextViewText(R.id.w_primary, "Start Reading")
                    v.setOnClickPendingIntent(R.id.w_primary, deepLink(context, "fh50://read/${s.day}"))
                    v.setViewVisibility(R.id.w_secondary, android.view.View.GONE)
                }
            }

            mgr.updateAppWidget(id, v)
        }
    }
}

/** Persistent widget snapshot so the provider renders instantly without I/O. */
class WidgetStateStore(context: Context) {
    private val prefs = context.getSharedPreferences("fh50_widget", Context.MODE_PRIVATE)

    data class State(
        val day: Int = 0,
        val totalDays: Int = 50,
        val assignment: String = "",
        val verse: String = "",
        val chaptersDone: Int = 0,
        val chaptersTotal: Int = 260,
        val started: Boolean = false,
        val completed: Boolean = false,
        val catchup: Boolean = false,
        val loggedIn: Boolean = true,
    )

    fun write(s: State) = prefs.edit()
        .putInt("day", s.day).putInt("totalDays", s.totalDays)
        .putString("assignment", s.assignment).putString("verse", s.verse)
        .putInt("chaptersDone", s.chaptersDone).putInt("chaptersTotal", s.chaptersTotal)
        .putBoolean("started", s.started).putBoolean("completed", s.completed)
        .putBoolean("catchup", s.catchup).putBoolean("loggedIn", s.loggedIn)
        .apply()

    fun read(): State = State(
        day = prefs.getInt("day", 0),
        totalDays = prefs.getInt("totalDays", 50),
        assignment = prefs.getString("assignment", "") ?: "",
        verse = prefs.getString("verse", "") ?: "",
        chaptersDone = prefs.getInt("chaptersDone", 0),
        chaptersTotal = prefs.getInt("chaptersTotal", 260),
        started = prefs.getBoolean("started", false),
        completed = prefs.getBoolean("completed", false),
        catchup = prefs.getBoolean("catchup", false),
        loggedIn = prefs.getBoolean("loggedIn", true),
    )
}
