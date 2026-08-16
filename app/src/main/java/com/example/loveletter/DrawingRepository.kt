package com.example.loveletter

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.*

class DrawingRepository (private val client: HttpClient){
    private val serverUrl = BuildConfig.SERVER_URL + "/api/drawings"
    private val apiKey = BuildConfig.API_KEY
    suspend fun sendDrawing(drawing: ByteArray): Boolean {
        val response = client.post(serverUrl) {
            header("api_key", apiKey)
            contentType(ContentType.Image.PNG)
            setBody(drawing)
        }
        return response.status.value in 200..299
    }

    suspend fun getLatestDrawing(): ByteArray? {
        return try {
            val response = client.get(serverUrl) {
                header("api_key", apiKey)
            }
            if (response.status.value == 200) {
                response.body<ByteArray>()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}