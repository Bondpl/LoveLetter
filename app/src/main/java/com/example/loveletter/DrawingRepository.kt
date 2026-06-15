package com.example.loveletter

import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.http.*

class DrawingRepository (private val client: HttpClient){
    private val serverUrl = BuildConfig.SERVER_URL + "/api/drawings"
    private val apiKey = BuildConfig.API_KEY
    suspend fun sendDrawing(drawing: ByteArray){
        client.post(serverUrl){
            header("api_key", apiKey)
            contentType(ContentType.Image.PNG)
            setBody(drawing)
        }
    }
}