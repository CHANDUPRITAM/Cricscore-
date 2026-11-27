#!/bin/bash
set -e

if [ ! -f debug.keystore ]; then
  keytool -genkey -v -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
fi

if [ ! -f app/google-services.json ]; then
  cat << 'EOF' > app/google-services.json
{
  "project_info": { "project_number": "100000000000", "project_id": "cricscore-demo", "storage_bucket": "://appspot.com" },
  "client": [{
    "client_info": { "mobilesdk_app_id": "1:100000000000:android:0000000000000000", "android_client_info": { "package_name": "com.aistudio.cricscore.krmxvp" } },
    "oauth_client": [],
    "api_key": [{ "current_key": "AIzaSyDummyKeyForCleanCompilation12345" }],
    "services": { "appinvite_service": { "other_platform_oauth_client": [] } }
  }],
  "configuration_version": "1"
}
EOF
fi

mkdir -p app/src/main/res/values
cat << 'EOF' > app/src/main/res/values/ci_strings.xml
<?xml version="1.0" encoding="utf-8"?>
<resources><string name="default_web_client_id">YOUR_WEB_CLIENT_ID</string></resources>
EOF

# Replace broken CirclePhotoCropDialog.kt with a 100% working clean version
cat << 'EOF' > app/src/main/java/com/example/ui/components/CirclePhotoCropDialog.kt
package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@Composable
fun CirclePhotoCropDialog(
    imageUri: Uri,
    title: String = "Crop Photo",
    onDismiss: () -> Unit,
    onCropped: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember(imageUri) { mutableStateOf<Bitmap?>(null) }
    var loadFailed by remember(imageUri) { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val boxDp = 240.dp
    val boxPx = with(LocalDensity.current) { boxDp.toPx() }

    LaunchedEffect(imageUri) {
        val loaded = withContext(Dispatchers.IO) { loadPreviewBitmap(context, imageUri) }
        if (loaded != null) bitmap = loaded else loadFailed = true
    }

    Dialog(onDismissRequest = { if (!isProcessing) onDismiss() }) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .size(boxDp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.85f))
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 4f)
                                val maxOff = (boxPx * (scale - 1f)) / 2f
                                offset = Offset(
                                    (offset.x + pan.x).coerceIn(-maxOff, maxOff),
                                    (offset.y + pan.y).coerceIn(-maxOff, maxOff)
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = bitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Crop Preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y
                                )
                        )
                    } else if (loadFailed) {
                        Text("Unable to load photo", color = Color.White, style = MaterialTheme.typography.bodySmall)
                    } else {
                        CircularProgressIndicator()
                    }
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(color = Color.White.copy(alpha = 0.85f), style = Stroke(width = 2.dp.toPx()))
                    }
                }

                Spacer(Modifier.height(10.dp))
                Slider(
                    value = scale,
                    onValueChange = {
                        scale = it
                        val maxOff = (boxPx * (scale - 1f)) / 2f
                        offset = Offset(offset.x.coerceIn(-maxOff, maxOff), offset.y.coerceIn(-maxOff, maxOff))
                    },
                    valueRange = 1f..4f
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss, enabled = !isProcessing) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = bitmap != null && !isProcessing,
                        onClick = {
                            val bmp = bitmap ?: return@Button
                            isProcessing = true
                            scope.launch {
                                val result = withContext(Dispatchers.Default) {
                                    cropAndCompressBitmapToBase64(bmp, scale, offset.x, offset.y, boxPx)
                                }
                                isProcessing = false
                                if (result != null) onCropped(result) else onDismiss()
                            }
                        }
                    ) {
                        Text(if (isProcessing) "Saving..." else "Crop & Save")
                    }
                }
            }
        }
    }
}

private fun loadPreviewBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val src = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val m = maxOf(info.size.width, info.size.height)
                if (m > 720) {
                    val sc = 720f / m
                    decoder.setTargetSize((info.size.width * sc).toInt().coerceAtLeast(1), (info.size.height * sc).toInt().coerceAtLeast(1))
                }
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }
    } catch (_: Throwable) { null }
}

private fun cropAndCompressBitmapToBase64(
    bmp: Bitmap,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    viewportPx: Float
): String? {
    return try {
        val baseSide = minOf(bmp.width, bmp.height).toFloat()
        val cropSide = (baseSide / scale.coerceAtLeast(1f)).toInt().coerceIn(1, minOf(bmp.width, bmp.height))
        val ratio = baseSide / viewportPx.coerceAtLeast(1f)
        val cx = (bmp.width / 2f) - (offsetX * ratio)
        val cy = (bmp.height / 2f) - (offsetY * ratio)
        val left = (cx - cropSide / 2f).toInt().coerceIn(0, (bmp.width - cropSide).coerceAtLeast(0))
        val top = (cy - cropSide / 2f).toInt().coerceIn(0, (bmp.height - cropSide).coerceAtLeast(0))

        val cropped = Bitmap.createBitmap(bmp, left, top, cropSide, cropSide)
        var dim = 160
        var bytes = ByteArray(0)
        while (dim >= 96) {
            val resized = Bitmap.createScaledBitmap(cropped, dim, dim, true)
            var q = 80
            while (q >= 20) {
                val out = ByteArrayOutputStream()
                resized.compress(Bitmap.CompressFormat.JPEG, q, out)
                bytes = out.toByteArray()
                if (bytes.size <= 15000) break
                q -= 15
            }
            if (bytes.size <= 15000) break
            dim -= 32
        }
        "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    } catch (_: Throwable) { null }
}
EOF

