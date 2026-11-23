package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.InningsState
import com.example.ui.theme.LocalCricketColors
import kotlin.math.max

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
