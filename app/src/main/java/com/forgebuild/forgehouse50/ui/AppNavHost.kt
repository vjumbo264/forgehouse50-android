package com.forgebuild.forgehouse50.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.forgebuild.engine.security.ScreenSecurity
import com.forgebuild.forgehouse50.data.Repository
import com.forgebuild.forgehouse50.ui.admin.AdminScreen
import com.forgebuild.forgehouse50.ui.home.HomeScreen
import com.forgebuild.forgehouse50.ui.leaderboard.LeaderboardScreen
import com.forgebuild.forgehouse50.ui.notes.NoteEditScreen
import com.forgebuild.forgehouse50.ui.notes.NotesScreen
import com.forgebuild.forgehouse50.ui.profile.ProfileScreen
import com.forgebuild.forgehouse50.ui.progress.ProgressScreen
import com.forgebuild.forgehouse50.ui.quiz.QuizScreen
import com.forgebuild.forgehouse50.ui.read.ReadScreen
import com.forgebuild.forgehouse50.ui.read.TranslationsScreen
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.forgebuild.forgehouse50.ui.changelog.ChangelogScreen
import com.forgebuild.forgehouse50.update.DownloadResult
import com.forgebuild.forgehouse50.update.PendingUpdate
import com.forgebuild.forgehouse50.update.UpdateChecker
import com.forgebuild.forgehouse50.ui.ExpressiveLoading

object Routes {
    const val HOME = "home"
    const val LEADERBOARD = "leaderboard"
    const val PROGRESS = "progress"
    const val PROFILE = "profile"
    const val READ = "read/{day}"
    const val READ_SESSION = "readsession/{days}"
    const val QUIZ = "quiz/{day}"
    const val NOTES = "notes"
    const val NOTE_EDIT = "note_edit?noteId={noteId}&day={day}&book={book}&chapter={chapter}&verse={verse}&type={type}"
    const val ADMIN = "admin"
    const val TRANSLATIONS = "translations"
    const val CHANGELOG = "changelog"
    fun read(day: Int) = "read/$day"
    fun readSession(days: List<Int>) = "readsession/${days.joinToString(",")}"
    fun quiz(day: Int) = "quiz/$day"
    fun noteEdit(
        noteId: String? = null,
        day: Int? = null,
        book: String? = null,
        chapter: Int? = null,
        verse: String? = null,
        type: String? = null
    ): String {
        val b = android.net.Uri.encode(book ?: "")
        val v = android.net.Uri.encode(verse ?: "")
        val t = android.net.Uri.encode(type ?: "")
        return "note_edit?noteId=${noteId ?: ""}&day=${day ?: 0}&book=$b&chapter=${chapter ?: 0}&verse=$v&type=$t"
    }
}

private data class Tab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
)

private val Tabs = listOf(
    Tab(Routes.HOME, "Today", Icons.Filled.Home, Icons.Outlined.Home),
    Tab(Routes.LEADERBOARD, "Leaderboard", Icons.Filled.EmojiEvents, Icons.Outlined.EmojiEvents),
    Tab(Routes.PROGRESS, "Progress", Icons.Filled.TrendingUp, Icons.Outlined.TrendingUp),
    Tab(Routes.PROFILE, "Profile", Icons.Filled.Person, Icons.Outlined.Person),
)

