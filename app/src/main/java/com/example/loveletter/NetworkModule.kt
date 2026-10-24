package com.example.loveletter

import android.content.Context
import com.example.loveletter.data.network.LoveLetterApiService
import com.example.loveletter.data.network.NetworkClient
import com.example.loveletter.data.repository.AuthRepository
import com.example.loveletter.data.repository.DrawingRepository
import com.example.loveletter.data.storage.SessionManager

object NetworkModule {
    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var authRepository: AuthRepository? = null

    @Volatile
    private var drawingRepository: DrawingRepository? = null

    fun initialize(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    private fun getContext(context: Context? = null): Context {
        return context?.applicationContext
            ?: appContext
            ?: throw IllegalStateException("NetworkModule not initialized with Context")
    }

    fun getSessionManager(context: Context? = null): SessionManager {
        return SessionManager.getInstance(getContext(context))
    }

    fun getApiService(context: Context? = null): LoveLetterApiService {
        return NetworkClient.getApiService(getContext(context))
    }

    fun getAuthRepository(context: Context? = null): AuthRepository {
        return authRepository ?: synchronized(this) {
            authRepository ?: run {
                val ctx = getContext(context)
                AuthRepository(getApiService(ctx), getSessionManager(ctx)).also { authRepository = it }
            }
        }
    }

    fun getDrawingRepository(context: Context? = null): DrawingRepository {
        return drawingRepository ?: synchronized(this) {
            drawingRepository ?: run {
                val ctx = getContext(context)
                DrawingRepository(getApiService(ctx), getSessionManager(ctx)).also { drawingRepository = it }
            }
        }
    }
}
