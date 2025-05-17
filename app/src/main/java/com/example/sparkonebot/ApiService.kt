package com.example.oxfordbot

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Headers
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import com.google.gson.annotations.SerializedName

interface ApiService {
    @Headers(
        "Content-Type: application/json",
        "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6IjQ3OGYyZTI3LWZhMzEtNDg3NC1iMzYzLTZiZDEwNDk5YTc4NSJ9.txr_8YDWdnou_Hc2C9Mta_PHdHLQ4qY11iMdUjLD5-8"
    )
    @POST("/api/chat/completions")
    suspend fun generateResponse(@Body request: ChatCompletionRequest): ApiResponse

    companion object {
        private const val BASE_URL = "http://$SparkOneBrain:8080/"

        fun create(): ApiService {
            val client = OkHttpClient.Builder()
                .connectTimeout(600, TimeUnit.SECONDS)
                .readTimeout(600, TimeUnit.SECONDS)
                .writeTimeout(600, TimeUnit.SECONDS)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build()
            return retrofit.create(ApiService::class.java)
        }
    }
}

data class ApiRequest(
    val query: String
)

//data class ApiRequest(
//    // Change the structure to match OpenAI API format
//    val model: String = "qwen3:8b",
//    val messages: List<ApiMessage> = listOf(),
//
//    // Keep the 'query' field for backwards compatibility with your code
//    val query: String = ""
//) {
//    constructor(query: String) : this(
//        model = "qwen3:8b",
//        messages = listOf(ApiMessage("user", query))
//    )
//}

// New request class that matches the expected format
data class ChatCompletionRequest(
    val model: String = "qwen3:8b",
    val messages: List<ApiMessage> = listOf(),
    val chat_id: String? = "d70b00f5-82e1-4070-bcf1-8cce0b9e31ec", // Add chat_id field
    val files: List<FileReference>? = null // Add files array field
)

data class ApiMessage(
    val role: String,
    val content: String
)

data class ApiResponse(
    val id: String? = null,
    val created: Long? = null,
    val model: String? = null,
    val choices: List<Choice>? = null,
    val status: String? = null,
    @SerializedName("response")
    private val _response: Any? = null
) {
    val response: String
        get() = when {
            choices != null && choices.isNotEmpty() ->
                choices[0].message?.content ?: "No content in response"
            _response is String -> _response
            _response is Map<*, *> -> _response["response"] as? String ?: _response.toString()
            else -> _response?.toString() ?: "No response"
        }
}

data class Choice(
    val index: Int? = null,
    val message: MessageResponse? = null,
    val finish_reason: String? = null
)

data class MessageResponse(
    val role: String? = null,
    val content: String? = null
)

// LlamaResponse can remain unchanged
data class LlamaResponse(
    val model: String,
    val created_at: String,
    val response: String,
    val done: Boolean,
    val done_reason: String,
    val context: List<Int>,
    val total_duration: Long,
    val load_duration: Long,
    val prompt_eval_count: Int,
    val prompt_eval_duration: Long,
    val eval_count: Int,
    val eval_duration: Long
)

// Note: This is added to test RAG extension of API message requests
// Add a new data class for file references
data class FileReference(
    val type: String = "file",
    val id: String
)