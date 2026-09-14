package com.forgebuild.forgehouse50.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.forgebuild.forgehouse50.data.Repository
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Daily reading reminder.
 *
 * A single WorkManager periodic job fires once per day at a fixed local time
 * (18:00). At DELIVERY time it checks the live API: if the user has already
 * completed today's reading day AND taken (or has no) quiz attempt, no
 * notification is shown — we never nudge someone who is already done.
 *
 * Scheduled after login / on app start when logged in; cancelled on logout.
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = RepositoryProvider.get(applicationContext)
        if (!repo.session.isLoggedIn) return Result.success()

        val today = runCatching { repo.api.today() }.getOrNull() ?: return Result.retry()
        val block = today.today ?: return Result.success()        // not a reading day
        if (block.completed.not()) { notify(today.today.day_number, block.assignment?.summary ?: ""); return Result.success() }

        // Reading done — suppress only if the quiz is also done (or none to take).
        val quiz = runCatching { repo.api.quiz(block.day_number) }.getOrNull()
        val quizDone = quiz?.attempt != null || quiz?.can_attempt == false
        if (!quizDone) notify(block.day_number, block.assignment?.summary ?: "")
        return Result.success()
    }

    private fun notify(day: Int, summary: String) {
        ensureChannel()
        val launch = applicationContext.packageManager
            .getLaunchIntentForPackage(applicationContext.packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP) }
        val pi = PendingIntent.getActivity(
            applicationContext, 0, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (summary.isNotBlank())
            "Day $day is waiting: $summary"
        else
            "Day $day of your reading is waiting."
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(applicationContext.applicationInfo.icon)
            .setContentTitle("Today's reading")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel() {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Daily reading reminder", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    companion object {
        private const val WORK_NAME = "fh50_daily_reading_reminder"
        private const val CHANNEL_ID = "daily_reading"
        private const val NOTIFICATION_ID = 4201
        private const val REMINDER_HOUR = 18   // 6pm local, every day

        /** Schedule (idempotently) the once-daily reminder at 18:00 local. */
        fun schedule(context: Context) {
            val now = Calendar.getInstance()
            val next = (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, REMINDER_HOUR)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
            }
            val initialDelayMs = next.timeInMillis - now.timeInMillis
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}

/** Late-bound repository accessor for the worker (avoids DI framework). */
object RepositoryProvider {
    @Volatile private var repo: Repository? = null
    fun get(context: Context): Repository =
        repo ?: synchronized(this) {
            repo ?: Repository(context.applicationContext, com.forgebuild.forgehouse50.data.SessionStore(context.applicationContext)).also { repo = it }
        }
}
