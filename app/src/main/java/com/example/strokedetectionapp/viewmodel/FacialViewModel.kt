package com.example.strokedetectionapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.strokedetectionapp.data.FacialDetectionEntity
import com.example.strokedetectionapp.data.repository.HistoryRepository
import com.example.strokedetectionapp.utils.FaceAnalysisResult
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FacialUiState(
    val isAnalyzing: Boolean = false,
    val analysisComplete: Boolean = false,
    val isStroke: Boolean = false,
    val mouthSymmetry: Float = 0f,
    val eyeDroopRatio: Float = 0f,
    val mouthAsymmetryDetected: Boolean = false,
    val eyeDroopDetected: Boolean = false,
    val confidence: Float = 0f,
    val strokeProbability: Float = 0f,
    val resultSaved: Boolean = false,
    // Live analysis state
    val faceDetected: Boolean = false,
    val liveMouthAsymmetry: Float = 0f,
    val liveEyeDroop: Float = 0f,
    val liveFacialDroop: Float = 0f,
    val liveLandmarks: List<NormalizedLandmark>? = null
)

class FacialViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HistoryRepository(application)

    private val _uiState = MutableStateFlow(FacialUiState())
    val uiState: StateFlow<FacialUiState> = _uiState.asStateFlow()

    /**
     * Update live analysis results from camera stream.
     * Called on every processed frame.
     */
    fun updateLiveResults(result: FaceAnalysisResult?) {
        if (result == null) {
            _uiState.value = _uiState.value.copy(
                faceDetected = false,
                liveLandmarks = null
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            faceDetected = result.faceDetected,
            liveMouthAsymmetry = result.mouthAsymmetry,
            liveEyeDroop = result.eyeDroop,
            liveFacialDroop = result.facialDroop,
            liveLandmarks = result.landmarks
        )
    }

    /**
     * Capture and finalize analysis from the current ML results.
     * Called when user presses the capture button.
     */
    fun captureAnalysis(result: FaceAnalysisResult) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAnalyzing = true)

            // Brief delay for visual feedback
            delay(500)

            _uiState.value = FacialUiState(
                isAnalyzing = false,
                analysisComplete = true,
                isStroke = result.isStroke,
                mouthSymmetry = result.mouthSymmetryPercent,
                eyeDroopRatio = result.eyeSymmetryPercent,
                mouthAsymmetryDetected = result.mouthAsymmetry > 0.15f,
                eyeDroopDetected = result.eyeDroop > 0.20f,
                confidence = result.confidence,
                strokeProbability = result.strokeProbability,
                faceDetected = true
            )
        }
    }

    /**
     * Simulate analysis for testing without camera.
     * Now generates more realistic simulated values.
     */
    fun analyzeImage() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAnalyzing = true)

            delay(2000)

            val isStroke = (0..10).random() > 7
            val mouthSymmetry = if (isStroke) (50..75).random().toFloat() else (85..98).random().toFloat()
            val eyeDroop = if (isStroke) (40..70).random().toFloat() else (90..98).random().toFloat()
            val confidence = (70..95).random().toFloat()

            _uiState.value = FacialUiState(
                isAnalyzing = false,
                analysisComplete = true,
                isStroke = isStroke,
                mouthSymmetry = mouthSymmetry,
                eyeDroopRatio = eyeDroop,
                mouthAsymmetryDetected = mouthSymmetry < 80,
                eyeDroopDetected = eyeDroop < 80,
                confidence = confidence,
                strokeProbability = if (isStroke) (0.5f..0.9f).random() else (0.05f..0.3f).random(),
                faceDetected = true
            )
        }
    }

    // Set result from camera analysis (legacy compat)
    fun setAnalysisResult(isStroke: Boolean, mouthSymmetry: Float, eyeDroop: Float) {
        _uiState.value = FacialUiState(
            isAnalyzing = false,
            analysisComplete = true,
            isStroke = isStroke,
            mouthSymmetry = mouthSymmetry,
            eyeDroopRatio = eyeDroop,
            mouthAsymmetryDetected = mouthSymmetry < 80,
            eyeDroopDetected = eyeDroop < 80,
            faceDetected = true
        )
    }

    fun saveResult() {
        viewModelScope.launch {
            val state = _uiState.value

            val detection = FacialDetectionEntity(
                isStroke = state.isStroke,
                mouthSymmetry = state.mouthSymmetry,
                eyeDroopRatio = state.eyeDroopRatio,
                mouthAsymmetryDetected = state.mouthAsymmetryDetected,
                eyeDroopDetected = state.eyeDroopDetected,
                confidence = state.confidence
            )

            repository.saveFacialDetection(detection)
            _uiState.value = _uiState.value.copy(resultSaved = true)
        }
    }

    fun reset() {
        _uiState.value = FacialUiState()
    }

    // Helper extension for random float range
    private fun ClosedFloatingPointRange<Float>.random(): Float {
        return start + (Math.random() * (endInclusive - start)).toFloat()
    }
}
