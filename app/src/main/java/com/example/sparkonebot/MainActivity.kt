package com.example.oxfordbot

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.material.Badge
import androidx.compose.material.BadgedBox
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
import com.google.gson.JsonSyntaxException
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonElement
import com.google.gson.stream.JsonReader

// Global Variables
val MyAppIcons = Icons.Rounded
val hostReachable = mutableStateOf(false)
//const val SparkOneBrain: String = "151.213.208.190"
//const val SparkOneBrain: String = "76.35.172.143"
const val SparkOneBrain: String = "24.26.41.112"
const val SparkOneBrainLocal: String = "192.168.254.131"

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"
    private val apiService = ApiService.create()
    private val coroutineScope = MainScope()
    private val chatState = mutableStateOf(ChatState())
    private var textToSpeech: TextToSpeech? = null
    private val isIntroAnimationFinished = mutableStateOf(false)

    companion object {
        private const val SPEECH_REQUEST_CODE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable("chatState", chatState.value)
        outState.putBoolean("isIntroAnimationFinished", isIntroAnimationFinished.value)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        val savedChatState = savedInstanceState.getSerializable("chatState") as? ChatState
        if (savedChatState != null) {
            chatState.value = savedChatState
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == SPEECH_REQUEST_CODE && resultCode == RESULT_OK) {
            val spokenText = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
            chatState.value = chatState.value.copy(inputText = spokenText ?: "")
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
        Log.d(TAG, "Raw API response: ${response.response}")

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

            Log.d(TAG, "Full processed message content: $messageContent")

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
                Log.e(TAG, "Received empty message content")
                handleUnexpectedResponse("Empty response received")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing response: ${e.message}")
            Log.e(TAG, "Stack trace: ${Log.getStackTraceString(e)}")
            // Use the raw response if JSON parsing fails
            var messageContent = response.response.trim()

            // Extract only the part after </think> if it exists
            if (messageContent.contains("</think>")) {
                val parts = messageContent.split("</think>", limit = 2)
                if (parts.size > 1) {
                    messageContent = parts[1].trim()
                }
            }

            Log.d(TAG, "Full raw message content: $messageContent")

            // Process the message content to add citations
            val processedContent = processResponseWithCitations(messageContent)

            val message = Message("assistant", processedContent)
            chatState.value = chatState.value.copy(
                messages = chatState.value.messages + message,
                isAnimationVisible = false
            )
            speak(messageContent) // Not adding citations to speech
        }
    }

    private fun handleUnexpectedResponse(responseString: String) {
        Log.w(TAG, "Unexpected response: $responseString")
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

        val errorHandler = CoroutineExceptionHandler { _, exception ->
            Log.e(TAG, "Coroutine exception: ${exception.message}")
            Log.e(TAG, "Stack trace: ${Log.getStackTraceString(exception)}")
            val errorMessage = Message(
                role = "system",
                content = "An error occurred: ${exception.message}\n\nStack trace: ${Log.getStackTraceString(exception)}"
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
                    if (showRagDataScreen.value) {
                        RagDataScreen(
                            chatState = chatState,
                            onClose = {
                                showRagDataScreen.value = false
                            }
                        )
                    } else if (!isIntroAnimationFinished.value && configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                        IntroScreen(
                            onAnimationFinished = {
                                isIntroAnimationFinished.value = true
                            }
                        )
                    } else {
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

                                            Log.d(TAG, "Sending API request: $chatCompletionRequest")
                                            val response = apiService.generateResponse(chatCompletionRequest)
                                            Log.d(TAG, "Received API response: $response")
                                            handleApiResponse(response)
                                        } catch (e: Exception) {
                                            when (e) {
                                                is SocketTimeoutException -> {
                                                    Log.e(TAG, "Socket timeout during API call: ${e.message}")
                                                    val errorMessage = Message(
                                                        role = "system",
                                                        content = "The server took too long to respond. Please try again later."
                                                    )
                                                    chatState.value = chatState.value.copy(
                                                        messages = chatState.value.messages + errorMessage
                                                    )
                                                }
                                                else -> {
                                                    Log.e(TAG, "Error during API call: ${e.message}")
                                                    Log.e(TAG, "Stack trace: ${Log.getStackTraceString(e)}")
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

    // Now, update the DrawerContent function to include RAG Data option
    @Composable
    fun DrawerContent(
        chatState: MutableState<ChatState>,
        onClose: () -> Unit,
        onRagDataClick: () -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .background(Navy)
                .padding(top = 32.dp)
        ) {
            // App name/title
            Text(
                text = "Techno-Bot",
                color = Gold,
                style = MaterialTheme.typography.h6,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )

            Divider(color = LightBlue, thickness = 1.dp)

            // Menu items
            MenuItem("Models", onClose)
            MenuItem("Log", onClose)

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
                            .selectable(
                                selected = false,
                                onClick = {},
                                indication = rememberRipple(bounded = true, color = LightBlue),
                                interactionSource = remember { MutableInteractionSource() }
                            )
                            .background(Navy),
                        softWrap = true
                    )
                }
            }
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

        // Get only the selected RAG files - THIS IS THE KEY CHANGE
        val selectedRagFiles = this.chatState.value.ragFiles.filter { it.isSelected }

        citations.sorted().forEach { citationNumber ->
            // Convert citation number to zero-based index
            val index = citationNumber - 1

            // Make sure index is valid within selected files
            if (index >= 0 && index < selectedRagFiles.size) {
                val ragFile = selectedRagFiles[index] // Use selectedRagFiles instead of all ragFiles
                citationsBuilder.append("[${citationNumber}] ${ragFile.displayName}\n")
            } else {
                citationsBuilder.append("[${citationNumber}] Unknown reference\n")
            }
        }

        return responseText + citationsBuilder.toString()
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
        RagFile("89c8e301-744e-455a-9d9c-0ec905869bc1", "Plastic Injection Molding Processing Technician Guide"),
        RagFile("abf471b1-55cd-41b4-b8f0-46211cf978b1", "Plastic Technician's Toolbox Volume 1 - Math"),
        RagFile("a17ecf98-5c0d-4208-8de2-03b2d68c8c37", "Plastic Technician's Toolbox Volume 2 - Safety"),
        RagFile("cf989978-e6e9-48fb-b5b4-6d8f9758e623", "Plastic Technician's Toolbox Volume 3 - Glossary"),
        RagFile("b40db982-31c1-4509-a83e-12721414d0b9", "Plastic Technician's Toolbox Volume 4A - Clamp End"),
        RagFile("827d3a3c-88a4-402f-9fbd-75b3870a0483", "Plastic Technician's Toolbox Volume 4B - Auxiliary Equipment"),
        RagFile("44d95954-ec6b-4014-8f7c-75756f8f60c0", "Plastic Technician's Toolbox Volume 5A - Part Design"),
        RagFile("69c94350-76cf-4cdf-9470-073335650543", "Plastic Technician's Toolbox Volume 5B - Mold Base Standard Components"),
        RagFile("efb987a5-60b0-47a7-bc3b-76acbb0120c7", "Plastic Technician's Toolbox Volume 5C - Mold Design"),
        RagFile("73471eb3-5f11-43e3-887e-6c2b3b6b007b", "Plastic Technician's Toolbox Volume 5D - Runners"),
        RagFile("1e280199-160d-4b4d-a246-20d3cc57a504", "Plastic Technician's Toolbox Volume 5E - Hot Runner Systems"),
        RagFile("92f5d18e-94a9-4de0-84cb-b98e033a5d17", "Plastic Technician's Toolbox Volume 5F - Ejection"),
        RagFile("48a8a134-2797-4d04-9c51-10351e03ac61", "Plastic Technician's Toolbox Volume 5G - Dealing with Undercuts"),
        RagFile("b869f50c-2883-4ccf-b694-fd583f906985", "Plastic Technician's Toolbox Volume 6A - Plastic Flow"),
        RagFile("9c563182-4ae6-4ecd-80c8-8c514c069e65", "Plastic Technician's Toolbox Volume 6B - Optimizing the Molding Process"),
        RagFile("a440c0b0-11a1-49d9-950a-0f9a46a1576c", "Plastic Technician's Toolbox Volume 6C - Tips for Supervisors and Technicians"),
        RagFile("5391b346-d64a-4507-ae73-7a25c50767a3", "Plastic Technician's Toolbox Volume 6D - Computer Flow Simulations"),
        RagFile("0a91203f-1216-49d5-9b95-229583e0a787", "Plastic Technician's Toolbox Volume 6E - The MuCell(R) Process"),
        RagFile("03ea07c4-6c3e-47c8-9f51-0e6489ae3189", "Plastic Technician's Toolbox Volume 6F - Troubleshooting"),
        RagFile("664cd351-e0a3-42b3-8429-ba0bedbc5501", "FANUC R-30iA and R-30iB Controller KAREL Reference Manual"),
        RagFile("f4539ec4-0e85-4614-9c2f-7dba282c5be9", "FANUC Series 0i, 16, 18, 20, 21 Macro Compiler/Executor Programming Manual"),
        RagFile( "c1efe9b2-29ee-4553-9c86-57c87b3f7c5f", "FANUC R-30iB / R-30iB Mate Plus Controller Maintenance Manual"),
        RagFile( "2c18abc6-14ee-4ac1-94c9-f2e40fe26508", "FANUC I/O Unit-MODEL A: Connection and Maintenance Manual"),
        RagFile( "37a65837-c584-451c-aa7f-ef97613e0a60", "FANUC Robot Series R-30iB/R-30iB Plus Controller Maintenance Manual"),
        RagFile( "d25909ee-6d11-4b4d-99ec-4a606545a31d", "FANUC R-30iB Plus and R-30iB Mate Plus Controller Software Error Code Manual"),
        RagFile("d770ad73-dfa9-4723-96ce-2a83b6fd3be8","FANUC Robot M-20iB Mechanical Unit Operator's Manual"),
        RagFile( "df8ccc7e-f647-4088-b45c-46924df6f77c", "FANUC Robot M-710iC /50/70/50H/50S/45M/50E Mechanical Unit Operator's Manual"),
        RagFile( "7f6766d2-cbda-4cf2-9a0a-e01fac414036", "FANUC Robot R-2000iB Mechanical Unit Operator's Manual"),
        RagFile( "af572edb-5de5-4d95-b8c8-ff6909839fcd", "FANUC Robot R-2000iC Mechanical Unit Operator's Manual")
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

data class PingResult(
    val isReachable: Boolean,
    val responseTime: Long? = null,
    val errorMessage: String? = null
)

data class RagFile(
    val id: String,
    val displayName: String,
    var isSelected: Boolean = false
)