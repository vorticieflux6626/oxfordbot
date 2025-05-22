package com.example.oxfordbot

import android.annotation.SuppressLint
import android.content.Intent
import android.content.Context
import android.content.res.Configuration
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.material.Badge
//import androidx.compose.material.BadgedBox
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ButtonDefaults
import com.example.oxfordbot.ui.theme.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CoroutineExceptionHandler
import java.io.IOException
import java.io.Serializable
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.*
import java.io.StringReader
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.stream.JsonReader
import com.google.gson.reflect.TypeToken
import androidx.core.content.edit

// Global Variables
val MyAppIcons = Icons.Rounded
val hostReachable = mutableStateOf(false)
const val SparkOneBrain: String = "24.26.41.112"
const val SparkOneBrainLocal: String = "192.168.254.131"

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"
    private val apiService = ApiService.create()
    private val coroutineScope = MainScope()
    private val chatState = mutableStateOf(ChatState())
    private var textToSpeech: TextToSpeech? = null
    private val isIntroAnimationFinished = mutableStateOf(false)
    private lateinit var sharedPreferences: SharedPreferences
    //private val gson = Gson()
    private val gson = GsonBuilder()
        .serializeNulls() // Include null fields
        .create()

    companion object {
        private const val SPEECH_REQUEST_CODE = 1
        private const val PREF_NAME = "OxfordBotPrefs"
        private const val KEY_CHAT_STATE = "chat_state"
        private const val KEY_INTRO_FINISHED = "intro_finished"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize LogManager with crash handling - ADD THIS LINE
        LogManager.initialize(this)

        // Log app startup
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            LogManager.i(TAG, "App started - version: ${packageInfo.versionName}")
        } catch (e: Exception) {
            LogManager.i(TAG, "App started - version: unknown")
        }

        // Test line to clear old JSON serial data
        // Initialize SharedPreferences (Keep state after home/standby)
        sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        // Add this line at the start of onCreate() - TEMPORARY FOR TESTING
        //sharedPreferences.edit().clear().apply()
        //LogManager.i(TAG, "SharedPreferences cleared for testing")

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
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.US
            }
        })

        if (savedInstanceState != null) {
            isIntroAnimationFinished.value = savedInstanceState.getBoolean("isIntroAnimationFinished", false)
        }

        setContent {
            oxfordbotTheme {
                MainScreen(chatState, apiService, coroutineScope, isIntroAnimationFinished)
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
                isAnimationVisible = false, // Don't persist animation state
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
            sharedPreferences.edit()
                .putString(KEY_CHAT_STATE, chatStateJson)
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
    // Override for future compatibility with onSaveInstanceState
    // Keep the original Bundle-based state saving
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

                    // Extract only the part after </think> if it exists
                    if (messageContent.contains("</think>")) {
                        val parts = messageContent.split("</think>", limit = 2)
                        if (parts.size > 1) {
                            messageContent = parts[1].trim()
                        }
                    }

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
                if (messageContent.contains("</think>")) {
                    val parts = messageContent.split("</think>", limit = 2)
                    if (parts.size > 1) {
                        messageContent = parts[1].trim()
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

    private fun speak(text: String) {
        val utteranceId = UUID.randomUUID().toString()
        textToSpeech?.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
    }

    private fun pingHost(host: String): Boolean {
        return try {
            val inetAddress = InetAddress.getByName(host)
            inetAddress.isReachable(30000)
        } catch (e: IOException) {
            false
        }
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
        coroutineScope: CoroutineScope,
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

                        showRagDataScreen.value -> {
                            RagDataScreen(
                                chatState = chatState,
                                onClose = {
                                    showRagDataScreen.value = false
                                }
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
                                    coroutineScope.launch(errorHandler) {
                                        hostReachable.value = pingHostAsync(SparkOneBrain)
                                        if (hostReachable.value) {
                                            try {
                                                // Create a chat completion request with selected file references
                                                val apiMessage = ApiMessage("user", prompt)

                                                // Only include selected RAG files
                                                val selectedFiles = chatState.value.ragFiles
                                                    .filter { it.isSelected }
                                                    .map { FileReference(id = it.id) }

                                                val chatCompletionRequest = ChatCompletionRequest(
                                                    model = "qwen3:8b",
                                                    messages = listOf(apiMessage),
                                                    chat_id = "d70b00f5-82e1-4070-bcf1-8cce0b9e31ec",
                                                    files = selectedFiles
                                                )

                                                LogManager.d(
                                                    TAG,
                                                    "Sending API request: $chatCompletionRequest"
                                                )
                                                val response = apiService.generateResponse(
                                                    chatCompletionRequest
                                                )
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
        onLogClick: () -> Unit
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
                    .clickable { onClose() }  // Close drawer when clicked
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Techno-Bot",
                    color = Gold,
                    style = MaterialTheme.typography.h6
                )

                // Optional: Add a visual indication that this is clickable
                Text(
                    text = "← Back",
                    color = LightBlue,
                    style = MaterialTheme.typography.caption,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Divider(color = LightBlue, thickness = 1.dp)

            // Menu items
            MenuItem("Models", onClose)

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

                    // Main content
                    SelectionContainer {
                        Text(
                            text = mainContent,
                            color = Gold,
                            modifier = Modifier.fillMaxWidth(),
                            softWrap = true
                        )
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

            // Log for debugging
            LogManager.d(TAG, "Found link: URL=$urlPart, Text=$textPart")

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

    // Replace your openUrlSafely function with this improved version
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

            // Clean and validate URL
            val cleanUrl = urlStr.trim()
            LogManager.d(TAG, "Cleaned URL: '$cleanUrl'")

            // Build proper URI
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

    private fun tryFallbackUrlOpen(context: Context, uri: Uri) {
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
    }

    // 5. Improve the getMimeType function:
    private fun getMimeType(url: String): String? {
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
    }

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
            modifier = Modifier.padding(16.dp)
        )
    }

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

}

private fun Modifier.disableSelection(): Modifier = composed {
    this.pointerInput(Unit) {
        detectTapGestures {
            // Do nothing, effectively disabling selection for this modifier
        }
    }
}

data class Selection(val start: Int, val end: Int)

// Then, modify the ChatState to include our RAG file selections
data class ChatState(
    val messages: List<Message> = emptyList(),
    val inputText: String = "",
    val isAnimationVisible: Boolean = false,
    val ragFiles: List<RagFile> = listOf(
        RagFile("89c8e301-744e-455a-9d9c-0ec905869bc1", "Plastic Injection Molding Processing Technician Guide", "https://sparkonelabs.com/RAG_pdfs/Processing_Troubleshooting_Guide.html"),
        RagFile("d4d7b17b-7397-4af9-b9ee-d6e081dd1196","Table of Workcells Robots, Presses and HMI units", "https://sparkonelabs.com/RAG_pdfs/Molding_Layout.txt"),
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