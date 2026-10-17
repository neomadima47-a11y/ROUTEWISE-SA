package com.routewise.sa.data.remote.dto

import com.google.gson.annotations.SerializedName

data class AiChatResponseDto(
    @SerializedName("responseText")
    val responseText: String = "",

    @SerializedName("suggestedActions")
    val suggestedActions: List<String> = emptyList(),

    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
)
