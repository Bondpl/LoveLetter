package com.bond.loveletter.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ApiErrorCode {
    VALIDATION_ERROR,
    INVALID_INPUT,
    UNAUTHORIZED,
    TOKEN_REVOKED,
    FORBIDDEN,
    NOT_IN_PAIR,
    PAIR_NOT_FOUND,
    PAIR_FULL,
    PAIR_RACE_CONDITION,
    PAIR_GENERATION_FAILED,
    DRAWING_NOT_FOUND,
    INVALID_FILE_KEY,
    FILE_NOT_FOUND,
    FILE_TOO_LARGE,
    FCM_TOKEN_REQUIRED,
    RATE_LIMIT_EXCEEDED,
    INTERNAL_ERROR,
    UNKNOWN
}

@Serializable
data class MessageResponse(
    @SerialName("message") val message: String
)

@Serializable
data class ApiErrorResponse(
    @SerialName("code") val code: String? = null,
    @SerialName("error") val error: String? = null,
    @SerialName("status") val status: Int? = null
) {
    val apiErrorCode: ApiErrorCode
        get() = code?.let {
            try {
                ApiErrorCode.valueOf(it)
            } catch (_: Exception) {
                ApiErrorCode.UNKNOWN
            }
        } ?: ApiErrorCode.UNKNOWN
}

@Serializable
data class AnonymousRegisterRequest(
    @SerialName("publicKey") val publicKey: String
)

@Serializable
data class AnonymousAuthResponse(
    @SerialName("token") val token: String,
    @SerialName("pairCode") val pairCode: String,
    @SerialName("userId") val userId: String
)

@Serializable
data class JoinPairRequest(
    @SerialName("pairCode") val pairCode: String,
    @SerialName("publicKey") val publicKey: String
)

@Serializable
data class JoinPairResponse(
    @SerialName("token") val token: String,
    @SerialName("pairId") val pairId: String,
    @SerialName("userId") val userId: String,
    @SerialName("partnerPublicKey") val partnerPublicKey: String
)

@Serializable
data class PairStatusResponse(
    @SerialName("isPaired") val isPaired: Boolean,
    @SerialName("partnerPublicKey") val partnerPublicKey: String? = null,
    @SerialName("pairId") val pairId: String? = null
)

@Serializable
data class FcmTokenRequest(
    @SerialName("fcmToken") val fcmToken: String
)

@Serializable
data class PresignedUrlResponse(
    @SerialName("S3URL") val s3Url: String,
    @SerialName("filekey") val fileKey: String
)

@Serializable
data class ConfirmDrawingRequest(
    @SerialName("filekey") val fileKey: String,
    @SerialName("iv") val iv: String
)

@Serializable
data class DrawingData(
    @SerialName("imageUrl") val imageUrl: String,
    @SerialName("iv") val iv: String
)

@Serializable
data class GetDrawingResponse(
    @SerialName("drawingUrl") val drawingUrl: DrawingData
)
