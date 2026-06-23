package com.example.loveletter2

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach

@Composable
fun ColumnScope.CanvasControls(
    selectedColor: Color,
    colors: List<Color>,
    onSelectColor: (Color) -> Unit,
    onClearCanvas: () -> Unit,
    onSendAction: () -> Unit = {},
    onUndo: () -> Unit = {},
    onFill: (Color) -> Unit = {},
    onSetBrushLevel: (Int) -> Unit = {},
    brushThickness: Float = 20f,
    modifier: Modifier = Modifier
) {
    // Carousel of colors
    val scrollState = rememberScrollState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
    ) {
        colors.fastForEach { color ->
            if (color == Color(0xFF123456)) {
                var showTools by remember { mutableStateOf(false) }
                IconButton(onClick = { showTools = true }) {
                    Icon(painter = painterResource(id = R.drawable.outline_arrow_circle_up_24), contentDescription = "More tools", modifier = Modifier.size(40.dp))
                }
                if (showTools) {
                    AlertDialog(onDismissRequest = { showTools = false }, title = { Text("Tools") }, text = {
                        Button(onClick = {
                            onFill(selectedColor)
                            showTools = false
                        }) { Text("Fill in") }
                        Spacer(modifier = Modifier.size(6.dp))
                        Button(onClick = {
                            onSelectColor(Color(0xFFFAFAFA))
                            showTools = false
                        }) { Text("Eraser") }
                    }, confirmButton = {}, dismissButton = {})
            } else {
                val isSelected = selectedColor == color
                Box(modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color)
                    .clickable { onSelectColor(color) }
                ) {
                    if (isSelected) {
                        // small border indicator
                        Box(modifier = Modifier
                            .align(Alignment.Center)
                            .size(44.dp))
                    }
                }
            }
        }
    }

    // Bottom controls: brush sizes left of Clear Canvas, then Send and Undo
    Row(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center) {

        // Brush size buttons: 1..4 mapped to thickness
        val levels = listOf(1,2,3,4)
        val currentLevel = when {
            brushThickness <= 10f -> 1
            brushThickness <= 30f -> 2
            brushThickness <= 60f -> 3
            else -> 4
        }
        levels.forEach { lvl ->
            IconButton(onClick = { onSetBrushLevel(lvl) }) {
                Icon(painter = painterResource(id = when(lvl) {
                    1 -> R.drawable.outline_pen_size_1_24
                    2 -> R.drawable.outline_pen_size_2_24
                    3 -> R.drawable.outline_pen_size_3_24
                    else -> R.drawable.outline_pen_size_4_24
                }), contentDescription = "Size $lvl")
            }
        }

        Spacer(modifier = Modifier.size(8.dp))

        Button(onClick = onClearCanvas) { Text("Clear Canvas") }

        Spacer(modifier = Modifier.size(8.dp))

        Button(onClick = onSendAction) { Text("Send") }

        Spacer(modifier = Modifier.size(8.dp))

        Button(onClick = onUndo) {
            Icon(painter = painterResource(id = R.drawable.outline_backspace_24), contentDescription = "Undo")
        }
    }
}
