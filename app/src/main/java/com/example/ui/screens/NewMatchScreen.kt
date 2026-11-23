package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsCricket
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.R
import com.example.data.repository.PlayerWithTeam
import com.example.data.repository.TeamWithPlayers
import com.example.domain.MatchSnapshot
import com.example.ui.components.LiveStatusPill
import com.example.ui.theme.LocalCricketColors
import com.example.ui.theme.ScoreboardMediumStyle
import com.example.viewmodel.NewMatchFormState
import kotlinx.coroutines.launch
import kotlin.random.Random

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewMatchScreen(
    formState: NewMatchFormState,
    teams: List<TeamWithPlayers>,
    allSavedPlayers: List<PlayerWithTeam>,
    liveMatches: List<MatchSnapshot>,
    onHostChange: (String) -> Unit,
    onVisitorChange: (String) -> Unit,
    onSwapTeams: () -> Unit,
    onTossWinnerChange: (Boolean) -> Unit,
    onOptedToBatChange: (Boolean) -> Unit,
    onOversChange: (Int) -> Unit,
    onPlayersChange: (Int) -> Unit,
    onWideReballChange: (Boolean) -> Unit,
    onWideRunsEnabledChange: (Boolean) -> Unit,
    onWideRunsChange: (Int) -> Unit,
    onNoBallReballChange: (Boolean) -> Unit,
    onNoBallRunsEnabledChange: (Boolean) -> Unit,
    onNoBallRunsChange: (Int) -> Unit,
    onLastManChange: (Boolean) -> Unit,
    onStartMatchConfirmed: (striker: String, nonStriker: String, bowler: String) -> Unit,
    onResumeMatch: (Long) -> Unit,
    onOpenWatchLive: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAdvancedRules by remember { mutableStateOf(false) }
    var showOpenersDialog by remember { mutableStateOf(false) }
    var customOversText by remember(formState.totalOvers) { mutableStateOf(formState.totalOvers.toString()) }
    var coinTossMessage by remember { mutableStateOf<String?>(null) }

    val coinRotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val hostName = formState.hostTeam.trim().ifEmpty { "Team A" }
    val visitorName = formState.visitorTeam.trim().ifEmpty { "Team B" }

    val batFirstTeam = remember(hostName, visitorName, formState.tossWonByHost, formState.optedToBat) {
        val tossWinner = if (formState.tossWonByHost) hostName else visitorName
        if (formState.optedToBat) tossWinner else if (tossWinner == hostName) visitorName else hostName
    }
    val bowlFirstTeam = if (batFirstTeam == hostName) visitorName else hostName

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Hero Stadium Banner
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(152.dp),
                    shape = MaterialTheme.shapes.large,
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            painter = painterResource(id = R.drawable.img_stadium_hero_1795402433856),
                            contentDescription = "Cricket stadium pitch at golden hour",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0x33042316),
                                            Color(0xD9052B1B)
                                        )
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp)
                        ) {
                            Surface(
                                color = Color(0x38FFFFFF),
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Text(
                                    text = "ADMIN SCORER • ZERO-LAG LIVE SHARE",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Cricscore",
                                style = ScoreboardMediumStyle.copy(color = Color.White)
                            )
                            Text(
                                text = "Score as Admin, pick saved players as openers & share live score in real time.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.88f)
                            )
                        }
                    }
                }
            }

            // Watch Live Score by Code quick entry card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_watch_live_card")
                        .clickable { onOpenWatchLive() },
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.LiveTv,
                                        contentDescription = "Watch live match",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "Watch Live Match Score",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Enter shared Match Code for instant zero-lag live scoreboard",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Open Watch Live",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Quick Resume Banner if a live match is ongoing
            if (liveMatches.isNotEmpty()) {
                val active = liveMatches.first()
                item {
                    val inn = active.currentInnings
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("resume_live_match_card")
                            .clickable { onResumeMatch(active.matchId) },
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    LiveStatusPill(isLive = true, label = "RESUME LIVE")
                                    Text(
                                        text = "${active.rules.totalOvers} ov",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "${active.hostTeam} vs ${active.visitorTeam}",
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${inn.battingTeam}: ${inn.totalRuns}/${inn.wickets} (${inn.oversDisplay})",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "Resume live match",
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Teams Selection Card
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
                            Text(
                                text = "Teams",
                                style = MaterialTheme.typography.titleMedium
                            )
                            TextButton(
                                onClick = onSwapTeams,
                                modifier = Modifier.testTag("swap_teams_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapVert,
                                    contentDescription = "Swap teams",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Swap")
                            }
                        }

                        OutlinedTextField(
                            value = formState.hostTeam,
                            onValueChange = onHostChange,
                            label = { Text("Host Team") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("host_team_input")
                        )

                        OutlinedTextField(
                            value = formState.visitorTeam,
                            onValueChange = onVisitorChange,
                            label = { Text("Visitor Team") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("visitor_team_input")
                        )

                        if (teams.isNotEmpty()) {
                            Text(
                                text = "Quick pick saved teams (tap to fill)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(teams, key = { it.team.id }) { twp ->
                                    SuggestionChip(
                                        onClick = {
                                            if (formState.hostTeam == "Team A" || formState.hostTeam.isBlank()) {
                                                onHostChange(twp.team.name)
                                            } else if (formState.visitorTeam == "Team B" || formState.visitorTeam.isBlank() ||
                                                !formState.hostTeam.equals(twp.team.name, ignoreCase = true)
                                            ) {
                                                onVisitorChange(twp.team.name)
                                            } else {
                                                onHostChange(twp.team.name)
                                            }
                                        },
                                        label = { Text(twp.team.name) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Toss Card with Interactive Coin Flip
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
                            Text("Toss", style = MaterialTheme.typography.titleMedium)
                            Surface(
                                onClick = {
                                    scope.launch {
                                        coinRotation.snapTo(0f)
                                        coinRotation.animateTo(720f, animationSpec = tween(550))
                                        val hostWon = Random.nextBoolean()
                                        onTossWinnerChange(hostWon)
                                        val face = if (Random.nextBoolean()) "Heads" else "Tails"
                                        coinTossMessage = "$face! ${if (hostWon) hostName else visitorName} won the toss"
                                    }
                                },
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                modifier = Modifier.testTag("coin_toss_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Casino,
                                        contentDescription = "Flip coin",
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .graphicsLayer { rotationY = coinRotation.value }
                                    )
                                    Text(
                                        text = "Flip Coin",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                        }

                        if (coinTossMessage != null) {
                            Text(
                                text = coinTossMessage!!,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }

                        Text(
                            text = "Toss won by",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = formState.tossWonByHost,
                                onClick = { onTossWinnerChange(true) },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                modifier = Modifier.testTag("toss_host_button")
                            ) {
                                Text(hostName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            SegmentedButton(
                                selected = !formState.tossWonByHost,
                                onClick = { onTossWinnerChange(false) },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                modifier = Modifier.testTag("toss_visitor_button")
                            ) {
                                Text(visitorName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }

                        Text(
                            text = "Opted to",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = formState.optedToBat,
                                onClick = { onOptedToBatChange(true) },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                modifier = Modifier.testTag("opted_bat_button")
                            ) {
                                Text("Bat")
                            }
                            SegmentedButton(
                                selected = !formState.optedToBat,
                                onClick = { onOptedToBatChange(false) },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                modifier = Modifier.testTag("opted_bowl_button")
                            ) {
                                Text("Bowl")
                            }
                        }
                    }
                }
            }

            // Overs & Squad Size Card
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
                            Text("Overs", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = customOversText,
                                onValueChange = { raw ->
                                    val digits = raw.filter { it.isDigit() }.take(2)
                                    customOversText = digits
                                    digits.toIntOrNull()?.let { if (it > 0) onOversChange(it) }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .width(84.dp)
                                    .testTag("overs_input")
                            )
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(5, 6, 8, 10, 12, 15, 20, 50).forEach { ov ->
                                FilterChip(
                                    selected = formState.totalOvers == ov,
                                    onClick = {
                                        onOversChange(ov)
                                        customOversText = ov.toString()
                                    },
                                    label = { Text("$ov") }
                                )
                            }
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Players per Team", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Adjust for gully / box cricket (2–15)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { onPlayersChange(formState.playersPerTeam - 1) },
                                    enabled = formState.playersPerTeam > 2
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Decrease players")
                                }
                                Text(
                                    text = "${formState.playersPerTeam}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                                IconButton(
                                    onClick = { onPlayersChange(formState.playersPerTeam + 1) },
                                    enabled = formState.playersPerTeam < 15
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Increase players")
                                }
                            }
                        }
                    }
                }
            }

            // Wide / No-Ball & Advanced Match Rules Card
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAdvancedRules = !showAdvancedRules }
                                .testTag("advanced_rules_toggle"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Match rules",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text("Wide, No-Ball & Match Rules", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "Wide: ${if (formState.wideRuns > 0) "+${formState.wideRuns}r" else "0r (No Run)"} • NB: ${if (formState.noBallRuns > 0) "+${formState.noBallRuns}r" else "0r (No Run)"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                imageVector = if (showAdvancedRules) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (showAdvancedRules) "Collapse rules" else "Expand rules"
                            )
                        }

                        HorizontalDivider()

                        // Always-visible optional Wide & No-Ball run switches
                        RuleToggleRow(
                            title = "Run on Wide Ball (Optional)",
                            subtitle = if (formState.wideRuns > 0) "Adds +${formState.wideRuns} run penalty on Wide" else "0 penalty run on Wide (only re-ball)",
                            checked = formState.wideRuns > 0,
                            onCheckedChange = onWideRunsEnabledChange
                        )
                        RuleToggleRow(
                            title = "Run on No-Ball (Optional)",
                            subtitle = if (formState.noBallRuns > 0) "Adds +${formState.noBallRuns} run penalty on No-Ball" else "0 penalty run on No-Ball",
                            checked = formState.noBallRuns > 0,
                            onCheckedChange = onNoBallRunsEnabledChange
                        )

                        AnimatedVisibility(visible = showAdvancedRules) {
                            Column(
                                modifier = Modifier.padding(top = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                HorizontalDivider()
                                RuleToggleRow(
                                    title = "Wide Ball Re-ball",
                                    subtitle = "Extra delivery bowled on a wide",
                                    checked = formState.wideReball,
                                    onCheckedChange = onWideReballChange
                                )
                                if (formState.wideRuns > 0) {
                                    RuleStepperRow(
                                        title = "Wide Penalty Runs",
                                        value = formState.wideRuns,
                                        onDecrement = { onWideRunsChange((formState.wideRuns - 1).coerceAtLeast(1)) },
                                        onIncrement = { onWideRunsChange(formState.wideRuns + 1) }
                                    )
                                }
                                HorizontalDivider()
                                RuleToggleRow(
                                    title = "No Ball Re-ball",
                                    subtitle = "Extra delivery bowled on a no-ball",
                                    checked = formState.noBallReball,
                                    onCheckedChange = onNoBallReballChange
                                )
                                if (formState.noBallRuns > 0) {
                                    RuleStepperRow(
                                        title = "No-Ball Penalty Runs",
                                        value = formState.noBallRuns,
                                        onDecrement = { onNoBallRunsChange((formState.noBallRuns - 1).coerceAtLeast(1)) },
                                        onIncrement = { onNoBallRunsChange(formState.noBallRuns + 1) }
                                    )
                                }
                                HorizontalDivider()
                                RuleToggleRow(
                                    title = "Last Man Stands",
                                    subtitle = "Gully cricket rule: last batter can bat solo",
                                    checked = formState.lastManStands,
                                    onCheckedChange = onLastManChange
                                )
                            }
                        }
                    }
                }
            }

            // Start Match Button
            item {
                Button(
                    onClick = { showOpenersDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("start_match_button"),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Start Match ($batFirstTeam to Bat)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    if (showOpenersDialog) {
        val allSavedPlayerNames = allSavedPlayers.map { it.player.name }.distinct()
        val teamBatRoster = teams.firstOrNull { it.team.name.equals(batFirstTeam, ignoreCase = true) }
            ?.players?.map { it.name }.orEmpty()
        val teamBowlRoster = teams.firstOrNull { it.team.name.equals(bowlFirstTeam, ignoreCase = true) }
            ?.players?.map { it.name }.orEmpty()

        OpeningPlayersDialog(
            title = "Opening Players • 1st Innings",
            battingTeam = batFirstTeam,
            bowlingTeam = bowlFirstTeam,
            battingRoster = (teamBatRoster + allSavedPlayerNames).distinct(),
            bowlingRoster = (teamBowlRoster + allSavedPlayerNames).distinct(),
            allSavedPlayers = allSavedPlayers,
            onDismiss = { showOpenersDialog = false },
            onConfirm = { striker, nonStriker, bowler ->
                showOpenersDialog = false
                onStartMatchConfirmed(striker, nonStriker, bowler)
            }
        )
    }
}

@Composable
private fun RuleToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun RuleStepperRow(
    title: String,
    value: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDecrement, enabled = value > 0) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease $title")
            }
            Text("$value", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            IconButton(onClick = onIncrement, enabled = value < 4) {
                Icon(Icons.Default.Add, contentDescription = "Increase $title")
            }
        }
    }
}

@Composable
fun OpeningPlayersDialog(
    title: String,
    battingTeam: String,
    bowlingTeam: String,
    battingRoster: List<String>,
    bowlingRoster: List<String>,
    allSavedPlayers: List<PlayerWithTeam> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (striker: String, nonStriker: String, bowler: String) -> Unit
) {
    var striker by remember { mutableStateOf(battingRoster.getOrNull(0) ?: "") }
    var nonStriker by remember { mutableStateOf(battingRoster.getOrNull(1) ?: "") }
    var bowler by remember { mutableStateOf(bowlingRoster.getOrNull(0) ?: "") }
    // 0 = Striker, 1 = Non-Striker, 2 = Opening Bowler
    var activeSlot by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    // Build unified saved players list with team & role info, prioritizing matching team
    val displayPlayers = remember(allSavedPlayers, battingRoster, bowlingRoster, activeSlot, battingTeam, bowlingTeam, searchQuery) {
        val baseList = if (allSavedPlayers.isNotEmpty()) {
            allSavedPlayers.distinctBy { it.player.name.lowercase() }
        } else {
            (battingRoster + bowlingRoster).distinct().mapIndexed { idx, name ->
                val resolvedTeam = if (name in battingRoster) battingTeam else bowlingTeam
                PlayerWithTeam(
                    player = com.example.data.local.PlayerEntity(
                        id = idx.toLong(),
                        teamId = 0L,
                        name = name,
                        role = "Player"
                    ),
                    teamName = resolvedTeam,
                    teamShortCode = resolvedTeam.take(3).uppercase().ifBlank { "TM" }
                )
            }
        }
        val preferredTeam = if (activeSlot == 2) bowlingTeam else battingTeam
        val sorted = baseList.sortedWith(
            compareByDescending<PlayerWithTeam> { it.teamName.equals(preferredTeam, ignoreCase = true) }
                .thenBy { it.player.name.lowercase() }
        )
        val q = searchQuery.trim()
        if (q.isEmpty()) sorted
        else sorted.filter {
            it.player.name.contains(q, ignoreCase = true) ||
                it.teamName.contains(q, ignoreCase = true) ||
                it.player.role.contains(q, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.SportsCricket, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Active Slot Selector Chips
                Text(
                    text = "Select slot or type name below:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("1. Striker", "2. Non-Strk", "3. Bowler").forEachIndexed { idx, label ->
                        FilterChip(
                            selected = activeSlot == idx,
                            onClick = { activeSlot = idx },
                            label = {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("opener_slot_chip_$idx")
                        )
                    }
                }

                // 3 Opener Text Fields (Editable + synced with Saved Players tap selection)
                OutlinedTextField(
                    value = striker,
                    onValueChange = {
                        striker = it
                        activeSlot = 0
                    },
                    label = { Text("Striker ($battingTeam)") },
                    placeholder = { Text("Tap a saved player or type") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("opening_striker_input")
                )
                OutlinedTextField(
                    value = nonStriker,
                    onValueChange = {
                        nonStriker = it
                        activeSlot = 1
                    },
                    label = { Text("Non-Striker ($battingTeam)") },
                    placeholder = { Text("Tap a saved player or type") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("opening_non_striker_input")
                )
                OutlinedTextField(
                    value = bowler,
                    onValueChange = {
                        bowler = it
                        activeSlot = 2
                    },
                    label = { Text("Opening Bowler ($bowlingTeam)") },
                    placeholder = { Text("Tap a saved player or type") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("opening_bowler_input")
                )

                HorizontalDivider()

                // Saved Players Picker Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Saved Players (${displayPlayers.size})",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = when (activeSlot) {
                            0 -> "Picking Striker"
                            1 -> "Picking Non-Striker"
                            else -> "Picking Bowler"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                if (allSavedPlayers.size > 4) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search saved player...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("openers_search_saved_players")
                    )
                }

                if (displayPlayers.isEmpty()) {
                    Text(
                        text = "No saved players found. Add players in the Players tab or type opener names above.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            displayPlayers.forEach { pwt ->
                                val name = pwt.player.name
                                val assignedBadge = when {
                                    name.equals(striker.trim(), ignoreCase = true) -> "STRIKER"
                                    name.equals(nonStriker.trim(), ignoreCase = true) -> "NON-STRIKER"
                                    name.equals(bowler.trim(), ignoreCase = true) -> "BOWLER"
                                    else -> null
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (assignedBadge != null) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            when (activeSlot) {
                                                0 -> {
                                                    striker = name
                                                    if (nonStriker.equals(name, ignoreCase = true)) nonStriker = ""
                                                    activeSlot = 1
                                                }
                                                1 -> {
                                                    nonStriker = name
                                                    if (striker.equals(name, ignoreCase = true)) striker = ""
                                                    activeSlot = 2
                                                }
                                                else -> {
                                                    bowler = name
                                                }
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = if (assignedBadge != null) Icons.Default.CheckCircle else Icons.Default.Person,
                                                contentDescription = null,
                                                tint = if (assignedBadge != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Column {
                                                Text(
                                                    text = name,
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${pwt.teamName} • ${pwt.player.role}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        if (assignedBadge != null) {
                                            Surface(
                                                shape = RoundedCornerShape(999.dp),
                                                color = MaterialTheme.colorScheme.primary
                                            ) {
                                                Text(
                                                    text = assignedBadge,
                                                    color = Color.White,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
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
        },
        confirmButton = {
            Button(
                onClick = {
                    val s = striker.trim().ifEmpty { "Batter 1" }
                    val ns = nonStriker.trim().ifEmpty { "Batter 2" }
                    val bw = bowler.trim().ifEmpty { "Bowler 1" }
                    onConfirm(s, ns, bw)
                },
                modifier = Modifier.testTag("confirm_openers_button")
            ) {
                Text("Play Ball")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
