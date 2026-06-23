package com.example.loveletter2

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import io.ktor.client.request.*
import io.ktor.http.ContentType
import io.ktor.http.contentType

class PushNotificationService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        remoteMessage.notification?.let { notification ->
            sendNotification(
                title = notification.title ?: "New message",
                body = notification.body ?: "Click to open",
                data = remoteMessage.data
            )
        }

        if (remoteMessage.data.isNotEmpty()) {
            sendNotification(
                title = remoteMessage.data["title"] ?: "New picture",
                body = remoteMessage.data["body"] ?: "check main menu",
                data = remoteMessage.data
            )
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = DrawingRepository(NetworkModule.client)
                val imageBytes = repository.getLatestDrawing()

                if (imageBytes != null) {
                    Log.d("PushService", "Downloaded image from API, size=${imageBytes.size}")
                    LoveLetterWidget.updateWidgetImage(applicationContext, imageBytes)
                } else {
                    Log.d("PushService", "No image available from API")
                }
            } catch (e: Exception) {
                Log.e("PushService", "Failed to fetch/update image", e)
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        sendTokenToServer(token)
    }

    private fun sendNotification(title: String, body: String, data: Map<String, String> = emptyMap()) {
        val channelId = CHANNEL_ID
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

        val notification = NotificationCompat.Builder(this, channelId)
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

    private fun sendTokenToServer(token: String) {
        val client = NetworkModule.client
        val url = "${BuildConfig.SERVER_URL}/api/fcm-token"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                client.post(url) {
                    header("api_key", BuildConfig.API_KEY)
                    contentType(ContentType.Application.Json)
                    setBody(mapOf("token" to token))
                }
                Log.d("FCM", "FCM token sent to server")
            } catch (e: Exception) {
                Log.e("FCM", "Failed to send FCM token to server", e)
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "love_letter_channel"
        const val NOTIFICATION_ID = 1
    }
}