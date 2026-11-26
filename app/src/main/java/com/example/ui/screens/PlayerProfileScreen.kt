package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SportsCricket
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.PlayerCareerStats
import com.example.domain.PlayerMatchPerformance
import com.example.ui.components.EditableProfileAvatar
import com.example.ui.components.MadeByFooter
import com.example.ui.components.ProfilePhotoUtils
import com.example.ui.components.TeamBadgeAvatar
import com.example.ui.components.WagonWheelViewerCard
import com.example.ui.components.formatMatchDate
import com.example.ui.components.sharePlayerCareer
import com.example.ui.theme.LocalCricketColors
import com.example.ui.theme.ScoreboardMediumStyle
import com.example.ui.theme.TabularStatStyle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerProfileScreen(
    playerName: String,
    role: String,
    teamName: String,
    stats: PlayerCareerStats?,
    onNavigateBack: () -> Unit,
    onOpenMatchScorecard: (Long) -> Unit,
    photoBase64: String = "",
    onUpdatePhoto: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val cricColors = LocalCricketColors.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var isCompressingPhoto by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && onUpdatePhoto != null) {
            scope.launch {
                isCompressingPhoto = true
                val compressed = ProfilePhotoUtils.compressGalleryPhotoToBase64(context, uri)
                isCompressingPhoto = false
                if (compressed != null) {
                    onUpdatePhoto(compressed)
                    snackbarHostState.showSnackbar("Profile photo saved (${ProfilePhotoUtils.estimateKb(compressed)} KB)")
                } else {
                    snackbarHostState.showSnackbar("Could not process selected photo")
                }
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Player Career Stats", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("player_profile_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (stats != null) {
                        IconButton(
                            onClick = { sharePlayerCareer(context, stats) },
                            modifier = Modifier.testTag("share_player_career_btn")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share career stats")
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp),
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Hero Gradient Player Header
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(cricColors.headerGradientStart, cricColors.headerGradientEnd)
                                )
                            )
                            .padding(horizontal = 18.dp, vertical = 20.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                if (onUpdatePhoto != null) {
                                    EditableProfileAvatar(
                                        label = playerName,
                                        photoBase64 = photoBase64,
                                        isCompressing = isCompressingPhoto,
                                        containerColor = Color.White.copy(alpha = 0.2f),
                                        contentColor = Color.White,
                                        size = 64.dp,
                                        onClick = {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        modifier = Modifier.testTag("player_profile_avatar_btn")
                                    )
                                } else {
                                    TeamBadgeAvatar(
                                        teamName = playerName,
                                        photoBase64 = photoBase64,
                                        containerColor = Color.White.copy(alpha = 0.2f),
                                        contentColor = Color.White,
                                        size = 62.dp
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = playerName,
                                        style = MaterialTheme.typography.headlineSmall.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = Color.White.copy(alpha = 0.18f)
                                        ) {
                                            Text(
                                                text = role,
                                                color = Color.White,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = Color(0x38FDE68A)
                                        ) {
                                            Text(
                                                text = stats?.teamName ?: teamName,
                                                color = Color(0xFFFEF3C7),
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // 4 Hero Career Highlights
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                HeroStatChip("MATCHES", "${stats?.matchesPlayed ?: 0}", Modifier.weight(1f))
                                HeroStatChip("RUNS", "${stats?.runsScored ?: 0}", Modifier.weight(1f))
                                HeroStatChip("WICKETS", "${stats?.wicketsTaken ?: 0}", Modifier.weight(1f))
                                HeroStatChip(
                                    "BEST",
                                    if (stats != null && stats.highestScore > 0) "${stats.highestScore}${if (stats.highestNotOut) "*" else ""}" else "-",
                                    Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                if (stats == null) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.SportsCricket,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Text("No Match Stats Yet", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Select $playerName as a batter or bowler in a match — their batting, bowling, and match-by-match career stats will update automatically.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    // Batting Career Card
                    item {
                        CareerStatsGridCard(
                            title = "Batting Career",
                            items = listOf(
                                "Innings" to "${stats.inningsBatted}",
                                "Not Outs" to "${stats.notOuts}",
                                "Runs" to "${stats.runsScored}",
                                "Balls Faced" to "${stats.ballsFaced}",
                                "Highest Score" to "${stats.highestScore}${if (stats.highestNotOut) "*" else ""}",
                                "Average" to "%.2f".format(stats.battingAverage),
                                "Strike Rate" to "%.1f".format(stats.strikeRate),
                                "Fours (4s)" to "${stats.fours}",
                                "Sixes (6s)" to "${stats.sixes}",
                                "30s" to "${stats.thirties}",
                                "50s / 100s" to "${stats.fifties} / ${stats.hundreds}",
                                "Ducks" to "${stats.ducks}"
                            ),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    // Bowling Career Card
                    item {
                        CareerStatsGridCard(
                            title = "Bowling Career",
                            items = listOf(
                                "Innings" to "${stats.inningsBowled}",
                                "Overs" to stats.oversBowledDisplay,
                                "Maidens" to "${stats.maidens}",
                                "Runs Conceded" to "${stats.runsConceded}",
                                "Wickets" to "${stats.wicketsTaken}",
                                "Best Bowling" to stats.bestBowlingDisplay,
                                "Economy" to "%.2f".format(stats.bowlingEconomy),
                                "Bowling Avg" to if (stats.wicketsTaken > 0) "%.2f".format(stats.bowlingAverage) else "-",
                                "Bowling SR" to if (stats.wicketsTaken > 0) "%.1f".format(stats.bowlingStrikeRate) else "-",
                                "Dot Balls" to "${stats.dotBallsBowled}",
                                "3W / 5W" to "${stats.threeWicketHauls} / ${stats.fiveWicketHauls}",
                                "Wides / NB" to "${stats.widesBowled} / ${stats.noBallsBowled}"
                            ),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    // Career Wagon Wheel (all recorded shots across matches)
                    if (stats.careerShots.isNotEmpty()) {
                        item {
                            WagonWheelViewerCard(
                                title = "Career Wagon Wheel",
                                shots = stats.careerShots,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }

                    // Recent Match-by-Match Performances
                    if (stats.recentPerformances.isNotEmpty()) {
                        item {
                            Text(
                                text = "Match-by-Match History (${stats.recentPerformances.size})",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }
                        items(stats.recentPerformances, key = { it.matchId }) { perf ->
                            PlayerPerformanceCard(
                                perf = perf,
                                onClick = { onOpenMatchScorecard(perf.matchId) },
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun HeroStatChip(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.14f)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = ScoreboardMediumStyle.copy(color = Color.White),
                maxLines = 1
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun CareerStatsGridCard(
    title: String,
    items: List<Pair<String, String>>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            items.chunked(3).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.forEach { (label, value) ->
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = value,
                                    style = TabularStatStyle.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    repeat(3 - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
internal fun PlayerPerformanceCard(
    perf: PlayerMatchPerformance,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "vs ${perf.opponentTeam}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatMatchDate(perf.matchDate),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (perf.batted) {
                    val star = if (!perf.isOut) "*" else ""
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Bat: ${perf.battingRuns}$star (${perf.battingBalls}) [${perf.battingFours}x4, ${perf.battingSixes}x6]",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                if (perf.bowled) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Bowl: ${perf.bowlingWickets}/${perf.bowlingRuns} (${perf.bowlingOversDisplay} ov)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (perf.resultSummary.isNotBlank()) {
                Text(
                    text = perf.resultSummary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
