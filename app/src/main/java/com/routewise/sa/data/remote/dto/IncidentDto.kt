package com.routewise.sa.data.remote.dto

import com.google.gson.annotations.SerializedName

data class IncidentDto(
    @SerializedName("id") val id: String,
    @SerializedName("type") val type: String,
    @SerializedName("roadName") val roadName: String,
    @SerializedName("city") val city: String,
    @SerializedName("province") val province: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("reportedBy") val reportedBy: String,
    @SerializedName("description") val description: String,
    @SerializedName("severity") val severity: String,
    @SerializedName("upvotes") val upvotes: Int = 0
)

data class ReportIncidentRequest(
    val type: String,
    val roadName: String,
    val city: String,
    val province: String,
    val latitude: Double,
    val longitude: Double,
    val description: String,
    val severity: String,
    val reportedByUserId: String
)
