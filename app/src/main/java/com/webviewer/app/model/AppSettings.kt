package com.webviewer.app.model

/**
 * Preferenze globali app — salvate su DataStore (non Room).
 * Separare settings da entità relazionali è best practice MVVM.
 */
data class AppSettings(
    val macroDroidPackage: String = "com.arlosoft.macrodroid",
    val macroDroidMacroName: String = "WebViewerBolla",
    val lastSessionUrl: String = "",
    val activeProjectId: Long = -1L
)
