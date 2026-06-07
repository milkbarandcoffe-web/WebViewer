package com.webviewer.app.util

import android.util.Patterns

object UrlValidator {
    fun isValid(url: String): Boolean {
        if (url.isBlank()) return false
        val trimmed = url.trim()
        // Accetta anche URL senza schema — aggiunge https automaticamente
        val full = if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            "https://$trimmed"
        } else trimmed
        return Patterns.WEB_URL.matcher(full).matches()
    }

    fun normalize(url: String): String {
        val trimmed = url.trim()
        return if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            "https://$trimmed"
        } else trimmed
    }
}
