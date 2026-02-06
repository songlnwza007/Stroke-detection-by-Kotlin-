package com.example.storkedetectionapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.storkedetectionapp.data.HandExerciseEntity
import com.example.storkedetectionapp.data.repository.HistoryRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HandUiState(
    val isExercising: Boolean = false,
    val exerciseComplete: Boolean = false,
    val elapsedSeconds: Float = 0f,
    val leftFistCount: Int = 0,
    val rightFistCount: Int = 0,
    val resultSaved: Boolean = false
)

class HandViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HistoryRepository(application)

    private val _uiState = MutableStateFlow(HandUiState())
    val uiState: StateFlow<HandUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var exerciseStartTime: Long = 0

    fun startExercise() {
        exerciseStartTime = System.currentTimeMillis()
        _uiState.value = HandUiState(isExercising = true)

        timerJob = viewModelScope.launch {
            while (true) {
                delay(100)
                val elapsed = (System.currentTimeMillis() - exerciseStartTime) / 1000f
                _uiState.value = _uiState.value.copy(elapsedSeconds = elapsed)
            }
        }
    }

    // For manual mode
    fun incrementLeftFist() {
        if (_uiState.value.isExercising) {
            _uiState.value = _uiState.value.copy(
                leftFistCount = _uiState.value.leftFistCount + 1
            )
        }
    }

    // For manual mode
    fun incrementRightFist() {
        if (_uiState.value.isExercising) {
            _uiState.value = _uiState.value.copy(
                rightFistCount = _uiState.value.rightFistCount + 1
            )
        }
    }

    // For camera mode - update from detector
    fun updateCounts(leftFists: Int, rightFists: Int) {
        if (_uiState.value.isExercising) {
            _uiState.value = _uiState.value.copy(
                leftFistCount = leftFists,
                rightFistCount = rightFists
            )
        }
    }

    fun stopExercise() {
        timerJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isExercising = false,
            exerciseComplete = true
        )
    }

    fun saveResult() {
        viewModelScope.launch {
            val state = _uiState.value

            val exercise = HandExerciseEntity(
                durationSeconds = state.elapsedSeconds,
                leftFistCount = state.leftFistCount,
                rightFistCount = state.rightFistCount
            )

            repository.saveHandExercise(exercise)
            _uiState.value = _uiState.value.copy(resultSaved = true)
        }
    }

    fun reset() {
        timerJob?.cancel()
        _uiState.value = HandUiState()
    }
}