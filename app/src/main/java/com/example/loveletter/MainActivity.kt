package com.example.loveletter

import android.os.Bundle
import android.util.Log
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
import kotlinx.coroutines.launch
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import android.widget.Toast
import kotlinx.coroutines.withContext
import io.ktor.client.request.post
import kotlinx.coroutines.Dispatchers
import io.ktor.client.request.*
import io.ktor.http.ContentType
import io.ktor.http.contentType

class MainActivity : ComponentActivity() {
    private val drawingViewModel: DrawingViewModel by viewModels()
    private lateinit var fcmTokenManager: FCMTokenManager
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        fcmTokenManager = FCMTokenManager(this)
        fcmTokenManager.requestNotificationPermission()
        fcmTokenManager.requestFCMToken(
            onSuccess = { token ->
                sendFcmTokenToServer(token)
                Log.d("FCM", "FCM token obtained and sent to server: $token")
            },
            onError = { error ->
                Log.e("FCM", "Error getting FCM token", error)
            }
        )

        setContent {
            LoveLetterTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val viewModel = drawingViewModel
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
                                .padding(8.dp)
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
                                    Toast.makeText(context, "Sending drawing...", Toast.LENGTH_SHORT).show()
                                    val imageBitmap = graphicsLayer.toImageBitmap()
                                    val pngByteArray = withContext(Dispatchers.Default) {
                                        imageBitmap.toPngByteArray()
                                    }
                                    val success = viewModel.uploadDrawing(pngByteArray)
                                    if (success) {
                                        Toast.makeText(context, "Drawing sent!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Failed to send drawing. Please try again.", Toast.LENGTH_LONG).show()
                                    }
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

    private fun sendFcmTokenToServer(token: String) {
        val client = NetworkModule.client
        val url = "${BuildConfig.SERVER_URL}/api/fcm-token"
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                client.post(url) {
                    header("api_key", BuildConfig.API_KEY)
                    contentType(ContentType.Application.Json)
                    setBody(mapOf("token" to token))
                }
                Log.d("FCM", "FCM token sent to server from MainActivity")
            } catch (e: Exception) {
                Log.e("FCM", "Failed to send FCM token from MainActivity", e)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        lifecycleScope.launch(Dispatchers.IO) {
            try { CanvasStorage.saveHistory(this@MainActivity, drawingViewModel.getHistory()) } catch (e: Exception) { e.printStackTrace() }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            try {
                val loaded = CanvasStorage.loadHistory(this@MainActivity)
                drawingViewModel.restoreHistory(loaded)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
}

