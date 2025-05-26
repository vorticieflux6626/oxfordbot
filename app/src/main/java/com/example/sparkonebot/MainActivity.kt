package com.example.oxfordbot

// Section to import various elements of project
import com.example.oxfordbot.ui.theme.*

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.Context
import android.content.res.Configuration
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.material.Badge
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.CoroutineExceptionHandler
import java.io.IOException
import java.io.Serializable
import java.net.InetAddress
import java.net.Socket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.net.URL
import java.util.*
import java.util.Date
import java.util.Locale
import java.io.StringReader
import java.io.File
import java.text.SimpleDateFormat
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.stream.JsonReader
import com.google.gson.reflect.TypeToken

// Global Variables
val MyAppIcons = Icons.Rounded
val hostReachable = mutableStateOf(false)
// This is the router address when the Local Development Network is accessed remotely
const val SparkOneBrain: String = "24.26.41.112"

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"
    private val apiService = ApiService.create()
    private val coroutineScope = MainScope()
    private val chatState = mutableStateOf(ChatState())
    private var textToSpeech: TextToSpeech? = null
    private val isIntroAnimationFinished = mutableStateOf(false)
    private lateinit var sharedPreferences: SharedPreferences
    private val gson = GsonBuilder()
        .serializeNulls() // Include null fields
        .create()
    // For network connectivity
    private val connectivityResults = mutableStateListOf<ConnectivityResult>()
    private val showNetworkDetailsScreen = mutableStateOf(false)
    private val isConnectivityTestRunning = mutableStateOf(false)
    // Settings related
    private val appSettings = mutableStateOf(AppSettings())
    private val showSettingsScreen = mutableStateOf(false)
    // TTS management
    private val availableLanguages = mutableStateListOf<TtsLanguageInfo>()
    private val availableVoices = mutableStateListOf<TtsVoiceInfo>()
    private val isTtsInitialized = mutableStateOf(false)
    // Storage permission
    private var isPermissionRequested = false
    // ADD THIS:
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (granted) {
            LogManager.i("Permissions", "Storage permissions granted")
            Toast.makeText(this, "Storage permissions granted", Toast.LENGTH_SHORT).show()
        } else {
            LogManager.w("Permissions", "Some storage permissions denied")
        }
    }

    companion object {
        private const val SPEECH_REQUEST_CODE = 1
        private const val PREF_NAME = "OxfordBotPrefs"
        private const val KEY_CHAT_STATE = "chat_state"
        private const val KEY_INTRO_FINISHED = "intro_finished"
        private const val STORAGE_PERMISSION_CODE = 100 // To allow PDFs to be located
        //private const val MEDIA_PERMISSION_CODE = 101 // For Android 13+
        private const val KEY_APP_SETTINGS = "app_settings"

        // Define the permission constant for older SDK compatibility
        private const val READ_MEDIA_DOCUMENTS = "android.permission.READ_MEDIA_DOCUMENTS"

    }

    // Add this method to request storage permissions
    private fun requestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            val permission = Manifest.permission.READ_MEDIA_IMAGES

            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                LogManager.i(TAG, "Requesting media permission for Android 13+")
                permissionLauncher.launch(arrayOf(permission))
            } else {
                LogManager.i(TAG, "Media permission already granted")
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                LogManager.i(TAG, "Requesting READ_EXTERNAL_STORAGE permission")
                permissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
            } else {
                LogManager.i(TAG, "READ_EXTERNAL_STORAGE permission already granted")
            }
        }
    }

    // Fixed rationale dialog - only shows the system permission dialog
    private fun showPermissionRationale() {
        AlertDialog.Builder(this)
            .setTitle("Storage Permission Needed")
            .setMessage("This app needs storage permission to check if PDFs are already downloaded on your device, so it can open them directly instead of downloading again.")
            .setPositiveButton("Grant Permission") { dialog, _ ->
                dialog.dismiss()
                // Request permission directly - this will show the system dialog
                isPermissionRequested = true
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    STORAGE_PERMISSION_CODE
                )
            }
            .setNegativeButton("Skip") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, "You can enable storage permission later in Settings", Toast.LENGTH_LONG).show()
            }
            .setCancelable(false) // Prevent dismissing by tapping outside
            .show()
    }

    // Dialog for when permission is permanently denied
    private fun showPermissionPermanentlyDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("Storage permission has been permanently denied. Please enable it manually in Settings to allow PDF detection.\n\nGo to: Settings → Apps → Oxford Bot → Permissions → Storage")
            .setPositiveButton("Open Settings") { dialog, _ ->
                dialog.dismiss()
                openAppSettings()
            }
            .setNegativeButton("Skip") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, "PDF detection will not work without storage permission", Toast.LENGTH_LONG).show()
            }
            .setCancelable(false)
            .show()
    }

    // Helper method to open app settings
    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (e: Exception) {
            LogManager.logCaughtException(TAG, "Error opening app settings", e)
            Toast.makeText(this, "Please manually enable storage permission in Settings", Toast.LENGTH_LONG).show()
        }
    }

    // Updated storage permission check
    private fun hasStoragePermission(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                // For Android 13+, check READ_MEDIA_IMAGES permission
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }
            else -> true // Permissions granted at install time for older versions
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize LogManager with crash handling - ADD THIS LINE
        LogManager.initialize(this)

        // Request storage permissions
        requestStoragePermissions()

        // Log app startup
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            LogManager.i(TAG, "App started - version: ${packageInfo.versionName}")
        } catch (e: Exception) {
            LogManager.i(TAG, "App started - version: unknown")
        }

        // Initialize SharedPreferences (Keep state after home/standby)
        sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        // Restore saved state or use default
        restoreState()

        // Fix any URL mismatches
        ensureRagFilesMatch()

        // Debugging LOG call for ragFile object
        logRagFileDetails("After restore")

        // Check host reachability on startup
        coroutineScope.launch {
            hostReachable.value = pingHostAsync(SparkOneBrain)
        }

        // Enhanced TTS initialization in onCreate()
        textToSpeech = TextToSpeech(this, TextToSpeech.OnInitListener { status ->
            LogManager.i(TAG, "TTS OnInitListener called with status: $status")

            if (status == TextToSpeech.SUCCESS) {
                LogManager.i(TAG, "TTS initialization successful")

                // ADDED: Set utterance progress listener for debugging
                textToSpeech?.setOnUtteranceProgressListener(ttsUtteranceListener)

                isTtsInitialized.value = true

                coroutineScope.launch {
                    delay(500) // Wait for TTS to be fully ready
                    setupTtsQuietly()
                }
            } else {
                LogManager.e(TAG, "TTS initialization failed with status: $status")
                isTtsInitialized.value = false

                // ADDED: More detailed error reporting
                when (status) {
                    TextToSpeech.ERROR -> LogManager.e(TAG, "TTS Error: Generic error")
                    else -> LogManager.e(TAG, "TTS Error: Unknown status code $status")
                }
            }
        })

        if (savedInstanceState != null) {
            isIntroAnimationFinished.value = savedInstanceState.getBoolean("isIntroAnimationFinished", false)
        }

        setContent {
            oxfordbotTheme {
                //MainScreen(chatState, apiService, coroutineScope, isIntroAnimationFinished)
                MainScreen(chatState, apiService, isIntroAnimationFinished)
            }
        }
    }

    // 5. Create a new quiet setup function
    private suspend fun setupTtsQuietly() {
        withContext(Dispatchers.Main) {
            try {
                LogManager.i(TAG, "Setting up TTS quietly...")

                // Load languages and voices without speaking
                loadLanguagesQuickly()
                loadVoicesQuickly()

                // Apply settings without testing
                setupTtsWithSettings()

                LogManager.i(TAG, "TTS setup complete - ready for use")

            } catch (e: Exception) {
                LogManager.logCaughtException(TAG, "Error in quiet TTS setup", e)
            }
        }
    }

    // Also update your saveState method to be more robust:
    private fun saveState() {
        try {
            LogManager.d(TAG, "Starting saveState...")

            // Create a serializable copy of ChatState with only essential data
            val serializableChatState = SerializableChatState(
                messages = chatState.value.messages.map { message ->
                    SerializableMessage(
                        role = message.role,
                        content = message.content,
                        id = message.id
                    )
                },
                inputText = chatState.value.inputText,
                isAnimationVisible = false,
                ragFiles = chatState.value.ragFiles.map { ragFile ->
                    SerializableRagFile(
                        id = ragFile.id,
                        displayName = ragFile.displayName,
                        url = ragFile.url,
                        isSelected = ragFile.isSelected
                    )
                }
            )

            val chatStateJson = gson.toJson(serializableChatState)
            val settingsJson = gson.toJson(appSettings.value)

            sharedPreferences.edit()
                .putString(KEY_CHAT_STATE, chatStateJson)
                .putString(KEY_APP_SETTINGS, settingsJson) // Add settings saving
                .putBoolean(KEY_INTRO_FINISHED, isIntroAnimationFinished.value)
                .apply()

            LogManager.d(TAG, "State saved successfully to SharedPreferences")

        } catch (e: Exception) {
            LogManager.logCaughtException(TAG, "Error saving state", e)
        }
    }

    // Update your restoreState method:
    private fun restoreState() {
        try {
            LogManager.d(TAG, "Starting restoreState...")

            // Restore intro animation state
            isIntroAnimationFinished.value = sharedPreferences.getBoolean(KEY_INTRO_FINISHED, false)

            // Restore settings
            val settingsJson = sharedPreferences.getString(KEY_APP_SETTINGS, null)
            if (settingsJson != null) {
                val savedSettings = gson.fromJson(settingsJson, AppSettings::class.java)
                appSettings.value = savedSettings
                LogManager.d(TAG, "Settings restored: model=${savedSettings.selectedModel}")
            }

            // Restore Chat State
            val chatStateJson = sharedPreferences.getString(KEY_CHAT_STATE, null)
            if (chatStateJson != null) {
                val type = object : TypeToken<SerializableChatState>() {}.type
                val savedChatState = gson.fromJson<SerializableChatState>(chatStateJson, type)

                // Convert back to your ChatState
                val messages = savedChatState.messages.map { serializableMessage ->
                    Message(
                        role = serializableMessage.role,
                        content = serializableMessage.content,
                        id = serializableMessage.id
                    )
                }

                val ragFiles = savedChatState.ragFiles.map { serializableRagFile ->
                    RagFile(
                        id = serializableRagFile.id,
                        displayName = serializableRagFile.displayName,
                        url = serializableRagFile.url,
                        isSelected = serializableRagFile.isSelected
                    )
                }

                chatState.value = ChatState(
                    messages = messages,
                    inputText = savedChatState.inputText,
                    isAnimationVisible = false,
                    ragFiles = ragFiles
                )

                LogManager.d(TAG, "State restored successfully: ${messages.size} messages")
            } else {
                LogManager.d(TAG, "No saved state found, using default")
            }

        } catch (e: Exception) {
            LogManager.logCaughtException(TAG, "Error restoring state", e)
            // In case of error, use default state
            chatState.value = ChatState()
            appSettings.value = AppSettings()
        }
    }

    override fun onPause() {
        super.onPause()
        saveState()
    }

    override fun onStop() {
        super.onStop()
        saveState()
    }

    // You can override or modify the old state saving methods
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        try {
            LogManager.d(TAG, "Saving instance state...")

            // Don't save to Bundle anymore - just use SharedPreferences
            // The Bundle serialization is causing the crash
            outState.putBoolean("isIntroAnimationFinished", isIntroAnimationFinished.value)

            // Save to SharedPreferences instead (this works fine)
            saveState()

            LogManager.d(TAG, "Instance state saved successfully")

        } catch (e: Exception) {
            LogManager.logCaughtException(TAG, "Error saving instance state", e)
            // Don't let the crash propagate - just save what we can
            try {
                outState.putBoolean("isIntroAnimationFinished", isIntroAnimationFinished.value)
            } catch (e2: Exception) {
                LogManager.logCaughtException(TAG, "Even basic state saving failed", e2)
            }
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)

        try {
            LogManager.d(TAG, "Restoring instance state...")

            // Only restore simple values from Bundle
            isIntroAnimationFinished.value = savedInstanceState.getBoolean("isIntroAnimationFinished", false)

            // ChatState will be restored from SharedPreferences in onCreate()
            LogManager.d(TAG, "Instance state restored successfully")

        } catch (e: Exception) {
            LogManager.logCaughtException(TAG, "Error restoring instance state", e)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == SPEECH_REQUEST_CODE && resultCode == RESULT_OK) {
            val spokenText = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
            if (spokenText != null) {
                // Append the spoken text to the existing input text instead of replacing it
                val currentText = chatState.value.inputText
                val updatedText = if (currentText.isEmpty()) {
                    spokenText
                } else {
                    // Add a space between existing text and new spoken text if needed
                    if (currentText.endsWith(" ")) {
                        currentText + spokenText
                    } else {
                        "$currentText $spokenText"
                    }
                }
                chatState.value = chatState.value.copy(inputText = updatedText)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        coroutineScope.cancel()  // Don't forget to cancel the scope when activity is destroyed
    }

    private val lenientGson: Gson by lazy {
        GsonBuilder().setLenient().create()
    }

    // UPDATE your handleApiResponse method to include debugging:
    private fun handleApiResponse(response: ApiResponse) {
        try {
            LogManager.d(TAG, "Raw API response: ${response.response}")

            try {
                // First, check if the response contains choices with message content
                if (response.choices != null && response.choices.isNotEmpty() && response.choices[0].message?.content != null) {
                    var messageContent = response.choices[0].message!!.content!!

                    if (messageContent.isNotEmpty()) {
                        // Process the message content to add citations
                        val processedContent = processResponseWithCitations(messageContent)

                        val message = Message("assistant", processedContent)
                        chatState.value = chatState.value.copy(
                            messages = chatState.value.messages + message,
                            isAnimationVisible = false
                        )

                        // ADD THIS: Debug TTS state before speaking
                        debugTtsState("Before speaking chat response")

                        // Use raw messageContent for speech (without citations)
                        speak(messageContent.trim())
                        return
                    }
                }

                // Fall back to the old parsing method if the above doesn't work
                val jsonReader = JsonReader(StringReader(response.response))
                jsonReader.isLenient = true
                val jsonElement: JsonElement = lenientGson.fromJson(jsonReader, JsonElement::class.java)

                var messageContent = when {
                    jsonElement.isJsonObject -> {
                        val jsonObject = jsonElement.asJsonObject
                        if (jsonObject.has("response")) {
                            jsonObject.get("response").asString
                        } else {
                            "Unexpected JSON structure: $jsonObject"
                        }
                    }
                    jsonElement.isJsonPrimitive -> {
                        val jsonPrimitive = jsonElement.asJsonPrimitive
                        if (jsonPrimitive.isString) {
                            jsonPrimitive.asString
                        } else {
                            "Unexpected JSON primitive: $jsonPrimitive"
                        }
                    }
                    else -> {
                        response.response // Use the raw response if JSON parsing fails
                    }
                }

                LogManager.d(TAG, "Full processed message content: $messageContent")

                if (messageContent.isNotEmpty()) {
                    // Process the message content to add citations
                    val processedContent = processResponseWithCitations(messageContent)

                    val message = Message("assistant", processedContent)
                    chatState.value = chatState.value.copy(
                        messages = chatState.value.messages + message,
                        isAnimationVisible = false
                    )

                    // ADD THIS: Debug TTS state before speaking
                    debugTtsState("Before speaking parsed response")

                    // Use raw messageContent for speech (without citations)
                    speak(messageContent.trim())
                } else {
                    LogManager.e(TAG, "Received empty message content")
                    handleUnexpectedResponse("Empty response received")
                }

            } catch (e: Exception) {
                LogManager.e(TAG, "Error parsing response: ${e.message}")
                LogManager.e(TAG, "Stack trace: ${Log.getStackTraceString(e)}")

                // Use the raw response if JSON parsing fails
                var messageContent = response.response.trim()

                // Extract only the part after </think> if it exists
                if (messageContent.contains("</think>")) {
                    val parts = messageContent.split("</think>", limit = 2)
                    if (parts.size > 1) {
                        messageContent = parts[1].trim()
                    }
                }

                LogManager.d(TAG, "Full raw message content: $messageContent")

                // Process the message content to add citations
                val processedContent = processResponseWithCitations(messageContent)

                val message = Message("assistant", processedContent)
                chatState.value = chatState.value.copy(
                    messages = chatState.value.messages + message,
                    isAnimationVisible = false
                )

                // ADD THIS: Debug TTS state before speaking
                debugTtsState("Before speaking raw response")

                // Use raw messageContent for speech (without citations)
                speak(messageContent.trim())
            }
        } catch (e: Exception) {
            LogManager.logCaughtException(TAG, "Error processing API response", e)

            // Add fallback error message
            val message = Message("system", "Error processing server response. Check logs for details.")
            chatState.value = chatState.value.copy(
                messages = chatState.value.messages + message,
                isAnimationVisible = false
            )
        }
    }

    private fun handleUnexpectedResponse(responseString: String) {
        LogManager.w(TAG, "Unexpected response: $responseString")
        val message = Message("system", "Unexpected response: $responseString")
        chatState.value = chatState.value.copy(
            messages = chatState.value.messages + message,
            isAnimationVisible = false
        )
        speak("An unexpected response was received from the server.")
    }

    // Enhanced speak function with better reliability
    private fun speak(text: String) {
        if (!appSettings.value.enableTTS) {
            LogManager.d(TAG, "TTS is disabled in settings")
            return
        }

        if (textToSpeech == null) {
            LogManager.w(TAG, "TextToSpeech object is null")
            return
        }

        if (!isTtsInitialized.value) {
            LogManager.w(TAG, "TTS not initialized yet")
            return
        }

        // FIXED: Remove overly restrictive filter that blocks legitimate messages
        // Only skip if it's clearly a test message from setup
        if (text.startsWith("Testing voice") || text == "This is a test of the selected voice settings.") {
            LogManager.d(TAG, "Skipping setup test speech: $text")
            return
        }

        // FIXED: Run TTS on main thread to ensure proper execution
        coroutineScope.launch(Dispatchers.Main) {
            try {
                val utteranceId = UUID.randomUUID().toString()
                LogManager.d(TAG, "Speaking text: \"${text.take(50)}...\" with utterance ID: $utteranceId")

                // FIXED: Stop any existing speech and reapply settings before speaking
                textToSpeech?.stop()

                // Reapply TTS settings to ensure consistency
                textToSpeech?.let { tts ->
                    tts.setSpeechRate(appSettings.value.ttsSpeechRate)
                    tts.setPitch(appSettings.value.ttsPitch)
                    val locale = parseLocaleString(appSettings.value.ttsLanguage)
                    tts.setLanguage(locale)

                    // Apply voice if specified (API 21+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && appSettings.value.ttsVoice.isNotEmpty()) {
                        val voice = tts.voices?.find { it.name == appSettings.value.ttsVoice }
                        voice?.let { tts.setVoice(it) }
                    }
                }

                // FIXED: Use QUEUE_FLUSH instead of QUEUE_ADD for immediate speech
                // FIXED: Simplify parameters - volume bundle can cause issues on some devices
                val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    // Create simplified parameters without volume (let system handle volume)
                    val params = Bundle()
                    textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
                } else {
                    // Legacy method without volume parameter
                    textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null)
                }

                LogManager.d(TAG, "TTS speak result: $result")

                // Log success/failure
                when (result) {
                    TextToSpeech.SUCCESS -> LogManager.i(TAG, "TTS speech queued successfully")
                    TextToSpeech.ERROR -> LogManager.e(TAG, "TTS speech failed with ERROR")
                    else -> LogManager.w(TAG, "TTS speech returned unexpected result: $result")
                }

            } catch (e: Exception) {
                LogManager.logCaughtException(TAG, "Error during TTS speak", e)
            }
        }
    }

    // Troubleshooting for TTS
    // Add this to MainActivity class for better TTS monitoring
    private val ttsUtteranceListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            LogManager.d(TAG, "TTS started speaking utterance: $utteranceId")
        }

        override fun onDone(utteranceId: String?) {
            LogManager.d(TAG, "TTS finished speaking utterance: $utteranceId")
        }

        override fun onError(utteranceId: String?) {
            LogManager.e(TAG, "TTS error for utterance: $utteranceId")
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            LogManager.e(TAG, "TTS error for utterance: $utteranceId, error code: $errorCode")
        }
    }

    // Enhanced debugging function to check TTS state
    private fun debugTtsState(context: String) {
        LogManager.i(TAG, "=== TTS State Debug ($context) ===")
        LogManager.i(TAG, "TTS object exists: ${textToSpeech != null}")
        LogManager.i(TAG, "TTS initialized: ${isTtsInitialized.value}")
        LogManager.i(TAG, "TTS enabled in settings: ${appSettings.value.enableTTS}")

        textToSpeech?.let { tts ->
            try {
                LogManager.i(TAG, "TTS language: ${tts.language}")
                LogManager.i(TAG, "TTS speech rate: ${appSettings.value.ttsSpeechRate}")
                LogManager.i(TAG, "TTS pitch: ${appSettings.value.ttsPitch}")
                LogManager.i(TAG, "TTS volume setting: ${appSettings.value.ttsVolume}")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    LogManager.i(TAG, "TTS voice: ${tts.voice?.name ?: "Default"}")
                    LogManager.i(TAG, "TTS is speaking: ${tts.isSpeaking}")
                }
            } catch (e: Exception) {
                LogManager.logCaughtException(TAG, "Error checking TTS state", e)
            }
        }
        LogManager.i(TAG, "=== End TTS State Debug ===")
    }

    // Test function to verify TTS is working (call this from settings or debug menu)
    private fun testTtsWithDifferentMethods() {
        LogManager.i(TAG, "=== Testing TTS with different methods ===")

        val testText = "Testing TTS functionality"

        coroutineScope.launch(Dispatchers.Main) {
            try {
                // Method 1: Simple speak
                LogManager.i(TAG, "Method 1: Simple speak")
                textToSpeech?.speak(testText, TextToSpeech.QUEUE_FLUSH, null, "test1")

                delay(3000)

                // Method 2: With bundle parameters
                LogManager.i(TAG, "Method 2: With bundle parameters")
                val params = Bundle()
                textToSpeech?.speak(testText, TextToSpeech.QUEUE_FLUSH, params, "test2")

                delay(3000)

                // Method 3: Reset TTS and speak
                LogManager.i(TAG, "Method 3: Reset TTS and speak")
                textToSpeech?.stop()
                setupTtsWithSettings()
                delay(500)
                textToSpeech?.speak(testText, TextToSpeech.QUEUE_FLUSH, null, "test3")

            } catch (e: Exception) {
                LogManager.logCaughtException(TAG, "Error in TTS testing", e)
            }
        }
    }

    private fun pingHost(host: String): Boolean {
        return try {
            val inetAddress = InetAddress.getByName(host)
            inetAddress.isReachable(30000)
        } catch (e: IOException) {
            false
        }
    }

    // Enhanced connectivity testing functions
    private suspend fun testDetailedConnectivity(host: String, port: Int = 5555): ConnectivityResult {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()

            try {
                LogManager.d(TAG, "Testing connectivity to $host:$port")

                // Test host reachability
                val hostReachable = try {
                    val inetAddress = InetAddress.getByName(host)
                    inetAddress.isReachable(5000) // 5 second timeout
                } catch (e: Exception) {
                    LogManager.e(TAG, "Host reachability test failed: ${e.message}")
                    false
                }

                // Test port connectivity
                val portOpen = try {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(host, port), 5000)
                        true
                    }
                } catch (e: Exception) {
                    LogManager.d(TAG, "Port connectivity test failed: ${e.message}")
                    false
                }

                val responseTime = System.currentTimeMillis() - startTime

                ConnectivityResult(
                    isHostReachable = hostReachable,
                    isPortOpen = portOpen,
                    responseTime = responseTime,
                    errorMessage = null,
                    portNumber = port
                )

            } catch (e: Exception) {
                val responseTime = System.currentTimeMillis() - startTime
                LogManager.logCaughtException(TAG, "Connectivity test failed", e)

                ConnectivityResult(
                    isHostReachable = false,
                    isPortOpen = false,
                    responseTime = responseTime,
                    errorMessage = e.message,
                    portNumber = port
                )
            }
        }
    }

    private fun runConnectivityTest() {
        if (isConnectivityTestRunning.value) return

        coroutineScope.launch {
            isConnectivityTestRunning.value = true
            try {
                val result = testDetailedConnectivity(SparkOneBrain)
                connectivityResults.add(0, result) // Add to front of list

                // Keep only last 20 results
                if (connectivityResults.size > 20) {
                    connectivityResults.removeRange(20, connectivityResults.size)
                }

                // Update the main hostReachable state
                hostReachable.value = result.isHostReachable && result.isPortOpen

            } catch (e: Exception) {
                LogManager.logCaughtException(TAG, "Error running connectivity test", e)
            } finally {
                isConnectivityTestRunning.value = false
            }
        }
    }

    // Update the PingResult composable to be clickable
    @Composable
    fun PingResult(host: String) {
        val coroutineScope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            coroutineScope.launch {
                hostReachable.value = pingHostAsync(host)
            }
        }

        Text(
            text = if (hostReachable.value) "SparkOne Brain Online" else "SparkOne Brain Unreachable",
            color = if (hostReachable.value) Color.Green else Color.Red,
            modifier = Modifier
                .padding(16.dp)
                .clickable {
                    showNetworkDetailsScreen.value = true
                    runConnectivityTest() // Run a fresh test when clicked
                },
            textDecoration = TextDecoration.Underline
        )
    }

    private suspend fun pingHostAsync(host: String): Boolean {
        return withContext(Dispatchers.IO) {
            pingHost(host)
        }
    }

    @SuppressLint("UnusedMaterialScaffoldPaddingParameter")
    @Composable
    fun MainScreen(
        chatState: MutableState<ChatState>,
        apiService: ApiService,
        isIntroAnimationFinished: MutableState<Boolean>
    ) {
        val backgroundColor = if (isIntroAnimationFinished.value) Navy else Color.Black
        val configuration = LocalConfiguration.current
        val scaffoldState = rememberScaffoldState()
        val scope = rememberCoroutineScope()
        val drawerState = rememberDrawerState(DrawerValue.Closed)

        // Add state for showing RAG Data screen
        val showRagDataScreen = remember { mutableStateOf(false) }
        val showLogScreen = remember {mutableStateOf(false)} // New state for log screen

        // 6. Add crash logging to your coroutine error handler:
        val errorHandler = CoroutineExceptionHandler { _, exception ->
            LogManager.logCrash("MainActivity", "Coroutine exception occurred", exception)

            val errorMessage = Message(
                role = "system",
                content = "An error occurred: ${exception.message}\n\nThis error has been logged. Please check the log screen for details."
            )
            chatState.value = chatState.value.copy(
                messages = chatState.value.messages + errorMessage
            )
        }

        ModalDrawer(
            drawerState = drawerState,
            gesturesEnabled = scaffoldState.drawerState.isOpen,
            drawerContent = {
                DrawerContent(
                    chatState = chatState,
                    onClose = {
                        scope.launch {
                            drawerState.close()
                        }
                    },
                    onRagDataClick = {
                        showRagDataScreen.value = true
                    },
                    onLogClick = { // Add handler for log screen
                        showLogScreen.value = true
                    },
                    onNetworkClick = { // Add this block
                        showNetworkDetailsScreen.value = true
                        runConnectivityTest() // Run a fresh test when opened from menu
                    },
                    onSettingsClick = { // Add this line
                        showSettingsScreen.value = true
                    }
                )
            }
        ) {
            Scaffold(
                scaffoldState = scaffoldState,
                topBar = {
                    TopAppBar(
                        title = { Text(text = "Techno Bot") },
                        navigationIcon = {
                            IconButton(onClick = {
                                scope.launch {
                                    drawerState.open()
                                }
                            }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        },
                        actions = {
                            // Optional: Add badge to menu icon showing selected RAG count
                            val selectedCount = chatState.value.ragFiles.count { it.isSelected }
                            if (selectedCount > 0) {
                                Badge(
                                    backgroundColor = Green,
                                    contentColor = Color.White
                                ) {
                                    Text("$selectedCount")
                                }
                            }
                        }
                    )
                }
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = backgroundColor
                ) {
                    when {

                        showLogScreen.value -> {
                            LogScreen(
                                onClose = {
                                    showLogScreen.value = false
                                }
                            )
                        }

                        showNetworkDetailsScreen.value -> {
                            NetworkDetailsScreen(
                                host = SparkOneBrain,
                                connectivityResults = connectivityResults,
                                isTestRunning = isConnectivityTestRunning.value,
                                onClose = { showNetworkDetailsScreen.value = false },
                                onRunTest = { runConnectivityTest() }
                            )
                        }

                        showRagDataScreen.value -> {
                            RagDataScreen(
                                chatState = chatState,
                                onClose = {
                                    showRagDataScreen.value = false
                                }
                            )
                        }

                        showSettingsScreen.value -> {
                            SettingsScreenWithLazyLoading(
                                settings = appSettings.value,
                                onSettingsChange = { newSettings ->
                                    val oldModel = appSettings.value.selectedModel
                                    val newModel = newSettings.selectedModel

                                    appSettings.value = newSettings

                                    if (oldModel != newModel) {
                                        val oldChatId = getChatIdForModel(oldModel)
                                        val newChatId = getChatIdForModel(newModel)

                                        LogManager.i(TAG, "Model changed: $oldModel -> $newModel")
                                        LogManager.i(TAG, "Chat ID changed: $oldChatId -> $newChatId")
                                    }
                                },
                                onClose = { showSettingsScreen.value = false },
                                textToSpeech = textToSpeech,
                                isTtsInitialized = isTtsInitialized.value,
                                availableLanguages = availableLanguages,
                                availableVoices = availableVoices,
                                onLoadLanguages = { loadLanguagesQuickly() },
                                onLoadVoices = { loadVoicesQuickly() },
                                onApplyVoiceSettings = { voiceName ->
                                    coroutineScope.launch(Dispatchers.Main) {
                                        applyVoiceSettingsQuickly(voiceName)
                                    }
                                },
                                onTestSpeech = {
                                    LogManager.i(TAG, "Test Speech button clicked - USER INITIATED")
                                    coroutineScope.launch(Dispatchers.Main) {
                                        try {
                                            textToSpeech?.stop()
                                            textToSpeech?.let { tts ->
                                                tts.setSpeechRate(appSettings.value.ttsSpeechRate)
                                                tts.setPitch(appSettings.value.ttsPitch)
                                                val locale = parseLocaleString(appSettings.value.ttsLanguage)
                                                tts.setLanguage(locale)
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && appSettings.value.ttsVoice.isNotEmpty()) {
                                                    val voice = tts.voices?.find { it.name == appSettings.value.ttsVoice }
                                                    voice?.let { tts.setVoice(it) }
                                                }
                                            }
                                            val testText = "This is a test of the selected voice settings."
                                            textToSpeech?.speak(
                                                testText,
                                                TextToSpeech.QUEUE_FLUSH,
                                                null,
                                                "voice_test_${System.currentTimeMillis()}"
                                            )
                                            LogManager.i(TAG, "Test speech initiated")
                                        } catch (e: Exception) {
                                            LogManager.logCaughtException(TAG, "Error in test speech", e)
                                        }
                                    }
                                },
                                onClearChatHistory = {
                                    chatState.value = chatState.value.copy(messages = emptyList())
                                    LogManager.i(TAG, "Chat history cleared by user from settings")
                                },
                                onRefreshTtsData = {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        withContext(Dispatchers.Main) {
                                            loadLanguagesQuickly()
                                            loadVoicesQuickly()
                                        }
                                    }
                                }
                            )
                        }

                        showNetworkDetailsScreen.value -> {
                            NetworkDetailsScreen(
                                host = SparkOneBrain,
                                connectivityResults = connectivityResults,
                                isTestRunning = isConnectivityTestRunning.value,
                                onClose = { showNetworkDetailsScreen.value = false },
                                onRunTest = { runConnectivityTest() }
                            )
                        }

                        !isIntroAnimationFinished.value && configuration.orientation == Configuration.ORIENTATION_PORTRAIT -> {
                            IntroScreen(
                                onAnimationFinished = {
                                    isIntroAnimationFinished.value = true
                                }
                            )
                        }

                        else -> {
                            ChatScreen(
                                chatState = chatState,
                                onSendPrompt = { prompt ->
                                    scope.launch(errorHandler) {
                                        hostReachable.value = pingHostAsync(SparkOneBrain)
                                        if (hostReachable.value) {
                                            try {
                                                // Create a chat completion request with selected file references
                                                val apiMessage = ApiMessage("user", prompt)

                                                // Only include selected RAG files
                                                val selectedFiles = chatState.value.ragFiles
                                                    .filter { it.isSelected }
                                                    .map { FileReference(id = it.id) }

                                                // Use model-specific chat-id
                                                val selectedModel = appSettings.value.selectedModel
                                                val chatId = getChatIdForModel(selectedModel)

                                                val chatCompletionRequest = ChatCompletionRequest(
                                                    model = selectedModel,
                                                    messages = listOf(apiMessage),
                                                    chat_id = chatId, // Use model-specific chat-id
                                                    files = selectedFiles
                                                )

                                                LogManager.d(
                                                    TAG,
                                                    "Sending API request: model=$selectedModel, chat_id=$chatId"
                                                )

                                                val response = apiService.generateResponse(chatCompletionRequest)

                                                LogManager.d(
                                                    TAG,
                                                    "Received API response: $response"
                                                )

                                                handleApiResponse(response)
                                            } catch (e: Exception) {
                                                when (e) {
                                                    is SocketTimeoutException -> {
                                                        LogManager.e(
                                                            TAG,
                                                            "Socket timeout during API call: ${e.message}"
                                                        )
                                                        val errorMessage = Message(
                                                            role = "system",
                                                            content = "The server took too long to respond. Please try again later."
                                                        )
                                                        chatState.value = chatState.value.copy(
                                                            messages = chatState.value.messages + errorMessage
                                                        )
                                                    }

                                                    else -> {
                                                        LogManager.e(
                                                            TAG,
                                                            "Error during API call: ${e.message}"
                                                        )
                                                        LogManager.e(
                                                            TAG,
                                                            "Stack trace: ${
                                                                Log.getStackTraceString(e)
                                                            }"
                                                        )
                                                        val errorMessage = Message(
                                                            role = "system",
                                                            content = "An error occurred: ${e.message}\n\nPlease try again or contact support if the problem persists."
                                                        )
                                                        chatState.value = chatState.value.copy(
                                                            messages = chatState.value.messages + errorMessage
                                                        )
                                                    }
                                                }
                                            }
                                        } else {
                                            val networkErrorMessage = Message(
                                                role = "system",
                                                content = "Network Connectivity Error. Please Check your Internet Connection and Try Again."
                                            )
                                            chatState.value = chatState.value.copy(
                                                messages = chatState.value.messages + networkErrorMessage
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Now, update the DrawerContent function to include RAG Data option
    @Composable
    fun DrawerContent(
        chatState: MutableState<ChatState>,
        onClose: () -> Unit,
        onRagDataClick: () -> Unit,
        onLogClick: () -> Unit,
        onNetworkClick: () -> Unit,
        onSettingsClick: () -> Unit // Add this parameter
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .background(Navy)
                .padding(top = 32.dp)
        ) {
            // Make the app title clickable to close the drawer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClose() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Techno-Bot",
                    color = Gold,
                    style = MaterialTheme.typography.h6
                )

                Text(
                    text = "← Back",
                    color = LightBlue,
                    style = MaterialTheme.typography.caption,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Divider(color = LightBlue, thickness = 1.dp)

            // Settings menu item (replaces Models)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onSettingsClick()
                        onClose()
                    }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Settings",
                    color = Gold
                )

                // Show current model as a badge
                Box(
                    modifier = Modifier
                        .background(
                            color = LightBlue,
                            shape = CircleShape
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = appSettings.value.selectedModel,
                        color = Color.White,
                        style = MaterialTheme.typography.caption
                    )
                }
            }

            // Log menu item with log count badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onLogClick()
                        onClose()
                    }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Log",
                    color = Gold
                )

                Box(
                    modifier = Modifier
                        .background(
                            color = LightBlue,
                            shape = CircleShape
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${LogManager.logs.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.caption
                    )
                }
            }

            // RAG Data menu item with badge showing selected count
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onRagDataClick()
                        onClose()
                    }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RAG Data",
                    color = Gold
                )

                val selectedCount = chatState.value.ragFiles.count { it.isSelected }
                Box(
                    modifier = Modifier
                        .background(
                            color = if (selectedCount > 0) Green else Color.Red,
                            shape = CircleShape
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "$selectedCount",
                        color = Color.White,
                        style = MaterialTheme.typography.caption
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onNetworkClick()
                        onClose()
                    }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Network",
                    color = Gold
                )

                // Status indicator showing current connectivity
                Box(
                    modifier = Modifier
                        .background(
                            color = if (hostReachable.value) Color.Green else Color.Red,
                            shape = CircleShape
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (hostReachable.value) "Online" else "Offline",
                        color = Color.White,
                        style = MaterialTheme.typography.caption
                    )
                }
            }

        }
    }

    @Composable
    fun MenuItem(text: String, onClick: () -> Unit) {
        Text(
            text = text,
            color = Gold,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(16.dp)
        )
    }

    @Composable
    fun ChatScreen(
        chatState: MutableState<ChatState>,
        onSendPrompt: (String) -> Unit
    ) {
        val scrollState = rememberLazyListState()
        val latestUserMessageId = remember { mutableStateOf<String?>(null) }

        LaunchedEffect(chatState.value.messages.size) {
            if (chatState.value.messages.isNotEmpty()) {
                scrollState.animateScrollToItem(chatState.value.messages.size - 1)
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = scrollState,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(chatState.value.messages) { message ->
                    MessageItem(message)
                    if (message.role == "user" && message.id == latestUserMessageId.value && chatState.value.isAnimationVisible) {
                        LoadingAnimation()
                    }
                }
            }

            Row(modifier = Modifier.padding(16.dp)) {
                TextField(
                    value = chatState.value.inputText,
                    onValueChange = { chatState.value = chatState.value.copy(inputText = it) },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(color = Gold)
                )

                Button(
                    onClick = {
                        val prompt = chatState.value.inputText.trim()
                        if (prompt.isNotEmpty()) {
                            val messageId = UUID.randomUUID().toString()
                            val message = Message("user", prompt, messageId)
                            chatState.value = chatState.value.copy(
                                messages = chatState.value.messages + message,
                                inputText = "",
                                isAnimationVisible = true
                            )
                            latestUserMessageId.value = messageId
                            onSendPrompt(prompt)
                        }
                    },
                    modifier = Modifier.padding(start = 16.dp)
                ) {
                    Text("Send")
                }

                Button(
                    onClick = {
                        // Launch speech recognition
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        intent.putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                        )
                        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now")
                        startActivityForResult(intent, SPEECH_REQUEST_CODE)
                    },
                    modifier = Modifier.padding(start = 16.dp)
                ) {
                    Icon(
                        imageVector = MyAppIcons.Phone,
                        contentDescription = "Microphone"
                    )
                }
            }

            PingResult(host = SparkOneBrain)
        }
    }

    @Composable
    fun LoadingAnimation() {
        IndeterminateCircularIndicator(
            modifier = Modifier.padding(16.dp)
        )
    }

    @Composable
    fun IndeterminateCircularIndicator(
        modifier: Modifier = Modifier
    ) {
        CircularProgressIndicator(
            modifier = modifier.width(16.dp),
            color = Gold,
            //trackColor = LightBlue,
        )
    }

    // Optional: Add a visual indicator in MessageItem for thinking content
    @Composable
    fun MessageItem(message: Message) {
        Log.d("MessageItem", "Displaying message: $message")
        Column(modifier = Modifier.padding(16.dp)) {
            // Define custom selection colors
            val customSelectionColors = TextSelectionColors(
                handleColor = Color.White,
                backgroundColor = LightBlue.copy(alpha = 0.3f)
            )

            CompositionLocalProvider(
                LocalTextSelectionColors provides customSelectionColors
            ) {
                if (message.content.contains("<link:")) {
                    // Parse and render content with clickable links
                    val parts = message.content.split("\n\n---\n")
                    val mainContent = parts[0]
                    val referencesSection = if (parts.size > 1) parts[1] else ""

                    // Check if this is a thinking-formatted message
                    if (mainContent.contains("🤔 **Thinking Process:**")) {
                        // Render thinking content with special formatting
                        RenderThinkingMessage(mainContent)
                    } else {
                        // Main content
                        SelectionContainer {
                            Text(
                                text = mainContent,
                                color = Gold,
                                modifier = Modifier.fillMaxWidth(),
                                softWrap = true
                            )
                        }
                    }

                    // References with clickable links
                    if (referencesSection.isNotEmpty()) {
                        Text(
                            text = "References:",
                            color = Color.Green,
                            style = MaterialTheme.typography.subtitle1,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )

                        // Parse and display each reference line
                        val referenceLines = referencesSection.replace("**References:**\n", "").split("\n")
                        referenceLines.forEach { line ->
                            if (line.isNotEmpty()) {
                                ReferenceLink(line)
                            }
                        }
                    }
                } else {
                    // Check if this is a thinking-formatted message
                    if (message.content.contains("🤔 **Thinking Process:**")) {
                        RenderThinkingMessage(message.content)
                    } else {
                        // Regular selection container for normal messages
                        SelectionContainer {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "${message.role}: ",
                                    color = Color.Green,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .disableSelection()
                                )
                                Text(
                                    text = message.content,
                                    color = Gold,
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(Navy),
                                    softWrap = true
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Special formatting for thinking portion of response if toggled
    @Composable
    fun RenderThinkingMessage(content: String) {
        // Parse the thinking-formatted content
        val lines = content.split("\n")
        var inThinkingBlock = false
        var inCodeBlock = false

        Column {
            // Role indicator
            Text(
                text = "assistant: ",
                color = Color.Green,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            lines.forEach { line ->
                when {
                    line.contains("🤔 **Thinking Process:**") -> {
                        Text(
                            text = "🤔 Thinking Process:",
                            color = Color.Yellow,
                            style = MaterialTheme.typography.subtitle1,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        inThinkingBlock = true
                    }
                    line == "```" && inThinkingBlock && !inCodeBlock -> {
                        inCodeBlock = true
                    }
                    line == "```" && inCodeBlock -> {
                        inCodeBlock = false
                        inThinkingBlock = false
                    }
                    line.contains("**Response:**") -> {
                        Text(
                            text = "Response:",
                            color = Color.Green,
                            style = MaterialTheme.typography.subtitle1,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )
                    }
                    inCodeBlock -> {
                        SelectionContainer {
                            Text(
                                text = line,
                                color = Color.Gray,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.3f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                softWrap = true
                            )
                        }
                    }
                    line.isNotEmpty() -> {
                        SelectionContainer {
                            Text(
                                text = line,
                                color = Gold,
                                modifier = Modifier.fillMaxWidth(),
                                softWrap = true
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun ReferenceLink(line: String) {
        val context = LocalContext.current
        val TAG = "ReferenceLink"

        // Parse the link outside of the composable rendering
        val linkPattern = "<link:(.+?)>(.+?)</link>".toRegex()
        val matchResult = linkPattern.find(line)

        if (matchResult != null) {
            // Extract the URL and text safely
            val urlPart = matchResult.groups[1]?.value ?: ""
            val textPart = matchResult.groups[2]?.value ?: ""
            val prefix = if (matchResult.range.first > 0) line.substring(0, matchResult.range.first) else ""

            // 🟢 This will only log once per unique line content
            LaunchedEffect(line) {
                LogManager.d(TAG, "Found link: URL=$urlPart, Text=$textPart")
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = prefix, color = Gold)

                Text(
                    text = textPart,
                    color = LightBlue,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        // Handle URL opening with comprehensive error handling
                        try {
                            LogManager.d(TAG, "User clicked on link: $urlPart")
                            openUrlSafely(context, urlPart)
                        } catch (e: Exception) {
                            LogManager.logCaughtException(TAG, "Exception in link click handler", e)
                            Toast.makeText(
                                context,
                                "Error opening link: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }
        } else {
            // No link found, just show the text
            Text(text = line, color = Gold)
        }
    }

    // Each model in open-webui has an associated chat-id for ease of configuration
    private fun getChatIdForModel(model: String): String {
        return when (model) {
            "qwen3:8b" -> "d70b00f5-82e1-4070-bcf1-8cce0b9e31ec"
            "qwen3:32b" -> "afea4aff-3c80-49a1-a9cb-b94603e5c68b"
            "deepseek-r1:32b" -> "c1b40fec-2685-4e95-82c5-d591043a009d"
            else -> "d70b00f5-82e1-4070-bcf1-8cce0b9e31ec" // Default to qwen3:8b chat-id
        }
    }

    // Replace your openUrlSafely function with this enhanced version
    private fun openUrlSafely(context: Context, urlStr: String) {
        val TAG = "openUrlSafely"

        try {
            LogManager.d(TAG, "Attempting to open URL: '$urlStr'")

            // Validate input
            if (urlStr.isBlank()) {
                LogManager.w(TAG, "Empty or blank URL provided")
                Toast.makeText(context, "Invalid URL: empty", Toast.LENGTH_SHORT).show()
                return
            }

            val cleanUrl = urlStr.trim()
            LogManager.d(TAG, "Cleaned URL: '$cleanUrl'")

            // For PDF files, check if we already have it locally
            if (cleanUrl.contains(".pdf", ignoreCase = true)) {
                val localFile = checkForLocalPdf(cleanUrl)
                if (localFile != null && localFile.exists()) {
                    LogManager.i(TAG, "Found existing PDF locally: ${localFile.absolutePath}")
                    if (openLocalPdf(context, localFile)) {
                        return
                    }
                    // If local PDF opening fails, continue with URL opening
                    LogManager.w(TAG, "Local PDF opening failed, falling back to URL")
                } else {
                    LogManager.d(TAG, "PDF not found locally, will download from URL")
                }
            }

            // Build proper URI for URL opening
            val uri = try {
                if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
                    Uri.parse("https://$cleanUrl")
                } else {
                    Uri.parse(cleanUrl)
                }
            } catch (e: Exception) {
                LogManager.logCaughtException(TAG, "Failed to parse URI from: $cleanUrl", e)
                Toast.makeText(context, "Invalid URL format", Toast.LENGTH_SHORT).show()
                return
            }

            LogManager.d(TAG, "Parsed URI: $uri")

            // Validate the URI
            if (uri == null || uri.scheme == null) {
                LogManager.w(TAG, "Invalid URI or missing scheme: $uri")
                Toast.makeText(context, "Invalid URL format", Toast.LENGTH_SHORT).show()
                return
            }

            // For PDF files, try to open directly in browser first
            if (cleanUrl.contains(".pdf", ignoreCase = true)) {
                LogManager.d(TAG, "PDF detected, trying browser-first approach")
                if (tryOpenInBrowser(context, uri)) {
                    return
                }
            }

            // Create a simple VIEW intent without MIME type initially
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addCategory(Intent.CATEGORY_BROWSABLE)
            }

            // Check if there's an app that can handle this intent
            val resolveInfo = try {
                context.packageManager.resolveActivity(intent, 0)
            } catch (e: Exception) {
                LogManager.logCaughtException(TAG, "Error resolving activity for intent", e)
                null
            }

            if (resolveInfo != null) {
                try {
                    context.startActivity(intent)
                    LogManager.i(TAG, "Successfully opened URL: $uri")
                    return
                } catch (e: Exception) {
                    LogManager.logCaughtException(TAG, "Failed to start activity with resolved intent", e)
                }
            }

            // Fallback: try to open in browser
            LogManager.d(TAG, "Standard intent failed, trying browser fallback")
            if (!tryOpenInBrowser(context, uri)) {
                // Final fallback: show chooser
                tryShowChooser(context, uri)
            }

        } catch (e: Exception) {
            LogManager.logCaughtException(TAG, "Unexpected error in openUrlSafely", e)
            Toast.makeText(
                context,
                "Error opening link: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Helper function to specifically try opening in browser
    private fun tryOpenInBrowser(context: Context, uri: Uri): Boolean {
        val TAG = "tryOpenInBrowser"

        val browserIntents = listOf(
            // Try Chrome
            Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.android.chrome")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            // Try Firefox
            Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("org.mozilla.firefox")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            // Try default browser
            Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.android.browser")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            // Try any browser
            Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
        )

        for (intent in browserIntents) {
            try {
                if (context.packageManager.resolveActivity(intent, 0) != null) {
                    context.startActivity(intent)
                    LogManager.i(TAG, "Successfully opened in browser: $uri")
                    return true
                }
            } catch (e: Exception) {
                LogManager.d(TAG, "Browser attempt failed: ${e.message}")
                continue
            }
        }

        LogManager.w(TAG, "All browser attempts failed for: $uri")
        return false
    }

    // Helper function to show chooser as last resort
    private fun tryShowChooser(context: Context, uri: Uri) {
        val TAG = "tryShowChooser"

        try {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(intent, "Open with...")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            context.startActivity(chooser)
            LogManager.i(TAG, "Showed chooser for: $uri")

        } catch (e: Exception) {
            LogManager.logCaughtException(TAG, "Even chooser failed", e)
            Toast.makeText(
                context,
                "No application found to open: $uri",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Updated PDF detection method to check permission first
    private fun checkForLocalPdf(url: String): File? {
        if (!hasStoragePermission()) {
            LogManager.w(TAG, "No storage permission, cannot check for local PDFs")
            return null
        }

        // Your existing PDF detection code here...
        return try {
            // ... existing implementation ...
            null // placeholder
        } catch (e: Exception) {
            LogManager.logCaughtException(TAG, "Error checking for local PDF", e)
            null
        }
    }

    // Helper method to search in a specific folder
    private fun searchInFolder(folder: File, nameWithoutExt: String, extension: String): File? {
        val TAG = "searchInFolder"

        try {
            val files = folder.listFiles() ?: return null
            val matchingFiles = mutableListOf<File>()

            for (file in files) {
                if (!file.isFile || !file.canRead()) continue

                val fileName = file.name

                // Check for exact match
                val exactName = "$nameWithoutExt$extension"
                if (fileName.equals(exactName, ignoreCase = true)) {
                    matchingFiles.add(file)
                    LogManager.d(TAG, "Found exact match: $fileName")
                    continue
                }

                // Check for numbered variants like "filename (1).pdf"
                val numberedPattern = Regex("^${Regex.escape(nameWithoutExt)}\\s*\\(\\d+\\)${Regex.escape(extension)}$", RegexOption.IGNORE_CASE)
                if (numberedPattern.matches(fileName)) {
                    matchingFiles.add(file)
                    LogManager.d(TAG, "Found numbered variant: $fileName")
                }
            }

            if (matchingFiles.isNotEmpty()) {
                // Return the most recently modified file
                val mostRecent = matchingFiles.sortedByDescending { it.lastModified() }.first()
                LogManager.i(TAG, "Returning most recent file: ${mostRecent.name}")
                return mostRecent
            }

        } catch (e: Exception) {
            LogManager.logCaughtException(TAG, "Error searching in folder: ${folder.absolutePath}", e)
        }

        return null
    }

    // Function to open local PDF file
    private fun openLocalPdf(context: Context, file: File): Boolean {
        val TAG = "openLocalPdf"

        try {
            LogManager.d(TAG, "Attempting to open local PDF: ${file.absolutePath}")

            // Create URI for the local file
            val uri = Uri.fromFile(file)
            LogManager.d(TAG, "Created URI: $uri")

            // Try different approaches to open the PDF
            val attempts = listOf(
                // Attempt 1: Generic PDF viewer
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },

                // Attempt 2: Generic file viewer
                Intent(Intent.ACTION_VIEW).apply {
                    setData(uri)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },

                // Attempt 3: File manager approach
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "*/*")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )

            for ((index, intent) in attempts.withIndex()) {
                try {
                    if (context.packageManager.resolveActivity(intent, 0) != null) {
                        context.startActivity(intent)
                        LogManager.i(TAG, "Successfully opened local PDF with attempt ${index + 1}")
                        return true
                    }
                } catch (e: Exception) {
                    LogManager.d(TAG, "Attempt ${index + 1} failed: ${e.message}")
                    continue
                }
            }

            // If all attempts fail, try chooser
            try {
                val chooserIntent = Intent.createChooser(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/pdf")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                    "Open PDF with..."
                )
                chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooserIntent)
                LogManager.i(TAG, "Opened local PDF with chooser")
                return true
            } catch (e: Exception) {
                LogManager.logCaughtException(TAG, "Even chooser failed for local PDF", e)
            }

            return false

        } catch (e: Exception) {
            LogManager.logCaughtException(TAG, "Error opening local PDF", e)
            return false
        }
    }

    private fun processResponseWithCitations(responseText: String): String {
        // First, check if the response contains citations in [X] format
        if (!responseText.contains("[") || !responseText.contains("]")) {
            return responseText
        }

        // Extract all citations from the response
        val citationPattern = Regex("\\[(\\d+)\\]")
        val citations = citationPattern.findAll(responseText).map {
            it.groupValues[1].toInt()
        }.toSet()

        // If no citations found, return original response
        if (citations.isEmpty()) {
            return responseText
        }

        // Build citation references section
        val citationsBuilder = StringBuilder("\n\n---\n**References:**\n")

        // Get only the selected RAG files
        val selectedRagFiles = this.chatState.value.ragFiles.filter { it.isSelected }

        citations.sorted().forEach { citationNumber ->
            // Convert citation number to zero-based index
            val index = citationNumber - 1

            // Make sure index is valid within selected files
            if (index >= 0 && index < selectedRagFiles.size) {
                val ragFile = selectedRagFiles[index]
                // Add URL as clickable link - use special marker for parsing later
                citationsBuilder.append("[${citationNumber}] <link:${ragFile.url}>${ragFile.displayName}</link>\n")
            } else {
                citationsBuilder.append("[${citationNumber}] Unknown reference\n")
            }
        }

        return responseText + citationsBuilder.toString()
    }

    // Add this function to MainActivity.kt
    private fun logRagFileDetails(tag: String) {
        LogManager.i(TAG, "$tag: Logging details for ${chatState.value.ragFiles.size} RagFile objects")

        chatState.value.ragFiles.forEachIndexed { index, ragFile ->
            LogManager.i(TAG, "RagFile[$index]: id='${ragFile.id}', displayName='${ragFile.displayName}', " +
                    "url='${ragFile.url}', isSelected=${ragFile.isSelected}")
        }
    }

    // Add this function to MainActivity.kt
    private fun ensureRagFilesMatch() {
        // Get the current definition from code
        val defaultRagFiles = ChatState().ragFiles

        // Create a map of display name -> url
        val urlMap = defaultRagFiles.associate { it.displayName to it.url }

        // Update ragFiles based on display name (which is more stable than ID)
        val updatedRagFiles = chatState.value.ragFiles.map { ragFile ->
            if (urlMap.containsKey(ragFile.displayName)) {
                // Only update URL if it's empty or different
                val correctUrl = urlMap[ragFile.displayName] ?: ""
                if (ragFile.url != correctUrl) {
                    LogManager.i(TAG, "Fixing URL for '${ragFile.displayName}': '${ragFile.url}' -> '$correctUrl'")
                    ragFile.copy(url = correctUrl)
                } else {
                    ragFile
                }
            } else {
                ragFile
            }
        }

        // Update state if changes were made
        if (updatedRagFiles != chatState.value.ragFiles) {
            chatState.value = chatState.value.copy(ragFiles = updatedRagFiles)
            LogManager.i(TAG, "Updated RagFile URLs based on display names")
        }
    }

    private fun processThinkingContent(content: String): String {
        return if (appSettings.value.showThinking) {
            formatThinkingContent(content)
        } else {
            extractResponseWithoutThinking(content)
        }
    }

    private fun extractResponseWithoutThinking(content: String): String {
        return if (content.contains("</think>")) {
            val parts = content.split("</think>", limit = 2)
            if (parts.size > 1) {
                parts[1].trim()
            } else {
                content
            }
        } else {
            content
        }
    }

    private fun formatThinkingContent(content: String): String {
        if (!content.contains("<think>") || !content.contains("</think>")) {
            return content
        }

        val thinkPattern = """<think>(.*?)</think>""".toRegex(RegexOption.DOT_MATCHES_ALL)
        val thinkMatch = thinkPattern.find(content)

        if (thinkMatch != null) {
            val thinkingContent = thinkMatch.groupValues[1].trim()
            val responseContent = content.replace(thinkMatch.value, "").trim()

            return buildString {
                append("🤔 **Thinking Process:**\n")
                append("```\n")
                append(thinkingContent)
                append("\n```\n\n")
                append("**Response:**\n")
                append(responseContent)
            }
        }

        return content
    }

    // Enhanced TTS setup with better error handling
    private fun setupTtsWithSettings() {
        textToSpeech?.let { tts ->
            val settings = appSettings.value
            LogManager.d(TAG, "Setting up TTS with settings: $settings")

            try {
                // Set speech rate
                tts.setSpeechRate(settings.ttsSpeechRate)
                LogManager.d(TAG, "TTS speech rate set to: ${settings.ttsSpeechRate}")

                // Set pitch
                tts.setPitch(settings.ttsPitch)
                LogManager.d(TAG, "TTS pitch set to: ${settings.ttsPitch}")

                // Set language - ensure we have a valid English locale
                val locale = if (settings.ttsLanguage.startsWith("en")) {
                    parseLocaleString(settings.ttsLanguage)
                } else {
                    LogManager.w(TAG, "Non-English language selected: ${settings.ttsLanguage}, defaulting to US English")
                    Locale.US
                }

                val result = tts.setLanguage(locale)
                when (result) {
                    TextToSpeech.LANG_MISSING_DATA -> {
                        LogManager.w(TAG, "TTS language data missing for: $locale")
                        // Try fallback to basic English
                        val fallbackResult = tts.setLanguage(Locale.ENGLISH)
                        LogManager.i(TAG, "Fallback to basic English result: $fallbackResult")
                    }
                    TextToSpeech.LANG_NOT_SUPPORTED -> {
                        LogManager.w(TAG, "TTS language not supported: $locale")
                        // Try fallback to US English
                        val fallbackResult = tts.setLanguage(Locale.US)
                        LogManager.i(TAG, "Fallback to US English result: $fallbackResult")
                    }
                    else -> {
                        LogManager.d(TAG, "TTS language set successfully to: $locale")
                    }
                }

                // Set voice if specified and available (API 21+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && settings.ttsVoice.isNotEmpty()) {
                    try {
                        val voices = tts.voices
                        val selectedVoice = voices?.find { it.name == settings.ttsVoice }
                        if (selectedVoice != null) {
                            val voiceResult = tts.setVoice(selectedVoice)
                            if (voiceResult == TextToSpeech.SUCCESS) {
                                LogManager.d(TAG, "TTS voice set successfully to: ${settings.ttsVoice}")
                            } else {
                                LogManager.w(TAG, "Failed to set TTS voice: ${settings.ttsVoice}")
                            }
                        } else {
                            LogManager.w(TAG, "TTS voice not found: ${settings.ttsVoice}")
                        }
                    } catch (e: Exception) {
                        LogManager.logCaughtException(TAG, "Error setting TTS voice", e)
                    }
                }

            } catch (e: Exception) {
                LogManager.logCaughtException(TAG, "Error in setupTtsWithSettings", e)
            }
        }
    }

    private fun testBasicTts() {
        textToSpeech?.let { tts ->
            LogManager.i(TAG, "--- Basic TTS Test ---")

            // Test if TTS object is working
            LogManager.i(TAG, "TTS object exists: ${tts != null}")

            // Test default language
            try {
                val currentLang = tts.language
                LogManager.i(TAG, "Current TTS language: $currentLang")
            } catch (e: Exception) {
                LogManager.e(TAG, "Error getting current language: ${e.message}")
            }

            // Set English and test
            try {
                val result = tts.setLanguage(Locale.US)
                LogManager.i(TAG, "Set US English result: $result")

                when (result) {
                    TextToSpeech.LANG_MISSING_DATA -> LogManager.w(TAG, "English data missing!")
                    TextToSpeech.LANG_NOT_SUPPORTED -> LogManager.w(TAG, "English not supported!")
                    TextToSpeech.LANG_AVAILABLE -> LogManager.i(TAG, "English available (language only)")
                    TextToSpeech.LANG_COUNTRY_AVAILABLE -> LogManager.i(TAG, "US English available")
                    TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> LogManager.i(TAG, "US English variant available")
                }
            } catch (e: Exception) {
                LogManager.logCaughtException(TAG, "Error setting language", e)
            }
        }
    }

    private fun loadAndDebugLanguages() {
        availableLanguages.clear()

        textToSpeech?.let { tts ->
            LogManager.i(TAG, "--- Loading Languages ---")

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val locales = tts.availableLanguages
                    LogManager.i(TAG, "Available locales from getAvailableLanguages(): ${locales?.size ?: 0}")

                    if (locales == null) {
                        LogManager.w(TAG, "getAvailableLanguages() returned null!")
                    } else {
                        locales.forEach { locale ->
                            LogManager.d(TAG, "Available locale: $locale (${locale.displayName})")

                            // Add all locales for debugging, filter for English later
                            if (locale.language == "en") {
                                availableLanguages.add(
                                    TtsLanguageInfo(
                                        locale = locale,
                                        displayName = "${locale.displayName} (${locale})",
                                        isAvailable = true
                                    )
                                )
                            }
                        }
                    }
                } else {
                    LogManager.i(TAG, "Using legacy language detection (Android < 5.0)")
                    // Legacy approach for older Android
                    val testLocales = listOf(
                        Locale.US, Locale.UK, Locale.CANADA,
                        Locale("en", "AU"), Locale("en", "IN")
                    )

                    testLocales.forEach { locale ->
                        val result = tts.isLanguageAvailable(locale)
                        LogManager.d(TAG, "Testing $locale: result = $result")

                        if (result >= TextToSpeech.LANG_AVAILABLE) {
                            availableLanguages.add(
                                TtsLanguageInfo(
                                    locale = locale,
                                    displayName = "${locale.displayName} (${locale})",
                                    isAvailable = result >= TextToSpeech.LANG_COUNTRY_AVAILABLE
                                )
                            )
                        }
                    }
                }

                LogManager.i(TAG, "Found ${availableLanguages.size} English languages")
                availableLanguages.forEach { lang ->
                    LogManager.i(TAG, "English language: ${lang.displayName}")
                }

            } catch (e: Exception) {
                LogManager.logCaughtException(TAG, "Error loading languages", e)
            }
        }
    }

    private fun loadAndDebugVoices() {
        coroutineScope.launch(Dispatchers.Main) {
            LogManager.i(TAG, "--- Loading Voices ---")

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                LogManager.i(TAG, "Voice selection not available on Android < 5.0")
                return@launch  // Fix: Use return@launch instead of return
            }

            textToSpeech?.let { tts ->
                try {
                    val allVoices = tts.voices
                    LogManager.i(TAG, "Total voices from getVoices(): ${allVoices?.size ?: 0}")

                    if (allVoices == null) {
                        LogManager.w(TAG, "getVoices() returned null!")
                        return@launch  // Fix: Use return@launch
                    }

                    if (allVoices.isEmpty()) {
                        LogManager.w(TAG, "No voices available from TTS engine!")
                        return@launch  // Fix: Use return@launch
                    }

                    // Create a new list to collect voices
                    val newVoices = mutableListOf<TtsVoiceInfo>()

                    // Log ALL voices first for debugging
                    LogManager.i(TAG, "=== ALL AVAILABLE VOICES ===")
                    allVoices.forEachIndexed { index, voice ->
                        LogManager.i(TAG, "Voice $index: ${voice.name}")
                        LogManager.i(TAG, "  Locale: ${voice.locale} (${voice.locale.displayName})")
                        LogManager.i(TAG, "  Language: ${voice.locale.language}")
                        LogManager.i(TAG, "  Country: ${voice.locale.country}")
                        LogManager.i(TAG, "  Quality: ${voice.quality}")
                        LogManager.i(TAG, "  Network required: ${voice.isNetworkConnectionRequired}")
                        LogManager.i(TAG, "  Features: ${voice.features}")
                    }

                    // Now filter for English voices
                    LogManager.i(TAG, "=== FILTERING FOR ENGLISH VOICES ===")
                    var englishCount = 0

                    allVoices.forEach { voice ->
                        LogManager.d(TAG, "Checking voice: ${voice.name}, language: '${voice.locale.language}'")

                        if (voice.locale.language.equals("en", ignoreCase = true)) {
                            englishCount++
                            LogManager.i(TAG, "Found English voice: ${voice.name}")

                            val qualityText = when (voice.quality) {
                                android.speech.tts.Voice.QUALITY_VERY_HIGH -> "Very High"
                                android.speech.tts.Voice.QUALITY_HIGH -> "High"
                                android.speech.tts.Voice.QUALITY_NORMAL -> "Normal"
                                android.speech.tts.Voice.QUALITY_LOW -> "Low"
                                android.speech.tts.Voice.QUALITY_VERY_LOW -> "Very Low"
                                else -> "Unknown (${voice.quality})"
                            }

                            val networkText = if (voice.isNetworkConnectionRequired) " (Network)" else " (Local)"

                            // Fix: Add to newVoices instead of availableVoices
                            newVoices.add(
                                TtsVoiceInfo(
                                    name = voice.name,
                                    displayName = "${voice.locale.displayName} - ${
                                        voice.name.split("#").lastOrNull() ?: voice.name
                                    } - $qualityText$networkText",
                                    locale = voice.locale,
                                    quality = voice.quality,
                                    isNetworkConnectionRequired = voice.isNetworkConnectionRequired
                                )
                            )
                        }
                    }

                    LogManager.i(TAG, "Found $englishCount English voices out of ${allVoices.size} total voices")

                    if (englishCount == 0) {
                        LogManager.w(TAG, "NO ENGLISH VOICES FOUND!")
                        LogManager.w(TAG, "User may need to:")
                        LogManager.w(TAG, "1. Install English TTS data")
                        LogManager.w(TAG, "2. Check TTS settings")
                        LogManager.w(TAG, "3. Install Google TTS or other TTS engine")
                    }

                    // Sort voices
                    newVoices.sortBy { it.displayName }

                    // Update the state list on the main thread
                    withContext(Dispatchers.Main) {
                        availableVoices.clear()
                        availableVoices.addAll(newVoices)
                        LogManager.i(TAG, "Updated available voices: ${availableVoices.size}")

                        // Log the final list
                        availableVoices.forEach { voice ->
                            LogManager.i(TAG, "English voice available: ${voice.displayName}")
                        }
                    }

                } catch (e: Exception) {
                    LogManager.logCaughtException(TAG, "Error loading voices", e)
                }
            }
        }
    }

    private fun parseLocaleString(localeString: String): Locale {
        val parts = localeString.split("-", "_")
        return when (parts.size) {
            1 -> Locale(parts[0])
            2 -> Locale(parts[0], parts[1])
            3 -> Locale(parts[0], parts[1], parts[2])
            else -> Locale.getDefault()
        }
    }

    private fun showTtsDataMissingDialog() {
        // You can implement this to show a dialog to the user
        LogManager.i(TAG, "Consider showing dialog to install TTS data")
    }


    // Fix 2: Quick language loading without heavy processing
    private fun loadLanguagesQuickly() {
        try {
            availableLanguages.clear()

            textToSpeech?.let { tts ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val locales = tts.availableLanguages

                    // Just add English locales quickly without extensive processing
                    locales?.filter { it.language == "en" }?.take(10)?.forEach { locale ->
                        availableLanguages.add(
                            TtsLanguageInfo(
                                locale = locale,
                                displayName = "${locale.displayName} (${locale})",
                                isAvailable = true
                            )
                        )
                    }
                } else {
                    // Add just common English locales for older Android
                    listOf(Locale.US, Locale.UK, Locale.CANADA).forEach { locale ->
                        availableLanguages.add(
                            TtsLanguageInfo(
                                locale = locale,
                                displayName = "${locale.displayName} (${locale})",
                                isAvailable = true
                            )
                        )
                    }
                }

                LogManager.i(TAG, "Quickly loaded ${availableLanguages.size} languages")
            }
        } catch (e: Exception) {
            LogManager.logCaughtException(TAG, "Error in quick language loading", e)
        }
    }

    // Fix 3: Quick voice loading without detailed analysis
    private fun loadVoicesQuickly() {
        try {
            availableVoices.clear()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                textToSpeech?.let { tts ->
                    val allVoices = tts.voices

                    if (allVoices != null) {
                        // Just get English voices quickly, limit to reasonable number
                        allVoices.asSequence()
                            .filter { it.locale.language == "en" }
                            .take(20) // Limit to prevent UI freeze
                            .forEach { voice ->
                                val typeIcon = if (voice.isNetworkConnectionRequired) "🌐" else "📱"
                                val qualityStars = "★".repeat(maxOf(1, voice.quality / 100))

                                availableVoices.add(
                                    TtsVoiceInfo(
                                        name = voice.name,
                                        displayName = "${voice.locale.displayName} $typeIcon $qualityStars",
                                        locale = voice.locale,
                                        quality = voice.quality,
                                        isNetworkConnectionRequired = voice.isNetworkConnectionRequired
                                    )
                                )
                            }

                        LogManager.i(TAG, "Quickly loaded ${availableVoices.size} voices")
                    }
                }
            }
        } catch (e: Exception) {
            LogManager.logCaughtException(TAG, "Error in quick voice loading", e)
        }
    }

    // Quick voice application without heavy processing
    private fun applyVoiceSettingsQuickly(voiceName: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && voiceName.isNotEmpty()) {
                textToSpeech?.let { tts ->
                    val voice = tts.voices?.find { it.name == voiceName }
                    voice?.let {
                        tts.setVoice(it)
                        LogManager.d(TAG, "Quickly applied voice: ${it.name}")
                    }
                }
            }
        } catch (e: Exception) {
            LogManager.logCaughtException(TAG, "Error in quick voice application", e)
        }
    }

} // End of MainActivity Class

private fun Modifier.disableSelection() = composed {
    this.pointerInput(Unit) {
        detectTapGestures {
            // Do nothing, effectively disabling selection for this modifier
        }
    }
}

//data class Selection(val start: Int, val end: Int)

// Settings related

// Then, modify the ChatState to include our RAG file selections
data class ChatState(
    val messages: List<Message> = emptyList(),
    val inputText: String = "",
    val isAnimationVisible: Boolean = false,
    val ragFiles: List<RagFile> = listOf(
        RagFile("2a4a9a2d-a317-470b-996c-6b0b44761b23", "Table of Polymer Data for Processing", "https://sparkonelabs.com/RAG_pdfs/Molding_Layout.html"),
        RagFile("9c4a27f2-509f-4856-838a-954d20f0098a", "Table of Workcells, Robots, Presses and HMI units", "https://sparkonelabs.com/RAG_pdfs/Molding_Layout.html"),
        RagFile("89c8e301-744e-455a-9d9c-0ec905869bc1", "Plastic Injection Molding Processing Technician Guide", "https://sparkonelabs.com/RAG_pdfs/Processing_Troubleshooting_Guide.html"),
        RagFile("abf471b1-55cd-41b4-b8f0-46211cf978b1", "Plastic Technician's Toolbox Volume 1 - Math", "https://sparkonelabs.com/RAG_pdfs/pdf_viewer.html?pdf=18036_01.pdf"),
        RagFile("a17ecf98-5c0d-4208-8de2-03b2d68c8c37", "Plastic Technician's Toolbox Volume 2 - Safety", "https://sparkonelabs.com/RAG_pdfs/pdf_viewer.html?pdf=18036_02.pdf"),
        RagFile("cf989978-e6e9-48fb-b5b4-6d8f9758e623", "Plastic Technician's Toolbox Volume 3 - Glossary", "https://sparkonelabs.com/RAG_pdfs/pdf_viewer.html?pdf=18036_03.pdf"),
        RagFile("b40db982-31c1-4509-a83e-12721414d0b9", "Plastic Technician's Toolbox Volume 4A - Clamp End", "https://sparkonelabs.com/RAG_pdfs/pdf_viewer.html?pdf=18036_04a.pdf"),
        RagFile("827d3a3c-88a4-402f-9fbd-75b3870a0483", "Plastic Technician's Toolbox Volume 4B - Auxiliary Equipment", "https://sparkonelabs.com/RAG_pdfs/pdf_viewer.html?pdf=18036_04b.pdf"),
        RagFile("44d95954-ec6b-4014-8f7c-75756f8f60c0", "Plastic Technician's Toolbox Volume 5A - Part Design", "https://sparkonelabs.com/RAG_pdfs/pdf_viewer.html?pdf=18036_05a.pdf"),
        RagFile("69c94350-76cf-4cdf-9470-073335650543", "Plastic Technician's Toolbox Volume 5B - Mold Base Standard Components", "https://sparkonelabs.com/RAG_pdfs/pdf_viewer.html?pdf=18036_05b.pdf"),
        RagFile("efb987a5-60b0-47a7-bc3b-76acbb0120c7", "Plastic Technician's Toolbox Volume 5C - Mold Design", "https://sparkonelabs.com/RAG_pdfs/pdf_viewer.html?pdf=18036_05c.pdf"),
        RagFile("73471eb3-5f11-43e3-887e-6c2b3b6b007b", "Plastic Technician's Toolbox Volume 5D - Runners", "https://sparkonelabs.com/RAG_pdfs/pdf_viewer.html?pdf=18036_05d.pdf"),
        RagFile("1e280199-160d-4b4d-a246-20d3cc57a504", "Plastic Technician's Toolbox Volume 5E - Hot Runner Systems", "https://sparkonelabs.com/RAG_pdfs/pdf_viewer.html?pdf=18036_05e.pdf"),
        RagFile("92f5d18e-94a9-4de0-84cb-b98e033a5d17", "Plastic Technician's Toolbox Volume 5F - Ejection", "https://sparkonelabs.com/RAG_pdfs/pdf_viewer.html?pdf=18036_05f.pdf"),
        RagFile("48a8a134-2797-4d04-9c51-10351e03ac61", "Plastic Technician's Toolbox Volume 5G - Dealing with Undercuts", "https://sparkonelabs.com/RAG_pdfs/pdf_viewer.html?pdf=18036_05g.pdf"),
        RagFile("b869f50c-2883-4ccf-b694-fd583f906985", "Plastic Technician's Toolbox Volume 6A - Plastic Flow", "https://sparkonelabs.com/RAG_pdfs/pdf_viewer.html?pdf=18036_06a.pdf"),
        RagFile("9c563182-4ae6-4ecd-80c8-8c514c069e65", "Plastic Technician's Toolbox Volume 6B - Optimizing the Molding Process", "https://sparkonelabs.com/RAG_pdfs/pdf_viewer.html?pdf=18036_06b.pdf"),
        RagFile("a440c0b0-11a1-49d9-950a-0f9a46a1576c", "Plastic Technician's Toolbox Volume 6C - Tips for Supervisors and Technicians", "https://sparkonelabs.com/RAG_pdfs/pdf_viewer.html?pdf=18036_06c.pdf"),
        RagFile("5391b346-d64a-4507-ae73-7a25c50767a3", "Plastic Technician's Toolbox Volume 6D - Computer Flow Simulations", "https://sparkonelabs.com/RAG_pdfs/pdf_viewer.html?pdf=18036_06d.pdf"),
        RagFile("0a91203f-1216-49d5-9b95-229583e0a787", "Plastic Technician's Toolbox Volume 6E - The MuCell(R) Process", "https://sparkonelabs.com/RAG_pdfs/pdf_viewer.html?pdf=18036_06e.pdf"),
        RagFile("03ea07c4-6c3e-47c8-9f51-0e6489ae3189", "Plastic Technician's Toolbox Volume 6F - Troubleshooting", "https://sparkonelabs.com/RAG_pdfs/pdf_viewer.html?pdf=18036_06f.pdf"),
        RagFile("664cd351-e0a3-42b3-8429-ba0bedbc5501", "FANUC R-30iA and R-30iB Controller KAREL Reference Manual", "https://sparkonelabs.com/RAG_pdfs/pdf_viewer.html?pdf=Fanuc_R-30iA_and_R-30iB.pdf"),
        RagFile("f4539ec4-0e85-4614-9c2f-7dba282c5be9", "FANUC Series 0i, 16, 18, 20, 21 Macro Compiler/Executor Programming Manual", "https://sparkonelabs.com/RAG_pdfs/pdf_viewer.html?pdf=Fanuc_Programming_Manual.pdf"),
        RagFile("c1efe9b2-29ee-4553-9c86-57c87b3f7c5f", "FANUC R-30iB / R-30iB Mate Plus Controller Maintenance Manual", "https://sparkonelabs.com/RAG_pdfs/pdf_viewer.html?pdf=FANUC_R-30iB_and_R-30iB_Mate_Plus_Controller_Maintenance_Manual.pdf"),
        RagFile("2c18abc6-14ee-4ac1-94c9-f2e40fe26508", "FANUC I/O Unit-MODEL A: Connection and Maintenance Manual", "https://sparkonelabs.com/RAG_pdfs/pdf_viewer.html?pdf=FANUC_IO_Unit_Model_Connection_and_Maintenance_Manual.pdf"),
        RagFile("37a65837-c584-451c-aa7f-ef97613e0a60", "FANUC Robot Series R-30iB/R-30iB Plus Controller Maintenance Manual", "https://sparkonelabs.com/RAG_pdfs/pdf_viewer.html?pdf=FANUC_R-30iB_and_R-30iB_Plus_Controller_Maintenance_Manual.pdf"),
        RagFile("d25909ee-6d11-4b4d-99ec-4a606545a31d", "FANUC R-30iB Plus and R-30iB Mate Plus Controller Software Error Code Manual", "https://sparkonelabs.com/RAG_pdfs/pdf_viewer.html?pdf=FANUC_R-30iB_Plus_and_R-30iB_Mate_Plus_Controller_Software_Error_Code_Manual.pdf"),
        RagFile("d770ad73-dfa9-4723-96ce-2a83b6fd3be8","FANUC Robot M-20iB Mechanical Unit Operator's Manual", "https://sparkonelabs.com/RAG_pdfs/pdf_viewer.html?pdf=FANUC_Robot_M-20iB_Mechanical_Unit_Operators_Manual.pdf"),
        RagFile("df8ccc7e-f647-4088-b45c-46924df6f77c", "FANUC Robot M-710iC /50/70/50H/50S/45M/50E Mechanical Unit Operator's Manual", "https://sparkonelabs.com/RAG_pdfs/pdf_viewer.html?pdf=FANUC_Robot_M-710iC_50_70_50H_50S_45M_50E_Mechanical_Unit_Operators_Manual.pdf"),
        RagFile("7f6766d2-cbda-4cf2-9a0a-e01fac414036", "FANUC Robot R-2000iB Mechanical Unit Operator's Manual", "https://sparkonelabs.com/RAG_pdfs/pdf_viewer.html?pdf=FANUC_Robot_R-2000iB_Mechanical_Unit_Operators_Manual.pdf"),
        RagFile("af572edb-5de5-4d95-b8c8-ff6909839fcd", "FANUC Robot R-2000iC Mechanical Unit Operator's Manual", "https://sparkonelabs.com/RAG_pdfs/pdf_viewer.html?pdf=FANUC_Robot_R-2000iC_Mechanical_Unit_Operators_Manual.pdf"),
        RagFile("4eec512d-76dd-474f-b6b4-702ebb7155fa", "eDart Process Control Software v10.xx Manual (2017)", "https://sparkonelabs.com/RAG_pdfs/pdf_viewer.html?pdf=eDART_Process_Control_Software_v10.xx_Manual_06.23.2017.pdf"),
        RagFile("a7018213-554e-4e67-ae92-a63d94c24c86", "RJG eDart Getting Started Manual", "https://sparkonelabs.com/RAG_pdfs/pdf_viewer.html?pdf=RJG_eDart_getting_started.pdf")
    )
) : Serializable

data class Message(
    val role: String,
    val content: String,
    val id: String = UUID.randomUUID().toString()
) : Serializable {
    override fun toString(): String {
        return "$role: ${content.take(50)}${if (content.length > 50) "..." else ""}"
    }
}

data class RagFile(
    val id: String,
    val displayName: String,
    val url: String, // No default value, force explicit assignment
    var isSelected: Boolean = false
)

// Serializable versions of your data classes for safe JSON serialization
data class SerializableChatState(
    val messages: List<SerializableMessage> = emptyList(),
    val inputText: String = "",
    val isAnimationVisible: Boolean = false,
    val ragFiles: List<SerializableRagFile> = emptyList()
)

data class SerializableMessage(
    val role: String,
    val content: String,
    val id: String
)

data class SerializableRagFile(
    val id: String,
    val displayName: String,
    val url: String,
    val isSelected: Boolean
)

// Enhanced connectivity result data class (update the existing one)
data class ConnectivityResult(
    val isHostReachable: Boolean,
    val isPortOpen: Boolean,
    val responseTime: Long,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val portNumber: Int = 5555
)

data class AppSettings(
    val selectedModel: String = "qwen3:8b",
    val apiTimeout: Int = 600,
    val enableTTS: Boolean = true,
    val autoSaveChat: Boolean = true,
    val maxLogEntries: Int = 500,
    val connectivityTestInterval: Int = 30,
    val showThinking: Boolean = false,
    // TTS-specific settings - ensure English defaults
    val ttsSpeechRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val ttsLanguage: String = "en-US", // Ensure English default
    val ttsVoice: String = "",
    val ttsVolume: Float = 1.0f
) : Serializable

// Add these data classes for TTS management
data class TtsLanguageInfo(
    val locale: Locale,
    val displayName: String,
    val isAvailable: Boolean
)

data class TtsVoiceInfo(
    val name: String,
    val displayName: String,
    val locale: Locale,
    val quality: Int = 0,
    val isNetworkConnectionRequired: Boolean = false
)