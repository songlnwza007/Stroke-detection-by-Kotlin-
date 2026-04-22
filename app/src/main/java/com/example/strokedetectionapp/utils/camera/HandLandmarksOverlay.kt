package com.example.strokedetectionapp.ui.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.example.strokedetectionapp.utils.HandDetector

/**
 * Draws hand landmarks on screen (like Python's mp_drawing.draw_landmarks)
 */
@Composable
fun HandLandmarksOverlay(
    leftHandLandmarks: List<NormalizedLandmark>?,
    rightHandLandmarks: List<NormalizedLandmark>?,
    leftHandState: HandDetector.HandState,
    rightHandState: HandDetector.HandState,
    isFlipped: Boolean = false,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        val canvasWidth = size.width
        val canvasHeight = size.height
        val centerX = canvasWidth / 2f
        val centerY = canvasHeight / 2f

        // Apply flip if needed (for front camera)
        if (isFlipped) {
            drawContext.canvas.save()
            drawContext.transform.scale(-1f, 1f, Offset(centerX, centerY))
        }

        try {
            // Draw left hand landmarks (GREEN for open, RED for fist)
            leftHandLandmarks?.let { landmarks ->
                val color = when (leftHandState) {
                    HandDetector.HandState.OPEN -> Color.Green
                    HandDetector.HandState.FIST -> Color.Red
                    else -> Color.Yellow
                }
                drawHandLandmarks(landmarks, color, width, height)
            }

            // Draw right hand landmarks (BLUE for open, MAGENTA for fist)
            rightHandLandmarks?.let { landmarks ->
                val color = when (rightHandState) {
                    HandDetector.HandState.OPEN -> Color.Cyan
                    HandDetector.HandState.FIST -> Color.Magenta
                    else -> Color.Yellow
                }
                drawHandLandmarks(landmarks, color, width, height)
            }
        } finally {
            if (isFlipped) {
                drawContext.canvas.restore()
            }
        }
    }
}

/**
 * Extension function to draw landmarks
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHandLandmarks(
    landmarks: List<NormalizedLandmark>,
    color: Color,
    width: Float,
    height: Float
) {
    if (landmarks.size < 21) return

    // Hand connections (same as MediaPipe HAND_CONNECTIONS)
    val connections = listOf(
        // Thumb
        Pair(0, 1), Pair(1, 2), Pair(2, 3), Pair(3, 4),
        // Index finger
        Pair(0, 5), Pair(5, 6), Pair(6, 7), Pair(7, 8),
        // Middle finger
        Pair(0, 9), Pair(9, 10), Pair(10, 11), Pair(11, 12),
        // Ring finger
        Pair(0, 13), Pair(13, 14), Pair(14, 15), Pair(15, 16),
        // Pinky
        Pair(0, 17), Pair(17, 18), Pair(18, 19), Pair(19, 20),
        // Palm
        Pair(5, 9), Pair(9, 13), Pair(13, 17)
    )

    // Draw connections (lines)
    for ((start, end) in connections) {
        val startX = getLandmarkXValue(landmarks, start) * width
        val startY = getLandmarkYValue(landmarks, start) * height
        val endX = getLandmarkXValue(landmarks, end) * width
        val endY = getLandmarkYValue(landmarks, end) * height

        drawLine(
            color = color,
            start = Offset(startX, startY),
            end = Offset(endX, endY),
            strokeWidth = 4f,
            cap = StrokeCap.Round
        )
    }

    // Draw landmarks (circles)
    for (i in landmarks.indices) {
        val x = getLandmarkXValue(landmarks, i) * width
        val y = getLandmarkYValue(landmarks, i) * height

        // Outer circle
        drawCircle(
            color = color,
            radius = 8f,
            center = Offset(x, y)
        )

        // Inner circle (white)
        drawCircle(
            color = Color.White,
            radius = 4f,
            center = Offset(x, y)
        )
    }
}

/**
 * Get X coordinate from landmark using reflection
 */
private fun getLandmarkXValue(landmarks: List<NormalizedLandmark>, index: Int): Float {
    return try {
        val landmark = landmarks[index]
        val methods = landmark.javaClass.methods
        val xMethod = methods.find {
            (it.name == "x" || it.name == "getX") && it.parameterCount == 0
        }
        if (xMethod != null) {
            val result = xMethod.invoke(landmark)
            when (result) {
                is Float -> result
                is Double -> result.toFloat()
                is Number -> result.toFloat()
                else -> 0f
            }
        } else 0f
    } catch (e: Exception) { 0f }
}

/**
 * Get Y coordinate from landmark using reflection
 */
private fun getLandmarkYValue(landmarks: List<NormalizedLandmark>, index: Int): Float {
    return try {
        val landmark = landmarks[index]
        val methods = landmark.javaClass.methods
        val yMethod = methods.find {
            (it.name == "y" || it.name == "getY") && it.parameterCount == 0
        }
        if (yMethod != null) {
            val result = yMethod.invoke(landmark)
            when (result) {
                is Float -> result
                is Double -> result.toFloat()
                is Number -> result.toFloat()
                else -> 0f
            }
        } else 0f
    } catch (e: Exception) { 0f }
}
