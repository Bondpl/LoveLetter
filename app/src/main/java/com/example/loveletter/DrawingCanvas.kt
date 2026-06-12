package com.example.loveletter

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.util.fastForEach

@Composable
fun DrawingCanvas(
    paths: List<PathData>,
    currentPath: PathData?,
    onAction: (DrawingAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .clipToBounds()
            .background(Color.White)
            .pointerInput(true) {
                detectDragGestures(
                    onDragStart = {
                        onAction(DrawingAction.OnNewPathStart)
                    },
                    onDragEnd = {
                        onAction(DrawingAction.OnPathEnd)
                    },
                    onDrag = { change, _ ->
                        onAction(DrawingAction.OnDraw(change.position))
                    },
                    onDragCancel = {
                        onAction(DrawingAction.OnPathEnd)
                    },
                )
            }
    ) {
        paths.fastForEach { pathData ->
            drawPath(
                path = pathData.path,
                color = pathData.color,
            )
        }
        currentPath?.let {
            drawPath(
                path = it.path,
                color = it.color
            )
        }
    }
}

private fun DrawScope.drawPath(
    path: List<Offset>,
    color: Color,
    thickness: Float = 20f
) {
    val simplePath = Path().apply {
        if (path.isNotEmpty()) {
            moveTo(path.first().x, path.first().y)
            for (i in 1..path.lastIndex) {
                val to = path[i]
                lineTo(to.x, to.y)
            }
        }
    }
    drawPath(
        path = simplePath,
        color = color,
        style = Stroke(
            width = thickness,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}