package com.webviewer.app.data.repository

import com.webviewer.app.data.db.ProjectDao
import com.webviewer.app.model.Project
import kotlinx.coroutines.flow.Flow

class ProjectRepository(private val dao: ProjectDao) {

    val allProjects: Flow<List<Project>> = dao.getAllProjects()

    suspend fun getActiveProject(): Project? = dao.getActiveProject()

    suspend fun insert(project: Project): Long = dao.insertProject(project)

    suspend fun update(project: Project) = dao.updateProject(project)

    suspend fun delete(project: Project) = dao.deleteProject(project)

    suspend fun duplicate(project: Project) {
        dao.insertProject(
            project.copy(
                id = 0,
                name = "${project.name} (copia)",
                isActive = false,
                createdAt = System.currentTimeMillis(),
                lastUsed = System.currentTimeMillis()
            )
        )
    }

    suspend fun setActive(projectId: Long) = dao.setActiveProject(projectId)

    suspend fun updateLastUsed(projectId: Long) =
        dao.updateLastUsed(projectId, System.currentTimeMillis())

    suspend fun deleteAll() = dao.deleteAll()
}
