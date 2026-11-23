package com.example

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.SportsCricket
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.SportsCricket
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.cloud.LiveBroadcastRepository
import com.example.data.cloud.authStateFlow
import com.example.data.cloud.signOutWithCleanup
import com.example.data.local.CricscoreDatabase
import com.example.data.local.PreferencesManager
import com.example.data.repository.CricscoreRepository
import com.example.ui.components.MadeByFooter
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.LiveScoringScreen
import com.example.ui.screens.LiveSpectatorScreen
import com.example.ui.screens.MatchHistoryScreen
import com.example.ui.screens.NewMatchScreen
import com.example.ui.screens.PlayerProfileScreen
import com.example.ui.screens.ScorecardScreen
import com.example.ui.screens.StatsAndSettingsScreen
import com.example.ui.screens.TeamsScreen
import com.example.ui.theme.CricscoreTheme
import com.example.viewmodel.CricscoreViewModel
import com.example.viewmodel.LiveMatchViewModel
import com.example.viewmodel.LiveSpectatorViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

private const val ROUTE_HOME = "home"
private const val ROUTE_LIVE_SCORING = "live/{matchId}"
private const val ROUTE_SCORECARD = "scorecard/{matchId}"
private const val ROUTE_PLAYER_PROFILE = "player/{playerName}"
private const val ROUTE_WATCH_LIVE = "watch_live?code={code}"

private data class HomeTabItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = CricscoreDatabase.getInstance(applicationContext)
        val prefs = PreferencesManager(applicationContext)
        val repository = CricscoreRepository(database.cricscoreDao(), prefs)
        val broadcastRepository = LiveBroadcastRepository(applicationContext)

        setContent {
            val mainViewModel: CricscoreViewModel = viewModel(
                factory = CricscoreViewModel.factory(repository)
            )
            val preferences by mainViewModel.preferences.collectAsStateWithLifecycle()

            CricscoreTheme(themeMode = preferences.themeMode) {
                AppContent(
                    repository = repository,
                    broadcastRepository = broadcastRepository,
                    mainViewModel = mainViewModel
                )
            }
        }
    }
}

@Composable
private fun AppContent(
    repository: CricscoreRepository,
    broadcastRepository: LiveBroadcastRepository,
    mainViewModel: CricscoreViewModel
) {
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var showGuestSpectator by rememberSaveable { mutableStateOf(false) }
    var guestInitialCode by rememberSaveable { mutableStateOf("") }

    val user = currentUser
    if (user == null) {
        if (showGuestSpectator) {
            androidx.activity.compose.BackHandler {
                showGuestSpectator = false
            }
            val guestSpectatorVm: LiveSpectatorViewModel = viewModel(
                key = "spectator_guest",
                factory = LiveSpectatorViewModel.factory(broadcastRepository, null)
            )
            LiveSpectatorScreen(
                viewModel = guestSpectatorVm,
                initialShareCode = guestInitialCode,
                isGuestSpectator = true,
                onAdminLoginClick = { showGuestSpectator = false },
                onNavigateBack = { showGuestSpectator = false }
            )
        } else {
            AuthScreen(
                onAuthSuccess = {
                    showGuestSpectator = false
                },
                onWatchLiveWithoutLogin = { code ->
                    guestInitialCode = code
                    showGuestSpectator = true
                }
            )
        }
    } else {
        val uid = user.uid
        LaunchedEffect(uid) {
            showGuestSpectator = false
            broadcastRepository.syncAdminProfile(
                displayName = user.displayName ?: user.email?.substringBefore("@") ?: "Cricscore Admin",
                email = user.email
            )
        }
        key(uid) {
            CricscoreNavHost(
                currentUserId = uid,
                adminDisplayName = user.displayName ?: user.email?.substringBefore("@") ?: "Cricscore Admin",
                adminEmail = user.email ?: "",
                repository = repository,
                broadcastRepository = broadcastRepository,
                mainViewModel = mainViewModel,
                onSignOut = {
                    signOutWithCleanup(
                        credentialManager = credentialManager,
                        scope = scope
                    )
                }
            )
        }
    }
}

