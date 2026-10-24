package com.example.loveletter

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object CanvasStorage {
    private const val FILE_NAME = "drawing_history.json"
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
    }

    suspend fun saveHistory(context: Context, history: List<DrawingState>) = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            val tmp = File(context.filesDir, "$FILE_NAME.tmp")
            tmp.writeText(json.encodeToString(history))
            tmp.renameTo(file)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun loadHistory(context: Context): List<DrawingState> = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return@withContext emptyList()
            json.decodeFromString<List<DrawingState>>(file.readText())
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
