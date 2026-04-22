package com.example.strokedetectionapp.ui.facial

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.strokedetectionapp.ui.camera.CameraPreview
import com.example.strokedetectionapp.ui.camera.FaceLandmarksOverlay
import com.example.strokedetectionapp.utils.FaceDetector
import com.example.strokedetectionapp.viewmodel.FacialViewModel
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FacialDetectionScreen(
    onNavigateBack: () -> Unit,
    viewModel: FacialViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // Camera permission state
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    // Camera selector
    var cameraSelector by remember { mutableStateOf(androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA) }

    // Camera controller
    val cameraController = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS or CameraController.IMAGE_CAPTURE)
            this.cameraSelector = cameraSelector
            imageAnalysisTargetSize = CameraController.OutputSize(Size(320, 240))
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
        }
    }

    // Face detector (ML-based)
    val faceDetector = remember { FaceDetector(context) }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose { faceDetector.close() }
    }

    // Update camera selector
    LaunchedEffect(cameraSelector) {
        cameraController.cameraSelector = cameraSelector
    }

    // State
    var showCamera by remember { mutableStateOf(false) }
    var fps by remember { mutableIntStateOf(0) }
    var frameCounter by remember { mutableIntStateOf(0) }
    var lastFpsTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Set up image analysis when camera is shown
    LaunchedEffect(showCamera, hasCameraPermission) {
        if (showCamera && hasCameraPermission && !uiState.analysisComplete) {
            val executor = Executors.newSingleThreadExecutor()

            cameraController.setImageAnalysisAnalyzer(executor) { imageProxy ->
                try {
                    // FPS calculation
                    frameCounter++
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastFpsTime >= 1000) {
                        fps = frameCounter
                        frameCounter = 0
                        lastFpsTime = currentTime
                    }

                    // Get and rotate bitmap
                    val bitmap = imageProxy.toBitmap()
                    val rotatedBitmap = if (imageProxy.imageInfo.rotationDegrees != 0) {
                        val matrix = Matrix().apply {
                            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                        }
                        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    } else {
                        bitmap
                    }

                    // Run ML face detection
                    val result = faceDetector.detectFace(rotatedBitmap)
                    viewModel.updateLiveResults(result)

                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    imageProxy.close()
                }
            }
        } else {
            cameraController.clearImageAnalysisAnalyzer()
        }
    }

    // Navigate back after save
    LaunchedEffect(uiState.resultSaved) {
        if (uiState.resultSaved) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Facial Analysis")
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = Color(0xFF00E676),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "AI",
                                color = Color.Black,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
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
                        confidence = uiState.confidence,
                        strokeProbability = uiState.strokeProbability,
                        onSave = { viewModel.saveResult() },
                        onRetry = {
                            faceDetector.reset()
                            viewModel.reset()
                            showCamera = false
                        }
                    )
                }

                // Analyzing
                uiState.isAnalyzing -> {
                    AnalyzingView()
                }

                // Camera view with live ML
                showCamera && hasCameraPermission -> {
                    LiveCameraView(
                        cameraController = cameraController,
                        uiState = uiState,
                        fps = fps,
                        onCapture = {
                            // Capture the current ML analysis result
                            val currentResult = faceDetector.lastResult
                            if (currentResult != null && currentResult.faceDetected) {
                                viewModel.captureAnalysis(currentResult)
                            }
                        },
                        onBack = {
                            showCamera = false
                            cameraController.clearImageAnalysisAnalyzer()
                        },
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

// ========== START VIEW ==========
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
            text = "🧠",
            style = MaterialTheme.typography.displayLarge
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "AI Facial Stroke Detection",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Uses Machine Learning to analyze\nfacial symmetry in real-time.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ML badge
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🔬", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "MediaPipe Face Landmarker • 478 Points • Offline",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Medium
                )
            }
        }

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
            Text(if (hasCameraPermission) "Start Live Analysis" else "Grant Camera Permission")
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