@Composable
fun AppNavHost(
    repo: Repository,
    loggedIn: Boolean,
    authenticatedTick: Int,
    deepLink: String? = null,
    onAuthChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val nav: NavHostController = rememberNavController()
    var registrationOpen by remember { mutableStateOf(true) }
    var signupLabel by remember { mutableStateOf("Joining closes on 21 Oct 2026") }
    var configLoaded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var pendingUpdate by remember { mutableStateOf<PendingUpdate?>(null) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var updateErrorDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    var updateErrorApkUrl by remember { mutableStateOf<String?>(null) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var downloadedApkToInstall by remember { mutableStateOf<java.io.File?>(null) }

    // Public config (join window state) — fetched once per cold start.
    LaunchedEffect(Unit) {
        runCatching { repo.api.config() }.onSuccess {
            registrationOpen = it.programme.registration_open
            it.programme.signup_label?.let { lbl -> signupLabel = lbl }
        }
        configLoaded = true
    }

    // In-app update check: checked on launch/resume and periodically while in use.
    LaunchedEffect(loggedIn, authenticatedTick) {
        if (loggedIn) {
            pendingUpdate = UpdateChecker.check(context, repo.session)
            while (isActive) {
                delay(15 * 60 * 1000L)
                if (pendingUpdate == null) {
                    val u = UpdateChecker.check(context, repo.session, isInUseCheck = true)
                    if (u != null) {
                        pendingUpdate = u
                    }
                }
            }
        }
    }

    if (!configLoaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ExpressiveLoading()
        }
        return
    }

    if (!loggedIn) {
        AuthScreen(
            repo = repo,
            registrationOpen = registrationOpen,
            signupLabel = signupLabel,
            onAuthenticated = { onAuthChanged(true) },
        )
        return
    }

    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    // combined_fixes_v1 Issue 9: FLAG_SECURE is owned by QuizScreen's own
    // DisposableEffect (apply on entry, clear on dispose).
    DisposableEffect(Unit) {
        onDispose { (context as? android.app.Activity)?.let { ScreenSecurity.clear(it) } }
    }

    Scaffold(
        bottomBar = {
            if (currentRoute in Tabs.map { it.route }) {
                // M3 Expressive floating pill navigation: inset, rounded card.
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .clip(RoundedCornerShape(28.dp)),
                    shape = RoundedCornerShape(28.dp),
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    NavigationBar(
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                        tonalElevation = 0.dp,
                        windowInsets = WindowInsets(0, 0, 0, 0),
                    ) {
                    Tabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(if (selected) tab.selectedIcon else tab.icon, tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    repo = repo,
                    reloadTick = authenticatedTick,
                    onOpenRead = { nav.navigate(Routes.read(it)) },
                    onOpenSession = { ds -> nav.navigate(Routes.readSession(ds)) },
                    onOpenQuiz = { nav.navigate(Routes.quiz(it)) },
                    onOpenNotes = { nav.navigate(Routes.NOTES) },
                    onOpenAdmin = { nav.navigate(Routes.ADMIN) },
                )
            }
            composable(Routes.LEADERBOARD) { LeaderboardScreen(repo) }
            composable(Routes.PROGRESS) {
                ProgressScreen(repo, onOpenDay = { nav.navigate(Routes.read(it)) })
            }
            composable(Routes.PROFILE) {
                ProfileScreen(
                    repo,
                    onSignedOut = { onAuthChanged(false) },
                    onManageTranslations = { nav.navigate(Routes.TRANSLATIONS) },
                    onOpenChangelog = { nav.navigate(Routes.CHANGELOG) },
                )
            }
            composable(Routes.CHANGELOG) {
                ChangelogScreen(onBack = { nav.popBackStack() })
            }
            composable(
                Routes.READ,
                arguments = listOf(navArgument("day") { type = NavType.IntType }),
            ) { entry ->
                ReadScreen(
                    repo = repo,
                    day = entry.arguments?.getInt("day") ?: 1,
                    onBack = { nav.popBackStack() },
                    onOpenQuiz = { nav.navigate(Routes.quiz(it)) },
                    onAddNote = { d, b, c, v, t -> nav.navigate(Routes.noteEdit(day = d, book = b, chapter = c, verse = v, type = t)) },
                    onManageTranslations = { nav.navigate(Routes.TRANSLATIONS) },
                )
            }
            composable(Routes.TRANSLATIONS) {
                TranslationsScreen(repo = repo, onBack = { nav.popBackStack() })
            }
            composable(
                Routes.READ_SESSION,
                arguments = listOf(navArgument("days") { type = NavType.StringType }),
            ) { entry ->
                val ds = entry.arguments?.getString("days")
                    ?.split(",")?.mapNotNull { it.trim().toIntOrNull() }.orEmpty()
                ReadScreen(
                    repo = repo,
                    day = ds.firstOrNull() ?: 1,
                    sessionDays = ds.ifEmpty { listOf(1) },
                    onBack = { nav.popBackStack() },
                    onOpenQuiz = { nav.navigate(Routes.quiz(it)) },
                    onAddNote = { d, b, c, v, t -> nav.navigate(Routes.noteEdit(day = d, book = b, chapter = c, verse = v, type = t)) },
                    onManageTranslations = { nav.navigate(Routes.TRANSLATIONS) },
                )
            }
            composable(
                Routes.QUIZ,
                arguments = listOf(navArgument("day") { type = NavType.IntType }),
            ) { entry ->
                QuizScreen(
                    repo = repo,
                    day = entry.arguments?.getInt("day") ?: 1,
                    onBack = { nav.popBackStack() },
                    onHome = { nav.popBackStack(Routes.HOME, inclusive = false) },
                )
            }
            composable(Routes.NOTES) {
                NotesScreen(
                    repo = repo,
                    onBack = { nav.popBackStack() },
                    onEdit = { id, day -> nav.navigate(Routes.noteEdit(id, day)) },
                )
            }
            composable(
                Routes.NOTE_EDIT,
                arguments = listOf(
                    navArgument("noteId") { type = NavType.StringType; defaultValue = "" },
                    navArgument("day") { type = NavType.IntType; defaultValue = 0 },
                    navArgument("book") { type = NavType.StringType; defaultValue = "" },
                    navArgument("chapter") { type = NavType.IntType; defaultValue = 0 },
                    navArgument("verse") { type = NavType.StringType; defaultValue = "" },
                    navArgument("type") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                NoteEditScreen(
                    repo = repo,
                    noteId = entry.arguments?.getString("noteId")?.takeIf { it.isNotBlank() },
                    dayNumber = entry.arguments?.getInt("day")?.takeIf { it > 0 },
                    initialBook = entry.arguments?.getString("book")?.takeIf { it.isNotBlank() },
                    initialChapter = entry.arguments?.getInt("chapter")?.takeIf { it > 0 },
                    initialVerse = entry.arguments?.getString("verse")?.takeIf { it.isNotBlank() },
                    initialType = entry.arguments?.getString("type")?.takeIf { it.isNotBlank() },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.ADMIN) { AdminScreen(repo, onBack = { nav.popBackStack() }) }
        }

        // catchup_widget_links_v1 / ISSUE 3: consume a widget deep link once,
        // when logged in (fh50://read/N opens the session flow; fh50://notes?day=N
        // opens Notes as the post-completion "Reflect" target).
        var deepLinkHandled by remember { mutableStateOf(false) }
        LaunchedEffect(deepLink, loggedIn) {
            if (!deepLinkHandled && loggedIn && !deepLink.isNullOrBlank()) {
                deepLinkHandled = true
                runCatching {
                    val u = android.net.Uri.parse(deepLink)
                    when (u.host) {
                        "read" -> u.pathSegments?.firstOrNull()?.toIntOrNull()?.let {
                            nav.navigate(Routes.read(it))
                        }
                        "notes" -> nav.navigate(Routes.NOTES)
                        "home" -> nav.navigate(Routes.HOME)
                        else -> {}
                    }
                }
            }
        }

        // Update dialog and installation flow
        pendingUpdate?.let { update ->
            val formattedSize = remember(update.sizeBytes) {
                if (update.sizeBytes > 0) {
                    val mb = update.sizeBytes / (1024f * 1024f)
                    " · %.1f MB".format(mb)
                } else ""
            }
            AlertDialog(
                onDismissRequest = {
                    repo.session.updateLaterDismissedAt = System.currentTimeMillis()
                    pendingUpdate = null
                },
                title = { Text("Update available") },
                text = {
                    Text(
                        "Version ${update.versionName} is ready to install$formattedSize.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (!UpdateChecker.isOnline(context)) {
                            updateErrorDialog = Pair("Offline", "Connect to the internet to update.")
                            updateErrorApkUrl = null
                            return@TextButton
                        }
                        isDownloadingUpdate = true
                        downloadProgress = 0f
                        scope.launch {
                            val res = UpdateChecker.downloadAndVerify(context, update) { p ->
                                downloadProgress = p
                            }
                            isDownloadingUpdate = false
                            when (res) {
                                is DownloadResult.Success -> {
                                    pendingUpdate = null
                                    downloadedApkToInstall = res.apkFile
                                    if (!UpdateChecker.canRequestPackageInstalls(context)) {
                                        showPermissionDialog = true
                                    } else {
                                        UpdateChecker.installApk(context, res.apkFile)
                                    }
                                }
                                is DownloadResult.VerificationFailed -> {
                                    updateErrorDialog = Pair("Update verification failed", res.reason)
                                    updateErrorApkUrl = res.apkUrl
                                }
                                is DownloadResult.Failure -> {
                                    updateErrorDialog = Pair("Download failed", res.message)
                                    updateErrorApkUrl = res.apkUrl
                                }
                            }
                        }
                    }) { Text("Update now") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        repo.session.updateLaterDismissedAt = System.currentTimeMillis()
                        pendingUpdate = null
                    }) { Text("Later") }
                },
            )
        }

        if (isDownloadingUpdate) {
            AlertDialog(
                onDismissRequest = { /* Modal during download */ },
                title = { Text("Downloading update") },
                text = {
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(
                            "${(downloadProgress * 100).toInt()}% completed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = {},
            )
        }

        if (showPermissionDialog) {
            AlertDialog(
                onDismissRequest = { showPermissionDialog = false },
                title = { Text("Permission required") },
                text = {
                    Text(
                        "To install this update directly, ForgeHouse 50 needs permission to install unknown apps. Tap Settings to allow.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showPermissionDialog = false
                        UpdateChecker.openUnknownAppSourcesSettings(context)
                    }) { Text("Settings") }
                },
                dismissButton = {
                    TextButton(onClick = { showPermissionDialog = false }) { Text("Cancel") }
                },
            )
        }

        updateErrorDialog?.let { (title, msg) ->
            AlertDialog(
                onDismissRequest = {
                    updateErrorDialog = null
                    updateErrorApkUrl = null
                },
                title = { Text(title) },
                text = {
                    Text(
                        if (updateErrorApkUrl != null) "$msg\n\nTap below to open in your browser instead." else msg,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                confirmButton = {
                    if (updateErrorApkUrl != null) {
                        TextButton(onClick = {
                            UpdateChecker.openInBrowser(context, updateErrorApkUrl!!)
                            updateErrorDialog = null
                            updateErrorApkUrl = null
                        }) { Text("Open in browser") }
                    } else {
                        TextButton(onClick = {
                            updateErrorDialog = null
                        }) { Text("OK") }
                    }
                },
                dismissButton = {
                    if (updateErrorApkUrl != null) {
                        TextButton(onClick = {
                            updateErrorDialog = null
                            updateErrorApkUrl = null
                        }) { Text("Cancel") }
                    }
                },
            )
        }
    }
}
