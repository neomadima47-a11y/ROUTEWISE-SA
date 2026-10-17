package com.routewise.sa.data.remote.dto

import com.google.gson.annotations.SerializedName

data class AiChatRequestDto(
    @SerializedName("prompt")
    val prompt: String,

    @SerializedName("userLocationProvince")
    val userLocationProvince: String = "Gauteng",

    @SerializedName("nearbyCorridors")
    val nearbyCorridors: List<String> = listOf("N1", "N3", "N4", "M1")
)
