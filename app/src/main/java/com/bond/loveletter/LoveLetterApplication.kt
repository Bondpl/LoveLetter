package com.bond.loveletter

import android.app.Application
import com.bond.loveletter.data.storage.SessionManager
import com.bond.loveletter.di.NetworkModule

class LoveLetterApplication : Application() {
    lateinit var sessionManager: SessionManager
        private set

    override fun onCreate() {
        super.onCreate()
        NetworkModule.initialize(this)
        sessionManager = SessionManager.getInstance(this)
    }
}
