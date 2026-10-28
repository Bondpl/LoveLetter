package com.bond.loveletter.data.repository

import com.bond.loveletter.data.crypto.CryptoManager
import com.bond.loveletter.data.model.ConfirmDrawingRequest
import com.bond.loveletter.data.network.LoveLetterApiService
import com.bond.loveletter.data.network.NetworkClient
import com.bond.loveletter.data.storage.SessionManager
import com.bond.loveletter.util.toUserFriendlyMessage
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response

class DrawingRepository(
    private val apiService: LoveLetterApiService,
    private val sessionManager: SessionManager
) {

    suspend fun sendDrawing(pngBytes: ByteArray): Result<Unit> {
        return try {
            val partnerPublicKey = sessionManager.getPartnerPublicKey()
            if (partnerPublicKey.isNullOrBlank()) {
                return Result.failure(IllegalStateException("Cannot send drawing: not paired with partner yet"))
            }

            val myPrivateKey = sessionManager.getMyPrivateKey()
            if (myPrivateKey.isNullOrBlank()) {
                return Result.failure(IllegalStateException("Cannot send drawing: missing cryptographic key"))
            }

            // 1. Encrypt drawing bytes using ECDH + AES-GCM
            val encryptedPayload = CryptoManager.encryptDrawing(
                plainBytes = pngBytes,
                partnerPublicKeyBase64 = partnerPublicKey,
                myPrivateKeyBase64 = myPrivateKey
            )

            // 2. Request presigned upload URL from server
            val presignedResponse = apiService.getPresignedUploadUrl()
            if (presignedResponse.code() == 403) {
                sessionManager.clearSession("You are not in an active pair. Your partner has disconnected đź’”")
                return Result.failure(IllegalStateException("You are not in an active pair. Your partner has disconnected đź’”"))
            }
            if (presignedResponse.code() == 429) {
                return Result.failure(Exception("You're sending drawings too fast"));
            }
            if (!presignedResponse.isSuccessful || presignedResponse.body() == null) {
                return Result.failure(Exception("Failed to get upload URL: ${parseError(presignedResponse)}"))
            }
            val presigned = presignedResponse.body()!!

            // 3. Direct upload encrypted bytes to S3 with exact Content-Type: image/png
            val requestBody = encryptedPayload.cipherBytes.toRequestBody("image/png".toMediaTypeOrNull())
            val s3Response = apiService.uploadToS3(
                s3Url = presigned.s3Url,
                contentType = "image/png",
                drawingBytes = requestBody
            )
            if (!s3Response.isSuccessful) {
                return Result.failure(Exception("S3 upload failed with HTTP ${s3Response.code()}"))
            }

            // 4. Confirm upload on backend with filekey and random iv
            val confirmResponse = apiService.confirmDrawingUpload(
                ConfirmDrawingRequest(
                    fileKey = presigned.fileKey,
                    iv = encryptedPayload.ivBase64
                )
            )
            if (confirmResponse.code() == 403) {
                sessionManager.clearSession("You are not in an active pair. Your partner has disconnected đź’”")
                return Result.failure(IllegalStateException("You are not in an active pair. Your partner has disconnected đź’”"))
            }
            if (!confirmResponse.isSuccessful) {
                return Result.failure(Exception("Confirmation failed: ${parseError(confirmResponse)}"))
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(e.toUserFriendlyMessage(), e))
        }
    }

    suspend fun getLatestDrawing(): Result<ByteArray?> {
        return try {
            // 1. Query backend for partner's latest drawing
            val response = apiService.getLatestDrawing()

            // 404 means no new drawing from partner (Section 7.2)
            if (response.code() == 404) {
                return Result.success(null)
            }

            if (response.code() == 403) {
                sessionManager.clearSession("You are not in an active pair. Your partner has disconnected đź’”")
                return Result.failure(IllegalStateException("You are not in an active pair. Your partner has disconnected đź’”"))
            }

            if (!response.isSuccessful || response.body() == null) {
                return Result.failure(Exception("Failed to fetch drawing: ${parseError(response)}"))
            }

            val drawingData = response.body()!!.drawingUrl

            // 2. Download encrypted payload from S3/Image URL
            val downloadResponse = apiService.downloadImage(drawingData.imageUrl)
            if (!downloadResponse.isSuccessful || downloadResponse.body() == null) {
                return Result.failure(Exception("Failed to download image payload: HTTP ${downloadResponse.code()}"))
            }

            val encryptedBytes = downloadResponse.body()!!.bytes()

            // 3. Decrypt using ECDH + AES-GCM
            val partnerPublicKey = sessionManager.getPartnerPublicKey()
            if (partnerPublicKey.isNullOrBlank()) {
                return Result.failure(IllegalStateException("Missing partner public key to decrypt drawing"))
            }

            val myPrivateKey = sessionManager.getMyPrivateKey()
            if (myPrivateKey.isNullOrBlank()) {
                return Result.failure(IllegalStateException("Missing private key to decrypt drawing"))
            }

            val decryptedBytes = CryptoManager.decryptDrawing(
                cipherBytes = encryptedBytes,
                ivBase64 = drawingData.iv,
                partnerPublicKeyBase64 = partnerPublicKey,
                myPrivateKeyBase64 = myPrivateKey
            )

            Result.success(decryptedBytes)
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
