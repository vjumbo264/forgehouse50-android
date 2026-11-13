package com.forgebuild.forgehouse50

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.forgebuild.forgehouse50.data.Repository
import com.forgebuild.forgehouse50.data.SessionStore
import com.forgebuild.forgehouse50.ui.AppNavHost
import com.forgebuild.forgehouse50.ui.ForgeHouseTheme
import com.forgebuild.forgehouse50.update.UpdateManager
import com.forgebuild.forgehouse50.update.UpdateWorker
import com.forgebuild.forgehouse50.widget.WidgetRefreshWorker
import com.forgebuild.forgehouse50.work.ChatNotificationLogic
import com.forgebuild.forgehouse50.work.ChatNotificationManager
import com.forgebuild.forgehouse50.work.KeepAliveService
import com.forgebuild.forgehouse50.work.QuoteWorker
import com.forgebuild.forgehouse50.work.ReminderWorker

class MainActivity : ComponentActivity() {
    private val deepLinkState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = Repository(applicationContext, SessionStore(applicationContext))
        ChatNotificationManager.ensureAllChannels(applicationContext)
        deepLinkState.value = intent?.data?.toString()
        setContent {
            ForgeHouseTheme {
                App(
                    repo = repo,
                    deepLink = deepLinkState.value,
                    onConsumeDeepLink = { deepLinkState.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.toString()?.let { uri ->
            deepLinkState.value = uri
        }
    }

    override fun onResume() {
        super.onResume()
        WidgetRefreshWorker.refreshNow(applicationContext)
        UpdateManager.onResume(applicationContext)
    }
}

@Composable
private fun App(
    repo: Repository,
    deepLink: String?,
    onConsumeDeepLink: () -> Unit,
) {
    val context = LocalContext.current
    var loggedIn by remember { mutableStateOf(repo.session.isLoggedIn) }
    var authenticatedTick by remember { mutableStateOf(0) }

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    var showNotificationRationale by remember { mutableStateOf(false) }

    LaunchedEffect(loggedIn, authenticatedTick) {
        if (loggedIn) {
            if (Build.VERSION.SDK_INT >= 33) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted && !repo.session.keepAliveExplained) {
                    showNotificationRationale = true
                }
            }
            ReminderWorker.schedule(context)
            if (repo.session.hourlyQuotesEnabled) QuoteWorker.schedule(context) else QuoteWorker.cancel(context)
            KeepAliveService.start(context)
            WidgetRefreshWorker.schedule(context)
            UpdateWorker.schedule(context)
        } else {
            ChatNotificationManager.cancelAll(context)
            ReminderWorker.cancel(context)
            QuoteWorker.cancel(context)
            KeepAliveService.stop(context)
            WidgetRefreshWorker.cancel(context)
            UpdateWorker.cancel(context)
        }
    }

    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    var showKeepAliveInfo by remember { mutableStateOf(false) }
    var showOemTip by remember { mutableStateOf(false) }

    LaunchedEffect(loggedIn, showNotificationRationale) {
        if (loggedIn && !showNotificationRationale && !repo.session.keepAliveExplained) {
            repo.session.keepAliveExplained = true
            showKeepAliveInfo = true
        }
    }

    LaunchedEffect(loggedIn, showNotificationRationale, showKeepAliveInfo) {
        if (
            loggedIn &&
            !showNotificationRationale &&
            !showKeepAliveInfo &&
            !repo.session.oemBackgroundTipDismissed &&
            ChatNotificationLogic.isAggressiveBackgroundOem(Build.MANUFACTURER, Build.BRAND)
        ) {
            showOemTip = true
        }
    }

    AppNavHost(
        repo = repo,
        loggedIn = loggedIn,
        authenticatedTick = authenticatedTick,
        deepLink = deepLink,
        onConsumeDeepLink = onConsumeDeepLink,
        onAuthChanged = { nowLoggedIn ->
            loggedIn = nowLoggedIn
            authenticatedTick++
        },
    )

    if (showNotificationRationale) {
        AlertDialog(
            onDismissRequest = {
                showNotificationRationale = false
            },
            title = { Text("Turn on notifications") },
            text = {
                Text(
                    "Allow ForgeHouse 50 notifications so you receive community messages, private replies from pastors & leaders, and your daily reading reminders right on your phone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNotificationRationale = false
                        if (Build.VERSION.SDK_INT >= 33) {
                            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                ) { Text("Allow notifications") }
            },
            dismissButton = {
                TextButton(onClick = { showNotificationRationale = false }) {
                    Text("Not now")
                }
            },
        )
    }

    if (showKeepAliveInfo) {
        AlertDialog(
            onDismissRequest = { showKeepAliveInfo = false },
            title = { Text("Reliable messages & reminders") },
            text = {
                Text(
                    "ForgeHouse 50 keeps a silent, low-priority connection active so chat messages and daily reading reminders arrive even when your phone sleeps. It makes no sound and never interrupts you.\n\nFor best reliability, allow ForgeHouse 50 to ignore battery optimization when asked next."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showKeepAliveInfo = false
                    if (Build.VERSION.SDK_INT >= 23) {
                        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                        if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
                            val intent = Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:" + context.packageName),
                            )
                            runCatching { batteryLauncher.launch(intent) }
                        }
                    }
                }) { Text("Got it") }
            },
        )
    }

    if (showOemTip) {
        val brandName = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }.ifBlank { "Your phone" }
        AlertDialog(
            onDismissRequest = {
                repo.session.oemBackgroundTipDismissed = true
                showOemTip = false
            },
            title = { Text("Keep chat notifications working") },
            text = {
                Text(
                    "$brandName phones sometimes pause background apps to save battery. If messages arrive late when the app is closed, open App Settings -> Battery / Autostart and allow ForgeHouse 50 to run in the background."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    repo.session.oemBackgroundTipDismissed = true
                    showOemTip = false
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}"),
                    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    runCatching { context.startActivity(intent) }
                }) { Text("Open app settings") }
            },
            dismissButton = {
                TextButton(onClick = {
                    repo.session.oemBackgroundTipDismissed = true
                    showOemTip = false
                }) { Text("Dismiss") }
            },
        )
    }
}
