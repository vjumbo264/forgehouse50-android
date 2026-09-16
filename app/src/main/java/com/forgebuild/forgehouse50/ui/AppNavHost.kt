package com.forgebuild.forgehouse50.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
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
import com.forgebuild.forgehouse50.update.UpdateChecker

object Routes {
    const val HOME = "home"
    const val LEADERBOARD = "leaderboard"
    const val PROGRESS = "progress"
    const val PROFILE = "profile"
    const val READ = "read/{day}"
    const val QUIZ = "quiz/{day}"
    const val NOTES = "notes"
    const val NOTE_EDIT = "note_edit?noteId={noteId}&day={day}"
    const val ADMIN = "admin"
    const val TRANSLATIONS = "translations"
    fun read(day: Int) = "read/$day"
    fun quiz(day: Int) = "quiz/$day"
    fun noteEdit(noteId: String?, day: Int?) =
        "note_edit?noteId=${noteId ?: ""}&day=${day ?: 0}"
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
    onAuthChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val nav: NavHostController = rememberNavController()
    var registrationOpen by remember { mutableStateOf(true) }
    var configLoaded by remember { mutableStateOf(false) }
    var pendingUpdate by remember { mutableStateOf<UpdateChecker.PendingUpdate?>(null) }

    // Public config (join window state) — fetched once per cold start.
    LaunchedEffect(Unit) {
        runCatching { repo.api.config() }.onSuccess { registrationOpen = it.programme.registration_open }
        configLoaded = true
    }

    // In-app update check: skippable but recurring, newest-always.
    LaunchedEffect(loggedIn, authenticatedTick) {
        if (loggedIn) {
            pendingUpdate = UpdateChecker.check(context, repo.session)
        }
    }

    if (!configLoaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (!loggedIn) {
        AuthScreen(
            repo = repo,
            registrationOpen = registrationOpen,
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
                NavigationBar {
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
                )
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
                    onAddNote = { d -> nav.navigate(Routes.noteEdit(null, d)) },
                    onManageTranslations = { nav.navigate(Routes.TRANSLATIONS) },
                )
            }
            composable(Routes.TRANSLATIONS) {
                TranslationsScreen(repo = repo, onBack = { nav.popBackStack() })
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
                ),
            ) { entry ->
                NoteEditScreen(
                    repo = repo,
                    noteId = entry.arguments?.getString("noteId")?.takeIf { it.isNotBlank() },
                    dayNumber = entry.arguments?.getInt("day")?.takeIf { it > 0 },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.ADMIN) { AdminScreen(repo, onBack = { nav.popBackStack() }) }
        }

        // Non-blocking, skippable update prompt.
        pendingUpdate?.let { update ->
            AlertDialog(
                onDismissRequest = { pendingUpdate = null },
                title = { Text("Update available") },
                text = {
                    Text(
                        buildString {
                            append("Version ${update.versionName} of ForgeHouse 50 is available.")
                            if (update.notes.isNotBlank()) append("\n\n${update.notes}")
                            append("\n\nYou can keep using the app either way.")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        UpdateChecker.openDownload(context, update.apkUrl)
                        pendingUpdate = null
                    }) { Text("Download") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingUpdate = null }) { Text("Skip for now") }
                },
            )
        }
    }
}
