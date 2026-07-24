package com.example.loveletter

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object CanvasStorage {

    private const val FILE_NAME = "strokes.json"

    suspend fun savePaths(context: Context, paths: List<PathData>) = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            val tmp = File(context.filesDir, "$FILE_NAME.tmp")

            val arr = JSONArray()
            paths.forEach { p ->
                val obj = JSONObject()
                obj.put("id", p.id)
                obj.put("color", p.color.toArgb())
                obj.put("thickness", p.thickness)
                obj.put("brushType", p.brushType.name)

                val points = JSONArray()
                p.path.forEach { pt ->
                    val ptObj = JSONObject()
                    ptObj.put("x", pt.x)
                    ptObj.put("y", pt.y)
                    points.put(ptObj)
                }
                obj.put("path", points)
                arr.put(obj)
            }

            tmp.writeText(arr.toString())
            tmp.renameTo(file)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun loadPaths(context: Context): List<PathData> = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return@withContext emptyList()

            val arr = JSONArray(file.readText())
            val list = mutableListOf<PathData>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.getString("id")
                val colorInt = obj.getInt("color")
                val thickness = obj.getDouble("thickness").toFloat()
                val brushType = BrushType.valueOf(obj.getString("brushType"))

                val pointsArr = obj.getJSONArray("path")
                val points = mutableListOf<Offset>()
                for (j in 0 until pointsArr.length()) {
                    val pt = pointsArr.getJSONObject(j)
                    points.add(Offset(pt.getDouble("x").toFloat(), pt.getDouble("y").toFloat()))
                }

                list.add(PathData(id = id, color = Color(colorInt), path = points, thickness = thickness, brushType = brushType))
            }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
