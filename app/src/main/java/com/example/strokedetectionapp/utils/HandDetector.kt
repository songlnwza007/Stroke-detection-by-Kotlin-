package com.example.strokedetectionapp.utils

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

/**
 * Optimized Hand Detector - Fixed right hand detection
 */
class HandDetector(context: Context) {

    private var handLandmarker: HandLandmarker? = null

    private var previousStateLeft: HandState? = null
    private var previousStateRight: HandState? = null

    private var _fistCountLeft: Int = 0
    val fistCountLeft: Int get() = _fistCountLeft

    private var _fistCountRight: Int = 0
    val fistCountRight: Int get() = _fistCountRight

    private var _leftHandDetected: Boolean = false
    val leftHandDetected: Boolean get() = _leftHandDetected

    private var _rightHandDetected: Boolean = false
    val rightHandDetected: Boolean get() = _rightHandDetected

    private var _leftHandState: HandState = HandState.NONE
    val leftHandState: HandState get() = _leftHandState

    private var _rightHandState: HandState = HandState.NONE
    val rightHandState: HandState get() = _rightHandState

    private var _leftHandLandmarks: List<NormalizedLandmark>? = null
    val leftHandLandmarks: List<NormalizedLandmark>? get() = _leftHandLandmarks

    private var _rightHandLandmarks: List<NormalizedLandmark>? = null
    val rightHandLandmarks: List<NormalizedLandmark>? get() = _rightHandLandmarks

    // Frame skipping - process every 3rd frame for better performance
    private var frameCount = 0
    private val processEveryNFrames = 3

    enum class HandState {
        NONE,
        OPEN,
        FIST
    }

    init {
        setupDetector(context)
    }

    private fun setupDetector(context: Context) {
        try {
            // Use CPU with optimized settings
            val baseOptions = BaseOptions.builder()
                .setDelegate(Delegate.CPU)
                .setModelAssetPath("hand_landmarker.task")
                .build()

            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setMinHandDetectionConfidence(0.4f)  // Lower for better detection
                .setMinTrackingConfidence(0.4f)
                .setMinHandPresenceConfidence(0.4f)
                .setNumHands(2)
                .setRunningMode(RunningMode.IMAGE)
                .build()

            handLandmarker = HandLandmarker.createFromOptions(context, options)
            Log.d("HandDetector", "MediaPipe initialized")
        } catch (e: Exception) {
            Log.e("HandDetector", "Init error: ${e.message}")
        }
    }

    fun detectHands(bitmap: Bitmap): HandDetectionResult {
        // Frame skipping
        frameCount++
        if (frameCount % processEveryNFrames != 0) {
            return getCurrentResult()
        }

        // Reset for this frame
        _leftHandDetected = false
        _rightHandDetected = false
        _leftHandState = HandState.NONE
        _rightHandState = HandState.NONE
        _leftHandLandmarks = null
        _rightHandLandmarks = null

        val detector = handLandmarker ?: return getCurrentResult()

        try {
            // Scale down for speed (very small)
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 256, 192, false)

            val mpImage = BitmapImageBuilder(scaledBitmap).build()
            val result: HandLandmarkerResult = detector.detect(mpImage)

            val landmarksList = result.landmarks()

            @Suppress("DEPRECATION")
            val handednessList = result.handednesses()

            if (landmarksList.isEmpty()) {
                return getCurrentResult()
            }

            Log.d("HandDetector", "Found ${landmarksList.size} hand(s)")

            // Process each detected hand
            for (i in landmarksList.indices) {
                if (i >= handednessList.size) break

                val landmarks = landmarksList[i]
                val categories = handednessList[i]

                if (categories.isEmpty()) continue

                // Get label from MediaPipe
                val rawLabel = getCategoryLabel(categories)
                Log.d("HandDetector", "Hand $i raw label: $rawLabel")

                // FIXED: Don't flip - use position-based detection instead
                // Check which side of screen the hand is on
                val centerX = getAverageX(landmarks)

                // Unified Logic:
                // Left Hand is on Screen RIGHT (x > 0.5)
                // Right Hand is on Screen LEFT (x <= 0.5)
                val isUserLeftHand = centerX > 0.5f
                val isUserRightHand = centerX <= 0.5f

                Log.d("HandDetector", "Hand center X: $centerX, isLeft: $isUserLeftHand, isRight: $isUserRightHand")

                val isOpen = checkIfHandOpen(landmarks)
                val currentState = if (isOpen) HandState.OPEN else HandState.FIST

                // Assign to left or right based on position
                if (isUserLeftHand && !_leftHandDetected) {
                    processLeftHand(landmarks, currentState)
                } else if (isUserRightHand && !_rightHandDetected) {
                    processRightHand(landmarks, currentState)
                }
            }

        } catch (e: Exception) {
            Log.e("HandDetector", "Error: ${e.message}")
        }

