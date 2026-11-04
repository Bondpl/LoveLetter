package com.bond.loveletter.ui.drawing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach

@Composable
fun DrawingCanvas(
    paths: List<PathData>,
    currentPath: PathData?,
    onAction: (DrawingAction) -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.White,
    graphicsLayer: GraphicsLayer? = null
) {
    // Cache for completed Path objects so we do not re-create Path() every single frame
    val pathCache = remember { mutableMapOf<String, Path>() }

    LaunchedEffect(paths) {
        val activeIds = paths.map { it.id }.toSet()
        pathCache.keys.retainAll(activeIds)
    }

    // Outer Box provides the UI rounded card appearance and border on screen
    Box(
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            .padding(1.5.dp)
            .clip(RoundedCornerShape(18.5.dp))
    ) {
        // Inner Canvas draws the rectangular background and paths; graphicsLayer records ONLY this clean canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (graphicsLayer != null) {
                        Modifier.drawWithContent {
                            graphicsLayer.record {
                                drawRect(color = backgroundColor)
                                this@drawWithContent.drawContent()
                            }
                            drawLayer(graphicsLayer)
                        }
                    } else Modifier
                )
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        onAction(DrawingAction.OnDrawingStart(offset))
                        onAction(DrawingAction.OnDrawingEnd)
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            onAction(DrawingAction.OnDrawingStart(offset))
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            onAction(DrawingAction.OnDraw(change.position))
                        },
                        onDragEnd = {
                            onAction(DrawingAction.OnDrawingEnd)
                        },
                        onDragCancel = {
                            onAction(DrawingAction.OnDrawingEnd)
                        }
                    )
                }
        ) {
            // Ensure solid background is also drawn in normal canvas render
            drawRect(color = backgroundColor)

            // Draw completed paths from cache
            paths.fastForEach { pathData ->
                val cachedPath = pathCache.getOrPut(pathData.id) {
                    buildComposePath(pathData.path)
                }
                drawStyledPath(pathData, cachedPath)
            }

            // Draw active in-progress path
            currentPath?.let { pathData ->
                val inProgressPath = buildComposePath(pathData.path)
                drawStyledPath(pathData, inProgressPath)
            }
        }
    }
}

private fun buildComposePath(points: List<androidx.compose.ui.geometry.Offset>): Path {
    val composePath = Path()
    if (points.isNotEmpty()) {
        composePath.moveTo(points.first().x, points.first().y)
        for (i in 1 until points.size) {
            composePath.lineTo(points[i].x, points[i].y)
        }
    }
    return composePath
}

private fun DrawScope.drawStyledPath(pathData: PathData, composePath: Path) {
    if (pathData.path.size == 1) {
        val pt = pathData.path[0]
        val radius = pathData.thickness / 2f
        when (pathData.brushType) {
            BrushType.HIGHLIGHTER -> {
                drawCircle(
                    color = pathData.color.copy(alpha = 0.4f),
                    radius = radius * 1.8f,
                    center = pt
                )
            }
            else -> {
                drawCircle(
                    color = pathData.color,
                    radius = radius,
                    center = pt
                )
            }
        }
        return
    }

    when (pathData.brushType) {
        BrushType.DASHED -> {
            drawPath(
                path = composePath,
                color = pathData.color,
                style = Stroke(
                    width = pathData.thickness,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(pathData.thickness * 1.5f, pathData.thickness * 1.5f),
                        0f
                    )
                )
            )
        }
        BrushType.HIGHLIGHTER -> {
            drawPath(
                path = composePath,
                color = pathData.color.copy(alpha = 0.4f),
                style = Stroke(
                    width = pathData.thickness * 1.8f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
        else -> {
            drawPath(
                path = composePath,
                color = pathData.color,
                style = Stroke(
                    width = pathData.thickness,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}
