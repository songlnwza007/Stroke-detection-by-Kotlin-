package com.example.storkedetectionapp.ui.hand

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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.storkedetectionapp.ui.camera.CameraPreview
import com.example.storkedetectionapp.ui.camera.HandLandmarksOverlay
import com.example.storkedetectionapp.utils.HandDetector
import com.example.storkedetectionapp.viewmodel.HandViewModel
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandExerciseScreen(
    onNavigateBack: () -> Unit,
    viewModel: HandViewModel = viewModel()
) {
    val context = LocalContext.current

    // Collect state from ViewModel
    val uiState by viewModel.uiState.collectAsState()

    // Extract values from uiState
    val isExercising = uiState.isExercising
    val exerciseComplete = uiState.exerciseComplete
    val elapsedSeconds = uiState.elapsedSeconds
    val leftFistCount = uiState.leftFistCount
    val rightFistCount = uiState.rightFistCount
    val resultSaved = uiState.resultSaved

    // Camera permission
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

    // Mode toggle
    var useCameraMode by remember { mutableStateOf(false) }

    // Detection states
    var leftHandDetected by remember { mutableStateOf(false) }
    var rightHandDetected by remember { mutableStateOf(false) }
    var leftHandState by remember { mutableStateOf(HandDetector.HandState.NONE) }
    var rightHandState by remember { mutableStateOf(HandDetector.HandState.NONE) }
    var leftHandLandmarks by remember { mutableStateOf<List<NormalizedLandmark>?>(null) }
    var rightHandLandmarks by remember { mutableStateOf<List<NormalizedLandmark>?>(null) }

    // FPS tracking
    var fps by remember { mutableIntStateOf(0) }
    var frameCounter by remember { mutableIntStateOf(0) }
    var lastFpsTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Hand detector
    val handDetector = remember { HandDetector(context) }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose { handDetector.close() }
    }

    // Camera selector state
    var cameraSelector by remember { mutableStateOf(androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA) }

    // Camera controller with low resolution for performance
    val cameraController = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            this.cameraSelector = cameraSelector
            imageAnalysisTargetSize = CameraController.OutputSize(Size(320, 240))
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
        }
    }

    // Update camera selector when state changes
    LaunchedEffect(cameraSelector) {
        cameraController.cameraSelector = cameraSelector
    }

    // Image analysis
    LaunchedEffect(useCameraMode, isExercising, cameraSelector) {
        if (useCameraMode && isExercising && hasCameraPermission) {
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

                    // Get bitmap
                    val bitmap = imageProxy.toBitmap()

                    // Rotate if needed
                    val rotatedBitmap = if (imageProxy.imageInfo.rotationDegrees != 0) {
                        val matrix = Matrix().apply {
                            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                        }
                        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    } else {
                        bitmap
                    }

                    // Detect hands
                    val result = handDetector.detectHands(rotatedBitmap)

                    // Update states
                    leftHandDetected = result.leftHandDetected
                    rightHandDetected = result.rightHandDetected
                    leftHandState = result.leftHandState
                    rightHandState = result.rightHandState
                    leftHandLandmarks = result.leftHandLandmarks
                    rightHandLandmarks = result.rightHandLandmarks

                    // Update counts in ViewModel
                    viewModel.updateCounts(result.fistCountLeft, result.fistCountRight)

                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    imageProxy.close()
                }
            }
        } else {
            cameraController.clearImageAnalysisAnalyzer()
            leftHandLandmarks = null
            rightHandLandmarks = null
        }
    }

    // Reset detector when starting
    LaunchedEffect(isExercising) {
        if (isExercising) {
            handDetector.reset()
        }
    }

    // Navigate back after save
    LaunchedEffect(resultSaved) {
        if (resultSaved) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hand Exercise") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                exerciseComplete -> {
                    ExerciseCompleteView(
                        elapsedSeconds = elapsedSeconds,
                        leftFistCount = leftFistCount,
                        rightFistCount = rightFistCount,
                        onSave = { viewModel.saveResult() },
                        onRetry = {
                            handDetector.reset()
                            viewModel.reset()
                        }
                    )
                }
                isExercising -> {
                    ExerciseInProgressView(
                        useCameraMode = useCameraMode,
                        cameraController = cameraController,
                        elapsedSeconds = elapsedSeconds,
                        leftFistCount = leftFistCount,
                        rightFistCount = rightFistCount,
                        leftHandDetected = leftHandDetected,
                        rightHandDetected = rightHandDetected,
                        leftHandState = leftHandState,
                        rightHandState = rightHandState,
                        leftHandLandmarks = leftHandLandmarks,
                        rightHandLandmarks = rightHandLandmarks,
                        fps = fps,
                        onLeftFist = { viewModel.incrementLeftFist() },
                        onRightFist = { viewModel.incrementRightFist() },
                        onStop = { viewModel.stopExercise() },
                        onSwitchCamera = {
                            cameraSelector = if (cameraSelector == androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA) {
                                androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
                            } else {
                                androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA
                            }
                        }
                    )
                }
                else -> {
                    StartView(
                        hasCameraPermission = hasCameraPermission,
                        useCameraMode = useCameraMode,
                        onToggleCameraMode = {
                            if (!hasCameraPermission) {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            } else {
                                useCameraMode = !useCameraMode
                            }
                        },
                        onStart = { viewModel.startExercise() }
                    )
                }
            }
        }
    }
}

