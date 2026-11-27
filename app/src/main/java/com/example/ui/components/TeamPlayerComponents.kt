package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.PlayerEntity
import com.example.data.repository.PlayerWithTeam

@Composable
fun ProfilePhotoPickerRow(
    label: String,
    photoBase64: String,
    isCompressing: Boolean,
    errorText: String?,
    onPickClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EditableProfileAvatar(
                label = label,
                photoBase64 = photoBase64,
                isCompressing = isCompressing,
                size = 58.dp,
                onClick = onPickClick
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onPickClick,
                        enabled = !isCompressing,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("dialog_choose_photo_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (photoBase64.isBlank()) "Choose Photo" else "Change Photo",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    if (photoBase64.isNotBlank()) {
                        TextButton(
                            onClick = onRemoveClick,
                            enabled = !isCompressing,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text("Remove", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                Text(
                    text = when {
                        isCompressing -> "Compressing photo..."
                        photoBase64.isNotBlank() -> "Compressed • ${ProfilePhotoUtils.estimateKb(photoBase64)} KB (cloud & live synced)"
                        else -> "Optional • Pick from gallery"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (errorText != null) {
            Text(
                text = errorText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun TeamFormDialog(
    title: String,
    initialName: String,
    initialCode: String,
    initialPhotoBase64: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, shortCode: String, photoBase64: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var code by remember { mutableStateOf(initialCode) }
    var photoBase64 by remember { mutableStateOf(initialPhotoBase64) }
    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }
    var photoError by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            photoError = null
            pendingCropUri = uri
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ProfilePhotoPickerRow(
                    label = name.ifBlank { "Team" },
                    photoBase64 = photoBase64,
                    isCompressing = false,
                    errorText = photoError,
                    onPickClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onRemoveClick = {
                        photoBase64 = ""
                        photoError = null
                    }
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Team Name") },
                    placeholder = { Text("e.g. Royal Strikers") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("team_name_dialog_input")
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.take(4).uppercase() },
                    label = { Text("Short Code (optional)") },
                    placeholder = { Text("e.g. RST") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("team_code_dialog_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim(), code.trim(), photoBase64) },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("confirm_save_team_btn")
            ) { Text("Save Team") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    pendingCropUri?.let { cropUri ->
        CirclePhotoCropDialog(
            imageUri = cropUri,
            title = "Crop Team Photo",
            onDismiss = { pendingCropUri = null },
            onCropped = { compressed ->
                photoBase64 = compressed
                pendingCropUri = null
            }
        )
    }
}

@Composable
fun PlayerFormDialog(
    title: String,
    initialName: String,
    initialRole: String,
    initialTeamName: String,
    initialPhotoBase64: String,
    existingTeamNames: List<String>,
    lockTeam: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (playerName: String, role: String, teamName: String, photoBase64: String) -> Unit
) {
    var playerName by remember { mutableStateOf(initialName) }
    var teamName by remember { mutableStateOf(initialTeamName) }
    var photoBase64 by remember { mutableStateOf(initialPhotoBase64) }
    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }
    var photoError by remember { mutableStateOf<String?>(null) }
    val roles = listOf("Batter", "Bowler", "All-Rounder", "WK-Batter")
    var selectedRole by remember { mutableStateOf(initialRole) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            photoError = null
            pendingCropUri = uri
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ProfilePhotoPickerRow(
                    label = playerName.ifBlank { "Player" },
                    photoBase64 = photoBase64,
                    isCompressing = false,
                    errorText = photoError,
                    onPickClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onRemoveClick = {
                        photoBase64 = ""
                        photoError = null
                    }
                )
                OutlinedTextField(
                    value = playerName,
                    onValueChange = { playerName = it },
                    label = { Text("Player Name") },
                    placeholder = { Text("e.g. Rohit Sharma") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("player_name_dialog_input")
                )

                if (!lockTeam) {
                    OutlinedTextField(
                        value = teamName,
                        onValueChange = { teamName = it },
                        label = { Text("Team Name (optional)") },
                        placeholder = { Text("e.g. My Squad") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("player_team_dialog_input")
                    )
                    if (existingTeamNames.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(existingTeamNames) { t ->
                                SuggestionChip(onClick = { teamName = t }, label = { Text(t) })
                            }
                        }
                    }
                }

                Text("Playing Role", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    roles.take(2).forEach { r ->
                        FilterChip(
                            selected = selectedRole == r,
                            onClick = { selectedRole = r },
                            label = { Text(r, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    roles.drop(2).forEach { r ->
                        FilterChip(
                            selected = selectedRole == r,
                            onClick = { selectedRole = r },
                            label = { Text(r, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (playerName.isNotBlank()) {
                        onConfirm(playerName.trim(), selectedRole, teamName.trim().ifEmpty { "My Squad" }, photoBase64)
                    }
                },
                enabled = playerName.isNotBlank(),
                modifier = Modifier.testTag("confirm_save_player_btn")
            ) { Text("Save Player") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    pendingCropUri?.let { cropUri ->
        CirclePhotoCropDialog(
            imageUri = cropUri,
            title = "Crop Player Photo",
            onDismiss = { pendingCropUri = null },
            onCropped = { compressed ->
                photoBase64 = compressed
                pendingCropUri = null
            }
        )
    }
}

@Composable
fun SavedPlayersPickerSection(
    title: String = "All Saved Players",
    preferredTeamName: String,
    allSavedPlayers: List<PlayerWithTeam>,
    fallbackNames: List<String> = emptyList(),
    excludedNames: Set<String> = emptySet(),
    selectedName: String = "",
    badgeForName: (String) -> String? = { name ->
        if (selectedName.isNotBlank() && name.equals(selectedName.trim(), ignoreCase = true)) "SELECTED" else null
    },
    onSelectPlayer: (String) -> Unit,
    onQuickAddPlayerClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var teamOnlyFilter by remember { mutableStateOf(false) }

    val unifiedPlayers = remember(allSavedPlayers, fallbackNames, excludedNames, preferredTeamName) {
        val merged = linkedMapOf<String, PlayerWithTeam>()
        allSavedPlayers.forEach { pwt ->
            val key = pwt.player.name.trim().lowercase()
            if (key.isNotEmpty() && !merged.containsKey(key)) {
                merged[key] = pwt
            } else if (key.isNotEmpty()) {
                val existing = merged[key]
                // Prefer entry matching preferredTeamName or with photo
                if (existing != null &&
                    !existing.teamName.equals(preferredTeamName, ignoreCase = true) &&
                    pwt.teamName.equals(preferredTeamName, ignoreCase = true)
                ) {
                    merged[key] = pwt
                }
            }
        }
        fallbackNames.forEachIndexed { idx, raw ->
            val clean = raw.trim()
            val key = clean.lowercase()
            if (clean.isNotEmpty() && !merged.containsKey(key)) {
                merged[key] = PlayerWithTeam(
                    player = PlayerEntity(
                        id = -(idx + 1L),
                        teamId = 0L,
                        name = clean,
                        role = "Player"
                    ),
                    teamName = preferredTeamName.ifBlank { "Match Roster" },
                    teamShortCode = preferredTeamName.take(3).uppercase().ifBlank { "PLY" }
                )
            }
        }
        merged.values
            .filter { pwt ->
                excludedNames.none { it.equals(pwt.player.name.trim(), ignoreCase = true) }
            }
            .sortedWith(
                compareByDescending<PlayerWithTeam> { it.teamName.equals(preferredTeamName, ignoreCase = true) }
                    .thenBy { it.player.name.lowercase() }
            )
    }

    val filteredPlayers = remember(unifiedPlayers, searchQuery, teamOnlyFilter, preferredTeamName) {
        val q = searchQuery.trim()
        unifiedPlayers.filter { pwt ->
            val matchesTeam = !teamOnlyFilter || pwt.teamName.equals(preferredTeamName, ignoreCase = true)
            val matchesQuery = q.isEmpty() ||
                pwt.player.name.contains(q, ignoreCase = true) ||
                pwt.teamName.contains(q, ignoreCase = true) ||
                pwt.player.role.contains(q, ignoreCase = true)
            matchesTeam && matchesQuery
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$title (${unifiedPlayers.size})",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            if (onQuickAddPlayerClick != null) {
                TextButton(
                    onClick = onQuickAddPlayerClick,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.testTag("quick_add_saved_player_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Player", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        if (preferredTeamName.isNotBlank() && unifiedPlayers.any { it.teamName.equals(preferredTeamName, ignoreCase = true) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = !teamOnlyFilter,
                    onClick = { teamOnlyFilter = false },
                    label = { Text("All Saved (${unifiedPlayers.size})", style = MaterialTheme.typography.labelSmall) }
                )
                FilterChip(
                    selected = teamOnlyFilter,
                    onClick = { teamOnlyFilter = true },
                    label = { Text("$preferredTeamName Squad", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )
            }
        }

        if (unifiedPlayers.size > 3) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search saved player...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("saved_players_picker_search")
            )
        }

        if (filteredPlayers.isEmpty()) {
            Text(
                text = if (unifiedPlayers.isEmpty()) {
                    "No saved players yet. Type any name above — it will be saved automatically for all live matches."
                } else {
                    "No matching saved players."
                },
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
                    .heightIn(max = 185.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    filteredPlayers.forEach { pwt ->
                        val name = pwt.player.name
                        val badge = badgeForName(name)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (badge != null) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectPlayer(name) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    TeamBadgeAvatar(
                                        teamName = name,
                                        photoBase64 = pwt.player.photoBase64,
                                        size = 32.dp
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
                                if (badge != null) {
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = badge,
                                                color = Color.White,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
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
