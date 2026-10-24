package com.example.loveletter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

class LoveLetterWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val file = File(context.filesDir, WIDGET_IMAGE_FILE)
        val bitmap: Bitmap? = if (file.exists()) {
            BitmapFactory.decodeFile(file.absolutePath)
        } else null

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
                        text = "Waiting on a letter...",
                        style = TextStyle(color = ColorProvider(day = Color.Black, night = Color.White))
                    )
                }
            }
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
                LoveLetterWidget().updateAll(context)
            }
        }
    }
}

class LoveLetterWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LoveLetterWidget()
}
