package com.example.strokedetectionapp.utils

import android.content.Context
import com.example.strokedetectionapp.data.AppDatabase
import com.example.strokedetectionapp.data.FacialDetectionEntity
import com.example.strokedetectionapp.data.HandExerciseEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object HistorySaver {

    fun saveFacialResult(
        context: Context,
        isStroke: Boolean,
        mouthSymmetry: Float,
        eyeDroop: Float,
        imagePath: String = ""
    ) {
        val dao = AppDatabase.getDatabase(context).facialDetectionDao()
        CoroutineScope(Dispatchers.IO).launch {
            dao.insertDetection(
                FacialDetectionEntity(
                    isStroke = isStroke,
                    mouthSymmetry = mouthSymmetry,
                    eyeDroopRatio = eyeDroop,
                    mouthAsymmetryDetected = mouthSymmetry < 80,
                    eyeDroopDetected = eyeDroop < 80,
                    imagePath = imagePath
                )
            )
        }
    }

    fun saveHandExercise(
        context: Context,
        durationSeconds: Float,
        leftFists: Int,
        rightFists: Int
    ) {
        val dao = AppDatabase.getDatabase(context).handExerciseDao()
        CoroutineScope(Dispatchers.IO).launch {
            dao.insertExercise(
                HandExerciseEntity(
                    durationSeconds = durationSeconds,
                    leftFistCount = leftFists,
                    rightFistCount = rightFists
                )
            )
        }
    }
}
