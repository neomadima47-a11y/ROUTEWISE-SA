package com.routewise.sa.data.remote

import com.routewise.sa.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface RouteWiseApiService {

    @GET("api/v1/incidents")
    suspend fun getIncidents(
        @Query("province") province: String? = null,
        @Query("category") category: String? = null
    ): Response<List<IncidentDto>>

    @POST("api/v1/incidents/report")
    suspend fun reportIncident(
        @Body request: ReportIncidentRequest
    ): Response<IncidentDto>

    @POST("api/v1/incidents/{id}/upvote")
    suspend fun upvoteIncident(
        @Path("id") incidentId: String
    ): Response<Unit>

    @GET("api/v1/directions")
    suspend fun getDirections(
        @Query("origin") origin: String,
        @Query("destination") destination: String,
        @Query("waypoints") waypoints: String? = null,
        @Query("avoid") avoid: String? = null,
        @Query("mode") mode: String = "driving"
    ): Response<DirectionsResponseDto>
}
