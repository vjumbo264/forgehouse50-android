package com.forgebuild.forgehouse50.work

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.forgebuild.forgehouse50.R
import com.forgebuild.forgehouse50.data.ChatSendResponse
import com.forgebuild.forgehouse50.data.ChatSyncResponse
import com.forgebuild.forgehouse50.data.PendingChatOutgoing
import com.forgebuild.forgehouse50.ui.community.ActiveChatForegroundTracker
import com.forgebuild.forgehouse50.ui.community.PendingChatStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Single persistent background foreground service (foregroundServiceType="dataSync").
 * Keeps daily reading reminders active AND runs the tuned cursor-based background chat
 * sync loop (k7: 5m day / 15m night Africa/Lagos / 30s active burst) with zero Firebase.
 */
class KeepAliveService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        ChatNotificationManager.ensureAllChannels(this)
        val launch = packageManager.getLaunchIntentForPackage(packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP) }
        val pi = PendingIntent.getActivity(
            this,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("ForgeHouse 50")
            .setContentText("Connected for chat messages and reading reminders")
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val repo = RepositoryProvider.get(applicationContext)
        if (!repo.session.isLoggedIn) {
            ChatNotificationManager.cancelAll(applicationContext)
            stopSelf()
            return START_NOT_STICKY
        }

        startBackgroundLoopIfNeeded()
        return START_STICKY
    }

    private fun startBackgroundLoopIfNeeded() {
        if (pollJob?.isActive == true) return
        pollJob = serviceScope.launch {
            val ctx = applicationContext
            val repo = RepositoryProvider.get(ctx)
            val gateway = object : ChatNotificationLogic.BackgroundChatGateway {
                override suspend fun baselineCursor(): ChatSyncResponse =
                    repo.api.chatSync(unreadOnly = true)

                override suspend fun syncBackground(
                    groupSince: String?,
                    directSince: String?,
                ): ChatSyncResponse = repo.api.chatSync(
                    groupSince = groupSince,
                    directSince = directSince,
                    background = true,
                )

                override suspend fun sendPending(item: PendingChatOutgoing): ChatSendResponse {
                    return if (item.channel_type == "group") {
                        repo.api.sendGroupMessage(
                            text = item.text,
                            replyToId = item.reply_to_id,
                            clientMsgId = item.client_msg_id,
                        )
                    } else {
                        repo.api.sendDirectMessage(
                            text = item.text,
                            targetUserId = if (repo.session.isAdmin) item.thread_id else null,
                            replyToId = item.reply_to_id,
                            clientMsgId = item.client_msg_id,
                        )
                    }
                }
            }

            val storage = object : ChatNotificationLogic.BackgroundChatStorage {
                override var isLoggedIn: Boolean
                    get() = repo.session.isLoggedIn
                    set(_) {}
                override var userId: String?
                    get() = repo.session.userId
                    set(_) {}
                override var isAdmin: Boolean
                    get() = repo.session.isAdmin
                    set(_) {}
                override var bgGroupCursor: String?
                    get() = repo.session.bgChatGroupCursor
                    set(value) { repo.session.bgChatGroupCursor = value }
                override var bgDirectCursor: String?
                    get() = repo.session.bgChatDirectCursor
                    set(value) { repo.session.bgChatDirectCursor = value }
                override var bgBurstUntilMs: Long
                    get() = repo.session.bgChatBurstUntilMs
                    set(value) { repo.session.bgChatBurstUntilMs = value }
                override var muteGroupNotifications: Boolean
                    get() = repo.session.muteGroupChatNotifications
                    set(value) { repo.session.muteGroupChatNotifications = value }

                override fun loadPendingOutgoing(): List<PendingChatOutgoing> =
                    PendingChatStore.loadAll(repo.session)

                override fun removePendingOutgoing(clientMsgId: String) {
                    PendingChatStore.remove(repo.session, clientMsgId)
                }

                override fun clearChatBackgroundState() {
                    repo.session.bgChatGroupCursor = null
                    repo.session.bgChatDirectCursor = null
                    repo.session.bgChatBurstUntilMs = 0L
                }
            }

            while (isActive) {
                val outcome = try {
                    ChatNotificationLogic.runBackgroundCycle(
                        gateway = gateway,
                        storage = storage,
                        activeForegroundChannel = ActiveChatForegroundTracker.activeChannel,
                        activeForegroundThreadId = ActiveChatForegroundTracker.activeThreadId,
                        nowMs = System.currentTimeMillis(),
                        onNotify = { filtered -> ChatNotificationManager.applyFilteredUpdates(ctx, filtered) },
                        onClearAll = { ChatNotificationManager.cancelAll(ctx) },
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    ChatNotificationLogic.BackgroundCycleOutcome(
                        nextDelayMs = ChatNotificationLogic.nextBackgroundIntervalMs(
                            nowMs = System.currentTimeMillis(),
                            burstUntilMs = repo.session.bgChatBurstUntilMs,
                        ),
                        hadNetworkError = true,
                    )
                }

                if (outcome.stoppedLoggedOut) {
                    stopSelf()
                    break
                }
                delay(outcome.nextDelayMs.coerceAtLeast(10_000L))
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val repo = RepositoryProvider.get(applicationContext)
        if (repo.session.isLoggedIn) {
            val restartIntent = Intent(applicationContext, ChatNotificationActionReceiver::class.java).apply {
                action = ChatNotificationLogic.ACTION_RESTART_KEEPALIVE
            }
            val pi = PendingIntent.getBroadcast(
                applicationContext,
                9101,
                restartIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE,
            )
            val am = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            am?.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 2_000L, pi)
        }
    }

    override fun onDestroy() {
        pollJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun ensureChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Background service",
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    companion object {
        private const val CHANNEL_ID = "keep_alive"
        private const val NOTIFICATION_ID = 4200
        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, KeepAliveService::class.java)) }
        }
        fun stop(context: Context) {
            context.stopService(Intent(context, KeepAliveService::class.java))
        }
    }
}
