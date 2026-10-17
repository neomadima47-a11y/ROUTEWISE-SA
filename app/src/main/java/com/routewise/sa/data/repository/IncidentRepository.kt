package com.routewise.sa.data.repository

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.routewise.sa.data.local.IncidentDao
import com.routewise.sa.data.model.IncidentEntity
import com.routewise.sa.data.remote.datasource.IncidentRemoteDataSource
import com.routewise.sa.data.remote.dto.ReportIncidentRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.UUID

class IncidentRepository(
    private val localDao: IncidentDao,
    private val remoteSource: IncidentRemoteDataSource
) {
    init {
        startRealtimeFirestoreSync()
    }

    fun getIncidents(province: String?): Flow<List<IncidentEntity>> {
        return if (province.isNullOrEmpty() || province == "All Provinces") {
            localDao.getAllIncidents()
        } else {
            localDao.getIncidentsByProvince(province)
        }
    }

    private fun startRealtimeFirestoreSync() {
        try {
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("incidents")
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener

                    val twelveHoursAgo = System.currentTimeMillis() - (12 * 60 * 60 * 1000L)

                    // Filter out reports older than 12 hours
                    val validDocs = snapshot.documents.filter { doc ->
                        val ts = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        ts >= twelveHoursAgo
                    }

                    val activeIds = validDocs.map { it.id }
                    val entities = validDocs.mapNotNull { doc ->
                        val id = doc.getString("id") ?: doc.id
                        val type = doc.getString("type") ?: "Hazard"
                        val roadName = doc.getString("roadName") ?: ""
                        val city = doc.getString("city") ?: ""
                        val prov = doc.getString("province") ?: ""
                        val latitude = doc.getDouble("latitude") ?: 0.0
                        val longitude = doc.getDouble("longitude") ?: 0.0
                        val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        val reportedBy = doc.getString("reportedBy") ?: "Community Driver"
                        val description = doc.getString("description") ?: ""
                        val severity = doc.getString("severity") ?: "Moderate"
                        val statusBox = doc.getString("statusBox") ?: "Active Alert"
                        val upvotes = doc.getLong("upvotes")?.toInt() ?: 1

                        IncidentEntity(
                            id = id,
                            type = type,
                            roadName = roadName,
                            city = city,
                            province = prov,
                            latitude = latitude,
                            longitude = longitude,
                            timestamp = timestamp,
                            reportedBy = reportedBy,
                            description = description,
                            severity = severity,
                            statusBox = statusBox,
                            upvotes = upvotes
                        )
                    }

                    CoroutineScope(Dispatchers.IO).launch {
                        // Purge incidents older than 12 hours
                        localDao.deleteIncidentsOlderThan(twelveHoursAgo)

                        if (activeIds.isNotEmpty()) {
                            localDao.retainOnlyActiveIncidents(activeIds)
                        } else {
                            localDao.deleteAllIncidents()
                        }
                        localDao.insertIncidents(entities)
                    }
                }
        } catch (e: Exception) {
            Log.e("IncidentRepo", "Realtime sync error: ${e.message}")
        }
    }

    suspend fun syncRemoteIncidents(province: String?): Result<Unit> {
        return Result.success(Unit)
    }

    suspend fun reportHazard(
        type: String,
        roadName: String,
        city: String,
        province: String,
        lat: Double,
        lng: Double,
        desc: String,
        severity: String,
        userId: String
    ): Result<Unit> {
        val incidentId = UUID.randomUUID().toString()

        // 1. Always save locally in Room Database
        val localIncident = IncidentEntity(
            id = incidentId,
            type = type,
            roadName = roadName,
            city = city,
            province = province,
            latitude = lat,
            longitude = lng,
            timestamp = System.currentTimeMillis(),
            reportedBy = userId,
            description = desc,
            severity = severity,
            statusBox = "Active Alert",
            upvotes = 1
        )
        localDao.insertIncident(localIncident)

        // 2. Direct Push to Firebase Cloud Firestore ("incidents" collection)
        try {
            val firestore = FirebaseFirestore.getInstance()
            val incidentMap = hashMapOf(
                "id" to localIncident.id,
                "type" to localIncident.type,
                "roadName" to localIncident.roadName,
                "city" to localIncident.city,
                "province" to localIncident.province,
                "latitude" to localIncident.latitude,
                "longitude" to localIncident.longitude,
                "timestamp" to localIncident.timestamp,
                "reportedBy" to localIncident.reportedBy,
                "description" to localIncident.description,
                "severity" to localIncident.severity,
                "statusBox" to localIncident.statusBox,
                "upvotes" to localIncident.upvotes
            )

            firestore.collection("incidents")
                .document(localIncident.id)
                .set(incidentMap)
                .addOnSuccessListener {
                    Log.d("IncidentRepo", "🔥 Report published to Cloud Firestore document: ${localIncident.id}")
                }
                .addOnFailureListener { e ->
                    Log.e("IncidentRepo", "❌ Error publishing to Cloud Firestore: ${e.message}")
                }

            // 3. AUTOMATICALLY CONSTRUCT & DISPATCH FCM NOTIFICATION DOCUMENT
            val emoji = when (type.lowercase()) {
                "accident" -> "💥"
                "pothole" -> "🕳️"
                "broken robot" -> "🚦"
                "police trap" -> "🚓"
                "road works" -> "🚧"
                "road closure" -> "⛔"
                "gridlock" -> "🚙"
                else -> "⚠️"
            }

            val notifTitle = "$emoji $type in $city"
            val notifBody = "$roadName — ${desc.ifEmpty { "Drive cautiously" }}"
            val provTopic = province.lowercase().replace(" ", "_")

            val notifMap = hashMapOf(
                "id" to "notif_${localIncident.id}",
                "incidentId" to localIncident.id,
                "title" to notifTitle,
                "message" to notifBody,
                "type" to type,
                "roadName" to roadName,
                "city" to city,
                "province" to province,
                "topic" to "alerts_$provTopic",
                "latitude" to lat,
                "longitude" to lng,
                "timestamp" to System.currentTimeMillis(),
                "isRead" to false,
                "impactBonus" to 25
            )

            firestore.collection("notifications")
                .document("notif_${localIncident.id}")
                .set(notifMap)
                .addOnSuccessListener {
                    Log.d("IncidentRepo", "🔔 Automated FCM notification generated: $notifTitle")
                }

        } catch (e: Exception) {
            Log.e("IncidentRepo", "Firestore not configured or initialized: ${e.message}")
        }

        // 4. Sync with optional API service
        val request = ReportIncidentRequest(
            type = type,
            roadName = roadName,
            city = city,
            province = province,
            latitude = lat,
            longitude = lng,
            description = desc,
            severity = severity,
            reportedByUserId = userId
        )
        try {
            remoteSource.submitReport(request)
        } catch (e: Exception) {
            Log.e("IncidentRepo", "Remote API sync failed: ${e.message}")
        }

        return Result.success(Unit)
    }

    suspend fun upvote(id: String) {
        localDao.incrementUpvote(id)

        // Update upvote counter in Cloud Firestore
        try {
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("incidents")
                .document(id)
                .update("upvotes", FieldValue.increment(1))
        } catch (e: Exception) {
            Log.e("IncidentRepo", "Firestore upvote update failed: ${e.message}")
        }

        remoteSource.upvote(id)
    }
}
