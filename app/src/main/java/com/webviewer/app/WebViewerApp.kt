package com.webviewer.app

import android.app.Application
import android.webkit.WebView

class WebViewerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Abilita debug WebView in development (rimosso in release da ProGuard)
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }
}
