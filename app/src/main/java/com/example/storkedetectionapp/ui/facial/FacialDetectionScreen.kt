package com.example.storkedetectionapp.ui.facial

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.storkedetectionapp.ui.camera.CameraPreview
import com.example.storkedetectionapp.utils.PythonBridge
import com.example.storkedetectionapp.viewmodel.FacialViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FacialDetectionScreen(
    onNavigateBack: () -> Unit,
    viewModel: FacialViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()

    // Camera permission state
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    // Camera selector state
    var cameraSelector by remember { mutableStateOf(androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA) }

    // Camera controller
    val cameraController = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            this.cameraSelector = cameraSelector
        }
    }

    // Update camera selector when state changes
    LaunchedEffect(cameraSelector) {
        cameraController.cameraSelector = cameraSelector
    }

    // State
    var showCamera by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }

    // Navigate back after saving
    LaunchedEffect(uiState.resultSaved) {
        if (uiState.resultSaved) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Facial Analysis") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.reset()
                        onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                // Show result
                uiState.analysisComplete -> {
                    ResultView(
                        isStroke = uiState.isStroke,
                        mouthSymmetry = uiState.mouthSymmetry,
                        eyeDroopRatio = uiState.eyeDroopRatio,
                        onSave = { viewModel.saveResult() },
                        onRetry = {
                            viewModel.reset()
                            showCamera = false
                        }
                    )
                }

                // Analyzing
                uiState.isAnalyzing -> {
                    AnalyzingView()
                }

                // Camera view
                showCamera && hasCameraPermission -> {
                    CameraViewWithGuide(
                        cameraController = cameraController,
                        isCapturing = isCapturing,
                        onCapture = {
                            isCapturing = true
                            captureAndAnalyze(
                                controller = cameraController,
                                context = context,
                                onResult = { bitmap ->
                                    scope.launch {
                                        val result = PythonBridge.analyzeFace(bitmap)
                                        viewModel.setAnalysisResult(
                                            isStroke = result.isStroke,
                                            mouthSymmetry = result.mouthSymmetry,
                                            eyeDroop = result.eyeDroop
                                        )
                                        isCapturing = false
                                    }
                                },
                                onError = {
                                    isCapturing = false
                                }
                            )
                        },
                        onBack = { showCamera = false },
                        onSwitchCamera = {
                            cameraSelector = if (cameraSelector == androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA) {
                                androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
                            } else {
                                androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA
                            }
                        }
                    )
                }

                // Start view
                else -> {
                    StartView(
                        hasCameraPermission = hasCameraPermission,
                        onRequestPermission = {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        onStartCamera = { showCamera = true },
                        onSimulate = { viewModel.analyzeImage() }
                    )
                }
            }
        }
    }
}

@Composable
private fun StartView(
    hasCameraPermission: Boolean,
    onRequestPermission: () -> Unit,
    onStartCamera: () -> Unit,
    onSimulate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "📸",
            style = MaterialTheme.typography.displayLarge
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Facial Stroke Detection",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Position your face in front of the camera.\nThe app will analyze facial symmetry.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = {
                if (hasCameraPermission) {
                    onStartCamera()
                } else {
                    onRequestPermission()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (hasCameraPermission) "Open Camera" else "Grant Camera Permission")
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onSimulate,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("🧪 Simulate Analysis (Test)")
        }
    }
}

@Composable
private fun CameraViewWithGuide(
    cameraController: LifecycleCameraController,
    isCapturing: Boolean,
    onCapture: () -> Unit,
    onBack: () -> Unit,
    onSwitchCamera: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Camera preview
        CameraPreview(
            controller = cameraController,
            modifier = Modifier.fillMaxSize()
        )

        // ========== FACE GUIDE OVERLAY ==========
        FaceGuideOverlay()

        // Instructions at top
        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.7f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "👤 Position your face inside the oval",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Keep your face straight and well-lit",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Status indicator
        Card(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF4CAF50)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color.White, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Camera Active",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Switch Camera Button
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 70.dp, end = 16.dp) // Below the "Camera Active" badge
        ) {
            FloatingActionButton(
                onClick = onSwitchCamera,
                containerColor = Color.White,
                contentColor = Color.Black,
                modifier = Modifier.size(50.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Switch Camera"
                )
            }
        }

        // Bottom controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Capture button
            Button(
                onClick = onCapture,
                enabled = !isCapturing,
                modifier = Modifier
                    .size(80.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
            ) {
                if (isCapturing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = Color.Black,
                        strokeWidth = 3.dp
                    )
                } else {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = "Capture",
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Cancel button
            TextButton(
                onClick = onBack,
                colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
            ) {
                Text("Cancel")
            }
        }
    }
}

