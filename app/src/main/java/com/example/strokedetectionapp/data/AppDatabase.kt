package com.example.strokedetectionapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// ==================== ENTITIES ====================

@Entity(tableName = "facial_detections")
data class FacialDetectionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val imagePath: String = "",
    val isStroke: Boolean,
    val mouthSymmetry: Float,
    val eyeDroopRatio: Float,
    val mouthAsymmetryDetected: Boolean,
    val eyeDroopDetected: Boolean,
    val notes: String = ""
)

@Entity(tableName = "hand_exercises")
data class HandExerciseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val durationSeconds: Float,
    val leftFistCount: Int,
    val rightFistCount: Int,
    val exerciseType: String = "fist_exercise",
    val notes: String = ""
)

// ==================== DAOs ====================

@Dao
interface FacialDetectionDao {
    @Query("SELECT * FROM facial_detections ORDER BY timestamp DESC")
    fun getAllDetections(): Flow<List<FacialDetectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDetection(detection: FacialDetectionEntity): Long

    @Delete
    suspend fun deleteDetection(detection: FacialDetectionEntity)

    @Query("SELECT COUNT(*) FROM facial_detections")
    fun getCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM facial_detections WHERE isStroke = 1")
    fun getStrokeCount(): Flow<Int>
}

@Dao
interface HandExerciseDao {
    @Query("SELECT * FROM hand_exercises ORDER BY timestamp DESC")
    fun getAllExercises(): Flow<List<HandExerciseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercise(exercise: HandExerciseEntity): Long

    @Delete
    suspend fun deleteExercise(exercise: HandExerciseEntity)

    @Query("SELECT COUNT(*) FROM hand_exercises")
    fun getCount(): Flow<Int>
}

// ==================== DATABASE ====================

@Database(
    entities = [FacialDetectionEntity::class, HandExerciseEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun facialDetectionDao(): FacialDetectionDao
    abstract fun handExerciseDao(): HandExerciseDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "stroke_detection_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
