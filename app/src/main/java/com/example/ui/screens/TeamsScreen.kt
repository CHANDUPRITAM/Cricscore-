package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.PlayerEntity
import com.example.data.local.TeamEntity
import com.example.data.repository.PlayerWithTeam
import com.example.data.repository.TeamWithPlayers
import com.example.domain.PlayerCareerStats
import com.example.ui.components.CirclePhotoCropDialog
import com.example.ui.components.EditableProfileAvatar
import com.example.ui.components.PlayerFormDialog
import com.example.ui.components.ProfilePhotoUtils
import com.example.ui.components.TeamFormDialog
import com.example.ui.theme.TabularStatStyle
import kotlinx.coroutines.launch

private sealed interface QuickPhotoTarget {
    data class Team(val team: TeamEntity) : QuickPhotoTarget
    data class Player(val player: PlayerEntity, val teamName: String) : QuickPhotoTarget
}

@Composable
fun TeamsScreen(
    teams: List<TeamWithPlayers>,
    allPlayers: List<PlayerWithTeam>,
    playerStatsMap: Map<String, PlayerCareerStats>,
    onAddTeam: (name: String, shortCode: String, photoBase64: String) -> Unit,
    onRenameTeam: (TeamEntity, newName: String, newShortCode: String, newPhotoBase64: String) -> Unit,
    onUpdateTeamPhoto: (TeamEntity, photoBase64: String) -> Unit,
    onDeleteTeam: (Long) -> Unit,
    onAddPlayerToTeam: (teamId: Long, playerName: String, role: String, photoBase64: String) -> Unit,
    onAddPlayerByTeamName: (playerName: String, role: String, teamName: String, photoBase64: String) -> Unit,
    onUpdatePlayer: (player: PlayerEntity, newName: String, newRole: String, newTeamName: String, newPhotoBase64: String) -> Unit,
    onUpdatePlayerPhoto: (player: PlayerEntity, teamName: String, photoBase64: String) -> Unit,
    onDeletePlayer: (Long) -> Unit,
    onOpenPlayerProfile: (playerName: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // 0 = Players (dedicated view with Add Player & Career Stats), 1 = Teams
    var activeSegment by rememberSaveable { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var roleFilter by remember { mutableStateOf("All") }

    var expandedTeamId by remember { mutableStateOf<Long?>(null) }
    var showAddTeamDialog by remember { mutableStateOf(false) }
    var showQuickAddPlayerDialog by remember { mutableStateOf(false) }
    var editingTeam by remember { mutableStateOf<TeamEntity?>(null) }
    var addPlayerForTeam by remember { mutableStateOf<TeamEntity?>(null) }
    var editingPlayer by remember { mutableStateOf<PlayerWithTeam?>(null) }
    var teamToDelete by remember { mutableStateOf<TeamEntity?>(null) }
    var playerToDelete by remember { mutableStateOf<PlayerWithTeam?>(null) }

    var quickPhotoTarget by remember { mutableStateOf<QuickPhotoTarget?>(null) }
    var pendingQuickCrop by remember { mutableStateOf<Pair<QuickPhotoTarget, Uri>?>(null) }
    val compressingTeamId: Long? = null
    val compressingPlayerId: Long? = null

    val quickPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val target = quickPhotoTarget
        quickPhotoTarget = null
        if (uri != null && target != null) {
            pendingQuickCrop = target to uri
        }
    }

    val filteredPlayers = remember(allPlayers, searchQuery, roleFilter) {
        allPlayers.filter { pwt ->
            val matchesQuery = searchQuery.isBlank() ||
                pwt.player.name.contains(searchQuery.trim(), ignoreCase = true) ||
                pwt.teamName.contains(searchQuery.trim(), ignoreCase = true)
            val matchesRole = roleFilter == "All" || pwt.player.role.equals(roleFilter, ignoreCase = true)
            matchesQuery && matchesRole
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
            ) {
                // Players / Teams Segmented Header
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    SegmentedButton(
                        selected = activeSegment == 0,
                        onClick = { activeSegment = 0 },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        modifier = Modifier.testTag("segment_players")
                    ) {
                        Text("Players (${allPlayers.size})")
                    }
                    SegmentedButton(
                        selected = activeSegment == 1,
                        onClick = { activeSegment = 1 },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        modifier = Modifier.testTag("segment_teams")
                    ) {
                        Text("Teams (${teams.size})")
                    }
                }

                if (activeSegment == 0) {
                    // ---- DEDICATED PLAYERS TAB ----
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search players or team...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search players") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .testTag("search_players_input")
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(listOf("All", "Batter", "Bowler", "All-Rounder", "WK-Batter")) { role ->
                            FilterChip(
                                selected = roleFilter == role,
                                onClick = { roleFilter = role },
                                label = { Text(role) }
                            )
                        }
                    }

                    if (filteredPlayers.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PersonAdd,
                                contentDescription = null,
                                modifier = Modifier.size(60.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (allPlayers.isEmpty()) "No Players Added Yet" else "No Matching Players",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Add your players here to quickly pick them in matches and track their complete batting & bowling career stats.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            if (allPlayers.isEmpty()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { showQuickAddPlayerDialog = true }) {
                                    Icon(Icons.Default.PersonAdd, null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Add Your First Player")
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 92.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filteredPlayers, key = { it.player.id }) { pwt ->
                                val stats = playerStatsMap[pwt.player.name.lowercase()]
                                PlayerDirectoryCard(
                                    playerWithTeam = pwt,
                                    stats = stats,
                                    isCompressingPhoto = compressingPlayerId == pwt.player.id,
                                    onPickPhoto = {
                                        quickPhotoTarget = QuickPhotoTarget.Player(pwt.player, pwt.teamName)
                                        quickPhotoLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    onOpenCareer = { onOpenPlayerProfile(pwt.player.name) },
                                    onEdit = { editingPlayer = pwt },
                                    onDelete = { playerToDelete = pwt }
                                )
                            }
                        }
                    }
                } else {
                    // ---- TEAMS TAB ----
                    if (teams.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Groups,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text("No Saved Teams Yet", style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Create teams and squad rosters here, or they will be saved automatically when you start a match.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { showAddTeamDialog = true }) {
                                Icon(Icons.Default.Add, null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Create Team")
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 92.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(teams, key = { it.team.id }) { twp ->
                                val isExpanded = expandedTeamId == twp.team.id
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.medium,
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                ) {
                                    Column {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    expandedTeamId = if (isExpanded) null else twp.team.id
                                                }
                                                .padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                EditableProfileAvatar(
                                                    label = twp.team.name,
                                                    photoBase64 = twp.team.photoBase64,
                                                    isCompressing = compressingTeamId == twp.team.id,
                                                    size = 46.dp,
                                                    onClick = {
                                                        quickPhotoTarget = QuickPhotoTarget.Team(twp.team)
                                                        quickPhotoLauncher.launch(
                                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                        )
                                                    },
                                                    modifier = Modifier.testTag("team_avatar_picker_${twp.team.id}")
                                                )
                                                Column {
                                                    Text(
                                                        text = twp.team.name,
                                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                                    )
                                                    Text(
                                                        text = "${twp.team.shortCode} • ${twp.players.size} players",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(onClick = { addPlayerForTeam = twp.team }) {
                                                    Icon(Icons.Default.PersonAdd, contentDescription = "Add player to ${twp.team.name}")
                                                }
                                                IconButton(onClick = { editingTeam = twp.team }) {
                                                    Icon(Icons.Default.Edit, contentDescription = "Edit team")
                                                }
                                                IconButton(onClick = { teamToDelete = twp.team }) {
                                                    Icon(
                                                        Icons.Default.DeleteOutline,
                                                        contentDescription = "Delete team",
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                                Icon(
                                                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                    contentDescription = if (isExpanded) "Collapse squad" else "Expand squad"
                                                )
                                            }
                                        }

                                        AnimatedVisibility(visible = isExpanded) {
                                            Column(modifier = Modifier.padding(bottom = 10.dp)) {
                                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                                if (twp.players.isEmpty()) {
                                                    Text(
                                                        text = "No players in roster yet. Tap the + icon above to add players.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(14.dp)
                                                    )
                                                } else {
                                                    twp.players.forEachIndexed { idx, player ->
                                                        if (idx > 0) HorizontalDivider(
                                                            modifier = Modifier.padding(horizontal = 14.dp),
                                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                                        )
                                                        val stats = playerStatsMap[player.name.lowercase()]
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clickable { onOpenPlayerProfile(player.name) }
                                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                        ) {
                                                            EditableProfileAvatar(
                                                                label = player.name,
                                                                photoBase64 = player.photoBase64,
                                                                isCompressing = compressingPlayerId == player.id,
                                                                size = 38.dp,
                                                                onClick = {
                                                                    quickPhotoTarget = QuickPhotoTarget.Player(player, twp.team.name)
                                                                    quickPhotoLauncher.launch(
                                                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                                    )
                                                                }
                                                            )
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Text(
                                                                    text = player.name,
                                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                                                )
                                                                Text(
                                                                    text = if (stats != null) {
                                                                        "${player.role} • ${stats.runsScored} runs, ${stats.wicketsTaken} wkts (${stats.matchesPlayed}m)"
                                                                    } else {
                                                                        "${player.role} • Tap for career stats"
                                                                    },
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                )
                                                            }
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                IconButton(
                                                                    onClick = { onDeletePlayer(player.id) },
                                                                    modifier = Modifier.size(36.dp)
                                                                ) {
                                                                    Icon(
                                                                        Icons.Default.DeleteOutline,
                                                                        contentDescription = "Remove ${player.name}",
                                                                        modifier = Modifier.size(18.dp),
                                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                                    )
                                                                }
                                                                Icon(
                                                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                                    contentDescription = "View career stats",
                                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                    }
                }
            }
        }

        // Snackbar for instant photo compression & save feedback
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 84.dp)
        )

        // Context-Aware Primary FAB: Add Player on Players tab, Add Team on Teams tab
        ExtendedFloatingActionButton(
            onClick = {
                if (activeSegment == 0) showQuickAddPlayerDialog = true else showAddTeamDialog = true
            },
            icon = {
                Icon(
                    imageVector = if (activeSegment == 0) Icons.Default.PersonAdd else Icons.Default.Add,
                    contentDescription = null
                )
            },
            text = { Text(if (activeSegment == 0) "Add Player" else "Add Team") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag(if (activeSegment == 0) "add_player_fab" else "add_team_fab")
        )
    }

    if (showQuickAddPlayerDialog) {
        PlayerFormDialog(
            title = "Add New Player",
            initialName = "",
            initialRole = "All-Rounder",
            initialTeamName = teams.firstOrNull()?.team?.name ?: "My Squad",
            initialPhotoBase64 = "",
            existingTeamNames = teams.map { it.team.name },
            onDismiss = { showQuickAddPlayerDialog = false },
            onConfirm = { name, role, teamName, photo ->
                onAddPlayerByTeamName(name, role, teamName, photo)
                showQuickAddPlayerDialog = false
            }
        )
    }

    editingPlayer?.let { pwt ->
        PlayerFormDialog(
            title = "Edit Player",
            initialName = pwt.player.name,
            initialRole = pwt.player.role,
            initialTeamName = pwt.teamName,
            initialPhotoBase64 = pwt.player.photoBase64,
            existingTeamNames = teams.map { it.team.name },
            onDismiss = { editingPlayer = null },
            onConfirm = { name, role, teamName, photo ->
                onUpdatePlayer(pwt.player, name, role, teamName, photo)
                editingPlayer = null
            }
        )
    }

    if (showAddTeamDialog) {
        TeamFormDialog(
            title = "Create Team",
            initialName = "",
            initialCode = "",
            initialPhotoBase64 = "",
            onDismiss = { showAddTeamDialog = false },
            onConfirm = { name, code, photo ->
                onAddTeam(name, code, photo)
                showAddTeamDialog = false
            }
        )
    }

    editingTeam?.let { t ->
        TeamFormDialog(
            title = "Edit Team",
            initialName = t.name,
            initialCode = t.shortCode,
            initialPhotoBase64 = t.photoBase64,
            onDismiss = { editingTeam = null },
            onConfirm = { name, code, photo ->
                onRenameTeam(t, name, code, photo)
                editingTeam = null
            }
        )
    }

    addPlayerForTeam?.let { t ->
        PlayerFormDialog(
            title = "Add Player to ${t.name}",
            initialName = "",
            initialRole = "All-Rounder",
            initialTeamName = t.name,
            initialPhotoBase64 = "",
            existingTeamNames = teams.map { it.team.name },
            lockTeam = true,
            onDismiss = { addPlayerForTeam = null },
            onConfirm = { playerName, role, _, photo ->
                onAddPlayerToTeam(t.id, playerName, role, photo)
                expandedTeamId = t.id
                addPlayerForTeam = null
            }
        )
    }

    teamToDelete?.let { t ->
        AlertDialog(
            onDismissRequest = { teamToDelete = null },
            title = { Text("Delete ${t.name}?") },
            text = { Text("This deletes the team and its saved squad list. Past match scorecards stay intact.") },
            confirmButton = {
                Button(onClick = {
                    onDeleteTeam(t.id)
                    teamToDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { teamToDelete = null }) { Text("Cancel") } }
        )
    }

    playerToDelete?.let { pwt ->
        AlertDialog(
            onDismissRequest = { playerToDelete = null },
            title = { Text("Remove ${pwt.player.name}?") },
            text = { Text("Remove this player from ${pwt.teamName}? Past match records will remain intact.") },
            confirmButton = {
                Button(onClick = {
                    onDeletePlayer(pwt.player.id)
                    playerToDelete = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { playerToDelete = null }) { Text("Cancel") } }
        )
    }

    pendingQuickCrop?.let { (target, cropUri) ->
        val cropTitle = when (target) {
            is QuickPhotoTarget.Team -> "Crop ${target.team.name} Photo"
            is QuickPhotoTarget.Player -> "Crop ${target.player.name} Photo"
        }
        CirclePhotoCropDialog(
            imageUri = cropUri,
            title = cropTitle,
            onDismiss = { pendingQuickCrop = null },
            onCropped = { compressed ->
                pendingQuickCrop = null
                scope.launch {
                    when (target) {
                        is QuickPhotoTarget.Team -> {
                            onUpdateTeamPhoto(target.team, compressed)
                            snackbarHostState.showSnackbar(
                                "${target.team.name} photo saved (${ProfilePhotoUtils.estimateKb(compressed)} KB)"
                            )
                        }
                        is QuickPhotoTarget.Player -> {
                            onUpdatePlayerPhoto(target.player, target.teamName, compressed)
                            snackbarHostState.showSnackbar(
                                "${target.player.name} photo saved (${ProfilePhotoUtils.estimateKb(compressed)} KB)"
                            )
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun PlayerDirectoryCard(
    playerWithTeam: PlayerWithTeam,
    stats: PlayerCareerStats?,
    isCompressingPhoto: Boolean,
    onPickPhoto: () -> Unit,
    onOpenCareer: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenCareer() }
            .testTag("player_card_${playerWithTeam.player.id}"),
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    EditableProfileAvatar(
                        label = playerWithTeam.player.name,
                        photoBase64 = playerWithTeam.player.photoBase64,
                        isCompressing = isCompressingPhoto,
                        size = 46.dp,
                        onClick = onPickPhoto,
                        modifier = Modifier.testTag("player_avatar_picker_${playerWithTeam.player.id}")
                    )
                    Column {
                        Text(
                            text = playerWithTeam.player.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${playerWithTeam.player.role} • ${playerWithTeam.teamName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit player", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Delete player",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Open career stats",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Mini Career Stat Strip
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MiniStatCell("MAT", "${stats?.matchesPlayed ?: 0}")
                    MiniStatCell("RUNS", "${stats?.runsScored ?: 0}")
                    MiniStatCell("HS", if (stats != null && stats.highestScore > 0) "${stats.highestScore}${if (stats.highestNotOut) "*" else ""}" else "-")
                    MiniStatCell("WKTS", "${stats?.wicketsTaken ?: 0}")
                    MiniStatCell("SR", if (stats != null && stats.ballsFaced > 0) "%.0f".format(stats.strikeRate) else "-")
                }
            }
        }
    }
}

@Composable
private fun MiniStatCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = TabularStatStyle.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

