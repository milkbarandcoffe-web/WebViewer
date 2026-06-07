package com.webviewer.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entità Room: rappresenta un progetto WebView salvato dall'utente.
 */
@Entity(tableName = "projects")
data class Project(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val url: String,
    val iconEmoji: String = "🌐",   // Icona emoji opzionale — no risorse esterne
    val isActive: Boolean = false,
    val lastUsed: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)
