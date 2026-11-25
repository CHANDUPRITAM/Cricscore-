package com.example

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.ui.theme.BarlowCondensedFamily
import com.example.ui.theme.CricscoreTheme
import com.example.ui.theme.LocalCricketColors
import com.example.viewmodel.CricscoreViewModel
import com.example.viewmodel.LiveMatchViewModel
import com.example.viewmodel.LiveSpectatorViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.delay

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
                var showSplash by rememberSaveable { mutableStateOf(true) }

                LaunchedEffect(Unit) {
                    if (showSplash) {
                        delay(3000L)
                        showSplash = false
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    AppContent(
                        repository = repository,
                        broadcastRepository = broadcastRepository,
                        mainViewModel = mainViewModel
                    )

                    AnimatedVisibility(
                        visible = showSplash,
                        enter = fadeIn(animationSpec = tween(250)),
                        exit = fadeOut(
                            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
                        ) + scaleOut(
                            targetScale = 1.04f,
                            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
                        )
                    ) {
                        CricscoreSplashScreen()
                    }
                }
            }
        }
    }
}

@Composable
private fun CricscoreSplashScreen() {
    var logoVisible by remember { mutableStateOf(false) }
    var titleVisible by remember { mutableStateOf(false) }
    var creditVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        logoVisible = true
        delay(180L)
        titleVisible = true
        delay(180L)
        creditVisible = true
    }

    val logoScale by animateFloatAsState(
        targetValue = if (logoVisible) 1f else 0.76f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "splash_logo_scale"
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (logoVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "splash_logo_alpha"
    )

    val titleAlpha by animateFloatAsState(
        targetValue = if (titleVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "splash_title_alpha"
    )
    val titleOffsetY by animateDpAsState(
        targetValue = if (titleVisible) 0.dp else 16.dp,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "splash_title_offset"
    )

    val creditAlpha by animateFloatAsState(
        targetValue = if (creditVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "splash_credit_alpha"
    )
    val creditOffsetY by animateDpAsState(
        targetValue = if (creditVisible) 0.dp else 14.dp,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "splash_credit_offset"
    )

    val pulseTransition = rememberInfiniteTransition(label = "splash_pulse")
    val haloScale by pulseTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "splash_halo_scale"
    )

    val cricColors = LocalCricketColors.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("splash_screen")
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        cricColors.headerGradientStart.copy(alpha = 0.22f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background,
                        cricColors.headerGradientEnd.copy(alpha = 0.14f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .scale(logoScale)
                    .alpha(logoAlpha)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    modifier = Modifier
                        .size(164.dp)
                        .scale(haloScale)
                ) {}

                Surface(
                    shape = RoundedCornerShape(34.dp),
                    color = cricColors.headerGradientStart,
                    tonalElevation = 8.dp,
                    shadowElevation = 14.dp,
                    border = BorderStroke(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier
                        .size(132.dp)
                        .testTag("splash_logo")
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_cricscore_icon_fg),
                        contentDescription = stringResource(id = R.string.app_name),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(34.dp))
                    )
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .offset(y = titleOffsetY)
                    .alpha(titleAlpha)
            ) {
                Text(
                    text = stringResource(id = R.string.app_name),
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontFamily = BarlowCondensedFamily,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = stringResource(id = R.string.splash_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 36.dp)
                .offset(y = creditOffsetY)
                .alpha(creditAlpha)
                .testTag("splash_made_by")
        ) {
            HorizontalDivider(
                modifier = Modifier.width(44.dp),
                thickness = 2.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                )
            ) {
                Text(
                    text = stringResource(id = R.string.splash_made_by),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 9.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppContent(
    repository: CricscoreRepository,
    broadcastRepository: LiveBroadcastRepository,
    mainViewModel: CricscoreViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val currentUser by remember { Firebase.auth.authStateFlow() }
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)
    var guestWatchCode by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(currentUser?.uid) {
        val user = currentUser
        if (user != null) {
            guestWatchCode = null
            broadcastRepository.syncAdminProfile(
                displayName = user.displayName ?: user.email?.substringBefore("@") ?: "Cricscore Admin",
                email = user.email
            )
        }
    }

    if (currentUser == null) {
        val activeGuestCode = guestWatchCode
        if (activeGuestCode != null) {
            key("guest_spectator") {
                val spectatorViewModel: LiveSpectatorViewModel = viewModel(
                    key = "guest_spectator_vm",
                    factory = LiveSpectatorViewModel.factory(
                        broadcastRepository = broadcastRepository,
                        currentUserId = null
                    )
                )
                LiveSpectatorScreen(
                    viewModel = spectatorViewModel,
                    initialShareCode = activeGuestCode,
                    isGuestSpectator = true,
                    onAdminLoginClick = { guestWatchCode = null },
                    onNavigateBack = { guestWatchCode = null }
                )
            }
        } else {
            AuthScreen(
                onAuthSuccess = { guestWatchCode = null },
                onWatchLiveWithoutLogin = { initialCode ->
                    guestWatchCode = initialCode
                }
            )
        }
        return
    }

    val formState by mainViewModel.formState.collectAsStateWithLifecycle()
    val teamsWithPlayers by mainViewModel.teamsWithPlayers.collectAsStateWithLifecycle()
    val allPlayersWithTeam by mainViewModel.allPlayersWithTeam.collectAsStateWithLifecycle()
    val matches by mainViewModel.matches.collectAsStateWithLifecycle()
    val leaderboardAndStats by mainViewModel.leaderboardAndStats.collectAsStateWithLifecycle()
    val preferences by mainViewModel.preferences.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = remember {
        listOf(
            HomeTabItem("New Match", Icons.Filled.SportsCricket, Icons.Outlined.SportsCricket, "tab_new_match"),
            HomeTabItem("Matches", Icons.Filled.History, Icons.Outlined.History, "tab_matches"),
            HomeTabItem("Teams", Icons.Filled.Groups, Icons.Outlined.Groups, "tab_teams"),
            HomeTabItem("Stats", Icons.Filled.Leaderboard, Icons.Outlined.Leaderboard, "tab_stats")
        )
    }

    NavHost(
        navController = navController,
        startDestination = ROUTE_HOME
    ) {
        composable(ROUTE_HOME) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useRail = maxWidth >= 600.dp

                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = "Cricscore",
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            actions = {
                                IconButton(
                                    onClick = { navController.navigate("watch_live?code=") },
                                    modifier = Modifier.testTag("top_watch_live_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.LiveTv,
                                        contentDescription = "Watch Live Matches"
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    },
                    bottomBar = {
                        if (!useRail) {
                            Column {
                                NavigationBar {
                                    tabs.forEachIndexed { index, item ->
                                        NavigationBarItem(
                                            selected = selectedTab == index,
                                            onClick = { selectedTab = index },
                                            icon = {
                                                Icon(
                                                    imageVector = if (selectedTab == index) item.selectedIcon else item.unselectedIcon,
                                                    contentDescription = item.label
                                                )
                                            },
                                            label = { Text(item.label) },
                                            modifier = Modifier.testTag(item.tag)
                                        )
                                    }
                                }
                                MadeByFooter(includeNavBarPadding = false)
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
                                tabs.forEachIndexed { index, item ->
                                    NavigationRailItem(
                                        selected = selectedTab == index,
                                        onClick = { selectedTab = index },
                                        icon = {
                                            Icon(
                                                imageVector = if (selectedTab == index) item.selectedIcon else item.unselectedIcon,
                                                contentDescription = item.label
                                            )
                                        },
                                        label = { Text(item.label) },
                                        modifier = Modifier.testTag(item.tag)
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                        ) {
                            when (selectedTab) {
                                0 -> NewMatchScreen(
                                    formState = formState,
                                    teams = teamsWithPlayers,
                                    allSavedPlayers = allPlayersWithTeam,
                                    liveMatches = matches.filter { !it.isMatchCompleted },
                                    onHostChange = mainViewModel::updateHostTeam,
                                    onVisitorChange = mainViewModel::updateVisitorTeam,
                                    onSwapTeams = mainViewModel::swapTeams,
                                    onTossWinnerChange = mainViewModel::setTossWonByHost,
                                    onOptedToBatChange = mainViewModel::setOptedToBat,
                                    onOversChange = mainViewModel::setOvers,
                                    onPlayersChange = mainViewModel::setPlayersPerTeam,
                                    onWideReballChange = mainViewModel::setWideReball,
                                    onWideRunsEnabledChange = mainViewModel::setWideRunsEnabled,
                                    onWideRunsChange = mainViewModel::setWideRuns,
                                    onNoBallReballChange = mainViewModel::setNoBallReball,
                                    onNoBallRunsEnabledChange = mainViewModel::setNoBallRunsEnabled,
                                    onNoBallRunsChange = mainViewModel::setNoBallRuns,
                                    onLastManChange = mainViewModel::setLastManStands,
                                    onStartMatchConfirmed = { striker, nonStriker, bowler ->
                                        mainViewModel.startNewMatch(striker, nonStriker, bowler) { matchId ->
                                            navController.navigate("live/$matchId")
                                        }
                                    },
                                    onResumeMatch = { matchId ->
                                        navController.navigate("live/$matchId")
                                    },
                                    onOpenWatchLive = {
                                        navController.navigate("watch_live?code=")
                                    }
                                )
                                1 -> MatchHistoryScreen(
                                    matches = matches,
                                    onResumeMatch = { matchId ->
                                        navController.navigate("live/$matchId")
                                    },
                                    onOpenScorecard = { matchId ->
                                        navController.navigate("scorecard/$matchId")
                                    },
                                    onDeleteMatch = mainViewModel::deleteMatch,
                                    onStartNewMatchTab = { selectedTab = 0 }
                                )
                                2 -> TeamsScreen(
                                    teams = teamsWithPlayers,
                                    allPlayers = allPlayersWithTeam,
                                    playerStatsMap = leaderboardAndStats.second,
                                    onAddTeam = mainViewModel::addTeam,
                                    onRenameTeam = mainViewModel::renameTeam,
                                    onDeleteTeam = mainViewModel::deleteTeam,
                                    onAddPlayerToTeam = mainViewModel::addPlayer,
                                    onAddPlayerByTeamName = mainViewModel::addPlayerByTeamName,
                                    onUpdatePlayer = mainViewModel::updatePlayer,
                                    onDeletePlayer = mainViewModel::deletePlayer,
                                    onOpenPlayerProfile = { playerName ->
                                        navController.navigate("player/${Uri.encode(playerName)}")
                                    }
                                )
                                3 -> StatsAndSettingsScreen(
                                    leaderboard = leaderboardAndStats.first,
                                    preferences = preferences,
                                    adminDisplayName = currentUser?.displayName
                                        ?: currentUser?.email?.substringBefore("@")
                                        ?: "Cricscore Admin",
                                    adminEmail = currentUser?.email ?: "",
                                    onOpenPlayerProfile = { playerName ->
                                        navController.navigate("player/${Uri.encode(playerName)}")
                                    },
                                    onOpenWatchLive = {
                                        navController.navigate("watch_live?code=")
                                    },
                                    onSignOut = {
                                        signOutWithCleanup(
                                            credentialManager = credentialManager,
                                            scope = scope
                                        )
                                    },
                                    onThemeModeChange = mainViewModel::setThemeMode,
                                    onAskShotDirectionChange = mainViewModel::setAskShotDirection,
                                    onUpdateDefaultRules = mainViewModel::saveDefaultRules
                                )
                            }
                        }
                    }
                }
            }
        }

        composable(
            route = ROUTE_LIVE_SCORING,
            arguments = listOf(navArgument("matchId") { type = NavType.LongType })
        ) { backStackEntry ->
            val matchId = backStackEntry.arguments?.getLong("matchId") ?: 0L
            key(matchId) {
                val liveVm: LiveMatchViewModel = viewModel(
                    key = "live_match_$matchId",
                    factory = LiveMatchViewModel.factory(
                        matchId = matchId,
                        repository = repository,
                        broadcastRepository = broadcastRepository
                    )
                )
                LiveScoringScreen(
                    viewModel = liveVm,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenScorecard = { id -> navController.navigate("scorecard/$id") }
                )
            }
        }

        composable(
            route = ROUTE_SCORECARD,
            arguments = listOf(navArgument("matchId") { type = NavType.LongType })
        ) { backStackEntry ->
            val matchId = backStackEntry.arguments?.getLong("matchId") ?: 0L
            key(matchId) {
                val scorecardVm: LiveMatchViewModel = viewModel(
                    key = "scorecard_$matchId",
                    factory = LiveMatchViewModel.factory(
                        matchId = matchId,
                        repository = repository,
                        broadcastRepository = broadcastRepository
                    )
                )
                ScorecardScreen(
                    viewModel = scorecardVm,
                    onNavigateBack = { navController.popBackStack() },
                    onResumeScoring = { id ->
                        navController.navigate("live/$id") {
                            popUpTo("scorecard/$id") { inclusive = true }
                        }
                    }
                )
            }
        }

        composable(
            route = ROUTE_PLAYER_PROFILE,
            arguments = listOf(navArgument("playerName") { type = NavType.StringType })
        ) { backStackEntry ->
            val encodedName = backStackEntry.arguments?.getString("playerName") ?: ""
            val playerName = Uri.decode(encodedName)
            val playerEntry = allPlayersWithTeam.firstOrNull {
                it.player.name.equals(playerName, ignoreCase = true)
            }
            val stats = leaderboardAndStats.second[playerName]
                ?: leaderboardAndStats.second.entries.firstOrNull {
                    it.key.equals(playerName, ignoreCase = true)
                }?.value
            PlayerProfileScreen(
                playerName = playerEntry?.player?.name ?: playerName,
                role = playerEntry?.player?.role ?: "All-Rounder",
                teamName = playerEntry?.teamName ?: stats?.teamName ?: "Unassigned",
                stats = stats,
                onNavigateBack = { navController.popBackStack() },
                onOpenMatchScorecard = { id -> navController.navigate("scorecard/$id") }
            )
        }

        composable(
            route = ROUTE_WATCH_LIVE,
            arguments = listOf(
                navArgument("code") {
                    type = NavType.StringType
                    defaultValue = ""
                    nullable = true
                }
            )
        ) { backStackEntry ->
            val code = Uri.decode(backStackEntry.arguments?.getString("code").orEmpty())
            val uid = currentUser?.uid
            key(uid) {
                val spectatorVm: LiveSpectatorViewModel = viewModel(
                    key = "spectator_${uid.orEmpty()}",
                    factory = LiveSpectatorViewModel.factory(
                        broadcastRepository = broadcastRepository,
                        currentUserId = uid
                    )
                )
                LiveSpectatorScreen(
                    viewModel = spectatorVm,
                    initialShareCode = code,
                    isGuestSpectator = false,
                    onAdminLoginClick = null,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}


