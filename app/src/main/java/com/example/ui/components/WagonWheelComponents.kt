package com.example.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.ShotRecord
import com.example.ui.theme.LocalCricketColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

object CricketFieldZones {
    val orderedZones = listOf(
        "Third Man",
        "Point",
        "Covers",
        "Long Off",
        "Long On",
        "Mid-Wicket",
        "Square Leg",
        "Fine Leg"
    )

    /**
     * Maps normalized ground coordinates (x, y in -1f..1f where 0,0 is pitch center,
     * -Y is behind wicketkeeper, +Y is straight down the ground, +X is Off Side, -X is Leg Side)
     * into one of the 8 standard cricket fielding zones.
     */
    fun resolveZone(normX: Float, normY: Float): String {
        val deg = Math.toDegrees(atan2(normY.toDouble(), normX.toDouble()))
        return when {
            deg >= -90.0 && deg < -45.0 -> "Third Man"
            deg >= -45.0 && deg < 0.0 -> "Point"
            deg >= 0.0 && deg < 45.0 -> "Covers"
            deg >= 45.0 && deg < 90.0 -> "Long Off"
            deg >= 90.0 && deg < 135.0 -> "Long On"
            deg >= 135.0 && deg <= 180.0 -> "Mid-Wicket"
            deg >= -180.0 && deg < -135.0 -> "Square Leg"
            else -> "Fine Leg"
        }
    }

    /**
     * Snaps radius neatly so 4s reach the boundary rope, 6s clear the boundary rope,
     * and 1/2/3 runs sit naturally inside the outfield.
     */
    fun normalizeForRuns(rawX: Float, rawY: Float, runs: Int): Pair<Float, Float> {
        val dist = hypot(rawX.toDouble(), rawY.toDouble()).toFloat()
        if (dist < 0.05f) return 0f to 0.55f
        val ux = rawX / dist
        val uy = rawY / dist
        val targetR = when {
            runs >= 6 -> 0.96f
            runs == 4 -> 0.87f
            else -> dist.coerceIn(0.28f, 0.78f)
        }
        return (ux * targetR) to (uy * targetR)
    }

    /**
     * Computes shortest distance from normalized point (px, py) to shot line segment (0,0)->(sx,sy).
     */
    fun pointToShotSegmentDistance(px: Float, py: Float, sx: Float, sy: Float): Float {
        val lenSq = sx * sx + sy * sy
        if (lenSq < 0.0001f) return hypot((px - sx).toDouble(), (py - sy).toDouble()).toFloat()
        val t = ((px * sx + py * sy) / lenSq).coerceIn(0f, 1f)
        val projX = t * sx
        val projY = t * sy
        return hypot((px - projX).toDouble(), (py - projY).toDouble()).toFloat()
    }
}

