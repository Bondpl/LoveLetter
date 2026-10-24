package com.example.loveletter.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Uniwersalna odpowiedź z komunikatem
@Serializable
data class MessageResponse(
    @SerialName("message") val message: String
)

// Uniwersalna struktura błędu
@Serializable
data class ApiErrorResponse(
    @SerialName("error") val error: String,
    @SerialName("status") val status: Int
)

// Rejestracja anonimowa
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

// Dołączanie do pary
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

// Status pary
@Serializable
data class PairStatusResponse(
    @SerialName("isPaired") val isPaired: Boolean,
    @SerialName("partnerPublicKey") val partnerPublicKey: String? = null
)

// FCM
@Serializable
data class FcmTokenRequest(
    @SerialName("fcmToken") val fcmToken: String
)

// Upload rysunku
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

// Pobranie rysunku
@Serializable
data class DrawingData(
    @SerialName("imageUrl") val imageUrl: String,
    @SerialName("iv") val iv: String
)

@Serializable
data class GetDrawingResponse(
    @SerialName("drawingUrl") val drawingUrl: DrawingData
)
