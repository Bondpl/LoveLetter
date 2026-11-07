package com.bond.loveletter.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import com.bond.loveletter.data.model.ApiErrorCode
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max

object ImageCompressor {
    const val MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024L // 5 MB hard limit
    private const val TARGET_MAX_DIMENSION = 1440 // Sharp HD for mobile screens

    fun processAndCompressImage(context: Context, uri: Uri): ByteArray {
        val tempFile = File(context.cacheDir, "picked_photo_${System.currentTimeMillis()}.tmp")
        try {
            // 1. Copy the Uri stream to a local cache file once using robust fallbacks
            val inputStream = openStream(context, uri)
                ?: throw IllegalArgumentException("Could not open image stream for: $uri")

            tempFile.outputStream().use { out ->
                inputStream.use { input ->
                    input.copyTo(out)
                }
            }

            if (!tempFile.exists() || tempFile.length() == 0L) {
                throw IllegalStateException("Failed to read image data (empty file)")
            }

            val path = tempFile.absolutePath

            // 2. Read bounds from local file without loading entire bitmap into RAM
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(path, boundsOptions)

            val origWidth = boundsOptions.outWidth
            val origHeight = boundsOptions.outHeight
            if (origWidth <= 0 || origHeight <= 0) {
                throw IllegalArgumentException("Invalid image dimensions ($origWidth x $origHeight)")
            }

            // 3. Compute sample size to avoid OOM
            val largestDim = max(origWidth, origHeight)
            var sampleSize = 1
            while (largestDim / (sampleSize * 2) >= TARGET_MAX_DIMENSION) {
                sampleSize *= 2
            }

            // 4. Decode sampled bitmap from local file
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            var bitmap = BitmapFactory.decodeFile(path, decodeOptions)
                ?: throw IllegalStateException("Failed to decode image from file")

            // 5. Correct orientation using EXIF
            bitmap = fixExifOrientation(path, bitmap)

            // 6. Scale down precisely to target maximum dimension if still larger
            val currentMaxDim = max(bitmap.width, bitmap.height)
            if (currentMaxDim > TARGET_MAX_DIMENSION) {
                val scale = TARGET_MAX_DIMENSION.toFloat() / currentMaxDim.toFloat()
                val newWidth = max(1, (bitmap.width * scale).toInt())
                val newHeight = max(1, (bitmap.height * scale).toInt())
                val scaled = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                if (scaled !== bitmap) {
                    bitmap.recycle()
                }
                bitmap = scaled
            }

            // 7. Compress initial JPEG with high quality (85)
            var quality = 85
            var stream = ByteArrayOutputStream()
            var currentBitmap = bitmap
            currentBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            var resultBytes = stream.toByteArray()

            // 8. Progressive Smart Downscaling & Quality Adjustment
            // If file exceeds 5 MB limit, iteratively scale down resolution and adjust quality
            var attempts = 0
            while (resultBytes.size > MAX_FILE_SIZE_BYTES && attempts < 6) {
                attempts++
                if (quality > 65) {
                    quality -= 10
                } else {
                    // Downscale image dimensions by 25% (reduces pixel count & memory by ~44%)
                    val scaleFactor = 0.75f
                    val nextW = max(320, (currentBitmap.width * scaleFactor).toInt())
                    val nextH = max(320, (currentBitmap.height * scaleFactor).toInt())
                    if (nextW < currentBitmap.width && nextH < currentBitmap.height) {
                        val scaled = Bitmap.createScaledBitmap(currentBitmap, nextW, nextH, true)
                        if (currentBitmap !== bitmap) {
                            currentBitmap.recycle()
                        }
                        currentBitmap = scaled
                        quality = 80 // Reset quality on smaller resolution to keep it visually sharp
                    } else {
                        quality -= 15
                    }
                }
                stream = ByteArrayOutputStream()
                currentBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                resultBytes = stream.toByteArray()
            }

            if (resultBytes.size > MAX_FILE_SIZE_BYTES) {
                throw AppException.Api(
                    errorCode = ApiErrorCode.FILE_TOO_LARGE,
                    serverMessage = "Image is too large to compress under 5 MB limit",
                    httpStatus = 400
                )
            }

            if (currentBitmap !== bitmap && !bitmap.isRecycled) {
                bitmap.recycle()
            }
            if (!currentBitmap.isRecycled) {
                currentBitmap.recycle()
            }

            return resultBytes
        } finally {
            try {
                if (tempFile.exists()) tempFile.delete()
            } catch (_: Exception) {}
        }
    }

    private fun openStream(context: Context, uri: Uri): InputStream? {
        // Strategy 1: Standard ContentResolver openInputStream
        try {
            val stream = context.contentResolver.openInputStream(uri)
            if (stream != null) return stream
        } catch (e: Exception) {
            Log.w("ImageCompressor", "openInputStream failed: ${e.message}")
        }

        // Strategy 2: FileDescriptor via openFileDescriptor
        try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) {
                return android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd)
            }
        } catch (e: Exception) {
            Log.w("ImageCompressor", "openFileDescriptor failed: ${e.message}")
        }

        // Strategy 3: AssetFileDescriptor via openAssetFileDescriptor
        try {
            val afd = context.contentResolver.openAssetFileDescriptor(uri, "r")
            if (afd != null) {
                return afd.createInputStream()
            }
        } catch (e: Exception) {
            Log.w("ImageCompressor", "openAssetFileDescriptor failed: ${e.message}")
        }

        return null
    }

    private fun fixExifOrientation(filePath: String, bitmap: Bitmap): Bitmap {
        return try {
            val exif = ExifInterface(filePath)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                else -> return bitmap
            }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) {
                bitmap.recycle()
            }
            rotated
        } catch (e: Exception) {
            Log.w("ImageCompressor", "Failed to fix EXIF orientation", e)
            bitmap
        }
    }
}
