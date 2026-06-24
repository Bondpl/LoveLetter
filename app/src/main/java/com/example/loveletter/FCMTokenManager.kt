package com.example.loveletter

import android.app.Activity
import android.content.Context
import androidx.core.app.ActivityCompat
import android.Manifest
import com.google.firebase.messaging.FirebaseMessaging

class FCMTokenManager(private val context: Context) {

    fun requestFCMToken(onSuccess: (String) -> Unit, onError: (Exception) -> Unit) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                onSuccess(token)
            } else {
                onError(task.exception ?: Exception("Unknown error"))
            }
        }
    }

    fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (context is Activity) {
                ActivityCompat.requestPermissions(
                    context,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }
    }
}

