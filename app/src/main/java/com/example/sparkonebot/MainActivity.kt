package com.example.oxfordbot

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
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
import com.example.oxfordbot.ui.theme.*
import kotlinx.coroutines.*
import java.io.IOException
import java.io.Serializable
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.*
import com.google.gson.Gson

// Global Variables
val MyAppIcons = Icons.Rounded
val hostReachable = mutableStateOf(false)
const val SparkOneBrain: String = "173.184.52.40"

class MainActivity : ComponentActivity() {
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

    private fun handleApiResponse(response: ApiResponse) {
        val gson = Gson()
        val llamaResponse = gson.fromJson(response.response, LlamaResponse::class.java)
        val message = Message("assistant", llamaResponse.response)
        chatState.value = chatState.value.copy(
            messages = chatState.value.messages + message,
            isAnimationVisible = false
        )
        speak(message.content)
    }

    private fun speak(text: String) {
        val utteranceId = UUID.randomUUID().toString()
        textToSpeech?.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
    }

    private fun pingHost(host: String): Boolean {
        return try {
            val inetAddress = InetAddress.getByName(host)
            inetAddress.isReachable(3000000)
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

        ModalDrawer(
            drawerState = drawerState,
            gesturesEnabled = scaffoldState.drawerState.isOpen,
            drawerContent = {
                DrawerContent(
                    onClose = {
                        scope.launch {
                            drawerState.close()
                        }
                    }
                )
            }
        ) {
            Scaffold(
                scaffoldState = scaffoldState,
                topBar = {
                    TopAppBar(
                        title = { Text(text = "Oxford Bot") },
                        navigationIcon = {
                            IconButton(onClick = {
                                scope.launch {
                                    drawerState.open()
                                }
                            }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        }
                    )
                }
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = backgroundColor
                ) {
                    if (!isIntroAnimationFinished.value && configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                        IntroScreen(
                            onAnimationFinished = {
                                isIntroAnimationFinished.value = true
                            }
                        )
                    } else {
                        ChatScreen(
                            chatState = chatState,
                            onSendPrompt = { prompt ->
                                coroutineScope.launch {
                                    hostReachable.value = pingHostAsync(SparkOneBrain)
                                    if (hostReachable.value) {
                                        try {
                                            val apiRequest = ApiRequest(query = prompt)
                                            val response = apiService.generateResponse(apiRequest)
                                            handleApiResponse(response)
                                        } catch (e: SocketTimeoutException) {
                                            val timeoutMessage = Message(
                                                role = "system",
                                                content = "Socket Timeout Exception"
                                            )
                                            chatState.value = chatState.value.copy(
                                                messages = chatState.value.messages + timeoutMessage
                                            )
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

    @Composable
    fun DrawerContent(onClose: () -> Unit) {
        Column {
            Text(
                text = "Models",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clickable {
                        onClose()
                        /* Handle click event for Models */
                    }
            )
            Text(
                text = "Log",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clickable {
                        onClose()
                        /* Handle click event for Log */
                    }
            )
        }
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
        Column(modifier = Modifier.padding(16.dp)) {
            SelectionContainer {
                Row {
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
                            .selectable(
                                selected = false,
                                onClick = {},
                                indication = rememberRipple(bounded = true, color = LightBlue),
                                interactionSource = remember { MutableInteractionSource() }
                            )
                            .background(Navy)
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
}

private fun Modifier.disableSelection(): Modifier = composed {
    this.pointerInput(Unit) {
        detectTapGestures {
            // Do nothing, effectively disabling selection for this modifier
        }
    }
}

data class Selection(val start: Int, val end: Int)

data class ChatState(
    val messages: List<Message> = emptyList(),
    val inputText: String = "",
    val isAnimationVisible: Boolean = false // Add isAnimationVisible property
) : Serializable

data class Message(
    val role: String,
    val content: String,
    val id: String = ""
) : Serializable