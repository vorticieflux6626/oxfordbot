// Enhanced LogManager.kt with persistent logging
package com.example.oxfordbot

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Log entry data class
data class LogEntry(
    val timestamp: String,
    val tag: String,
    val level: LogLevel,
    val message: String,
    val isCrash: Boolean = false // New field to mark crash logs
)

// Log levels
enum class LogLevel {
    DEBUG, INFO, WARNING, ERROR, CRASH;

    fun getColor(): androidx.compose.ui.graphics.Color {
        return when (this) {
            DEBUG -> androidx.compose.ui.graphics.Color.Gray
            INFO -> androidx.compose.ui.graphics.Color.White
            WARNING -> androidx.compose.ui.graphics.Color.Yellow
            ERROR -> androidx.compose.ui.graphics.Color.Red
            CRASH -> androidx.compose.ui.graphics.Color.Magenta
        }
    }
}

// Enhanced LogManager with persistent storage
object LogManager {
    // Use mutableStateListOf to automatically trigger recomposition when modified
    private val _logs = mutableStateListOf<LogEntry>()
    val logs: List<LogEntry> get() = _logs

    // Maximum number of logs to keep in memory and storage
    private const val MAX_LOGS = 500
    private const val MAX_PERSISTENT_LOGS = 100

    // SharedPreferences for persistent storage
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var gson: Gson
    private var isInitialized = false

    // Date formatter for timestamps
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    // Initialize the LogManager with Context
    fun initialize(context: Context) {
        if (!isInitialized) {
            sharedPreferences = context.getSharedPreferences("app_logs", Context.MODE_PRIVATE)
            gson = Gson()
            loadPersistedLogs()
            setupCrashHandler()
            isInitialized = true
            i("LogManager", "LogManager initialized with persistent storage")
        }
    }

    // Load persisted logs from SharedPreferences
    private fun loadPersistedLogs() {
        try {
            val logsJson = sharedPreferences.getString("persisted_logs", null)
            if (logsJson != null) {
                val type = object : TypeToken<List<LogEntry>>() {}.type
                val persistedLogs: List<LogEntry> = gson.fromJson(logsJson, type)
                _logs.clear()
                _logs.addAll(persistedLogs)
                android.util.Log.i("LogManager", "Loaded ${persistedLogs.size} persisted logs")
            }
        } catch (e: Exception) {
            android.util.Log.e("LogManager", "Error loading persisted logs: ${e.message}")
        }
    }

    // Save logs to SharedPreferences
    private fun persistLogs() {
        try {
            // Only persist the most recent logs to avoid storage bloat
            val logsToSave = _logs.takeLast(MAX_PERSISTENT_LOGS)
            val logsJson = gson.toJson(logsToSave)
            sharedPreferences.edit()
                .putString("persisted_logs", logsJson)
                .apply()
        } catch (e: Exception) {
            android.util.Log.e("LogManager", "Error persisting logs: ${e.message}")
        }
    }

    // Setup global crash handler
    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            // Log the crash
            logCrash("CRASH", "Uncaught exception in thread ${thread.name}", exception)

            // Call the default handler to perform normal crash handling
            defaultHandler?.uncaughtException(thread, exception)
        }
    }

    // Add a debug log entry
    fun d(tag: String, message: String) {
        addLog(tag, LogLevel.DEBUG, message)
        android.util.Log.d(tag, message)
    }

    // Add an info log entry
    fun i(tag: String, message: String) {
        addLog(tag, LogLevel.INFO, message)
        android.util.Log.i(tag, message)
    }

    // Add a warning log entry
    fun w(tag: String, message: String) {
        addLog(tag, LogLevel.WARNING, message)
        android.util.Log.w(tag, message)
    }

    // Add an error log entry
    fun e(tag: String, message: String) {
        addLog(tag, LogLevel.ERROR, message)
        android.util.Log.e(tag, message)
    }

    // Add an error log with exception
    fun e(tag: String, message: String, throwable: Throwable) {
        val fullMessage = "$message\n${android.util.Log.getStackTraceString(throwable)}"
        addLog(tag, LogLevel.ERROR, fullMessage)
        android.util.Log.e(tag, message, throwable)
    }

    // Add a crash log entry
    fun logCrash(tag: String, message: String, throwable: Throwable) {
        val fullMessage = "$message\n${android.util.Log.getStackTraceString(throwable)}"
        addLog(tag, LogLevel.CRASH, fullMessage, isCrash = true)
        android.util.Log.e(tag, message, throwable)

        // Immediately persist crash logs
        persistLogs()
    }

    // Log caught exceptions that might lead to crashes
    fun logCaughtException(tag: String, message: String, throwable: Throwable) {
        val fullMessage = "CAUGHT EXCEPTION: $message\n${android.util.Log.getStackTraceString(throwable)}"
        addLog(tag, LogLevel.ERROR, fullMessage)
        android.util.Log.e(tag, message, throwable)

        // Persist immediately for important exceptions
        persistLogs()
    }

    // Clear all logs
    fun clear() {
        _logs.clear()
        // Also clear persisted logs
        sharedPreferences.edit()
            .remove("persisted_logs")
            .apply()
    }

    // Get crash logs only
    fun getCrashLogs(): List<LogEntry> {
        return _logs.filter { it.isCrash || it.level == LogLevel.CRASH }
    }

    // Check if there are any crash logs
    fun hasCrashLogs(): Boolean {
        return _logs.any { it.isCrash || it.level == LogLevel.CRASH }
    }

    // Private helper to add a log entry
    private fun addLog(tag: String, level: LogLevel, message: String, isCrash: Boolean = false) {
        val timestamp = dateFormat.format(Date())
        _logs.add(LogEntry(timestamp, tag, level, message, isCrash))

        // Trim logs if exceeding maximum size
        if (_logs.size > MAX_LOGS) {
            val itemsToRemove = _logs.size - MAX_LOGS
            for (i in 0 until itemsToRemove) {
                _logs.removeAt(0)
            }
        }

        // Persist logs periodically (every 10 logs) or immediately for crashes
        if (isCrash || level == LogLevel.CRASH || _logs.size % 10 == 0) {
            persistLogs()
        }
    }
}