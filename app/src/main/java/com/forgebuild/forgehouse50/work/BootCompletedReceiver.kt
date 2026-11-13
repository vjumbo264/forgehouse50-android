package com.forgebuild.forgehouse50.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.forgebuild.forgehouse50.data.SessionStore
import com.forgebuild.forgehouse50.update.UpdateWorker
import com.forgebuild.forgehouse50.widget.WidgetRefreshWorker

/**
 * Restores KeepAliveService and periodic WorkManager jobs after device reboot or app update (k7).
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        val session = SessionStore(context)
        if (!session.isLoggedIn) return
        KeepAliveService.start(context)
        ReminderWorker.schedule(context)
        WidgetRefreshWorker.schedule(context)
        UpdateWorker.schedule(context)
    }
}