private fun DrawScope.drawCricketFieldBase() {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val maxR = minOf(size.width, size.height) / 2f
    val outerFieldR = maxR * 0.96f
    val boundaryR = maxR * 0.87f
    val innerCircleR = maxR * 0.48f

    // Deep stadium apron
    drawCircle(
        color = Color(0xFF0A4D2C),
        radius = outerFieldR,
        center = Offset(cx, cy)
    )

    // Lush concentric mowed turf rings inside boundary
    drawCircle(
        color = Color(0xFF147A46),
        radius = boundaryR,
        center = Offset(cx, cy)
    )
    drawCircle(
        color = Color(0xFF198B51),
        radius = boundaryR * 0.78f,
        center = Offset(cx, cy)
    )
    drawCircle(
        color = Color(0xFF147A46),
        radius = boundaryR * 0.56f,
        center = Offset(cx, cy)
    )
    drawCircle(
        color = Color(0xFF1D9658),
        radius = innerCircleR * 0.72f,
        center = Offset(cx, cy)
    )

    // 8 subtle fielding sector divider lines
    for (i in 0 until 8) {
        val angleRad = Math.toRadians(i * 45.0)
        val endX = cx + cos(angleRad).toFloat() * boundaryR
        val endY = cy + sin(angleRad).toFloat() * boundaryR
        drawLine(
            color = Color.White.copy(alpha = 0.16f),
            start = Offset(cx, cy),
            end = Offset(endX, endY),
            strokeWidth = 1.dp.toPx()
        )
    }

    // 30-yard dashed inner circle
    drawCircle(
        color = Color.White.copy(alpha = 0.5f),
        radius = innerCircleR,
        center = Offset(cx, cy),
        style = Stroke(
            width = 1.2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
        )
    )

    // Crisp white boundary rope
    drawCircle(
        color = Color.White.copy(alpha = 0.92f),
        radius = boundaryR,
        center = Offset(cx, cy),
        style = Stroke(width = 2.2.dp.toPx())
    )

    // Central clay pitch rectangle
    val pitchW = maxR * 0.14f
    val pitchH = maxR * 0.34f
    drawRoundRect(
        color = Color(0xFFD9C08B),
        topLeft = Offset(cx - pitchW / 2f, cy - pitchH / 2f),
        size = Size(pitchW, pitchH),
        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
    )
    // Batting & Bowling crease lines
    drawLine(
        color = Color.White,
        start = Offset(cx - pitchW * 0.65f, cy - pitchH * 0.32f),
        end = Offset(cx + pitchW * 0.65f, cy - pitchH * 0.32f),
        strokeWidth = 1.3.dp.toPx()
    )
    drawLine(
        color = Color.White,
        start = Offset(cx - pitchW * 0.65f, cy + pitchH * 0.32f),
        end = Offset(cx + pitchW * 0.65f, cy + pitchH * 0.32f),
        strokeWidth = 1.3.dp.toPx()
    )

    // Batsman striker dot at pitch center
    drawCircle(
        color = Color(0xFF0A2E1D),
        radius = 4.5.dp.toPx(),
        center = Offset(cx, cy)
    )
    drawCircle(
        color = Color.White,
        radius = 2.4.dp.toPx(),
        center = Offset(cx, cy)
    )

    // Zone labels drawn cleanly on native canvas
    val labelPaint = Paint().apply {
        color = android.graphics.Color.WHITE
        alpha = 225
        textSize = (maxR * 0.082f).coerceIn(20f, 34f)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
        setShadowLayer(4f, 0f, 1.5f, android.graphics.Color.argb(170, 0, 0, 0))
    }

    val labelR = boundaryR * 0.72f
    val labelAngles = listOf(
        -67.5 to "Third Man",
        -22.5 to "Point",
        22.5 to "Covers",
        67.5 to "Long Off",
        112.5 to "Long On",
        157.5 to "Mid-Wkt",
        -157.5 to "Sq. Leg",
        -112.5 to "Fine Leg"
    )
    drawContext.canvas.nativeCanvas.apply {
        labelAngles.forEach { (deg, text) ->
            val rad = Math.toRadians(deg)
            val lx = cx + cos(rad).toFloat() * labelR
            val ly = cy + sin(rad).toFloat() * labelR + (labelPaint.textSize * 0.35f)
            drawText(text, lx, ly, labelPaint)
        }

        // Subtle LEG / OFF indicators
        val sidePaint = Paint(labelPaint).apply {
            textSize = (maxR * 0.064f).coerceIn(16f, 26f)
            color = android.graphics.Color.argb(190, 254, 243, 199)
        }
        drawText("LEG", cx - innerCircleR * 0.65f, cy + sidePaint.textSize * 0.35f, sidePaint)
        drawText("OFF", cx + innerCircleR * 0.65f, cy + sidePaint.textSize * 0.35f, sidePaint)
    }
}

/**
 * Draws a high-contrast animated shot trajectory beam with glow, white core,
 * directional arrowhead, and cricket ball marker at the destination.
 */
