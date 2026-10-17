package com.routewise.sa.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val uid: String,
    val email: String,
    val fullName: String,
    val phoneNumber: String,
    val vehicleType: String,
    val licensePlate: String,
    val emergencyContact: String,
    val province: String,
    val city: String,
    val impactPoints: Int = 100,
    val totalReports: Int = 0,
    val locationConsent: Boolean = true,
    val notificationEnabled: Boolean = true,
    val isAdmin: Boolean = false,
    val registrationDate: Long = System.currentTimeMillis()
)
