package com.routewise.sa.data.local

import androidx.room.*
import com.routewise.sa.data.model.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    @Query("SELECT * FROM users LIMIT 1")
    fun getActiveUser(): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE uid = :uid LIMIT 1")
    suspend fun getUserById(uid: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("UPDATE users SET impactPoints = impactPoints + :points, totalReports = totalReports + 1 WHERE uid = :uid")
    suspend fun addImpactPoints(uid: String, points: Int = 10)

    @Query("DELETE FROM users")
    suspend fun clearUser()
}
