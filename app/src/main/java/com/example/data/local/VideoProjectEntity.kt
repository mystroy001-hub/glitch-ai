package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "video_projects")
data class VideoProjectEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val videoPath: String,
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    val durationMs: Long = 0L,
    val lastEdited: Long = System.currentTimeMillis()
)