@Composable
private fun StartView(
    hasCameraPermission: Boolean,
    useCameraMode: Boolean,
    onToggleCameraMode: () -> Unit,
    onStart: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "👋", style = MaterialTheme.typography.displayLarge)

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Hand Exercise",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Make fists to count your exercises",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Mode selection card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Detection Mode", style = MaterialTheme.typography.titleMedium)

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (useCameraMode) "📷 Camera AI" else "👆 Manual",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (useCameraMode) "Auto-detect with skeleton" else "Tap buttons to count",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = useCameraMode,
                        onCheckedChange = { onToggleCameraMode() }
                    )
                }

                if (!hasCameraPermission && useCameraMode) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "⚠️ Camera permission required",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tips card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("💡 Tips", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("• Keep hands visible in camera", style = MaterialTheme.typography.bodySmall)
                Text("• Left hand on RIGHT side of screen", style = MaterialTheme.typography.bodySmall)
                Text("• Right hand on LEFT side of screen", style = MaterialTheme.typography.bodySmall)
                Text("• Good lighting improves detection", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Text("▶️ Start Exercise", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ExerciseInProgressView(
    useCameraMode: Boolean,
    cameraController: LifecycleCameraController,
    elapsedSeconds: Float,
    leftFistCount: Int,
    rightFistCount: Int,
    leftHandDetected: Boolean,
    rightHandDetected: Boolean,
    leftHandState: HandDetector.HandState,
    rightHandState: HandDetector.HandState,
    leftHandLandmarks: List<NormalizedLandmark>?,
    rightHandLandmarks: List<NormalizedLandmark>?,
    fps: Int,
    onLeftFist: () -> Unit,
    onRightFist: () -> Unit,
    onStop: () -> Unit,
    onSwitchCamera: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Camera preview
        if (useCameraMode) {
            CameraPreview(
                controller = cameraController,
                modifier = Modifier.fillMaxSize()
            )

            // Hand skeleton overlay
            val isFrontCamera = cameraController.cameraSelector == androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA
            HandLandmarksOverlay(
                leftHandLandmarks = leftHandLandmarks,
                rightHandLandmarks = rightHandLandmarks,
                leftHandState = leftHandState,
                rightHandState = rightHandState,
                isFlipped = isFrontCamera,
                modifier = Modifier.fillMaxSize()
            )
        }

        // UI overlay
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status bar (camera mode)
            if (useCameraMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Detection status
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                leftHandDetected && rightHandDetected -> Color(0xFF4CAF50)
                                leftHandDetected || rightHandDetected -> Color(0xFFFFC107)
                                else -> Color(0xFFFF5722)
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Pulsing indicator
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
                                text = when {
                                    leftHandDetected && rightHandDetected -> "✌️ Both"
                                    leftHandDetected -> "🤛 Left"
                                    rightHandDetected -> "🤜 Right"
                                    else -> "👀 Looking..."
                                },
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // FPS indicator
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



                Spacer(modifier = Modifier.height(12.dp))
            }

            // Switch Camera Button (only in camera mode)
            if (useCameraMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 16.dp)
                ) {
                    androidx.compose.material3.FloatingActionButton(
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
            }

            // Timer
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (useCameraMode)
                        Color.Black.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    text = formatTime(elapsedSeconds),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (useCameraMode) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Hand status cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                HandStatusCard(
                    title = "LEFT",
                    emoji = "🤛",
                    isDetected = leftHandDetected,
                    state = leftHandState,
                    count = leftFistCount
                )

                HandStatusCard(
                    title = "RIGHT",
                    emoji = "🤜",
                    isDetected = rightHandDetected,
                    state = rightHandState,
                    count = rightFistCount
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Manual buttons (non-camera mode only)
            if (!useCameraMode) {
                Text(
                    text = "Tap button when you make a fist",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    FistButton(emoji = "🤛", label = "Left", onClick = onLeftFist)
                    FistButton(emoji = "🤜", label = "Right", onClick = onRightFist)
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Stop button
            Button(
                onClick = onStop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("⏹️ Stop Exercise", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HandStatusCard(
    title: String,
    emoji: String,
    isDetected: Boolean,
    state: HandDetector.HandState,
    count: Int
) {
    val isFist = state == HandDetector.HandState.FIST
    val isOpen = state == HandDetector.HandState.OPEN

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isFist -> Color(0xFF4CAF50)      // Green
            isOpen -> Color(0xFFFFC107)      // Yellow
            isDetected -> Color(0xFF2196F3) // Blue
            else -> Color.Black.copy(alpha = 0.7f)
        },
        animationSpec = tween(150),
        label = "bgColor"
    )

    val borderColor = if (isFist) Color.White else Color.Transparent

    Card(
        modifier = Modifier
            .width(150.dp)
            .border(3.dp, borderColor, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = emoji, fontSize = 32.sp)

            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // State indicator
            Text(
                text = when (state) {
                    HandDetector.HandState.FIST -> "✊ FIST!"
                    HandDetector.HandState.OPEN -> "✋ OPEN"
                    else -> if (isDetected) "👀 ..." else "—"
                },
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Count display
            Text(
                text = "$count",
                color = Color.White,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "fists",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun FistButton(
    emoji: String,
    label: String,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "buttonScale"
    )

    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(100)
            isPressed = false
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = {
                isPressed = true
                onClick()
            },
            modifier = Modifier
                .size(100.dp)
                .scale(scale),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(text = emoji, fontSize = 40.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ExerciseCompleteView(
    elapsedSeconds: Float,
    leftFistCount: Int,
    rightFistCount: Int,
    onSave: () -> Unit,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Text(text = "🎉", style = MaterialTheme.typography.displayLarge)

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Exercise Complete!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Results card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(24.dp)) {
                ResultRow(label = "⏱️ Duration", value = formatTime(elapsedSeconds))

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                ResultRow(label = "🤛 Left Fists", value = "$leftFistCount")

                Spacer(modifier = Modifier.height(12.dp))

                ResultRow(label = "🤜 Right Fists", value = "$rightFistCount")

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                ResultRow(
                    label = "📊 Total Fists",
                    value = "${leftFistCount + rightFistCount}",
                    isBold = true
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Save button
        Button(
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("💾 Save Result", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Retry button
        OutlinedButton(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("🔄 Try Again", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ResultRow(
    label: String,
    value: String,
    isBold: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium,
            color = if (isBold) MaterialTheme.colorScheme.primary else Color.Unspecified
        )
    }
}

private fun formatTime(seconds: Float): String {
    val mins = (seconds / 60).toInt()
    val secs = (seconds % 60).toInt()
    return String.format("%02d:%02d", mins, secs)
}