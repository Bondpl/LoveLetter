package com.bond.loveletter

import com.bond.loveletter.data.crypto.CryptoManager
import com.bond.loveletter.data.model.*
import com.bond.loveletter.data.network.LoveLetterApiService
import com.bond.loveletter.data.repository.DrawingRepository
import com.bond.loveletter.data.storage.SessionManager
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class FakeLoveLetterApiService : LoveLetterApiService {
    var lastUploadedBytes: ByteArray? = null
    var lastUploadedContentType: String? = null
    var lastConfirmedRequest: ConfirmDrawingRequest? = null
    var mockLatestDrawingResponse: Response<GetDrawingResponse>? = null
    var mockS3DownloadResponse: Response<ResponseBody>? = null

    override suspend fun registerAnonymous(request: AnonymousRegisterRequest): Response<AnonymousAuthResponse> {
        return Response.success(
            AnonymousAuthResponse(
                token = "jwt-user-a",
                pairCode = "654321",
                userId = "uuid-a"
            )
        )
    }

    override suspend fun joinPair(request: JoinPairRequest): Response<JoinPairResponse> {
        return Response.success(
            JoinPairResponse(
                token = "jwt-user-b",
                pairId = "pair-uuid",
                userId = "uuid-b",
                partnerPublicKey = "partner-pub-key"
            )
        )
    }

    override suspend fun getPairStatus(): Response<PairStatusResponse> {
        return Response.success(
            PairStatusResponse(
                isPaired = true,
                partnerPublicKey = "partner-pub-key"
            )
        )
    }

    override suspend fun leavePair(): Response<MessageResponse> {
        return Response.success(MessageResponse("Left pair successfully"))
    }

    override suspend fun updateFcmToken(request: FcmTokenRequest): Response<MessageResponse> {
        return Response.success(MessageResponse("FCM token saved successfully"))
    }

    override suspend fun getPresignedUploadUrl(): Response<PresignedUrlResponse> {
        return Response.success(
            PresignedUrlResponse(
                s3Url = "https://s3.fake.amazonaws.com/drawings/pair/12345.png",
                fileKey = "drawings/pair/12345.png"
            )
        )
    }

    override suspend fun uploadToS3(
        s3Url: String,
        contentType: String,
        drawingBytes: RequestBody
    ): Response<Unit> {
        lastUploadedContentType = contentType
        val buffer = okio.Buffer()
        drawingBytes.writeTo(buffer)
        lastUploadedBytes = buffer.readByteArray()
        return Response.success(Unit)
    }

    override suspend fun confirmDrawingUpload(request: ConfirmDrawingRequest): Response<MessageResponse> {
        lastConfirmedRequest = request
        return Response.success(MessageResponse("Upload confirmed."))
    }

    override suspend fun getLatestDrawing(): Response<GetDrawingResponse> {
        return mockLatestDrawingResponse
            ?: Response.error(404, "{\"error\": \"No new drawings found\", \"status\": 404}".toResponseBody("application/json".toMediaTypeOrNull()))
    }

    override suspend fun downloadImage(imageUrl: String): Response<ResponseBody> {
        return mockS3DownloadResponse
            ?: Response.error(404, "Not Found".toResponseBody("text/plain".toMediaTypeOrNull()))
    }
}

class FakeSessionManager {
    var token: String? = null
    var myPublicKey: String? = null
    var myPrivateKey: String? = null
    var partnerPublicKey: String? = null
    var isPaired: Boolean = false
}

class IntegrationFlowTest {

