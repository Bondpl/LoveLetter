package com.example.loveletter2

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
import android.widget.RemoteViews

class LoveLetterWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidgetUI(context, appWidgetManager, appWidgetId, null, "Waiting on a letter...")
        }
    }

    companion object {
        private fun updateWidgetUI(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            imageBytes: ByteArray?,
            statusText: String?
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            if (imageBytes != null) {
                val bmp: Bitmap? = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                if (bmp != null) {
                    views.setImageViewBitmap(R.id.widget_image, bmp)
                    views.setViewVisibility(R.id.widget_image, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_text, View.GONE)
                }
            } else if (statusText != null) {
                views.setTextViewText(R.id.widget_text, statusText)
                views.setViewVisibility(R.id.widget_image, View.GONE)
                views.setViewVisibility(R.id.widget_text, View.VISIBLE)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun updateWidgetImage(context: Context, imageBytes: ByteArray) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = android.content.ComponentName(context, LoveLetterWidget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

            for (appWidgetId in appWidgetIds) {
                updateWidgetUI(context, appWidgetManager, appWidgetId, imageBytes, null)
            }
        }
    }
}