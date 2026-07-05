package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "faculty_profiles")
data class Faculty(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val title: String,
    val researchAreas: String, // Comma separated, e.g. "NLP, LLMs, Sentiment Analysis"
    val description: String,
    val currentProjects: Int,
    val maxProjects: Int,
    val email: String,
    val office: String
)
