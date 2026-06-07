package com.webviewer.app.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.webviewer.app.model.Project
import java.io.File

/**
 * Backup JSON in storage interno privato — nessun permesso richiesto.
 * File: /data/data/com.webviewer.app/files/backup.json
 */
class BackupRepository(private val context: Context) {

    private val gson = Gson()
    private val backupFile: File
        get() = File(context.filesDir, "backup.json")

    data class BackupData(
        val version: Int = 1,
        val timestamp: Long = System.currentTimeMillis(),
        val projects: List<Project>
    )

    fun export(projects: List<Project>): Result<String> = runCatching {
        val data = BackupData(projects = projects)
        val json = gson.toJson(data)
        backupFile.writeText(json)
        backupFile.absolutePath
    }

    fun import(): Result<List<Project>> = runCatching {
        if (!backupFile.exists()) error("Nessun backup trovato")
        val json = backupFile.readText()
        val type = object : TypeToken<BackupData>() {}.type
        val data: BackupData = gson.fromJson(json, type)
        // Reset ID per evitare conflitti Room — verranno riassegnati
        data.projects.map { it.copy(id = 0, isActive = false) }
    }

    fun backupExists(): Boolean = backupFile.exists()

    fun backupTimestamp(): Long {
        if (!backupFile.exists()) return 0L
        return runCatching {
            val json = backupFile.readText()
            val type = object : TypeToken<BackupData>() {}.type
            val data: BackupData = gson.fromJson(json, type)
            data.timestamp
        }.getOrDefault(backupFile.lastModified())
    }
}