@Composable
private fun CricscoreNavHost(
    currentUserId: String,
    adminDisplayName: String,
    adminEmail: String,
    repository: CricscoreRepository,
    broadcastRepository: LiveBroadcastRepository,
    mainViewModel: CricscoreViewModel,
    onSignOut: () -> Unit
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = ROUTE_HOME
    ) {
        composable(ROUTE_HOME) {
            HomeShellScreen(
                viewModel = mainViewModel,
                adminDisplayName = adminDisplayName,
                adminEmail = adminEmail,
                onOpenLiveMatch = { matchId ->
                    navController.navigate("live/$matchId")
                },
                onOpenScorecard = { matchId ->
                    navController.navigate("scorecard/$matchId")
                },
                onOpenPlayerProfile = { playerName ->
                    navController.navigate("player/${Uri.encode(playerName)}")
                },
                onOpenWatchLive = { code ->
                    val encoded = Uri.encode(code)
                    navController.navigate("watch_live?code=$encoded")
                },
                onSignOut = onSignOut
            )
        }

        composable(
            route = ROUTE_LIVE_SCORING,
            arguments = listOf(navArgument("matchId") { type = NavType.LongType })
        ) { entry ->
            val matchId = entry.arguments?.getLong("matchId") ?: return@composable
            val liveVm: LiveMatchViewModel = viewModel(
                key = "live_${currentUserId}_$matchId",
                factory = LiveMatchViewModel.factory(matchId, repository, broadcastRepository)
            )
            LiveScoringScreen(
                viewModel = liveVm,
                onNavigateBack = { navController.popBackStack() },
                onOpenScorecard = { id -> navController.navigate("scorecard/$id") }
            )
        }

        composable(
            route = ROUTE_SCORECARD,
            arguments = listOf(navArgument("matchId") { type = NavType.LongType })
        ) { entry ->
            val matchId = entry.arguments?.getLong("matchId") ?: return@composable
            val liveVm: LiveMatchViewModel = viewModel(
                key = "scorecard_${currentUserId}_$matchId",
                factory = LiveMatchViewModel.factory(matchId, repository, broadcastRepository)
            )
            ScorecardScreen(
                viewModel = liveVm,
                onNavigateBack = { navController.popBackStack() },
                onResumeScoring = { id ->
                    navController.navigate("live/$id") {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(
            route = ROUTE_PLAYER_PROFILE,
            arguments = listOf(navArgument("playerName") { type = NavType.StringType })
        ) { entry ->
            val rawName = entry.arguments?.getString("playerName") ?: return@composable
            val playerName = Uri.decode(rawName)
            val allPlayers by mainViewModel.allPlayersWithTeam.collectAsStateWithLifecycle()
            val leaderboardAndStats by mainViewModel.leaderboardAndStats.collectAsStateWithLifecycle()
            val playerRecord = allPlayers.firstOrNull { it.player.name.equals(playerName, ignoreCase = true) }
            val stats = leaderboardAndStats.second[playerName.lowercase()]

            PlayerProfileScreen(
                playerName = playerRecord?.player?.name ?: stats?.playerName ?: playerName,
                role = playerRecord?.player?.role ?: "All-Rounder",
                teamName = playerRecord?.teamName ?: stats?.teamName ?: "My Squad",
                stats = stats,
                onNavigateBack = { navController.popBackStack() },
                onOpenMatchScorecard = { matchId -> navController.navigate("scorecard/$matchId") }
            )
        }

        composable(
            route = ROUTE_WATCH_LIVE,
            arguments = listOf(
                navArgument("code") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { entry ->
            val initialCode = Uri.decode(entry.arguments?.getString("code").orEmpty())
            val spectatorVm: LiveSpectatorViewModel = viewModel(
                key = "spectator_$currentUserId",
                factory = LiveSpectatorViewModel.factory(broadcastRepository, currentUserId)
            )
            LiveSpectatorScreen(
                viewModel = spectatorVm,
                initialShareCode = initialCode,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeShellScreen(
    viewModel: CricscoreViewModel,
    adminDisplayName: String,
    adminEmail: String,
    onOpenLiveMatch: (Long) -> Unit,
    onOpenScorecard: (Long) -> Unit,
    onOpenPlayerProfile: (String) -> Unit,
    onOpenWatchLive: (String) -> Unit,
    onSignOut: () -> Unit
) {
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val teams by viewModel.teamsWithPlayers.collectAsStateWithLifecycle()
    val allPlayers by viewModel.allPlayersWithTeam.collectAsStateWithLifecycle()
    val matches by viewModel.matches.collectAsStateWithLifecycle()
    val leaderboardAndStats by viewModel.leaderboardAndStats.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val tabs = remember {
        listOf(
            HomeTabItem("New Match", Icons.Filled.SportsCricket, Icons.Outlined.SportsCricket, "nav_new_match"),
            HomeTabItem("Matches", Icons.Filled.History, Icons.Outlined.History, "nav_matches"),
            HomeTabItem("Players", Icons.Filled.Groups, Icons.Outlined.Groups, "nav_teams"),
            HomeTabItem("Stats", Icons.Filled.Leaderboard, Icons.Outlined.Leaderboard, "nav_stats")
        )
    }

    val liveMatches = remember(matches) { matches.filter { !it.isMatchCompleted } }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = when (selectedTab) {
                                0 -> "Cricscore"
                                1 -> "Match History"
                                2 -> "Players & Teams"
                                else -> "Stats & Settings"
                            },
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = { onOpenWatchLive("") },
                            modifier = Modifier.testTag("topbar_watch_live_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.LiveTv,
                                contentDescription = "Watch Live Match",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        tabs.forEachIndexed { idx, tab ->
                            val selected = selectedTab == idx
                            NavigationBarItem(
                                selected = selected,
                                onClick = { selectedTab = idx },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail {
                        tabs.forEachIndexed { idx, tab ->
                            val selected = selectedTab == idx
                            NavigationRailItem(
                                selected = selected,
                                onClick = { selectedTab = idx },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) {
                        when (selectedTab) {
                            0 -> NewMatchScreen(
                                formState = formState,
                                teams = teams,
                                allSavedPlayers = allPlayers,
                                liveMatches = liveMatches,
                                onHostChange = viewModel::updateHostTeam,
                                onVisitorChange = viewModel::updateVisitorTeam,
                                onSwapTeams = viewModel::swapTeams,
                                onTossWinnerChange = viewModel::setTossWonByHost,
                                onOptedToBatChange = viewModel::setOptedToBat,
                                onOversChange = viewModel::setOvers,
                                onPlayersChange = viewModel::setPlayersPerTeam,
                                onWideReballChange = viewModel::setWideReball,
                                onWideRunsEnabledChange = viewModel::setWideRunsEnabled,
                                onWideRunsChange = viewModel::setWideRuns,
                                onNoBallReballChange = viewModel::setNoBallReball,
                                onNoBallRunsEnabledChange = viewModel::setNoBallRunsEnabled,
                                onNoBallRunsChange = viewModel::setNoBallRuns,
                                onLastManChange = viewModel::setLastManStands,
                                onStartMatchConfirmed = { striker, nonStriker, bowler ->
                                    viewModel.startNewMatch(striker, nonStriker, bowler) { matchId ->
                                        onOpenLiveMatch(matchId)
                                    }
                                },
                                onResumeMatch = onOpenLiveMatch,
                                onOpenWatchLive = { onOpenWatchLive("") },
                                modifier = Modifier.fillMaxSize()
                            )
                            1 -> MatchHistoryScreen(
                                matches = matches,
                                onResumeMatch = onOpenLiveMatch,
                                onOpenScorecard = onOpenScorecard,
                                onDeleteMatch = viewModel::deleteMatch,
                                onStartNewMatchTab = { selectedTab = 0 },
                                modifier = Modifier.fillMaxSize()
                            )
                            2 -> TeamsScreen(
                                teams = teams,
                                allPlayers = allPlayers,
                                playerStatsMap = leaderboardAndStats.second,
                                onAddTeam = viewModel::addTeam,
                                onRenameTeam = viewModel::renameTeam,
                                onDeleteTeam = viewModel::deleteTeam,
                                onAddPlayerToTeam = viewModel::addPlayer,
                                onAddPlayerByTeamName = viewModel::addPlayerByTeamName,
                                onUpdatePlayer = viewModel::updatePlayer,
                                onDeletePlayer = viewModel::deletePlayer,
                                onOpenPlayerProfile = onOpenPlayerProfile,
                                modifier = Modifier.fillMaxSize()
                            )
                            else -> StatsAndSettingsScreen(
                                leaderboard = leaderboardAndStats.first,
                                preferences = preferences,
                                adminDisplayName = adminDisplayName,
                                adminEmail = adminEmail,
                                onOpenPlayerProfile = onOpenPlayerProfile,
                                onOpenWatchLive = { onOpenWatchLive("") },
                                onSignOut = onSignOut,
                                onThemeModeChange = viewModel::setThemeMode,
                                onAskShotDirectionChange = viewModel::setAskShotDirection,
                                onUpdateDefaultRules = viewModel::saveDefaultRules,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    MadeByFooter(includeNavBarPadding = useRail)
                }
            }
        }
    }
}
