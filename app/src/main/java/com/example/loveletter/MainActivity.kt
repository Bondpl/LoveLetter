package com.example.loveletter

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.loveletter.data.storage.AuthState
import com.example.loveletter.ui.AuthScreen
import com.example.loveletter.ui.AuthViewModel
import com.example.loveletter.ui.WaitingScreen
import com.example.loveletter.ui.theme.LoveLetterTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val authViewModel: AuthViewModel by viewModels()
    private val drawingViewModel: DrawingViewModel by viewModels()
    private lateinit var fcmTokenManager: FCMTokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        fcmTokenManager = FCMTokenManager(this)
        fcmTokenManager.requestNotificationPermission()
        fcmTokenManager.requestFCMToken(
            onSuccess = { token ->
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        NetworkModule.getAuthRepository(this@MainActivity).updateFcmToken(token)
                        Log.d("FCM", "FCM token saved & sent to server: $token")
                    } catch (e: Exception) {
                        Log.e("FCM", "Failed to register FCM token", e)
                    }
                }
            },
            onError = { error ->
                Log.e("FCM", "Error getting FCM token", error)
            }
        )

        setContent {
            LoveLetterTheme {
                val authState by authViewModel.authState.collectAsStateWithLifecycle()
                val isAuthLoading by authViewModel.isLoading.collectAsStateWithLifecycle()
                val authErrorMessage by authViewModel.errorMessage.collectAsStateWithLifecycle()

                LaunchedEffect(authState) {
                    if (authState is AuthState.Unauthenticated) {
                        drawingViewModel.clearAll(this@MainActivity)
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (val state = authState) {
                        is AuthState.Unauthenticated -> {
                            AuthScreen(
                                isLoading = isAuthLoading,
                                errorMessage = authErrorMessage,
                                onCreatePair = { authViewModel.createPair() },
                                onJoinPair = { code -> authViewModel.joinPair(code) },
                                onClearError = { authViewModel.clearError() },
                                modifier = Modifier.safeDrawingPadding()
                            )
                        }

                        is AuthState.WaitingForPartner -> {
                            WaitingScreen(
                                pairCode = state.pairCode,
                                isLoading = isAuthLoading,
                                onCancelPair = {
                                    drawingViewModel.clearAll(this@MainActivity)
                                    authViewModel.leavePair()
                                },
                                modifier = Modifier.safeDrawingPadding()
                            )
                        }

                        is AuthState.Paired -> {
                            DrawingMainScreen(
                                drawingViewModel = drawingViewModel,
                                onDisconnectPair = {
                                    drawingViewModel.clearAll(this@MainActivity)
                                    authViewModel.leavePair()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (authViewModel.authState.value is AuthState.Paired) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    CanvasStorage.saveHistory(this@MainActivity, drawingViewModel.getHistoryToSave())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (authViewModel.authState.value is AuthState.Paired) {
            lifecycleScope.launch {
                try {
                    val loaded = CanvasStorage.loadHistory(this@MainActivity)
                    if (loaded.isNotEmpty()) {
                        drawingViewModel.restoreHistory(loaded)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            drawingViewModel.fetchLatestDrawing()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawingMainScreen(
    drawingViewModel: DrawingViewModel,
    onDisconnectPair: () -> Unit
) {
    val state by drawingViewModel.state.collectAsStateWithLifecycle()
    val isSending by drawingViewModel.isSending.collectAsStateWithLifecycle()
    val isFetching by drawingViewModel.isFetching.collectAsStateWithLifecycle()
    val downloadedImage by drawingViewModel.downloadedImage.collectAsStateWithLifecycle()
    val graphicsLayer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var showDisconnectDialog by remember { mutableStateOf(false) }
    var showPartnerDrawingDialog by remember { mutableStateOf(false) }

    val partnerBitmap = remember(downloadedImage) {
        downloadedImage?.let { bytes ->
            try {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }

    LaunchedEffect(Unit) {
        drawingViewModel.loadSavedHistory(context)
        drawingViewModel.loadCachedImage(context)
        drawingViewModel.fetchLatestDrawing()
    }

    LaunchedEffect(downloadedImage) {
        downloadedImage?.let { bytes ->
            LoveLetterWidget.updateWidgetImage(context, bytes)
        }
    }

    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text("Disconnect Pair") },
            text = { Text("Are you sure you want to disconnect from your partner? You will need a new pair code to connect again.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDisconnectDialog = false
                        onDisconnectPair()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Disconnect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Modal dialog displaying partner's drawing — completely isolated from Canvas size
    if (showPartnerDrawingDialog && partnerBitmap != null) {
        Dialog(
            onDismissRequest = { showPartnerDrawingDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black.copy(alpha = 0.94f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "💌 Partner's Drawing",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = { showPartnerDrawingDialog = false }) {
                            Text("Close", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = partnerBitmap,
                            contentDescription = "Full Screen Partner Drawing",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { showPartnerDrawingDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Back to Drawing Canvas")
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                },
                actions = {
                    if (partnerBitmap != null) {
                        FilledTonalButton(
                            onClick = { showPartnerDrawingDialog = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text("💌 Partner", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (isFetching) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(20.dp)
                                .padding(end = 8.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        TextButton(onClick = { drawingViewModel.fetchLatestDrawing() }) {
                            Text("Refresh")
                        }
                    }
                    TextButton(
                        onClick = { showDisconnectDialog = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Disconnect")
                    }
                }
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // DrawingCanvas has 100% of the remaining vertical space — NO cards pushing or resizing it!
            DrawingCanvas(
                paths = state.paths,
                currentPath = state.currentPath,
                onAction = drawingViewModel::onAction,
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
                onSelectColor = { drawingViewModel.onAction(DrawingAction.OnSelectColor(it)) },
                onClearCanvas = { drawingViewModel.onAction(DrawingAction.OnClearCanvasClick) },
                onSendAction = {
                    if (isSending) return@CanvasControls
                    scope.launch {
                        Toast.makeText(context, "Encrypting & uploading drawing...", Toast.LENGTH_SHORT).show()
                        val imageBitmap = graphicsLayer.toImageBitmap()
                        val pngByteArray = withContext(Dispatchers.Default) {
                            imageBitmap.toPngByteArray()
                        }
                        val result = drawingViewModel.uploadDrawing(pngByteArray)
                        result.onSuccess {
                            Toast.makeText(context, "Drawing encrypted & sent! 💌", Toast.LENGTH_SHORT).show()
                        }.onFailure { error ->
                            val msg = error.message ?: "Upload failed"
                            Toast.makeText(context, "Failed to send: $msg", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onUndo = { drawingViewModel.onAction(DrawingAction.OnUndo) },
                onFill = { drawingViewModel.onAction(DrawingAction.OnFill(it)) },
                onSetBrushType = { drawingViewModel.onAction(DrawingAction.OnSetBrushType(it)) },
                onSetBrushLevel = { level ->
                    val size = when(level) { 1 -> 6f; 2 -> 20f; 3 -> 40f; else -> 80f }
                    drawingViewModel.onAction(DrawingAction.OnSetBrushSize(size))
                },
                brushThickness = state.brushThickness,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
