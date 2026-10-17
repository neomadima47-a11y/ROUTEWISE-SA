package com.routewise.sa.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "incidents")
data class IncidentEntity(
    @PrimaryKey val id: String,
    val type: String, // Accident, Pothole, Load Shedding Outage, Police Trap, Roadworks, Closure
    val roadName: String,
    val city: String,
    val province: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val reportedBy: String,
    val description: String,
    val severity: String, // low, medium, high, critical
    val statusBox: String = "Active Alert",
    val routeBadge: String? = null,
    val directionBadge: String? = null,
    var upvotes: Int = 0,
    val isVerified: Boolean = false
)
