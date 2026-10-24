package com.example.loveletter.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.loveletter.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

class LoveLetterWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Single
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val hasDrawing = prefs[KEY_HAS_DRAWING] ?: File(context.filesDir, WIDGET_IMAGE_FILE).exists()
            val bitmap = if (hasDrawing) loadWidgetBitmap(context) else null

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
                        text = "Waiting on a letter...",
                        style = TextStyle(color = ColorProvider(day = Color(0xFF1C1B1F), night = Color(0xFFE6E1E5)))
                    )
                }
            }
        }
    }

    private fun loadWidgetBitmap(context: Context): Bitmap? {
        return try {
            val file = File(context.filesDir, WIDGET_IMAGE_FILE)
            if (!file.exists()) {
                Log.d("LoveLetterWidget", "loadWidgetBitmap: $WIDGET_IMAGE_FILE does not exist")
                return null
            }

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

            val resultBitmap = if (sampled.width > maxDim || sampled.height > maxDim) {
                val ratio = minOf(maxDim.toFloat() / sampled.width, maxDim.toFloat() / sampled.height)
                val targetW = (sampled.width * ratio).toInt()
                val targetH = (sampled.height * ratio).toInt()
                Bitmap.createScaledBitmap(sampled, targetW, targetH, true)
            } else {
                sampled
            }
            Log.d("LoveLetterWidget", "loadWidgetBitmap: loaded bitmap ${resultBitmap.width}x${resultBitmap.height}")
            resultBitmap
        } catch (e: Exception) {
            Log.e("LoveLetterWidget", "Error decoding widget bitmap", e)
            null
        }
    }

    companion object {
        private const val WIDGET_IMAGE_FILE = "latest_widget_image.png"
        private val KEY_HAS_DRAWING = booleanPreferencesKey("has_drawing")
        private val KEY_TIMESTAMP = longPreferencesKey("timestamp")

        suspend fun updateWidgetImageSuspend(context: Context, imageBytes: ByteArray) = withContext(Dispatchers.IO) {
            try {
                val file = File(context.filesDir, WIDGET_IMAGE_FILE)
                file.writeBytes(imageBytes)
                Log.d("LoveLetterWidget", "Saved $WIDGET_IMAGE_FILE, size=${imageBytes.size}")
            } catch (e: Exception) {
                Log.e("LoveLetterWidget", "Failed to write widget image file", e)
            }
            try {
                val manager = GlanceAppWidgetManager(context)
                val glanceIds = manager.getGlanceIds(LoveLetterWidget::class.java)
                Log.d("LoveLetterWidget", "Updating ${glanceIds.size} widget instances with new image")
                for (glanceId in glanceIds) {
                    updateAppWidgetState(context, glanceId) { prefs ->
                        prefs[KEY_HAS_DRAWING] = true
                        prefs[KEY_TIMESTAMP] = System.currentTimeMillis()
                    }
                    LoveLetterWidget().update(context, glanceId)
                }
                LoveLetterWidget().updateAll(context)
                Log.d("LoveLetterWidget", "Widget update completed")
            } catch (e: Exception) {
                Log.e("LoveLetterWidget", "Error updating widget in updateWidgetImageSuspend", e)
            }
        }

        fun updateWidgetImage(context: Context, imageBytes: ByteArray) {
            CoroutineScope(Dispatchers.IO).launch {
                updateWidgetImageSuspend(context, imageBytes)
            }
        }

        suspend fun clearWidgetSuspend(context: Context) = withContext(Dispatchers.IO) {
            try {
                val file = File(context.filesDir, WIDGET_IMAGE_FILE)
                if (file.exists()) {
                    val deleted = file.delete()
                    Log.d("LoveLetterWidget", "latest_widget_image.png deleted: $deleted")
                }
            } catch (e: Exception) {
                Log.e("LoveLetterWidget", "Failed to delete widget image", e)
            }
            try {
                val manager = GlanceAppWidgetManager(context)
                val glanceIds = manager.getGlanceIds(LoveLetterWidget::class.java)
                Log.d("LoveLetterWidget", "Clearing ${glanceIds.size} widget instances")
                for (glanceId in glanceIds) {
                    updateAppWidgetState(context, glanceId) { prefs ->
                        prefs[KEY_HAS_DRAWING] = false
                        prefs[KEY_TIMESTAMP] = System.currentTimeMillis()
                    }
                    LoveLetterWidget().update(context, glanceId)
                }
                LoveLetterWidget().updateAll(context)
                Log.d("LoveLetterWidget", "Widget clear completed")
            } catch (e: Exception) {
                Log.e("LoveLetterWidget", "Failed to update widget after clearing", e)
            }
        }

        fun clearWidget(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                clearWidgetSuspend(context)
            }
        }
    }
}

class LoveLetterWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LoveLetterWidget()
}