    @Test
    fun testFullDrawingUploadAndDownloadE2EEFlow() {
        // User A and User B keypairs
        val keyPairA = CryptoManager.generateKeyPair()
        val keyPairB = CryptoManager.generateKeyPair()

        // Set up fake API service
        val fakeApi = FakeLoveLetterApiService()

        // User A wants to send a drawing to User B
        val originalDrawingBytes = "HEART_DRAWING_PNG_IMAGE_BYTES_12345".toByteArray(Charsets.UTF_8)

        // 1. Encrypt drawing by User A for User B
        val encryptedPayload = CryptoManager.encryptDrawing(
            plainBytes = originalDrawingBytes,
            partnerPublicKeyBase64 = keyPairB.publicKeyBase64,
            myPrivateKeyBase64 = keyPairA.privateKeyBase64
        )
        assertNotNull(encryptedPayload.cipherBytes)
        assertNotNull(encryptedPayload.ivBase64)

        // 2. User A gets presigned URL
        val presigned = kotlinx.coroutines.runBlocking { fakeApi.getPresignedUploadUrl() }.body()!!
        assertEquals("https://s3.fake.amazonaws.com/drawings/pair/12345.png", presigned.s3Url)
        assertEquals("drawings/pair/12345.png", presigned.fileKey)

        // 3. User A uploads to S3 with Content-Type: image/png
        val requestBody = encryptedPayload.cipherBytes.toRequestBody("image/png".toMediaTypeOrNull())
        val s3Response = kotlinx.coroutines.runBlocking {
            fakeApi.uploadToS3(presigned.s3Url, "image/png", requestBody)
        }
        assertTrue(s3Response.isSuccessful)
        assertEquals("image/png", fakeApi.lastUploadedContentType)
        assertArrayEquals(encryptedPayload.cipherBytes, fakeApi.lastUploadedBytes)

        // 4. User A confirms upload with filekey and IV
        val confirmResponse = kotlinx.coroutines.runBlocking {
            fakeApi.confirmDrawingUpload(
                ConfirmDrawingRequest(
                    fileKey = presigned.fileKey,
                    iv = encryptedPayload.ivBase64
                )
            )
        }
        assertTrue(confirmResponse.isSuccessful)
        assertEquals("drawings/pair/12345.png", fakeApi.lastConfirmedRequest?.fileKey)
        assertEquals(encryptedPayload.ivBase64, fakeApi.lastConfirmedRequest?.iv)

        // 5. Now User B retrieves drawing
        fakeApi.mockLatestDrawingResponse = Response.success(
            GetDrawingResponse(
                DrawingData(
                    imageUrl = "https://s3.fake.amazonaws.com/drawings/pair/12345.png",
                    iv = encryptedPayload.ivBase64
                )
            )
        )
        fakeApi.mockS3DownloadResponse = Response.success(
            encryptedPayload.cipherBytes.toResponseBody("image/png".toMediaTypeOrNull())
        )

        val latestDrawingResp = kotlinx.coroutines.runBlocking { fakeApi.getLatestDrawing() }
        assertTrue(latestDrawingResp.isSuccessful)
        val drawingData = latestDrawingResp.body()!!.drawingUrl

        val downloadResp = kotlinx.coroutines.runBlocking { fakeApi.downloadImage(drawingData.imageUrl) }
        assertTrue(downloadResp.isSuccessful)
        val downloadedEncryptedBytes = downloadResp.body()!!.bytes()

        // 6. User B decrypts drawing using User B's private key and User A's public key
        val decryptedBytes = CryptoManager.decryptDrawing(
            cipherBytes = downloadedEncryptedBytes,
            ivBase64 = drawingData.iv,
            partnerPublicKeyBase64 = keyPairA.publicKeyBase64,
            myPrivateKeyBase64 = keyPairB.privateKeyBase64
        )

        assertArrayEquals(originalDrawingBytes, decryptedBytes)
        assertEquals("HEART_DRAWING_PNG_IMAGE_BYTES_12345", String(decryptedBytes, Charsets.UTF_8))
    }

    @Test
    fun test404HandlingWhenNoDrawings() {
        val fakeApi = FakeLoveLetterApiService()
        fakeApi.mockLatestDrawingResponse = Response.error(
            404,
            "{\"error\": \"No new drawings found\", \"status\": 404}".toResponseBody("application/json".toMediaTypeOrNull())
        )

        val resp = kotlinx.coroutines.runBlocking { fakeApi.getLatestDrawing() }
        assertEquals(404, resp.code())
        assertFalse(resp.isSuccessful)
    }
}
