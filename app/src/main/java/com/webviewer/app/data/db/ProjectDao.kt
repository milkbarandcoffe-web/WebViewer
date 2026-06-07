package com.webviewer.app.data.db

import androidx.room.*
import com.webviewer.app.model.Project
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {

    @Query("SELECT * FROM projects ORDER BY lastUsed DESC")
    fun getAllProjects(): Flow<List<Project>>

    @Query("SELECT * FROM projects WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveProject(): Project?

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProjectById(id: Long): Project?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: Project): Long

    @Update
    suspend fun updateProject(project: Project)

    @Delete
    suspend fun deleteProject(project: Project)

    /** Disattiva tutti i progetti, poi attiva solo quello selezionato */
    @Transaction
    suspend fun setActiveProject(projectId: Long) {
        deactivateAll()
        activateProject(projectId)
    }

    @Query("UPDATE projects SET isActive = 0")
    suspend fun deactivateAll()

    @Query("UPDATE projects SET isActive = 1 WHERE id = :projectId")
    suspend fun activateProject(projectId: Long)

    @Query("UPDATE projects SET lastUsed = :timestamp WHERE id = :projectId")
    suspend fun updateLastUsed(projectId: Long, timestamp: Long)

    @Query("DELETE FROM projects")
    suspend fun deleteAll()
}
