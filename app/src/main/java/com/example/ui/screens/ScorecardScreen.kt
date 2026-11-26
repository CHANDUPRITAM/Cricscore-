package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.FilterChip
import com.example.domain.InningsState
import com.example.domain.MatchSnapshot
import com.example.domain.WinProbabilityEngine
import com.example.ui.components.BallChipBadge
import com.example.ui.components.MadeByFooter
import com.example.ui.components.ManhattanChartCard
import com.example.ui.components.RunBreakdownCard
import com.example.ui.components.StatTableHeaderRow
import com.example.ui.components.StatTableValueRow
import com.example.ui.components.WagonWheelViewerCard
import com.example.ui.components.WinProbabilityCard
import com.example.ui.components.WormGraphCard
import com.example.ui.components.shareMatchScorecard
import com.example.ui.theme.LocalCricketColors
import com.example.viewmodel.LiveMatchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScorecardScreen(
    viewModel: LiveMatchViewModel,
    onNavigateBack: () -> Unit,
    onResumeScoring: ((Long) -> Unit)? = null,
    onOpenPlayerProfile: ((String) -> Unit)? = null,
    onDeleteMatch: (() -> Unit)? = null
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val snap = snapshot
    if (snap == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedInnings by remember(snap.currentInningsNumber) {
        mutableIntStateOf(if (snap.innings2.isStarted) 2 else 1)
    }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val cricColors = LocalCricketColors.current

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
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
                            text = snap.resultSummary,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("scorecard_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!snap.isMatchCompleted && onResumeScoring != null) {
                        IconButton(onClick = { onResumeScoring(snap.matchId) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Resume live scoring")
                        }
                    }
                    IconButton(
                        onClick = { shareMatchScorecard(context, snap) },
                        modifier = Modifier.testTag("share_scorecard_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share scorecard")
                    }
                    if (snap.isMatchCompleted && onDeleteMatch != null) {
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.testTag("scorecard_delete_match_btn")
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete finished match")
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
        },
        bottomBar = {
            MadeByFooter(includeNavBarPadding = true)
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
                    .widthIn(max = 680.dp)
            ) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Scorecard", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.testTag("tab_scorecard")
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Overs", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.testTag("tab_overs")
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Shots", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.testTag("tab_wagon_wheel")
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = { Text("Graphs", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.testTag("tab_graphs")
                    )
                }

                when (selectedTab) {
                    0 -> ScorecardTabContent(
                        snapshot = snap,
                        selectedInnings = selectedInnings,
                        onSelectInnings = { selectedInnings = it },
                        onOpenPlayerProfile = onOpenPlayerProfile
                    )
                    1 -> OversTabContent(
                        snapshot = snap,
                        selectedInnings = selectedInnings,
                        onSelectInnings = { selectedInnings = it },
                        onOpenPlayerProfile = onOpenPlayerProfile
                    )
                    2 -> WagonWheelTabContent(
                        snapshot = snap,
                        selectedInnings = selectedInnings,
                        onSelectInnings = { selectedInnings = it },
                        onOpenPlayerProfile = onOpenPlayerProfile
                    )
                    3 -> GraphsTabContent(snapshot = snap)
                }
            }
        }
    }

    if (showDeleteConfirm && onDeleteMatch != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Match?") },
            text = {
                Text("Remove ${snap.hostTeam} vs ${snap.visitorTeam} and all its ball-by-ball history? This cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteMatch()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_scorecard_delete_btn")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScorecardTabContent(
    snapshot: MatchSnapshot,
    selectedInnings: Int,
    onSelectInnings: (Int) -> Unit,
    onOpenPlayerProfile: ((String) -> Unit)? = null
) {
    val activeInnings = if (selectedInnings == 2 && snapshot.innings2.isStarted) snapshot.innings2 else snapshot.innings1

    LazyColumn(
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Result & Player of Match summary banner
        if (snapshot.isMatchCompleted) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Column {
                            Text(
                                text = snapshot.resultSummary,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            snapshot.playerOfMatch?.let { potm ->
                                Text(
                                    text = "POTM: ${potm.playerName} (${potm.summary})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }

        // Innings Switcher
        if (snapshot.innings2.isStarted) {
            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedInnings == 1,
                        onClick = { onSelectInnings(1) },
                        shape = SegmentedButtonDefaults.itemShape(0, 2)
                    ) {
                        Text(
                            "${snapshot.innings1.battingTeam} (${snapshot.innings1.totalRuns}/${snapshot.innings1.wickets})",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    SegmentedButton(
                        selected = selectedInnings == 2,
                        onClick = { onSelectInnings(2) },
                        shape = SegmentedButtonDefaults.itemShape(1, 2)
                    ) {
                        Text(
                            "${snapshot.innings2.battingTeam} (${snapshot.innings2.totalRuns}/${snapshot.innings2.wickets})",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Batting Table Card
        item {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${activeInnings.battingTeam} Batting",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "${activeInnings.totalRuns}/${activeInnings.wickets} (${activeInnings.oversDisplay} ov)",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    StatTableHeaderRow(firstLabel = "BATTER", columns = listOf("R", "B", "4s", "6s", "SR"))

                    activeInnings.batters.forEachIndexed { idx, batter ->
                        if (idx > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        val star = if (!batter.isOut && !batter.isRetiredHurt) " *" else ""
                        StatTableValueRow(
                            name = "${batter.name}$star",
                            subtitle = batter.dismissalText,
                            isHighlighted = !batter.isOut && !batter.isRetiredHurt && !activeInnings.isCompleted,
                            values = listOf(
                                "${batter.runs}",
                                "${batter.balls}",
                                "${batter.fours}",
                                "${batter.sixes}",
                                "%.1f".format(batter.strikeRate)
                            ),
                            onNameClick = onOpenPlayerProfile?.let { cb -> { cb(batter.name) } }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Extras", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text(
                            text = activeInnings.extras.summary(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        Text(
                            text = "${activeInnings.totalRuns}/${activeInnings.wickets} (${activeInnings.oversDisplay} Ov, CRR ${"%.2f".format(activeInnings.currentRunRate)})",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Fall of Wickets
        if (activeInnings.fallOfWickets.isNotEmpty()) {
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Fall of Wickets", style = MaterialTheme.typography.titleMedium)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            activeInnings.fallOfWickets.forEach { fow ->
                                AssistChip(
                                    onClick = { onOpenPlayerProfile?.invoke(fow.batterName) },
                                    label = {
                                        Text(
                                            "${fow.runsAtFall}-${fow.wicketNumber} (${fow.batterName}, ${fow.overDisplay})",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bowling Table
        item {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    Text(
                        text = "${activeInnings.bowlingTeam} Bowling",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                    )
                    StatTableHeaderRow(firstLabel = "BOWLER", columns = listOf("O", "M", "R", "W", "ER"))
                    activeInnings.bowlers.forEachIndexed { idx, bw ->
                        if (idx > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        StatTableValueRow(
                            name = bw.name,
                            subtitle = if (bw.wides > 0 || bw.noBalls > 0) "WD: ${bw.wides} • NB: ${bw.noBalls}" else null,
                            values = listOf(
                                bw.oversDisplay,
                                "${bw.maidens}",
                                "${bw.runsConceded}",
                                "${bw.wickets}",
                                "%.1f".format(bw.economy)
                            ),
                            onNameClick = onOpenPlayerProfile?.let { cb -> { cb(bw.name) } }
                        )
                    }
                }
            }
        }

        // Partnerships Section
        if (activeInnings.partnerships.isNotEmpty()) {
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Partnerships", style = MaterialTheme.typography.titleMedium)
                        activeInnings.partnerships.forEach { p ->
                            val totalBatRuns = (p.batter1Runs + p.batter2Runs).coerceAtLeast(1)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${p.batter1} ${p.batter1Runs}(${p.batter1Balls})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (onOpenPlayerProfile != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        modifier = if (onOpenPlayerProfile != null) Modifier.clickable { onOpenPlayerProfile(p.batter1) } else Modifier
                                    )
                                    Text(
                                        text = "${p.runs} (${p.balls})${if (p.isUnbroken) "*" else ""}",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "${p.batter2} ${p.batter2Runs}(${p.batter2Balls})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (onOpenPlayerProfile != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        modifier = if (onOpenPlayerProfile != null) Modifier.clickable { onOpenPlayerProfile(p.batter2) } else Modifier
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(7.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    if (p.batter1Runs > 0) {
                                        Box(
                                            modifier = Modifier
                                                .weight(p.batter1Runs.toFloat() / totalBatRuns)
                                                .height(7.dp)
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                    }
                                    if (p.batter2Runs > 0) {
                                        Box(
                                            modifier = Modifier
                                                .weight(p.batter2Runs.toFloat() / totalBatRuns)
                                                .height(7.dp)
                                                .background(MaterialTheme.colorScheme.tertiary)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OversTabContent(
    snapshot: MatchSnapshot,
    selectedInnings: Int,
    onSelectInnings: (Int) -> Unit,
    onOpenPlayerProfile: ((String) -> Unit)? = null
) {
    val inn: InningsState = if (selectedInnings == 2 && snapshot.innings2.isStarted) snapshot.innings2 else snapshot.innings1

    LazyColumn(
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (snapshot.innings2.isStarted) {
            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedInnings == 1,
                        onClick = { onSelectInnings(1) },
                        shape = SegmentedButtonDefaults.itemShape(0, 2)
                    ) { Text(snapshot.innings1.battingTeam, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    SegmentedButton(
                        selected = selectedInnings == 2,
                        onClick = { onSelectInnings(2) },
                        shape = SegmentedButtonDefaults.itemShape(1, 2)
                    ) { Text(snapshot.innings2.battingTeam, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }

        if (inn.overs.isEmpty()) {
            item {
                Text(
                    text = "No balls bowled in this innings yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            items(inn.overs.reversed(), key = { "${inn.inningsNumber}_${it.overNumber}" }) { over ->
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = if (onOpenPlayerProfile != null) {
                                    Modifier.clickable { onOpenPlayerProfile(over.bowlerName) }
                                } else Modifier
                            ) {
                                Text(
                                    text = "Over ${over.overNumber} • ${over.bowlerName}",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (onOpenPlayerProfile != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Score: ${over.cumulativeRuns}/${over.cumulativeWickets}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "${over.runsInOver} runs" + if (over.wicketsInOver > 0) " • ${over.wicketsInOver}W" else "",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(over.balls, key = { it.eventId }) { chip ->
                                BallChipBadge(chip = chip, size = 34.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WagonWheelTabContent(
    snapshot: MatchSnapshot,
    selectedInnings: Int,
    onSelectInnings: (Int) -> Unit,
    onOpenPlayerProfile: ((String) -> Unit)? = null
) {
    val inn: InningsState = if (selectedInnings == 2 && snapshot.innings2.isStarted) snapshot.innings2 else snapshot.innings1
    var selectedBatter by remember(selectedInnings) { mutableIntStateOf(-1) } // -1 = All Batters
    val battersWithShots = remember(inn.batters) { inn.batters }
    val activeBatterName = if (selectedBatter in battersWithShots.indices) battersWithShots[selectedBatter].name else null

    val shotsToShow = remember(inn.shots, activeBatterName) {
        if (activeBatterName == null) inn.shots
        else inn.shots.filter { it.batterName.equals(activeBatterName, ignoreCase = true) }
    }

    LazyColumn(
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (snapshot.innings2.isStarted) {
            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedInnings == 1,
                        onClick = { onSelectInnings(1) },
                        shape = SegmentedButtonDefaults.itemShape(0, 2)
                    ) { Text(snapshot.innings1.battingTeam, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    SegmentedButton(
                        selected = selectedInnings == 2,
                        onClick = { onSelectInnings(2) },
                        shape = SegmentedButtonDefaults.itemShape(1, 2)
                    ) { Text(snapshot.innings2.battingTeam, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }

        // Batter filter row
        if (battersWithShots.isNotEmpty()) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedBatter == -1,
                            onClick = { selectedBatter = -1 },
                            label = { Text("All Batters") }
                        )
                    }
                    items(battersWithShots.size) { idx ->
                        val b = battersWithShots[idx]
                        FilterChip(
                            selected = selectedBatter == idx,
                            onClick = { selectedBatter = idx },
                            label = { Text("${b.name} (${b.runs})") }
                        )
                    }
                }
            }
        }

        // Main Wagon Wheel Ground Card
        item {
            WagonWheelViewerCard(
                title = if (activeBatterName != null) "$activeBatterName • Shot Map" else "${inn.battingTeam} • Wagon Wheel",
                shots = shotsToShow
            )
        }

        // Ball-by-Ball Shot Direction Log
        if (shotsToShow.isNotEmpty()) {
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Ball-by-Ball Shot Log (${shotsToShow.size})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        shotsToShow.reversed().forEachIndexed { idx, shot ->
                            if (idx > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            val cricColors = LocalCricketColors.current
                            val badgeColor = when {
                                shot.isSix -> cricColors.boundarySix
                                shot.isFour -> cricColors.boundaryFour
                                else -> MaterialTheme.colorScheme.primary
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Over ${shot.overBallDisplay} • ${shot.batterName}",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (onOpenPlayerProfile != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        modifier = if (onOpenPlayerProfile != null) Modifier.clickable { onOpenPlayerProfile(shot.batterName) } else Modifier
                                    )
                                    Text(
                                        text = "${shot.shotZone} • vs ${shot.bowlerName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = if (onOpenPlayerProfile != null) Modifier.clickable { onOpenPlayerProfile(shot.bowlerName) } else Modifier
                                    )
                                }
                                Surface(
                                    color = badgeColor,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "${shot.runs} ${if (shot.runs == 1) "run" else "runs"}",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GraphsTabContent(snapshot: MatchSnapshot) {
    val winProbSummary = remember(snapshot.totalEventCount, snapshot.isMatchCompleted, snapshot.rules.totalOvers) {
        WinProbabilityEngine.calculateFromSnapshot(snapshot)
    }
    LazyColumn(
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            WinProbabilityCard(summary = winProbSummary)
        }
        item {
            ManhattanChartCard(innings1 = snapshot.innings1, innings2 = snapshot.innings2)
        }
        item {
            WormGraphCard(innings1 = snapshot.innings1, innings2 = snapshot.innings2)
        }
        if (snapshot.innings1.isStarted) {
            item {
                RunBreakdownCard(innings = snapshot.innings1)
            }
        }
        if (snapshot.innings2.isStarted) {
            item {
                RunBreakdownCard(innings = snapshot.innings2)
            }
        }
    }
}
