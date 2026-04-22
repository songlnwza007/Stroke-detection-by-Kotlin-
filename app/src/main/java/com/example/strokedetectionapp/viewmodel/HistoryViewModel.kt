package com.example.strokedetectionapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.strokedetectionapp.data.FacialDetectionEntity
import com.example.strokedetectionapp.data.HandExerciseEntity
import com.example.strokedetectionapp.data.repository.HistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RecordFilter {
    ALL, FACIAL, HAND
}

data class HistoryUiState(
    val facialDetections: List<FacialDetectionEntity> = emptyList(),
    val handExercises: List<HandExerciseEntity> = emptyList(),
    val filter: RecordFilter = RecordFilter.ALL,
    val facialCount: Int = 0,
    val handCount: Int = 0,
    val strokeCount: Int = 0
)

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HistoryRepository(application)

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            combine(
                repository.getAllFacialDetections(),
                repository.getAllHandExercises(),
                repository.getFacialCount(),
                repository.getHandExerciseCount(),
                repository.getStrokeCount()
            ) { facial, hand, facialCount, handCount, strokeCount ->
                HistoryUiState(
                    facialDetections = facial,
                    handExercises = hand,
                    filter = _uiState.value.filter,
                    facialCount = facialCount,
                    handCount = handCount,
                    strokeCount = strokeCount
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun setFilter(filter: RecordFilter) {
        _uiState.update { it.copy(filter = filter) }
    }

    fun deleteFacialDetection(detection: FacialDetectionEntity) {
        viewModelScope.launch {
            repository.deleteFacialDetection(detection)
        }
    }

    fun deleteHandExercise(exercise: HandExerciseEntity) {
        viewModelScope.launch {
            repository.deleteHandExercise(exercise)
        }
    }
}
