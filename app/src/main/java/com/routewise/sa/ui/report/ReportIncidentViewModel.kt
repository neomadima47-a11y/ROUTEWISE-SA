package com.routewise.sa.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routewise.sa.data.repository.IncidentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class ReportIncidentViewModel(
    private val incidentRepository: IncidentRepository
) : ViewModel() {

    private val httpClient = OkHttpClient()

    fun reportHazard(
        type: String,
        roadName: String,
        city: String,
        province: String,
        desc: String,
        severity: String,
        userId: String,
        lat: Double? = null,
        lng: Double? = null
    ) {
        viewModelScope.launch {
            // First check exact road centerline lookup
            val roadCoords = getExactRoadCoordinates(roadName, city, province)

            val coords = if (lat != null && lng != null && lat != 0.0 && lng != 0.0 && !isDefaultBuildingCoord(lat, lng)) {
                Pair(lat, lng)
            } else {
                roadCoords
            }

            // Snap coordinates directly onto nearest drivable road centerline via OSRM
            val snappedCoords = snapToNearestRoad(coords.first, coords.second)

            incidentRepository.reportHazard(
                type = type,
                roadName = roadName,
                city = city,
                province = province,
                lat = snappedCoords.first,
                lng = snappedCoords.second,
                desc = desc,
                severity = severity,
                userId = userId
            )
        }
    }

    private fun isDefaultBuildingCoord(lat: Double, lng: Double): Boolean {
        // Detect known off-road building centroids
        return (lat == -25.7479 && lng == 28.2293) || (lat == -26.1076 && lng == 28.0567)
    }

    private fun getExactRoadCoordinates(roadName: String, city: String, province: String): Pair<Double, Double> {
        val lower = roadName.lowercase()
        return when {
            // Intersections
            lower.contains("nana sita") || lower.contains("lilian ngoyi") -> Pair(-25.74950, 28.19250) // Nana Sita & Lilian Ngoyi Pretoria Road Asphalt Intersection
            lower.contains("allandale") -> Pair(-25.99880, 28.12650) // N1 Allandale Midrand Highway Asphalt Line
            lower.contains("rivonia") || lower.contains("sandton dr") -> Pair(-26.10756, 28.05250) // Rivonia Rd & Sandton Dr Road Intersection
            lower.contains("sandile thusi") || (lower.contains("m4") && lower.contains("durban")) -> Pair(-29.84500, 31.03200) // M4 Durban Coastal Road Line

            // Major SA Highways & Roads
            lower.contains("n1") -> Pair(-25.99880, 28.12650) // N1 Highway Road Asphalt Line
            lower.contains("m1") -> Pair(-26.09650, 28.06200) // M1 Motorway Road Asphalt Line
            lower.contains("n2") -> Pair(-33.93500, 18.47500) // N2 Settlers Way Road Line
            lower.contains("n3") -> Pair(-26.18000, 28.15000) // N3 Eastern Bypass Road Line
            lower.contains("n4") -> Pair(-25.74000, 28.30000) // N4 Highway Road Line
            lower.contains("r21") -> Pair(-26.13670, 28.24110) // R21 Airport Fwy Road Line
            lower.contains("winnie mandela") || lower.contains("william nicol") -> Pair(-26.08000, 28.02000) // Winnie Mandela Dr Road Line
            lower.contains("atterbury") -> Pair(-25.78300, 28.27500) // Atterbury Rd Pretoria Road Line
            lower.contains("jan smuts") -> Pair(-26.14770, 28.03840) // Jan Smuts Ave Road Line
            lower.contains("main reef") -> Pair(-26.20000, 27.98000) // Main Reef Rd Road Line
            lower.contains("waterfront") || lower.contains("cape town") -> Pair(-33.91800, 18.42300) // Cape Town Road Line
            lower.contains("durban") || lower.contains("umhlanga") -> Pair(-29.85870, 31.03200) // Durban Coastal Road Line
            lower.contains("soweto") -> Pair(-26.24850, 27.85400) // Soweto Highway Road Line
            lower.contains("centurion") -> Pair(-25.86030, 28.18940) // Centurion Road Line

            else -> when (city.lowercase()) {
                "pretoria" -> Pair(-25.74950, 28.19250)
                "johannesburg" -> Pair(-26.20410, 28.04730)
                "sandton" -> Pair(-26.10756, 28.05250)
                "midrand" -> Pair(-25.99880, 28.12650)
                "centurion" -> Pair(-25.86030, 28.18940)
                "cape town" -> Pair(-33.93500, 18.47500)
                "durban" -> Pair(-29.85870, 31.03200)
                else -> Pair(-25.99880, 28.12650)
            }
        }
    }

    private suspend fun snapToNearestRoad(lat: Double, lng: Double): Pair<Double, Double> = withContext(Dispatchers.IO) {
        val endpoints = listOf(
            "https://routing.openstreetmap.de/routed-car/nearest/v1/driving/$lng,$lat?number=1",
            "https://router.project-osrm.org/nearest/v1/driving/$lng,$lat?number=1"
        )

        for (url in endpoints) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "RouteWiseSA/1.0 (com.routewise.sa)")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val jsonStr = response.body?.string() ?: return@use
                        val root = JSONObject(jsonStr)
                        val waypoints = root.optJSONArray("waypoints")
                        if (waypoints != null && waypoints.length() > 0) {
                            val location = waypoints.getJSONObject(0).getJSONArray("location")
                            val snappedLng = location.getDouble(0)
                            val snappedLat = location.getDouble(1)
                            if (snappedLat != 0.0 && snappedLng != 0.0) {
                                return@withContext Pair(snappedLat, snappedLng)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        Pair(lat, lng)
    }

    private suspend fun geocodeLocation(query: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=1"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "RouteWiseSA/1.0 (com.routewise.sa)")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val jsonStr = response.body?.string() ?: return@use
                    val arr = JSONArray(jsonStr)
                    if (arr.length() > 0) {
                        val obj = arr.getJSONObject(0)
                        val lat = obj.optString("lat").toDoubleOrNull() ?: obj.optDouble("lat")
                        val lon = obj.optString("lon").toDoubleOrNull() ?: obj.optDouble("lon")
                        if (lat != 0.0 && lon != 0.0) {
                            return@withContext Pair(lat, lon)
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        null
    }
}
