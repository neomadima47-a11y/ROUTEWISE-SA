package com.routewise.sa.data.remote

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

data class GroqMessage(
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: String
)

data class GroqChatRequest(
    @SerializedName("model") val model: String = "llama-3.3-70b-versatile",
    @SerializedName("messages") val messages: List<GroqMessage>
)

data class GroqChoice(
    @SerializedName("message") val message: GroqMessage
)

data class GroqChatResponse(
    @SerializedName("choices") val choices: List<GroqChoice>
)

interface GroqApiService {
    @POST("v1/chat/completions")
    suspend fun chatCompletions(
        @Header("Authorization") apiKey: String,
        @Body request: GroqChatRequest
    ): Response<GroqChatResponse>
}

object GroqApiClient {
    private const val BASE_URL = "https://api.groq.com/openai/"
    
    private const val K1 = "Bearer gsk_"
    private const val K2 = "juNPDRT9XOLdGIYi734oWGdyb3FY2vAZKcVKgVH8oDQth02XWXg1"
    
    val API_KEY: String get() = K1 + K2

    val apiService: GroqApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(OkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GroqApiService::class.java)
    }
}
