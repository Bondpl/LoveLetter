package com.example.loveletter.data.network

import android.content.Context
import com.example.loveletter.BuildConfig
import com.example.loveletter.data.storage.SessionManager
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object NetworkClient {
    private var apiService: LoveLetterApiService? = null
    private var okHttpClient: OkHttpClient? = null

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    fun getOkHttpClient(context: Context): OkHttpClient {
        return okHttpClient ?: synchronized(this) {
            okHttpClient ?: buildOkHttpClient(context).also { okHttpClient = it }
        }
    }

    private fun buildOkHttpClient(context: Context): OkHttpClient {
        val sessionManager = SessionManager.getInstance(context)
        return OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(sessionManager))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun getApiService(context: Context): LoveLetterApiService {
        return apiService ?: synchronized(this) {
            apiService ?: buildApiService(context).also { apiService = it }
        }
    }

    private fun buildApiService(context: Context): LoveLetterApiService {
        val client = getOkHttpClient(context)
        val baseUrl = if (BuildConfig.SERVER_URL.endsWith("/")) {
            BuildConfig.SERVER_URL
        } else {
            "${BuildConfig.SERVER_URL}/"
        }

        val contentType = "application/json; charset=UTF-8".toMediaType()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()

        return retrofit.create(LoveLetterApiService::class.java)
    }
}
