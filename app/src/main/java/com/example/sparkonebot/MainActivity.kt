package com.example.oxfordbot

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.Context
import android.content.res.Configuration
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.Card
import androidx.compose.material.RadioButton
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.OutlinedButton
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
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
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ButtonDefaults
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import com.example.oxfordbot.ui.theme.*
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
        private const val MEDIA_PERMISSION_CODE = 101 // For Android 13+
        private const val KEY_APP_SETTINGS = "app_settings"

        // Define the permission constant for older SDK compatibility
        private const val READ_MEDIA_DOCUMENTS = "android.permission.READ_MEDIA_DOCUMENTS"

    }

    // Add this method to request storage permissions
    // Updated permission request method with proper API checks
    // Updated permission request method with proper API checks
    // Then use READ_MEDIA_DOCUMENTS instead of the string literal:
    // Fix 2: Update requestStoragePermissions to use correct permission constants
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

    // Simplified version that should work for most cases (Preferred over more extensive version)
    private fun requestStoragePermissionsSimple() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permissions = mutableListOf<String>()

            // For most Android versions, READ_EXTERNAL_STORAGE is sufficient
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }

            if (permissions.isNotEmpty()) {
                ActivityCompat.requestPermissions(
                    this,
                    permissions.toTypedArray(),
                    STORAGE_PERMISSION_CODE
                )
            } else {
                LogManager.i(TAG, "Storage permission already granted")
            }
        }
    }

    // Fix 1: Update the onRequestPermissionsResult method signature
    //override fun onRequestPermissionsResult(
    //    requestCode: Int,
    //    permissions: Array<String>,  // Changed from Array<out String>
    //    grantResults: IntArray
    //) {
    //    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    //
    //    when (requestCode) {
    //        STORAGE_PERMISSION_CODE -> {
    //            isPermissionRequested = false
    //
    //            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
    //                LogManager.i("Permissions", "READ_EXTERNAL_STORAGE permission granted")
    //                Toast.makeText(this, "Storage permission granted - can now find downloaded PDFs", Toast.LENGTH_SHORT).show()
    //            } else {
    //                LogManager.w("Permissions", "READ_EXTERNAL_STORAGE permission denied")
    //                handlePermissionDenied(Manifest.permission.READ_EXTERNAL_STORAGE)
    //            }
    //        }
    //
    //        MEDIA_PERMISSION_CODE -> {
    //            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
    //                LogManager.i("Permissions", "READ_MEDIA_IMAGES permission granted")
    //                Toast.makeText(this, "Media permission granted - can now find downloaded PDFs", Toast.LENGTH_SHORT).show()
    //            } else {
    //                LogManager.w("Permissions", "READ_MEDIA_IMAGES permission denied")
    //                handlePermissionDenied(Manifest.permission.READ_MEDIA_IMAGES)
    //            }
    //        }
    //    }
    //}

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

    // Helper method to handle permission denial
    private fun handlePermissionDenied(permission: String) {
        if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
            LogManager.w("Permissions", "User selected 'Don't ask again' for $permission")
            showPermissionPermanentlyDeniedDialog()
        } else {
            LogManager.w("Permissions", "User denied $permission but can ask again")
            Toast.makeText(this, "Storage permission needed to find downloaded PDFs", Toast.LENGTH_LONG).show()
        }
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
    // Fix 3: Update hasStoragePermission to use correct permission constants
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

        textToSpeech = TextToSpeech(this, TextToSpeech.OnInitListener { status ->
            LogManager.i(TAG, "TTS OnInitListener called with status: $status")

            if (status == TextToSpeech.SUCCESS) {
                LogManager.i(TAG, "TTS initialization successful")
                isTtsInitialized.value = true

                // REMOVE the automatic setup that causes speech
                // Just set up TTS without the debugging speech
                coroutineScope.launch {
                    delay(500) // Wait for TTS to be fully ready
                    setupTtsQuietly() // New function without automatic speech
                }
            } else {
                LogManager.e(TAG, "TTS initialization failed with status: $status")
                isTtsInitialized.value = false
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

    // Here's the fully updated handleApiResponse method that processes all response paths
    private fun handleApiResponse(response: ApiResponse) {
        try {
            LogManager.d(TAG, "Raw API response: ${response.response}")

            try {
                // First, check if the response contains choices with message content
                if (response.choices != null && response.choices.isNotEmpty() && response.choices[0].message?.content != null) {
                    var messageContent = response.choices[0].message!!.content!!

                    // Show thinking if toggled
                    //val processedContent = processThinkingContent(messageContent)


                    if (messageContent.isNotEmpty()) {
                        // Process the message content to add citations
                        val processedContent = processResponseWithCitations(messageContent)

                        val message = Message("assistant", processedContent)
                        chatState.value = chatState.value.copy(
                            messages = chatState.value.messages + message,
                            isAnimationVisible = false
                        )
                        speak(messageContent) // Not adding citations to speech to keep it natural
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

                // Extract only the part after </think> if it exists
                //val processedContent = processThinkingContent(messageContent)

                LogManager.d(TAG, "Full processed message content: $messageContent")

                if (messageContent.isNotEmpty()) {
                    // Process the message content to add citations
                    val processedContent = processResponseWithCitations(messageContent)

                    val message = Message("assistant", processedContent)
                    chatState.value = chatState.value.copy(
                        messages = chatState.value.messages + message,
                        isAnimationVisible = false
                    )
                    speak(messageContent) // Not adding citations to speech
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
                speak(messageContent) // Not adding citations to speech
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

    // Enhanced speak function with better error handling
    // 3. Update the speak function to be more explicit about when it should speak
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

        // Add a check to prevent unwanted speech during setup
        if (text.contains("Testing voice") || text.contains("test")) {
            LogManager.d(TAG, "Skipping test speech during setup: $text")
            return
        }

        try {
            val utteranceId = UUID.randomUUID().toString()
            LogManager.d(TAG, "Speaking text: \"${text.take(50)}...\" with utterance ID: $utteranceId")

            // Create parameters bundle for volume control
            val params = Bundle()
            params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, appSettings.value.ttsVolume)

            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                textToSpeech?.speak(text, TextToSpeech.QUEUE_ADD, params, utteranceId)
            } else {
                // Legacy method
                val hashParams = HashMap<String, String>()
                hashParams[TextToSpeech.Engine.KEY_PARAM_VOLUME] = appSettings.value.ttsVolume.toString()
                textToSpeech?.speak(text, TextToSpeech.QUEUE_ADD, hashParams)
            }

            LogManager.d(TAG, "TTS speak result: $result")

        } catch (e: Exception) {
            LogManager.logCaughtException(TAG, "Error during TTS speak", e)
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
                            // Use the non-blocking version instead of the heavy blocking version
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
                                onClose = { showSettingsScreen.value = false }
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

    // Complete Settings Screen Composable with Lazy Loading
    @Composable
    fun SettingsScreenWithLazyLoading(
        settings: AppSettings,
        onSettingsChange: (AppSettings) -> Unit,
        onClose: () -> Unit
    ) {
        val scrollState = rememberLazyListState()
        var isLoadingTtsData by remember { mutableStateOf(true) }
        var ttsLoadingMessage by remember { mutableStateOf("Loading TTS data...") }

        // Non-blocking TTS data loading
        LaunchedEffect(Unit) {
            launch(Dispatchers.IO) {
                try {
                    withContext(Dispatchers.Main) {
                        ttsLoadingMessage = "Loading languages..."
                    }

                    delay(50) // Let UI update

                    withContext(Dispatchers.Main) {
                        loadLanguagesQuickly()
                    }

                    withContext(Dispatchers.Main) {
                        ttsLoadingMessage = "Loading voices..."
                    }

                    delay(50) // Let UI update

                    withContext(Dispatchers.Main) {
                        loadVoicesQuickly()
                    }

                    delay(100) // Small final delay

                    withContext(Dispatchers.Main) {
                        isLoadingTtsData = false
                    }

                } catch (e: Exception) {
                    LogManager.logCaughtException(TAG, "Error in lazy TTS loading", e)
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
                                                // Reload voices for new language (non-blocking)
                                                coroutineScope.launch(Dispatchers.IO) {
                                                    withContext(Dispatchers.Main) {
                                                        loadVoicesQuickly()
                                                    }
                                                }
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
                                                // Apply voice in background to avoid UI freeze
                                                coroutineScope.launch(Dispatchers.Main) {
                                                    applyVoiceSettingsQuickly(voiceName)
                                                }
                                            }
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))
                                    }

                                    // Test TTS Button
                                    Button(
                                        onClick = {
                                            LogManager.i(TAG, "Test Speech button clicked - USER INITIATED")

                                            // Quick test without heavy processing
                                            coroutineScope.launch(Dispatchers.Main) {
                                                try {
                                                    // Stop any current speech
                                                    textToSpeech?.stop()

                                                    // Apply current settings quickly
                                                    textToSpeech?.let { tts ->
                                                        tts.setSpeechRate(settings.ttsSpeechRate)
                                                        tts.setPitch(settings.ttsPitch)

                                                        val locale = parseLocaleString(settings.ttsLanguage)
                                                        tts.setLanguage(locale)

                                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && settings.ttsVoice.isNotEmpty()) {
                                                            val voice = tts.voices?.find { it.name == settings.ttsVoice }
                                                            voice?.let { tts.setVoice(it) }
                                                        }
                                                    }

                                                    // Test speak with immediate playback
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

                            // Add this section right after the Auto-save Chat toggle in your SettingsScreenWithLazyLoading composable
// Find the SwitchSettingCard for "Auto-save Chat" and add this code right after it:

                            Spacer(modifier = Modifier.height(8.dp))

// Clear Chat History Button
                            Button(
                                onClick = {
                                    // Clear the chat messages
                                    chatState.value = chatState.value.copy(messages = emptyList())

                                    // Log the action
                                    LogManager.i(TAG, "Chat history cleared by user from settings")

                                    // Show confirmation toast
                                    Toast.makeText(
                                        this@MainActivity,
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
                                    text = "Initialized: ${isTtsInitialized.value}",
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
                                        onClick = {
                                            // Reload voices non-blocking
                                            coroutineScope.launch(Dispatchers.IO) {
                                                withContext(Dispatchers.Main) {
                                                    loadLanguagesQuickly()
                                                    loadVoicesQuickly()
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(backgroundColor = LightBlue),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Reload TTS", color = Color.White)
                                    }

                                    Button(
                                        onClick = {
                                            // Add simple fallback voices
                                            loadVoicesSimple()
                                        },
                                        colors = ButtonDefaults.buttonColors(backgroundColor = Green),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Add Default Voice", color = Color.White)
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

    // Network Details Screen Composable
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
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
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

    // Text to Speech related
    // Add these new composable functions
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
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
                        // Fix: Handle empty options list
                        val displayText = when {
                            options.isEmpty() -> "No options available"
                            selectedValue.isEmpty() -> "Select ${title.lowercase()}"
                            else -> options.find { it.first == selectedValue }?.second ?: "Default Voice"
                        }

                        Text(
                            text = displayText,
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Start
                        )
                        Text("▼", color = Gold)
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Navy) // Set background to Navy blue
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

                // Fix: Show selected value only if we have options
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

    // Each model in open-webui has an associated chat-id for ease of configuration
    private fun getChatIdForModel(model: String): String {
        return when (model) {
            "qwen3:8b" -> "d70b00f5-82e1-4070-bcf1-8cce0b9e31ec"
            "qwen3:32b" -> "afea4aff-3c80-49a1-a9cb-b94603e5c68b"
            "deepseek-r1:32b" -> "c1b40fec-2685-4e95-82c5-d591043a009d"
            else -> "d70b00f5-82e1-4070-bcf1-8cce0b9e31ec" // Default to qwen3:8b chat-id
        }
    }

    // Helper function to get model descriptions
    private fun getModelDescription(model: String): String {
        return when (model) {
            "qwen3:8b" -> "Balanced performance and speed (Chat: ${getChatIdForModel(model).take(8)}...)"
            "qwen3:32b" -> "Large Qwen model with enhanced capabilities (Chat: ${getChatIdForModel(model).take(8)}...)"
            "deepseek-r1:32b" -> "Large model with enhanced reasoning (Chat: ${getChatIdForModel(model).take(8)}...)"
            else -> "Unknown model"
        }
    }

    // Helper function to format timestamps
    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
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

    // New method using MediaStore API
    private fun findFileUsingMediaStore(filename: String): File? {
        val TAG = "findFileUsingMediaStore"

        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                LogManager.w(TAG, "No READ_EXTERNAL_STORAGE permission")
                return null
            }

            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.SIZE
            )

            // Get filename without extension for pattern matching
            val nameWithoutExt = if (filename.contains('.')) {
                filename.substring(0, filename.lastIndexOf('.'))
            } else {
                filename
            }

            // Create selection to find files that match our pattern
            val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ? OR " +
                    "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf(filename, "$nameWithoutExt (%).pdf")

            val cursor: Cursor? = contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC" // Most recent first
            )

            cursor?.use {
                val dataColumnIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                val nameColumnIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeColumnIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)

                while (cursor.moveToNext()) {
                    val filePath = cursor.getString(dataColumnIndex)
                    val fileName = cursor.getString(nameColumnIndex)
                    val fileSize = cursor.getLong(sizeColumnIndex)

                    LogManager.d(TAG, "Found potential match: $fileName at $filePath (${fileSize}KB)")

                    val file = File(filePath)
                    if (file.exists() && file.canRead()) {
                        LogManager.i(TAG, "Verified file exists and is readable: $filePath")
                        return file
                    }
                }
            }

        } catch (e: Exception) {
            LogManager.logCaughtException(TAG, "Error using MediaStore API", e)
        }

        return null
    }

    // Improved direct access method
    private fun findFileUsingDirectAccess(filename: String): File? {
        val TAG = "findFileUsingDirectAccess"

        try {
            val nameWithoutExt = if (filename.contains('.')) {
                filename.substring(0, filename.lastIndexOf('.'))
            } else {
                filename
            }

            val extension = if (filename.contains('.')) {
                filename.substring(filename.lastIndexOf('.'))
            } else {
                ".pdf"
            }

            // Check multiple possible download locations
            val downloadFolders = listOf(
                // Standard Downloads folder
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                // Alternative Downloads folder
                File(Environment.getExternalStorageDirectory(), "Download"),
                // Another common location
                File("/storage/emulated/0/Download"),
                // User's Documents folder
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                // App-specific external files directory
                getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            )

            for (folder in downloadFolders) {
                if (folder == null || !folder.exists() || !folder.isDirectory) {
                    continue
                }

                LogManager.d(TAG, "Searching in: ${folder.absolutePath}")

                val foundFile = searchInFolder(folder, nameWithoutExt, extension)
                if (foundFile != null) {
                    return foundFile
                }
            }

        } catch (e: Exception) {
            LogManager.logCaughtException(TAG, "Error in direct access search", e)
        }

        return null
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

    // Handle permission request results
    //override fun onRequestPermissionsResult(
    //    requestCode: Int,
    //    permissions: Array<out String>,
    //    grantResults: IntArray
    //) {
    //    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    //
    //    when (requestCode) {
    //        STORAGE_PERMISSION_CODE -> {
    //            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
    //                LogManager.i("Permissions", "Storage permissions granted")
    //                Toast.makeText(this, "Storage permissions granted", Toast.LENGTH_SHORT).show()
    //            } else {
    //                LogManager.w("Permissions", "Storage permissions denied")
    //                Toast.makeText(this, "Storage permissions needed to find downloaded files", Toast.LENGTH_LONG).show()
    //            }
    //        }
    //    }
    //}

    // Helper function to find the most recent matching file
    /*private fun findMostRecentMatchingFile(folder: File, nameWithoutExt: String, extension: String): File? {
        val TAG = "findMostRecentMatchingFile"

        try {
            val files = folder.listFiles() ?: return null
            val matchingFiles = mutableListOf<File>()

            // Collect all matching files (exact name and numbered variants)
            for (file in files) {
                if (file.isFile && file.canRead()) {
                    val fileName = file.name

                    // Check exact match
                    val exactName = "$nameWithoutExt$extension"
                    if (fileName.equals(exactName, ignoreCase = true)) {
                        matchingFiles.add(file)
                        continue
                    }

                    // Check numbered variants
                    val pattern = Regex("^${Regex.escape(nameWithoutExt)}\\s*\\(\\d+\\)${Regex.escape(extension)}$", RegexOption.IGNORE_CASE)
                    if (pattern.matches(fileName)) {
                        matchingFiles.add(file)
                    }
                }
            }

            if (matchingFiles.isEmpty()) {
                LogManager.d(TAG, "No matching files found")
                return null
            }

            // Sort by last modified date (most recent first)
            val mostRecent = matchingFiles.sortedByDescending { it.lastModified() }.first()

            LogManager.i(TAG, "Most recent file: ${mostRecent.name} (${matchingFiles.size} variants found)")
            LogManager.d(TAG, "File details: ${mostRecent.absolutePath}, size: ${mostRecent.length()} bytes, modified: ${java.util.Date(mostRecent.lastModified())}")

            return mostRecent

        } catch (e: Exception) {
            LogManager.logCaughtException(TAG, "Error finding most recent matching file", e)
            return null
        }
    }*/

    // Helper function to extract filename from URL
    private fun extractFilenameFromUrl(url: String): String? {
        return try {
            val urlObj = URL(url)
            val path = urlObj.path
            val filename = path.substring(path.lastIndexOf('/') + 1)

            // Make sure it has a reasonable filename
            if (filename.isNotEmpty() && filename.contains('.')) {
                LogManager.d("extractFilenameFromUrl", "Extracted filename: $filename")
                filename
            } else {
                LogManager.w("extractFilenameFromUrl", "Invalid filename extracted: $filename")
                null
            }
        } catch (e: Exception) {
            LogManager.logCaughtException("extractFilenameFromUrl", "Error extracting filename from: $url", e)
            null
        }
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

    // Also update the showFileFoundToast function to show which variant was found
    /*private fun showFileFoundToast(context: Context, file: File) {
        val sizeKB = file.length() / 1024
        val fileName = file.name

        val message = if (fileName.contains("(") && fileName.contains(")")) {
            "Opening local copy: $fileName (${sizeKB}KB)"
        } else {
            "Opening local copy (${sizeKB}KB)"
        }

        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        LogManager.i("showFileFoundToast", "Showing toast for file: $fileName")
    }*/

    /*private fun tryFallbackUrlOpen(context: Context, uri: Uri) {
        val TAG = "tryFallbackUrlOpen"

        try {
            LogManager.d(TAG, "Trying fallback approach for URI: $uri")

            val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // Remove MIME type in case it was causing issues
                type = null
            }

            context.startActivity(fallbackIntent)
            LogManager.i(TAG, "Fallback approach succeeded for: $uri")

        } catch (e: Exception) {
            LogManager.logCaughtException(TAG, "Fallback approach also failed", e)

            // Last resort - try to open in browser explicitly
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    setPackage("com.android.browser") // Try default browser
                }
                context.startActivity(browserIntent)
                LogManager.i(TAG, "Browser-specific intent succeeded")
            } catch (e2: Exception) {
                LogManager.logCaughtException(TAG, "Even browser-specific intent failed", e2)
                Toast.makeText(
                    context,
                    "No application found to open: $uri",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }*/

    // 5. Improve the getMimeType function:
    /*private fun getMimeType(url: String): String? {
        return try {
            when {
                url.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
                url.endsWith(".txt", ignoreCase = true) -> "text/plain"
                url.endsWith(".doc", ignoreCase = true) -> "application/msword"
                url.endsWith(".docx", ignoreCase = true) -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                url.contains("pdf", ignoreCase = true) -> "application/pdf"
                else -> null
            }
        } catch (e: Exception) {
            LogManager.logCaughtException("getMimeType", "Error determining MIME type for: $url", e)
            null
        }
    }*/

    // Now, create a RAG Data screen component
    @Composable
    fun RagDataScreen(
        chatState: MutableState<ChatState>,
        onClose: () -> Unit
    ) {
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
                            if (!ragFile.url.isNullOrEmpty()) {
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

    // Update your API request to use the selected model
    /*private fun createChatCompletionRequest(prompt: String, selectedFiles: List<FileReference>): ChatCompletionRequest {
        val apiMessage = ApiMessage("user", prompt)

        return ChatCompletionRequest(
            model = appSettings.value.selectedModel, // Use selected model from settings
            messages = listOf(apiMessage),
            chat_id = "d70b00f5-82e1-4070-bcf1-8cce0b9e31ec",
            files = selectedFiles
        )
    }*/

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

    private suspend fun setupTtsWithDebugging() {
        withContext(Dispatchers.Main) {
            try {
                LogManager.i(TAG, "=== Starting TTS Setup and Debugging ===")

                // 1. Test basic TTS functionality
                testBasicTts()

                // 2. Load and debug languages
                loadAndDebugLanguages()

                // 3. Load and debug voices
                loadAndDebugVoices()

                // 4. Apply user settings
                setupTtsWithSettings()

                // 5. Final test
                //finalTtsTest()

                LogManager.i(TAG, "=== TTS Setup Complete ===")

            } catch (e: Exception) {
                LogManager.logCaughtException(TAG, "Error in TTS setup", e)
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

    // Simplified voice loading as fallback
    private fun loadVoicesSimple() {
        availableVoices.clear()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            textToSpeech?.let { tts ->
                try {
                    // Just add a "Default Voice" option if no specific voices found
                    availableVoices.add(
                        TtsVoiceInfo(
                            name = "",
                            displayName = "Default English Voice",
                            locale = Locale.US,
                            quality = android.speech.tts.Voice.QUALITY_NORMAL,
                            isNetworkConnectionRequired = false
                        )
                    )

                    LogManager.i(TAG, "Added default voice option")

                } catch (e: Exception) {
                    LogManager.logCaughtException(TAG, "Error in simple voice loading", e)
                }
            }
        }
    }

    // Improved language loading
    /*private fun loadAvailableLanguages() {
        availableLanguages.clear()

        textToSpeech?.let { tts ->
            try {
                LogManager.d(TAG, "Loading available languages...")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    // Use newer API
                    val locales = tts.availableLanguages
                    LogManager.d(TAG, "Available locales from TTS: ${locales?.size ?: 0}")

                    locales?.forEach { locale ->
                        LogManager.d(TAG, "Found locale: ${locale} (${locale.displayName})")
                        availableLanguages.add(
                            TtsLanguageInfo(
                                locale = locale,
                                displayName = "${locale.displayName} (${locale})",
                                isAvailable = true
                            )
                        )
                    }
                } else {
                    // Use legacy method - test common English locales
                    val englishLocales = listOf(
                        Locale.US,
                        Locale.UK,
                        Locale.CANADA,
                        Locale("en", "AU"), // Australia
                        Locale("en", "IN"), // India
                        Locale("en", "ZA")  // South Africa
                    )

                    englishLocales.forEach { locale ->
                        val result = tts.isLanguageAvailable(locale)
                        LogManager.d(TAG, "Testing locale $locale: result = $result")

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

                // Sort by display name
                availableLanguages.sortBy { it.displayName }
                LogManager.i(TAG, "Loaded ${availableLanguages.size} TTS languages")

                // Log all available languages for debugging
                availableLanguages.forEach { langInfo ->
                    LogManager.d(TAG, "Available language: ${langInfo.displayName}")
                }

            } catch (e: Exception) {
                LogManager.logCaughtException(TAG, "Error loading TTS languages", e)
            }
        }
    }*/

    // Improved voice loading - filter for English voices
    private fun loadAvailableVoices() {
        availableVoices.clear()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            textToSpeech?.let { tts ->
                try {
                    LogManager.d(TAG, "Loading available voices...")

                    val allVoices = tts.voices
                    LogManager.d(TAG, "Total voices available: ${allVoices?.size ?: 0}")

                    allVoices?.forEach { voice ->
                        LogManager.d(TAG, "Found voice: ${voice.name} - ${voice.locale} - Quality: ${voice.quality}")

                        // Filter for English voices only
                        if (voice.locale.language == "en") {
                            val qualityText = when (voice.quality) {
                                android.speech.tts.Voice.QUALITY_VERY_HIGH -> "Very High"
                                android.speech.tts.Voice.QUALITY_HIGH -> "High"
                                android.speech.tts.Voice.QUALITY_NORMAL -> "Normal"
                                android.speech.tts.Voice.QUALITY_LOW -> "Low"
                                android.speech.tts.Voice.QUALITY_VERY_LOW -> "Very Low"
                                else -> "Unknown"
                            }

                            val networkText = if (voice.isNetworkConnectionRequired) " (Network)" else ""

                            availableVoices.add(
                                TtsVoiceInfo(
                                    name = voice.name,
                                    displayName = "${voice.locale.displayName} - ${voice.name} - $qualityText$networkText",
                                    locale = voice.locale,
                                    quality = voice.quality,
                                    isNetworkConnectionRequired = voice.isNetworkConnectionRequired
                                )
                            )

                            LogManager.d(TAG, "Added English voice: ${voice.name} for ${voice.locale}")
                        }
                    }

                    // Sort by display name
                    availableVoices.sortBy { it.displayName }
                    LogManager.i(TAG, "Loaded ${availableVoices.size} English TTS voices")

                    // If no English voices found, log a warning
                    if (availableVoices.isEmpty()) {
                        LogManager.w(TAG, "No English voices found! User may need to install English TTS data.")

                        // Log all available voices for debugging
                        allVoices?.forEach { voice ->
                            LogManager.w(TAG, "Available voice (non-English): ${voice.name} - ${voice.locale}")
                        }
                    }

                } catch (e: Exception) {
                    LogManager.logCaughtException(TAG, "Error loading TTS voices", e)
                }
            }
        } else {
            LogManager.i(TAG, "Voice selection not available on Android < 5.0")
        }
    }

    // Add this improved voice loading with better filtering
    private fun loadAvailableVoicesImproved() {
        availableVoices.clear()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            textToSpeech?.let { tts ->
                try {
                    LogManager.i(TAG, "Loading available voices (improved)...")

                    val allVoices = tts.voices
                    LogManager.i(TAG, "Total voices available: ${allVoices?.size ?: 0}")

                    if (allVoices == null || allVoices.isEmpty()) {
                        LogManager.w(TAG, "No voices available from TTS engine")
                        return
                    }

                    // Get current language setting to prioritize matching voices
                    val currentLanguage = parseLocaleString(appSettings.value.ttsLanguage)
                    LogManager.i(TAG, "Current language setting: $currentLanguage")

                    allVoices.filter { voice ->
                        // Filter for English voices
                        voice.locale.language.equals("en", ignoreCase = true)
                    }.sortedWith(compareBy<android.speech.tts.Voice> { voice ->
                        // Sort: matching language/country first, then by quality (higher first), then by name
                        when {
                            voice.locale == currentLanguage -> 0
                            voice.locale.language == currentLanguage.language -> 1
                            else -> 2
                        }
                    }.thenBy { -it.quality }.thenBy { it.name }).forEach { voice ->

                        val qualityText = when (voice.quality) {
                            android.speech.tts.Voice.QUALITY_VERY_HIGH -> "★★★★★"
                            android.speech.tts.Voice.QUALITY_HIGH -> "★★★★"
                            android.speech.tts.Voice.QUALITY_NORMAL -> "★★★"
                            android.speech.tts.Voice.QUALITY_LOW -> "★★"
                            android.speech.tts.Voice.QUALITY_VERY_LOW -> "★"
                            else -> "?"
                        }

                        val networkText = if (voice.isNetworkConnectionRequired) " 🌐" else " 📱"
                        val matchText = if (voice.locale == currentLanguage) " ✓" else ""

                        // Create a more readable display name
                        val voiceName = voice.name.split("#").lastOrNull()?.let {
                            it.replace("_", " ").replace("-", " ")
                        } ?: voice.name

                        availableVoices.add(
                            TtsVoiceInfo(
                                name = voice.name,
                                displayName = "${voice.locale.displayName}$matchText - $voiceName $qualityText$networkText",
                                locale = voice.locale,
                                quality = voice.quality,
                                isNetworkConnectionRequired = voice.isNetworkConnectionRequired
                            )
                        )

                        LogManager.d(TAG, "Added voice: ${voice.name} (${voice.locale}) Quality: ${voice.quality}")
                    }

                    LogManager.i(TAG, "Loaded ${availableVoices.size} English voices")

                    if (availableVoices.isEmpty()) {
                        LogManager.w(TAG, "No English voices found!")
                        // Log all available voices for debugging
                        allVoices.take(10).forEach { voice ->
                            LogManager.w(TAG, "Available voice: ${voice.name} (${voice.locale.language}-${voice.locale.country})")
                        }
                    }

                } catch (e: Exception) {
                    LogManager.logCaughtException(TAG, "Error loading voices (improved)", e)
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

    private fun formatLocaleString(locale: Locale): String {
        return if (locale.country.isNotEmpty()) {
            "${locale.language}-${locale.country}"
        } else {
            locale.language
        }
    }

    // Add a function to check and install TTS data if needed
    private fun checkTtsDataAvailability() {
        textToSpeech?.let { tts ->
            // Check if English is available
            val result = tts.isLanguageAvailable(Locale.US)

            when (result) {
                TextToSpeech.LANG_MISSING_DATA -> {
                    LogManager.w(TAG, "TTS data missing - user should install English TTS data")
                    // You could show a dialog here to prompt user to install TTS data
                    showTtsDataMissingDialog()
                }
                TextToSpeech.LANG_NOT_SUPPORTED -> {
                    LogManager.w(TAG, "English TTS not supported on this device")
                }
                else -> {
                    LogManager.i(TAG, "English TTS is available")
                }
            }
        }
    }

    private fun showTtsDataMissingDialog() {
        // You can implement this to show a dialog to the user
        LogManager.i(TAG, "Consider showing dialog to install TTS data")
    }

    // Also add a function to force reload voices when settings screen is opened:
    // In MainActivity, add this to be called when settings screen opens:
    private fun refreshTtsData() {
        coroutineScope.launch {
            if (isTtsInitialized.value) {
                LogManager.i(TAG, "Refreshing TTS data for settings")
                loadAndDebugLanguages()
                loadAndDebugVoices()
            }
        }
    }

    // Add this function to help debug voice selection
    private fun debugCurrentTtsSettings() {
        textToSpeech?.let { tts ->
            try {
                LogManager.i(TAG, "=== Current TTS Settings Debug ===")

                // Current language
                val currentLanguage = tts.language
                LogManager.i(TAG, "Current TTS Language: $currentLanguage")

                // Current voice (API 21+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val currentVoice = tts.voice
                    if (currentVoice != null) {
                        LogManager.i(TAG, "Current TTS Voice: ${currentVoice.name}")
                        LogManager.i(TAG, "Voice Locale: ${currentVoice.locale}")
                        LogManager.i(TAG, "Voice Quality: ${currentVoice.quality}")
                    } else {
                        LogManager.w(TAG, "Current TTS Voice: null")
                    }
                }

                // Settings from app
                val settings = appSettings.value
                LogManager.i(TAG, "App Settings - Language: ${settings.ttsLanguage}")
                LogManager.i(TAG, "App Settings - Voice: '${settings.ttsVoice}'")
                LogManager.i(TAG, "App Settings - Speech Rate: ${settings.ttsSpeechRate}")
                LogManager.i(TAG, "App Settings - Pitch: ${settings.ttsPitch}")

            } catch (e: Exception) {
                LogManager.logCaughtException(TAG, "Error in TTS debug", e)
            }
        }
    }

    // Fix 1: Make TTS data loading non-blocking
    private fun refreshTtsDataNonBlocking() {
        coroutineScope.launch(Dispatchers.IO) { // Use IO dispatcher for heavy work
            try {
                LogManager.i(TAG, "Starting non-blocking TTS data refresh...")

                if (!isTtsInitialized.value) {
                    LogManager.w(TAG, "TTS not initialized, skipping refresh")
                    return@launch
                }

                // Load languages in background
                withContext(Dispatchers.Main) {
                    loadLanguagesQuickly()
                }

                // Small delay to let UI update
                delay(100)

                // Load voices in background
                withContext(Dispatchers.Main) {
                    loadVoicesQuickly()
                }

                LogManager.i(TAG, "TTS data refresh completed")

            } catch (e: Exception) {
                LogManager.logCaughtException(TAG, "Error in non-blocking TTS refresh", e)
            }
        }

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
        RagFile("89c8e301-744e-455a-9d9c-0ec905869bc1", "Plastic Injection Molding Processing Technician Guide", "https://sparkonelabs.com/RAG_pdfs/Processing_Troubleshooting_Guide.html"),
        RagFile("9c4a27f2-509f-4856-838a-954d20f0098a", "Table of Workcells, Robots, Presses and HMI units", "https://sparkonelabs.com/RAG_pdfs/Molding_Layout.html"),
        RagFile("abf471b1-55cd-41b4-b8f0-46211cf978b1", "Plastic Technician's Toolbox Volume 1 - Math", "https://sparkonelabs.com/RAG_pdfs/18036_01.pdf"),
        RagFile("a17ecf98-5c0d-4208-8de2-03b2d68c8c37", "Plastic Technician's Toolbox Volume 2 - Safety", "https://sparkonelabs.com/RAG_pdfs/18036_02.pdf"),
        RagFile("cf989978-e6e9-48fb-b5b4-6d8f9758e623", "Plastic Technician's Toolbox Volume 3 - Glossary", "https://sparkonelabs.com/RAG_pdfs/18036_03.pdf"),
        RagFile("b40db982-31c1-4509-a83e-12721414d0b9", "Plastic Technician's Toolbox Volume 4A - Clamp End", "https://sparkonelabs.com/RAG_pdfs/18036_04a.pdf"),
        RagFile("827d3a3c-88a4-402f-9fbd-75b3870a0483", "Plastic Technician's Toolbox Volume 4B - Auxiliary Equipment", "https://sparkonelabs.com/RAG_pdfs/18036_04b.pdf"),
        RagFile("44d95954-ec6b-4014-8f7c-75756f8f60c0", "Plastic Technician's Toolbox Volume 5A - Part Design", "https://sparkonelabs.com/RAG_pdfs/18036_05a.pdf"),
        RagFile("69c94350-76cf-4cdf-9470-073335650543", "Plastic Technician's Toolbox Volume 5B - Mold Base Standard Components", "https://sparkonelabs.com/RAG_pdfs/18036_05b.pdf"),
        RagFile("efb987a5-60b0-47a7-bc3b-76acbb0120c7", "Plastic Technician's Toolbox Volume 5C - Mold Design", "https://sparkonelabs.com/RAG_pdfs/18036_05c.pdf"),
        RagFile("73471eb3-5f11-43e3-887e-6c2b3b6b007b", "Plastic Technician's Toolbox Volume 5D - Runners", "https://sparkonelabs.com/RAG_pdfs/18036_05d.pdf"),
        RagFile("1e280199-160d-4b4d-a246-20d3cc57a504", "Plastic Technician's Toolbox Volume 5E - Hot Runner Systems", "https://sparkonelabs.com/RAG_pdfs/18036_05e.pdf"),
        RagFile("92f5d18e-94a9-4de0-84cb-b98e033a5d17", "Plastic Technician's Toolbox Volume 5F - Ejection", "https://sparkonelabs.com/RAG_pdfs/18036_05f.pdf"),
        RagFile("48a8a134-2797-4d04-9c51-10351e03ac61", "Plastic Technician's Toolbox Volume 5G - Dealing with Undercuts", "https://sparkonelabs.com/RAG_pdfs/18036_05g.pdf"),
        RagFile("b869f50c-2883-4ccf-b694-fd583f906985", "Plastic Technician's Toolbox Volume 6A - Plastic Flow", "https://sparkonelabs.com/RAG_pdfs/18036_06a.pdf"),
        RagFile("9c563182-4ae6-4ecd-80c8-8c514c069e65", "Plastic Technician's Toolbox Volume 6B - Optimizing the Molding Process", "https://sparkonelabs.com/RAG_pdfs/18036_06b.pdf"),
        RagFile("a440c0b0-11a1-49d9-950a-0f9a46a1576c", "Plastic Technician's Toolbox Volume 6C - Tips for Supervisors and Technicians", "https://sparkonelabs.com/RAG_pdfs/18036_06c.pdf"),
        RagFile("5391b346-d64a-4507-ae73-7a25c50767a3", "Plastic Technician's Toolbox Volume 6D - Computer Flow Simulations", "https://sparkonelabs.com/RAG_pdfs/18036_06d.pdf"),
        RagFile("0a91203f-1216-49d5-9b95-229583e0a787", "Plastic Technician's Toolbox Volume 6E - The MuCell(R) Process", "https://sparkonelabs.com/RAG_pdfs/18036_06e.pdf"),
        RagFile("03ea07c4-6c3e-47c8-9f51-0e6489ae3189", "Plastic Technician's Toolbox Volume 6F - Troubleshooting", "https://sparkonelabs.com/RAG_pdfs/18036_06f.pdf"),
        RagFile("664cd351-e0a3-42b3-8429-ba0bedbc5501", "FANUC R-30iA and R-30iB Controller KAREL Reference Manual", "https://sparkonelabs.com/RAG_pdfs/Fanuc_R-30iA_and_R-30iB.pdf"),
        RagFile("f4539ec4-0e85-4614-9c2f-7dba282c5be9", "FANUC Series 0i, 16, 18, 20, 21 Macro Compiler/Executor Programming Manual", "https://sparkonelabs.com/RAG_pdfs/Fanuc_Programming_Manual.pdf"),
        RagFile("c1efe9b2-29ee-4553-9c86-57c87b3f7c5f", "FANUC R-30iB / R-30iB Mate Plus Controller Maintenance Manual", "https://sparkonelabs.com/RAG_pdfs/FANUC_R-30iB_and_R-30iB_Mate_Plus_Controller_Maintenance_Manual.pdf"),
        RagFile("2c18abc6-14ee-4ac1-94c9-f2e40fe26508", "FANUC I/O Unit-MODEL A: Connection and Maintenance Manual", "https://sparkonelabs.com/RAG_pdfs/FANUC_IO_Unit_Model_Connection_and_Maintenance_Manual.pdf"),
        RagFile("37a65837-c584-451c-aa7f-ef97613e0a60", "FANUC Robot Series R-30iB/R-30iB Plus Controller Maintenance Manual", "https://sparkonelabs.com/RAG_pdfs/FANUC_R-30iB_and_R-30iB_Plus_Controller_Maintenance_Manual.pdf"),
        RagFile("d25909ee-6d11-4b4d-99ec-4a606545a31d", "FANUC R-30iB Plus and R-30iB Mate Plus Controller Software Error Code Manual", "https://sparkonelabs.com/RAG_pdfs/FANUC_R-30iB_Plus_and_R-30iB_Mate_Plus_Controller_Software_Error_Code_Manual.pdf"),
        RagFile("d770ad73-dfa9-4723-96ce-2a83b6fd3be8","FANUC Robot M-20iB Mechanical Unit Operator's Manual", "https://sparkonelabs.com/RAG_pdfs/FANUC_Robot_M-20iB_Mechanical_Unit_Operators_Manual.pdf"),
        RagFile("df8ccc7e-f647-4088-b45c-46924df6f77c", "FANUC Robot M-710iC /50/70/50H/50S/45M/50E Mechanical Unit Operator's Manual", "https://sparkonelabs.com/RAG_pdfs/FANUC_Robot_M-710iC_50_70_50H_50S_45M_50E_Mechanical_Unit_Operators_Manual.pdf"),
        RagFile("7f6766d2-cbda-4cf2-9a0a-e01fac414036", "FANUC Robot R-2000iB Mechanical Unit Operator's Manual", "https://sparkonelabs.com/RAG_pdfs/FANUC_Robot_R-2000iB_Mechanical_Unit_Operators_Manual.pdf"),
        RagFile("af572edb-5de5-4d95-b8c8-ff6909839fcd", "FANUC Robot R-2000iC Mechanical Unit Operator's Manual", "https://sparkonelabs.com/RAG_pdfs/FANUC_Robot_R-2000iC_Mechanical_Unit_Operators_Manual.pdf"),
        RagFile("4eec512d-76dd-474f-b6b4-702ebb7155fa", "eDart Process Control Software v10.xx Manual (2017)", "https://sparkonelabs.com/RAG_pdfs/eDART_Process_Control_Software_v10.xx_Manual_06.23.2017.pdf"),
        RagFile("a7018213-554e-4e67-ae92-a63d94c24c86", "RJG eDart Getting Started Manual", "https://sparkonelabs.com/RAG_pdfs/RJG_eDart_getting_started.pdf")
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