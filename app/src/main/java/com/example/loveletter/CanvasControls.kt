package com.example.loveletter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    brushThickness: Float,
    currentBrushType: BrushType = BrushType.MARKER,
    isSending: Boolean = false,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var showOptions by remember { mutableStateOf(false) }

    val currentLevel = when {
        brushThickness <= 10f -> 1
        brushThickness <= 25f -> 2
        brushThickness <= 50f -> 3
        else -> 4
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Color Palette Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (color in colors) {
                    val isSelected = color == selectedColor
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.5f),
                                shape = CircleShape
                            )
                            .clickable { onSelectColor(color) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (color == Color.White) Color.Black else Color.White
                                    )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 2. Brush Sizes, Pen Style & Fill Tool Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Brush sizes 1..4 with visual highlight
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val penIcons = listOf(
                        1 to R.drawable.outline_pen_size_1_24,
                        2 to R.drawable.outline_pen_size_2_24,
                        3 to R.drawable.outline_pen_size_3_24,
                        4 to R.drawable.outline_pen_size_4_24
                    )
                    for ((lvl, iconRes) in penIcons) {
                        val isLevelSelected = currentLevel == lvl
                        if (isLevelSelected) {
                            FilledIconButton(
                                onClick = { onSetBrushLevel(lvl) },
                                modifier = Modifier.size(38.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(
                                    painter = painterResource(id = iconRes),
                                    contentDescription = "Size $lvl",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else {
                            IconButton(
                                onClick = { onSetBrushLevel(lvl) },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = iconRes),
                                    contentDescription = "Size $lvl",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Pen style dropdown / selector button
                val brushLabel = when (currentBrushType) {
                    BrushType.MARKER, BrushType.CONTINUOUS -> "Marker"
                    BrushType.DASHED -> "Dashed"
                    BrushType.HIGHLIGHTER -> "Highlighter"
                }
                FilledTonalButton(
                    onClick = { showOptions = true },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(
                        text = brushLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Dedicated Fill Background tool
                FilledTonalIconButton(
                    onClick = { onFill(selectedColor) },
                    modifier = Modifier.size(38.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.outline_format_paint_24),
                        contentDescription = "Fill Canvas Background",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 3. Actions Row: Undo, Clear, and Send Drawing
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Undo Button
                OutlinedIconButton(
                    onClick = onUndo,
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.outline_backspace_24),
                        contentDescription = "Undo",
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Clear Canvas Button
                OutlinedButton(
                    onClick = onClearCanvas,
                    modifier = Modifier.height(42.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Clear", fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.weight(1f))

                // Send Drawing Button (Primary CTA)
                Button(
                    onClick = onSendAction,
                    enabled = !isSending,
                    modifier = Modifier
                        .height(42.dp)
                        .weight(1.8f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sending...", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Text("Send Drawing", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showOptions) {
        AlertDialog(
            onDismissRequest = { showOptions = false },
            title = { Text("Brush Style") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val brushOptions = listOf(
                        BrushType.MARKER to "Marker",
                        BrushType.DASHED to "Dashed",
                        BrushType.HIGHLIGHTER to "Highlighter"
                    )
                    for ((type, label) in brushOptions) {
                        val isCurrent = currentBrushType == type
                        if (isCurrent) {
                            Button(
                                onClick = { onSetBrushType(type); showOptions = false },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(label, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onSetBrushType(type); showOptions = false },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showOptions = false }) {
                    Text("Close")
                }
            }
        )
    }
}
