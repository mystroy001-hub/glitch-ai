package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppiumLogDao {
    @Query("SELECT * FROM appium_automation_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<AppiumLogEntity>>

    @Insert
    suspend fun insertLog(log: AppiumLogEntity)

    @Query("DELETE FROM appium_automation_logs")
    suspend fun clearLogs()
}
