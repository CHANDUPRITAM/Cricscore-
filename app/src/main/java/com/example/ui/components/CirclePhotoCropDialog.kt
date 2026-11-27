package com.example.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun CirclePhotoCropDialog(
    imageUri: Uri,
    title: String = "Crop Photo",
    onDismiss: () -> Unit,
    onCropped: (compressedBase64: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceBitmap by remember(imageUri) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(imageUri) { mutableStateOf(true) }
    var isSaving by remember(imageUri) { mutableStateOf(false) }
    var errorMessage by remember(imageUri) { mutableStateOf<String?>(null) }

    var viewportSizePx by remember { mutableFloatStateOf(0f) }
    var zoom by remember(imageUri) { mutableFloatStateOf(1f) }
    var panX by remember(imageUri) { mutableFloatStateOf(0f) }
    var panY by remember(imageUri) { mutableFloatStateOf(0f) }

    LaunchedEffect(imageUri) {
        isLoading = true
        errorMessage = null
        val loaded = ProfilePhotoUtils.loadPreviewBitmap(context, imageUri, maxDimPx = 1080)
        sourceBitmap = loaded
        isLoading = false
        if (loaded == null) {
            errorMessage = "Could not load selected image."
        }
    }

    fun clampPan(rawX: Float, rawY: Float, targetZoom: Float, bmp: Bitmap?, viewport: Float): Pair<Float, Float> {
        if (bmp == null || viewport <= 0f) return 0f to 0f
        val bmpW = bmp.width.toFloat()
        val bmpH = bmp.height.toFloat()
        val baseScale = max(viewport / bmpW, viewport / bmpH)
        val totalScale = baseScale * targetZoom.coerceIn(1f, 4f)
        val maxPanX = max(0f, (bmpW * totalScale - viewport) / 2f)
        val maxPanY = max(0f, (bmpH * totalScale - viewport) / 2f)
        return rawX.coerceIn(-maxPanX, maxPanX) to rawY.coerceIn(-maxPanY, maxPanY)
    }

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .widthIn(max = 440.dp)
                .testTag("circle_crop_photo_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Drag & zoom to fit face or logo inside the circle",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { if (!isSaving) onDismiss() },
                        modifier = Modifier.testTag("crop_photo_close_btn")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close crop dialog")
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0E141B))
                        .onSizeChanged { intSize ->
                            viewportSizePx = intSize.width.toFloat()
                            val (cx, cy) = clampPan(panX, panY, zoom, sourceBitmap, viewportSizePx)
                            panX = cx
                            panY = cy
                        }
                        .pointerInput(sourceBitmap) {
                            detectTransformGestures { _, pan, gestureZoom, _ ->
                                val bmp = sourceBitmap ?: return@detectTransformGestures
                                val nextZoom = (zoom * gestureZoom).coerceIn(1f, 4f)
                                val (cx, cy) = clampPan(
                                    panX + pan.x,
                                    panY + pan.y,
                                    nextZoom,
                                    bmp,
                                    viewportSizePx
                                )
                                zoom = nextZoom
                                panX = cx
                                panY = cy
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = sourceBitmap
                    val imageBitmap = remember(bmp) { bmp?.asImageBitmap() }

                    when {
                        isLoading -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(color = Color.White)
                                Text(
                                    text = "Loading photo...",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }
                        imageBitmap != null && bmp != null -> {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val vp = size.minDimension
                                val bmpW = bmp.width.toFloat()
                                val bmpH = bmp.height.toFloat()
                                val baseScale = max(vp / bmpW, vp / bmpH)
                                val totalScale = baseScale * zoom.coerceIn(1f, 4f)
                                val maxPanX = max(0f, (bmpW * totalScale - vp) / 2f)
                                val maxPanY = max(0f, (bmpH * totalScale - vp) / 2f)
                                val clampedX = panX.coerceIn(-maxPanX, maxPanX)
                                val clampedY = panY.coerceIn(-maxPanY, maxPanY)

                                val drawW = (bmpW * totalScale).roundToInt().coerceAtLeast(1)
                                val drawH = (bmpH * totalScale).roundToInt().coerceAtLeast(1)
                                val drawLeft = ((vp - drawW) / 2f + clampedX).roundToInt()
                                val drawTop = ((vp - drawH) / 2f + clampedY).roundToInt()

                                drawImage(
                                    image = imageBitmap,
                                    dstOffset = IntOffset(drawLeft, drawTop),
                                    dstSize = IntSize(drawW, drawH)
                                )

                                // Dim outer corners outside the circular avatar preview
                                val circleRect = Rect(0f, 0f, vp, vp)
                                val overlayPath = Path().apply {
                                    fillType = PathFillType.EvenOdd
                                    addRect(circleRect)
                                    addOval(circleRect)
                                }
                                drawPath(
                                    path = overlayPath,
                                    color = Color.Black.copy(alpha = 0.62f)
                                )

                                // Subtle 3x3 alignment grid inside circle
                                val third = vp / 3f
                                val twoThird = (vp * 2f) / 3f
                                val guideColor = Color.White.copy(alpha = 0.20f)
                                drawLine(guideColor, Offset(third, vp * 0.08f), Offset(third, vp * 0.92f), 1.dp.toPx())
                                drawLine(guideColor, Offset(twoThird, vp * 0.08f), Offset(twoThird, vp * 0.92f), 1.dp.toPx())
                                drawLine(guideColor, Offset(vp * 0.08f, third), Offset(vp * 0.92f, third), 1.dp.toPx())
                                drawLine(guideColor, Offset(vp * 0.08f, twoThird), Offset(vp * 0.92f, twoThird), 1.dp.toPx())

                                // Crisp white circular frame border
                                drawCircle(
                                    color = Color.White,
                                    radius = (vp / 2f) - 1.5.dp.toPx(),
                                    center = Offset(vp / 2f, vp / 2f),
                                    style = Stroke(width = 2.5.dp.toPx())
                                )
                            }
                        }
                        else -> {
                            Text(
                                text = errorMessage ?: "Unable to preview photo",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }

                // Zoom Slider + Reset
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomOut,
                        contentDescription = "Zoom out",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Slider(
                        value = zoom,
                        onValueChange = { newZoom ->
                            val (cx, cy) = clampPan(panX, panY, newZoom, sourceBitmap, viewportSizePx)
                            zoom = newZoom
                            panX = cx
                            panY = cy
                        },
                        valueRange = 1f..4f,
                        enabled = sourceBitmap != null && !isSaving,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("crop_photo_zoom_slider")
                    )
                    Icon(
                        imageVector = Icons.Default.ZoomIn,
                        contentDescription = "Zoom in",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    TextButton(
                        onClick = {
                            zoom = 1f
                            panX = 0f
                            panY = 0f
                        },
                        enabled = sourceBitmap != null && !isSaving,
                        modifier = Modifier.testTag("crop_photo_reset_btn")
                    ) {
                        Text("Reset")
                    }
                }

                if (errorMessage != null && sourceBitmap != null) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isSaving,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("crop_photo_cancel_btn")
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val bmp = sourceBitmap ?: return@Button
                            scope.launch {
                                isSaving = true
                                errorMessage = null
                                val encoded = ProfilePhotoUtils.cropAndCompressBitmapToBase64(
                                    bitmap = bmp,
                                    viewportSizePx = viewportSizePx,
                                    zoom = zoom,
                                    panX = panX,
                                    panY = panY,
                                    targetSizePx = 160
                                )
                                isSaving = false
                                if (encoded != null) {
                                    onCropped(encoded)
                                } else {
                                    errorMessage = "Failed to crop and compress image under 18 KB."
                                }
                            }
                        },
                        enabled = sourceBitmap != null && !isLoading && !isSaving,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("crop_photo_save_btn")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Saving...")
                        } else {
                            Icon(
                                imageVector = Icons.Default.Crop,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Crop & Save")
                        }
                    }
                }
            }
        }
    }
}
