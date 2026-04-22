package com.example.strokedetectionapp.utils

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * ML-based Face Detector using MediaPipe Face Landmarker.
 *
 * Detects 478 facial landmarks and computes asymmetry scores
 * for stroke detection. Runs entirely offline on-device.
 *
 * Key landmark indices (MediaPipe Face Mesh):
 * - Nose tip: 1
 * - Left eye inner corner: 133, Right eye inner corner: 362
 * - Left eye outer corner: 33, Right eye outer corner: 263
 * - Left mouth corner: 61, Right mouth corner: 291
 * - Upper lip center: 13, Lower lip center: 14
 * - Left eyebrow: 70, Right eyebrow: 300
 * - Chin: 152
 */
class FaceDetector(context: Context) {

    companion object {
        private const val TAG = "FaceDetector"
        private const val MODEL_PATH = "face_landmarker.task"

        // Stroke detection thresholds
        const val MOUTH_ASYMMETRY_THRESHOLD = 0.15f  // >15% difference = concerning
        const val EYE_DROOP_THRESHOLD = 0.20f          // >20% difference = concerning
        const val FACIAL_DROOP_THRESHOLD = 0.12f       // >12% difference = concerning
        const val STROKE_PROBABILITY_THRESHOLD = 0.45f // Combined score threshold

        // Symmetric landmark pair indices [left, right]
        // Mouth region
        private val MOUTH_CORNER_LEFT = 61
        private val MOUTH_CORNER_RIGHT = 291
        private val UPPER_LIP_LEFT = 40
        private val UPPER_LIP_RIGHT = 270
        private val LOWER_LIP_LEFT = 88
        private val LOWER_LIP_RIGHT = 318

        // Eye region
        private val EYE_TOP_LEFT = 159
        private val EYE_BOTTOM_LEFT = 145
        private val EYE_INNER_LEFT = 133
        private val EYE_OUTER_LEFT = 33

        private val EYE_TOP_RIGHT = 386
        private val EYE_BOTTOM_RIGHT = 374
        private val EYE_INNER_RIGHT = 362
        private val EYE_OUTER_RIGHT = 263

        // Eyebrow region
        private val EYEBROW_INNER_LEFT = 107
        private val EYEBROW_OUTER_LEFT = 70
        private val EYEBROW_INNER_RIGHT = 336
        private val EYEBROW_OUTER_RIGHT = 300

        // Nose (midline reference)
        private val NOSE_TIP = 1
        private val NOSE_BRIDGE = 6
        private val FOREHEAD_CENTER = 10
        private val CHIN = 152

        // Jawline pairs for overall face droop
        private val JAW_LEFT = listOf(132, 58, 172, 136, 150)
        private val JAW_RIGHT = listOf(361, 288, 397, 365, 379)
    }

    private var faceLandmarker: FaceLandmarker? = null
    private var _lastLandmarks: List<NormalizedLandmark>? = null
    val lastLandmarks: List<NormalizedLandmark>? get() = _lastLandmarks

    private var _lastResult: FaceAnalysisResult? = null
    val lastResult: FaceAnalysisResult? get() = _lastResult

    // Frame skipping for performance
    private var frameCount = 0
    private val processEveryNFrames = 2

    init {
        setupDetector(context)
    }

    private fun setupDetector(context: Context) {
        try {
            val baseOptions = BaseOptions.builder()
                .setDelegate(Delegate.CPU)
                .setModelAssetPath(MODEL_PATH)
                .build()

            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setMinFaceDetectionConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setMinFacePresenceConfidence(0.5f)
                .setNumFaces(1)
                .setRunningMode(RunningMode.IMAGE)
                .build()

            faceLandmarker = FaceLandmarker.createFromOptions(context, options)
            Log.d(TAG, "MediaPipe FaceLandmarker initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize FaceLandmarker: ${e.message}", e)
        }
    }

