package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SportsCricket
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import com.example.ui.components.TeamBadgeAvatar
import com.example.ui.components.WagonWheelViewerCard
import com.example.ui.components.sharePlayerCareer
import com.example.ui.theme.LocalCricketColors
import com.example.viewmodel.SpectatorPlayerCareerState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpectatorPlayerCareerSheet(
    state: SpectatorPlayerCareerState,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val cricColors = LocalCricketColors.current
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.testTag("spectator_player_career_sheet")
    ) {
        when (state) {
            is SpectatorPlayerCareerState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                    Text(
                        text = "Loading ${state.playerName}'s career record...",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }

            is SpectatorPlayerCareerState.Ready -> {
                val stats = state.stats
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    contentPadding = PaddingValues(bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(cricColors.headerGradientStart, cricColors.headerGradientEnd)
                                    )
                                )
                                .padding(horizontal = 16.dp, vertical = 18.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    TeamBadgeAvatar(
                                        teamName = state.playerName,
                                        photoBase64 = state.photoBase64,
                                        containerColor = Color.White.copy(alpha = 0.2f),
                                        contentColor = Color.White,
                                        size = 58.dp
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = state.playerName,
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.testTag("spectator_career_player_name")
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Surface(
                                                shape = RoundedCornerShape(999.dp),
                                                color = Color.White.copy(alpha = 0.18f)
                                            ) {
                                                Text(
                                                    text = state.role,
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
                                                    text = stats?.teamName?.ifBlank { state.teamName } ?: state.teamName,
                                                    color = Color(0xFFFEF3C7),
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                    if (stats != null) {
                                        IconButton(
                                            onClick = { sharePlayerCareer(context, stats) },
                                            modifier = Modifier.testTag("spectator_career_share_btn")
                                        ) {
                                            Icon(
                                                Icons.Default.Share,
                                                contentDescription = "Share career stats",
                                                tint = Color.White
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = onDismiss,
                                        modifier = Modifier.testTag("spectator_career_close_btn")
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Close career profile",
                                            tint = Color.White
                                        )
                                    }
                                }

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

                    if (state.isLiveMatchFallbackOnly && stats != null) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            ) {
                                Text(
                                    text = "Showing live match stats (full career record syncs automatically as the scorer updates).",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
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
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
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
                                    Text("No Career Stats Yet", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "${state.playerName} has not batted or bowled in recorded matches yet.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
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

                        if (stats.careerShots.isNotEmpty()) {
                            item {
                                WagonWheelViewerCard(
                                    title = "Career Wagon Wheel",
                                    shots = stats.careerShots,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }

                        if (stats.recentPerformances.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Match-by-Match History (${stats.recentPerformances.size})",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                            }
                            items(
                                items = stats.recentPerformances,
                                key = { "${it.matchId}_${it.opponentTeam}_${it.matchDate}" }
                            ) { perf ->
                                PlayerPerformanceCard(
                                    perf = perf,
                                    onClick = {},
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
