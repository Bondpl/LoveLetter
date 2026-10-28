package com.bond.loveletter.data.network

import com.bond.loveletter.data.model.*
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface LoveLetterApiService {

    // --- Auth & Pairing ---

    @POST("api/auth/anonymous")
    suspend fun registerAnonymous(
        @Body request: AnonymousRegisterRequest
    ): Response<AnonymousAuthResponse>

    @POST("api/pairs/join")
    suspend fun joinPair(
        @Body request: JoinPairRequest
    ): Response<JoinPairResponse>

    @GET("api/pairs/status")
    suspend fun getPairStatus(): Response<PairStatusResponse>

    @POST("api/pairs/leave")
    suspend fun leavePair(): Response<MessageResponse>

    // --- FCM ---

    @POST("api/fcm-token")
    suspend fun updateFcmToken(
        @Body request: FcmTokenRequest
    ): Response<MessageResponse>

    // --- Drawings ---

    @POST("api/drawings")
    suspend fun getPresignedUploadUrl(): Response<PresignedUrlResponse>

    @POST("api/drawings/confirm")
    suspend fun confirmDrawingUpload(
        @Body request: ConfirmDrawingRequest
    ): Response<MessageResponse>

    @GET("api/drawings")
    suspend fun getLatestDrawing(): Response<GetDrawingResponse>

    // --- Direct S3 Upload ---

    @PUT
    suspend fun uploadToS3(
        @Url s3Url: String,
        @Header("Content-Type") contentType: String = "image/png",
        @Body drawingBytes: RequestBody
    ): Response<Unit>

    // --- Direct Image Download ---

    @GET
    suspend fun downloadImage(
        @Url imageUrl: String
    ): Response<ResponseBody>
}
