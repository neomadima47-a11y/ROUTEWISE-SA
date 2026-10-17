package com.routewise.sa.data.repository

import com.routewise.sa.data.local.UserDao
import com.routewise.sa.data.model.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class UserRepository(
    private val userDao: UserDao
) {
    val activeUserFlow: Flow<UserEntity?> = userDao.getActiveUser()

    suspend fun saveUser(user: UserEntity): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            userDao.insertUser(user)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateUserProfile(
        uid: String,
        fullName: String,
        phone: String,
        vehicleType: String,
        licensePlate: String,
        emergencyContact: String,
        province: String,
        city: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val existing = userDao.getUserById(uid)
            if (existing != null) {
                val updated = existing.copy(
                    fullName = fullName,
                    phoneNumber = phone,
                    vehicleType = vehicleType,
                    licensePlate = licensePlate,
                    emergencyContact = emergencyContact,
                    province = province,
                    city = city
                )
                userDao.updateUser(updated)
                Result.success(Unit)
            } else {
                Result.failure(Exception("User not found in database"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun rewardImpactPoints(uid: String, points: Int = 10): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            userDao.addImpactPoints(uid, points)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            userDao.clearUser()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
