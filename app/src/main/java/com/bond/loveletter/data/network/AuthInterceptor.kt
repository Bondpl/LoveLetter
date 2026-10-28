package com.bond.loveletter.data.network

import com.bond.loveletter.BuildConfig
import com.bond.loveletter.data.storage.SessionManager
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val sessionManager: SessionManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val path = originalRequest.url.encodedPath

        val isPublicAuth = path.endsWith("api/auth/anonymous") || path.endsWith("api/pairs/join")
        val isServerUrl = originalRequest.url.toString().startsWith(BuildConfig.SERVER_URL)

        val request = if (!isPublicAuth && isServerUrl) {
            val token = sessionManager.getToken()
            if (!token.isNullOrBlank()) {
                originalRequest.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            } else {
                originalRequest
            }
        } else {
            originalRequest
        }

        val response = chain.proceed(request)

        if (response.code == 401 && isServerUrl && !isPublicAuth) {
            sessionManager.onSessionExpired()
        }

        return response
    }
}
