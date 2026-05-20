package dev.stremiobridge

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persists intent dumps to app-private storage.
 * Useful for comparing multiple Stremio launch intents across sessions.
 */
object IntentLogger {

    private const val TAG = "StremioBridge"
    private const val LOG_DIR = "intent_logs"
    private const val MAX_LOGS = 50

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)

    /**
     * Log an intent to Logcat AND persist to file.
     */
    fun log(context: Context, data: IntentData) {
        // Logcat — visible in adb logcat | grep StremioBridge
        Log.i(TAG, "══════════════════════════════════════")
        Log.i(TAG, "INTENT RECEIVED FROM STREMIO")
        Log.i(TAG, "══════════════════════════════════════")
        Log.i(TAG, "URI: ${data.uri}")
        Log.i(TAG, "MIME: ${data.mimeType}")
        Log.i(TAG, "Title: ${data.title}")
        Log.i(TAG, "Position: ${data.position}ms")
        Log.i(TAG, "Subtitle URL: ${data.subtitleUrl}")
        Log.i(TAG, "Headers: ${data.headers}")
        Log.i(TAG, "IMDB: ${data.imdbId}")
        Log.i(TAG, "Season: ${data.season}, Episode: ${data.episode}")
        Log.i(TAG, "All extras (${data.rawExtras.size}):")
        data.rawExtras.forEach { (k, v) -> Log.i(TAG, "  $k = $v") }
        Log.i(TAG, "══════════════════════════════════════")

        // Persist to file
        try {
            val logDir = File(context.filesDir, LOG_DIR)
            logDir.mkdirs()

            // Clean up old logs
            val existing = logDir.listFiles()?.sortedBy { it.lastModified() } ?: emptyList()
            if (existing.size >= MAX_LOGS) {
                existing.take(existing.size - MAX_LOGS + 1).forEach { it.delete() }
            }

            val timestamp = dateFormat.format(Date())
            val logFile = File(logDir, "intent_$timestamp.json")
            logFile.writeText(gson.toJson(data.rawExtras))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write intent log", e)
        }
    }

    /**
     * Get list of saved log files, most recent first.
     */
    fun getLogs(context: Context): List<File> {
        val logDir = File(context.filesDir, LOG_DIR)
        return logDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    /**
     * Read a log file content as string.
     */
    fun readLog(file: File): String {
        return try { file.readText() } catch (_: Exception) { "Error reading log" }
    }

    /**
     * Clear all saved logs.
     */
    fun clearLogs(context: Context) {
        val logDir = File(context.filesDir, LOG_DIR)
        logDir.listFiles()?.forEach { it.delete() }
    }
}
