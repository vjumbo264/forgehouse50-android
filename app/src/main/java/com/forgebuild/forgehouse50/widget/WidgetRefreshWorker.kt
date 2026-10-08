package com.forgebuild.forgehouse50.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.forgebuild.forgehouse50.data.AppCache
import com.forgebuild.forgehouse50.data.ReadingSession
import com.forgebuild.forgehouse50.data.Repository
import com.forgebuild.forgehouse50.data.SessionStore
import java.util.concurrent.TimeUnit

/**
 * Periodic + opportunistic widget state refresh. Uses the SAME [ReadingSession]
 * engine as the app so the home-screen widget and the app never disagree about
 * which day(s) are due.
 *
 * Cache-first guarantee (Issue 1): fails soft. If the network is unavailable
 * or slow, falls back to [AppCache] and existing [WidgetStateStore] data.
 * Never wipes or zeroes out widget state on failure.
 */
class WidgetRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repo = Repository(applicationContext, SessionStore(applicationContext))
        refreshWidgetState(applicationContext, repo)
        ForgeHouseWidgetProvider.updateAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "fh50_widget_refresh"

        /** 30-minute periodic refresh so day/progress data never goes stale. */
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
        }

        /** One-shot refresh (widget placed, app foreground, day completed). */
        fun refreshNow(context: Context) {
            WorkManager.getInstance(context)
                .enqueue(OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build())
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        /** Recompute widget state from the live API + shared catch-up engine with cache fallback. */
        suspend fun refreshWidgetState(context: Context, repo: Repository) {
            val loggedIn = repo.session.isLoggedIn
            if (!loggedIn) {
                WidgetStateStore(context).write(WidgetStateStore.State(loggedIn = false))
                return
            }

            // Always try live API first, fallback to AppCache. NEVER wipe state on failure!
            val progress = runCatching { repo.api.progress() }.getOrNull()?.also {
                AppCache.saveProgress(context, it)
            } ?: AppCache.getProgress(context)

            val today = runCatching { repo.api.today() }.getOrNull()?.also {
                AppCache.saveToday(context, it)
            } ?: AppCache.getToday(context)

            val me = runCatching { repo.api.me() }.getOrNull()?.also {
                AppCache.saveMe(context, it)
                repo.session.userId = it.id
                repo.session.userName = it.name
                repo.session.userRole = it.role
            } ?: AppCache.getMe(context)

            if (today != null) {
                val plan = ReadingSession.resolve(
                    progress = progress,
                    today = today,
                    meEndDate = me?.programme?.programme_end_date,
                )
                val d = plan.days.firstOrNull() ?: today.today?.day_number ?: 0
                var assignment = ""
                var verse = ""
                var started = false
                var completed = false
                if (d > 0) {
                    val dayResp = runCatching { repo.api.day(d) }.getOrNull()?.also {
                        AppCache.saveDay(context, d, it)
                    } ?: AppCache.getDay(context, d)

                    completed = dayResp?.progress?.completed ?: false
                    started = (dayResp?.progress?.reading_seconds ?: 0) > 0 && !completed
                    assignment = dayResp?.assignments?.joinToString(", ") {
                        if (it.chapter_start == it.chapter_end) "${it.book} ${it.chapter_start}"
                        else "${it.book} ${it.chapter_start}–${it.chapter_end}"
                    } ?: ""
                    dayResp?.assignments?.firstOrNull()?.let { a ->
                        verse = runCatching {
                            repo.getPassage(
                                a.book, a.chapter_start, a.chapter_start,
                                repo.session.translationId ?: Repository.KJV_ID
                            )
                        }.getOrNull()?.verses?.firstOrNull()?.text.orEmpty()
                    }
                }
                val newState = WidgetStateStore.State(
                    day = d,
                    totalDays = today.programme.total_days,
                    assignment = assignment,
                    verse = verse.take(220),
                    chaptersDone = progress?.totals?.chapters_completed ?: 0,
                    chaptersTotal = today.programme.total_chapters,
                    started = started,
                    completed = completed,
                    catchup = plan.catchup,
                    loggedIn = true,
                )
                WidgetStateStore(context).write(newState)
            }
        }
    }
}
