package com.example.loveletter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.loveletter.ui.theme.LoveLetterTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            LoveLetterTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val viewModel = viewModel<DrawingViewModel>()
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    val graphicsLayer = rememberGraphicsLayer()
                    val scope = rememberCoroutineScope()
                    val downloadedImage by viewModel.downloadedImage.collectAsStateWithLifecycle()
                    val context = LocalContext.current

                    LaunchedEffect(Unit) { viewModel.fetchLatestDrawing() }
                    LaunchedEffect(downloadedImage) { downloadedImage?.let { bytes -> LoveLetterWidget.updateWidgetImage(context, bytes) } }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        DrawingCanvas(
                            paths = state.paths,
                            currentPath = state.currentPath,
                            onAction = viewModel::onAction,
                            backgroundColor = state.backgroundColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .drawWithContent {
                                    graphicsLayer.record { this@drawWithContent.drawContent() }
                                    drawLayer(graphicsLayer)
                                }
                        )

                        CanvasControls(
                            selectedColor = state.selectedColor,
                            colors = flattenedColors,
                            onSelectColor = { viewModel.onAction(DrawingAction.OnSelectColor(it)) },
                            onClearCanvas = { viewModel.onAction(DrawingAction.OnClearCanvasClick) },
                            onSendAction = {
                                scope.launch {
                                    val imageBitmap = graphicsLayer.toImageBitmap()
                                    val pngByteArray = imageBitmap.toPngByteArray()
                                    try { viewModel.uploadDrawing(pngByteArray) } catch (e: Exception) { e.printStackTrace() }
                                }
                            },
                            onUndo = { viewModel.onAction(DrawingAction.OnUndo) },
                            onFill = { viewModel.onAction(DrawingAction.OnFill(it)) },
                            onSetBrushType = { viewModel.onAction(DrawingAction.OnSetBrushType(it)) },
                            onSetBrushLevel = { level ->
                                val size = when(level) { 1 -> 6f; 2 -> 20f; 3 -> 40f; else -> 80f }
                                viewModel.onAction(DrawingAction.OnSetBrushSize(size))
                            },
                            brushThickness = state.brushThickness,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
