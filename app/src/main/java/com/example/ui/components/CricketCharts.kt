package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.InningsState
import com.example.domain.WinProbabilitySummary
import com.example.ui.theme.LocalCricketColors
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun ManhattanChartCard(
    innings1: InningsState,
    innings2: InningsState,
    modifier: Modifier = Modifier
) {
    val cricColors = LocalCricketColors.current
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    val overs1 = innings1.overs
    val overs2 = innings2.overs
    val maxOvers = max(overs1.size, overs2.size).coerceAtLeast(1)
    val maxRunsInOver = max(
        overs1.maxOfOrNull { it.runsInOver } ?: 0,
        overs2.maxOfOrNull { it.runsInOver } ?: 0
    ).coerceAtLeast(6)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Manhattan (Runs per Over)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            ChartLegendRow(
                team1 = innings1.battingTeam,
                team1Color = cricColors.teamOneChart,
                team2 = if (innings2.isStarted) innings2.battingTeam else null,
                team2Color = cricColors.teamTwoChart,
                wicketColor = cricColors.wicket
            )
            Spacer(modifier = Modifier.height(14.dp))

            if (overs1.isEmpty() && overs2.isEmpty()) {
                Text(
                    text = "Score at least one ball to view over-by-over Manhattan bars.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = labelColor
                )
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    val leftPad = 12.dp.toPx()
                    val topPad = 18.dp.toPx()
                    val bottomPad = 14.dp.toPx()
                    val chartWidth = size.width - leftPad * 2
                    val chartHeight = size.height - topPad - bottomPad

                    // Horizontal guide lines
                    for (i in 0..3) {
                        val y = topPad + (chartHeight / 3f) * i
                        drawLine(
                            color = gridColor,
                            start = Offset(leftPad, y),
                            end = Offset(size.width - leftPad, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    val groupWidth = chartWidth / maxOvers.toFloat()
                    val hasTwo = overs2.isNotEmpty()
                    val barWidth = if (hasTwo) (groupWidth * 0.36f).coerceIn(6f, 28f) else (groupWidth * 0.56f).coerceIn(8f, 36f)

                    for (overIdx in 0 until maxOvers) {
                        val groupCenter = leftPad + groupWidth * (overIdx + 0.5f)
                        val o1 = overs1.getOrNull(overIdx)
                        val o2 = overs2.getOrNull(overIdx)

                        if (o1 != null) {
                            val h = (o1.runsInOver.toFloat() / maxRunsInOver.toFloat()) * chartHeight
                            val x = if (hasTwo) groupCenter - barWidth - 2f else groupCenter - barWidth / 2f
                            val topY = topPad + chartHeight - h
                            drawRoundRect(
                                color = cricColors.teamOneChart,
                                topLeft = Offset(x, topY),
                                size = Size(barWidth, h.coerceAtLeast(3f)),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )
                            repeat(o1.wicketsInOver) { wIdx ->
                                drawCircle(
                                    color = cricColors.wicket,
                                    radius = 4.dp.toPx(),
                                    center = Offset(x + barWidth / 2f, (topY - 7.dp.toPx() - wIdx * 9.dp.toPx()).coerceAtLeast(4f))
                                )
                            }
                        }

                        if (o2 != null) {
                            val h = (o2.runsInOver.toFloat() / maxRunsInOver.toFloat()) * chartHeight
                            val x = groupCenter + 2f
                            val topY = topPad + chartHeight - h
                            drawRoundRect(
                                color = cricColors.teamTwoChart,
                                topLeft = Offset(x, topY),
                                size = Size(barWidth, h.coerceAtLeast(3f)),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )
                            repeat(o2.wicketsInOver) { wIdx ->
                                drawCircle(
                                    color = cricColors.wicket,
                                    radius = 4.dp.toPx(),
                                    center = Offset(x + barWidth / 2f, (topY - 7.dp.toPx() - wIdx * 9.dp.toPx()).coerceAtLeast(4f))
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Over 1", style = MaterialTheme.typography.labelSmall, color = labelColor)
                    Text("Peak: $maxRunsInOver runs/over", style = MaterialTheme.typography.labelSmall, color = labelColor)
                    Text("Over $maxOvers", style = MaterialTheme.typography.labelSmall, color = labelColor)
                }
            }
        }
    }
}

@Composable
fun WormGraphCard(
    innings1: InningsState,
    innings2: InningsState,
    modifier: Modifier = Modifier
) {
    val cricColors = LocalCricketColors.current
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    val overs1 = innings1.overs
    val overs2 = innings2.overs
    val maxOvers = max(overs1.size, overs2.size).coerceAtLeast(1)
    val maxScore = max(innings1.totalRuns, innings2.totalRuns).coerceAtLeast(10)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Worm Graph (Run Progression)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            ChartLegendRow(
                team1 = innings1.battingTeam,
                team1Color = cricColors.teamOneChart,
                team2 = if (innings2.isStarted) innings2.battingTeam else null,
                team2Color = cricColors.teamTwoChart,
                wicketColor = cricColors.wicket
            )
            Spacer(modifier = Modifier.height(14.dp))

            if (overs1.isEmpty() && overs2.isEmpty()) {
                Text(
                    text = "Worm progression line appears once overs begin.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = labelColor
                )
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    val pad = 14.dp.toPx()
                    val chartW = size.width - pad * 2
                    val chartH = size.height - pad * 2

                    for (i in 0..3) {
                        val y = pad + (chartH / 3f) * i
                        drawLine(
                            color = gridColor,
                            start = Offset(pad, y),
                            end = Offset(size.width - pad, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    fun drawWorm(overs: List<com.example.domain.OverSummary>, lineColor: Color) {
                        if (overs.isEmpty()) return
                        val path = Path().apply {
                            moveTo(pad, pad + chartH)
                        }
                        val points = mutableListOf<Pair<Offset, Boolean>>()
                        overs.forEachIndexed { idx, over ->
                            val x = pad + ((idx + 1).toFloat() / maxOvers.toFloat()) * chartW
                            val y = pad + chartH - (over.cumulativeRuns.toFloat() / maxScore.toFloat()) * chartH
                            path.lineTo(x, y)
                            points.add(Offset(x, y) to (over.wicketsInOver > 0))
                        }
                        drawPath(
                            path = path,
                            color = lineColor,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                        points.forEach { (pt, hasWicket) ->
                            drawCircle(
                                color = if (hasWicket) cricColors.wicket else lineColor,
                                radius = if (hasWicket) 5.dp.toPx() else 3.dp.toPx(),
                                center = pt
                            )
                        }
                    }

                    drawWorm(overs1, cricColors.teamOneChart)
                    drawWorm(overs2, cricColors.teamTwoChart)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("0 ov", style = MaterialTheme.typography.labelSmall, color = labelColor)
                    Text("Max: $maxScore runs", style = MaterialTheme.typography.labelSmall, color = labelColor)
                    Text("$maxOvers ov", style = MaterialTheme.typography.labelSmall, color = labelColor)
                }
            }
        }
    }
}

@Composable
fun RunBreakdownCard(
    innings: InningsState,
    modifier: Modifier = Modifier
) {
    val cricColors = LocalCricketColors.current
    val dist = innings.runDistribution
    val total = (dist.singlesTwosThreesRuns + dist.foursRuns + dist.sixesRuns + dist.extrasRuns).coerceAtLeast(1)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "${innings.battingTeam} Run Breakdown",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Stacked proportion bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                val segs = listOf(
                    dist.singlesTwosThreesRuns to MaterialTheme.colorScheme.primary,
                    dist.foursRuns to cricColors.boundaryFour,
                    dist.sixesRuns to cricColors.boundarySix,
                    dist.extrasRuns to cricColors.extra
                )
                segs.forEach { (value, color) ->
                    if (value > 0) {
                        Box(
                            modifier = Modifier
                                .weight(value.toFloat() / total.toFloat())
                                .height(14.dp)
                                .background(color)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BreakdownStatItem("1s/2s/3s", "${dist.singlesTwosThreesRuns}", MaterialTheme.colorScheme.primary)
                BreakdownStatItem("4s", "${dist.foursRuns}", cricColors.boundaryFour)
                BreakdownStatItem("6s", "${dist.sixesRuns}", cricColors.boundarySix)
                BreakdownStatItem("Extras", "${dist.extrasRuns}", cricColors.extra)
                BreakdownStatItem("Dots", "${dist.dots}", MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun BreakdownStatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
            Text(value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ChartLegendRow(
    team1: String,
    team1Color: Color,
    team2: String?,
    team2Color: Color,
    wicketColor: Color
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendDot(team1, team1Color)
        if (team2 != null) {
            LegendDot(team2, team2Color)
        }
        LegendDot("Wicket", wicketColor)
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun WinProbabilityCard(
    summary: WinProbabilitySummary,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val cricColors = LocalCricketColors.current
    val team1Color = cricColors.teamOneChart
    val team2Color = cricColors.teamTwoChart
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceColor = MaterialTheme.colorScheme.surface

    val animatedTeam1Fraction by animateFloatAsState(
        targetValue = (summary.team1WinProb / 100f).coerceIn(0.02f, 0.98f),
        animationSpec = tween(durationMillis = 350),
        label = "win_prob_split"
    )

    val points = summary.points
    var inspectedIndex by remember(points.size) { mutableIntStateOf(-1) }
    val activePoint = points.getOrNull(inspectedIndex) ?: points.lastOrNull()

    val modeBadge = when {
        summary.isMatchCompleted -> "FINAL"
        summary.isSecondInnings && summary.requiredRrr != null -> "CRR vs RRR"
        else -> "CRR vs PAR"
    }

    val rateSubtitle = if (summary.isSecondInnings && summary.requiredRrr != null) {
        "CRR ${"%.2f".format(summary.currentCrr)} • RRR ${"%.2f".format(summary.requiredRrr)}"
    } else {
        "CRR ${"%.2f".format(summary.currentCrr)} • Par ${"%.1f".format(summary.parRunRate)}"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("win_probability_card"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = surfaceColor)
    ) {
        Column(
            modifier = Modifier.padding(if (compact) 12.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Win Probability",
                        style = if (compact) MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        else MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = rateSubtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = modeBadge,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }

            // Two-team split percentage header + progress bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(team1Color))
                    Text(
                        text = summary.team1Name,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = "${summary.team1PercentInt}%",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = team1Color,
                        modifier = Modifier.testTag("win_prob_team1_pct")
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "${summary.team2PercentInt}%",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = team2Color,
                        modifier = Modifier.testTag("win_prob_team2_pct")
                    )
                    Text(
                        text = summary.team2Name,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(team2Color))
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(team2Color)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedTeam1Fraction)
                        .fillMaxHeight()
                        .background(team1Color)
                )
            }

            if (points.size < 2) {
                Text(
                    text = "Win probability line updates ball-by-ball once play begins.",
                    style = MaterialTheme.typography.bodySmall,
                    color = labelColor
                )
            } else {
                val chartHeight = if (compact) 118.dp else 160.dp
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(chartHeight)
                        .testTag("win_probability_line_chart")
                        .pointerInput(points) {
                            detectTapGestures { offset ->
                                val pad = 12.dp.toPx()
                                val chartW = (size.width - pad * 2f).coerceAtLeast(1f)
                                val frac = ((offset.x - pad) / chartW).coerceIn(0f, 1f)
                                val idx = (frac * (points.size - 1)).roundToInt().coerceIn(0, points.lastIndex)
                                inspectedIndex = if (inspectedIndex == idx) -1 else idx
                            }
                        }
                ) {
                    val padH = 12.dp.toPx()
                    val padV = 10.dp.toPx()
                    val chartW = (size.width - padH * 2f).coerceAtLeast(1f)
                    val chartH = (size.height - padV * 2f).coerceAtLeast(1f)
                    val midY = padV + chartH * 0.5f

                    // Quarter reference lines (75% & 25%)
                    listOf(0.25f, 0.75f).forEach { frac ->
                        val y = padV + chartH * frac
                        drawLine(
                            color = gridColor.copy(alpha = 0.55f),
                            start = Offset(padH, y),
                            end = Offset(size.width - padH, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // 50% equilibrium dashed midline
                    drawLine(
                        color = gridColor,
                        start = Offset(padH, midY),
                        end = Offset(size.width - padH, midY),
                        strokeWidth = 1.2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                    )

                    val coords = points.mapIndexed { idx, pt ->
                        val x = padH + (idx.toFloat() / (points.size - 1).coerceAtLeast(1).toFloat()) * chartW
                        val y = padV + (1f - (pt.team1WinProb / 100f).coerceIn(0f, 1f)) * chartH
                        Offset(x, y)
                    }

                    val linePath = Path().apply {
                        coords.forEachIndexed { idx, pt ->
                            if (idx == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                        }
                    }

                    val areaPath = Path().apply {
                        moveTo(coords.first().x, midY)
                        coords.forEach { lineTo(it.x, it.y) }
                        lineTo(coords.last().x, midY)
                        close()
                    }

                    // Top half fill (Team 1 favored > 50%)
                    clipRect(left = 0f, top = 0f, right = size.width, bottom = midY) {
                        drawPath(
                            path = areaPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(team1Color.copy(alpha = 0.28f), team1Color.copy(alpha = 0.04f)),
                                startY = padV,
                                endY = midY
                            )
                        )
                    }

                    // Bottom half fill (Team 2 favored < 50%)
                    clipRect(left = 0f, top = midY, right = size.width, bottom = size.height) {
                        drawPath(
                            path = areaPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(team2Color.copy(alpha = 0.04f), team2Color.copy(alpha = 0.28f)),
                                startY = midY,
                                endY = padV + chartH
                            )
                        )
                    }

                    val leadColor = if ((points.last().team1WinProb) >= 50f) team1Color else team2Color
                    drawPath(
                        path = linePath,
                        color = leadColor,
                        style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )

                    // Point markers (highlight wickets & active/inspected point)
                    coords.forEachIndexed { idx, ptOffset ->
                        val pt = points[idx]
                        val isLast = idx == coords.lastIndex
                        val isInspected = idx == inspectedIndex
                        when {
                            isInspected -> {
                                drawCircle(
                                    color = surfaceColor,
                                    radius = 5.5.dp.toPx(),
                                    center = ptOffset
                                )
                                drawCircle(
                                    color = if (pt.team1WinProb >= 50f) team1Color else team2Color,
                                    radius = 4.dp.toPx(),
                                    center = ptOffset
                                )
                            }
                            isLast -> {
                                drawCircle(
                                    color = leadColor.copy(alpha = 0.25f),
                                    radius = 7.dp.toPx(),
                                    center = ptOffset
                                )
                                drawCircle(
                                    color = leadColor,
                                    radius = 4.dp.toPx(),
                                    center = ptOffset
                                )
                            }
                            pt.hasWicket -> {
                                drawCircle(
                                    color = cricColors.wicket,
                                    radius = 3.2.dp.toPx(),
                                    center = ptOffset
                                )
                            }
                        }
                    }
                }

                // Bottom axis & tap-inspect readout
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = points.first().label,
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor
                    )
                    if (activePoint != null) {
                        val rrrStr = activePoint.rrr?.let { " • RRR ${"%.2f".format(it)}" }.orEmpty()
                        Text(
                            text = "${activePoint.label}: ${activePoint.team1WinProb.roundToInt()}% / ${activePoint.team2WinProb.roundToInt()}% • CRR ${"%.2f".format(activePoint.crr)}$rrrStr",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                    }
                    Text(
                        text = points.last().label,
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor
                    )
                }
            }
        }
    }
}

