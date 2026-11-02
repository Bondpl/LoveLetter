package com.bond.loveletter.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.ByteArrayOutputStream
import java.io.File

fun ImageBitmap.toPngByteArray(): ByteArray {
    val androidBitmap = this.asAndroidBitmap()
    val stream = ByteArrayOutputStream()
    androidBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
    return stream.toByteArray()
}

/**
 * Removes any transparent pixels (such as rounded corner cutouts) by blending them
 * onto the solid background color of the drawing, preventing gallery apps from rendering
 * transparent corners as black borders.
 */
fun cleanImageBytes(imageBytes: ByteArray): ByteArray {
    return try {
        val original = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            ?: return imageBytes
        val width = original.width
        val height = original.height

        // Sample background color from center of the drawing
        val centerPixel = original.getPixel(width / 2, height / 2)
        val bgColor = if (Color.alpha(centerPixel) > 200) {
            centerPixel
        } else {
            Color.WHITE
        }

        // Create a solid non-transparent bitmap filled with bgColor
        val cleanBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(cleanBitmap)
        canvas.drawColor(bgColor)
        canvas.drawBitmap(original, 0f, 0f, null)

        val origPixels = IntArray(width * height)
        original.getPixels(origPixels, 0, width, 0, 0, width, height)
        val cleanPixels = IntArray(width * height)
        cleanBitmap.getPixels(cleanPixels, 0, width, 0, 0, width, height)

        var hasTransparency = false
        for (i in origPixels.indices) {
            val alpha = (origPixels[i] ushr 24) and 0xFF
            // If pixel was transparent or anti-aliased edge, fill with solid background
            if (alpha < 250) {
                hasTransparency = true
                cleanPixels[i] = bgColor
            }
        }

        if (hasTransparency) {
            cleanBitmap.setPixels(cleanPixels, 0, width, 0, 0, width, height)
        }

        val stream = ByteArrayOutputStream()
        cleanBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.toByteArray()
    } catch (e: Exception) {
        imageBytes
    }
}

fun saveImageToGallery(context: Context, imageBytes: ByteArray): Boolean {
    return try {
        val bytesToSave = cleanImageBytes(imageBytes)
        val filename = "LoveLetter_${System.currentTimeMillis()}.png"
        val resolver = context.contentResolver

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + "LoveLetter")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return false

            resolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(bytesToSave)
                outputStream.flush()
            } ?: return false

            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
            true
        } else {
            @Suppress("DEPRECATION")
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val folder = File(picturesDir, "LoveLetter").apply { if (!exists()) mkdirs() }
            val file = File(folder, filename)
            file.writeBytes(bytesToSave)

            val contentValues = ContentValues().apply {
                @Suppress("DEPRECATION")
                put(MediaStore.Images.Media.DATA, file.absolutePath)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            }
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            true
        }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}
