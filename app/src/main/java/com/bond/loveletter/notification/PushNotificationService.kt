package com.bond.loveletter.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.bond.loveletter.MainActivity
import com.bond.loveletter.R
import com.bond.loveletter.di.NetworkModule
import com.bond.loveletter.widget.LoveLetterWidget
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class PushNotificationService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val type = remoteMessage.data["type"]
        val dataTitle = remoteMessage.data["title"] ?: remoteMessage.notification?.title
        val dataBody = remoteMessage.data["body"] ?: remoteMessage.notification?.body

        when (type) {
            "NEW_DRAWING" -> {
                val title = dataTitle ?: "New Drawing"
                val body = dataBody ?: "Your partner sent you a drawing!"
                sendNotification(title = title, body = body, data = remoteMessage.data)

                // Run blocking to hold Firebase WakeLock while downloading, decrypting and updating widget
                runBlocking(Dispatchers.IO) {
                    try {
                        val repository = NetworkModule.getDrawingRepository(applicationContext)
                        val result = repository.getLatestDrawing()
                        result.onSuccess { imageBytes ->
                            if (imageBytes != null) {
                                Log.d("PushService", "Downloaded and decrypted drawing, size=${imageBytes.size}")
                                LoveLetterWidget.updateWidgetImageSuspend(applicationContext, imageBytes)
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

            "PAIR_DISCONNECTED" -> {
                val title = dataTitle ?: "LoveLetter"
                val body = dataBody ?: "Your partner has left the pair"
                sendNotification(title = title, body = body, data = remoteMessage.data)

                // Reset local state, session and widget synchronously while service is active
                runBlocking(Dispatchers.IO) {
                    val sessionManager = NetworkModule.getSessionManager(applicationContext)
                    sessionManager.clearSession("Your partner has disconnected the pair")
                    LoveLetterWidget.clearWidgetSuspend(applicationContext)
                    DrawingUpdateBus.clear()
                }
            }

            else -> {
                val title = dataTitle ?: "LoveLetter"
                val body = dataBody ?: "You have a new update!"
                sendNotification(title = title, body = body, data = remoteMessage.data)

                if (remoteMessage.data.isNotEmpty()) {
                    runBlocking(Dispatchers.IO) {
                        try {
                            val repository = NetworkModule.getDrawingRepository(applicationContext)
                            val result = repository.getLatestDrawing()
                            result.onSuccess { imageBytes ->
                                if (imageBytes != null) {
                                    LoveLetterWidget.updateWidgetImageSuspend(applicationContext, imageBytes)
                                    DrawingUpdateBus.notifyNewDrawing(imageBytes)
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        runBlocking(Dispatchers.IO) {
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
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "LoveLetter Alerts",
            NotificationManager.IMPORTANCE_HIGH
        )
        channel.description = "Love Letter pair & drawing notifications"

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "love_letter_channel"
        const val NOTIFICATION_ID = 1
    }
}
