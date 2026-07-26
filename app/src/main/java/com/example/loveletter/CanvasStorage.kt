package com.example.loveletter

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.gson.Gson
import java.io.File

object CanvasStorage {
    private const val FILE_NAME = "drawing_history.json"
    private val gson = Gson()

    suspend fun saveHistory(context: Context, history: List<DrawingState>) = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            val tmp = File(context.filesDir, "$FILE_NAME.tmp")
            tmp.writeText(gson.toJson(history))
            tmp.renameTo(file)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun loadHistory(context: Context): List<DrawingState> = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return@withContext emptyList()
            gson.fromJson(file.readText(), Array<DrawingState>::class.java).toList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
