package com.bond.loveletter.data.repository

import com.bond.loveletter.data.model.*
import com.bond.loveletter.data.network.LoveLetterApiService
import com.bond.loveletter.data.storage.SessionManager
import com.bond.loveletter.util.AppException
import com.bond.loveletter.util.toApiException
import com.bond.loveletter.util.toAppException

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
                val apiEx = response.toApiException()
                Result.failure(apiEx)
            }
        } catch (e: Exception) {
            Result.failure(e.toAppException())
        }
    }

    suspend fun joinPair(pairCode: String): Result<JoinPairResponse> {
        val cleanCode = pairCode.trim()
        if (cleanCode.length != 6) {
            return Result.failure(AppException.General("Pair code must be 6 digits"))
        }

        return try {
            val (publicKey, _) = sessionManager.getOrCreateKeyPair()
            val response = apiService.joinPair(JoinPairRequest(pairCode = cleanCode, publicKey = publicKey))
            if (response.isSuccessful && response.body() != null) {
                val joinData = response.body()!!
                sessionManager.savePairJoined(
                    token = joinData.token,
                    userId = joinData.userId,
                    partnerPublicKey = joinData.partnerPublicKey,
                    pairId = joinData.pairId
                )
                // Register FCM token if already cached
                sessionManager.getFcmToken()?.let { fcmToken ->
                    try {
                        apiService.updateFcmToken(FcmTokenRequest(fcmToken))
                    } catch (_: Exception) {}
                }
                Result.success(joinData)
            } else {
                val apiEx = response.toApiException()
                Result.failure(apiEx)
            }
        } catch (e: Exception) {
            Result.failure(e.toAppException())
        }
    }

    suspend fun checkPairStatus(): Result<PairStatusResponse> {
        return try {
            val response = apiService.getPairStatus()
            if (response.isSuccessful && response.body() != null) {
                val statusData = response.body()!!
                if (statusData.isPaired && !statusData.partnerPublicKey.isNullOrBlank()) {
                    sessionManager.updatePartnerConnected(
                        partnerPublicKey = statusData.partnerPublicKey,
                        pairId = statusData.pairId
                    )
                } else if (!statusData.isPaired && sessionManager.isPaired()) {
                    // Partner disconnected from the pair
                    sessionManager.clearSession()
                } else if (statusData.isPaired && !statusData.pairId.isNullOrBlank()) {
                    sessionManager.savePairId(statusData.pairId)
                }
                Result.success(statusData)
            } else {
                val apiEx = response.toApiException()
                if ((apiEx.errorCode == ApiErrorCode.NOT_IN_PAIR || apiEx.errorCode == ApiErrorCode.TOKEN_REVOKED) && sessionManager.isPaired()) {
                    sessionManager.clearSession("Your partner has disconnected or session expired")
                }
                Result.failure(apiEx)
            }
        } catch (e: Exception) {
            Result.failure(e.toAppException())
        }
    }

    suspend fun leavePair(): Result<Unit> {
        return try {
            apiService.leavePair()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e.toAppException())
        } finally {
            sessionManager.clearSession()
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
                val apiEx = response.toApiException()
                if ((apiEx.errorCode == ApiErrorCode.NOT_IN_PAIR || apiEx.errorCode == ApiErrorCode.TOKEN_REVOKED) && sessionManager.isPaired()) {
                    sessionManager.clearSession()
                }
                Result.failure(apiEx)
            }
        } catch (e: Exception) {
            Result.failure(e.toAppException())
        }
    }
}
