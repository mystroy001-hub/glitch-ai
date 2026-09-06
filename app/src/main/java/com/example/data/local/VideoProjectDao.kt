package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoProjectDao {
    @Query("SELECT * FROM video_projects ORDER BY lastEdited DESC")
    fun getAllProjectsFlow(): Flow<List<VideoProjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: VideoProjectEntity)

    @Query("DELETE FROM video_projects WHERE id = :id")
    suspend fun deleteProjectById(id: String)

    @Query("SELECT * FROM video_projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: String): VideoProjectEntity?
}
