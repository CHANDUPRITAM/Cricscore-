package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import android.content.Intent
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.InningsState
import com.example.domain.MatchSnapshot
import com.example.ui.components.BallChipBadge
import com.example.ui.components.MadeByFooter
import com.example.ui.components.ShotDirectionPickerDialog
import com.example.ui.components.StatTableHeaderRow
import com.example.ui.components.StatTableValueRow
import com.example.ui.components.shareMatchScorecard
import com.example.ui.theme.LocalCricketColors
import com.example.ui.theme.ScoreboardHeroStyle
import com.example.viewmodel.KeypadModifiers
import com.example.viewmodel.LiveMatchViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScoringScreen(
    viewModel: LiveMatchViewModel,
    onNavigateBack: () -> Unit,
    onOpenScorecard: (Long) -> Unit
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val modifiers by viewModel.modifiers.collectAsStateWithLifecycle()
    val pendingWicketRuns by viewModel.pendingWicketRuns.collectAsStateWithLifecycle()
    val pendingShotRuns by viewModel.pendingShotRuns.collectAsStateWithLifecycle()
    val askShotDirection by viewModel.askShotDirection.collectAsStateWithLifecycle()
    val teamsWithPlayers by viewModel.teamsWithPlayers.collectAsStateWithLifecycle()
    val allSavedPlayers by viewModel.allPlayersWithTeam.collectAsStateWithLifecycle()
    val liveShareCode by viewModel.liveShareCode.collectAsStateWithLifecycle()
    val isLiveSharingEnabled by viewModel.isLiveSharingEnabled.collectAsStateWithLifecycle()
    val isSyncingLive by viewModel.isSyncingLive.collectAsStateWithLifecycle()
    val liveSyncMessage by viewModel.liveSyncMessage.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showMenu by remember { mutableStateOf(false) }
    var showLiveShareDialog by remember { mutableStateOf(false) }
    var showRetireDialog by remember { mutableStateOf(false) }
    var showCustomRunsDialog by remember { mutableStateOf(false) }
    var showEditOversDialog by remember { mutableStateOf(false) }
    var showEditRulesDialog by remember { mutableStateOf(false) }
    var showChangeBowlerDialog by remember { mutableStateOf(false) }
    var showEndInningsConfirm by remember { mutableStateOf(false) }
    var matchCompleteDismissed by remember { mutableStateOf(false) }

    LaunchedEffect(liveSyncMessage) {
        liveSyncMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearLiveSyncMessage()
        }
    }

    val snap = snapshot
    if (snap == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val inn = snap.currentInnings
    val cricColors = LocalCricketColors.current

    fun rosterFor(teamName: String): List<String> {
        val teamPlayers = teamsWithPlayers.firstOrNull { it.team.name.equals(teamName, ignoreCase = true) }
            ?.players?.map { it.name }.orEmpty()
        val allPlayers = teamsWithPlayers.flatMap { twp -> twp.players.map { it.name } }
        return (teamPlayers + allPlayers).distinct()
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "${snap.hostTeam} v ${snap.visitorTeam}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (snap.isMatchCompleted) "Match Completed" else "Innings ${snap.currentInningsNumber} • ${snap.rules.totalOvers} Overs",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("scoring_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Surface(
                        onClick = {
                            if (liveShareCode == null) {
                                viewModel.startOrResumeLiveBroadcast()
                            }
                            showLiveShareDialog = true
                        },
                        shape = RoundedCornerShape(999.dp),
                        color = if (isLiveSharingEnabled && liveShareCode != null) Color(0xFF16A34A) else Color(0x33FFFFFF),
                        modifier = Modifier
                            .padding(end = 2.dp)
                            .testTag("go_live_broadcast_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WifiTethering,
                                contentDescription = "Go Live Broadcast",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = if (liveShareCode != null && isLiveSharingEnabled) liveShareCode!! else "GO LIVE",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold)
                            )
                        }
                    }
                    IconButton(
                        onClick = { onOpenScorecard(snap.matchId) },
                        modifier = Modifier.testTag("open_scorecard_button")
                    ) {
                        Icon(Icons.Default.Assessment, contentDescription = "Full Scorecard & Graphs")
                    }
                    IconButton(
                        onClick = { shareMatchScorecard(context, snap) },
                        modifier = Modifier.testTag("share_live_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share Live Score")
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Match options")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Change Bowler") },
                                leadingIcon = { Icon(Icons.Default.SwapHoriz, null) },
                                onClick = {
                                    showMenu = false
                                    showChangeBowlerDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Retire Batsman") },
                                leadingIcon = { Icon(Icons.Default.PersonOff, null) },
                                onClick = {
                                    showMenu = false
                                    showRetireDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Change Match Overs") },
                                leadingIcon = { Icon(Icons.Default.Timer, null) },
                                onClick = {
                                    showMenu = false
                                    showEditOversDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Wide & No-Ball Rules") },
                                leadingIcon = { Icon(Icons.Default.Assessment, null) },
                                onClick = {
                                    showMenu = false
                                    showEditRulesDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (askShotDirection) "Shot Ground Picker: ON" else "Shot Ground Picker: OFF") },
                                leadingIcon = { Icon(Icons.Default.AddCircleOutline, null) },
                                onClick = {
                                    showMenu = false
                                    viewModel.setAskShotDirection(!askShotDirection)
                                }
                            )
                            if (!snap.isMatchCompleted && inn.isStarted && !inn.isCompleted) {
                                DropdownMenuItem(
                                    text = { Text("Declare / End Innings") },
                                    leadingIcon = { Icon(Icons.Default.Flag, null) },
                                    onClick = {
                                        showMenu = false
                                        showEndInningsConfirm = true
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = cricColors.headerGradientStart,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp)
            ) {
                // Top Broadcast Scoreboard Header + Live Stats (scrollable area)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ScoreboardHeaderBand(snapshot = snap, inn = inn)

                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Active Batters & Bowler Card
                        LivePlayersCard(
                            inn = inn,
                            onSwapStrike = { viewModel.swapStrike() },
                            onChangeBowler = { showChangeBowlerDialog = true }
                        )

                        // This Over Timeline Card
                        ThisOverCard(inn = inn)

                        // Match Completed Banner if dismissed dialog
                        if (snap.isMatchCompleted) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = snap.resultSummary,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    snap.playerOfMatch?.let { potm ->
                                        Text(
                                            text = "Player of the Match: ${potm.playerName} (${potm.summary})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { onOpenScorecard(snap.matchId) },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Full Scorecard")
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                matchCompleteDismissed = false
                                                viewModel.undoLastBall()
                                            }
                                        ) {
                                            Text("Undo Last Ball")
                                        }
                                    }
                                }
                            }
                        }

                        // Action Banner if awaiting innings 2 start
                        if (!snap.isMatchCompleted && snap.innings1.isCompleted && !snap.innings2.isStarted) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Innings Break • Target ${snap.innings1.totalRuns + 1}",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "${snap.innings1.battingTeam} scored ${snap.innings1.totalRuns}/${snap.innings1.wickets} in ${snap.innings1.oversDisplay} overs.",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                // Bottom Tactile Scoring Keypad
                if (!snap.isMatchCompleted && inn.isStarted && !inn.isCompleted) {
                    ScoringKeypadPanel(
                        modifiers = modifiers,
                        defaultWideRuns = snap.rules.wideRuns,
                        defaultNoBallRuns = snap.rules.noBallRuns,
                        onToggleWide = viewModel::toggleWide,
                        onToggleNoBall = viewModel::toggleNoBall,
                        onToggleBye = viewModel::toggleBye,
                        onToggleLegBye = viewModel::toggleLegBye,
                        onToggleWicket = viewModel::toggleWicket,
                        onToggleExtraRunPenalty = viewModel::toggleExtraRunPenalty,
                        onRunClick = { runs ->
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.onRunButtonTapped(runs)
                        },
                        onQuickWicket = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.openWicketDirectly()
                        },
                        onUndo = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.undoLastBall { undone ->
                                if (!undone) {
                                    scope.launch { snackbarHostState.showSnackbar("Nothing left to undo") }
                                }
                            }
                        },
                        onSwapStrike = { viewModel.swapStrike() },
                        onRetire = { showRetireDialog = true },
                        onCustomRuns = { showCustomRunsDialog = true }
                    )
                } else {
                    MadeByFooter(includeNavBarPadding = true)
                }
            }
        }
    }

    // ---- Smart Automated Match Dialogs ----

    // 0. Shot Direction Ground Picker Dialog (Wagon Wheel)
    val shotRuns = pendingShotRuns
    if (shotRuns != null) {
        ShotDirectionPickerDialog(
            runs = shotRuns,
            batterName = inn.striker.ifBlank { "Batter" },
            onConfirmShot = { x, y, zone ->
                viewModel.confirmShotAndRecord(x, y, zone)
            },
            onSkip = {
                viewModel.confirmShotAndRecord(null, null, null)
            },
            onDismiss = viewModel::dismissShotDialog
        )
    }

    // 1. Wicket Dialog
    val wicketRuns = pendingWicketRuns
    if (wicketRuns != null) {
        val alreadyBatted = inn.batters.map { it.name }.toSet()
        val availableBatters = rosterFor(inn.battingTeam).filter { it !in alreadyBatted }
        val isLastWicket = (inn.wickets + 1) >= inn.maxWickets
        WicketDialog(
            initialRuns = wicketRuns,
            striker = inn.striker,
            nonStriker = inn.nonStriker,
            isLastWicket = isLastWicket,
            availableBatters = availableBatters,
            fieldingRoster = rosterFor(inn.bowlingTeam),
            onDismiss = viewModel::dismissWicketDialog,
            onConfirm = { runs, type, outBatter, fielder, newBatter ->
                viewModel.confirmWicket(runs, type, outBatter, fielder, newBatter)
            }
        )
    }

    // 2. Needs New Batsman (e.g. after undo or retire without replacement)
    if (!snap.isMatchCompleted && inn.isStarted && !inn.isCompleted && inn.needsNewBatsman && wicketRuns == null) {
        val alreadyBatted = inn.batters.filter { it.isOut || it.name == inn.striker || it.name == inn.nonStriker }
            .map { it.name }.toSet()
        val suggestions = rosterFor(inn.battingTeam).filter { it !in alreadyBatted }
        NextPlayerPromptDialog(
            title = "Select Next Batsman",
            label = "Incoming Batter (${inn.battingTeam})",
            suggestions = suggestions,
            helperNote = null,
            onConfirm = { viewModel.setNextBatsman(it) }
        )
    }

    // 3. End of Over -> Select New Bowler (or manual Change Bowler)
    if ((!snap.isMatchCompleted && inn.isStarted && !inn.isCompleted && inn.needsNewBowler && !inn.needsNewBatsman && wicketRuns == null) ||
        showChangeBowlerDialog
    ) {
        val bowlingRoster = (rosterFor(inn.bowlingTeam) + inn.bowlers.map { it.name }).distinct()
        val lastBowler = inn.lastCompletedOverBowler
        val overCompletedNum = inn.legalBalls / 6
        val title = if (showChangeBowlerDialog) "Change Current Bowler" else "Over $overCompletedNum Complete • Next Bowler"
        NextPlayerPromptDialog(
            title = title,
            label = "Bowler (${inn.bowlingTeam})",
            suggestions = bowlingRoster,
            helperNote = if (!showChangeBowlerDialog && lastBowler.isNotBlank()) {
                "Previous over bowled by $lastBowler"
            } else null,
            allowDismiss = showChangeBowlerDialog,
            onDismiss = { showChangeBowlerDialog = false },
            onConfirm = { name ->
                showChangeBowlerDialog = false
                viewModel.setNextBowler(name)
            }
        )
    }

    // 4. Innings Break -> Start 2nd Innings
    if (!snap.isMatchCompleted && snap.innings1.isCompleted && !snap.innings2.isStarted) {
        OpeningPlayersDialog(
            title = "2nd Innings • Target ${snap.innings1.totalRuns + 1}",
            battingTeam = snap.innings2.battingTeam,
            bowlingTeam = snap.innings2.bowlingTeam,
            battingRoster = rosterFor(snap.innings2.battingTeam),
            bowlingRoster = rosterFor(snap.innings2.bowlingTeam),
            allSavedPlayers = allSavedPlayers,
            onDismiss = { /* User can undo last ball via menu or start 2nd innings */ },
            onConfirm = { s, ns, bw -> viewModel.startSecondInnings(s, ns, bw) }
        )
    }

    // 4b. Live Broadcast Share Code Dialog
    if (showLiveShareDialog) {
        AlertDialog(
            onDismissRequest = { showLiveShareDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.WifiTethering,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "Live Match Broadcast",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Share this Match Code so viewers can watch instant, zero-lag ball-by-ball updates on Cricscore.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isSyncingLive && liveShareCode == null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Starting live broadcast...")
                        }
                    } else if (liveShareCode != null) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "LIVE MATCH CODE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                                    )
                                    Text(
                                        text = liveShareCode!!,
                                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.testTag("live_share_code_value")
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(liveShareCode!!))
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Copied code ${liveShareCode!!}")
                                        }
                                    },
                                    modifier = Modifier.testTag("copy_live_code_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy live code",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Allow Spectators", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = if (isLiveSharingEnabled) "Syncing every ball live" else "Paused — viewers cannot join",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isLiveSharingEnabled,
                                onCheckedChange = { viewModel.toggleLiveSharing(it) }
                            )
                        }
                    } else {
                        Button(
                            onClick = { viewModel.startOrResumeLiveBroadcast() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Start Live Broadcast")
                        }
                    }
                }
            },
            confirmButton = {
                if (liveShareCode != null) {
                    Button(
                        onClick = {
                            val code = liveShareCode!!
                            val shareBody = buildString {
                                appendLine("LIVE Cricket Score on Cricscore!")
                                appendLine("${snap.hostTeam} vs ${snap.visitorTeam}")
                                appendLine("${inn.battingTeam}: ${inn.totalRuns}/${inn.wickets} (${inn.oversDisplay}/${inn.maxOvers} ov)")
                                appendLine()
                                appendLine("Open Cricscore -> Watch Live Match -> Enter Code: $code")
                            }
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareBody)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Live Match Code"))
                        },
                        modifier = Modifier.testTag("share_live_code_intent_btn")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share Code")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showLiveShareDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // 5. Retire Batter Dialog
    if (showRetireDialog) {
        val alreadyBatted = inn.batters.filter { it.isOut || it.name == inn.striker || it.name == inn.nonStriker }
            .map { it.name }.toSet()
        RetireBatterDialog(
            striker = inn.striker,
            nonStriker = inn.nonStriker,
            suggestions = rosterFor(inn.battingTeam).filter { it !in alreadyBatted },
            onDismiss = { showRetireDialog = false },
            onConfirm = { retiring, replacement ->
                showRetireDialog = false
                viewModel.retireBatter(retiring, replacement)
            }
        )
    }

    // 6. Custom Runs Dialog (5, 7, etc.)
    if (showCustomRunsDialog) {
        CustomRunsDialog(
            onDismiss = { showCustomRunsDialog = false },
            onSelectRuns = { r ->
                showCustomRunsDialog = false
                viewModel.onRunButtonTapped(r)
            }
        )
    }

    // 7. Change Match Overs Dialog
    if (showEditOversDialog) {
        EditOversDialog(
            currentOvers = snap.rules.totalOvers,
            onDismiss = { showEditOversDialog = false },
            onConfirm = { newOv ->
                showEditOversDialog = false
                viewModel.updateTotalOvers(newOv)
            }
        )
    }

    // 7b. Live Wide / No-Ball Rules Dialog
    if (showEditRulesDialog) {
        LiveMatchRulesDialog(
            initialWideReball = snap.rules.wideReball,
            initialWideRuns = snap.rules.wideRuns,
            initialNoBallReball = snap.rules.noBallReball,
            initialNoBallRuns = snap.rules.noBallRuns,
            initialLastManStands = snap.rules.lastManStands,
            onDismiss = { showEditRulesDialog = false },
            onSave = { wReball, wRuns, nbReball, nbRuns, lastMan ->
                showEditRulesDialog = false
                viewModel.updateMatchRules(wReball, wRuns, nbReball, nbRuns, lastMan)
            }
        )
    }

    // 8. Declare / End Innings Confirmation
    if (showEndInningsConfirm) {
        ConfirmActionDialog(
            title = "End Current Innings?",
            message = "This will finish ${inn.battingTeam}'s innings at ${inn.totalRuns}/${inn.wickets} (${inn.oversDisplay} ov).",
            confirmLabel = "End Innings",
            onDismiss = { showEndInningsConfirm = false },
            onConfirm = {
                showEndInningsConfirm = false
                viewModel.endCurrentInningsEarly()
            }
        )
    }

    // 9. Match Completed Celebration Dialog
    if (snap.isMatchCompleted && !matchCompleteDismissed) {
        MatchCompleteDialog(
            snapshot = snap,
            onViewScorecard = {
                matchCompleteDismissed = true
                onOpenScorecard(snap.matchId)
            },
            onShare = { shareMatchScorecard(context, snap) },
            onDismiss = { matchCompleteDismissed = true }
        )
    }
}

@Composable
private fun ScoreboardHeaderBand(
    snapshot: MatchSnapshot,
    inn: InningsState
) {
    val cricColors = LocalCricketColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    colors = listOf(cricColors.headerGradientStart, cricColors.headerGradientEnd)
                )
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${inn.battingTeam.uppercase()} • ${if (inn.inningsNumber == 1) "1ST INN" else "2ND INN"}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        ),
                        color = Color(0xFFA7F3D0),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "${inn.totalRuns}-${inn.wickets}",
                            style = ScoreboardHeroStyle.copy(color = Color.White),
                            modifier = Modifier.testTag("live_score_text")
                        )
                        Text(
                            text = "(${inn.oversDisplay}/${inn.maxOvers})",
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = Color.White.copy(alpha = 0.88f),
                                fontWeight = FontWeight.SemiBold
                            ),
                            modifier = Modifier
                                .padding(bottom = 6.dp)
                                .testTag("live_overs_text")
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "CRR",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.72f)
                    )
                    Text(
                        text = "%.2f".format(inn.currentRunRate),
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    if (inn.requiredRunRate != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "RRR ${"%.2f".format(inn.requiredRunRate)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFFDE68A)
                        )
                    }
                }
            }

            // Target / Chase Strip
            val chaseBanner = when {
                snapshot.isMatchCompleted -> snapshot.resultSummary
                inn.target != null -> {
                    val need = inn.runsNeeded ?: 0
                    "Target ${inn.target} • Need $need runs off ${inn.ballsRemaining} balls"
                }
                else -> snapshot.tossSummary
            }
            Surface(
                color = Color(0x2BFFFFFF),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = chaseBanner,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun LivePlayersCard(
    inn: InningsState,
    onSwapStrike: () -> Unit,
    onChangeBowler: () -> Unit
) {
    val strikerStats = inn.batterStat(inn.striker)
    val nonStrikerStats = inn.batterStat(inn.nonStriker)
    val bowlerStats = inn.bowlerStat(inn.currentBowler)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            StatTableHeaderRow(firstLabel = "BATTER", columns = listOf("R", "B", "4s", "6s", "SR"))

            if (strikerStats != null) {
                StatTableValueRow(
                    name = "${strikerStats.name} *",
                    isHighlighted = true,
                    values = listOf(
                        "${strikerStats.runs}",
                        "${strikerStats.balls}",
                        "${strikerStats.fours}",
                        "${strikerStats.sixes}",
                        "%.1f".format(strikerStats.strikeRate)
                    ),
                    modifier = Modifier.clickable { onSwapStrike() }
                )
            }
            if (nonStrikerStats != null && nonStrikerStats.name != strikerStats?.name) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                StatTableValueRow(
                    name = nonStrikerStats.name,
                    isHighlighted = false,
                    values = listOf(
                        "${nonStrikerStats.runs}",
                        "${nonStrikerStats.balls}",
                        "${nonStrikerStats.fours}",
                        "${nonStrikerStats.sixes}",
                        "%.1f".format(nonStrikerStats.strikeRate)
                    ),
                    modifier = Modifier.clickable { onSwapStrike() }
                )
            }

            // Partnership & Last Wicket Strip
            val partnership = inn.currentPartnership
            if (partnership != null || inn.lastWicketSummary != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (partnership != null) {
                        Text(
                            text = "P'ship: ${partnership.runs} (${partnership.balls})",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (inn.lastWicketSummary != null) {
                        Text(
                            text = "Last Wkt: ${inn.lastWicketSummary}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }

            StatTableHeaderRow(firstLabel = "BOWLER", columns = listOf("O", "M", "R", "W", "ER"))
            if (bowlerStats != null) {
                StatTableValueRow(
                    name = bowlerStats.name,
                    subtitle = "Tap to change bowler",
                    values = listOf(
                        bowlerStats.oversDisplay,
                        "${bowlerStats.maidens}",
                        "${bowlerStats.runsConceded}",
                        "${bowlerStats.wickets}",
                        "%.1f".format(bowlerStats.economy)
                    ),
                    modifier = Modifier.clickable { onChangeBowler() }
                )
            }
        }
    }
}

@Composable
private fun ThisOverCard(inn: InningsState) {
    val listState = rememberLazyListState()
    LaunchedEffect(inn.currentOverBalls.size) {
        if (inn.currentOverBalls.isNotEmpty()) {
            listState.animateScrollToItem(inn.currentOverBalls.lastIndex)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "This Over",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (inn.currentOverBalls.isEmpty()) {
                Text(
                    text = "New over ready — tap runs below",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            } else {
                LazyRow(
                    state = listState,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(inn.currentOverBalls, key = { it.eventId }) { chip ->
                        BallChipBadge(chip = chip, size = 34.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoringKeypadPanel(
    modifiers: KeypadModifiers,
    defaultWideRuns: Int,
    defaultNoBallRuns: Int,
    onToggleWide: () -> Unit,
    onToggleNoBall: () -> Unit,
    onToggleBye: () -> Unit,
    onToggleLegBye: () -> Unit,
    onToggleWicket: () -> Unit,
    onToggleExtraRunPenalty: () -> Unit,
    onRunClick: (Int) -> Unit,
    onQuickWicket: () -> Unit,
    onUndo: () -> Unit,
    onSwapStrike: () -> Unit,
    onRetire: () -> Unit,
    onCustomRuns: () -> Unit
) {
    val cricColors = LocalCricketColors.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Contextual Optional Penalty Run bar whenever WD or NB is selected
            if (modifiers.isWide || modifiers.isNoBall) {
                val extraName = if (modifiers.isWide) "Wide" else "No-Ball"
                val activePenalty = if (modifiers.isWide) defaultWideRuns.coerceAtLeast(1) else defaultNoBallRuns.coerceAtLeast(1)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = cricColors.extra.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, cricColors.extra.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (modifiers.includeExtraRunPenalty) {
                                "$extraName (+$activePenalty run) • Tap 0 for plain $extraName"
                            } else {
                                "$extraName (0 penalty run) • Tap 0 for 0-run $extraName"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilterChip(
                            selected = modifiers.includeExtraRunPenalty,
                            onClick = onToggleExtraRunPenalty,
                            label = {
                                Text(
                                    text = if (modifiers.includeExtraRunPenalty) "+$activePenalty Run ON" else "0 Run OFF",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            },
                            modifier = Modifier
                                .height(30.dp)
                                .testTag("toggle_extra_penalty_run_chip")
                        )
                    }
                }
            }

            // Extras & Wicket Modifier Toggles (just like Cricket Scorer)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val wdLabel = if (defaultWideRuns == 0) "WD(0)" else "WD"
                val nbLabel = if (defaultNoBallRuns == 0) "NB(0)" else "NB"
                ModifierChipItem(wdLabel, modifiers.isWide, cricColors.extra, Modifier.weight(1f).testTag("mod_wide"), onToggleWide)
                ModifierChipItem(nbLabel, modifiers.isNoBall, cricColors.extra, Modifier.weight(1f).testTag("mod_noball"), onToggleNoBall)
                ModifierChipItem("B", modifiers.isBye, cricColors.extra, Modifier.weight(1f).testTag("mod_bye"), onToggleBye)
                ModifierChipItem("LB", modifiers.isLegBye, cricColors.extra, Modifier.weight(1f).testTag("mod_legbye"), onToggleLegBye)
                ModifierChipItem("WKT", modifiers.isWicket, cricColors.wicket, Modifier.weight(1f).testTag("mod_wicket"), onToggleWicket)
            }

            // Quick Utility Row: Undo, Swap, Retire, 5+ Runs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KeypadUtilityButton(
                    label = "Undo",
                    icon = Icons.AutoMirrored.Filled.Undo,
                    onClick = onUndo,
                    modifier = Modifier.weight(1f).testTag("btn_undo")
                )
                KeypadUtilityButton(
                    label = "Swap",
                    icon = Icons.Default.SwapHoriz,
                    onClick = onSwapStrike,
                    modifier = Modifier.weight(1f).testTag("btn_swap_strike")
                )
                KeypadUtilityButton(
                    label = "Retire",
                    icon = Icons.Default.PersonOff,
                    onClick = onRetire,
                    modifier = Modifier.weight(1f).testTag("btn_retire")
                )
                KeypadUtilityButton(
                    label = "5+",
                    icon = Icons.Default.AddCircleOutline,
                    onClick = onCustomRuns,
                    modifier = Modifier.weight(1f).testTag("btn_custom_runs")
                )
            }

            // Main Run Buttons Row 1: 0, 1, 2, 3
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RunPadButton("0", "DOT", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f).testTag("run_btn_0")) { onRunClick(0) }
                RunPadButton("1", null, MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f).testTag("run_btn_1"), outlined = true) { onRunClick(1) }
                RunPadButton("2", null, MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f).testTag("run_btn_2"), outlined = true) { onRunClick(2) }
                RunPadButton("3", null, MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f).testTag("run_btn_3"), outlined = true) { onRunClick(3) }
            }

            // Main Run Buttons Row 2: 4, 6, OUT (Wicket)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RunPadButton("4", "FOUR", cricColors.boundaryFour, cricColors.onBoundaryFour, Modifier.weight(1f).testTag("run_btn_4")) { onRunClick(4) }
                RunPadButton("6", "SIX", cricColors.boundarySix, cricColors.onBoundarySix, Modifier.weight(1f).testTag("run_btn_6")) { onRunClick(6) }
                RunPadButton("OUT", "WICKET", cricColors.wicket, cricColors.onWicket, Modifier.weight(1.3f).testTag("run_btn_out")) { onQuickWicket() }
            }

            MadeByFooter()
        }
    }
}

@Composable
private fun ModifierChipItem(
    label: String,
    selected: Boolean,
    activeColor: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier.height(38.dp),
        label = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = activeColor,
            selectedLabelColor = Color.White
        )
    )
}

@Composable
private fun KeypadUtilityButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun RunPadButton(
    primaryText: String,
    subText: String?,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(58.dp),
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = if (outlined) BorderStroke(1.2.dp, MaterialTheme.colorScheme.outline) else null,
        tonalElevation = if (outlined) 0.dp else 2.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = primaryText,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = contentColor
            )
            if (subText != null) {
                Text(
                    text = subText,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                    color = contentColor.copy(alpha = 0.85f)
                )
            }
        }
    }
}
