package com.example.loveletter2

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

data class DrawingState(
    val selectedColor: Color = Color.Black,
    val currentPath: PathData? = null,
    val paths: List<PathData> = emptyList(),
    val backgroundColor: Color = Color.White,
    val brushThickness: Float = 20f
)

val SENTINEL_ICON_COLOR = Color(0xFF123456)

val allColors = listOf(
    Color.Black,
    Color.Red,
    Color.Blue,
    Color.Green,
    Color.Yellow,
    Color.Magenta,
    SENTINEL_ICON_COLOR // special icon entry
)

val colorSubMap: Map<Color, List<Color>> = mapOf(
    Color.Black to listOf(Color(0xFFB0B0B0), Color(0xFFFAFAFA)), // grey, white
    Color.Red to listOf(Color(0xFFEB5050), Color(0xFFF765B8)),
    Color.Blue to listOf(Color(0xFF2B98E0), Color(0xFF84C0E8), Color(0xFF15268F), Color(0xFF5638B5)),
    Color.Green to listOf(Color(0xFF4FAB61), Color(0xFF1F660F)),
    Color.Yellow to listOf(Color(0xFFFAEA34), Color(0xFFEB9E1E), Color(0xFF5C3705))
)

data class PathData(
    val id: String,
    val color: Color,
    val path: List<Offset>,
    val thickness: Float = 20f
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
    data object OnAddCircle: DrawingAction
}

class DrawingViewModel: ViewModel() {
    private val repository = DrawingRepository(NetworkModule.client)

    private val _state = MutableStateFlow(DrawingState())
    val state = _state.asStateFlow()

    private val _downloadedImage = MutableStateFlow<ByteArray?>(null)

    val downloadedImage = _downloadedImage.asStateFlow()

    fun onAction(action: DrawingAction) {
        when(action) {
            DrawingAction.OnClearCanvasClick -> onClearCanvasClick()
            DrawingAction.OnUndo -> onUndo()
            is DrawingAction.OnDraw -> onDraw(action.offset)
            DrawingAction.OnNewPathStart -> onNewPathStart()
            DrawingAction.OnPathEnd -> onPathEnd()
            is DrawingAction.OnSelectColor -> onSelectColor(action.color)
            is DrawingAction.OnFill -> onFill(action.color)
            is DrawingAction.OnSetBrushSize -> onSetBrushSize(action.size)
            DrawingAction.OnAddCircle -> onAddCircle()
        }
    }

    private fun onSelectColor(color: Color) {
        _state.update { it.copy(
            selectedColor = color
        ) }
    }

    private fun onSetBrushSize(size: Float) {
        _state.update { it.copy(brushThickness = size) }
    }

    private fun onAddCircle() {
        val center = Offset(300f, 300f)
        val r = 100f
        val points = mutableListOf<Offset>()
        val steps = 40
        for (i in 0 until steps) {
            val angle = (i.toDouble() / steps) * Math.PI * 2.0
            val x = center.x + (r * Math.cos(angle)).toFloat()
            val y = center.y + (r * Math.sin(angle)).toFloat()
            points.add(Offset(x, y))
        }
        val newPath = PathData(
            id = System.currentTimeMillis().toString(),
            color = state.value.selectedColor,
            path = points,
            thickness = state.value.brushThickness
        )
        _state.update { it.copy(paths = it.paths + newPath) }
    }

    private fun onFill(color: Color) {
        _state.update { it.copy(backgroundColor = color, paths = emptyList()) }
    }

    private fun onUndo() {
        _state.update { it.copy(paths = if (it.paths.isNotEmpty()) it.paths.dropLast(1) else it.paths) }
    }

    private fun onPathEnd() {
        val currentPathData = state.value.currentPath ?: return
        _state.update { it.copy(
            currentPath = null,
            paths = it.paths + currentPathData
        ) }
    }

    private fun onNewPathStart() {
        _state.update { it.copy(
            currentPath = PathData(
                id = System.currentTimeMillis().toString(),
                color = it.selectedColor,
                path = emptyList(),
                thickness = it.brushThickness
            )
        ) }
    }

    private fun onDraw(offset: Offset) {
        val currentPathData = state.value.currentPath ?: return
        _state.update { it.copy(
            currentPath = currentPathData.copy(
                path = currentPathData.path + offset
            )
        ) }
    }

    private fun onClearCanvasClick() {
        _state.update { it.copy(
            currentPath = null,
            paths = emptyList(),
            backgroundColor = Color.White
        ) }
    }
    fun uploadDrawing(pngByteArray: ByteArray) {
        viewModelScope.launch {
            try {
                repository.sendDrawing(pngByteArray)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun fetchLatestDrawing() {
        viewModelScope.launch {
            val bytes = repository.getLatestDrawing()
            if (bytes != null) {
                _downloadedImage.value = bytes
            }
        }
    }
}