// ========== LIVE CAMERA VIEW WITH ML ==========
@Composable
private fun LiveCameraView(
    cameraController: LifecycleCameraController,
    uiState: com.example.strokedetectionapp.viewmodel.FacialUiState,
    fps: Int,
    onCapture: () -> Unit,
    onBack: () -> Unit,
    onSwitchCamera: () -> Unit
) {
    val isFrontCamera = cameraController.cameraSelector == androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera preview
        CameraPreview(
            controller = cameraController,
            modifier = Modifier.fillMaxSize()
        )

        // Face mesh overlay
        FaceLandmarksOverlay(
            landmarks = uiState.liveLandmarks,
            mouthAsymmetry = uiState.liveMouthAsymmetry,
            eyeDroop = uiState.liveEyeDroop,
            isFlipped = isFrontCamera,
            modifier = Modifier.fillMaxSize()
        )

        // Face guide overlay (when no face detected)
        if (!uiState.faceDetected) {
            FaceGuideOverlay()
        }

        // Top HUD - Detection status and FPS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Face detection status
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.faceDetected)
                        Color(0xFF4CAF50) else Color(0xFFFF5722)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pulsing dot
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val scale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.3f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(500),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "scale"
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .scale(scale)
                            .background(Color.White, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (uiState.faceDetected) "👤 Face Detected" else "👀 Looking...",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // FPS counter
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.6f)
                )
            ) {
                Text(
                    text = "$fps FPS",
                    color = if (fps >= 10) Color.Green else Color.Red,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        // Switch camera button
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 60.dp, end = 16.dp)
        ) {
            FloatingActionButton(
                onClick = onSwitchCamera,
                containerColor = Color.White,
                contentColor = Color.Black,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Switch Camera"
                )
            }
        }

        // Live asymmetry scores HUD (when face detected)
        if (uiState.faceDetected) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
            ) {
                AsymmetryBadge(
                    label = "Mouth",
                    value = uiState.liveMouthAsymmetry,
                    threshold = 0.15f
                )
                Spacer(modifier = Modifier.height(8.dp))
                AsymmetryBadge(
                    label = "Eyes",
                    value = uiState.liveEyeDroop,
                    threshold = 0.20f
                )
                Spacer(modifier = Modifier.height(8.dp))
                AsymmetryBadge(
                    label = "Face",
                    value = uiState.liveFacialDroop,
                    threshold = 0.12f
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
            // Instruction text
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.7f)
                )
            ) {
                Text(
                    text = if (uiState.faceDetected)
                        "✅ Face locked. Tap to analyze."
                    else
                        "👤 Position your face in front of the camera",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Capture button
            Button(
                onClick = onCapture,
                enabled = uiState.faceDetected,
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.faceDetected) Color.White else Color.Gray,
                    contentColor = Color.Black
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
            ) {
                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = "Capture Analysis",
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(
                onClick = onBack,
                colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
            ) {
                Text("Cancel")
            }
        }
    }
}

// ========== ASYMMETRY BADGE ==========
@Composable
private fun AsymmetryBadge(
    label: String,
    value: Float,
    threshold: Float
) {
    val color by animateColorAsState(
        targetValue = when {
            value > threshold -> Color(0xFFFF5252)  // Red
            value > threshold * 0.5f -> Color(0xFFFFC107)  // Yellow
            else -> Color(0xFF00E676)  // Green
        },
        animationSpec = tween(300),
        label = "badgeColor"
    )

    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.7f)
        ),
        modifier = Modifier.width(90.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 10.sp
            )
            Text(
                text = "${(value * 100).toInt()}%",
                color = color,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ========== FACE GUIDE OVERLAY ==========
@Composable
private fun FaceGuideOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val centerX = size.width / 2
        val centerY = size.height / 2 - 50.dp.toPx()

        val ovalWidth = size.width * 0.65f
        val ovalHeight = size.height * 0.45f

        // Dashed oval
        drawOval(
            color = Color.White,
            topLeft = Offset(centerX - ovalWidth / 2, centerY - ovalHeight / 2),
            size = ComposeSize(ovalWidth, ovalHeight),
            style = Stroke(
                width = 4.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 20f), 0f)
            )
        )

        // Corner markers
        val markerLength = 30.dp.toPx()
        val markerOffset = 10.dp.toPx()

        // Top-left
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

        // Top-right
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

        // Bottom-left
        drawLine(
            color = Color(0xFF4CAF50),
            start = Offset(centerX - ovalWidth / 2 - markerOffset, centerY + ovalHeight / 2 - 50),
            end = Offset(centerX - ovalWidth / 2 - markerOffset, centerY + ovalHeight / 2 - 50 - markerLength),
            strokeWidth = 4.dp.toPx()
        )

        // Bottom-right
        drawLine(
            color = Color(0xFF4CAF50),
            start = Offset(centerX + ovalWidth / 2 + markerOffset, centerY + ovalHeight / 2 - 50),
            end = Offset(centerX + ovalWidth / 2 + markerOffset, centerY + ovalHeight / 2 - 50 - markerLength),
            strokeWidth = 4.dp.toPx()
        )
    }
}

// ========== ANALYZING VIEW ==========
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
            text = "ML model processing facial landmarks",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ========== RESULT VIEW ==========
@Composable
private fun ResultView(
    isStroke: Boolean,
    mouthSymmetry: Float,
    eyeDroopRatio: Float,
    confidence: Float,
    strokeProbability: Float,
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

        // Result banner
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
                    text = if (isStroke) "⚠️" else "✅",
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

        Spacer(modifier = Modifier.height(24.dp))

        // ML Analysis Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🧠 ML Analysis Results",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Surface(
                        color = Color(0xFF00E676),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "AI",
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Mouth Symmetry
                ResultRow(
                    label = "Mouth Symmetry",
                    value = "${mouthSymmetry.toInt()}%",
                    isGood = mouthSymmetry >= 80
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Eye Droop
                ResultRow(
                    label = "Eye Symmetry",
                    value = "${eyeDroopRatio.toInt()}%",
                    isGood = eyeDroopRatio >= 80
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Stroke Probability
                ResultRow(
                    label = "Stroke Probability",
                    value = "${(strokeProbability * 100).toInt()}%",
                    isGood = strokeProbability < 0.45f
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Confidence
                ResultRow(
                    label = "Model Confidence",
                    value = "${confidence.toInt()}%",
                    isGood = confidence >= 60
                )
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

@Composable
private fun ResultRow(
    label: String,
    value: String,
    isGood: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        Text(
            text = value,
            color = if (isGood) Color(0xFF2E7D32) else Color(0xFFC62828),
            fontWeight = FontWeight.Bold
        )
    }
}
