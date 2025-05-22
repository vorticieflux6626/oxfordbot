// Enhanced LogScreen.kt with crash log highlighting
package com.example.oxfordbot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.oxfordbot.ui.theme.*
import kotlinx.coroutines.launch
import com.example.oxfordbot.LogManager
import com.example.oxfordbot.LogEntry
import com.example.oxfordbot.LogLevel

@Composable
fun LogScreen(
    onClose: () -> Unit
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val logs = remember { LogManager.logs }
    var showCrashLogsOnly by remember { mutableStateOf(false) }

    // Get filtered logs based on the current filter
    val filteredLogs = if (showCrashLogsOnly) {
        LogManager.getCrashLogs()
    } else {
        logs
    }

    // Auto-scroll to bottom when new logs are added
    LaunchedEffect(filteredLogs.size) {
        if (filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Navy)
            .padding(16.dp)
    ) {
        // Header row with title and buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Application Logs",
                    color = Gold,
                    style = MaterialTheme.typography.h6
                )

                // Show crash indicator if there are crash logs
                if (LogManager.hasCrashLogs()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Crash Warning",
                            tint = Color.Red,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Crash logs detected",
                            color = Color.Red,
                            style = MaterialTheme.typography.caption,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }

            Row {
                // Clear logs button
                IconButton(
                    onClick = {
                        LogManager.clear()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear Logs",
                        tint = Color.Red
                    )
                }

                // Close button
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(backgroundColor = LightBlue)
                ) {
                    Text("Close", color = Color.White)
                }
            }
        }

        Divider(color = LightBlue, thickness = 1.dp)

        // Filter options
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${filteredLogs.size} log entries",
                color = Color.Gray,
                fontSize = 12.sp
            )

            // Filter toggle for crash logs
            if (LogManager.hasCrashLogs()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = showCrashLogsOnly,
                        onCheckedChange = { showCrashLogsOnly = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color.Red,
                            uncheckedColor = Color.Gray
                        )
                    )
                    Text(
                        text = "Show crashes only",
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }

        Divider(color = LightBlue, thickness = 0.5.dp)

        // Log entries
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            state = listState
        ) {
            items(filteredLogs) { logEntry ->
                LogEntryItem(logEntry)
            }
        }
    }
}

@Composable
fun LogEntryItem(logEntry: LogEntry) {
    val backgroundColor = if (logEntry.isCrash || logEntry.level == LogLevel.CRASH) {
        Color.Red.copy(alpha = 0.1f)
    } else {
        Color.Transparent
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(vertical = 4.dp)
    ) {
        // Timestamp and tag
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = logEntry.timestamp,
                color = Color.Gray,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Show crash icon for crash logs
                if (logEntry.isCrash || logEntry.level == LogLevel.CRASH) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Crash",
                        tint = Color.Red,
                        modifier = Modifier.size(12.dp).padding(end = 2.dp)
                    )
                }

                Text(
                    text = "[${logEntry.tag}]",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (logEntry.isCrash) FontWeight.Bold else FontWeight.Normal
                )
            }
        }

        // Log message
        Text(
            text = logEntry.message,
            color = logEntry.level.getColor(),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (logEntry.isCrash || logEntry.level == LogLevel.CRASH) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(start = 8.dp, top = 2.dp)
        )

        Divider(
            color = if (logEntry.isCrash || logEntry.level == LogLevel.CRASH) Color.Red else Color.DarkGray,
            thickness = if (logEntry.isCrash || logEntry.level == LogLevel.CRASH) 1.dp else 0.5.dp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}