    /**
     * Detect face and analyze for stroke indicators.
     * Returns null if no face is detected.
     */
    fun detectFace(bitmap: Bitmap): FaceAnalysisResult? {
        frameCount++
        if (frameCount % processEveryNFrames != 0) {
            return _lastResult
        }

        val detector = faceLandmarker ?: return null

        try {
            // Scale down for speed
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 320, 240, false)
            val mpImage = BitmapImageBuilder(scaledBitmap).build()
            val result: FaceLandmarkerResult = detector.detect(mpImage)

            val landmarksList = result.faceLandmarks()
            if (landmarksList.isEmpty()) {
                _lastLandmarks = null
                _lastResult = null
                Log.d(TAG, "No face detected")
                return null
            }

            val landmarks = landmarksList[0]
            _lastLandmarks = landmarks

            // Compute asymmetry scores
            val mouthAsymmetry = computeMouthAsymmetry(landmarks)
            val eyeDroop = computeEyeDroop(landmarks)
            val facialDroop = computeFacialDroop(landmarks)

            // Compute overall stroke probability
            val strokeProbability = computeStrokeProbability(mouthAsymmetry, eyeDroop, facialDroop)

            // Convert asymmetry ratios to "symmetry percentages" for display
            // 0% asymmetry = 100% symmetry, 50% asymmetry = 50% symmetry
            val mouthSymmetryPercent = ((1f - mouthAsymmetry.coerceIn(0f, 1f)) * 100f)
            val eyeSymmetryPercent = ((1f - eyeDroop.coerceIn(0f, 1f)) * 100f)

            val isStroke = strokeProbability >= STROKE_PROBABILITY_THRESHOLD

            val analysisResult = FaceAnalysisResult(
                faceDetected = true,
                landmarks = landmarks,
                mouthAsymmetry = mouthAsymmetry,
                eyeDroop = eyeDroop,
                facialDroop = facialDroop,
                mouthSymmetryPercent = mouthSymmetryPercent,
                eyeSymmetryPercent = eyeSymmetryPercent,
                strokeProbability = strokeProbability,
                isStroke = isStroke,
                confidence = result.faceLandmarks()[0].let {
                    // Use inverse of asymmetry as a proxy for confidence
                    (1f - strokeProbability).coerceIn(0f, 1f) * 100f
                }
            )

            _lastResult = analysisResult
            Log.d(TAG, "Face analyzed: mouth=${mouthAsymmetry.format()}, eye=${eyeDroop.format()}, " +
                    "droop=${facialDroop.format()}, prob=${strokeProbability.format()}, stroke=$isStroke")

            return analysisResult

        } catch (e: Exception) {
            Log.e(TAG, "Face detection error: ${e.message}", e)
            return _lastResult
        }
    }

    /**
     * Compute mouth asymmetry by comparing distances from each mouth corner
     * to the nose center (midline reference).
     */
    private fun computeMouthAsymmetry(landmarks: List<NormalizedLandmark>): Float {
        if (landmarks.size < 468) return 0f

        val noseCenter = landmarks[NOSE_TIP]

        // Distance from left mouth corner to nose
        val leftCorner = landmarks[MOUTH_CORNER_LEFT]
        val leftDist = euclidean2D(leftCorner, noseCenter)

        // Distance from right mouth corner to nose
        val rightCorner = landmarks[MOUTH_CORNER_RIGHT]
        val rightDist = euclidean2D(rightCorner, noseCenter)

        // Also check vertical displacement of mouth corners
        val leftCornerY = leftCorner.y()
        val rightCornerY = rightCorner.y()
        val verticalDiff = abs(leftCornerY - rightCornerY)

        // Also check upper lip symmetry
        val upperLipLeft = landmarks[UPPER_LIP_LEFT]
        val upperLipRight = landmarks[UPPER_LIP_RIGHT]
        val lipLeftDist = euclidean2D(upperLipLeft, noseCenter)
        val lipRightDist = euclidean2D(upperLipRight, noseCenter)
        val lipAsymmetry = if (lipLeftDist + lipRightDist > 0f)
            abs(lipLeftDist - lipRightDist) / ((lipLeftDist + lipRightDist) / 2f) else 0f

        // Compute normalized asymmetry
        val cornerAsymmetry = if (leftDist + rightDist > 0f)
            abs(leftDist - rightDist) / ((leftDist + rightDist) / 2f) else 0f

        // Weight both corner and lip asymmetry, plus vertical difference
        return (cornerAsymmetry * 0.4f + lipAsymmetry * 0.3f + verticalDiff * 3f * 0.3f)
            .coerceIn(0f, 1f)
    }

    /**
     * Compute eye droop by comparing the Eye Aspect Ratio (EAR) of each eye.
     * EAR = vertical_distance / horizontal_distance
     * Significant difference between left and right EAR indicates droop.
     */
    private fun computeEyeDroop(landmarks: List<NormalizedLandmark>): Float {
        if (landmarks.size < 468) return 0f

        // Left eye aspect ratio
        val leftVertical = euclidean2D(landmarks[EYE_TOP_LEFT], landmarks[EYE_BOTTOM_LEFT])
        val leftHorizontal = euclidean2D(landmarks[EYE_INNER_LEFT], landmarks[EYE_OUTER_LEFT])
        val leftEAR = if (leftHorizontal > 0f) leftVertical / leftHorizontal else 0f

        // Right eye aspect ratio
        val rightVertical = euclidean2D(landmarks[EYE_TOP_RIGHT], landmarks[EYE_BOTTOM_RIGHT])
        val rightHorizontal = euclidean2D(landmarks[EYE_INNER_RIGHT], landmarks[EYE_OUTER_RIGHT])
        val rightEAR = if (rightHorizontal > 0f) rightVertical / rightHorizontal else 0f

        // Also check eyebrow asymmetry (drooped eyebrow often accompanies)
        val leftBrowHeight = abs(landmarks[EYEBROW_OUTER_LEFT].y() - landmarks[EYE_OUTER_LEFT].y())
        val rightBrowHeight = abs(landmarks[EYEBROW_OUTER_RIGHT].y() - landmarks[EYE_OUTER_RIGHT].y())
        val browAsymmetry = if (leftBrowHeight + rightBrowHeight > 0f)
            abs(leftBrowHeight - rightBrowHeight) / ((leftBrowHeight + rightBrowHeight) / 2f) else 0f

        // EAR asymmetry
        val earAsymmetry = if (leftEAR + rightEAR > 0f)
            abs(leftEAR - rightEAR) / ((leftEAR + rightEAR) / 2f) else 0f

        return (earAsymmetry * 0.6f + browAsymmetry * 0.4f).coerceIn(0f, 1f)
    }

    /**
     * Compute overall facial droop by comparing jawline landmark distances
     * from the nose/midline reference on each side.
     */
    private fun computeFacialDroop(landmarks: List<NormalizedLandmark>): Float {
        if (landmarks.size < 468) return 0f

        val noseCenter = landmarks[NOSE_TIP]
        var totalAsymmetry = 0f
        var count = 0

        for (i in JAW_LEFT.indices) {
            if (i >= JAW_RIGHT.size) break
            val leftIdx = JAW_LEFT[i]
            val rightIdx = JAW_RIGHT[i]

            if (leftIdx < landmarks.size && rightIdx < landmarks.size) {
                val leftDist = euclidean2D(landmarks[leftIdx], noseCenter)
                val rightDist = euclidean2D(landmarks[rightIdx], noseCenter)

                if (leftDist + rightDist > 0f) {
                    totalAsymmetry += abs(leftDist - rightDist) / ((leftDist + rightDist) / 2f)
                    count++
                }
            }
        }

        return if (count > 0) (totalAsymmetry / count).coerceIn(0f, 1f) else 0f
    }

    /**
     * Compute overall stroke probability from individual asymmetry scores.
     * Uses weighted combination with non-linear scaling.
     */
    private fun computeStrokeProbability(
        mouthAsymmetry: Float,
        eyeDroop: Float,
        facialDroop: Float
    ): Float {
        // Normalize each score relative to its threshold
        val mouthScore = (mouthAsymmetry / MOUTH_ASYMMETRY_THRESHOLD).coerceIn(0f, 2f) / 2f
        val eyeScore = (eyeDroop / EYE_DROOP_THRESHOLD).coerceIn(0f, 2f) / 2f
        val droopScore = (facialDroop / FACIAL_DROOP_THRESHOLD).coerceIn(0f, 2f) / 2f

        // Weighted combination: mouth droop is the strongest stroke indicator
        val weightedScore = mouthScore * 0.45f + eyeScore * 0.35f + droopScore * 0.20f

        return weightedScore.coerceIn(0f, 1f)
    }

    // --- Utility functions ---

    private fun euclidean2D(a: NormalizedLandmark, b: NormalizedLandmark): Float {
        val dx = a.x() - b.x()
        val dy = a.y() - b.y()
        return sqrt(dx * dx + dy * dy)
    }

    private fun Float.format(decimals: Int = 3): String = "%.${decimals}f".format(this)

    fun reset() {
        _lastLandmarks = null
        _lastResult = null
        frameCount = 0
    }

    fun close() {
        faceLandmarker?.close()
        faceLandmarker = null
    }
}

/**
 * Result of face analysis for stroke detection.
 */
data class FaceAnalysisResult(
    val faceDetected: Boolean = false,
    val landmarks: List<NormalizedLandmark>? = null,
    val mouthAsymmetry: Float = 0f,      // 0.0 = symmetric, 1.0 = max asymmetry
    val eyeDroop: Float = 0f,             // 0.0 = symmetric, 1.0 = max droop
    val facialDroop: Float = 0f,          // 0.0 = symmetric, 1.0 = max droop
    val mouthSymmetryPercent: Float = 100f,  // 100% = perfect symmetry
    val eyeSymmetryPercent: Float = 100f,    // 100% = perfect symmetry
    val strokeProbability: Float = 0f,    // 0.0 - 1.0
    val isStroke: Boolean = false,
    val confidence: Float = 0f            // 0 - 100%
)
