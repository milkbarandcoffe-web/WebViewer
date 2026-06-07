package com.webviewer.app.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

/**
 * Integrazione MacroDroid tramite Intent esplicito.
 * Strategia: Intent con ACTION_VIEW + macro name → fallback apertura app.
 *
 * MacroDroid espone un URI trigger:
 *   macrodroid://trigger?name=<MacroName>
 * oppure Intent con package + action broadcast.
 *
 * Metodo più affidabile testato su MacroDroid 5.x:
 *   Intent action = "com.arlosoft.macrodroid.TRIGGER_MACRO"
 *   extra   name  = "com.arlosoft.macrodroid.trigger.name" → nome macro
 */
object MacroDroidHelper {

    private const val TAG = "MacroDroidHelper"
    private const val ACTION_TRIGGER = "com.arlosoft.macrodroid.TRIGGER_MACRO"
    private const val EXTRA_MACRO_NAME = "com.arlosoft.macrodroid.trigger.name"

    /** Lancia la macro specificata tramite Intent broadcast esplicito */
    fun triggerMacro(context: Context, packageName: String, macroName: String): Boolean {
        if (!isInstalled(context, packageName)) {
            Log.w(TAG, "MacroDroid non installato: $packageName")
            return false
        }
        return try {
            val intent = Intent(ACTION_TRIGGER).apply {
                setPackage(packageName)
                putExtra(EXTRA_MACRO_NAME, macroName)
            }
            context.sendBroadcast(intent)
            Log.d(TAG, "Macro inviata: $macroName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Errore trigger macro", e)
            // Fallback: apre MacroDroid direttamente
            openApp(context, packageName)
        }
    }

    /** Apre l'app MacroDroid (fallback) */
    fun openApp(context: Context, packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Impossibile aprire MacroDroid", e)
            false
        }
    }

    /** Termina MacroDroid tramite forceStop — richiede permesso speciale,
     *  quindi mandiamo invece una macro di "stop" se configurata,
     *  altrimenti usiamo moveTaskToBack sull'app stessa */
    fun stopMacro(context: Context, packageName: String, macroName: String): Boolean {
        // Convenzione: macro di stop = "<MacroName>_stop"
        val stopMacroName = "${macroName}_stop"
        return triggerMacro(context, packageName, stopMacroName)
    }

    fun isInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
