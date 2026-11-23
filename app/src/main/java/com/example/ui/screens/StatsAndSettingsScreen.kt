package com.example.ui.screens

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SportsCricket
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.data.local.DefaultMatchPreferences
import com.example.domain.LeaderboardData
import com.example.domain.PlayerCareerStats
import com.example.ui.theme.ScoreboardMediumStyle
import com.example.ui.theme.ThemeMode

@Composable
fun StatsAndSettingsScreen(
    leaderboard: LeaderboardData,
    preferences: DefaultMatchPreferences,
    adminDisplayName: String,
    adminEmail: String,
    onOpenPlayerProfile: (String) -> Unit,
    onOpenWatchLive: () -> Unit,
    onSignOut: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAskShotDirectionChange: (Boolean) -> Unit,
    onUpdateDefaultRules: (
        overs: Int,
        players: Int,
        wideReball: Boolean,
        wideRuns: Int,
        noBallReball: Boolean,
        noBallRuns: Int,
        lastManStands: Boolean
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Overall App Summary KPI Row
            item {
                Text("Cricscore Career Overview", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SummaryKpiCard("Matches", "${leaderboard.totalMatches}", Modifier.weight(1f))
                    SummaryKpiCard("Total Runs", "${leaderboard.totalRunsAcrossApp}", Modifier.weight(1f))
                    SummaryKpiCard("Wickets", "${leaderboard.totalWicketsAcrossApp}", Modifier.weight(1f))
                }
            }

            // Top Run Scorers
            item {
                LeaderboardCard(
                    title = "Top Run Scorers",
                    icon = Icons.Default.EmojiEvents,
                    emptyText = "Score matches to see batting leaders here.",
                    players = leaderboard.topBatters,
                    onPlayerClick = onOpenPlayerProfile,
                    statBuilder = { p -> "${p.runsScored} runs" to "SR ${"%.1f".format(p.strikeRate)} • ${p.matchesPlayed}m" }
                )
            }

            // Top Wicket Takers
            item {
                LeaderboardCard(
                    title = "Top Wicket Takers",
                    icon = Icons.Default.SportsCricket,
                    emptyText = "Bowl overs in matches to track top bowlers.",
                    players = leaderboard.topBowlers,
                    onPlayerClick = onOpenPlayerProfile,
                    statBuilder = { p -> "${p.wicketsTaken} wkts" to "ER ${"%.2f".format(p.bowlingEconomy)} • Best ${p.bestBowlingDisplay}" }
                )
            }

            // Top Six Hitters
            item {
                LeaderboardCard(
                    title = "Maximum Sixes",
                    icon = Icons.Default.Whatshot,
                    emptyText = "Smash sixes to claim the big-hitter board!",
                    players = leaderboard.topSixHitters,
                    onPlayerClick = onOpenPlayerProfile,
                    statBuilder = { p -> "${p.sixes} sixes" to "${p.fours} fours • ${p.runsScored} runs" }
                )
            }

            // Appearance Theme Selector
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Appearance", style = MaterialTheme.typography.titleMedium)
                        }
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            ThemeMode.entries.forEachIndexed { idx, mode ->
                                SegmentedButton(
                                    selected = preferences.themeMode == mode,
                                    onClick = { onThemeModeChange(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(idx, ThemeMode.entries.size),
                                    modifier = Modifier.testTag("theme_mode_${mode.name.lowercase()}")
                                ) {
                                    Text(mode.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }

            // Admin Account & Live Cloud Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                Icon(
                                    imageVector = Icons.Default.AdminPanelSettings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text("Admin Account", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            }
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "VERIFIED",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = adminDisplayName.ifBlank { "Cricscore Admin" },
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                            if (adminEmail.isNotBlank()) {
                                Text(
                                    text = adminEmail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onOpenWatchLive,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("settings_live_broadcasts_btn")
                            ) {
                                Icon(Icons.Default.LiveTv, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Live Matches", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(
                                onClick = onSignOut,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("admin_sign_out_button")
                            ) {
                                Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sign Out")
                            }
                        }
                    }
                }
            }

            // Default Gully Cricket Preferences
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Default Match Rules", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Applied automatically whenever you open New Match. Local history stays on device while Live Broadcasts sync in real time.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Shot Direction Ground (Wagon Wheel)", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    if (preferences.askShotDirection) "Show ground to tick shot on every run" else "Off — quick score without ground popup",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = preferences.askShotDirection,
                                onCheckedChange = onAskShotDirectionChange
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Run on Wide by Default", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    if (preferences.defaultWideRuns > 0) "+${preferences.defaultWideRuns} run penalty" else "0 runs (off)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = preferences.defaultWideRuns > 0,
                                onCheckedChange = { checked ->
                                    onUpdateDefaultRules(
                                        preferences.defaultOvers,
                                        preferences.defaultPlayers,
                                        preferences.defaultWideReball,
                                        if (checked) 1 else 0,
                                        preferences.defaultNoBallReball,
                                        preferences.defaultNoBallRuns,
                                        preferences.defaultLastManStands
                                    )
                                }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Run on No-Ball by Default", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    if (preferences.defaultNoBallRuns > 0) "+${preferences.defaultNoBallRuns} run penalty" else "0 runs (off)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = preferences.defaultNoBallRuns > 0,
                                onCheckedChange = { checked ->
                                    onUpdateDefaultRules(
                                        preferences.defaultOvers,
                                        preferences.defaultPlayers,
                                        preferences.defaultWideReball,
                                        preferences.defaultWideRuns,
                                        preferences.defaultNoBallReball,
                                        if (checked) 1 else 0,
                                        preferences.defaultLastManStands
                                    )
                                }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Wide Re-ball by Default", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = preferences.defaultWideReball,
                                onCheckedChange = { checked ->
                                    onUpdateDefaultRules(
                                        preferences.defaultOvers,
                                        preferences.defaultPlayers,
                                        checked,
                                        preferences.defaultWideRuns,
                                        preferences.defaultNoBallReball,
                                        preferences.defaultNoBallRuns,
                                        preferences.defaultLastManStands
                                    )
                                }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("No-Ball Re-ball by Default", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = preferences.defaultNoBallReball,
                                onCheckedChange = { checked ->
                                    onUpdateDefaultRules(
                                        preferences.defaultOvers,
                                        preferences.defaultPlayers,
                                        preferences.defaultWideReball,
                                        preferences.defaultWideRuns,
                                        checked,
                                        preferences.defaultNoBallRuns,
                                        preferences.defaultLastManStands
                                    )
                                }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Last Man Stands (Gully Mode)", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = preferences.defaultLastManStands,
                                onCheckedChange = { checked ->
                                    onUpdateDefaultRules(
                                        preferences.defaultOvers,
                                        preferences.defaultPlayers,
                                        preferences.defaultWideReball,
                                        preferences.defaultWideRuns,
                                        preferences.defaultNoBallReball,
                                        preferences.defaultNoBallRuns,
                                        checked
                                    )
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun SummaryKpiCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = ScoreboardMediumStyle.copy(color = MaterialTheme.colorScheme.primary)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LeaderboardCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    emptyText: String,
    players: List<PlayerCareerStats>,
    onPlayerClick: (String) -> Unit,
    statBuilder: (PlayerCareerStats) -> Pair<String, String>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }

            if (players.isEmpty()) {
                Text(emptyText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                players.take(5).forEachIndexed { idx, p ->
                    if (idx > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val (primaryStat, secondaryStat) = statBuilder(p)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlayerClick(p.playerName) }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "#${idx + 1}",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = if (idx == 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Column {
                                Text(
                                    text = p.playerName,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = p.teamName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = primaryStat,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = secondaryStat,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
