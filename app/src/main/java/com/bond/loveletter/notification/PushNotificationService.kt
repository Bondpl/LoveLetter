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
import com.bond.loveletter.util.DrawingPayloadUtils
import com.bond.loveletter.widget.LoveLetterWidget
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File

class PushNotificationService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val type = remoteMessage.data["type"]
        val dataTitle = remoteMessage.data["title"] ?: remoteMessage.notification?.title
        val dataBody = remoteMessage.data["body"] ?: remoteMessage.notification?.body
        val sessionManager = NetworkModule.getSessionManager(applicationContext)

        when (type) {
            "NEW_DRAWING" -> {
                val incomingPairId = remoteMessage.data["pairId"]
                val currentPairId = sessionManager.getPairId()

                if (!incomingPairId.isNullOrBlank() && !currentPairId.isNullOrBlank() && incomingPairId != currentPairId) {
                    Log.w("FCM", "Zignorowano przestarzale NEW_DRAWING: incoming=$incomingPairId, current=$currentPairId")
                    return
                }

                if (!sessionManager.isPaired()) {
                    Log.w("FCM", "Zignorowano NEW_DRAWING: uzytkownik nie jest w aktywnej parze")
                    return
                }

                // Run blocking to hold Firebase WakeLock while downloading, decrypting and routing
                runBlocking(Dispatchers.IO) {
                    try {
                        val repository = NetworkModule.getDrawingRepository(applicationContext)
                        val result = repository.getLatestDrawing()
                        result.onSuccess { payloadResult ->
                            if (payloadResult != null) {
                                val rawBytes = payloadResult.bytes
                                val iv = payloadResult.iv
                                if (DrawingPayloadUtils.isPhotoPayload(rawBytes)) {
                                    val isViewOnce = DrawingPayloadUtils.isViewOncePhoto(rawBytes)
                                    if (isViewOnce && sessionManager.isViewOnceConsumed(iv)) {
                                        Log.d("PushService", "View-once photo $iv is already consumed, skipping.")
                                        return@onSuccess
                                    }
                                    val photoBytes = DrawingPayloadUtils.extractPhotoBytes(rawBytes)
                                    Log.d("PushService", "Downloaded and decrypted photo (viewOnce=$isViewOnce), size=${photoBytes.size}")

                                    // 1. Save photo locally for partner photo viewer
                                    try {
                                        File(applicationContext.filesDir, "latest_partner_photo.png").writeBytes(photoBytes)
                                        File(applicationContext.filesDir, "partner_photo.iv").writeText(iv)
                                        val flagFile = File(applicationContext.filesDir, "partner_photo_view_once.flag")
                                        if (isViewOnce) {
                                            flagFile.createNewFile()
                                        } else {
                                            flagFile.delete()
                                        }
                                    } catch (e: Exception) {
                                        Log.e("PushService", "Failed to cache photo locally", e)
                                    }

                                    // 2. Notify bus (do NOT touch widget!)
                                    DrawingUpdateBus.notifyNewPhoto(photoBytes, isViewOnce, iv)

                                    // 3. Send photo notification
                                    val title = if (isViewOnce) getString(R.string.notif_view_once_photo_title) else (dataTitle ?: getString(R.string.notif_new_photo_title))
                                    val body = if (isViewOnce) getString(R.string.notif_view_once_photo_body) else (dataBody ?: getString(R.string.notif_new_photo_body))
                                    val photoData = remoteMessage.data.toMutableMap().apply {
                                        put("EXTRA_OPEN_PARTNER_PHOTO", "true")
                                    }
                                    sendNotification(
                                        title = title,
                                        body = body,
                                        data = photoData,
                                        notificationId = NOTIFICATION_PHOTO_ID
                                    )
                                } else {
                                    Log.d("PushService", "Downloaded and decrypted drawing, size=${rawBytes.size}")
                                    LoveLetterWidget.updateWidgetImageSuspend(applicationContext, rawBytes)
                                    DrawingUpdateBus.notifyNewDrawing(rawBytes)

                                    val title = dataTitle ?: getString(R.string.notif_new_drawing_title)
                                    val body = dataBody ?: getString(R.string.notif_new_drawing_body)
                                    val drawingData = remoteMessage.data.toMutableMap().apply {
                                        put("EXTRA_OPEN_PARTNER_DRAWING", "true")
                                    }
                                    sendNotification(
                                        title = title,
                                        body = body,
                                        data = drawingData,
                                        notificationId = NOTIFICATION_DRAWING_ID
                                    )
                                }
                            } else {
                                Log.d("PushService", "No new drawing or photo found")
                            }
                        }.onFailure { error ->
                            Log.e("PushService", "Error downloading/decrypting drawing or photo", error)
                            val title = dataTitle ?: getString(R.string.notif_new_update_title)
                            val body = dataBody ?: getString(R.string.notif_new_update_body)
                            sendNotification(title = title, body = body, data = remoteMessage.data, notificationId = NOTIFICATION_DRAWING_ID)
                        }
                    } catch (e: Exception) {
                        Log.e("PushService", "Failed to fetch/update image", e)
                        val title = dataTitle ?: getString(R.string.notif_new_update_title)
                        val body = dataBody ?: getString(R.string.notif_new_update_body)
                        sendNotification(title = title, body = body, data = remoteMessage.data, notificationId = NOTIFICATION_DRAWING_ID)
                    }
                }
            }

            "PAIR_DISCONNECTED" -> {
                val incomingPairId = remoteMessage.data["pairId"]
                val currentPairId = sessionManager.getPairId()

                val isMatching = if (!incomingPairId.isNullOrBlank() && !currentPairId.isNullOrBlank()) {
                    incomingPairId == currentPairId
                } else {
                    sessionManager.isPaired()
                }

                if (isMatching && sessionManager.isPaired()) {
                    val title = dataTitle ?: getString(R.string.notif_partner_left_title)
                    val body = dataBody ?: getString(R.string.notif_partner_left_body)
                    sendNotification(title = title, body = body, data = remoteMessage.data, notificationId = NOTIFICATION_DISCONNECT_ID)

                    // Reset local state, session and widget synchronously while service is active
                    runBlocking(Dispatchers.IO) {
                        sessionManager.clearSession(getString(R.string.toast_partner_disconnected))
                        LoveLetterWidget.clearWidgetSuspend(applicationContext)
                        DrawingUpdateBus.clear()
                        try {
                            File(applicationContext.filesDir, "latest_partner_photo.png").delete()
                            File(applicationContext.filesDir, "partner_photo_view_once.flag").delete()
                            File(applicationContext.filesDir, "partner_photo.iv").delete()
                        } catch (_: Exception) {}
                    }
                } else {
                    Log.w("FCM", "Zignorowano przestarzale PAIR_DISCONNECTED: $incomingPairId (aktywny: $currentPairId)")
                }
            }

            else -> {
                if (!sessionManager.isPaired()) {
                    val title = dataTitle ?: "LoveLetter"
                    val body = dataBody ?: "You have a new update!"
                    sendNotification(title = title, body = body, data = remoteMessage.data, notificationId = NOTIFICATION_DRAWING_ID)
                    return
                }

                runBlocking(Dispatchers.IO) {
                    try {
                        val repository = NetworkModule.getDrawingRepository(applicationContext)
                        val result = repository.getLatestDrawing()
                        result.onSuccess { payloadResult ->
                            if (payloadResult != null) {
                                val rawBytes = payloadResult.bytes
                                val iv = payloadResult.iv
                                if (DrawingPayloadUtils.isPhotoPayload(rawBytes)) {
                                    val isViewOnce = DrawingPayloadUtils.isViewOncePhoto(rawBytes)
                                    if (isViewOnce && sessionManager.isViewOnceConsumed(iv)) {
                                        return@onSuccess
                                    }
                                    val photoBytes = DrawingPayloadUtils.extractPhotoBytes(rawBytes)
                                    try {
                                        File(applicationContext.filesDir, "latest_partner_photo.png").writeBytes(photoBytes)
                                        File(applicationContext.filesDir, "partner_photo.iv").writeText(iv)
                                        val flagFile = File(applicationContext.filesDir, "partner_photo_view_once.flag")
                                        if (isViewOnce) flagFile.createNewFile() else flagFile.delete()
                                    } catch (e: Exception) {
                                        Log.e("PushService", "Failed to cache photo locally", e)
                                    }

                                    DrawingUpdateBus.notifyNewPhoto(photoBytes, isViewOnce, iv)

                                    val title = if (isViewOnce) "View-Once Photo" else (dataTitle ?: "New Photo")
                                    val body = if (isViewOnce) "Your partner sent you a view-once photo!" else (dataBody ?: "Your partner sent you a photo!")
                                    val photoData = remoteMessage.data.toMutableMap().apply {
                                        put("EXTRA_OPEN_PARTNER_PHOTO", "true")
                                    }
                                    sendNotification(
                                        title = title,
                                        body = body,
                                        data = photoData,
                                        notificationId = NOTIFICATION_PHOTO_ID
                                    )
                                } else {
                                    LoveLetterWidget.updateWidgetImageSuspend(applicationContext, rawBytes)
                                    DrawingUpdateBus.notifyNewDrawing(rawBytes)

                                    val title = dataTitle ?: getString(R.string.notif_new_drawing_title)
                                    val body = dataBody ?: getString(R.string.notif_new_drawing_body)
                                    val drawingData = remoteMessage.data.toMutableMap().apply {
                                        put("EXTRA_OPEN_PARTNER_DRAWING", "true")
                                    }
                                    sendNotification(
                                        title = title,
                                        body = body,
                                        data = drawingData,
                                        notificationId = NOTIFICATION_DRAWING_ID
                                    )
                                }
                            } else {
                                val title = dataTitle ?: getString(R.string.notif_partner_left_title)
                                val body = dataBody ?: getString(R.string.notif_fallback_update_body)
                                sendNotification(title = title, body = body, data = remoteMessage.data, notificationId = NOTIFICATION_DRAWING_ID)
                            }
                        }.onFailure {
                            val title = dataTitle ?: getString(R.string.notif_partner_left_title)
                            val body = dataBody ?: getString(R.string.notif_fallback_update_body)
                            sendNotification(title = title, body = body, data = remoteMessage.data, notificationId = NOTIFICATION_DRAWING_ID)
                        }
                    } catch (e: Exception) {
                        Log.e("PushService", "Failed in fallback handler", e)
                        val title = dataTitle ?: getString(R.string.notif_partner_left_title)
                        val body = dataBody ?: getString(R.string.notif_fallback_update_body)
                        sendNotification(title = title, body = body, data = remoteMessage.data, notificationId = NOTIFICATION_DRAWING_ID)
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

    private fun sendNotification(
        title: String,
        body: String,
        data: Map<String, String> = emptyMap(),
        notificationId: Int = NOTIFICATION_DRAWING_ID
    ) {
        createNotificationChannel()

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            data.forEach { (key, value) ->
                putExtra(key, value)
            }
            if (data["EXTRA_OPEN_PARTNER_PHOTO"] == "true") {
                putExtra("EXTRA_OPEN_PARTNER_PHOTO", true)
            } else if (data["EXTRA_OPEN_PARTNER_DRAWING"] == "true" || data["type"] == "NEW_DRAWING") {
                putExtra("EXTRA_OPEN_PARTNER_DRAWING", true)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
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
        notificationManager.notify(notificationId, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        )
        channel.description = getString(R.string.notif_channel_desc)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "love_letter_channel"
        const val NOTIFICATION_DRAWING_ID = 1
        const val NOTIFICATION_DISCONNECT_ID = 2
        const val NOTIFICATION_PHOTO_ID = 3
    }
}
