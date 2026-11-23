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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.ui.components.LiveStatusPill
import com.example.ui.components.MadeByFooter
import com.example.ui.components.StatTableHeaderRow
import com.example.ui.components.StatTableValueRow
import com.example.ui.theme.LocalCricketColors
import com.example.ui.theme.ScoreboardHeroStyle
import com.example.viewmodel.LiveSpectatorViewModel
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

    var codeInput by remember(initialShareCode) { mutableStateOf(initialShareCode) }
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
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
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
                                    LiveSpectatorScoreboardContent(doc = liveDoc)
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
                                        }
                                    )
                                }
                            }
                        }
                        else -> Unit
                    }
                }
            }

            MadeByFooter(includeNavBarPadding = true)
        }
    }
}

@Composable
private fun LiveSpectatorScoreboardContent(doc: LiveMatchDoc) {
    val cricColors = LocalCricketColors.current
    val pulseTransition = rememberInfiniteTransition(label = "livePulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

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

                    Text(
                        text = "${doc.hostTeam} vs ${doc.visitorTeam}",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodySmall
                    )

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
                Text("At the Crease", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                if (doc.strikerName.isNotBlank()) {
                    LivePlayerStatRow("${doc.strikerName} *", doc.strikerStats, highlighted = true)
                }
                if (doc.nonStrikerName.isNotBlank() && doc.nonStrikerName != doc.strikerName) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    LivePlayerStatRow(doc.nonStrikerName, doc.nonStrikerStats, highlighted = false)
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
                    LivePlayerStatRow(doc.bowlerName, doc.bowlerStats, highlighted = false)
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

        // Live Batting Scorecard Table
        if (doc.battingCardLines.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    StatTableHeaderRow(firstLabel = "${doc.battingTeam.uppercase()} BATTING", columns = listOf("R", "B", "4s", "6s", "SR"))
                    doc.battingCardLines.forEachIndexed { idx, raw ->
                        val parts = raw.split("|")
                        if (parts.size >= 7) {
                            if (idx > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            StatTableValueRow(
                                name = parts[0],
                                subtitle = parts[6],
                                isHighlighted = parts[0].endsWith("*"),
                                values = listOf(parts[1], parts[2], parts[3], parts[4], parts[5])
                            )
                        }
                    }
                }
            }
        }

        // Live Bowling Scorecard Table
        if (doc.bowlingCardLines.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    StatTableHeaderRow(firstLabel = "${doc.bowlingTeam.uppercase()} BOWLING", columns = listOf("O", "M", "R", "W", "ER"))
                    doc.bowlingCardLines.forEachIndexed { idx, raw ->
                        val parts = raw.split("|")
                        if (parts.size >= 6) {
                            if (idx > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            StatTableValueRow(
                                name = parts[0],
                                values = listOf(parts[1], parts[2], parts[3], parts[4], parts[5])
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LivePlayerStatRow(name: String, stats: String, highlighted: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (highlighted) FontWeight.Bold else FontWeight.SemiBold,
                color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
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
    onToggleSharing: (Boolean) -> Unit
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
                IconButton(onClick = onCopyCode) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Match Code", modifier = Modifier.size(18.dp))
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

            Text(
                text = "${doc.hostTeam} vs ${doc.visitorTeam}",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
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

