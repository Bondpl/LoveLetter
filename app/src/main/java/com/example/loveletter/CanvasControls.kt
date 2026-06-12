package com.example.loveletter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach

@Composable
fun ColumnScope.CanvasControls(
    selectedColor: Color,
    colors: List<Color>,
    onSelectColor: (Color) -> Unit,
    onClearCanvas: () -> Unit,
    onSendAction: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
    ) {
        colors.fastForEach { color ->
            val isSelected = selectedColor == color
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
                    .clickable { onSelectColor(color) }
            )
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Button(
            onClick = onClearCanvas
        ) {
            Text("Clear Canvas")
        }

        Spacer(modifier = Modifier.size(8.dp))

        Button(
            onClick = onSendAction
        ) {
            Text("Send")
        }
    }
}