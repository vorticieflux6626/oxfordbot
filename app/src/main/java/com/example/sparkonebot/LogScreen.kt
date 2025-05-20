package com.example.oxfordbot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
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

    // Auto-scroll to bottom when new logs are added
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
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
            Text(
                text = "Application Logs",
                color = Gold,
                style = MaterialTheme.typography.h6
            )

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

        // Filter options (could be expanded later)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${logs.size} log entries",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }

        Divider(color = LightBlue, thickness = 0.5.dp)

        // Log entries
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            state = listState
        ) {
            items(logs) { logEntry ->
                LogEntryItem(logEntry)
            }
        }
    }
}

@Composable
fun LogEntryItem(logEntry: LogEntry) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Timestamp and tag
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = logEntry.timestamp,
                color = Color.Gray,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )

            Text(
                text = "[${logEntry.tag}]",
                color = Color.Gray,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        // Log message
        Text(
            text = logEntry.message,
            color = logEntry.level.getColor(),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(start = 8.dp, top = 2.dp)
        )

        Divider(
            color = Color.DarkGray,
            thickness = 0.5.dp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}