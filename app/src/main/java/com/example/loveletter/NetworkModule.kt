package com.example.loveletter

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*

object NetworkModule {
    val client by lazy {
        HttpClient(OkHttp) {
            engine {
                config {
                    followRedirects(true)
                    connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                }
            }
        }
    }
}