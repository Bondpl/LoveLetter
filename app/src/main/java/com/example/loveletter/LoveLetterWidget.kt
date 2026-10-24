package com.example.loveletter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max

class LoveLetterWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val bitmap = loadWidgetBitmap(context)

        provideContent {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .clickable(actionStartActivity<MainActivity>()),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        provider = ImageProvider(bitmap),
                        contentDescription = "Love Letter Drawing",
                        contentScale = ContentScale.Fit,
                        modifier = GlanceModifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = "Waiting on a letter... 💌",
                        style = TextStyle(color = ColorProvider(day = Color(0xFF1C1B1F), night = Color(0xFFE6E1E5)))
                    )
                }
            }
        }
    }

    private fun loadWidgetBitmap(context: Context): Bitmap? {
        return try {
            val file = File(context.filesDir, WIDGET_IMAGE_FILE)
            if (!file.exists()) return null

            // First decode with inJustDecodeBounds to check dimensions
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)

            val maxDim = 512
            val largestDim = max(options.outWidth, options.outHeight)
            var inSampleSize = 1
            while (largestDim / (inSampleSize * 2) >= maxDim) {
                inSampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val sampled = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return null

            if (sampled.width > maxDim || sampled.height > maxDim) {
                val ratio = minOf(maxDim.toFloat() / sampled.width, maxDim.toFloat() / sampled.height)
                val targetW = (sampled.width * ratio).toInt()
                val targetH = (sampled.height * ratio).toInt()
                Bitmap.createScaledBitmap(sampled, targetW, targetH, true)
            } else {
                sampled
            }
        } catch (e: Exception) {
            Log.e("LoveLetterWidget", "Error decoding widget bitmap", e)
            null
        }
    }

    companion object {
        private const val WIDGET_IMAGE_FILE = "latest_widget_image.png"

        fun updateWidgetImage(context: Context, imageBytes: ByteArray) {
            try {
                val file = File(context.filesDir, WIDGET_IMAGE_FILE)
                file.writeBytes(imageBytes)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    LoveLetterWidget().updateAll(context)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        fun clearWidget(context: Context) {
            try {
                val file = File(context.filesDir, WIDGET_IMAGE_FILE)
                if (file.exists()) {
                    val deleted = file.delete()
                    Log.d("LoveLetterWidget", "latest_widget_image.png deleted: $deleted")
                }
            } catch (e: Exception) {
                Log.e("LoveLetterWidget", "Failed to delete widget image", e)
            }
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    LoveLetterWidget().updateAll(context)
                } catch (e: Exception) {
                    Log.e("LoveLetterWidget", "Failed to update widget after clearing", e)
                }
            }
        }
    }
}

class LoveLetterWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LoveLetterWidget()
}