// ========== FACE GUIDE OVERLAY COMPONENT ==========
@Composable
private fun FaceGuideOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val centerX = size.width / 2
        val centerY = size.height / 2 - 50.dp.toPx()

        val ovalWidth = size.width * 0.65f
        val ovalHeight = size.height * 0.45f

        // Draw semi-transparent background with oval cutout effect
        // Draw the oval border (dashed line)
        drawOval(
            color = Color.White,
            topLeft = Offset(centerX - ovalWidth / 2, centerY - ovalHeight / 2),
            size = Size(ovalWidth, ovalHeight),
            style = Stroke(
                width = 4.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 20f), 0f)
            )
        )

        // Draw corner markers for better visibility
        val markerLength = 30.dp.toPx()
        val markerOffset = 10.dp.toPx()

        // Top-left corner
        drawLine(
            color = Color(0xFF4CAF50),
            start = Offset(centerX - ovalWidth / 2 - markerOffset, centerY - ovalHeight / 2 + 50),
            end = Offset(centerX - ovalWidth / 2 - markerOffset, centerY - ovalHeight / 2 + 50 + markerLength),
            strokeWidth = 4.dp.toPx()
        )
        drawLine(
            color = Color(0xFF4CAF50),
            start = Offset(centerX - ovalWidth / 2 + 20, centerY - ovalHeight / 2 - markerOffset),
            end = Offset(centerX - ovalWidth / 2 + 20 + markerLength, centerY - ovalHeight / 2 - markerOffset),
            strokeWidth = 4.dp.toPx()
        )

        // Top-right corner
        drawLine(
            color = Color(0xFF4CAF50),
            start = Offset(centerX + ovalWidth / 2 + markerOffset, centerY - ovalHeight / 2 + 50),
            end = Offset(centerX + ovalWidth / 2 + markerOffset, centerY - ovalHeight / 2 + 50 + markerLength),
            strokeWidth = 4.dp.toPx()
        )
        drawLine(
            color = Color(0xFF4CAF50),
            start = Offset(centerX + ovalWidth / 2 - 20, centerY - ovalHeight / 2 - markerOffset),
            end = Offset(centerX + ovalWidth / 2 - 20 - markerLength, centerY - ovalHeight / 2 - markerOffset),
            strokeWidth = 4.dp.toPx()
        )

        // Bottom-left corner
        drawLine(
            color = Color(0xFF4CAF50),
            start = Offset(centerX - ovalWidth / 2 - markerOffset, centerY + ovalHeight / 2 - 50),
            end = Offset(centerX - ovalWidth / 2 - markerOffset, centerY + ovalHeight / 2 - 50 - markerLength),
            strokeWidth = 4.dp.toPx()
        )

        // Bottom-right corner
        drawLine(
            color = Color(0xFF4CAF50),
            start = Offset(centerX + ovalWidth / 2 + markerOffset, centerY + ovalHeight / 2 - 50),
            end = Offset(centerX + ovalWidth / 2 + markerOffset, centerY + ovalHeight / 2 - 50 - markerLength),
            strokeWidth = 4.dp.toPx()
        )

        // Draw face feature guides (eyes and mouth positions)
        val eyeY = centerY - ovalHeight * 0.1f
        val eyeSpacing = ovalWidth * 0.25f
        val eyeSize = 15.dp.toPx()

//        // Left eye marker
//        drawCircle(
//            color = Color.Yellow.copy(alpha = 0.5f),
//            radius = eyeSize,
//            center = Offset(centerX - eyeSpacing, eyeY),
//            style = Stroke(width = 2.dp.toPx())
//        )
//
//        // Right eye marker
//        drawCircle(
//            color = Color.Yellow.copy(alpha = 0.5f),
//            radius = eyeSize,
//            center = Offset(centerX + eyeSpacing, eyeY),
//            style = Stroke(width = 2.dp.toPx())
//        )

        // Mouth marker
//        val mouthY = centerY + ovalHeight * 0.15f
//        drawOval(
//            color = Color.Yellow.copy(alpha = 0.5f),
//            topLeft = Offset(centerX - 25.dp.toPx(), mouthY - 8.dp.toPx()),
//            size = Size(50.dp.toPx(), 16.dp.toPx()),
//            style = Stroke(width = 2.dp.toPx())
//        )
    }
}

@Composable
private fun AnalyzingView() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(80.dp),
            strokeWidth = 8.dp
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Analyzing...",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Processing facial features",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ResultView(
    isStroke: Boolean,
    mouthSymmetry: Float,
    eyeDroopRatio: Float,
    onSave: () -> Unit,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = if (isStroke) Color(0xFFFFEBEE) else Color(0xFFE8F5E9),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isStroke) "⚠️" else "✓",
                    style = MaterialTheme.typography.displayMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isStroke) "STROKE INDICATORS DETECTED" else "NORMAL",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (isStroke) Color(0xFFC62828) else Color(0xFF2E7D32),
                    textAlign = TextAlign.Center
                )

                if (isStroke) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Please consult a medical professional",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFC62828)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Analysis Results",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Mouth Symmetry")
                    Text(
                        text = "${mouthSymmetry.toInt()}%",
                        color = if (mouthSymmetry >= 80) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Eye Droop Ratio")
                    Text(
                        text = "${eyeDroopRatio.toInt()}%",
                        color = if (eyeDroopRatio >= 80) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("💾 Save Result")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("🔄 Try Again")
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

private fun captureAndAnalyze(
    controller: LifecycleCameraController,
    context: android.content.Context,
    onResult: (Bitmap) -> Unit,
    onError: () -> Unit
) {
    controller.takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val bitmap = image.toBitmap()
                    val matrix = Matrix().apply {
                        postRotate(image.imageInfo.rotationDegrees.toFloat())
                    }
                    val rotatedBitmap = Bitmap.createBitmap(
                        bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                    )
                    onResult(rotatedBitmap)
                } catch (e: Exception) {
                    e.printStackTrace()
                    onError()
                } finally {
                    image.close()
                }
            }

            override fun onError(exception: androidx.camera.core.ImageCaptureException) {
                exception.printStackTrace()
                onError()
            }
        }
    )
}