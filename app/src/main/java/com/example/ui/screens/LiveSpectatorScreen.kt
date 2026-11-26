package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.cloud.CloudUiState
import com.example.data.cloud.LiveMatchDoc
import com.example.data.cloud.SpectatorInningsScorecard
import com.example.data.cloud.toInningsScorecard
import com.example.data.cloud.toInningsState
import com.example.data.cloud.toWinProbabilitySummary
import com.example.domain.InningsState
import com.example.ui.components.BallChipBadge
import com.example.ui.components.LiveStatusPill
import com.example.ui.components.MadeByFooter
import com.example.ui.components.ManhattanChartCard
import com.example.ui.components.RunBreakdownCard
import com.example.ui.components.StatTableHeaderRow
import com.example.ui.components.StatTableValueRow
import com.example.ui.components.TeamBadgeAvatar
import com.example.ui.components.WagonWheelViewerCard
import com.example.ui.components.WinProbabilityCard
import com.example.ui.components.WormGraphCard
import com.example.ui.theme.LocalCricketColors
import com.example.ui.theme.ScoreboardHeroStyle
import com.example.viewmodel.LiveSpectatorViewModel
import com.example.viewmodel.SpectatorSearchPlayerItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveSpectatorScreen(
    viewModel: LiveSpectatorViewModel,
    initialShareCode: String = "",
    isGuestSpectator: Boolean = false,
    onAdminLoginClick: (() -> Unit)? = null,
    onNavigateBack: () -> Unit
) {
    val activeShareCode by viewModel.activeShareCode.collectAsStateWithLifecycle()
    val watchedState by viewModel.watchedMatchState.collectAsStateWithLifecycle()
    val publicLiveMatchesState by viewModel.publicLiveMatches.collectAsStateWithLifecycle()
    val myBroadcastsState by viewModel.myBroadcasts.collectAsStateWithLifecycle()
    val codeInputError by viewModel.codeInputError.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    val selectedPlayerCareer by viewModel.selectedPlayerCareerState.collectAsStateWithLifecycle()
    val playerSearchQuery by viewModel.playerSearchQuery.collectAsStateWithLifecycle()
    val allSearchablePlayers by viewModel.allSearchablePlayers.collectAsStateWithLifecycle()
    val filteredSearchPlayers by viewModel.filteredSearchPlayers.collectAsStateWithLifecycle()

    var codeInput by remember(initialShareCode) { mutableStateOf(initialShareCode) }
    var selectedTopTab by rememberSaveable { mutableIntStateOf(0) }
    var selectedRoleFilter by rememberSaveable { mutableStateOf("All") }
    var broadcastToDelete by remember { mutableStateOf<LiveMatchDoc?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cricColors = LocalCricketColors.current

    LaunchedEffect(initialShareCode) {
        if (initialShareCode.isNotBlank()) {
            viewModel.watchMatchByCode(initialShareCode)
        }
    }

    LaunchedEffect(actionMessage) {
        actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearActionMessage()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (activeShareCode.isNotBlank()) "Live Match • $activeShareCode" else "Watch Live Score",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = if (isGuestSpectator) "Spectator Mode • No Login Needed" else "Real-time zero-lag push stream",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("spectator_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isGuestSpectator && onAdminLoginClick != null) {
                        Surface(
                            onClick = onAdminLoginClick,
                            shape = RoundedCornerShape(999.dp),
                            color = Color(0x33FFFFFF),
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .testTag("spectator_admin_login_btn")
                        ) {
                            Text(
                                text = "Admin Login",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                    if (activeShareCode.isNotBlank()) {
                        IconButton(
                            onClick = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "Watch live cricket score on Cricscore (No login required)!\nMatch Code: $activeShareCode"
                                    )
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share Match Code"))
                            },
                            modifier = Modifier.testTag("spectator_share_code_btn")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share Match Code")
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(
                selectedTabIndex = selectedTopTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTopTab == 0,
                    onClick = { selectedTopTab = 0 },
                    modifier = Modifier.testTag("spectator_mode_tab_live"),
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LiveTv,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Live Match",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (selectedTopTab == 0) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                )
                Tab(
                    selected = selectedTopTab == 1,
                    onClick = { selectedTopTab = 1 },
                    modifier = Modifier.testTag("spectator_mode_tab_players"),
                    text = {
                        val countLabel = if (allSearchablePlayers.isNotEmpty()) " (${allSearchablePlayers.size})" else ""
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Players Career$countLabel",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (selectedTopTab == 1) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                if (selectedTopTab == 0) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 680.dp),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Enter Match Code Card
                        item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LiveTv,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Join Live Match by Code",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = codeInput,
                                        onValueChange = { raw ->
                                            codeInput = raw.uppercase().filter { it.isLetterOrDigit() || it == '-' }.take(16)
                                        },
                                        label = { Text("Match Code (e.g. CS7K4M)") },
                                        singleLine = true,
                                        isError = codeInputError != null,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("spectator_code_input")
                                    )
                                    Button(
                                        onClick = { viewModel.watchMatchByCode(codeInput) },
                                        modifier = Modifier
                                            .height(54.dp)
                                            .testTag("watch_live_submit_btn")
                                    ) {
                                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Watch")
                                    }
                                }
                                if (codeInputError != null) {
                                    Text(
                                        text = codeInputError!!,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }

                    // Active Watched Live Match Stream
                    when (val state = watchedState) {
                        CloudUiState.Idle -> Unit
                        CloudUiState.Loading -> {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(20.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text("Connecting to live feed $activeShareCode...")
                                    }
                                }
                            }
                        }
                        is CloudUiState.Error -> {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = state.message,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        OutlinedButton(onClick = { viewModel.stopWatching() }) {
                                            Text("Clear")
                                        }
                                    }
                                }
                            }
                        }
                        is CloudUiState.Success -> {
                            val liveDoc = state.data
                            if (liveDoc == null) {
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(18.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "No live match found for code '$activeShareCode'",
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                            )
                                            Text(
                                                text = "Ask the Match Admin to tap 'Go Live' on the scoring screen and share the 6-character Match Code.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            } else {
                                item(key = "live_board_${liveDoc.shareCode}") {
                                    LiveSpectatorScoreboardContent(
                                        doc = liveDoc,
                                        onPlayerClick = viewModel::openPlayerCareer
                                    )
                                }
                            }
                        }
                    }

                    // Public Live Matches Feed (Available to Spectators WITHOUT Login)
                    when (val publicMatches = publicLiveMatchesState) {
                        CloudUiState.Loading -> {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Loading live matches...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        is CloudUiState.Success -> {
                            val list = publicMatches.data
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Live Matches Right Now",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "${list.size} active",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            if (list.isEmpty()) {
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Text(
                                            text = "No live matches are broadcasting right now. Enter a 6-character Match Code above or sign in as Admin to start a new live match.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(14.dp)
                                        )
                                    }
                                }
                            } else {
                                items(list, key = { "pub_${it.shareCode}" }) { doc ->
                                    PublicLiveMatchCard(
                                        doc = doc,
                                        isSelected = doc.shareCode == activeShareCode,
                                        onWatch = {
                                            codeInput = doc.shareCode
                                            viewModel.watchMatchByCode(doc.shareCode)
                                        }
                                    )
                                }
                            }
                        }
                        is CloudUiState.Error -> {
                            item {
                                Text(
                                    text = publicMatches.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        CloudUiState.Idle -> Unit
                    }

                    // Admin's Own Live Broadcasts Quick List
                    when (val broadcasts = myBroadcastsState) {
                        is CloudUiState.Success -> {
                            if (broadcasts.data.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "My Broadcasted Matches (Admin)",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                                items(broadcasts.data, key = { it.shareCode }) { doc ->
                                    AdminBroadcastItemCard(
                                        doc = doc,
                                        onWatch = {
                                            codeInput = doc.shareCode
                                            viewModel.watchMatchByCode(doc.shareCode)
                                        },
                                        onCopyCode = {
                                            clipboard.setText(AnnotatedString(doc.shareCode))
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Copied Match Code ${doc.shareCode}")
                                            }
                                        },
                                        onToggleSharing = { enabled ->
                                            viewModel.toggleBroadcastSharing(doc.shareCode, enabled)
                                        },
                                        onDelete = {
                                            broadcastToDelete = doc
                                        }
                                    )
                                }
                            }
                        }
                        else -> Unit
                    }
                }
                } else {
                    PlayersCareerDirectoryTabContent(
                        query = playerSearchQuery,
                        selectedRoleFilter = selectedRoleFilter,
                        allPlayersCount = allSearchablePlayers.size,
                        filteredPlayers = filteredSearchPlayers,
                        onQueryChange = viewModel::updatePlayerSearchQuery,
                        onRoleFilterChange = { selectedRoleFilter = it },
                        onPlayerSelect = { item ->
                            viewModel.openPlayerCareer(item.playerName, item.shareCode)
                        },
                        onGoToLiveMatchTab = { selectedTopTab = 0 }
                    )
                }
            }

            MadeByFooter(includeNavBarPadding = true)
        }
    }

    broadcastToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { broadcastToDelete = null },
            title = { Text("Delete Match Broadcast?") },
            text = {
                Text("Remove ${target.hostTeam} vs ${target.visitorTeam} (${target.shareCode}) from live broadcasts? Viewers will no longer see it.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteBroadcast(target.shareCode)
                        broadcastToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_broadcast_btn")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { broadcastToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    selectedPlayerCareer?.let { careerState ->
        SpectatorPlayerCareerSheet(
            state = careerState,
            onDismiss = viewModel::closePlayerCareer
        )
    }
}

