package com.example.loveletter

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .background(backgroundColor)
            .border(2.dp, Color.LightGray, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
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
            drawPathWithType(pathData)
        }
        currentPath?.let {
            drawPathWithType(it)
        }
    }
}

private fun DrawScope.drawPathWithType(pathData: PathData) {
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

    val simplePath = Path().apply {
        moveTo(pathData.path.first().x, pathData.path.first().y)
        for (i in 1..pathData.path.lastIndex) {
            val to = pathData.path[i]
            lineTo(to.x, to.y)
        }
    }

    drawPath(
        path = simplePath,
        color = color,
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
            pathEffect = pathEffect
        )
    )
}
