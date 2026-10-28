package com.example.loveletter.data.repository

import com.example.loveletter.data.model.*
import com.example.loveletter.data.network.LoveLetterApiService
import com.example.loveletter.data.network.NetworkClient
import com.example.loveletter.data.storage.SessionManager
import com.example.loveletter.util.toUserFriendlyMessage
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.Response

class AuthRepository(
    private val apiService: LoveLetterApiService,
    private val sessionManager: SessionManager
) {

    suspend fun createPair(): Result<AnonymousAuthResponse> {
        return try {
            val (publicKey, _) = sessionManager.getOrCreateKeyPair()
            val response = apiService.registerAnonymous(AnonymousRegisterRequest(publicKey = publicKey))
            if (response.isSuccessful && response.body() != null) {
                val authData = response.body()!!
                sessionManager.savePairCreated(
                    token = authData.token,
                    userId = authData.userId,
                    pairCode = authData.pairCode
                )
                // Register FCM token if already cached
                sessionManager.getFcmToken()?.let { fcmToken ->
                    try {
                        apiService.updateFcmToken(FcmTokenRequest(fcmToken))
                    } catch (_: Exception) {}
                }
                Result.success(authData)
            } else {
                if ((response.code() == 401 || response.code() == 403 || response.code() == 404) && sessionManager.isPaired()) {
                    sessionManager.clearSession()
                }
                Result.failure(Exception(parseError(response)))
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.toUserFriendlyMessage(), e))
        }
    }

    suspend fun joinPair(pairCode: String): Result<JoinPairResponse> {
        return try {
            val cleanCode = pairCode.trim()
            if (cleanCode.isBlank()) {
                return Result.failure(IllegalArgumentException("Pair code cannot be empty"))
            }
            val (publicKey, _) = sessionManager.getOrCreateKeyPair()
            val response = apiService.joinPair(JoinPairRequest(pairCode = cleanCode, publicKey = publicKey))
            if (response.isSuccessful && response.body() != null) {
                val joinData = response.body()!!
                sessionManager.savePairJoined(
                    token = joinData.token,
                    userId = joinData.userId,
                    partnerPublicKey = joinData.partnerPublicKey
                )
                // Register FCM token if already cached
                sessionManager.getFcmToken()?.let { fcmToken ->
                    try {
                        apiService.updateFcmToken(FcmTokenRequest(fcmToken))
                    } catch (_: Exception) {}
                }
                Result.success(joinData)
            } else {
                if ((response.code() == 401 || response.code() == 403 || response.code() == 404) && sessionManager.isPaired()) {
                    sessionManager.clearSession()
                }
                Result.failure(Exception(parseError(response)))
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.toUserFriendlyMessage(), e))
        }
    }

    suspend fun checkPairStatus(): Result<PairStatusResponse> {
        return try {
            val response = apiService.getPairStatus()
            if (response.isSuccessful && response.body() != null) {
                val statusData = response.body()!!
                if (statusData.isPaired && !statusData.partnerPublicKey.isNullOrBlank()) {
                    sessionManager.updatePartnerConnected(statusData.partnerPublicKey)
                } else if (!statusData.isPaired && sessionManager.isPaired()) {
                    // Partner disconnected from the pair
                    sessionManager.clearSession()
                }
                Result.success(statusData)
            } else {
                if ((response.code() == 401 || response.code() == 403 || response.code() == 404) && sessionManager.isPaired()) {
                    sessionManager.clearSession()
                }
                Result.failure(Exception(parseError(response)))
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.toUserFriendlyMessage(), e))
        }
    }

    suspend fun leavePair(): Result<Unit> {
        return try {
            val response = apiService.leavePair()
            sessionManager.clearSession()
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.success(Unit) // Still clear local session on failure
            }
        } catch (e: Exception) {
            sessionManager.clearSession()
            Result.success(Unit)
        }
    }

    suspend fun updateFcmToken(token: String): Result<Unit> {
        sessionManager.setFcmToken(token)
        if (sessionManager.getToken().isNullOrBlank()) {
            return Result.success(Unit)
        }
        return try {
            val response = apiService.updateFcmToken(FcmTokenRequest(token))
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                if ((response.code() == 401 || response.code() == 403 || response.code() == 404) && sessionManager.isPaired()) {
                    sessionManager.clearSession()
                }
                Result.failure(Exception(parseError(response)))
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.toUserFriendlyMessage(), e))
        }
    }

    private fun <T> parseError(response: Response<T>): String {
        return try {
            val errorJson = response.errorBody()?.string()
            if (!errorJson.isNullOrBlank()) {
                val element = NetworkClient.json.parseToJsonElement(errorJson)
                val obj = element as? JsonObject
                val err = obj?.get("error")?.jsonPrimitive?.content
                err ?: errorJson
            } else {
                response.message().ifBlank { "HTTP error ${response.code()}" }
            }
        } catch (e: Exception) {
            response.message().ifBlank { "HTTP error ${response.code()}" }
        }
    }
}
