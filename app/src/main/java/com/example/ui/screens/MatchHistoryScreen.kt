package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.MatchSnapshot
import com.example.ui.components.LiveStatusPill
import com.example.ui.components.TeamBadgeAvatar
import com.example.ui.components.formatMatchDate
import com.example.ui.components.shareMatchScorecard
import com.example.ui.theme.TabularStatStyle

@Composable
fun MatchHistoryScreen(
    matches: List<MatchSnapshot>,
    onResumeMatch: (Long) -> Unit,
    onOpenScorecard: (Long) -> Unit,
    onDeleteMatch: (Long) -> Unit,
    onStartNewMatchTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    var filterIndex by remember { mutableIntStateOf(0) } // 0 = All, 1 = Live, 2 = Completed
    var matchToDelete by remember { mutableStateOf<MatchSnapshot?>(null) }
    val context = LocalContext.current

    val filtered = remember(matches, filterIndex) {
        when (filterIndex) {
            1 -> matches.filter { !it.isMatchCompleted }
            2 -> matches.filter { it.isMatchCompleted }
            else -> matches
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filterIndex == 0,
                    onClick = { filterIndex = 0 },
                    label = { Text("All (${matches.size})") },
                    modifier = Modifier.testTag("filter_all_matches")
                )
                FilterChip(
                    selected = filterIndex == 1,
                    onClick = { filterIndex = 1 },
                    label = { Text("Live (${matches.count { !it.isMatchCompleted }})") },
                    modifier = Modifier.testTag("filter_live_matches")
                )
                FilterChip(
                    selected = filterIndex == 2,
                    onClick = { filterIndex = 2 },
                    label = { Text("Completed (${matches.count { it.isMatchCompleted }})") },
                    modifier = Modifier.testTag("filter_completed_matches")
                )
            }

            if (filtered.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_cricket_trophy_1795402434544),
                        contentDescription = "No matches yet illustration",
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .height(170.dp)
                            .clip(RoundedCornerShape(18.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = if (matches.isEmpty()) "No Matches Scored Yet" else "No Matches in This Filter",
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Start a match to track live ball-by-ball runs, scorecards, and player stats — saved offline on your phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    if (matches.isEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onStartNewMatchTab) {
                            Text("Start Your First Match")
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filtered, key = { it.matchId }) { snap ->
                        MatchHistoryCard(
                            snapshot = snap,
                            onCardClick = {
                                if (snap.isMatchCompleted) onOpenScorecard(snap.matchId)
                                else onResumeMatch(snap.matchId)
                            },
                            onResume = { onResumeMatch(snap.matchId) },
                            onScorecard = { onOpenScorecard(snap.matchId) },
                            onShare = { shareMatchScorecard(context, snap) },
                            onDelete = { matchToDelete = snap }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }

    matchToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { matchToDelete = null },
            title = { Text("Delete Match?") },
            text = { Text("Remove ${target.hostTeam} vs ${target.visitorTeam} and all its ball-by-ball history? This cannot be undone.") },
            confirmButton = {
                Button(onClick = {
                    onDeleteMatch(target.matchId)
                    matchToDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { matchToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun MatchHistoryCard(
    snapshot: MatchSnapshot,
    onCardClick: () -> Unit,
    onResume: () -> Unit,
    onScorecard: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() }
            .testTag("match_card_${snapshot.matchId}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LiveStatusPill(isLive = !snapshot.isMatchCompleted)
                    Text(
                        text = "${snapshot.rules.totalOvers} Overs • ${formatMatchDate(snapshot.createdAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row {
                    IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Share, contentDescription = "Share match", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Delete match",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Innings 1 Row
            TeamScoreLine(
                teamName = snapshot.innings1.battingTeam,
                scoreText = if (snapshot.innings1.isStarted) {
                    "${snapshot.innings1.totalRuns}/${snapshot.innings1.wickets} (${snapshot.innings1.oversDisplay})"
                } else "Yet to bat",
                isWinner = snapshot.winnerTeam == snapshot.innings1.battingTeam
            )

            // Innings 2 Row
            TeamScoreLine(
                teamName = snapshot.innings2.battingTeam,
                scoreText = if (snapshot.innings2.isStarted) {
                    "${snapshot.innings2.totalRuns}/${snapshot.innings2.wickets} (${snapshot.innings2.oversDisplay})"
                } else "Yet to bat",
                isWinner = snapshot.winnerTeam == snapshot.innings2.battingTeam
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Text(
                text = snapshot.resultSummary,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!snapshot.isMatchCompleted) {
                    Button(
                        onClick = onResume,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Resume")
                    }
                }
                OutlinedButton(
                    onClick = onScorecard,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Assessment, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Scorecard")
                }
            }
        }
    }
}

@Composable
private fun TeamScoreLine(
    teamName: String,
    scoreText: String,
    isWinner: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            TeamBadgeAvatar(teamName = teamName, size = 32.dp)
            Text(
                text = teamName,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = if (isWinner) FontWeight.Bold else FontWeight.SemiBold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = scoreText,
            style = TabularStatStyle.copy(
                fontWeight = if (isWinner) FontWeight.Bold else FontWeight.SemiBold
            ),
            color = if (isWinner) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}
