package com.example.loveletter2

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach

@Composable
fun ColumnScope.CanvasControls(
    selectedColor: Color,
    colors: List<Color>,
    colorSubMap: Map<Color, List<Color>> = emptyMap(),
    onSelectColor: (Color) -> Unit,
    onClearCanvas: () -> Unit,
    onSendAction: () -> Unit = {},
    onUndo: () -> Unit = {},
    onFill: (Color) -> Unit = {},
    onAddCircle: () -> Unit = {},
    onBrushSizeChanged: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Top: brush size chooser
    var showSizeDialog by remember { mutableStateOf(false) }
    var tempSize by remember { mutableStateOf(20f) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.End
    ) {
        IconButton(onClick = { showSizeDialog = true }) {
            Icon(
                painter = painterResource(id = R.drawable.outline_format_paint_24),
                contentDescription = "Brush size"
            )
        }
    }

    if (showSizeDialog) {
        AlertDialog(
            onDismissRequest = { showSizeDialog = false },
            confirmButton = {},
            dismissButton = {},
            title = { Text("Brush size") },
            text = {
                Slider(
                    value = tempSize,
                    onValueChange = { tempSize = it },
                    valueRange = 1f..100f,
                    onValueChangeFinished = {
                        onBrushSizeChanged(tempSize)
                        showSizeDialog = false
                    }
                )
            }
        )
    }

    // Expanded subcolors row (appears above base colors)
    var expandedColor by remember { mutableStateOf<Color?>(null) }
    expandedColor?.let { base ->
        val subs = colorSubMap[base] ?: emptyList()
        if (subs.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
            ) {
                subs.fastForEach { color ->
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = 2.dp,
                                color = if (selectedColor == color) Color.Black else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable {
                                onSelectColor(color)
                                expandedColor = null
                            }
                    )
                }
            }
        }
    }

    // Base colors row
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
    ) {
        val specialIconColor = Color(0xFF123456) // sentinel for the "more tools" icon
        colors.fastForEach { color ->
            val isSelected = selectedColor == color
            if (color == specialIconColor) {
                // special icon that opens tools dialog
                var showTools by remember { mutableStateOf(false) }
                IconButton(onClick = { showTools = true }) {
                    Icon(
                        painter = painterResource(id = R.drawable.outline_arrow_circle_up_24),
                        contentDescription = "More tools",
                        modifier = Modifier.size(40.dp)
                    )
                }
                if (showTools) {
                    AlertDialog(
                        onDismissRequest = { showTools = false },
                        title = { Text("Tools") },
                        text = {
                            Column {
                                Button(onClick = {
                                    // Fill with currently selected color
                                    onFill(selectedColor)
                                    showTools = false
                                }) { Text("Fill in") }
                                Spacer(modifier = Modifier.size(6.dp))
                                Button(onClick = {
                                    onSelectColor(Color.White) // Eraser -> white
                                    showTools = false
                                }) { Text("Eraser") }
                                Spacer(modifier = Modifier.size(6.dp))
                                Button(onClick = {
                                    onAddCircle()
                                    showTools = false
                                }) { Text("Add circle") }
                            }
                        },
                        confirmButton = {},
                        dismissButton = {}
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            val scale = if (isSelected) 1.2f else 1f
                            scaleX = scale
                            scaleY = scale
                        }
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = 2.dp,
                            color = if (selectedColor == color) Color.Black else Color.Transparent,
                            shape = CircleShape
                        )
                        .clickable {
                            onSelectColor(color)
                            // toggle expansion for subcolors
                            expandedColor = if (expandedColor == color) null else color
                        }
                )
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Button(onClick = onClearCanvas) {
            Text("Clear Canvas")
        }

        Spacer(modifier = Modifier.size(8.dp))

        Button(onClick = onSendAction) {
            Text("Send")
        }

        Spacer(modifier = Modifier.size(8.dp))

        Button(onClick = onUndo) {
            Icon(painter = painterResource(id = R.drawable.outline_backspace_24), contentDescription = "Undo")
        }
    }
}
