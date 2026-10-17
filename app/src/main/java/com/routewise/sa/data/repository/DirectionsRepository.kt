package com.routewise.sa.data.repository

import com.routewise.sa.util.RoadCurveService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.osmdroid.util.GeoPoint

data class DirectionStep(
    val instruction: String,
    val roadName: String,
    val distanceMeters: Int,
    val durationSec: Int,
    val maneuver: String
)

data class DirectionRoute(
    val polylinePoints: List<GeoPoint>,
    val steps: List<DirectionStep>,
    val totalDistanceMeters: Int,
    val totalDurationSeconds: Int
)

class DirectionsRepository(private val client: OkHttpClient = OkHttpClient()) {

    suspend fun getRoadRoute(origin: GeoPoint, destination: GeoPoint): Result<DirectionRoute> =
        withContext(Dispatchers.IO) {
            val urls = listOf(
                "https://routing.openstreetmap.de/routed-car/route/v1/driving/${origin.longitude},${origin.latitude};${destination.longitude},${destination.latitude}?overview=full&geometries=geojson&steps=true",
                "https://router.project-osrm.org/route/v1/driving/${origin.longitude},${origin.latitude};${destination.longitude},${destination.latitude}?overview=full&geometries=geojson&steps=true"
            )

            for (url in urls) {
                try {
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "RouteWiseAndroid/1.0")
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@use

                        val jsonStr = response.body?.string() ?: return@use
                        val json = JSONObject(jsonStr)
                        if (json.optString("code") != "Ok") return@use

                        val route = json.getJSONArray("routes").getJSONObject(0)
                        val coords = route.getJSONObject("geometry").getJSONArray("coordinates")
                        
                        // Parse road points
                        val points = mutableListOf<GeoPoint>()
                        for (i in 0 until coords.length()) {
                            val pt = coords.getJSONArray(i)
                            points.add(GeoPoint(pt.getDouble(1), pt.getDouble(0)))
                        }

                        // Apply Catmull-Rom smooth road curvature
                        val smoothedPoints = RoadCurveService.smoothRoadCurvature(points, 4)

                        // Parse turn-by-turn steps
                        val steps = mutableListOf<DirectionStep>()
                        val legs = route.optJSONArray("legs")
                        if (legs != null && legs.length() > 0) {
                            val rawSteps = legs.getJSONObject(0).optJSONArray("steps")
                            if (rawSteps != null) {
                                for (i in 0 until rawSteps.length()) {
                                    val s = rawSteps.getJSONObject(i)
                                    val maneuverObj = s.optJSONObject("maneuver")
                                    steps.add(
                                        DirectionStep(
                                            instruction = maneuverObj?.optString("instruction") ?: "Continue",
                                            roadName = s.optString("name", "Main Road"),
                                            distanceMeters = s.optDouble("distance", 0.0).toInt(),
                                            durationSec = s.optDouble("duration", 0.0).toInt(),
                                            maneuver = maneuverObj?.optString("type") ?: "straight"
                                        )
                                    )
                                }
                            }
                        }

                        return@withContext Result.success(
                            DirectionRoute(
                                polylinePoints = smoothedPoints,
                                steps = steps,
                                totalDistanceMeters = route.optDouble("distance", 0.0).toInt(),
                                totalDurationSeconds = route.optDouble("duration", 0.0).toInt()
                            )
                        )
                    }
                } catch (_: Exception) {
                    // Try next URL
                }
            }

            Result.failure(Exception("Could not fetch road route from OSRM"))
        }
}
