package com.routewise.sa.data.remote.dto

import com.google.gson.annotations.SerializedName

data class DirectionsResponseDto(
    @SerializedName("routes") val routes: List<RouteDto>,
    @SerializedName("status") val status: String
)

data class RouteDto(
    @SerializedName("summary") val summary: String,
    @SerializedName("legs") val legs: List<LegDto>,
    @SerializedName("overview_polyline") val overviewPolyline: PolylineDto
)

data class LegDto(
    @SerializedName("distance") val distance: TextValueDto,
    @SerializedName("duration") val duration: TextValueDto,
    @SerializedName("start_address") val startAddress: String,
    @SerializedName("end_address") val endAddress: String,
    @SerializedName("start_location") val startLocation: LatLngDto,
    @SerializedName("end_location") val endLocation: LatLngDto,
    @SerializedName("steps") val steps: List<StepDto>
)

data class StepDto(
    @SerializedName("html_instructions") val htmlInstructions: String,
    @SerializedName("distance") val distance: TextValueDto,
    @SerializedName("duration") val duration: TextValueDto,
    @SerializedName("start_location") val startLocation: LatLngDto,
    @SerializedName("end_location") val endLocation: LatLngDto,
    @SerializedName("maneuver") val maneuver: String?
)

data class LatLngDto(
    @SerializedName("lat") val lat: Double,
    @SerializedName("lng") val lng: Double
)

data class TextValueDto(
    @SerializedName("text") val text: String,
    @SerializedName("value") val value: Long
)

data class PolylineDto(
    @SerializedName("points") val points: String
)
