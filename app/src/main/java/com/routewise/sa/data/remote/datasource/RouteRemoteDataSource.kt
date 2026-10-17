package com.routewise.sa.data.remote.datasource

import com.routewise.sa.data.remote.RouteWiseApiService
import com.routewise.sa.data.remote.dto.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.net.URLEncoder

class RouteRemoteDataSource(
    private val apiService: RouteWiseApiService? = null,
    private val client: OkHttpClient = OkHttpClient()
) {
    data class OsrmResult(
        val distance: Long,
        val duration: Long,
        val steps: List<StepDto>,
        val pathPoints: List<GeoPoint>
    )

    suspend fun fetchRoute(
        origin: String,
        destination: String
    ): Result<OsrmResult> = withContext(Dispatchers.IO) {
        val (origLat, origLng) = geocodePlace(origin) ?: getFallbackCoordinates(origin)
        val (destLat, destLng) = geocodePlace(destination) ?: getFallbackCoordinates(destination)

        val endpoints = listOf(
            "https://routing.openstreetmap.de/routed-car/route/v1/driving/$origLng,$origLat;$destLng,$destLat?overview=full&geometries=geojson&steps=true",
            "https://router.project-osrm.org/route/v1/driving/$origLng,$origLat;$destLng,$destLat?overview=full&geometries=geojson&steps=true"
        )

        for (url in endpoints) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "RouteWiseAndroid/1.0")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val jsonStr = response.body?.string() ?: return@use
                    val root = JSONObject(jsonStr)
                    if (root.optString("code") == "Ok") {
                        val routes = root.getJSONArray("routes")
                        if (routes.length() > 0) {
                            val firstRoute = routes.getJSONObject(0)
                            val distance = firstRoute.optDouble("distance", 10000.0).toLong()
                            val duration = firstRoute.optDouble("duration", 600.0).toLong()

                            val geom = firstRoute.optJSONObject("geometry")
                            val coordsArr = geom?.optJSONArray("coordinates")
                            val pathPoints = mutableListOf<GeoPoint>()
                            if (coordsArr != null) {
                                for (i in 0 until coordsArr.length()) {
                                    val pt = coordsArr.getJSONArray(i)
                                    val lng = pt.getDouble(0)
                                    val lat = pt.getDouble(1)
                                    pathPoints.add(GeoPoint(lat, lng))
                                }
                            }

                            val legsArr = firstRoute.optJSONArray("legs")
                            val stepsList = mutableListOf<StepDto>()
                            if (legsArr != null && legsArr.length() > 0) {
                                val stepsArr = legsArr.getJSONObject(0).optJSONArray("steps")
                                if (stepsArr != null) {
                                    for (i in 0 until stepsArr.length()) {
                                        val st = stepsArr.getJSONObject(i)
                                        val maneuver = st.optJSONObject("maneuver")
                                        stepsList.add(
                                            StepDto(
                                                htmlInstructions = maneuver?.optString("instruction") ?: st.optString("name", "Continue on road"),
                                                distance = TextValueDto("", st.optDouble("distance", 500.0).toLong()),
                                                duration = TextValueDto("", st.optDouble("duration", 60.0).toLong()),
                                                startLocation = LatLngDto(0.0, 0.0),
                                                endLocation = LatLngDto(0.0, 0.0),
                                                maneuver = maneuver?.optString("type") ?: "straight"
                                            )
                                        )
                                    }
                                }
                            }

                            if (pathPoints.isNotEmpty()) {
                                return@withContext Result.success(OsrmResult(distance, duration, stepsList, pathPoints))
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Try next endpoint
            }
        }

        Result.failure(Exception("Could not fetch route from OSRM"))
    }

    private suspend fun geocodePlace(place: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        try {
            val query = if (place.contains("South Africa", true)) place else "$place, South Africa"
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=1"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "RouteWiseAndroid/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val jsonStr = response.body?.string() ?: return@withContext null
                    val arr = JSONArray(jsonStr)
                    if (arr.length() > 0) {
                        val obj = arr.getJSONObject(0)
                        val lat = obj.getDouble("lat")
                        val lon = obj.getDouble("lon")
                        return@withContext Pair(lat, lon)
                    }
                }
            }
        } catch (_: Exception) {
        }
        null
    }

    private fun getFallbackCoordinates(place: String): Pair<Double, Double> {
        val lower = place.lowercase()
        return when {
            lower.contains("pretoria") || lower.contains("menlyn") -> Pair(-25.7845, 28.2770)
            lower.contains("cape town") || lower.contains("waterfront") -> Pair(-33.9249, 18.4241)
            lower.contains("durban") || lower.contains("umhlanga") -> Pair(-29.8587, 31.0218)
            lower.contains("sandton") -> Pair(-26.1076, 28.0567)
            lower.contains("rosebank") -> Pair(-26.1477, 28.0384)
            lower.contains("johannesburg") || lower.contains("joburg") -> Pair(-26.2041, 28.0473)
            else -> Pair(-25.7500, 28.1900)
        }
    }
}
