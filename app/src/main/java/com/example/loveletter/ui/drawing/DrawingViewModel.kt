package com.example.loveletter.ui.drawing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.loveletter.data.model.ColorSerializer
import com.example.loveletter.data.model.OffsetSerializer
import com.example.loveletter.data.repository.DrawingRepository
import com.example.loveletter.data.storage.CanvasStorage
import com.example.loveletter.data.storage.SessionManager
import com.example.loveletter.di.NetworkModule
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
enum class BrushType { DASHED, CONTINUOUS, MARKER, HIGHLIGHTER }

val flattenedColors = listOf(
    // Neutrals
    Color(0xFF000000), // black
    Color(0xFFB0B0B0), // grey
    Color(0xFFFFFFFF), // pure white (matches canvas)

    // Reds & Pinks
    Color(0xFFFF0000), // red
    Color(0xFFEB5050), // cherry red
    Color(0xFFF765B8), // pink
    Color(0xFFFF00FF), // magenta

    // Purples
    Color(0xFF5638B5), // purple

    // Blues
    Color(0xFF15268F), // navy blue
    Color(0xFF0000FF), // blue
    Color(0xFF2B98E0), // sky blue
    Color(0xFF84C0E8), // light opalite

    // Greens (grouped together)
    Color(0xFF1F660F), // forest green
    Color(0xFF00AA00), // green
    Color(0xFF4FAB61), // grass green

    // Yellows
    Color(0xFFFFFF00), // yellow
    Color(0xFFFAEA34), // banana yellow

    // Oranges & Browns
    Color(0xFFEB9E1E), // orange
    Color(0xFF5C3705)  // brown
)

@Serializable
data class PathData(
    val id: String,
    @Serializable(with = ColorSerializer::class)
    val color: Color,
    val path: List<@Serializable(with = OffsetSerializer::class) Offset>,
    val thickness: Float = 20f,
    val brushType: BrushType = BrushType.MARKER
)

@Serializable
data class DrawingState(
    @Serializable(with = ColorSerializer::class)
    val selectedColor: Color = Color(0xFF000000),
    val currentPath: PathData? = null,
    val paths: List<PathData> = emptyList(),
    @Serializable(with = ColorSerializer::class)
    val backgroundColor: Color = Color.White,
    val brushThickness: Float = 20f,
    val brushType: BrushType = BrushType.MARKER
)

sealed interface DrawingAction {
    data class OnSelectColor(val color: Color) : DrawingAction
    data class OnDrawingStart(val offset: Offset) : DrawingAction
    data class OnDraw(val offset: Offset) : DrawingAction
    data object OnDrawingEnd : DrawingAction
    data object OnUndo : DrawingAction
    data object OnClearCanvasClick : DrawingAction
    data class OnFill(val color: Color) : DrawingAction
    data class OnSetBrushSize(val size: Float) : DrawingAction
    data class OnSetBrushType(val brushType: BrushType) : DrawingAction
}

class DrawingViewModel(
    private val repository: DrawingRepository = NetworkModule.getDrawingRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(DrawingState())
    val state = _state.asStateFlow()

    private val history = mutableListOf<DrawingState>()

    private val _isSending = MutableStateFlow(false)
    val isSending = _isSending.asStateFlow()

    private val _isFetching = MutableStateFlow(false)
    val isFetching = _isFetching.asStateFlow()

    private val _downloadedImage = MutableStateFlow<ByteArray?>(null)
    val downloadedImage = _downloadedImage.asStateFlow()

    fun onAction(action: DrawingAction) {
        when (action) {
            is DrawingAction.OnSelectColor -> {
                _state.update { it.copy(selectedColor = action.color) }
            }
            is DrawingAction.OnDrawingStart -> {
                saveToHistory()
                val newPath = PathData(
                    id = java.util.UUID.randomUUID().toString(),
                    color = _state.value.selectedColor,
                    path = listOf(action.offset),
                    thickness = _state.value.brushThickness,
                    brushType = _state.value.brushType
                )
                _state.update { it.copy(currentPath = newPath) }
            }
            is DrawingAction.OnDraw -> {
                _state.update { currentState ->
                    val updatedList = (currentState.currentPath?.path ?: emptyList()) + action.offset
                    currentState.copy(
                        currentPath = currentState.currentPath?.copy(path = updatedList)
                    )
                }
            }
            DrawingAction.OnDrawingEnd -> {
                _state.update { currentState ->
                    val current = currentState.currentPath
                    if (current != null) {
                        currentState.copy(
                            paths = currentState.paths + current,
                            currentPath = null
                        )
                    } else {
                        currentState
                    }
                }
            }
            DrawingAction.OnUndo -> {
                if (history.isNotEmpty()) {
                    val lastState = history.removeAt(history.size - 1)
                    _state.value = lastState
                }
            }
            DrawingAction.OnClearCanvasClick -> {
                saveToHistory()
                _state.update {
                    it.copy(
                        paths = emptyList(),
                        currentPath = null,
                        backgroundColor = Color.White
                    )
                }
            }
            is DrawingAction.OnFill -> {
                saveToHistory()
                _state.update { it.copy(backgroundColor = action.color) }
            }
            is DrawingAction.OnSetBrushSize -> {
                _state.update { it.copy(brushThickness = action.size) }
            }
            is DrawingAction.OnSetBrushType -> {
                _state.update { it.copy(brushType = action.brushType) }
            }
        }
    }

    private fun saveToHistory() {
        if (history.size >= 30) {
            history.removeAt(0)
        }
        history.add(_state.value)
    }

    fun getHistoryToSave(): List<DrawingState> {
        return history + listOf(_state.value)
    }

    fun restoreHistory(savedHistory: List<DrawingState>) {
        if (savedHistory.isEmpty()) return
        history.clear()
        if (savedHistory.size > 1) {
            history.addAll(savedHistory.subList(0, savedHistory.size - 1))
        }
        _state.value = savedHistory.last()
    }

    fun loadSavedHistory(context: android.content.Context) {
        viewModelScope.launch {
            try {
                val savedHistory = CanvasStorage.loadHistory(context)
                if (savedHistory.isNotEmpty()) {
                    restoreHistory(savedHistory)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clearAll(context: android.content.Context) {
        history.clear()
        _state.value = DrawingState()
        _downloadedImage.value = null
        viewModelScope.launch {
            CanvasStorage.clearHistory(context)
        }
    }

    suspend fun uploadDrawing(pngByteArray: ByteArray): Result<Unit> {
        _isSending.value = true
        return try {
            val result = repository.sendDrawing(pngByteArray)
            delay(1000L)
            result
        } catch (e: Exception) {
            delay(1000L)
            Result.failure(e)
        } finally {
            _isSending.value = false
        }
    }

    fun fetchLatestDrawing() {
        viewModelScope.launch {
            _isFetching.value = true
            try {
                val result = repository.getLatestDrawing()
                result.onSuccess { bytes ->
                    if (bytes != null) {
                        _downloadedImage.value = bytes
                    }
                }
            } finally {
                _isFetching.value = false
            }
        }
    }

    fun loadCachedImage(context: android.content.Context) {
        if (_downloadedImage.value != null) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val file = java.io.File(context.filesDir, "latest_widget_image.png")
                if (file.exists()) {
                    val bytes = file.readBytes()
                    if (bytes.isNotEmpty()) {
                        _downloadedImage.value = bytes
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun onDrawingReceived(imageBytes: ByteArray) {
        _downloadedImage.value = imageBytes
    }
}
