package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.repository.PlayerWithTeam
import com.example.domain.DismissalType
import com.example.domain.MatchSnapshot
import com.example.ui.components.PlayerFormDialog
import com.example.ui.components.SavedPlayersPickerSection
import com.example.ui.components.TeamBadgeAvatar
import com.example.ui.theme.LocalCricketColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WicketDialog(
    initialRuns: Int,
    striker: String,
    nonStriker: String,
    isLastWicket: Boolean,
    availableBatters: List<String>,
    fieldingRoster: List<String>,
    battingTeam: String = "",
    bowlingTeam: String = "",
    allSavedPlayers: List<PlayerWithTeam> = emptyList(),
    excludedBatters: Set<String> = emptySet(),
    onQuickAddPlayer: ((playerName: String, role: String, teamName: String, photoBase64: String) -> Unit)? = null,
    onDismiss: () -> Unit,
    onConfirm: (runsScored: Int, type: DismissalType, outBatter: String, fielder: String, newBatter: String) -> Unit
) {
    var selectedType by remember { mutableStateOf(DismissalType.BOWLED) }
    var outBatter by remember { mutableStateOf(striker) }
    var fielder by remember { mutableStateOf("") }
    var newBatter by remember { mutableStateOf(availableBatters.firstOrNull().orEmpty()) }
    var runs by remember { mutableIntStateOf(initialRuns) }
    var showQuickAddPlayer by remember { mutableStateOf(false) }

    val savedPhotoLookup = remember(allSavedPlayers) {
        allSavedPlayers.associate { it.player.name.trim().lowercase() to it.player.photoBase64 }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Fall of Wicket",
                style = MaterialTheme.typography.titleLarge.copy(color = LocalCricketColors.current.wicket)
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("How Out?", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    DismissalType.entries.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = {
                                selectedType = type
                                if (type != DismissalType.RUN_OUT && type != DismissalType.RETIRED_OUT) {
                                    outBatter = striker
                                }
                            },
                            label = { Text(type.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                if ((selectedType == DismissalType.RUN_OUT || selectedType == DismissalType.RETIRED_OUT) && nonStriker.isNotBlank()) {
                    Text("Who is Out?", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = outBatter == striker,
                            onClick = { outBatter = striker },
                            shape = SegmentedButtonDefaults.itemShape(0, 2)
                        ) {
                            Text(striker, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        SegmentedButton(
                            selected = outBatter == nonStriker,
                            onClick = { outBatter = nonStriker },
                            shape = SegmentedButtonDefaults.itemShape(1, 2)
                        ) {
                            Text(nonStriker, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                if (selectedType == DismissalType.RUN_OUT) {
                    Text("Runs completed on ball", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0, 1, 2, 3).forEach { r ->
                            FilterChip(
                                selected = runs == r,
                                onClick = { runs = r },
                                label = { Text("$r") }
                            )
                        }
                    }
                }

                if (selectedType.needsFielder) {
                    OutlinedTextField(
                        value = fielder,
                        onValueChange = { fielder = it },
                        label = { Text("Fielder / Keeper (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val allFielderChoices = (fieldingRoster + allSavedPlayers.map { it.player.name }).distinct()
                    if (allFielderChoices.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(allFielderChoices) { f ->
                                SuggestionChip(
                                    onClick = { fielder = f },
                                    icon = {
                                        TeamBadgeAvatar(
                                            teamName = f,
                                            photoBase64 = savedPhotoLookup[f.trim().lowercase()].orEmpty(),
                                            size = 20.dp
                                        )
                                    },
                                    label = { Text(f) }
                                )
                            }
                        }
                    }
                }

                if (!isLastWicket) {
                    OutlinedTextField(
                        value = newBatter,
                        onValueChange = { newBatter = it },
                        label = { Text("New Batsman") },
                        placeholder = { Text("Tap saved player or type new name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("wicket_new_batter_input")
                    )
                    SavedPlayersPickerSection(
                        title = "Saved Players",
                        preferredTeamName = battingTeam,
                        allSavedPlayers = allSavedPlayers,
                        fallbackNames = availableBatters,
                        excludedNames = excludedBatters,
                        selectedName = newBatter,
                        onSelectPlayer = { newBatter = it },
                        onQuickAddPlayerClick = if (onQuickAddPlayer != null) {
                            { showQuickAddPlayer = true }
                        } else null
                    )
                } else {
                    Text(
                        text = "This wicket ends the innings (All Out).",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = LocalCricketColors.current.wicket
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val incoming = if (isLastWicket) "" else newBatter.trim().ifEmpty { "Batter" }
                    onConfirm(runs, selectedType, outBatter, fielder.trim(), incoming)
                },
                colors = ButtonDefaults.buttonColors(containerColor = LocalCricketColors.current.wicket),
                modifier = Modifier.testTag("confirm_wicket_button")
            ) {
                Text("Confirm Out")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showQuickAddPlayer && onQuickAddPlayer != null) {
        PlayerFormDialog(
            title = "Save New Batter",
            initialName = "",
            initialRole = "Batter",
            initialTeamName = battingTeam.ifBlank { "My Squad" },
            initialPhotoBase64 = "",
            existingTeamNames = listOf(battingTeam, bowlingTeam).filter { it.isNotBlank() }.distinct(),
            onDismiss = { showQuickAddPlayer = false },
            onConfirm = { pName, pRole, pTeam, pPhoto ->
                onQuickAddPlayer(pName, pRole, pTeam, pPhoto)
                newBatter = pName
                showQuickAddPlayer = false
            }
        )
    }
}

@Composable
fun NextPlayerPromptDialog(
    title: String,
    label: String,
    suggestions: List<String>,
    helperNote: String?,
    preferredTeamName: String = "",
    allSavedPlayers: List<PlayerWithTeam> = emptyList(),
    excludedNames: Set<String> = emptySet(),
    onQuickAddPlayer: ((playerName: String, role: String, teamName: String, photoBase64: String) -> Unit)? = null,
    allowDismiss: Boolean = false,
    onDismiss: () -> Unit = {},
    onConfirm: (String) -> Unit
) {
    var playerName by remember { mutableStateOf("") }
    var showQuickAddPlayer by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (allowDismiss) onDismiss() },
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!helperNote.isNullOrBlank()) {
                    Text(
                        text = helperNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = playerName,
                    onValueChange = { playerName = it },
                    label = { Text(label) },
                    placeholder = { Text("Tap saved player or type new name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("next_player_input")
                )
                SavedPlayersPickerSection(
                    title = "Saved Players",
                    preferredTeamName = preferredTeamName,
                    allSavedPlayers = allSavedPlayers,
                    fallbackNames = suggestions,
                    excludedNames = excludedNames,
                    selectedName = playerName,
                    onSelectPlayer = { playerName = it },
                    onQuickAddPlayerClick = if (onQuickAddPlayer != null) {
                        { showQuickAddPlayer = true }
                    } else null
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clean = playerName.trim().ifEmpty { "Player" }
                    onConfirm(clean)
                },
                modifier = Modifier.testTag("confirm_next_player_button")
            ) {
                Text("Continue")
            }
        },
        dismissButton = if (allowDismiss) {
            { TextButton(onClick = onDismiss) { Text("Cancel") } }
        } else null
    )

    if (showQuickAddPlayer && onQuickAddPlayer != null) {
        PlayerFormDialog(
            title = "Save New Player",
            initialName = "",
            initialRole = if (label.contains("Bowler", ignoreCase = true)) "Bowler" else "Batter",
            initialTeamName = preferredTeamName.ifBlank { "My Squad" },
            initialPhotoBase64 = "",
            existingTeamNames = listOf(preferredTeamName).filter { it.isNotBlank() },
            onDismiss = { showQuickAddPlayer = false },
            onConfirm = { pName, pRole, pTeam, pPhoto ->
                onQuickAddPlayer(pName, pRole, pTeam, pPhoto)
                playerName = pName
                showQuickAddPlayer = false
            }
        )
    }
}

@Composable
fun RetireBatterDialog(
    striker: String,
    nonStriker: String,
    suggestions: List<String>,
    battingTeam: String = "",
    allSavedPlayers: List<PlayerWithTeam> = emptyList(),
    excludedNames: Set<String> = emptySet(),
    onQuickAddPlayer: ((playerName: String, role: String, teamName: String, photoBase64: String) -> Unit)? = null,
    onDismiss: () -> Unit,
    onConfirm: (retiring: String, replacement: String) -> Unit
) {
    var selectedRetiring by remember { mutableStateOf(striker) }
    var replacement by remember { mutableStateOf(suggestions.firstOrNull().orEmpty()) }
    var showQuickAddPlayer by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Retire Batsman (Hurt)") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (nonStriker.isNotBlank()) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = selectedRetiring == striker,
                            onClick = { selectedRetiring = striker },
                            shape = SegmentedButtonDefaults.itemShape(0, 2)
                        ) {
                            Text(striker, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        SegmentedButton(
                            selected = selectedRetiring == nonStriker,
                            onClick = { selectedRetiring = nonStriker },
                            shape = SegmentedButtonDefaults.itemShape(1, 2)
                        ) {
                            Text(nonStriker, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                OutlinedTextField(
                    value = replacement,
                    onValueChange = { replacement = it },
                    label = { Text("Replacement Batsman") },
                    placeholder = { Text("Tap saved player or type new name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                SavedPlayersPickerSection(
                    title = "Saved Players",
                    preferredTeamName = battingTeam,
                    allSavedPlayers = allSavedPlayers,
                    fallbackNames = suggestions,
                    excludedNames = excludedNames,
                    selectedName = replacement,
                    onSelectPlayer = { replacement = it },
                    onQuickAddPlayerClick = if (onQuickAddPlayer != null) {
                        { showQuickAddPlayer = true }
                    } else null
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(selectedRetiring, replacement.trim().ifEmpty { "Sub Batter" })
            }) { Text("Retire") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showQuickAddPlayer && onQuickAddPlayer != null) {
        PlayerFormDialog(
            title = "Save New Batter",
            initialName = "",
            initialRole = "Batter",
            initialTeamName = battingTeam.ifBlank { "My Squad" },
            initialPhotoBase64 = "",
            existingTeamNames = listOf(battingTeam).filter { it.isNotBlank() },
            onDismiss = { showQuickAddPlayer = false },
            onConfirm = { pName, pRole, pTeam, pPhoto ->
                onQuickAddPlayer(pName, pRole, pTeam, pPhoto)
                replacement = pName
                showQuickAddPlayer = false
            }
        )
    }
}

@Composable
fun CustomRunsDialog(
    onDismiss: () -> Unit,
    onSelectRuns: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Extra Runs") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("For overthrows or boundary extras (5, 7, 8 runs):", style = MaterialTheme.typography.bodySmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(5, 7, 8).forEach { r ->
                        Button(
                            onClick = { onSelectRuns(r) },
                            modifier = Modifier.weight(1f)
                        ) { Text("$r") }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun EditOversDialog(
    currentOvers: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var text by remember { mutableStateOf(currentOvers.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Match Overs") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { ch -> ch.isDigit() }.take(2) },
                label = { Text("Total Overs per Innings") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = {
                val parsed = text.toIntOrNull()
                if (parsed != null && parsed in 1..99) onConfirm(parsed)
            }) { Text("Update") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onConfirm) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun MatchCompleteDialog(
    snapshot: MatchSnapshot,
    onViewScorecard: () -> Unit,
    onShare: () -> Unit,
    onDeleteMatch: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_cricket_trophy_1795402434544),
                    contentDescription = "Championship trophy",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Match Complete!",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = snapshot.resultSummary,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "${snapshot.innings1.battingTeam}: ${snapshot.innings1.totalRuns}/${snapshot.innings1.wickets} (${snapshot.innings1.oversDisplay})\n" +
                        "${snapshot.innings2.battingTeam}: ${snapshot.innings2.totalRuns}/${snapshot.innings2.wickets} (${snapshot.innings2.oversDisplay})",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                snapshot.playerOfMatch?.let { potm ->
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.EmojiEvents,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Column {
                                Text(
                                    text = "Player of the Match",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    text = "${potm.playerName} • ${potm.summary}",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }
                if (onDeleteMatch != null) {
                    TextButton(
                        onClick = onDeleteMatch,
                        modifier = Modifier.testTag("match_complete_delete_btn")
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.padding(horizontal = 3.dp))
                        Text("Delete Match", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onViewScorecard, modifier = Modifier.testTag("match_complete_scorecard_btn")) {
                Text("Scorecard")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onShare) {
                Text("Share")
            }
        }
    )
}

@Composable
fun LiveMatchRulesDialog(
    initialWideReball: Boolean,
    initialWideRuns: Int,
    initialNoBallReball: Boolean,
    initialNoBallRuns: Int,
    initialLastManStands: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        wideReball: Boolean,
        wideRuns: Int,
        noBallReball: Boolean,
        noBallRuns: Int,
        lastManStands: Boolean
    ) -> Unit
) {
    var wideReball by remember { mutableStateOf(initialWideReball) }
    var wideRunsEnabled by remember { mutableStateOf(initialWideRuns > 0) }
    var wideRuns by remember { mutableIntStateOf(initialWideRuns.coerceAtLeast(1)) }
    var noBallReball by remember { mutableStateOf(initialNoBallReball) }
    var noBallRunsEnabled by remember { mutableStateOf(initialNoBallRuns > 0) }
    var noBallRuns by remember { mutableIntStateOf(initialNoBallRuns.coerceAtLeast(1)) }
    var lastManStands by remember { mutableStateOf(initialLastManStands) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wide & No-Ball Rules") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Make Wide or No-Ball penalty runs optional (ON = +1 run, OFF = 0 runs).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                DialogRuleSwitch("Run on Wide Ball", if (wideRunsEnabled) "+$wideRuns run penalty" else "0 runs (optional off)", wideRunsEnabled) {
                    wideRunsEnabled = it
                }
                DialogRuleSwitch("Wide Re-ball", "Bowl extra ball on wide", wideReball) { wideReball = it }
                DialogRuleSwitch("Run on No-Ball", if (noBallRunsEnabled) "+$noBallRuns run penalty" else "0 runs (optional off)", noBallRunsEnabled) {
                    noBallRunsEnabled = it
                }
                DialogRuleSwitch("No-Ball Re-ball", "Bowl extra ball on no-ball", noBallReball) { noBallReball = it }
                DialogRuleSwitch("Last Man Stands", "Last batter can bat solo", lastManStands) { lastManStands = it }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        wideReball,
                        if (wideRunsEnabled) wideRuns else 0,
                        noBallReball,
                        if (noBallRunsEnabled) noBallRuns else 0,
                        lastManStands
                    )
                },
                modifier = Modifier.testTag("save_live_rules_btn")
            ) { Text("Save Rules") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun DialogRuleSwitch(
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
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
