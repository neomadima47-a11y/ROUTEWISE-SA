package com.routewise.sa.data.remote

import com.routewise.sa.data.remote.dto.AiChatRequestDto
import com.routewise.sa.data.remote.dto.AiChatResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface GeminiRoadAiService {
    @POST("api/v1/ai/road-assistant")
    suspend fun askRoadAssistant(
        @Body request: AiChatRequestDto
    ): Response<AiChatResponseDto>
}
