package com.example.storkedetectionapp.data.repository

import android.content.Context
import com.example.storkedetectionapp.data.AppDatabase
import com.example.storkedetectionapp.data.FacialDetectionEntity
import com.example.storkedetectionapp.data.HandExerciseEntity
import kotlinx.coroutines.flow.Flow

class HistoryRepository(context: Context) {

    private val database = AppDatabase.getDatabase(context)
    private val facialDao = database.facialDetectionDao()
    private val handDao = database.handExerciseDao()

    // Facial Detection Operations
    fun getAllFacialDetections(): Flow<List<FacialDetectionEntity>> =
        facialDao.getAllDetections()

    suspend fun saveFacialDetection(detection: FacialDetectionEntity): Long {
        return facialDao.insertDetection(detection)
    }

    suspend fun deleteFacialDetection(detection: FacialDetectionEntity) {
        facialDao.deleteDetection(detection)
    }

    // Hand Exercise Operations
    fun getAllHandExercises(): Flow<List<HandExerciseEntity>> =
        handDao.getAllExercises()

    suspend fun saveHandExercise(exercise: HandExerciseEntity): Long =
        handDao.insertExercise(exercise)

    suspend fun deleteHandExercise(exercise: HandExerciseEntity) =
        handDao.deleteExercise(exercise)

    // Statistics
    fun getFacialCount(): Flow<Int> = facialDao.getCount()
    fun getStrokeCount(): Flow<Int> = facialDao.getStrokeCount()
    fun getHandExerciseCount(): Flow<Int> = handDao.getCount()
}