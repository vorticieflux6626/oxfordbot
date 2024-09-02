package com.example.oxfordbot

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

interface ApiService {
    @POST("/query")
    suspend fun generateResponse(@Body request: ApiRequest): ApiResponse

    companion object {
        private const val BASE_URL = "http://$SparkOneBrain:5000/"

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

data class ApiResponse(
    val response: String
)

// Helper class to parse the nested JSON response
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