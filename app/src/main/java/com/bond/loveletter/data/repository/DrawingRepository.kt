package com.bond.loveletter.data.repository

import com.bond.loveletter.data.crypto.CryptoManager
import com.bond.loveletter.data.model.ApiErrorCode
import com.bond.loveletter.data.model.ConfirmDrawingRequest
import com.bond.loveletter.data.network.LoveLetterApiService
import com.bond.loveletter.data.storage.SessionManager
import com.bond.loveletter.util.AppException
import com.bond.loveletter.util.DrawingPayloadUtils
import com.bond.loveletter.util.ImageCompressor
import com.bond.loveletter.util.toApiException
import com.bond.loveletter.util.toAppException
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class DrawingRepository(
    private val apiService: LoveLetterApiService,
    private val sessionManager: SessionManager
) {

    suspend fun sendDrawing(pngBytes: ByteArray): Result<Unit> {
        return sendEncryptedPayload(pngBytes)
    }

    suspend fun sendPhoto(photoBytes: ByteArray, isViewOnce: Boolean = false): Result<Unit> {
        if (photoBytes.size > ImageCompressor.MAX_FILE_SIZE_BYTES) {
            return Result.failure(AppException.General("Photo is too large (exceeds 5 MB limit)"))
        }
        val payload = DrawingPayloadUtils.wrapPhotoPayload(photoBytes, isViewOnce)
        return sendEncryptedPayload(payload)
    }

    private suspend fun sendEncryptedPayload(plainBytes: ByteArray): Result<Unit> {
        return try {
            val partnerPublicKey = sessionManager.getPartnerPublicKey()
            if (partnerPublicKey.isNullOrBlank()) {
                return Result.failure(AppException.General("Cannot send: not paired with partner yet"))
            }

            val myPrivateKey = sessionManager.getMyPrivateKey()
            if (myPrivateKey.isNullOrBlank()) {
                return Result.failure(AppException.General("Cannot send: missing cryptographic key"))
            }

            // 1. Encrypt payload bytes using ECDH + AES-GCM
            val encryptedPayload = CryptoManager.encryptDrawing(
                plainBytes = plainBytes,
                partnerPublicKeyBase64 = partnerPublicKey,
                myPrivateKeyBase64 = myPrivateKey
            )

            // 2. Request presigned upload URL from server
            val presignedResponse = apiService.getPresignedUploadUrl()
            if (!presignedResponse.isSuccessful || presignedResponse.body() == null) {
                val apiEx = presignedResponse.toApiException()
                if (apiEx.errorCode == ApiErrorCode.NOT_IN_PAIR) {
                    sessionManager.clearSession("You are not in an active pair. Your partner has disconnected")
                    return Result.failure(AppException.PartnerDisconnected())
                }
                return Result.failure(apiEx)
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
                return Result.failure(AppException.General("Upload failed with HTTP ${s3Response.code()}"))
            }

            // 4. Confirm upload on backend with filekey and random iv
            val confirmResponse = apiService.confirmDrawingUpload(
                ConfirmDrawingRequest(
                    fileKey = presigned.fileKey,
                    iv = encryptedPayload.ivBase64
                )
            )
            if (!confirmResponse.isSuccessful) {
                val apiEx = confirmResponse.toApiException()
                if (apiEx.errorCode == ApiErrorCode.NOT_IN_PAIR) {
                    sessionManager.clearSession("You are not in an active pair. Your partner has disconnected")
                    return Result.failure(AppException.PartnerDisconnected())
                }
                return Result.failure(apiEx)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e.toAppException())
        }
    }

    suspend fun getLatestDrawing(): Result<ByteArray?> {
        return try {
            // 1. Query backend for partner's latest drawing
            val response = apiService.getLatestDrawing()

            // 404 means no new drawing from partner
            if (response.code() == 404) {
                return Result.success(null)
            }

            if (!response.isSuccessful || response.body() == null) {
                val apiEx = response.toApiException()
                if (apiEx.errorCode == ApiErrorCode.NOT_IN_PAIR) {
                    sessionManager.clearSession("You are not in an active pair. Your partner has disconnected")
                    return Result.failure(AppException.PartnerDisconnected())
                }
                return Result.failure(apiEx)
            }

            val drawingData = response.body()!!.drawingUrl

            // 2. Download encrypted payload from S3/Image URL
            val downloadResponse = apiService.downloadImage(drawingData.imageUrl)
            if (!downloadResponse.isSuccessful || downloadResponse.body() == null) {
                return Result.failure(AppException.General("Failed to download image payload: HTTP ${downloadResponse.code()}"))
            }

            val encryptedBytes = downloadResponse.body()!!.bytes()

            // 3. Decrypt using ECDH + AES-GCM
            val partnerPublicKey = sessionManager.getPartnerPublicKey()
            if (partnerPublicKey.isNullOrBlank()) {
                return Result.failure(AppException.General("Missing partner public key to decrypt drawing"))
            }

            val myPrivateKey = sessionManager.getMyPrivateKey()
            if (myPrivateKey.isNullOrBlank()) {
                return Result.failure(AppException.General("Missing private key to decrypt drawing"))
            }

            val decryptedBytes = CryptoManager.decryptDrawing(
                cipherBytes = encryptedBytes,
                ivBase64 = drawingData.iv,
                partnerPublicKeyBase64 = partnerPublicKey,
                myPrivateKeyBase64 = myPrivateKey
            )

            Result.success(decryptedBytes)
        } catch (e: Exception) {
            Result.failure(e.toAppException())
        }
    }
}
