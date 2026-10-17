package com.routewise.sa.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stop_suggestions")
data class StopSuggestionEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val category: String, // "Fuel", "Food", "Coffee", "ATM", "Rest Area"
    val brand: String? = null, // "Engen 1-Stop", "Shell Ultra City", "TotalEnergies", "Wimpy"
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val extraDetourMinutes: Int = 0,
    val rating: Double = 4.5,
    val is24Hours: Boolean = true
)
