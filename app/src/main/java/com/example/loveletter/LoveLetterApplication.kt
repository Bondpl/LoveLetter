package com.example.loveletter

import android.app.Application
import com.example.loveletter.data.storage.SessionManager

class LoveLetterApplication : Application() {
    lateinit var sessionManager: SessionManager
        private set

    override fun onCreate() {
        super.onCreate()
        NetworkModule.initialize(this)
        sessionManager = SessionManager.getInstance(this)
    }
}
