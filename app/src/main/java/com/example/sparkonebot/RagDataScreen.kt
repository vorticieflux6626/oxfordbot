
package com.example.oxfordbot

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.oxfordbot.ui.theme.*

@Composable
fun RagDataScreen(
    chatState: MutableState<ChatState>,
    onClose: () -> Unit
) {
    val TAG = "RagDataScreen"

    // Log RagFile details when screen is displayed
    LaunchedEffect(Unit) {
        LogManager.i(TAG, "RagDataScreen: Displaying ${chatState.value.ragFiles.size} RagFile objects")
        chatState.value.ragFiles.forEachIndexed { index, ragFile ->
            LogManager.i(TAG, "RagFile[$index]: id='${ragFile.id}', displayName='${ragFile.displayName}', " +
                    "url='${ragFile.url}', isSelected=${ragFile.isSelected}")
        }
    }

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
            Text(
                text = "RAG Data Selection",
                color = Gold,
                style = MaterialTheme.typography.h6
            )

            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(backgroundColor = LightBlue)
            ) {
                Text("Done", color = Color.White)
            }
        }

        Divider(color = LightBlue, thickness = 1.dp)

        // Select/Deselect All buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = {
                    chatState.value = chatState.value.copy(
                        ragFiles = chatState.value.ragFiles.map { it.copy(isSelected = true) }
                    )
                },
                colors = ButtonDefaults.buttonColors(backgroundColor = Green)
            ) {
                Text("Select All", color = Color.White)
            }

            Button(
                onClick = {
                    chatState.value = chatState.value.copy(
                        ragFiles = chatState.value.ragFiles.map { it.copy(isSelected = false) }
                    )
                },
                colors = ButtonDefaults.buttonColors(backgroundColor = Color.Red)
            ) {
                Text("Deselect All", color = Color.White)
            }
        }

        Divider(color = LightBlue, thickness = 1.dp)

        // File list with checkboxes
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            items(chatState.value.ragFiles) { ragFile ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clickable {
                            // Toggle the selection status
                            val updatedFiles = chatState.value.ragFiles.map {
                                if (it.id == ragFile.id) {
                                    it.copy(isSelected = !it.isSelected)
                                } else {
                                    it
                                }
                            }
                            chatState.value = chatState.value.copy(ragFiles = updatedFiles)
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = ragFile.isSelected,
                        onCheckedChange = { isChecked ->
                            val updatedFiles = chatState.value.ragFiles.map {
                                if (it.id == ragFile.id) {
                                    it.copy(isSelected = isChecked)
                                } else {
                                    it
                                }
                            }
                            chatState.value = chatState.value.copy(ragFiles = updatedFiles)
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Gold,
                            uncheckedColor = LightBlue,
                            checkmarkColor = Navy
                        )
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                    ) {
                        Text(
                            text = ragFile.displayName,
                            color = Gold
                        )
                        Text(
                            text = ragFile.id,
                            color = Color.Gray,
                            style = MaterialTheme.typography.caption
                        )
                        // Display URL only if it exists
                        if (ragFile.url.isNotEmpty()) {
                            Text(
                                text = ragFile.url,
                                color = LightBlue,
                                style = MaterialTheme.typography.caption
                            )
                        }
                    }
                }

                Divider(color = Color.DarkGray, thickness = 0.5.dp)
            }
        }

        // Status text showing selected file count
        val selectedCount = chatState.value.ragFiles.count { it.isSelected }
        Text(
            text = "$selectedCount of ${chatState.value.ragFiles.size} files selected",
            color = Gold,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}