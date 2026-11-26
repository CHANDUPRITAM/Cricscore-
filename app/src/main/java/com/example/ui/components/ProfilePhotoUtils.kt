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
    // 25 KB Base64 limit (~18 KB raw JPEG) keeps Firestore writes instant & well within Spark free plan
    const val MAX_BASE64_LENGTH = 26_000
    private const val MAX_RAW_JPEG_BYTES = 18_500

    private val bitmapCache = object : LruCache<String, ImageBitmap>(120) {}

    suspend fun compressGalleryPhotoToBase64(
        context: Context,
        uri: Uri,
        targetSizePx: Int = 160
    ): String? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver

        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOpts)
        } ?: return@withContext null

        val srcW = boundsOpts.outWidth
        val srcH = boundsOpts.outHeight
        if (srcW <= 0 || srcH <= 0) return@withContext null

        var sampleSize = 1
        val maxSide = targetSizePx * 2
        while (srcW / sampleSize > maxSide && srcH / sampleSize > maxSide) {
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

        var workingBitmap: Bitmap? = sampledBitmap
        var croppedBitmap: Bitmap? = null
        var scaledBitmap: Bitmap? = null
        try {
            val rotated = applyExifOrientation(sampledBitmap, orientation)
            if (rotated !== sampledBitmap) {
                sampledBitmap.recycle()
                workingBitmap = rotated
            }

            val current = workingBitmap ?: return@withContext null
            val side = min(current.width, current.height).coerceAtLeast(1)
            val cropX = ((current.width - side) / 2).coerceAtLeast(0)
            val cropY = ((current.height - side) / 2).coerceAtLeast(0)
            croppedBitmap = if (current.width != current.height) {
                Bitmap.createBitmap(current, cropX, cropY, side, side)
            } else {
                current
            }

            var dim = min(side, targetSizePx).coerceAtLeast(64)
            var bestBytes: ByteArray? = null

            for (attemptDim in listOf(dim, (dim * 0.8f).roundToInt().coerceAtLeast(64), 96)) {
                if (scaledBitmap != null && scaledBitmap !== croppedBitmap) {
                    scaledBitmap.recycle()
                }
                scaledBitmap = if (croppedBitmap.width > attemptDim) {
                    Bitmap.createScaledBitmap(croppedBitmap, attemptDim, attemptDim, true)
                } else {
                    croppedBitmap
                }

                for (quality in listOf(76, 62, 48, 36)) {
                    val out = ByteArrayOutputStream()
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    val bytes = out.toByteArray()
                    bestBytes = bytes
                    if (bytes.size <= MAX_RAW_JPEG_BYTES) {
                        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        if (encoded.length <= MAX_BASE64_LENGTH) {
                            return@withContext encoded
                        }
                    }
                }
                dim = attemptDim
            }

            bestBytes?.let { bytes ->
                val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
                if (encoded.length <= MAX_BASE64_LENGTH) encoded else null
            }
        } finally {
            if (scaledBitmap != null && scaledBitmap !== croppedBitmap && scaledBitmap !== workingBitmap) {
                scaledBitmap.recycle()
            }
            if (croppedBitmap != null && croppedBitmap !== workingBitmap) {
                croppedBitmap.recycle()
            }
            if (workingBitmap != null && !workingBitmap.isRecycled) {
                workingBitmap.recycle()
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
