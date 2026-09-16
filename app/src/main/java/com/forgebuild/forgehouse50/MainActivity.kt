package com.forgebuild.forgehouse50

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.forgebuild.forgehouse50.data.Repository
import com.forgebuild.forgehouse50.data.SessionStore
import com.forgebuild.forgehouse50.ui.AppNavHost
import com.forgebuild.forgehouse50.ui.ForgeHouseTheme
import com.forgebuild.forgehouse50.work.ReminderWorker
import com.forgebuild.forgehouse50.work.KeepAliveService
import android.os.PowerManager
import android.provider.Settings

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = Repository(applicationContext, SessionStore(applicationContext))
        setContent {
            ForgeHouseTheme {
                App(repo)
            }
        }
    }
}

@Composable
private fun App(repo: Repository) {
    val context = LocalContext.current

    var loggedIn by remember { mutableStateOf(repo.session.isLoggedIn) }
    var authenticatedTick by remember { mutableStateOf(0) }   // force full reload on auth change

    // leaderboard_audio_removal_offline_bible_v1 / ISSUE 3: the Media3 audio
    // player and its lifecycle-bound controller are gone (audio removed
    // upstream). The daily-reminder KeepAliveService below is a SEPARATE,
    // still-needed foreground service and is untouched.

    // POST_NOTIFICATIONS (API 33+) for the daily reminder; fine if denied.
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(loggedIn, authenticatedTick) {
        if (loggedIn) {
            if (Build.VERSION.SDK_INT >= 33) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            ReminderWorker.schedule(context)
            KeepAliveService.start(context)
        } else {
            ReminderWorker.cancel(context)
            KeepAliveService.stop(context)
        }
    }

    // combined_fixes_v1 Issue 6: one-time explanation + one-time battery-exemption prompt.
    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }
    var showKeepAliveInfo by remember { mutableStateOf(false) }
    LaunchedEffect(loggedIn) {
        if (loggedIn && !repo.session.keepAliveExplained) {
            repo.session.keepAliveExplained = true
            showKeepAliveInfo = true
        }
    }

    AppNavHost(
        repo = repo,
        loggedIn = loggedIn,
        authenticatedTick = authenticatedTick,
        onAuthChanged = { nowLoggedIn ->
            loggedIn = nowLoggedIn
            authenticatedTick++
        },
    )

    if (showKeepAliveInfo) {
        AlertDialog(
            onDismissRequest = { showKeepAliveInfo = false },
            title = { Text("Daily reminders stay reliable") },
            text = {
                Text(
                    "ForgeHouse 50 keeps a silent, low-priority notification running so your daily reading reminder still arrives even if Android puts the app to sleep. It makes no sound and never interrupts you — tapping it simply opens the app.\n\nFor the best reliability, allow ForgeHouse 50 to ignore battery optimization when asked next."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showKeepAliveInfo = false
                    if (Build.VERSION.SDK_INT >= 23) {
                        val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
                        if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
                            val intent = android.content.Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                android.net.Uri.parse("package:" + context.packageName),
                            )
                            runCatching { batteryLauncher.launch(intent) }
                        }
                    }
                }) { Text("Got it") }
            },
        )
    }
}
