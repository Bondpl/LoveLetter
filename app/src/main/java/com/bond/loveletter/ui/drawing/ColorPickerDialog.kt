package com.bond.loveletter.ui.drawing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun ColorPickerDialog(
    initialColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    val initialHsv = remember(initialColor) {
        val hsv = FloatArray(3)
        val r = (initialColor.red * 255).toInt().coerceIn(0, 255)
        val g = (initialColor.green * 255).toInt().coerceIn(0, 255)
        val b = (initialColor.blue * 255).toInt().coerceIn(0, 255)
        android.graphics.Color.RGBToHSV(r, g, b, hsv)
        hsv
    }

    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var sat by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }

    val currentColor = remember(hue, sat, value) {
        Color.hsv(hue, sat, value)
    }

    val r = (currentColor.red * 255).toInt().coerceIn(0, 255)
    val g = (currentColor.green * 255).toInt().coerceIn(0, 255)
    val b = (currentColor.blue * 255).toInt().coerceIn(0, 255)
    val hexString = String.format("#%02X%02X%02X", r, g, b)

    val rainbowBrush = remember {
        Brush.horizontalGradient(
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
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title
                Text(
                    text = "Custom Color",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // 2D Saturation / Value Box
                val pureHue = remember(hue) { Color.hsv(hue, 1f, 1f) }
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                sat = (down.position.x / size.width).coerceIn(0f, 1f)
                                value = (1f - (down.position.y / size.height)).coerceIn(0f, 1f)

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    if (!change.pressed) break
                                    change.consume()
                                    sat = (change.position.x / size.width).coerceIn(0f, 1f)
                                    value = (1f - (change.position.y / size.height)).coerceIn(0f, 1f)
                                }
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                listOf(Color.White, pureHue)
                            )
                        )
                        drawRect(
                            brush = Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black)
                            )
                        )

                        val selectorX = (sat * size.width).coerceIn(0f, size.width)
                        val selectorY = ((1f - value) * size.height).coerceIn(0f, size.height)

                        drawCircle(
                            color = Color.White,
                            radius = 12.dp.toPx(),
                            center = Offset(selectorX, selectorY),
                            style = Stroke(width = 3.dp.toPx())
                        )
                        drawCircle(
                            color = Color.Black,
                            radius = 10.dp.toPx(),
                            center = Offset(selectorX, selectorY),
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }
                }

                // Hue Rainbow Slider
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(rainbowBrush)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(17.dp))
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                hue = ((down.position.x / size.width).coerceIn(0f, 1f) * 360f).coerceIn(0f, 360f)

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    if (!change.pressed) break
                                    change.consume()
                                    hue = ((change.position.x / size.width).coerceIn(0f, 1f) * 360f).coerceIn(0f, 360f)
                                }
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val thumbX = ((hue / 360f) * size.width).coerceIn(0f, size.width)
                        drawCircle(
                            color = Color.White,
                            radius = 14.dp.toPx(),
                            center = Offset(thumbX, size.height / 2f),
                            style = Stroke(width = 3.dp.toPx())
                        )
                        drawCircle(
                            color = Color.hsv(hue, 1f, 1f),
                            radius = 11.dp.toPx(),
                            center = Offset(thumbX, size.height / 2f)
                        )
                    }
                }

                // Color Preview & Hex Details
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(currentColor)
                                .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        )
                        Column {
                            Text(
                                text = hexString,
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "RGB($r, $g, $b)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Quick presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val presets = listOf(
                        Color(0xFF000000), Color(0xFFFFFFFF), Color(0xFFFF2A2A),
                        Color(0xFFFF9800), Color(0xFFFFEB3B), Color(0xFF4CAF50),
                        Color(0xFF2196F3), Color(0xFF9C27B0)
                    )
                    for (preset in presets) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                            .background(preset)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            .clickable {
                                val h = FloatArray(3)
                                android.graphics.Color.RGBToHSV(
                                    (preset.red * 255).toInt(),
                                    (preset.green * 255).toInt(),
                                    (preset.blue * 255).toInt(),
                                    h
                                )
                                hue = h[0]
                                sat = h[1]
                                value = h[2]
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onColorSelected(currentColor)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Select Color")
                    }
                }
            }
        }
    }
}
