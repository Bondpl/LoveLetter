package com.bond.loveletter

import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.bond.loveletter.data.storage.AuthState
import com.bond.loveletter.data.storage.CanvasStorage
import com.bond.loveletter.di.NetworkModule
import com.bond.loveletter.notification.DrawingUpdateBus
import com.bond.loveletter.notification.FCMTokenManager
import com.bond.loveletter.ui.auth.AuthScreen
import com.bond.loveletter.ui.auth.AuthViewModel
import com.bond.loveletter.ui.auth.WaitingScreen
import com.bond.loveletter.ui.drawing.CanvasControls
import com.bond.loveletter.ui.drawing.DrawingAction
import com.bond.loveletter.ui.drawing.DrawingCanvas
import com.bond.loveletter.ui.drawing.DrawingViewModel
import com.bond.loveletter.ui.drawing.flattenedColors
import com.bond.loveletter.ui.theme.LoveLetterTheme
import com.bond.loveletter.util.ImageCompressor
import com.bond.loveletter.util.saveImageToGallery
import com.bond.loveletter.util.savePhotoToGallery
import com.bond.loveletter.util.toPngByteArray
import com.bond.loveletter.util.toUserFriendlyMessage
import com.bond.loveletter.widget.LoveLetterWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val authViewModel: AuthViewModel by viewModels()
    private val drawingViewModel: DrawingViewModel by viewModels()
    private lateinit var fcmTokenManager: FCMTokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        enableEdgeToEdge()

        handleNotificationIntent(intent)

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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent == null) return
        val type = intent.getStringExtra("type")
        val openArt = intent.getBooleanExtra("EXTRA_OPEN_PARTNER_DRAWING", false)
        val openPhoto = intent.getBooleanExtra("EXTRA_OPEN_PARTNER_PHOTO", false)
        if (openPhoto) {
            drawingViewModel.openPartnerPhoto()
            drawingViewModel.fetchLatestDrawing()
            intent.removeExtra("EXTRA_OPEN_PARTNER_PHOTO")
        } else if (type == "NEW_DRAWING" || openArt) {
            drawingViewModel.openPartnerArt()
            drawingViewModel.fetchLatestDrawing()
            intent.removeExtra("type")
            intent.removeExtra("EXTRA_OPEN_PARTNER_DRAWING")
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
    val partnerPhoto by drawingViewModel.partnerPhoto.collectAsStateWithLifecycle()
    val isPartnerPhotoViewOnce by drawingViewModel.isPartnerPhotoViewOnce.collectAsStateWithLifecycle()
    val isViewOnceSelected by drawingViewModel.isViewOnceSelected.collectAsStateWithLifecycle()
    val showPartnerDrawingDialog by drawingViewModel.showPartnerArt.collectAsStateWithLifecycle()
    val showPartnerPhotoDialog by drawingViewModel.showPartnerPhoto.collectAsStateWithLifecycle()
    val isSendingPhoto by drawingViewModel.isSendingPhoto.collectAsStateWithLifecycle()
    val canUndo by drawingViewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by drawingViewModel.canRedo.collectAsStateWithLifecycle()
    val graphicsLayer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var showDisconnectDialog by remember { mutableStateOf(false) }

    val partnerBitmap = remember(downloadedImage) {
        downloadedImage?.let { bytes ->
            try {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }

    val partnerPhotoBitmap = remember(partnerPhoto) {
        partnerPhoto?.let { bytes ->
            try {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val compressedBytes = withContext(Dispatchers.IO) {
                        ImageCompressor.processAndCompressImage(context.applicationContext, uri)
                    }
                    val result = drawingViewModel.uploadPhoto(compressedBytes)
                    result.onSuccess {
                        Toast.makeText(context, "Photo sent!", Toast.LENGTH_SHORT).show()
                    }
                    result.onFailure { error ->
                        val msg = error.toUserFriendlyMessage()
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    val msg = e.localizedMessage ?: "Failed to process photo"
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
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

    if (showPartnerDrawingDialog) {
        Dialog(
            onDismissRequest = { drawingViewModel.closePartnerArt() },
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
                            text = "Partner's Art",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (downloadedImage != null) {
                                IconButton(
                                    onClick = {
                                        scope.launch(Dispatchers.IO) {
                                            val success = saveImageToGallery(context, downloadedImage!!)
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(
                                                    context,
                                                    if (success) "Drawing saved to gallery!" else "Failed to save drawing",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(Color.White.copy(alpha = 0.15f), CircleShape)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.outline_download_24),
                                        contentDescription = "Download to phone",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            TextButton(onClick = { drawingViewModel.closePartnerArt() }) {
                                Text("Close", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (partnerBitmap != null) {
                        val imageAspectRatio = remember(partnerBitmap) {
                            if (partnerBitmap.height > 0) {
                                partnerBitmap.width.toFloat() / partnerBitmap.height.toFloat()
                            } else 1f
                        }

                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            val containerAspect = maxWidth / maxHeight
                            val imageModifier = if (containerAspect > imageAspectRatio) {
                                Modifier
                                    .fillMaxHeight()
                                    .width(maxHeight * imageAspectRatio)
                            } else {
                                Modifier
                                    .fillMaxWidth()
                                    .height(maxWidth / imageAspectRatio)
                            }

                            Box(
                                modifier = imageModifier
                                    .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                                    .padding(1.5.dp)
                                    .clip(RoundedCornerShape(18.5.dp))
                                    .background(Color.White)
                            ) {
                                Image(
                                    bitmap = partnerBitmap,
                                    contentDescription = "Full Screen Partner Drawing",
                                    contentScale = ContentScale.FillBounds,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = "Loading partner's drawing...",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { drawingViewModel.closePartnerArt() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Back to Drawing Canvas")
                    }
                }
            }
        }
    }

    if (showPartnerPhotoDialog) {
        Dialog(
            onDismissRequest = { drawingViewModel.closePartnerPhoto(context) },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                securePolicy = if (isPartnerPhotoViewOnce) SecureFlagPolicy.SecureOn else SecureFlagPolicy.Inherit
            )
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
                        Column {
                            Text(
                                text = if (isPartnerPhotoViewOnce) "Partner's Photo ①" else "Partner's Photo",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (isPartnerPhotoViewOnce) {
                                Text(
                                    text = "View once — will be deleted when closed",
                                    color = Color.White.copy(alpha = 0.65f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!isPartnerPhotoViewOnce && partnerPhoto != null) {
                                IconButton(
                                    onClick = {
                                        scope.launch(Dispatchers.IO) {
                                            val success = savePhotoToGallery(context, partnerPhoto!!)
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(
                                                    context,
                                                    if (success) "Photo saved to gallery!" else "Failed to save photo",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(Color.White.copy(alpha = 0.15f), CircleShape)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.outline_download_24),
                                        contentDescription = "Download photo to phone",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            TextButton(onClick = { drawingViewModel.closePartnerPhoto(context) }) {
                                Text("Close", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (partnerPhotoBitmap != null) {
                        val photoAspectRatio = remember(partnerPhotoBitmap) {
                            if (partnerPhotoBitmap.height > 0) {
                                partnerPhotoBitmap.width.toFloat() / partnerPhotoBitmap.height.toFloat()
                            } else 1f
                        }

                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            val containerAspect = maxWidth / maxHeight
                            val imageModifier = if (containerAspect > photoAspectRatio) {
                                Modifier
                                    .fillMaxHeight()
                                    .width(maxHeight * photoAspectRatio)
                            } else {
                                Modifier
                                    .fillMaxWidth()
                                    .height(maxWidth / photoAspectRatio)
                            }

                            Box(
                                modifier = imageModifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                            ) {
                                Image(
                                    bitmap = partnerPhotoBitmap,
                                    contentDescription = "Full Screen Partner Photo",
                                    contentScale = ContentScale.FillBounds,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = "Loading partner's photo...",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { drawingViewModel.closePartnerPhoto(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp)
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
                            onClick = { drawingViewModel.openPartnerArt() },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.padding(end = 4.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Partner's Art", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (partnerPhotoBitmap != null) {
                        FilledTonalButton(
                            onClick = { drawingViewModel.openPartnerPhoto() },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.padding(end = 4.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = if (isPartnerPhotoViewOnce) "Partner's Photo ①" else "Partner's Photo",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isPartnerPhotoViewOnce) MaterialTheme.colorScheme.primary else Color.Unspecified
                            )
                        }
                    }

                    if (isFetching) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(20.dp)
                                .padding(end = 8.dp),
                            strokeWidth = 2.dp
                        )
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
            // DrawingCanvas has 100% of the remaining vertical space â€” NO cards pushing or resizing it!
            DrawingCanvas(
                paths = state.paths,
                currentPath = state.currentPath,
                onAction = drawingViewModel::onAction,
                backgroundColor = state.backgroundColor,
                graphicsLayer = graphicsLayer,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )

            CanvasControls(
                selectedColor = state.selectedColor,
                colors = flattenedColors,
                onSelectColor = { drawingViewModel.onAction(DrawingAction.OnSelectColor(it)) },
                onClearCanvas = { drawingViewModel.onAction(DrawingAction.OnClearCanvasClick) },
                onSendAction = {
                    if (isSending) return@CanvasControls
                    scope.launch {
                        val imageBitmap = graphicsLayer.toImageBitmap()
                        val pngByteArray = withContext(Dispatchers.Default) {
                            imageBitmap.toPngByteArray()
                        }
                        val result = drawingViewModel.uploadDrawing(pngByteArray)
                        result.onSuccess {
                            Toast.makeText(context, "Drawing sent!", Toast.LENGTH_SHORT).show()
                        }
                        result.onFailure { error ->
                            val msg = error.toUserFriendlyMessage()
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onUndo = { drawingViewModel.onAction(DrawingAction.OnUndo) },
                onRedo = { drawingViewModel.onAction(DrawingAction.OnRedo) },
                canUndo = canUndo,
                canRedo = canRedo,
                onFill = { drawingViewModel.onAction(DrawingAction.OnFill(it)) },
                onSetBrushType = { drawingViewModel.onAction(DrawingAction.OnSetBrushType(it)) },
                onSetBrushLevel = { level ->
                    val size = when(level) { 1 -> 6f; 2 -> 20f; 3 -> 40f; else -> 80f }
                    drawingViewModel.onAction(DrawingAction.OnSetBrushSize(size))
                },
                brushThickness = state.brushThickness,
                currentBrushType = state.brushType,
                isSending = isSending,
                onPickPhoto = {
                    photoPickerLauncher.launch("image/*")
                },
                isSendingPhoto = isSendingPhoto,
                isViewOnceSelected = isViewOnceSelected,
                onToggleViewOnce = { drawingViewModel.toggleViewOnce() },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
