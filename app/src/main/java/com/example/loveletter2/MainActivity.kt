package com.example.loveletter2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.loveletter2.ui.theme.LoveLetterTheme
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
import android.util.Log
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import io.ktor.client.request.*
import io.ktor.http.ContentType
import io.ktor.http.contentType

class MainActivity : ComponentActivity() {
    //private lateinit var fcmTokenManager: FCMTokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

//        fcmTokenManager = FCMTokenManager(this)
//        fcmTokenManager.requestNotificationPermission()
//        fcmTokenManager.requestFCMToken(
//            onSuccess = { token ->
//                sendFcmTokenToServer(token)
//                Log.d("FCM", "FCM token obtained and sent to server: $token")
//            },
//            onError = { error ->
//                Log.e("FCM", "Error getting FCM token", error)
//            }
//        )

        setContent {
            LoveLetterTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val viewModel = viewModel<DrawingViewModel>()
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    val graphicsLayer = rememberGraphicsLayer()
                    val scope = rememberCoroutineScope()
                    val downloadedImage by viewModel.downloadedImage.collectAsStateWithLifecycle()
                    val context = LocalContext.current

                    LaunchedEffect(Unit) {
                        viewModel.fetchLatestDrawing()
                    }
                    LaunchedEffect(downloadedImage) {
                        downloadedImage?.let { bytes ->
                            LoveLetterWidget.updateWidgetImage(context, bytes)
                        }
                    }

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
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .drawWithContent {
                                    graphicsLayer.record {
                                        this@drawWithContent.drawContent()
                                    }
                                    drawLayer(graphicsLayer)
                                }
                        )
                        CanvasControls(
                            selectedColor = state.selectedColor,
                            colors = allColors,
                            onSelectColor = {
                                viewModel.onAction(DrawingAction.OnSelectColor(it))
                            },
                            onClearCanvas = {
                                viewModel.onAction(DrawingAction.OnClearCanvasClick)
                            },
                            onSendAction = {
                                scope.launch {
                                    val imageBitmap = graphicsLayer.toImageBitmap()
                                    val pngByteArray = imageBitmap.toPngByteArray()

                                    try {
                                        viewModel.uploadDrawing(pngByteArray)
                                        Log.d("Upload",  "Image uploaded successfully! Size: ${pngByteArray.size} bytes")
                                    } catch (e: Exception) {
                                        Log.e("Upload", "Image upload failed!", e)
                                    }
                                }
                            }
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
}