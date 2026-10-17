package com.forgebuild.forgehouse50.ui

import com.forgebuild.forgehouse50.ui.community.CommunityScreen
import com.forgebuild.forgehouse50.ui.announcements.AnnouncementsScreen
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import android.animation.ValueAnimator

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
import com.forgebuild.forgehouse50.update.UpdateManager
import com.forgebuild.forgehouse50.update.UpdateUiState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
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
    const val COMMUNITY = "community"
    const val ANNOUNCEMENTS = "announcements"
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

private val Tabs = listOf(
    NavItem(Routes.HOME, "Today", Icons.Filled.Home, Icons.Outlined.Home),
    NavItem(Routes.LEADERBOARD, "Leaderboard", Icons.Filled.EmojiEvents, Icons.Outlined.EmojiEvents),
    NavItem(Routes.PROGRESS, "Progress", Icons.Filled.TrendingUp, Icons.Outlined.TrendingUp),
    NavItem(Routes.PROFILE, "Profile", Icons.Filled.Person, Icons.Outlined.Person),
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
    val updateState by UpdateManager.uiState.collectAsState()
    var updateLaterDismissed by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                UpdateManager.onResume(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Public config (join window state) — fetched once per cold start.
    LaunchedEffect(Unit) {
        runCatching { repo.api.config() }.onSuccess {
            registrationOpen = it.programme.registration_open
            it.programme.signup_label?.let { lbl -> signupLabel = lbl }
        }
        configLoaded = true
    }

    // In-app update check: initialized on startup, checked on login and periodically while in use.
    LaunchedEffect(loggedIn, authenticatedTick) {
        UpdateManager.init(context, repo.session)
        if (loggedIn) {
            UpdateManager.check(context, repo.session)
            while (isActive) {
                delay(15 * 60 * 1000L)
                UpdateManager.check(context, repo.session, isInUseCheck = true)
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
    val rootPagerState = rememberPagerState(initialPage = 0) { 4 }
    val areAnimatorsEnabled = remember {
        try {
            ValueAnimator.areAnimatorsEnabled()
        } catch (_: Throwable) {
            true
        }
    }

    // combined_fixes_v1 Issue 9: FLAG_SECURE is owned by QuizScreen's own
    // DisposableEffect (apply on entry, clear on dispose).
    DisposableEffect(Unit) {
        onDispose { (context as? android.app.Activity)?.let { ScreenSecurity.clear(it) } }
    }

    val isRootTab = currentRoute == null || currentRoute == Routes.HOME || currentRoute in Tabs.map { it.route }

    // Intercept back button when on non-home root tab to smoothly return to home tab
    BackHandler(enabled = isRootTab && rootPagerState.currentPage != 0) {
        scope.launch {
            if (areAnimatorsEnabled) rootPagerState.animateScrollToPage(0) else rootPagerState.scrollToPage(0)
        }
    }

    Scaffold(
        bottomBar = {
            if (isRootTab) {
                DynamicFloatingNavBar(
                    items = Tabs,
                    pagerState = rootPagerState,
                    onItemSelected = { index, _ ->
                        scope.launch {
                            if (areAnimatorsEnabled) {
                                rootPagerState.animateScrollToPage(index)
                            } else {
                                rootPagerState.scrollToPage(index)
                            }
                        }
                    },
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) {
                HorizontalPager(
                    state = rootPagerState,
                    beyondViewportPageCount = 0,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    when (page) {
                        0 -> HomeScreen(
                            repo = repo,
                            reloadTick = authenticatedTick,
                            onOpenRead = { nav.navigate(Routes.read(it)) },
                            onOpenSession = { ds -> nav.navigate(Routes.readSession(ds)) },
                            onOpenQuiz = { nav.navigate(Routes.quiz(it)) },
                            onOpenNotes = { nav.navigate(Routes.NOTES) },
                            onOpenAdmin = { nav.navigate(Routes.ADMIN) },
                            onOpenCommunity = { nav.navigate(Routes.COMMUNITY) },
                            onOpenAnnouncements = { nav.navigate(Routes.ANNOUNCEMENTS) },
                        )
                        1 -> LeaderboardScreen(repo)
                        2 -> ProgressScreen(repo, onOpenDay = { nav.navigate(Routes.read(it)) })
                        3 -> ProfileScreen(
                            repo,
                            onSignedOut = { onAuthChanged(false) },
                            onManageTranslations = { nav.navigate(Routes.TRANSLATIONS) },
                            onOpenChangelog = { nav.navigate(Routes.CHANGELOG) },
                        )
                    }
                }
            }
            composable(Routes.LEADERBOARD) {
                LaunchedEffect(Unit) {
                    if (areAnimatorsEnabled) rootPagerState.animateScrollToPage(1) else rootPagerState.scrollToPage(1)
                    nav.popBackStack()
                }
            }
            composable(Routes.PROGRESS) {
                LaunchedEffect(Unit) {
                    if (areAnimatorsEnabled) rootPagerState.animateScrollToPage(2) else rootPagerState.scrollToPage(2)
                    nav.popBackStack()
                }
            }
            composable(Routes.PROFILE) {
                LaunchedEffect(Unit) {
                    if (areAnimatorsEnabled) rootPagerState.animateScrollToPage(3) else rootPagerState.scrollToPage(3)
                    nav.popBackStack()
                }
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
            composable(Routes.COMMUNITY) { CommunityScreen(repo = repo, onBack = { nav.popBackStack() }) }
            composable(Routes.ANNOUNCEMENTS) { AnnouncementsScreen(repo = repo, onBack = { nav.popBackStack() }) }
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
        when (val s = updateState) {
            is UpdateUiState.Available -> {
                if (!updateLaterDismissed) {
                    val update = s.update
                    val formattedSize = remember(update.sizeBytes) {
                        if (update.sizeBytes > 0) {
                            val mb = update.sizeBytes / (1024f * 1024f)
                            " · %.1f MB".format(mb)
                        } else ""
                    }
                    AlertDialog(
                        onDismissRequest = {
                            repo.session.updateLaterDismissedAt = System.currentTimeMillis()
                            updateLaterDismissed = true
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
                                UpdateManager.startDownload(context, repo.session, update, isHidden = false)
                            }) { Text("Update now") }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                repo.session.updateLaterDismissedAt = System.currentTimeMillis()
                                updateLaterDismissed = true
                            }) { Text("Later") }
                        },
                    )
                }
            }
            is UpdateUiState.Downloading -> {
                if (!s.isHidden) {
                    AlertDialog(
                        onDismissRequest = { UpdateManager.hideDownload() },
                        title = { Text("Downloading update") },
                        text = {
                            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Text(
                                    "${(s.progress * 100).toInt()}% completed",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(8.dp))
                                ExpressiveLinearProgress(
                                    progress = { s.progress },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        },
                        confirmButton = {},
                        dismissButton = {
                            TextButton(onClick = { UpdateManager.hideDownload() }) {
                                Text("Hide")
                            }
                        },
                    )
                }
            }
            is UpdateUiState.PermissionRequired -> {
                AlertDialog(
                    onDismissRequest = { /* Keep state */ },
                    title = { Text("Permission required") },
                    text = {
                        Text(
                            "To install this update directly, ForgeHouse 50 needs permission to install unknown apps. Tap Settings to allow.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            UpdateChecker.openUnknownAppSourcesSettings(context)
                        }) { Text("Settings") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            UpdateManager.dismissError()
                        }) { Text("Cancel") }
                    },
                )
            }
            is UpdateUiState.Error -> {
                AlertDialog(
                    onDismissRequest = { UpdateManager.dismissError() },
                    title = { Text("Update failed") },
                    text = {
                        Text(
                            if (s.apkUrl != null) "${s.message}\n\nTap below to open in your browser instead." else s.message,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    confirmButton = {
                        if (s.apkUrl != null) {
                            TextButton(onClick = {
                                UpdateChecker.openInBrowser(context, s.apkUrl)
                                UpdateManager.dismissError()
                            }) { Text("Open in browser") }
                        } else {
                            TextButton(onClick = { UpdateManager.dismissError() }) { Text("OK") }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { UpdateManager.dismissError() }) { Text("Cancel") }
                    },
                )
            }
            else -> {}
        }
    }
}
