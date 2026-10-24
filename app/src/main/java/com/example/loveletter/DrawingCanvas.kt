package com.example.loveletter

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach

@Composable
fun DrawingCanvas(
    paths: List<PathData>,
    currentPath: PathData?,
    onAction: (DrawingAction) -> Unit,
    backgroundColor: Color = Color.White,
    modifier: Modifier = Modifier
) {
    // Cache for completed Path objects so we do not re-create Path() every single frame
    val pathCache = remember { mutableMapOf<String, Path>() }

    LaunchedEffect(paths) {
        val activeIds = paths.map { it.id }.toSet()
        pathCache.keys.retainAll(activeIds)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .background(backgroundColor)
            .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        onAction(DrawingAction.OnTap(offset))
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        onAction(DrawingAction.OnNewPathStart(offset))
                    },
                    onDragEnd = { onAction(DrawingAction.OnPathEnd) },
                    onDrag = { change, _ ->
                        change.consume()
                        onAction(DrawingAction.OnDraw(change.position))
                    },
                    onDragCancel = { onAction(DrawingAction.OnPathEnd) }
                )
            }
    ) {
        paths.fastForEach { pathData ->
            val cachedPath = if (pathData.path.size > 1) {
                pathCache.getOrPut(pathData.id) {
                    createComposePath(pathData.path)
                }
            } else null
            drawPathWithType(pathData, cachedPath)
        }
        currentPath?.let {
            val livePath = if (it.path.size > 1) createComposePath(it.path) else null
            drawPathWithType(it, livePath)
        }
    }
}

private fun createComposePath(points: List<androidx.compose.ui.geometry.Offset>): Path {
    return Path().apply {
        if (points.isNotEmpty()) {
            moveTo(points.first().x, points.first().y)
            for (i in 1..points.lastIndex) {
                val to = points[i]
                lineTo(to.x, to.y)
            }
        }
    }
}

private fun DrawScope.drawPathWithType(pathData: PathData, path: Path?) {
    if (pathData.path.isEmpty()) return

    val isHighlighter = pathData.brushType == BrushType.HIGHLIGHTER
    val isDashed = pathData.brushType == BrushType.DASHED
    
    val strokeWidth = if (isHighlighter) pathData.thickness * 1.5f else pathData.thickness
    val color = if (isHighlighter) pathData.color.copy(alpha = 0.35f) else pathData.color
    val pathEffect = if (isDashed) PathEffect.dashPathEffect(floatArrayOf(60f, 60f), 0f) else null

    if (pathData.path.size == 1) {
        val center = pathData.path.first()
        drawCircle(
            color = color,
            radius = strokeWidth / 2f,
            center = center
        )
        return
    }

    if (path != null) {
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
                pathEffect = pathEffect
            )
        )
    }
}
