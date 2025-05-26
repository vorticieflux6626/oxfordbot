package com.example.oxfordbot

import android.os.Build
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.oxfordbot.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreenWithLazyLoading(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onClose: () -> Unit,
    textToSpeech: TextToSpeech?,
    isTtsInitialized: Boolean,
    availableLanguages: List<TtsLanguageInfo>,
    availableVoices: List<TtsVoiceInfo>,
    onLoadLanguages: () -> Unit,
    onLoadVoices: () -> Unit,
    onApplyVoiceSettings: (String) -> Unit,
    onTestSpeech: () -> Unit,
    onClearChatHistory: () -> Unit,
    onRefreshTtsData: () -> Unit
) {
    val scrollState = rememberLazyListState()
    var isLoadingTtsData by remember { mutableStateOf(true) }
    var ttsLoadingMessage by remember { mutableStateOf("Loading TTS data...") }
    val context = LocalContext.current

    // Non-blocking TTS data loading
    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    ttsLoadingMessage = "Loading languages..."
                }

                delay(50) // Let UI update

                withContext(Dispatchers.Main) {
                    onLoadLanguages()
                }

                withContext(Dispatchers.Main) {
                    ttsLoadingMessage = "Loading voices..."
                }

                delay(50) // Let UI update

                withContext(Dispatchers.Main) {
                    onLoadVoices()
                }

                delay(100) // Small final delay

                withContext(Dispatchers.Main) {
                    isLoadingTtsData = false
                }

            } catch (e: Exception) {
                LogManager.logCaughtException("SettingsScreen", "Error in lazy TTS loading", e)
                withContext(Dispatchers.Main) {
                    isLoadingTtsData = false
                }
            }
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
                text = "Settings",
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

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            state = scrollState
        ) {
            // Model Selection Section
            item {
                SettingsSection(title = "AI Model") {
                    ModelSelectionCard(
                        selectedModel = settings.selectedModel,
                        onModelSelected = { model ->
                            onSettingsChange(settings.copy(selectedModel = model))
                        }
                    )
                }
            }

            // API Settings Section
            item {
                SettingsSection(title = "API Settings") {
                    TimeoutSettingCard(
                        timeout = settings.apiTimeout,
                        onTimeoutChanged = { timeout ->
                            onSettingsChange(settings.copy(apiTimeout = timeout))
                        }
                    )
                }
            }

            // App Behavior Section
            item {
                SettingsSection(title = "App Behavior") {
                    Column {
                        SwitchSettingCard(
                            title = "Show Thinking",
                            description = "Display AI reasoning process in responses",
                            isEnabled = settings.showThinking,
                            onToggle = { enabled ->
                                onSettingsChange(settings.copy(showThinking = enabled))
                            }
                        )

                        SwitchSettingCard(
                            title = "Enable Text-to-Speech",
                            description = "Enable voice output for AI responses",
                            isEnabled = settings.enableTTS,
                            onToggle = { enabled ->
                                onSettingsChange(settings.copy(enableTTS = enabled))
                            }
                        )

                        if (settings.enableTTS) {
                            Spacer(modifier = Modifier.height(8.dp))

                            if (isLoadingTtsData) {
                                // Show loading indicator
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    backgroundColor = Color.Black.copy(alpha = 0.3f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = Gold,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = ttsLoadingMessage,
                                            color = Color.White,
                                            style = MaterialTheme.typography.body2
                                        )
                                    }
                                }
                            } else {
                                // Speech Rate
                                SliderSettingCard(
                                    title = "Speech Rate",
                                    description = "How fast the speech is (0.5 = slow, 2.0 = fast)",
                                    value = settings.ttsSpeechRate,
                                    range = 0.5f..2.0f,
                                    steps = 29, // 0.05 increments
                                    onValueChanged = { rate ->
                                        onSettingsChange(settings.copy(ttsSpeechRate = rate))
                                        // Apply immediately for preview
                                        textToSpeech?.setSpeechRate(rate)
                                    }
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Pitch
                                SliderSettingCard(
                                    title = "Pitch",
                                    description = "Voice tone (0.5 = low, 2.0 = high)",
                                    value = settings.ttsPitch,
                                    range = 0.5f..2.0f,
                                    steps = 29, // 0.05 increments
                                    onValueChanged = { pitch ->
                                        onSettingsChange(settings.copy(ttsPitch = pitch))
                                        // Apply immediately for preview
                                        textToSpeech?.setPitch(pitch)
                                    }
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Volume
                                SliderSettingCard(
                                    title = "TTS Volume",
                                    description = "Speech volume (0.0 = silent, 1.0 = max)",
                                    value = settings.ttsVolume,
                                    range = 0.0f..1.0f,
                                    steps = 19, // 0.05 increments
                                    onValueChanged = { volume ->
                                        onSettingsChange(settings.copy(ttsVolume = volume))
                                    }
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Language Selection
                                if (availableLanguages.isNotEmpty()) {
                                    DropdownSettingCard(
                                        title = "Language",
                                        description = "Select TTS language",
                                        selectedValue = settings.ttsLanguage,
                                        options = availableLanguages.map {
                                            formatLocaleString(it.locale) to it.displayName
                                        },
                                        onSelectionChanged = { localeString ->
                                            onSettingsChange(settings.copy(ttsLanguage = localeString))
                                            // Apply immediately
                                            val locale = parseLocaleString(localeString)
                                            textToSpeech?.setLanguage(locale)
                                            // Reload voices for new language
                                            onLoadVoices()
                                        }
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                // Voice Selection (API 21+)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                    val voiceOptions = if (availableVoices.isNotEmpty()) {
                                        listOf("" to "Default Voice") + availableVoices.map {
                                            it.name to it.displayName
                                        }
                                    } else {
                                        listOf("" to "Default Voice")
                                    }

                                    DropdownSettingCard(
                                        title = "Voice",
                                        description = if (availableVoices.isEmpty())
                                            "Loading voices..."
                                        else
                                            "Select specific voice (optional)",
                                        selectedValue = settings.ttsVoice,
                                        options = voiceOptions,
                                        onSelectionChanged = { voiceName ->
                                            onSettingsChange(settings.copy(ttsVoice = voiceName))
                                            // Apply voice
                                            onApplyVoiceSettings(voiceName)
                                        }
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                // Test TTS Button
                                Button(
                                    onClick = onTestSpeech,
                                    colors = ButtonDefaults.buttonColors(backgroundColor = Green),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Test Speech", color = Color.White)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        SwitchSettingCard(
                            title = "Auto-save Chat",
                            description = "Automatically save chat history",
                            isEnabled = settings.autoSaveChat,
                            onToggle = { enabled ->
                                onSettingsChange(settings.copy(autoSaveChat = enabled))
                            }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Clear Chat History Button
                        Button(
                            onClick = {
                                onClearChatHistory()
                                Toast.makeText(
                                    context,
                                    "Chat history cleared",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color.Red),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Clear Chat History", color = Color.White)
                        }

                        Text(
                            text = "This will permanently delete all chat messages",
                            color = Color.Gray,
                            style = MaterialTheme.typography.caption,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // TTS Debugging
            item {
                SettingsSection(title = "TTS Debug Info") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = Color.Black.copy(alpha = 0.3f)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "TTS Status",
                                color = Gold,
                                style = MaterialTheme.typography.body1,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = "Initialized: $isTtsInitialized",
                                color = Color.White,
                                style = MaterialTheme.typography.body2
                            )

                            Text(
                                text = "Languages: ${availableLanguages.size}",
                                color = Color.White,
                                style = MaterialTheme.typography.body2
                            )

                            Text(
                                text = "Voices: ${availableVoices.size}",
                                color = Color.White,
                                style = MaterialTheme.typography.body2
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onRefreshTtsData,
                                    colors = ButtonDefaults.buttonColors(backgroundColor = LightBlue),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Reload TTS", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            // Logging Settings Section
            item {
                SettingsSection(title = "Logging") {
                    NumberSettingCard(
                        title = "Max Log Entries",
                        description = "Maximum number of log entries to keep",
                        value = settings.maxLogEntries,
                        range = 100..1000,
                        step = 100,
                        onValueChanged = { value ->
                            onSettingsChange(settings.copy(maxLogEntries = value))
                        }
                    )
                }
            }

            // Network Settings Section
            item {
                SettingsSection(title = "Network") {
                    NumberSettingCard(
                        title = "Test Interval",
                        description = "Connectivity test interval (seconds)",
                        value = settings.connectivityTestInterval,
                        range = 10..300,
                        step = 10,
                        onValueChanged = { value ->
                            onSettingsChange(settings.copy(connectivityTestInterval = value))
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = title,
            color = Gold,
            style = MaterialTheme.typography.subtitle1,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        content()
        Divider(
            color = Color.DarkGray,
            thickness = 0.5.dp,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
fun ModelSelectionCard(
    selectedModel: String,
    onModelSelected: (String) -> Unit
) {
    val availableModels = listOf("qwen3:8b", "qwen3:32b", "deepseek-r1:32b")

    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = Color.Black.copy(alpha = 0.3f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Select AI Model",
                color = LightBlue,
                style = MaterialTheme.typography.body1,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            availableModels.forEach { model ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onModelSelected(model) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedModel == model,
                        onClick = { onModelSelected(model) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = Gold,
                            unselectedColor = Color.Gray
                        )
                    )

                    Column(
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = model,
                            color = Color.White,
                            style = MaterialTheme.typography.body2
                        )
                        Text(
                            text = getModelDescription(model),
                            color = Color.Gray,
                            style = MaterialTheme.typography.caption
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SwitchSettingCard(
    title: String,
    description: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = Color.Black.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle(!isEnabled) }
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.body1
                )
                Text(
                    text = description,
                    color = Color.Gray,
                    style = MaterialTheme.typography.caption
                )
            }

            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Gold,
                    checkedTrackColor = Gold.copy(alpha = 0.5f),
                    uncheckedThumbColor = Color.Gray,
                    uncheckedTrackColor = Color.DarkGray
                )
            )
        }
    }
}

@Composable
fun TimeoutSettingCard(
    timeout: Int,
    onTimeoutChanged: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = Color.Black.copy(alpha = 0.3f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "API Timeout",
                color = Color.White,
                style = MaterialTheme.typography.body1,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Request timeout in seconds",
                color = Color.Gray,
                style = MaterialTheme.typography.caption
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onTimeoutChanged(maxOf(60, timeout - 60)) },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color.Red)
                ) {
                    Text("-", color = Color.White)
                }

                Text(
                    text = "${timeout}s",
                    color = Gold,
                    style = MaterialTheme.typography.h6
                )

                Button(
                    onClick = { onTimeoutChanged(minOf(1200, timeout + 60)) },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Green)
                ) {
                    Text("+", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun NumberSettingCard(
    title: String,
    description: String,
    value: Int,
    range: IntRange,
    step: Int,
    onValueChanged: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = Color.Black.copy(alpha = 0.3f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.body1,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = description,
                color = Color.Gray,
                style = MaterialTheme.typography.caption
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onValueChanged(maxOf(range.first, value - step)) },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color.Red)
                ) {
                    Text("-", color = Color.White)
                }

                Text(
                    text = "$value",
                    color = Gold,
                    style = MaterialTheme.typography.h6
                )

                Button(
                    onClick = { onValueChanged(minOf(range.last, value + step)) },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Green)
                ) {
                    Text("+", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun SliderSettingCard(
    title: String,
    description: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChanged: (Float) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = Color.Black.copy(alpha = 0.3f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.body1,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = description,
                color = Color.Gray,
                style = MaterialTheme.typography.caption
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = String.format("%.2f", range.start),
                    color = Color.Gray,
                    style = MaterialTheme.typography.caption,
                    modifier = Modifier.width(32.dp)
                )

                Slider(
                    value = value,
                    onValueChange = onValueChanged,
                    valueRange = range,
                    steps = steps,
                    colors = SliderDefaults.colors(
                        thumbColor = Gold,
                        activeTrackColor = Gold,
                        inactiveTrackColor = Color.Gray
                    ),
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = String.format("%.2f", range.endInclusive),
                    color = Color.Gray,
                    style = MaterialTheme.typography.caption,
                    modifier = Modifier.width(32.dp)
                )
            }

            Text(
                text = "Current: ${String.format("%.2f", value)}",
                color = Gold,
                style = MaterialTheme.typography.body2,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun DropdownSettingCard(
    title: String,
    description: String,
    selectedValue: String,
    options: List<Pair<String, String>>, // value to display name
    onSelectionChanged: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = Color.Black.copy(alpha = 0.3f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.body1,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = description,
                color = Color.Gray,
                style = MaterialTheme.typography.caption
            )

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = { expanded = true },
                    colors = ButtonDefaults.outlinedButtonColors(
                        backgroundColor = Color.Blue,
                        contentColor = Gold
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val displayText = when {
                        options.isEmpty() -> "No options available"
                        selectedValue.isEmpty() -> "Select ${title.lowercase()}"
                        else -> options.find { it.first == selectedValue }?.second ?: "Default Voice"
                    }

                    Text(
                        text = displayText,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Start
                    )
                    Text("▼", color = Gold)
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Navy)
                ) {
                    options.forEach { (value, displayName) ->
                        DropdownMenuItem(
                            onClick = {
                                onSelectionChanged(value)
                                expanded = false
                            },
                            modifier = Modifier.background(
                                if (value == selectedValue) LightBlue.copy(alpha = 0.3f) else Color.Transparent
                            )
                        ) {
                            Text(
                                text = displayName,
                                color = if (value == selectedValue) Gold else Color.White
                            )
                        }
                    }
                }
            }

            if (selectedValue.isNotEmpty() && options.isNotEmpty()) {
                val selectedDisplay = options.find { it.first == selectedValue }?.second
                if (selectedDisplay != null) {
                    Text(
                        text = "Selected: $selectedDisplay",
                        color = Gold,
                        style = MaterialTheme.typography.caption,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

// Helper functions
private fun getChatIdForModel(model: String): String {
    return when (model) {
        "qwen3:8b" -> "d70b00f5-82e1-4070-bcf1-8cce0b9e31ec"
        "qwen3:32b" -> "afea4aff-3c80-49a1-a9cb-b94603e5c68b"
        "deepseek-r1:32b" -> "c1b40fec-2685-4e95-82c5-d591043a009d"
        else -> "d70b00f5-82e1-4070-bcf1-8cce0b9e31ec" // Default to qwen3:8b chat-id
    }
}

private fun getModelDescription(model: String): String {
    return when (model) {
        "qwen3:8b" -> "Balanced performance and speed (Chat: ${getChatIdForModel(model).take(8)}...)"
        "qwen3:32b" -> "Large Qwen model with enhanced capabilities (Chat: ${getChatIdForModel(model).take(8)}...)"
        "deepseek-r1:32b" -> "Large model with enhanced reasoning (Chat: ${getChatIdForModel(model).take(8)}...)"
        else -> "Unknown model"
    }
}

fun parseLocaleString(localeString: String): java.util.Locale {
    val parts = localeString.split("-", "_")
    return when (parts.size) {
        1 -> java.util.Locale(parts[0])
        2 -> java.util.Locale(parts[0], parts[1])
        3 -> java.util.Locale(parts[0], parts[1], parts[2])
        else -> java.util.Locale.getDefault()
    }
}

fun formatLocaleString(locale: java.util.Locale): String {
    return if (locale.country.isNotEmpty()) {
        "${locale.language}-${locale.country}"
    } else {
        locale.language
    }
}