private fun DrawScope.drawShotTrajectoryLine(
    nx: Float,
    ny: Float,
    progress: Float,
    lineColor: Color,
    ballCoreColor: Color
) {
    val clampedProgress = progress.coerceIn(0.02f, 1f)
    val cx = size.width / 2f
    val cy = size.height / 2f
    val maxR = minOf(size.width, size.height) / 2f
    val start = Offset(cx, cy)
    val end = Offset(cx + nx * maxR * clampedProgress, cy + ny * maxR * clampedProgress)

    // Wide outer glow beam
    drawLine(
        color = lineColor.copy(alpha = 0.36f),
        start = start,
        end = end,
        strokeWidth = 11.dp.toPx(),
        cap = StrokeCap.Round
    )
    // Main vivid shot line
    drawLine(
        color = lineColor,
        start = start,
        end = end,
        strokeWidth = 4.4.dp.toPx(),
        cap = StrokeCap.Round
    )
    // Crisp bright inner core for maximum visibility over turf
    drawLine(
        color = Color.White.copy(alpha = 0.88f),
        start = start,
        end = end,
        strokeWidth = 1.6.dp.toPx(),
        cap = StrokeCap.Round
    )

    // Directional arrowhead near the tip once line has extended
    if (clampedProgress > 0.25f) {
        val angle = atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
        val arrowLen = 11.dp.toPx()
        val wingAngle = Math.toRadians(28.0)
        val tipPullBack = 7.dp.toPx()
        val arrowTip = Offset(
            end.x - cos(angle).toFloat() * tipPullBack,
            end.y - sin(angle).toFloat() * tipPullBack
        )
        val leftWing = Offset(
            arrowTip.x - cos(angle - wingAngle).toFloat() * arrowLen,
            arrowTip.y - sin(angle - wingAngle).toFloat() * arrowLen
        )
        val rightWing = Offset(
            arrowTip.x - cos(angle + wingAngle).toFloat() * arrowLen,
            arrowTip.y - sin(angle + wingAngle).toFloat() * arrowLen
        )
        drawLine(
            color = Color.White,
            start = arrowTip,
            end = leftWing,
            strokeWidth = 2.6.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White,
            start = arrowTip,
            end = rightWing,
            strokeWidth = 2.6.dp.toPx(),
            cap = StrokeCap.Round
        )
    }

    // Origin anchor highlight at pitch center
    drawCircle(
        color = lineColor,
        radius = 4.5.dp.toPx(),
        center = start
    )
    drawCircle(
        color = Color.White,
        radius = 2.2.dp.toPx(),
        center = start
    )

    // Endpoint glowing ball target
    if (clampedProgress > 0.12f) {
        drawCircle(
            color = lineColor.copy(alpha = 0.30f),
            radius = 15.dp.toPx(),
            center = end
        )
        drawCircle(
            color = Color.White,
            radius = 9.dp.toPx(),
            center = end
        )
        drawCircle(
            color = ballCoreColor,
            radius = 6.dp.toPx(),
            center = end
        )
    }
}

