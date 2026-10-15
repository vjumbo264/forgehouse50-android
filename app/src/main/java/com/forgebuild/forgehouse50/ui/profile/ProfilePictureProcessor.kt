package com.forgebuild.forgehouse50.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

data class ProcessedPicture(
    val bytes: ByteArray,
    val contentType: String,
    val previewBitmap: Bitmap,
)

object ProfilePictureProcessor {
    const val TARGET_SIZE = 256
    const val MAX_ALLOWED_BYTES = 100 * 1024 // 100 KB hard limit
    const val TARGET_BYTES = 40 * 1024       // 40 KB target

    /**
     * Decode Bitmap from Uri, applying EXIF rotation.
     */
    fun decodeBitmap(context: Context, uri: Uri): Bitmap? {
        val orientation = try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                val exif = ExifInterface(inputStream)
                val orient = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                inputStream.close()
                orient
            } else {
                ExifInterface.ORIENTATION_NORMAL
            }
        } catch (_: Throwable) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val decoded: Bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: return null

        return applyExifOrientation(decoded, orientation)
    }

    /**
     * Apply EXIF orientation matrix to normalize bitmap rotation.
     */
    fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (_: Throwable) {
            bitmap
        }
    }

    /**
     * Crop to square with offset, scale to 256x256, and compress to WebP (fallback JPEG)
     * stepping down quality until byte size is <= 100KB (aiming < 40KB).
     * Re-encoding inherently strips EXIF metadata and GPS location.
     */
    fun processAndCompress(
        source: Bitmap,
        cropXOffsetPercent: Float = 0f,
        cropYOffsetPercent: Float = 0f,
        scaleFactor: Float = 1f,
    ): ProcessedPicture {
        val origW = source.width
        val origH = source.height
        val baseDim = min(origW, origH)
        val visibleDim = (baseDim / scaleFactor.coerceAtLeast(1f)).toInt().coerceIn(1, baseDim)

        val maxOffsetX = (origW - visibleDim) / 2
        val maxOffsetY = (origH - visibleDim) / 2

        val centerX = origW / 2
        val centerY = origH / 2

        val startX = (centerX - visibleDim / 2 + (cropXOffsetPercent * maxOffsetX).toInt())
            .coerceIn(0, max(0, origW - visibleDim))
        val startY = (centerY - visibleDim / 2 + (cropYOffsetPercent * maxOffsetY).toInt())
            .coerceIn(0, max(0, origH - visibleDim))

        val cropped = Bitmap.createBitmap(source, startX, startY, visibleDim, visibleDim)
        val scaled = Bitmap.createScaledBitmap(cropped, TARGET_SIZE, TARGET_SIZE, true)

        // Stepped compression loop
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
        val mimeType = "image/webp"

        var quality = 85
        var outputBytes = ByteArray(0)

        while (quality >= 30) {
            val bos = ByteArrayOutputStream()
            scaled.compress(format, quality, bos)
            outputBytes = bos.toByteArray()
            if (outputBytes.isNotEmpty() && outputBytes.size <= MAX_ALLOWED_BYTES) {
                if (outputBytes.size <= TARGET_BYTES || quality <= 50) {
                    break
                }
            }
            quality -= 10
        }

        // If WebP compression failed to produce valid format or exceeds limit, fallback to JPEG
        if (outputBytes.isEmpty() || outputBytes.size > MAX_ALLOWED_BYTES) {
            quality = 80
            while (quality >= 30) {
                val bos = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, bos)
                val testBytes = bos.toByteArray()
                if (testBytes.isNotEmpty() && testBytes.size <= MAX_ALLOWED_BYTES) {
                    return ProcessedPicture(testBytes, "image/jpeg", scaled)
                }
                quality -= 15
            }
        }

        return ProcessedPicture(outputBytes, mimeType, scaled)
    }
}
