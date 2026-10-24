package com.example.loveletter

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.loveletter.data.repository.DrawingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
enum class BrushType { DASHED, CONTINUOUS, MARKER, HIGHLIGHTER }

val flattenedColors = listOf(
    Color(0xFFFF0000), // red
    Color(0xFF2B98E0), // sky
    Color(0xFF5638B5), // purple
    Color(0xFF00AA00), // green
    Color(0xFFFAEA34), // banana
    Color(0xFFEB9E1E), // orange
    Color(0xFF5C3705), // brown
    Color(0xFF000000), // black
    Color(0xFFB0B0B0), // grey
    Color(0xFFFAFAFA), // white
    Color(0xFFEB5050), // cherry
    Color(0xFFF765B8), // pink
    Color(0xFF0000FF), // blue
    Color(0xFF84C0E8), // opalite
    Color(0xFF15268F), // navy
    Color(0xFF4FAB61), // grass
    Color(0xFF1F660F), // forest
    Color(0xFFFFFF00), // yellow
    Color(0xFFFF00FF), // magenta
    Color(0xFF123456)  // sentinel -> tools icon
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
    data class OnNewPathStart(val initialOffset: Offset? = null) : DrawingAction
    data class OnDraw(val offset: Offset) : DrawingAction
    data class OnTap(val offset: Offset) : DrawingAction
    data object OnPathEnd : DrawingAction
    data class OnSelectColor(val color: Color) : DrawingAction
    data object OnClearCanvasClick : DrawingAction
    data object OnUndo : DrawingAction
    data class OnFill(val color: Color) : DrawingAction
    data class OnSetBrushSize(val size: Float) : DrawingAction
    data class OnSetBrushType(val type: BrushType) : DrawingAction
}

class DrawingViewModel : ViewModel() {
    private val repository: DrawingRepository = NetworkModule.getDrawingRepository()

    private val _state = MutableStateFlow(DrawingState())
    val state = _state.asStateFlow()

    private val _downloadedImage = MutableStateFlow<ByteArray?>(null)
    val downloadedImage = _downloadedImage.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending = _isSending.asStateFlow()

    private val _isFetching = MutableStateFlow(false)
    val isFetching = _isFetching.asStateFlow()

    init {
        viewModelScope.launch {
            DrawingUpdateBus.drawingUpdates.collect { bytes ->
                _downloadedImage.value = bytes
            }
        }
    }

    private val history = mutableListOf<DrawingState>()

    fun getHistory(): List<DrawingState> = getHistoryToSave()

    fun getHistoryToSave(): List<DrawingState> {
        val currentState = state.value
        return if (currentState.paths.isNotEmpty() || currentState.backgroundColor != Color.White) {
            history + listOf(currentState)
        } else {
            history.toList()
        }
    }

    private fun pushHistory() {
        history.add(state.value.copy(paths = state.value.paths.toList()))
        if (history.size > 50) history.removeAt(0)
    }

    fun onAction(action: DrawingAction) {
        when(action) {
            DrawingAction.OnClearCanvasClick -> {
                pushHistory()
                onClearCanvasClick()
            }
            DrawingAction.OnUndo -> onUndo()
            is DrawingAction.OnDraw -> onDraw(action.offset)
            is DrawingAction.OnNewPathStart -> {
                pushHistory()
                onNewPathStart(action.initialOffset)
            }
            is DrawingAction.OnTap -> onTap(action.offset)
            DrawingAction.OnPathEnd -> onPathEnd()
            is DrawingAction.OnSelectColor -> onSelectColor(action.color)
            is DrawingAction.OnFill -> {
                pushHistory()
                onFill(action.color)
            }
            is DrawingAction.OnSetBrushSize -> onSetBrushSize(action.size)
            is DrawingAction.OnSetBrushType -> onSetBrushType(action.type)
        }
    }

    private fun onSelectColor(color: Color) {
        _state.update { it.copy(selectedColor = color) }
    }

    private fun onSetBrushSize(size: Float) {
        _state.update { it.copy(brushThickness = size) }
    }

    private fun onSetBrushType(type: BrushType) {
        _state.update { it.copy(brushType = type) }
    }

    private fun onFill(color: Color) {
        _state.update { it.copy(backgroundColor = color, paths = emptyList()) }
    }

    private fun onUndo() {
        if (history.isNotEmpty()) {
            val last = history.removeAt(history.size - 1)
            _state.update { last }
        }
    }

    private fun onPathEnd() {
        val currentPathData = state.value.currentPath ?: return
        _state.update { it.copy(currentPath = null, paths = it.paths + currentPathData) }
    }

    private fun onNewPathStart(initialOffset: Offset? = null) {
        val initialPoints = if (initialOffset != null) listOf(initialOffset) else emptyList()
        _state.update {
            it.copy(
                currentPath = PathData(
                    id = System.currentTimeMillis().toString(),
                    color = it.selectedColor,
                    path = initialPoints,
                    thickness = it.brushThickness,
                    brushType = it.brushType
                )
            )
        }
    }

    private fun onTap(offset: Offset) {
        pushHistory()
        val tapPath = PathData(
            id = System.currentTimeMillis().toString(),
            color = state.value.selectedColor,
            path = listOf(offset),
            thickness = state.value.brushThickness,
            brushType = state.value.brushType
        )
        _state.update { it.copy(paths = it.paths + tapPath) }
    }

    private fun onDraw(offset: Offset) {
        val currentPathData = state.value.currentPath ?: return
        _state.update { it.copy(currentPath = currentPathData.copy(path = currentPathData.path + offset)) }
    }

    private fun onClearCanvasClick() {
        _state.update { it.copy(currentPath = null, paths = emptyList(), backgroundColor = Color.White) }
    }

    suspend fun uploadDrawing(pngByteArray: ByteArray): Result<Unit> {
        _isSending.value = true
        return try {
            repository.sendDrawing(pngByteArray)
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

    fun restoreHistory(savedHistory: List<DrawingState>) {
        if (savedHistory.isEmpty()) return
        history.clear()
        if (savedHistory.size > 1) {
            history.addAll(savedHistory.subList(0, savedHistory.size - 1))
        }
        _state.value = savedHistory.last()
    }

    fun loadSavedHistory(context: android.content.Context) {
        if (_state.value.paths.isNotEmpty()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val loaded = CanvasStorage.loadHistory(context)
                if (loaded.isNotEmpty()) {
                    restoreHistory(loaded)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clearAll(context: android.content.Context) {
        _state.value = DrawingState()
        _downloadedImage.value = null
        DrawingUpdateBus.clear()
        history.clear()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                CanvasStorage.clearHistory(context)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            LoveLetterWidget.clearWidget(context)
        }
    }
}