@Composable
private fun PlayersCareerDirectoryTabContent(
    query: String,
    selectedRoleFilter: String,
    allPlayersCount: Int,
    filteredPlayers: List<SpectatorSearchPlayerItem>,
    onQueryChange: (String) -> Unit,
    onRoleFilterChange: (String) -> Unit,
    onPlayerSelect: (SpectatorSearchPlayerItem) -> Unit,
    onGoToLiveMatchTab: () -> Unit
) {
    val roleOptions = remember { listOf("All", "Batters", "Bowlers", "All-Rounders") }
    val roleFilteredPlayers = remember(filteredPlayers, selectedRoleFilter) {
        when (selectedRoleFilter) {
            "Batters" -> filteredPlayers.filter {
                it.role.contains("bat", ignoreCase = true) ||
                    it.role.contains("wk", ignoreCase = true) ||
                    it.role.contains("keeper", ignoreCase = true)
            }
            "Bowlers" -> filteredPlayers.filter { it.role.contains("bowl", ignoreCase = true) }
            "All-Rounders" -> filteredPlayers.filter { it.role.contains("all", ignoreCase = true) }
            else -> filteredPlayers
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 680.dp)
            .testTag("spectator_players_directory_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "players_search_filter_card") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("spectator_player_search_card"),
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "All Players Career",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = if (query.isBlank() && selectedRoleFilter == "All") {
                                    "$allPlayersCount players"
                                } else {
                                    "${roleFilteredPlayers.size} of $allPlayersCount"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        placeholder = { Text("Search player, team or role...") },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null)
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(
                                    onClick = { onQueryChange("") },
                                    modifier = Modifier.testTag("spectator_player_search_clear_btn")
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("spectator_player_search_input")
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(roleOptions) { option ->
                            FilterChip(
                                selected = selectedRoleFilter == option,
                                onClick = { onRoleFilterChange(option) },
                                label = { Text(option) }
                            )
                        }
                    }
                }
            }
        }

        if (allPlayersCount == 0) {
            item(key = "players_directory_empty_state") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "No players available yet",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Open or enter a live match code in the Live Match tab to browse all players and tap any name for full career records.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = onGoToLiveMatchTab) {
                            Icon(Icons.Default.LiveTv, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Go to Live Match")
                        }
                    }
                }
            }
        } else if (roleFilteredPlayers.isEmpty()) {
            item(key = "players_filter_empty_state") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No player matches your search or filter.",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        OutlinedButton(
                            onClick = {
                                onQueryChange("")
                                onRoleFilterChange("All")
                            }
                        ) {
                            Text("Show All Players")
                        }
                    }
                }
            }
        } else {
            items(
                items = roleFilteredPlayers,
                key = { "${it.shareCode}_${it.playerName}" }
            ) { item ->
                PlayerCareerDirectoryRowCard(
                    item = item,
                    onClick = { onPlayerSelect(item) }
                )
            }
        }
    }
}

