package com.bond.loveletter.ui.drawing

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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bond.loveletter.R

@Composable
fun ColumnScope.CanvasControls(
    selectedColor: Color,
    colors: List<Color>,
    brushThickness: Float,
    onSelectColor: (Color) -> Unit,
    onClearCanvas: () -> Unit,
    onSendAction: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    canUndo: Boolean = true,
    canRedo: Boolean = false,
    modifier: Modifier = Modifier,
    onFill: (Color) -> Unit = {},
    onSetBrushType: (BrushType) -> Unit = {},
    onSetBrushLevel: (Int) -> Unit = {},
    currentBrushType: BrushType = BrushType.MARKER,
    isSending: Boolean = false,
    onPickPhoto: () -> Unit = {},
    isSendingPhoto: Boolean = false
) {
    val scrollState = rememberScrollState()
    var showOptions by remember { mutableStateOf(false) }
    var showColorPickerDialog by remember { mutableStateOf(false) }
    var customColor by remember { mutableStateOf<Color?>(null) }

    LaunchedEffect(selectedColor) {
        if (selectedColor !in colors) {
            customColor = selectedColor
        }
    }

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
                .padding(vertical = 10.dp, horizontal = 12.dp)
        ) {
            // 1. Color picker row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Custom Color Palette Button (Rainbow sweep gradient border)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.sweepGradient(
                                listOf(
                                    Color(0xFFFF0000),
                                    Color(0xFFFFFF00),
                                    Color(0xFF00FF00),
                                    Color(0xFF00FFFF),
                                    Color(0xFF0000FF),
                                    Color(0xFFFF00FF),
                                    Color(0xFFFF0000)
                                )
                            )
                        )
                        .clickable { showColorPickerDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.outline_palette_24),
                            contentDescription = stringResource(R.string.canvas_custom_color_picker),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // If user selected a custom color not in preset list, display it right next to palette button
                val activeCustom = customColor
                if (activeCustom != null && activeCustom !in colors) {
                    val isSelected = activeCustom == selectedColor
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(activeCustom)
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.6f),
                                shape = CircleShape
                            )
                            .clickable { onSelectColor(activeCustom) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (activeCustom.luminance() > 0.5f) Color.Black else Color.White)
                            )
                        }
                    }
                }

                for (color in colors) {
                    val isSelected = color == selectedColor
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.6f),
                                shape = CircleShape
                            )
                            .clickable { onSelectColor(color) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (color.luminance() > 0.5f) Color.Black else Color.White)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. Brush sizes, pen style, and fill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Brush sizes
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val sizes = listOf(
                        1 to R.drawable.outline_pen_size_1_24,
                        2 to R.drawable.outline_pen_size_2_24,
                        3 to R.drawable.outline_pen_size_3_24,
                        4 to R.drawable.outline_pen_size_4_24
                    )
                    for ((level, iconRes) in sizes) {
                        val isCurrentSize = currentLevel == level
                        if (isCurrentSize) {
                            FilledIconButton(
                                onClick = { onSetBrushLevel(level) },
                                modifier = Modifier.size(38.dp),
                                shape = CircleShape,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(
                                    painter = painterResource(id = iconRes),
                                    contentDescription = stringResource(R.string.canvas_brush_size, level),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else {
                            IconButton(
                                onClick = { onSetBrushLevel(level) },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = iconRes),
                                    contentDescription = stringResource(R.string.canvas_brush_size, level),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Brush Type / Style Button with active name
                val currentStyleName = when (currentBrushType) {
                    BrushType.MARKER -> stringResource(R.string.canvas_brush_marker)
                    BrushType.DASHED -> stringResource(R.string.canvas_brush_dashed)
                    BrushType.HIGHLIGHTER -> stringResource(R.string.canvas_brush_highlighter)
                    BrushType.CONTINUOUS -> stringResource(R.string.canvas_brush_marker)
                }

                FilledTonalButton(
                    onClick = { showOptions = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(text = currentStyleName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                // Fill Canvas Button
                FilledTonalIconButton(
                    onClick = { onFill(selectedColor) },
                    modifier = Modifier.size(38.dp),
                    shape = CircleShape
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.outline_format_paint_24),
                        contentDescription = stringResource(R.string.canvas_fill_canvas),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Undo, Redo, Clear, and Send actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Undo Button
                OutlinedIconButton(
                    onClick = onUndo,
                    enabled = canUndo,
                    modifier = Modifier.size(38.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.outline_undo_24),
                        contentDescription = stringResource(R.string.canvas_undo),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Redo Button
                OutlinedIconButton(
                    onClick = onRedo,
                    enabled = canRedo,
                    modifier = Modifier.size(38.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.outline_redo_24),
                        contentDescription = stringResource(R.string.canvas_redo),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Clear Canvas Button
                OutlinedButton(
                    onClick = onClearCanvas,
                    modifier = Modifier.height(38.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(R.string.canvas_clear), fontSize = 12.sp)
                }

                // Send Photo Button (Opens picker; preview & view-once selection in preview dialog)
                OutlinedIconButton(
                    onClick = onPickPhoto,
                    enabled = !isSending && !isSendingPhoto,
                    modifier = Modifier.size(38.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isSendingPhoto) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.outline_image_24),
                            contentDescription = stringResource(R.string.canvas_pick_photo),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Send Drawing Button (Primary CTA - expands to fill all remaining space without wrapping)
                Button(
                    onClick = onSendAction,
                    enabled = !isSending && !isSendingPhoto,
                    modifier = Modifier
                        .height(38.dp)
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.canvas_sending),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.canvas_send_drawing),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }

    if (showOptions) {
        AlertDialog(
            onDismissRequest = { showOptions = false },
            title = { Text(stringResource(R.string.canvas_brush_style_dialog_title)) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val brushOptions = listOf(
                        BrushType.MARKER to stringResource(R.string.canvas_brush_marker),
                        BrushType.DASHED to stringResource(R.string.canvas_brush_dashed),
                        BrushType.HIGHLIGHTER to stringResource(R.string.canvas_brush_highlighter)
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
                    Text(stringResource(R.string.btn_close))
                }
            }
        )
    }

    if (showColorPickerDialog) {
        ColorPickerDialog(
            initialColor = selectedColor,
            onColorSelected = { newColor ->
                customColor = newColor
                onSelectColor(newColor)
            },
            onDismiss = { showColorPickerDialog = false }
        )
    }
}
