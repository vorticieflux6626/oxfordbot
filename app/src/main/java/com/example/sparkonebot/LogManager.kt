package com.example.oxfordbot

import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Log entry data class
data class LogEntry(
    val timestamp: String,
    val tag: String,
    val level: LogLevel,
    val message: String
)

// Log levels
enum class LogLevel {
    DEBUG, INFO, WARNING, ERROR;

    fun getColor(): androidx.compose.ui.graphics.Color {
        return when (this) {
            DEBUG -> androidx.compose.ui.graphics.Color.Gray
            INFO -> androidx.compose.ui.graphics.Color.White
            WARNING -> androidx.compose.ui.graphics.Color.Yellow
            ERROR -> androidx.compose.ui.graphics.Color.Red
        }
    }
}

// Singleton object for managing logs
object LogManager {
    // Use mutableStateListOf to automatically trigger recomposition when modified
    private val _logs = mutableStateListOf<LogEntry>()
    val logs: List<LogEntry> get() = _logs

    // Maximum number of logs to keep in memory
    private const val MAX_LOGS = 500

    // Date formatter for timestamps
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    // Add a debug log entry
    fun d(tag: String, message: String) {
        addLog(tag, LogLevel.DEBUG, message)
        // Still log to Android system for debugging in logcat
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

    // Clear all logs
    fun clear() {
        _logs.clear()
    }

    // Private helper to add a log entry
    private fun addLog(tag: String, level: LogLevel, message: String) {
        val timestamp = dateFormat.format(Date())
        _logs.add(LogEntry(timestamp, tag, level, message))

        // Trim logs if exceeding maximum size
        if (_logs.size > MAX_LOGS) {
            val itemsToRemove = _logs.size - MAX_LOGS
            for (i in 0 until itemsToRemove) {
                _logs.removeAt(0)
            }
        }
    }
}