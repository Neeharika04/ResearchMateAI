package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FacultyDao {
    @Query("SELECT * FROM faculty_profiles ORDER BY name ASC")
    fun getAllFaculty(): Flow<List<Faculty>>

    @Query("SELECT * FROM faculty_profiles ORDER BY name ASC")
    suspend fun getAllFacultyList(): List<Faculty>

    @Query("SELECT * FROM faculty_profiles WHERE name = :name LIMIT 1")
    suspend fun getFacultyByName(name: String): Faculty?

    @Query("SELECT * FROM faculty_profiles WHERE id = :id LIMIT 1")
    suspend fun getFacultyById(id: Int): Faculty?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFaculty(faculty: Faculty)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllFaculty(faculties: List<Faculty>)

    @Query("UPDATE faculty_profiles SET currentProjects = :load WHERE id = :id")
    suspend fun updateFacultyLoad(id: Int, load: Int)

    @Query("SELECT * FROM decisions ORDER BY timestamp DESC")
    fun getAllDecisions(): Flow<List<Decision>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDecision(decision: Decision)

    @Query("DELETE FROM decisions")
    suspend fun clearAllDecisions()
}
