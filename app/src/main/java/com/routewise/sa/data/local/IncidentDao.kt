package com.routewise.sa.data.local

import androidx.room.*
import com.routewise.sa.data.model.IncidentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IncidentDao {

    @Query("SELECT * FROM incidents ORDER BY timestamp DESC")
    fun getAllIncidents(): Flow<List<IncidentEntity>>

    @Query("SELECT * FROM incidents WHERE province = :province ORDER BY timestamp DESC")
    fun getIncidentsByProvince(province: String): Flow<List<IncidentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIncidents(incidents: List<IncidentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIncident(incident: IncidentEntity)

    @Query("UPDATE incidents SET upvotes = upvotes + 1 WHERE id = :id")
    suspend fun incrementUpvote(id: String)

    @Query("DELETE FROM incidents WHERE id = :id")
    suspend fun deleteIncident(id: String)

    @Query("DELETE FROM incidents WHERE id NOT IN (:activeIds)")
    suspend fun retainOnlyActiveIncidents(activeIds: List<String>)

    @Query("DELETE FROM incidents WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteIncidentsOlderThan(cutoffTimestamp: Long)

    @Query("DELETE FROM incidents")
    suspend fun deleteAllIncidents()
}
