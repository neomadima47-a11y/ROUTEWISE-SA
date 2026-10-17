package com.routewise.sa.ui.map

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routewise.sa.data.model.IncidentEntity
import com.routewise.sa.data.model.RouteStepEntity
import com.routewise.sa.data.repository.CalculatedRoute
import com.routewise.sa.data.repository.DirectionsRepository
import com.routewise.sa.data.repository.IncidentRepository
import com.routewise.sa.data.repository.RouteRepository
import com.routewise.sa.util.RoadCurveService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.net.URLEncoder
import kotlin.math.cos
import kotlin.math.sin

class MapViewModel(
    private val routeRepository: RouteRepository,
    private val incidentRepository: IncidentRepository
) : ViewModel() {

    private val _activeRoute = MutableLiveData<CalculatedRoute?>()
    val activeRoute: LiveData<CalculatedRoute?> = _activeRoute

    private val _currentStepIndex = MutableLiveData<Int>(0)
    val currentStepIndex: LiveData<Int> = _currentStepIndex

    private val _isVoiceMuted = MutableLiveData<Boolean>(false)
    val isVoiceMuted: LiveData<Boolean> = _isVoiceMuted

    private val _incidents = MutableLiveData<List<IncidentEntity>>()
    val incidents: LiveData<List<IncidentEntity>> = _incidents

    private val _focusedIncident = MutableLiveData<IncidentEntity?>()
    val focusedIncident: LiveData<IncidentEntity?> = _focusedIncident

    private val directionsRepository = DirectionsRepository()
    private val httpClient = OkHttpClient()

    init {
        loadIncidents()
    }

    fun focusIncidentOnMap(incident: IncidentEntity) {
        _focusedIncident.value = incident
    }

    fun clearFocusedIncident() {
        _focusedIncident.value = null
    }

    fun upvoteIncident(id: String) {
        viewModelScope.launch {
            incidentRepository.upvote(id)
        }
    }

    fun refreshIncidents() {
        loadIncidents()
    }

    private fun loadIncidents() {
        viewModelScope.launch {
            incidentRepository.getIncidents(null).collectLatest {
                _incidents.postValue(it)
            }
        }
    }

    fun planTrip(origin: String, destination: String, avoidTolls: Boolean) {
        viewModelScope.launch {
            val origPoint = geocodeDestination(origin) ?: GeoPoint(-26.2041, 28.0473)
            val destPoint = geocodeDestination(destination) ?: calculateNearbyPoint(origPoint, destination)

            planTripInternal(origPoint, destPoint, destination, avoidTolls)
        }
    }

    fun planTripWithCoordinates(originPoint: GeoPoint, destination: String, avoidTolls: Boolean) {
        viewModelScope.launch {
            val destPoint = geocodeDestination(destination) ?: calculateNearbyPoint(originPoint, destination)

            planTripInternal(originPoint, destPoint, destination, avoidTolls)
        }
    }

    private suspend fun planTripInternal(originPoint: GeoPoint, destPoint: GeoPoint, destinationName: String, avoidTolls: Boolean) {
        val result = directionsRepository.getRoadRoute(originPoint, destPoint)

        if (result.isSuccess && result.getOrNull() != null) {
            val dirRoute = result.getOrNull()!!
            val routeId = "route_${System.currentTimeMillis()}"

            val stepEntities = dirRoute.steps.mapIndexed { index, step ->
                val stepStart = dirRoute.polylinePoints.getOrNull((index * dirRoute.polylinePoints.size) / dirRoute.steps.size)
                    ?: originPoint
                val stepEnd = dirRoute.polylinePoints.getOrNull(((index + 1) * dirRoute.polylinePoints.size) / dirRoute.steps.size)
                    ?: destPoint

                RouteStepEntity(
                    routeId = routeId,
                    stepIndex = index,
                    instruction = step.instruction,
                    roadName = step.roadName,
                    distanceMeters = step.distanceMeters.toLong(),
                    durationSeconds = step.durationSec.toLong(),
                    startLat = stepStart.latitude,
                    startLng = stepStart.longitude,
                    endLat = stepEnd.latitude,
                    endLng = stepEnd.longitude,
                    maneuver = step.maneuver
                )
            }

            val calculated = CalculatedRoute(
                routeId = routeId,
                originName = "Current Location",
                destinationName = destinationName,
                totalDistanceMeters = dirRoute.totalDistanceMeters.toLong(),
                totalDurationSeconds = dirRoute.totalDurationSeconds.toLong(),
                overviewPolylinePoints = "",
                pathPoints = dirRoute.polylinePoints,
                steps = stepEntities,
                waypoints = emptyList(),
                speedLimit = if (dirRoute.totalDistanceMeters > 20000) 120 else 60
            )

            _activeRoute.postValue(calculated)
            _currentStepIndex.postValue(0)
        } else {
            val localRoute = generateLocalRouteFromPoints(originPoint, destPoint, destinationName)
            _activeRoute.postValue(localRoute)
            _currentStepIndex.postValue(0)
        }
    }

    private suspend fun geocodeDestination(destination: String): GeoPoint? = withContext(Dispatchers.IO) {
        if (destination.isBlank()) return@withContext null
        val queries = listOf(
            destination,
            "$destination, South Africa"
        )
        for (q in queries) {
            try {
                val encoded = URLEncoder.encode(q, "UTF-8")
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
                                return@withContext GeoPoint(lat, lon)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        null
    }

    private fun calculateNearbyPoint(startPoint: GeoPoint, placeName: String): GeoPoint {
        val lower = placeName.lowercase()
        return when {
            lower.contains("pretoria") || lower.contains("menlyn") || lower.contains("hatfield") || lower.contains("brooklyn") -> GeoPoint(-25.7479, 28.2293)
            lower.contains("sandton") || lower.contains("rivonia") || lower.contains("morningside") -> GeoPoint(-26.1076, 28.0567)
            lower.contains("mall of africa") || lower.contains("midrand") || lower.contains("kyalami") -> GeoPoint(-25.9983, 28.1278)
            lower.contains("or tambo") || lower.contains("kempton park") || lower.contains("benoni") -> GeoPoint(-26.1367, 28.2411)
            lower.contains("cape town") || lower.contains("waterfront") || lower.contains("sea point") -> GeoPoint(-33.9249, 18.4241)
            lower.contains("durban") || lower.contains("umhlanga") || lower.contains("gateway") -> GeoPoint(-29.8587, 31.0218)
            lower.contains("soweto") || lower.contains("maponya") || lower.contains("orlando") -> GeoPoint(-26.2485, 27.8540)
            lower.contains("rosebank") || lower.contains("parkhurst") || lower.contains("melrose") -> GeoPoint(-26.1477, 28.0384)
            lower.contains("johannesburg") || lower.contains("joburg") || lower.contains("braamfontein") -> GeoPoint(-26.2041, 28.0473)
            lower.contains("centurion") || lower.contains("zwartkop") -> GeoPoint(-25.8603, 28.1894)
            lower.contains("polokwane") || lower.contains("pietersburg") -> GeoPoint(-23.9044, 29.4688)
            lower.contains("bloemfontein") || lower.contains("mimosa") -> GeoPoint(-29.1181, 26.2243)
            lower.contains("port elizabeth") || lower.contains("gqeberha") || lower.contains("walmer") -> GeoPoint(-33.9608, 25.6022)
            lower.contains("stellenbosch") -> GeoPoint(-33.9321, 18.8602)
            lower.contains("nelspruit") || lower.contains("mbombela") -> GeoPoint(-25.4753, 30.9694)
            lower.contains("rustenburg") -> GeoPoint(-25.6676, 27.2421)
            lower.contains("kimberley") -> GeoPoint(-28.7282, 24.7499)
            lower.contains("george") -> GeoPoint(-33.9630, 22.4617)
            lower.contains("ballito") -> GeoPoint(-29.5392, 31.2183)
            else -> GeoPoint(startPoint.latitude + 0.05, startPoint.longitude + 0.05)
        }
    }

    private fun generateLocalRouteFromPoints(startPoint: GeoPoint, destPoint: GeoPoint, destinationName: String): CalculatedRoute {
        val routeId = "route_${System.currentTimeMillis()}"
        
        val basePoints = mutableListOf<GeoPoint>()
        val stepsCount = 20
        for (i in 0..stepsCount) {
            val fraction = i.toDouble() / stepsCount
            val latOffset = sin(fraction * Math.PI) * 0.015
            val lngOffset = cos(fraction * Math.PI * 0.5) * 0.01
            val lat = startPoint.latitude + (destPoint.latitude - startPoint.latitude) * fraction + latOffset
            val lng = startPoint.longitude + (destPoint.longitude - startPoint.longitude) * fraction + lngOffset
            basePoints.add(GeoPoint(lat, lng))
        }
        val pathPoints = RoadCurveService.smoothRoadCurvature(basePoints, 4)

        val steps = listOf(
            RouteStepEntity(
                routeId = routeId,
                stepIndex = 0,
                instruction = "Head toward main road to $destinationName",
                roadName = "Main Road",
                distanceMeters = 2500,
                durationSeconds = 150,
                startLat = pathPoints.first().latitude,
                startLng = pathPoints.first().longitude,
                endLat = pathPoints[pathPoints.size / 3].latitude,
                endLng = pathPoints[pathPoints.size / 3].longitude,
                maneuver = "straight"
            ),
            RouteStepEntity(
                routeId = routeId,
                stepIndex = 1,
                instruction = "Continue toward $destinationName",
                roadName = "Connecting Road",
                distanceMeters = 18000,
                durationSeconds = 900,
                startLat = pathPoints[pathPoints.size / 3].latitude,
                startLng = pathPoints[pathPoints.size / 3].longitude,
                endLat = pathPoints.last().latitude,
                endLng = pathPoints.last().longitude,
                maneuver = "fork-right"
            ),
            RouteStepEntity(
                routeId = routeId,
                stepIndex = 2,
                instruction = "Arrive at $destinationName",
                roadName = destinationName,
                distanceMeters = 300,
                durationSeconds = 45,
                startLat = pathPoints.last().latitude,
                startLng = pathPoints.last().longitude,
                endLat = pathPoints.last().latitude,
                endLng = pathPoints.last().longitude,
                maneuver = "straight"
            )
        )

        return CalculatedRoute(
            routeId = routeId,
            originName = "Current Location",
            destinationName = destinationName,
            totalDistanceMeters = 20800,
            totalDurationSeconds = 1095,
            overviewPolylinePoints = "",
            pathPoints = pathPoints,
            steps = steps,
            waypoints = emptyList(),
            speedLimit = 120
        )
    }

    fun setCalculatedRoute(route: CalculatedRoute) {
        _activeRoute.value = route
        _currentStepIndex.value = 0
    }

    fun nextStep() {
        val current = _currentStepIndex.value ?: 0
        val max = (_activeRoute.value?.steps?.size ?: 1) - 1
        if (current < max) {
            _currentStepIndex.value = current + 1
        }
    }

    fun prevStep() {
        val current = _currentStepIndex.value ?: 0
        if (current > 0) {
            _currentStepIndex.value = current - 1
        }
    }

    fun toggleVoice() {
        _isVoiceMuted.value = !(_isVoiceMuted.value ?: false)
    }

    fun clearRoute() {
        _activeRoute.value = null
        _currentStepIndex.value = 0
    }
}
