package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "appium_automation_logs")
data class AppiumLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val actionName: String,
    val targetLocator: String,
    val payload: String,
    val status: String, // e.g., "SUCCESS", "FAILED", "INFO"
    val timestamp: Long = System.currentTimeMillis()
)
