package com.example.loveletter

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PushNotificationService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val type = remoteMessage.data["type"]
        val title = remoteMessage.data["title"]
            ?: remoteMessage.notification?.title
            ?: "New Drawing"
        val body = remoteMessage.data["body"]
            ?: remoteMessage.notification?.body
            ?: "Your partner sent you a drawing!"

        sendNotification(title = title, body = body, data = remoteMessage.data)

        if (type == "NEW_DRAWING" || remoteMessage.data.isNotEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val repository = NetworkModule.getDrawingRepository(applicationContext)
                    val result = repository.getLatestDrawing()
                    result.onSuccess { imageBytes ->
                        if (imageBytes != null) {
                            Log.d("PushService", "Downloaded and decrypted drawing, size=${imageBytes.size}")
                            LoveLetterWidget.updateWidgetImage(applicationContext, imageBytes)
                            DrawingUpdateBus.notifyNewDrawing(imageBytes)
                        } else {
                            Log.d("PushService", "No new drawing found")
                        }
                    }.onFailure { error ->
                        Log.e("PushService", "Error downloading/decrypting drawing", error)
                    }
                } catch (e: Exception) {
                    Log.e("PushService", "Failed to fetch/update image", e)
                }
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                NetworkModule.getAuthRepository(applicationContext).updateFcmToken(token)
                Log.d("FCM", "FCM token updated successfully onNewToken")
            } catch (e: Exception) {
                Log.e("FCM", "Failed to update FCM token onNewToken", e)
            }
        }
    }

    private fun sendNotification(title: String, body: String, data: Map<String, String> = emptyMap()) {
        createNotificationChannel()

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            data.forEach { (key, value) ->
                putExtra(key, value)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Love Letter Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            channel.description = "Love Letter Notifications"

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "love_letter_channel"
        const val NOTIFICATION_ID = 1
    }
}
