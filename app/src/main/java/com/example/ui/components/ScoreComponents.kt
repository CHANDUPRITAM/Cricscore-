package com.example.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.BallChip
import com.example.domain.CricketEngine
import com.example.domain.MatchSnapshot
import com.example.ui.theme.LocalCricketColors
import com.example.ui.theme.TabularStatStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BallChipBadge(
    chip: BallChip,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    val cricColors = LocalCricketColors.current
    val (bgColor, textColor, borderColor) = when {
        chip.isWicket -> Triple(cricColors.wicket, cricColors.onWicket, Color.Transparent)
        chip.isSix -> Triple(cricColors.boundarySix, cricColors.onBoundarySix, Color.Transparent)
        chip.isFour -> Triple(cricColors.boundaryFour, cricColors.onBoundaryFour, Color.Transparent)
        chip.isWide || chip.isNoBall || chip.isBye || chip.isLegBye ->
            Triple(cricColors.extra, cricColors.onExtra, Color.Transparent)
        chip.isDot -> Triple(cricColors.dotBall, cricColors.onDotBall, Color.Transparent)
        else -> Triple(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.onSurface,
            MaterialTheme.colorScheme.outline
        )
    }

    val isPill = chip.label.length > 2
    Surface(
        modifier = modifier
            .height(size)
            .widthIn(min = size),
        shape = if (isPill) RoundedCornerShape(size / 2) else CircleShape,
        color = bgColor,
        border = if (borderColor != Color.Transparent) BorderStroke(1.dp, borderColor) else null
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = if (isPill) 8.dp else 2.dp)
        ) {
            Text(
                text = chip.label,
                color = textColor,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = if (chip.label.length >= 4) 10.sp else 12.sp
                ),
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
fun TeamBadgeAvatar(
    teamName: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    size: Dp = 40.dp
) {
    val initials = teamName.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .take(2)
        .map { it.first().uppercaseChar() }
        .joinToString("")
        .ifEmpty { "CR" }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = contentColor,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            maxLines = 1
        )
    }
}

@Composable
fun LiveStatusPill(
    isLive: Boolean,
    label: String = if (isLive) "LIVE" else "COMPLETED",
    modifier: Modifier = Modifier
) {
    val cricColors = LocalCricketColors.current
    val bg = if (isLive) cricColors.wicket.copy(alpha = 0.14f) else MaterialTheme.colorScheme.primaryContainer
    val fg = if (isLive) cricColors.wicket else MaterialTheme.colorScheme.onPrimaryContainer
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = bg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (isLive) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(cricColors.livePulse)
                )
            }
            Text(
                text = label,
                color = fg,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
fun StatTableHeaderRow(
    firstLabel: String,
    columns: List<String>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = firstLabel,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        columns.forEach { col ->
            Text(
                text = col,
                modifier = Modifier.width(if (col == "SR" || col == "ER") 48.dp else 32.dp),
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun StatTableValueRow(
    name: String,
    subtitle: String? = null,
    isHighlighted: Boolean = false,
    values: List<String>,
    modifier: Modifier = Modifier
) {
    val bg = if (isHighlighted) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        Color.Transparent
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.SemiBold
                ),
                color = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        values.forEachIndexed { idx, value ->
            val isLastWide = idx == values.lastIndex
            Text(
                text = value,
                modifier = Modifier.width(if (isLastWide) 48.dp else 32.dp),
                textAlign = TextAlign.End,
                style = TabularStatStyle.copy(
                    fontWeight = if (idx == 0 || isHighlighted) FontWeight.Bold else FontWeight.Normal
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

fun shareMatchScorecard(context: Context, snapshot: MatchSnapshot) {
    val text = CricketEngine.formatShareableScorecard(snapshot)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "${snapshot.hostTeam} vs ${snapshot.visitorTeam} - Cricscore")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share Scorecard"))
}

fun sharePlayerCareer(context: Context, stats: com.example.domain.PlayerCareerStats) {
    val text = CricketEngine.formatShareablePlayerCareer(stats)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "${stats.playerName} Career Stats - Cricscore")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share Career Profile"))
}

fun formatMatchDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Composable
fun MadeByFooter(
    modifier: Modifier = Modifier,
    includeNavBarPadding: Boolean = false
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("made_by_footer"),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (includeNavBarPadding) Modifier.navigationBarsPadding() else Modifier)
                .padding(horizontal = 12.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(id = R.string.footer_made_by),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    letterSpacing = 0.6.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}
