
package com.example.oxfordbot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.oxfordbot.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun NetworkDetailsScreen(
    host: String,
    connectivityResults: List<ConnectivityResult>,
    isTestRunning: Boolean,
    onClose: () -> Unit,
    onRunTest: () -> Unit
) {
    val scrollState = rememberLazyListState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Navy)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Network Connectivity",
                    color = Gold,
                    style = MaterialTheme.typography.h6
                )
                Text(
                    text = "Target: $host:5555",
                    color = LightBlue,
                    style = MaterialTheme.typography.caption
                )
            }

            Row {
                // Test button
                Button(
                    onClick = onRunTest,
                    enabled = !isTestRunning,
                    colors = ButtonDefaults.buttonColors(backgroundColor = Green),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    if (isTestRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Test", color = Color.White)
                    }
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

        // Current Status Summary
        if (connectivityResults.isNotEmpty()) {
            val latestResult = connectivityResults.first()

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                backgroundColor = if (latestResult.isHostReachable && latestResult.isPortOpen)
                    Color.Green.copy(alpha = 0.1f) else Color.Red.copy(alpha = 0.1f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Current Status",
                        color = Gold,
                        style = MaterialTheme.typography.subtitle1,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatusIndicator("Host Reachable", latestResult.isHostReachable)
                        StatusIndicator("Port Open", latestResult.isPortOpen)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Response Time: ${latestResult.responseTime}ms",
                        color = LightBlue,
                        style = MaterialTheme.typography.body2
                    )

                    Text(
                        text = "Last Tested: ${formatTimestamp(latestResult.timestamp)}",
                        color = Color.Gray,
                        style = MaterialTheme.typography.caption
                    )

                    if (latestResult.errorMessage != null) {
                        Text(
                            text = "Error: ${latestResult.errorMessage}",
                            color = Color.Red,
                            style = MaterialTheme.typography.caption,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        // Test History
        Text(
            text = "Test History (${connectivityResults.size})",
            color = Gold,
            style = MaterialTheme.typography.subtitle1,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Divider(color = LightBlue, thickness = 0.5.dp)

        // Results list
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            state = scrollState
        ) {
            items(connectivityResults) { result ->
                ConnectivityResultItem(result)
            }

            if (connectivityResults.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No test results yet.\nClick 'Test' to run connectivity check.",
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Statistics if we have results
        if (connectivityResults.isNotEmpty()) {
            Divider(color = LightBlue, thickness = 0.5.dp)

            ConnectivityStats(connectivityResults)
        }
    }
}

@Composable
fun StatusIndicator(label: String, status: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = if (status) Icons.Default.CheckCircle else Icons.Default.Close,
            contentDescription = null,
            tint = if (status) Color.Green else Color.Red,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            color = if (status) Color.Green else Color.Red,
            style = MaterialTheme.typography.caption,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ConnectivityResultItem(result: ConnectivityResult) {
    val overallStatus = result.isHostReachable && result.isPortOpen

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (overallStatus) Color.Green.copy(alpha = 0.05f)
                else Color.Red.copy(alpha = 0.05f)
            )
            .padding(vertical = 8.dp, horizontal = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatTimestamp(result.timestamp),
                color = Color.Gray,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )

            Text(
                text = "${result.responseTime}ms",
                color = when {
                    result.responseTime < 100 -> Color.Green
                    result.responseTime < 500 -> Color.Yellow
                    else -> Color.Red
                },
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Host: ${if (result.isHostReachable) "✓" else "✗"}",
                color = if (result.isHostReachable) Color.Green else Color.Red,
                fontSize = 12.sp
            )

            Text(
                text = "Port ${result.portNumber}: ${if (result.isPortOpen) "✓" else "✗"}",
                color = if (result.isPortOpen) Color.Green else Color.Red,
                fontSize = 12.sp
            )
        }

        if (result.errorMessage != null) {
            Text(
                text = "Error: ${result.errorMessage}",
                color = Color.Red,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }

    Divider(
        color = Color.DarkGray,
        thickness = 0.5.dp
    )
}

@Composable
fun ConnectivityStats(results: List<ConnectivityResult>) {
    if (results.isEmpty()) return

    val successfulTests = results.count { it.isHostReachable && it.isPortOpen }
    val successRate = (successfulTests.toFloat() / results.size * 100).toInt()
    val avgResponseTime = results.map { it.responseTime }.average().toLong()
    val minResponseTime = results.minOf { it.responseTime }
    val maxResponseTime = results.maxOf { it.responseTime }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        backgroundColor = Color.Black.copy(alpha = 0.3f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "Statistics",
                color = Gold,
                style = MaterialTheme.typography.subtitle2,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem("Success Rate", "$successRate%")
                StatItem("Avg Response", "${avgResponseTime}ms")
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem("Min Response", "${minResponseTime}ms")
                StatItem("Max Response", "${maxResponseTime}ms")
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            color = LightBlue,
            style = MaterialTheme.typography.body2,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = Color.Gray,
            style = MaterialTheme.typography.caption
        )
    }
}

// Helper function to format timestamps
private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}