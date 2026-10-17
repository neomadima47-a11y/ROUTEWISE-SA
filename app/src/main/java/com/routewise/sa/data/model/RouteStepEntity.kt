package com.routewise.sa.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "route_steps")
data class RouteStepEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val routeId: String,
    val stepIndex: Int,
    val instruction: String,
    val roadName: String,
    val distanceMeters: Long,
    val durationSeconds: Long,
    val startLat: Double,
    val startLng: Double,
    val endLat: Double,
    val endLng: Double,
    val maneuver: String? = null
)
