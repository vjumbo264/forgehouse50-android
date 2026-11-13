package com.forgebuild.forgehouse50.work

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.forgebuild.forgehouse50.MainActivity
import com.forgebuild.forgehouse50.R
import com.forgebuild.forgehouse50.data.SessionStore
import java.util.concurrent.TimeUnit

/**
 * Hourly encouraging spiritual quote notifications during waking hours (k8).
 * Quiet overnight (22:00-06:59 Africa/Lagos), respects Profile toggle & logout.
 */
class QuoteWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val session = SessionStore(applicationContext)
        if (!QuoteLibrary.shouldShowQuoteNotification(
                isLoggedIn = session.isLoggedIn,
                enabled = session.hourlyQuotesEnabled,
                nowMs = System.currentTimeMillis(),
            )
        ) {
            return Result.success()
        }

        val nextIdx = QuoteLibrary.nextQuoteIndex(session.lastQuoteIndex)
        session.lastQuoteIndex = nextIdx
        val quote = QuoteLibrary.QUOTES[nextIdx]

        ChatNotificationManager.ensureAllChannels(applicationContext)
        val tapIntent = Intent(applicationContext, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("fh50://home")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            applicationContext,
            NOTIFICATION_ID,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, ChatNotificationLogic.CHANNEL_REMINDERS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(quote.reference)
            .setContentText(quote.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(quote.text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { nm.notify(NOTIFICATION_ID, notification) }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "fh50_hourly_quotes"
        private const val NOTIFICATION_ID = 4202

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<QuoteWorker>(1, TimeUnit.HOURS)
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            runCatching { nm.cancel(NOTIFICATION_ID) }
        }
    }
}