        return getCurrentResult()
    }

    private fun getAverageX(landmarks: List<NormalizedLandmark>): Float {
        if (landmarks.isEmpty()) return 0.5f

        var sum = 0f
        var count = 0

        // Use wrist (0), middle finger base (9), and pinky base (17) for center
        val keyPoints = listOf(0, 9, 17)

        for (idx in keyPoints) {
            if (idx < landmarks.size) {
                sum += getLandmarkX(landmarks, idx)
                count++
            }
        }

        return if (count > 0) sum / count else 0.5f
    }

    private fun processLeftHand(landmarks: List<NormalizedLandmark>, currentState: HandState) {
        _leftHandDetected = true
        _leftHandState = currentState
        _leftHandLandmarks = landmarks

        if (previousStateLeft != null && previousStateLeft != currentState) {
            if (currentState == HandState.FIST) {
                _fistCountLeft++
                Log.d("HandDetector", "LEFT FIST! Count: $_fistCountLeft")
            }
        }
        previousStateLeft = currentState
    }

    private fun processRightHand(landmarks: List<NormalizedLandmark>, currentState: HandState) {
        _rightHandDetected = true
        _rightHandState = currentState
        _rightHandLandmarks = landmarks

        if (previousStateRight != null && previousStateRight != currentState) {
            if (currentState == HandState.FIST) {
                _fistCountRight++
                Log.d("HandDetector", "RIGHT FIST! Count: $_fistCountRight")
            }
        }
        previousStateRight = currentState
    }

    private fun getCategoryLabel(category: Any): String {
        return try {
            val methods = category.javaClass.methods
            for (name in listOf("categoryName", "displayName", "getLabel")) {
                val method = methods.find { it.name == name && it.parameterCount == 0 }
                if (method != null) {
                    val result = method.invoke(category)
                    if (result is String && result.isNotEmpty()) return result
                }
            }
            "unknown"
        } catch (e: Exception) { "unknown" }
    }

    private fun checkIfHandOpen(landmarks: List<NormalizedLandmark>): Boolean {
        if (landmarks.size < 21) return false

        var openFingers = 0

        // Index: tip=8, pip=6
        if (getLandmarkY(landmarks, 8) < getLandmarkY(landmarks, 6)) openFingers++

        // Middle: tip=12, pip=10
        if (getLandmarkY(landmarks, 12) < getLandmarkY(landmarks, 10)) openFingers++

        // Ring: tip=16, pip=14
        if (getLandmarkY(landmarks, 16) < getLandmarkY(landmarks, 14)) openFingers++

        // Pinky: tip=20, pip=18
        if (getLandmarkY(landmarks, 20) < getLandmarkY(landmarks, 18)) openFingers++

        // Thumb: compare X positions
        val thumbTipX = getLandmarkX(landmarks, 4)
        val thumbPipX = getLandmarkX(landmarks, 2)
        val wristX = getLandmarkX(landmarks, 0)

        // Determine if thumb is extended based on hand orientation
        val thumbExtended = kotlin.math.abs(thumbTipX - wristX) > kotlin.math.abs(thumbPipX - wristX)
        if (thumbExtended) openFingers++

        return openFingers >= 4
    }

    private fun getLandmarkY(landmarks: List<NormalizedLandmark>, index: Int): Float {
        return try {
            val landmark = landmarks[index]
            val method = landmark.javaClass.methods.find {
                (it.name == "y" || it.name == "getY") && it.parameterCount == 0
            }
            when (val result = method?.invoke(landmark)) {
                is Float -> result
                is Double -> result.toFloat()
                is Number -> result.toFloat()
                else -> 0f
            }
        } catch (e: Exception) { 0f }
    }

    private fun getLandmarkX(landmarks: List<NormalizedLandmark>, index: Int): Float {
        return try {
            val landmark = landmarks[index]
            val method = landmark.javaClass.methods.find {
                (it.name == "x" || it.name == "getX") && it.parameterCount == 0
            }
            when (val result = method?.invoke(landmark)) {
                is Float -> result
                is Double -> result.toFloat()
                is Number -> result.toFloat()
                else -> 0f
            }
        } catch (e: Exception) { 0f }
    }

    private fun getCurrentResult(): HandDetectionResult {
        return HandDetectionResult(
            leftHandDetected = _leftHandDetected,
            rightHandDetected = _rightHandDetected,
            leftHandState = _leftHandState,
            rightHandState = _rightHandState,
            fistCountLeft = _fistCountLeft,
            fistCountRight = _fistCountRight,
            leftHandLandmarks = _leftHandLandmarks,
            rightHandLandmarks = _rightHandLandmarks
        )
    }

    fun reset() {
        _fistCountLeft = 0
        _fistCountRight = 0
        previousStateLeft = null
        previousStateRight = null
        _leftHandDetected = false
        _rightHandDetected = false
        _leftHandState = HandState.NONE
        _rightHandState = HandState.NONE
        _leftHandLandmarks = null
        _rightHandLandmarks = null
        frameCount = 0
    }

    fun close() {
        handLandmarker?.close()
        handLandmarker = null
    }
}

data class HandDetectionResult(
    val leftHandDetected: Boolean,
    val rightHandDetected: Boolean,
    val leftHandState: HandDetector.HandState,
    val rightHandState: HandDetector.HandState,
    val fistCountLeft: Int,
    val fistCountRight: Int,
    val leftHandLandmarks: List<NormalizedLandmark>? = null,
    val rightHandLandmarks: List<NormalizedLandmark>? = null
)
