package com.example.strokedetectionapp.ui.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

/**
 * Draws face mesh landmarks and connections on screen.
 * Color-codes regions by stroke risk level:
 *   - Green: Normal symmetry
 *   - Yellow: Mild asymmetry
 *   - Red: Severe asymmetry (stroke indicator)
 */
@Composable
fun FaceLandmarksOverlay(
    landmarks: List<NormalizedLandmark>?,
    mouthAsymmetry: Float = 0f,
    eyeDroop: Float = 0f,
    isFlipped: Boolean = false,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        landmarks ?: return@Canvas
        if (landmarks.size < 468) return@Canvas

        val canvasWidth = size.width
        val canvasHeight = size.height
        val centerX = canvasWidth / 2f
        val centerY = canvasHeight / 2f

        // Apply flip for front camera
        if (isFlipped) {
            drawContext.canvas.save()
            drawContext.transform.scale(-1f, 1f, Offset(centerX, centerY))
        }

        try {
            // Determine colors based on asymmetry levels
            val mouthColor = when {
                mouthAsymmetry > 0.15f -> Color.Red
                mouthAsymmetry > 0.08f -> Color.Yellow
                else -> Color(0xFF00E676) // Green
            }

            val eyeColor = when {
                eyeDroop > 0.20f -> Color.Red
                eyeDroop > 0.10f -> Color.Yellow
                else -> Color(0xFF00E676) // Green
            }

            val faceColor = Color(0xFF64B5F6) // Light blue for face outline

            // Draw face oval/contour connections
            drawFaceContour(landmarks, faceColor, canvasWidth, canvasHeight)

            // Draw eye regions
            drawEyeRegion(landmarks, eyeColor, canvasWidth, canvasHeight, isLeft = true)
            drawEyeRegion(landmarks, eyeColor, canvasWidth, canvasHeight, isLeft = false)

            // Draw mouth region
            drawMouthRegion(landmarks, mouthColor, canvasWidth, canvasHeight)

            // Draw nose reference line
            drawNoseLine(landmarks, Color.White.copy(alpha = 0.5f), canvasWidth, canvasHeight)

            // Draw key landmark dots
            drawKeyLandmarks(landmarks, canvasWidth, canvasHeight)

        } finally {
            if (isFlipped) {
                drawContext.canvas.restore()
            }
        }
    }
}

// ===== Drawing helpers =====

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFaceContour(
    landmarks: List<NormalizedLandmark>,
    color: Color,
    width: Float,
    height: Float
) {
    // Face oval landmarks (subset for cleaner visualization)
    val faceOval = listOf(
        10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288,
        397, 365, 379, 378, 400, 377, 152, 148, 176, 149, 150, 136,
        172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67, 109, 10
    )

    for (i in 0 until faceOval.size - 1) {
        val startIdx = faceOval[i]
        val endIdx = faceOval[i + 1]
        if (startIdx < landmarks.size && endIdx < landmarks.size) {
            drawLine(
                color = color.copy(alpha = 0.4f),
                start = Offset(landmarks[startIdx].x() * width, landmarks[startIdx].y() * height),
                end = Offset(landmarks[endIdx].x() * width, landmarks[endIdx].y() * height),
                strokeWidth = 2f,
                cap = StrokeCap.Round
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEyeRegion(
    landmarks: List<NormalizedLandmark>,
    color: Color,
    width: Float,
    height: Float,
    isLeft: Boolean
) {
    // Eye contour landmark indices
    val eyeIndices = if (isLeft) {
        listOf(33, 7, 163, 144, 145, 153, 154, 155, 133, 173, 157, 158, 159, 160, 161, 246, 33)
    } else {
        listOf(263, 249, 390, 373, 374, 380, 381, 382, 362, 398, 384, 385, 386, 387, 388, 466, 263)
    }

    for (i in 0 until eyeIndices.size - 1) {
        val startIdx = eyeIndices[i]
        val endIdx = eyeIndices[i + 1]
        if (startIdx < landmarks.size && endIdx < landmarks.size) {
            drawLine(
                color = color,
                start = Offset(landmarks[startIdx].x() * width, landmarks[startIdx].y() * height),
                end = Offset(landmarks[endIdx].x() * width, landmarks[endIdx].y() * height),
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMouthRegion(
    landmarks: List<NormalizedLandmark>,
    color: Color,
    width: Float,
    height: Float
) {
    // Outer lip contour
    val outerLip = listOf(
        61, 146, 91, 181, 84, 17, 314, 405, 321, 375, 291,
        409, 270, 269, 267, 0, 37, 39, 40, 185, 61
    )

    for (i in 0 until outerLip.size - 1) {
        val startIdx = outerLip[i]
        val endIdx = outerLip[i + 1]
        if (startIdx < landmarks.size && endIdx < landmarks.size) {
            drawLine(
                color = color,
                start = Offset(landmarks[startIdx].x() * width, landmarks[startIdx].y() * height),
                end = Offset(landmarks[endIdx].x() * width, landmarks[endIdx].y() * height),
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNoseLine(
    landmarks: List<NormalizedLandmark>,
    color: Color,
    width: Float,
    height: Float
) {
    // Draw a vertical reference line through the nose
    val noseTop = 6    // nose bridge
    val noseBottom = 1  // nose tip
    val chin = 152

    if (noseTop < landmarks.size && chin < landmarks.size) {
        drawLine(
            color = color,
            start = Offset(landmarks[noseTop].x() * width, landmarks[noseTop].y() * height),
            end = Offset(landmarks[chin].x() * width, landmarks[chin].y() * height),
            strokeWidth = 1.5f,
            cap = StrokeCap.Round,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawKeyLandmarks(
    landmarks: List<NormalizedLandmark>,
    width: Float,
    height: Float
) {
    // Key landmarks to highlight with dots
    val keyPoints = listOf(
        1,    // Nose tip
        33, 263,   // Eye outer corners
        133, 362,  // Eye inner corners
        61, 291,   // Mouth corners
        13, 14,    // Lip center
        70, 300,   // Eyebrow outer
        152        // Chin
    )

    for (idx in keyPoints) {
        if (idx < landmarks.size) {
            val x = landmarks[idx].x() * width
            val y = landmarks[idx].y() * height

            // Outer dot
            drawCircle(
                color = Color.White,
                radius = 5f,
                center = Offset(x, y)
            )
            // Inner dot
            drawCircle(
                color = Color(0xFF00E676),
                radius = 3f,
                center = Offset(x, y)
            )
        }
    }
}
