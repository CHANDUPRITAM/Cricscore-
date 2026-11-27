package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ProfilePhotoUtils {
    // <= 18 KB raw JPEG (~24 KB Base64) keeps Firestore avatar & team sync instant
    const val MAX_BASE64_LENGTH = 26_000
    private const val MAX_RAW_JPEG_BYTES = 18 * 1024

    private val bitmapCache = object : LruCache<String, ImageBitmap>(120) {}

    suspend fun loadPreviewBitmap(
        context: Context,
        uri: Uri,
        maxDimPx: Int = 1080
    ): Bitmap? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver

        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOpts)
        } ?: return@withContext null

        val srcW = boundsOpts.outWidth
        val srcH = boundsOpts.outHeight
        if (srcW <= 0 || srcH <= 0) return@withContext null

        var sampleSize = 1
        while (srcW / sampleSize > maxDimPx || srcH / sampleSize > maxDimPx) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val sampledBitmap = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: return@withContext null

        val orientation = runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val rotated = applyExifOrientation(sampledBitmap, orientation)
        if (rotated !== sampledBitmap) {
            sampledBitmap.recycle()
        }
        rotated
    }

    suspend fun cropAndCompressBitmapToBase64(
        bitmap: Bitmap,
        viewportSizePx: Float,
        zoom: Float,
        panX: Float,
        panY: Float,
        targetSizePx: Int = 160
    ): String? = withContext(Dispatchers.IO) {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return@withContext null

        val bmpW = bitmap.width.toFloat()
        val bmpH = bitmap.height.toFloat()
        val effectiveViewport = if (viewportSizePx > 0f) viewportSizePx else min(bmpW, bmpH)
        val baseScale = max(effectiveViewport / bmpW, effectiveViewport / bmpH)
        val totalScale = baseScale * zoom.coerceIn(1f, 4f)
        val maxPanX = max(0f, (bmpW * totalScale - effectiveViewport) / 2f)
        val maxPanY = max(0f, (bmpH * totalScale - effectiveViewport) / 2f)
        val clampedX = panX.coerceIn(-maxPanX, maxPanX)
        val clampedY = panY.coerceIn(-maxPanY, maxPanY)

        val imgLeft = (effectiveViewport - bmpW * totalScale) / 2f + clampedX
        val imgTop = (effectiveViewport - bmpH * totalScale) / 2f + clampedY

        val cropLeft = ((0f - imgLeft) / totalScale).roundToInt().coerceIn(0, max(0, bitmap.width - 1))
        val cropTop = ((0f - imgTop) / totalScale).roundToInt().coerceIn(0, max(0, bitmap.height - 1))
        val desiredSide = (effectiveViewport / totalScale).roundToInt().coerceAtLeast(1)
        val cropSide = min(desiredSide, min(bitmap.width - cropLeft, bitmap.height - cropTop)).coerceAtLeast(1)

        var croppedSquare: Bitmap? = null
        try {
            croppedSquare = Bitmap.createBitmap(bitmap, cropLeft, cropTop, cropSide, cropSide)
            compressSquareBitmapToBase64(croppedSquare, targetSizePx)
        } finally {
            if (croppedSquare != null && croppedSquare !== bitmap && !croppedSquare.isRecycled) {
                croppedSquare.recycle()
            }
        }
    }

    suspend fun compressGalleryPhotoToBase64(
        context: Context,
        uri: Uri,
        targetSizePx: Int = 160
    ): String? = withContext(Dispatchers.IO) {
        val workingBitmap = loadPreviewBitmap(context, uri, maxDimPx = targetSizePx * 3)
            ?: return@withContext null
        var croppedBitmap: Bitmap? = null
        try {
            val side = min(workingBitmap.width, workingBitmap.height).coerceAtLeast(1)
            val cropX = ((workingBitmap.width - side) / 2).coerceAtLeast(0)
            val cropY = ((workingBitmap.height - side) / 2).coerceAtLeast(0)
            croppedBitmap = if (workingBitmap.width != workingBitmap.height) {
                Bitmap.createBitmap(workingBitmap, cropX, cropY, side, side)
            } else {
                workingBitmap
            }
            compressSquareBitmapToBase64(croppedBitmap, targetSizePx)
        } finally {
            if (croppedBitmap != null && croppedBitmap !== workingBitmap && !croppedBitmap.isRecycled) {
                croppedBitmap.recycle()
            }
            if (!workingBitmap.isRecycled) {
                workingBitmap.recycle()
            }
        }
    }

    private fun compressSquareBitmapToBase64(
        croppedBitmap: Bitmap,
        targetSizePx: Int = 160
    ): String? {
        var scaledBitmap: Bitmap? = null
        try {
            val initialDim = min(targetSizePx, max(targetSizePx, croppedBitmap.width)).coerceAtLeast(64)
            val candidateDims = listOf(initialDim, 128, 96).distinct()
            var bestBytes: ByteArray? = null

            for (attemptDim in candidateDims) {
                if (scaledBitmap != null && scaledBitmap !== croppedBitmap && !scaledBitmap.isRecycled) {
                    scaledBitmap.recycle()
                }
                scaledBitmap = if (croppedBitmap.width != attemptDim || croppedBitmap.height != attemptDim) {
                    Bitmap.createScaledBitmap(croppedBitmap, attemptDim, attemptDim, true)
                } else {
                    croppedBitmap
                }

                for (quality in listOf(84, 74, 62, 48, 36, 26)) {
                    val out = ByteArrayOutputStream()
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    val bytes = out.toByteArray()
                    bestBytes = bytes
                    if (bytes.size <= MAX_RAW_JPEG_BYTES) {
                        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        if (encoded.length <= MAX_BASE64_LENGTH) {
                            return encoded
                        }
                    }
                }
            }

            return bestBytes?.let { bytes ->
                val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
                if (bytes.size <= MAX_RAW_JPEG_BYTES && encoded.length <= MAX_BASE64_LENGTH) encoded else null
            }
        } finally {
            if (scaledBitmap != null && scaledBitmap !== croppedBitmap && !scaledBitmap.isRecycled) {
                scaledBitmap.recycle()
            }
        }
    }

    fun decodeBase64ToImageBitmap(base64: String): ImageBitmap? {
        if (base64.isBlank()) return null
        val cacheKey = "${base64.length}:${base64.hashCode()}"
        bitmapCache.get(cacheKey)?.let { return it }

        return runCatching {
            val clean = base64.substringAfter("base64,", base64)
            val bytes = Base64.decode(clean, Base64.DEFAULT)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            bmp.asImageBitmap().also { img ->
                bitmapCache.put(cacheKey, img)
            }
        }.getOrNull()
    }

    fun estimateKb(base64: String): Int {
        if (base64.isBlank()) return 0
        val approxBytes = (base64.length * 3) / 4
        return max(1, (approxBytes + 512) / 1024)
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrDefault(bitmap)
    }
}