@Composable
private fun PlayerCareerDirectoryRowCard(
    item: SpectatorSearchPlayerItem,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("spectator_player_list_item_${item.playerName}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TeamBadgeAvatar(
                teamName = item.playerName,
                photoBase64 = item.photoBase64,
                size = 46.dp
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.playerName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.isFromWatchedMatch) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "WATCHING",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = buildString {
                        append(item.teamName)
                        if (item.role.isNotBlank()) append(" • ${item.role}")
                        if (item.matchTitle.isNotBlank()) append(" • ${item.matchTitle}")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val statsBadgeText = when {
                    item.matchesPlayed > 0 || item.runsScored > 0 || item.wicketsTaken > 0 ->
                        "${item.matchesPlayed}M • ${item.runsScored} Runs • ${item.wicketsTaken} Wkts"
                    item.quickStatsSummary.isNotBlank() -> item.quickStatsSummary
                    else -> "Tap to view full career record"
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = statsBadgeText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "View ${item.playerName} career",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun LiveSpectatorScoreboardContent(
    doc: LiveMatchDoc,
    onPlayerClick: (String) -> Unit
) {
    val cricColors = LocalCricketColors.current
    val pulseTransition = rememberInfiniteTransition(label = "livePulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    var selectedTab by rememberSaveable(doc.shareCode) { mutableIntStateOf(0) }
    var selectedInnings by remember(doc.shareCode, doc.currentInningsNumber) {
        mutableIntStateOf(if (doc.currentInningsNumber >= 2L) 2 else 1)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Real-Time Hero Broadcast Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("spectator_live_scoreboard_card"),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(cricColors.headerGradientStart, cricColors.headerGradientEnd)
                        )
                    )
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .alpha(if (doc.matchStatus == "LIVE") pulseAlpha else 1f)
                                    .background(if (doc.matchStatus == "LIVE") Color(0xFF4ADE80) else Color(0xFFFBBF24))
                            )
                            Text(
                                text = when (doc.matchStatus) {
                                    "COMPLETED" -> "FINAL"
                                    "INNINGS_BREAK" -> "INNINGS BREAK"
                                    else -> "LIVE • ZERO LAG"
                                },
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold)
                            )
                        }
                        if (doc.lastBallBadge.isNotBlank()) {
                            AnimatedContent(targetState = "${doc.ballSequence}:${doc.lastBallBadge}", label = "lastBall") {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = when {
                                        doc.lastBallBadge.contains("W") && !doc.lastBallBadge.startsWith("WD") -> cricColors.wicket
                                        doc.lastBallBadge == "6" -> cricColors.boundarySix
                                        doc.lastBallBadge == "4" -> cricColors.boundaryFour
                                        else -> Color(0x38FFFFFF)
                                    }
                                ) {
                                    val zoneSuffix = if (doc.lastShotZone.isNotBlank()) " • ${doc.lastShotZone}" else ""
                                    Text(
                                        text = "LAST: ${doc.lastBallBadge}$zoneSuffix",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (doc.hostTeamPhoto.isNotBlank() || doc.visitorTeamPhoto.isNotBlank()) {
                            TeamBadgeAvatar(
                                teamName = doc.hostTeam,
                                photoBase64 = doc.hostTeamPhoto,
                                size = 28.dp,
                                containerColor = Color(0x33FFFFFF),
                                contentColor = Color.White
                            )
                        }
                        Text(
                            text = "${doc.hostTeam} vs ${doc.visitorTeam}",
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (doc.hostTeamPhoto.isNotBlank() || doc.visitorTeamPhoto.isNotBlank()) {
                            TeamBadgeAvatar(
                                teamName = doc.visitorTeam,
                                photoBase64 = doc.visitorTeamPhoto,
                                size = 28.dp,
                                containerColor = Color(0x33FFFFFF),
                                contentColor = Color.White
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${doc.battingTeam.uppercase()} • INN ${doc.currentInningsNumber}",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                ),
                                color = Color(0xFFA7F3D0)
                            )
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "${doc.scoreRuns}-${doc.scoreWickets}",
                                    style = ScoreboardHeroStyle.copy(color = Color.White),
                                    modifier = Modifier.testTag("spectator_score_text")
                                )
                                Text(
                                    text = "(${doc.oversDisplay}/${doc.totalOvers})",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        color = Color.White.copy(alpha = 0.88f),
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("CRR", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.72f))
                            Text(
                                text = doc.crr,
                                style = MaterialTheme.typography.titleLarge.copy(color = Color.White, fontWeight = FontWeight.Bold)
                            )
                            if (doc.rrr.isNotBlank()) {
                                Text(
                                    text = "RRR ${doc.rrr}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFFFDE68A)
                                )
                            }
                        }
                    }

                    val banner = doc.resultSummary.ifBlank { doc.chaseSummary.ifBlank { doc.tossSummary } }
                    if (banner.isNotBlank()) {
                        Surface(
                            color = Color(0x2BFFFFFF),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = banner,
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Text(
                        text = "Scorer Admin: ${doc.adminName} • Code: ${doc.shareCode}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }
            }
        }

        // Live / Scorecard / Overs / Shots / Graphs Tab Switcher for Spectators (matches Admin panel)
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab.coerceIn(0, 4),
                edgePadding = 6.dp
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Live", maxLines = 1) },
                    modifier = Modifier.testTag("spectator_tab_live")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Scorecard", maxLines = 1) },
                    modifier = Modifier.testTag("spectator_tab_scorecard")
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Overs", maxLines = 1) },
                    modifier = Modifier.testTag("spectator_tab_overs")
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("Shots", maxLines = 1) },
                    modifier = Modifier.testTag("spectator_tab_shots")
                )
                Tab(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    text = { Text("Graphs", maxLines = 1) },
                    modifier = Modifier.testTag("spectator_tab_graphs")
                )
            }
        }

        if (selectedTab == 0) {
            // Innings Summary Strip
            if (doc.innings1Summary.isNotBlank() || doc.innings2Summary.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (doc.innings1Summary.isNotBlank()) {
                            Text(doc.innings1Summary, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                        }
                        if (doc.innings2Summary.isNotBlank()) {
                            Text(doc.innings2Summary, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                        }
                    }
                }
            }

            // At the Crease & Current Bowler
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("At the Crease", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        Text(
                            text = "Tap player for career",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (doc.strikerName.isNotBlank()) {
                        LivePlayerStatRow(
                            name = "${doc.strikerName} *",
                            stats = doc.strikerStats,
                            highlighted = true,
                            onClick = { onPlayerClick(doc.strikerName) }
                        )
                    }
                    if (doc.nonStrikerName.isNotBlank() && doc.nonStrikerName != doc.strikerName) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        LivePlayerStatRow(
                            name = doc.nonStrikerName,
                            stats = doc.nonStrikerStats,
                            highlighted = false,
                            onClick = { onPlayerClick(doc.nonStrikerName) }
                        )
                    }
                    if (doc.partnershipSummary.isNotBlank() || doc.lastWicketSummary.isNotBlank()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (doc.partnershipSummary.isNotBlank()) {
                                Text(
                                    text = "P'ship: ${doc.partnershipSummary}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (doc.lastWicketSummary.isNotBlank()) {
                                Text(
                                    text = "Last Wkt: ${doc.lastWicketSummary}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    if (doc.bowlerName.isNotBlank()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text("Bowler", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LivePlayerStatRow(
                            name = doc.bowlerName,
                            stats = doc.bowlerStats,
                            highlighted = false,
                            onClick = { onPlayerClick(doc.bowlerName) }
                        )
                    }
                }
            }

            // This Over Timeline
            if (doc.currentOverBalls.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "This Over",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(doc.currentOverBalls) { ball ->
                                val bg = when {
                                    ball.contains("W") && !ball.startsWith("WD") -> cricColors.wicket
                                    ball == "6" -> cricColors.boundarySix
                                    ball == "4" -> cricColors.boundaryFour
                                    ball.startsWith("WD") || ball.startsWith("NB") -> cricColors.extra
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                                val fg = if (bg == MaterialTheme.colorScheme.surfaceVariant) MaterialTheme.colorScheme.onSurface else Color.White
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(bg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = ball,
                                        color = fg,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Real-time Win Probability Graph (CRR vs RRR)
            val winProbSummary = remember(
                doc.shareCode,
                doc.ballSequence,
                doc.analyticsLines,
                doc.crr,
                doc.rrr,
                doc.scoreRuns,
                doc.scoreWickets,
                doc.oversDisplay,
                doc.matchStatus
            ) {
                doc.toWinProbabilitySummary()
            }
            WinProbabilityCard(summary = winProbSummary, compact = true)

            // Recent Ball-by-Ball Feed
            if (doc.recentBalls.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Recent Balls", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        doc.recentBalls.take(8).forEachIndexed { idx, line ->
                            if (idx > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        } else when (selectedTab) {
            1 -> SpectatorFullScorecardContent(
                doc = doc,
                selectedInnings = selectedInnings,
                onSelectInnings = { selectedInnings = it },
                onPlayerClick = onPlayerClick
            )
            2 -> SpectatorOversTabContent(
                doc = doc,
                selectedInnings = selectedInnings,
                onSelectInnings = { selectedInnings = it },
                onPlayerClick = onPlayerClick
            )
            3 -> SpectatorShotsTabContent(
                doc = doc,
                selectedInnings = selectedInnings,
                onSelectInnings = { selectedInnings = it },
                onPlayerClick = onPlayerClick
            )
            else -> SpectatorGraphsTabContent(doc = doc)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpectatorFullScorecardContent(
    doc: LiveMatchDoc,
    selectedInnings: Int,
    onSelectInnings: (Int) -> Unit,
    onPlayerClick: (String) -> Unit
) {
    val innings1 = remember(doc) { doc.toInningsScorecard(1) }
    val innings2 = remember(doc) { doc.toInningsScorecard(2) }
    val activeInnings: SpectatorInningsScorecard = if (selectedInnings == 2 && innings2.isStarted) innings2 else innings1

    Column(
        modifier = Modifier.testTag("spectator_full_scorecard_content"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Result & Player of Match summary banner (matches Admin ScorecardScreen)
        if (doc.matchStatus == "COMPLETED" && doc.resultSummary.isNotBlank()) {
            val potmPlayerName = doc.potmSummary.substringBefore("(").trim()
            Card(
                modifier = if (potmPlayerName.isNotBlank()) {
                    Modifier.clickable { onPlayerClick(potmPlayerName) }
                } else Modifier,
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
                            text = doc.resultSummary,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        if (doc.potmSummary.isNotBlank()) {
                            Text(
                                text = "POTM: ${doc.potmSummary}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }
        }

        // Innings Switcher (when 2nd innings has started)
        if (innings2.isStarted) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = activeInnings.inningsNumber == 1,
                    onClick = { onSelectInnings(1) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    modifier = Modifier.testTag("spectator_innings_1_btn")
                ) {
                    Text(
                        "${innings1.battingTeam} (${innings1.totalRuns}/${innings1.wickets})",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                SegmentedButton(
                    selected = activeInnings.inningsNumber == 2,
                    onClick = { onSelectInnings(2) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    modifier = Modifier.testTag("spectator_innings_2_btn")
                ) {
                    Text(
                        "${innings2.battingTeam} (${innings2.totalRuns}/${innings2.wickets})",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Batting Table Card (with Header, Batters, Extras, and Total)
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

                if (activeInnings.batters.isEmpty()) {
                    Text(
                        text = "Batting scorecard will appear once the first ball is bowled.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                } else {
                    activeInnings.batters.forEachIndexed { idx, batter ->
                        if (idx > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        StatTableValueRow(
                            name = batter.name,
                            subtitle = batter.dismissalText,
                            isHighlighted = batter.isHighlighted,
                            values = listOf(
                                batter.runs,
                                batter.balls,
                                batter.fours,
                                batter.sixes,
                                batter.strikeRate
                            ),
                            onNameClick = { onPlayerClick(batter.name) }
                        )
                    }
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
                        text = activeInnings.extrasSummary,
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
                        text = "${activeInnings.totalRuns}/${activeInnings.wickets} (${activeInnings.oversDisplay} Ov, CRR ${activeInnings.crr})",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Fall of Wickets Card
        if (activeInnings.fallOfWickets.isNotEmpty()) {
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
                        activeInnings.fallOfWickets.forEach { fowLabel ->
                            val fowPlayer = fowLabel.substringAfter("(", "").substringBefore(",").trim()
                            AssistChip(
                                onClick = { if (fowPlayer.isNotBlank()) onPlayerClick(fowPlayer) },
                                label = {
                                    Text(
                                        fowLabel,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        // Bowling Table Card
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
                if (activeInnings.bowlers.isEmpty()) {
                    Text(
                        text = "Bowling figures will appear once the first ball is bowled.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                } else {
                    activeInnings.bowlers.forEachIndexed { idx, bw ->
                        if (idx > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        StatTableValueRow(
                            name = bw.name,
                            subtitle = bw.extrasSubtitle,
                            values = listOf(
                                bw.overs,
                                bw.maidens,
                                bw.runs,
                                bw.wickets,
                                bw.economy
                            ),
                            onNameClick = { onPlayerClick(bw.name) }
                        )
                    }
                }
            }
        }

        // Partnerships Card
        if (activeInnings.partnerships.isNotEmpty()) {
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
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${p.batter1} ${p.batter1Runs}(${p.batter1Balls})",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clickable { onPlayerClick(p.batter1) }
                                        .padding(vertical = 4.dp)
                                )
                                Text(
                                    text = "${p.totalRuns} (${p.totalBalls})${if (p.isUnbroken) "*" else ""}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${p.batter2} ${p.batter2Runs}(${p.batter2Balls})",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clickable { onPlayerClick(p.batter2) }
                                        .padding(vertical = 4.dp)
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

@Composable
private fun SpectatorInningsToggle(
    innings1: InningsState,
    innings2: InningsState,
    selectedInnings: Int,
    onSelectInnings: (Int) -> Unit
) {
    if (innings2.isStarted) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = selectedInnings == 1,
                onClick = { onSelectInnings(1) },
                shape = SegmentedButtonDefaults.itemShape(0, 2)
            ) {
                Text(
                    "${innings1.battingTeam} (${innings1.totalRuns}/${innings1.wickets})",
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
                    "${innings2.battingTeam} (${innings2.totalRuns}/${innings2.wickets})",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SpectatorOversTabContent(
    doc: LiveMatchDoc,
    selectedInnings: Int,
    onSelectInnings: (Int) -> Unit,
    onPlayerClick: (String) -> Unit
) {
    val innings1 = remember(doc) { doc.toInningsState(1) }
    val innings2 = remember(doc) { doc.toInningsState(2) }
    val inn = if (selectedInnings == 2 && innings2.isStarted) innings2 else innings1

    Column(
        modifier = Modifier.testTag("spectator_overs_content"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SpectatorInningsToggle(
            innings1 = innings1,
            innings2 = innings2,
            selectedInnings = inn.inningsNumber,
            onSelectInnings = onSelectInnings
        )

        if (inn.overs.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Text(
                    text = "Over-by-over details will appear once balls are bowled.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            inn.overs.reversed().forEach { over ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
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
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onPlayerClick(over.bowlerName) }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Over ${over.overNumber} • ",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = over.bowlerName,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
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
private fun SpectatorShotsTabContent(
    doc: LiveMatchDoc,
    selectedInnings: Int,
    onSelectInnings: (Int) -> Unit,
    onPlayerClick: (String) -> Unit
) {
    val innings1 = remember(doc) { doc.toInningsState(1) }
    val innings2 = remember(doc) { doc.toInningsState(2) }
    val inn = if (selectedInnings == 2 && innings2.isStarted) innings2 else innings1
    val scorecard = remember(doc, inn.inningsNumber) { doc.toInningsScorecard(inn.inningsNumber) }

    val batterNames = remember(scorecard, inn.shots) {
        val fromCard = scorecard.batters.map { it.name.removeSuffix(" *").trim() }
        val fromShots = inn.shots.map { it.batterName }
        (fromCard + fromShots).filter { it.isNotBlank() }.distinct()
    }
    var selectedBatterIdx by remember(inn.inningsNumber) { mutableIntStateOf(-1) }
    val activeBatterName = batterNames.getOrNull(selectedBatterIdx)
    val shotsToShow = remember(inn.shots, activeBatterName) {
        if (activeBatterName == null) inn.shots
        else inn.shots.filter { it.batterName.equals(activeBatterName, ignoreCase = true) }
    }

    Column(
        modifier = Modifier.testTag("spectator_shots_content"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SpectatorInningsToggle(
            innings1 = innings1,
            innings2 = innings2,
            selectedInnings = inn.inningsNumber,
            onSelectInnings = onSelectInnings
        )

        if (batterNames.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedBatterIdx == -1,
                        onClick = { selectedBatterIdx = -1 },
                        label = { Text("All Batters") }
                    )
                }
                items(batterNames.size) { idx ->
                    val name = batterNames[idx]
                    val batterRuns = inn.shots
                        .filter { it.batterName.equals(name, ignoreCase = true) }
                        .sumOf { it.runs }
                    FilterChip(
                        selected = selectedBatterIdx == idx,
                        onClick = { selectedBatterIdx = idx },
                        label = { Text("$name ($batterRuns)") }
                    )
                }
            }
        }

        if (activeBatterName != null) {
            AssistChip(
                onClick = { onPlayerClick(activeBatterName) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = { Text("Open $activeBatterName Career Record") }
            )
        }

        WagonWheelViewerCard(
            title = if (activeBatterName != null) "$activeBatterName • Shot Map" else "${inn.battingTeam} • Wagon Wheel",
            shots = shotsToShow
        )

        if (shotsToShow.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
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
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { onPlayerClick(shot.batterName) }
                                )
                                Text(
                                    text = "${shot.shotZone} • vs ${shot.bowlerName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.clickable { onPlayerClick(shot.bowlerName) }
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

@Composable
private fun SpectatorGraphsTabContent(doc: LiveMatchDoc) {
    val innings1 = remember(doc) { doc.toInningsState(1) }
    val innings2 = remember(doc) { doc.toInningsState(2) }
    val winProbSummary = remember(
        doc.shareCode,
        doc.ballSequence,
        doc.analyticsLines,
        doc.crr,
        doc.rrr,
        doc.scoreRuns,
        doc.scoreWickets,
        doc.oversDisplay,
        doc.matchStatus
    ) {
        doc.toWinProbabilitySummary()
    }

    Column(
        modifier = Modifier.testTag("spectator_graphs_content"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        WinProbabilityCard(summary = winProbSummary)
        ManhattanChartCard(innings1 = innings1, innings2 = innings2)
        WormGraphCard(innings1 = innings1, innings2 = innings2)
        if (innings1.isStarted) {
            RunBreakdownCard(innings = innings1)
        }
        if (innings2.isStarted) {
            RunBreakdownCard(innings = innings2)
        }
    }
}

@Composable
private fun LivePlayerStatRow(
    name: String,
    stats: String,
    highlighted: Boolean,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (highlighted || onClick != null) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (highlighted || onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (onClick != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Open player career",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Text(
            text = stats,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AdminBroadcastItemCard(
    doc: LiveMatchDoc,
    onWatch: () -> Unit,
    onCopyCode: () -> Unit,
    onToggleSharing: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onWatch() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    LiveStatusPill(isLive = doc.matchStatus == "LIVE", label = doc.matchStatus)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = doc.shareCode,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCopyCode) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Match Code", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("delete_broadcast_${doc.shareCode}")
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Delete broadcast",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Text(
                text = "${doc.hostTeam} vs ${doc.visitorTeam}",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "${doc.battingTeam}: ${doc.scoreRuns}/${doc.scoreWickets} (${doc.oversDisplay}/${doc.totalOvers})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.WifiTethering, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = if (doc.sharingEnabled) "Spectators can watch" else "Sharing paused",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = doc.sharingEnabled, onCheckedChange = onToggleSharing)
            }
        }
    }
}

@Composable
private fun PublicLiveMatchCard(
    doc: LiveMatchDoc,
    isSelected: Boolean,
    onWatch: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onWatch() }
            .testTag("public_live_match_${doc.shareCode}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LiveStatusPill(isLive = doc.matchStatus == "LIVE", label = doc.matchStatus)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = doc.shareCode,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Text(
                    text = if (isSelected) "Watching" else "Tap to Watch",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (doc.hostTeamPhoto.isNotBlank() || doc.visitorTeamPhoto.isNotBlank()) {
                    TeamBadgeAvatar(
                        teamName = doc.hostTeam,
                        photoBase64 = doc.hostTeamPhoto,
                        size = 28.dp
                    )
                }
                Text(
                    text = "${doc.hostTeam} vs ${doc.visitorTeam}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (doc.hostTeamPhoto.isNotBlank() || doc.visitorTeamPhoto.isNotBlank()) {
                    TeamBadgeAvatar(
                        teamName = doc.visitorTeam,
                        photoBase64 = doc.visitorTeamPhoto,
                        size = 28.dp
                    )
                }
            }
            Text(
                text = "${doc.battingTeam}: ${doc.scoreRuns}/${doc.scoreWickets} (${doc.oversDisplay}/${doc.totalOvers} ov) • CRR ${doc.crr}",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Admin: ${doc.adminName}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

