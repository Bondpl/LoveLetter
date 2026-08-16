package com.example.loveletter

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

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

data class PathData(
    val id: String,
    val color: Color,
    val path: List<Offset>,
    val thickness: Float = 20f,
    val brushType: BrushType = BrushType.MARKER
)

data class DrawingState(
    val selectedColor: Color = Color(0xFF000000),
    val currentPath: PathData? = null,
    val paths: List<PathData> = emptyList(),
    val backgroundColor: Color = Color.White,
    val brushThickness: Float = 20f,
    val brushType: BrushType = BrushType.MARKER
)

sealed interface DrawingAction {
    data object OnNewPathStart: DrawingAction
    data class OnDraw(val offset: Offset): DrawingAction
    data object OnPathEnd: DrawingAction
    data class OnSelectColor(val color: Color): DrawingAction
    data object OnClearCanvasClick: DrawingAction
    data object OnUndo: DrawingAction
    data class OnFill(val color: Color): DrawingAction
    data class OnSetBrushSize(val size: Float): DrawingAction
    data class OnSetBrushType(val type: BrushType): DrawingAction
}

class DrawingViewModel: ViewModel() {
    private val repository = DrawingRepository(NetworkModule.client)

    private val _state = MutableStateFlow(DrawingState())
    val state = _state.asStateFlow()

    private val _downloadedImage = MutableStateFlow<ByteArray?>(null)
    private val history = mutableListOf<DrawingState>()

    fun getHistory(): List<DrawingState> = history.toList()
    val downloadedImage = _downloadedImage.asStateFlow()

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
            DrawingAction.OnNewPathStart -> {
                pushHistory()
                onNewPathStart()
            }
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

    private fun onNewPathStart() {
        _state.update { it.copy(currentPath = PathData(id = System.currentTimeMillis().toString(), color = it.selectedColor, path = emptyList(), thickness = it.brushThickness, brushType = it.brushType)) }
    }

    private fun onDraw(offset: Offset) {
        val currentPathData = state.value.currentPath ?: return
        _state.update { it.copy(currentPath = currentPathData.copy(path = currentPathData.path + offset)) }
    }

    private fun onClearCanvasClick() {
        _state.update { it.copy(currentPath = null, paths = emptyList(), backgroundColor = Color.White) }
    }

    suspend fun uploadDrawing(pngByteArray: ByteArray): Boolean {
        return try {
            repository.sendDrawing(pngByteArray)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun fetchLatestDrawing() {
        viewModelScope.launch {
            val bytes = repository.getLatestDrawing()
            if (bytes != null) _downloadedImage.value = bytes
        }
    }

    fun restoreHistory(savedHistory: List<DrawingState>) {
        history.clear()
        history.addAll(savedHistory)
    }
}

