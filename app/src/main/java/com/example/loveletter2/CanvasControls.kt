package com.example.loveletter2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ButtonDefaults
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

@Composable
fun ColumnScope.CanvasControls(
    selectedColor: Color,
    colors: List<Color>,
    onSelectColor: (Color) -> Unit,
    onClearCanvas: () -> Unit,
    onSendAction: () -> Unit = {},
    onUndo: () -> Unit = {},
    onFill: (Color) -> Unit = {},
    onSetBrushType: (BrushType) -> Unit = {},
    onSetBrushLevel: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    brushThickness: Float
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
    ) {
        for (color in colors) {
            if (color == Color(0xFF123456)) {
                IconButton(onClick = { onFill(selectedColor) }) {
                    Icon(painter = painterResource(id = R.drawable.outline_arrow_circle_up_24), contentDescription = "Fill", modifier = Modifier.size(40.dp))
                }
            } else {
                Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(color).clickable { onSelectColor(color) })
            }
        }
    }

    // Second row: brush sizes + Clear Canvas
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        IconButton(onClick = { onSetBrushLevel(1) }) { Icon(painter = painterResource(id = R.drawable.outline_pen_size_1_24), contentDescription = "Size 1") }
        IconButton(onClick = { onSetBrushLevel(2) }) { Icon(painter = painterResource(id = R.drawable.outline_pen_size_2_24), contentDescription = "Size 2") }
        IconButton(onClick = { onSetBrushLevel(3) }) { Icon(painter = painterResource(id = R.drawable.outline_pen_size_3_24), contentDescription = "Size 3") }
        IconButton(onClick = { onSetBrushLevel(4) }) { Icon(painter = painterResource(id = R.drawable.outline_pen_size_4_24), contentDescription = "Size 4") }

        Spacer(modifier = Modifier.size(8.dp))

        Button(onClick = onClearCanvas) { Text("Clear Canvas") }
    }

    // Third row: Send, Reverse, Options
    var showOptions by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Button(onClick = onSendAction, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4DA6FF))) { Text("Send") }

        Spacer(modifier = Modifier.size(8.dp))

        Button(onClick = onUndo) { Icon(painter = painterResource(id = R.drawable.outline_backspace_24), contentDescription = "Reverse") }

        Spacer(modifier = Modifier.size(8.dp))

        Button(onClick = { showOptions = true }) { Text("Pen") }
    }

    if (showOptions) {
        AlertDialog(
            onDismissRequest = { showOptions = false },
            title = { Text("Pen Options") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(onClick = { onSetBrushType(BrushType.DASHED); showOptions = false }) { Text("Dashed") }
                    Spacer(modifier = Modifier.size(8.dp))
                    Button(onClick = { onSetBrushType(BrushType.CONTINUOUS); showOptions = false }) { Text("Continuous") }
                    Spacer(modifier = Modifier.size(8.dp))
                    Button(onClick = { onSetBrushType(BrushType.HIGHLIGHTER); showOptions = false }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF69B4))) { Text("Highlighter") }
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
    }
}