@Composable
fun ShotDirectionPickerDialog(
    runs: Int,
    batterName: String,
    onConfirmShot: (shotX: Float, shotY: Float, shotZone: String) -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit
) {
    val cricColors = LocalCricketColors.current
    val shotLineColor = when {
        runs >= 6 -> Color(0xFFFBBF24) // Vivid gold for Sixes
        runs == 4 -> Color(0xFF38BDF8) // Vivid sky blue for Fours
        else -> Color(0xFFFEF08A)      // Bright warm yellow-white for 1-3 runs
    }
    val ballCoreColor = when {
        runs >= 6 -> cricColors.boundarySix
        runs == 4 -> cricColors.boundaryFour
        else -> Color(0xFFDC2626)      // Classic red leather cricket ball dot
    }

    val scope = rememberCoroutineScope()
    var selectedPoint by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var selectedZone by remember { mutableStateOf<String?>(null) }
    var autoSaveOnTap by remember { mutableStateOf(false) }
    var isAutoSaving by remember { mutableStateOf(false) }
    var activeJob by remember { mutableStateOf<Job?>(null) }
    val lineProgress = remember { Animatable(0f) }

    fun handlePointSelection(offset: Offset, width: Int, height: Int, animateLine: Boolean, triggerAutoSave: Boolean) {
        val cx = width / 2f
        val cy = height / 2f
        val maxR = minOf(width, height) / 2f
        if (maxR <= 1f) return
        val rawX = ((offset.x - cx) / maxR).coerceIn(-1f, 1f)
        val rawY = ((offset.y - cy) / maxR).coerceIn(-1f, 1f)
        val (nx, ny) = CricketFieldZones.normalizeForRuns(rawX, rawY, runs)
        val zone = CricketFieldZones.resolveZone(nx, ny)

        selectedPoint = nx to ny
        selectedZone = zone

        activeJob?.cancel()
        isAutoSaving = false
        activeJob = scope.launch {
            if (animateLine) {
                lineProgress.snapTo(0f)
                lineProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                )
            } else {
                lineProgress.snapTo(1f)
            }

            if (triggerAutoSave) {
                isAutoSaving = true
                delay(480L)
                onConfirmShot(nx, ny, zone)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tap Ground • Shot Line",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "$batterName scored $runs ${if (runs == 1) "run" else "runs"} — tap or drag on ground",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = when {
                            runs >= 6 -> cricColors.boundarySix
                            runs == 4 -> cricColors.boundaryFour
                            else -> MaterialTheme.colorScheme.primary
                        }
                    ) {
                        Text(
                            text = if (runs == 4) "FOUR" else if (runs == 6) "SIX" else "+$runs",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }

                // Interactive Cricket Ground Canvas (supports both Tap & Drag with live line)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF06321C))
                        .testTag("shot_ground_canvas")
                        .pointerInput(runs, autoSaveOnTap) {
                            detectTapGestures { offset ->
                                handlePointSelection(
                                    offset = offset,
                                    width = size.width,
                                    height = size.height,
                                    animateLine = true,
                                    triggerAutoSave = autoSaveOnTap
                                )
                            }
                        }
                        .pointerInput(runs, autoSaveOnTap) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    handlePointSelection(
                                        offset = offset,
                                        width = size.width,
                                        height = size.height,
                                        animateLine = false,
                                        triggerAutoSave = false
                                    )
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    handlePointSelection(
                                        offset = change.position,
                                        width = size.width,
                                        height = size.height,
                                        animateLine = false,
                                        triggerAutoSave = false
                                    )
                                },
                                onDragEnd = {
                                    val pt = selectedPoint
                                    val z = selectedZone
                                    if (autoSaveOnTap && pt != null && z != null) {
                                        activeJob?.cancel()
                                        activeJob = scope.launch {
                                            isAutoSaving = true
                                            delay(420L)
                                            onConfirmShot(pt.first, pt.second, z)
                                        }
                                    }
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCricketFieldBase()

                        selectedPoint?.let { (nx, ny) ->
                            drawShotTrajectoryLine(
                                nx = nx,
                                ny = ny,
                                progress = lineProgress.value,
                                lineColor = shotLineColor,
                                ballCoreColor = ballCoreColor
                            )
                        }
                    }

                    // Live floating zone pill on top of the field after tapping
                    androidx.compose.animation.AnimatedVisibility(
                        visible = selectedZone != null,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color(0xDD062617),
                            shadowElevation = 4.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(shotLineColor)
                                )
                                Text(
                                    text = if (isAutoSaving) {
                                        "Saving ${selectedZone ?: ""}..."
                                    } else {
                                        "${selectedZone ?: ""} • $runs ${if (runs == 1) "Run" else "Runs"}"
                                    },
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }

                // Mode toggle & selected zone readout
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = autoSaveOnTap,
                        onClick = { autoSaveOnTap = !autoSaveOnTap },
                        leadingIcon = {
                            Icon(
                                imageVector = if (autoSaveOnTap) Icons.Default.Bolt else Icons.Default.MyLocation,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Text(
                                text = if (autoSaveOnTap) "Auto-Save ON" else "Tap & Confirm",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.testTag("instant_tap_toggle_chip")
                    )

                    Text(
                        text = selectedZone ?: "Tap ground to draw line",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                // Action Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            activeJob?.cancel()
                            onSkip()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("skip_shot_direction_btn")
                    ) {
                        Text("Skip Ground")
                    }
                    Button(
                        onClick = {
                            activeJob?.cancel()
                            val pt = selectedPoint
                            val z = selectedZone
                            if (pt != null && z != null) {
                                onConfirmShot(pt.first, pt.second, z)
                            }
                        },
                        enabled = selectedPoint != null,
                        modifier = Modifier
                            .weight(1.25f)
                            .heightIn(min = 48.dp)
                            .testTag("confirm_shot_direction_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(
                            text = if (selectedZone != null) "Save ($selectedZone)" else "Tap Field",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WagonWheelViewerCard(
    title: String,
    shots: List<ShotRecord>,
    modifier: Modifier = Modifier
) {
    val cricColors = LocalCricketColors.current
    // 0 = All, 1 = 1s/2s/3s, 2 = 4s, 3 = 6s
    var runFilter by remember { mutableIntStateOf(0) }
    var inspectedShotId by remember { mutableStateOf<Long?>(null) }

    val filteredShots = remember(shots, runFilter) {
        when (runFilter) {
            1 -> shots.filter { !it.isFour && !it.isSix }
            2 -> shots.filter { it.isFour }
            3 -> shots.filter { it.isSix }
            else -> shots
        }
    }

    val inspectedShot = remember(filteredShots, inspectedShotId) {
        filteredShots.firstOrNull { it.eventId == inspectedShotId }
    }

    val zoneBreakdown = remember(filteredShots) {
        filteredShots.groupBy { it.shotZone }
            .map { (zone, list) ->
                Triple(zone, list.sumOf { it.runs }, list.size)
            }
            .sortedByDescending { it.second }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
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
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "${filteredShots.size} shots • ${filteredShots.sumOf { it.runs }} runs",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Run type filter chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("All", "1-3s", "4s", "6s").forEachIndexed { idx, label ->
                    FilterChip(
                        selected = runFilter == idx,
                        onClick = {
                            runFilter = idx
                            inspectedShotId = null
                        },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            if (shots.isEmpty()) {
                Text(
                    text = "No shot directions recorded yet. Tap the cricket ground when scoring runs to build the Wagon Wheel.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                )
            } else {
                // Interactive Wagon Wheel Ground Canvas (tap any line to highlight & inspect ball)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF06321C))
                        .testTag("wagon_wheel_viewer_canvas")
                        .pointerInput(filteredShots) {
                            detectTapGestures { offset ->
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                val maxR = minOf(size.width, size.height) / 2f
                                if (maxR <= 1f || filteredShots.isEmpty()) return@detectTapGestures
                                val tapX = (offset.x - cx) / maxR
                                val tapY = (offset.y - cy) / maxR

                                val nearest = filteredShots.minByOrNull { shot ->
                                    CricketFieldZones.pointToShotSegmentDistance(
                                        px = tapX,
                                        py = tapY,
                                        sx = shot.shotX,
                                        sy = shot.shotY
                                    )
                                }
                                if (nearest != null) {
                                    val dist = CricketFieldZones.pointToShotSegmentDistance(
                                        px = tapX,
                                        py = tapY,
                                        sx = nearest.shotX,
                                        sy = nearest.shotY
                                    )
                                    inspectedShotId = if (dist <= 0.18f && inspectedShotId != nearest.eventId) {
                                        nearest.eventId
                                    } else {
                                        null
                                    }
                                }
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCricketFieldBase()

                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val maxR = minOf(size.width, size.height) / 2f
                        val hasSelection = inspectedShot != null

                        // Draw non-selected shots first
                        filteredShots.forEach { shot ->
                            if (shot.eventId == inspectedShot?.eventId) return@forEach
                            val lineColor = when {
                                shot.isSix -> Color(0xFFFBBF24)
                                shot.isFour -> Color(0xFF38BDF8)
                                else -> Color(0xFFE2FBE8)
                            }
                            val baseAlpha = when {
                                hasSelection -> 0.24f
                                shot.isFour || shot.isSix -> 0.94f
                                else -> 0.78f
                            }
                            val end = Offset(cx + shot.shotX * maxR, cy + shot.shotY * maxR)
                            drawLine(
                                color = lineColor.copy(alpha = baseAlpha),
                                start = Offset(cx, cy),
                                end = end,
                                strokeWidth = if (shot.isFour || shot.isSix) 3.dp.toPx() else 2.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                            drawCircle(
                                color = lineColor.copy(alpha = baseAlpha),
                                radius = if (shot.isFour || shot.isSix) 4.4.dp.toPx() else 3.2.dp.toPx(),
                                center = end
                            )
                        }

                        // Highlighted shot on top with full glowing beam & arrow
                        inspectedShot?.let { shot ->
                            val highlightColor = when {
                                shot.isSix -> Color(0xFFFBBF24)
                                shot.isFour -> Color(0xFF38BDF8)
                                else -> Color(0xFFFEF08A)
                            }
                            val coreColor = when {
                                shot.isSix -> cricColors.boundarySix
                                shot.isFour -> cricColors.boundaryFour
                                else -> Color(0xFFDC2626)
                            }
                            drawShotTrajectoryLine(
                                nx = shot.shotX,
                                ny = shot.shotY,
                                progress = 1f,
                                lineColor = highlightColor,
                                ballCoreColor = coreColor
                            )
                        }
                    }
                }

                // Selected Shot Ball Details Banner
                if (inspectedShot != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Over ${inspectedShot.overBallDisplay} • ${inspectedShot.runs} ${if (inspectedShot.runs == 1) "Run" else "Runs"} (${inspectedShot.shotZone})",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "${inspectedShot.batterName} vs ${inspectedShot.bowlerName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                )
                            }
                            IconButton(
                                onClick = { inspectedShotId = null },
                                modifier = Modifier.testTag("clear_inspected_shot_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear selected shot",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Tip: Tap any shot line on the ground to inspect that ball",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Color Legend
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ShotLegendDot("1s / 2s / 3s", Color(0xFF16A34A))
                    ShotLegendDot("Fours (4)", Color(0xFF38BDF8))
                    ShotLegendDot("Sixes (6)", Color(0xFFFBBF24))
                }

                // Zone Breakdown Bars
                if (zoneBreakdown.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = "Runs by Ground Zone",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    val maxZoneRuns = zoneBreakdown.maxOf { it.second }.coerceAtLeast(1)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        zoneBreakdown.forEach { (zone, runsInZone, count) ->
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = zone,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = "$runsInZone runs ($count ${if (count == 1) "shot" else "shots"})",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(7.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(fraction = (runsInZone.toFloat() / maxZoneRuns).coerceIn(0.08f, 1f))
                                            .height(7.dp)
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(MaterialTheme.colorScheme.primary)
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
private fun ShotLegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
