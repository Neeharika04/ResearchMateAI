package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "decisions")
data class Decision(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val studentName: String,
    val facultyName: String,
    val projectName: String,
    val type: String, // "GUIDE_SELECTION" or "COLLABORATION_PROPOSAL"
    val status: String, // "CONFIRMED", "DRAFT"
    val timestamp: Long = System.currentTimeMillis()
)
