package com.example.strokedetectionapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.strokedetectionapp.data.FacialDetectionEntity
import com.example.strokedetectionapp.data.repository.HistoryRepository
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
    val resultSaved: Boolean = false
)

class FacialViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HistoryRepository(application)

    private val _uiState = MutableStateFlow(FacialUiState())
    val uiState: StateFlow<FacialUiState> = _uiState.asStateFlow()

    // Simulate analysis (for testing without camera)
    fun analyzeImage() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAnalyzing = true)

            delay(2000)

            val isStroke = (0..10).random() > 7
            val mouthSymmetry = if (isStroke) (50..75).random().toFloat() else (85..98).random().toFloat()
            val eyeDroop = if (isStroke) (40..70).random().toFloat() else (90..98).random().toFloat()

            _uiState.value = FacialUiState(
                isAnalyzing = false,
                analysisComplete = true,
                isStroke = isStroke,
                mouthSymmetry = mouthSymmetry,
                eyeDroopRatio = eyeDroop,
                mouthAsymmetryDetected = mouthSymmetry < 80,
                eyeDroopDetected = eyeDroop < 80
            )
        }
    }

    // Set result from camera analysis
    fun setAnalysisResult(isStroke: Boolean, mouthSymmetry: Float, eyeDroop: Float) {
        _uiState.value = FacialUiState(
            isAnalyzing = false,
            analysisComplete = true,
            isStroke = isStroke,
            mouthSymmetry = mouthSymmetry,
            eyeDroopRatio = eyeDroop,
            mouthAsymmetryDetected = mouthSymmetry < 80,
            eyeDroopDetected = eyeDroop < 80
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
                eyeDroopDetected = state.eyeDroopDetected
            )

            repository.saveFacialDetection(detection)
            _uiState.value = _uiState.value.copy(resultSaved = true)
        }
    }

    fun reset() {
        _uiState.value = FacialUiState()
    }
}
