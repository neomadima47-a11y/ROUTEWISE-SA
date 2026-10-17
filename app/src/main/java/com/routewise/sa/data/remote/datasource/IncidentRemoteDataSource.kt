package com.routewise.sa.data.remote.datasource

import com.routewise.sa.data.remote.RouteWiseApiService
import com.routewise.sa.data.remote.dto.IncidentDto
import com.routewise.sa.data.remote.dto.ReportIncidentRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class IncidentRemoteDataSource(
    private val apiService: RouteWiseApiService
) {
    suspend fun fetchIncidents(province: String?): Result<List<IncidentDto>> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.getIncidents(province = province)
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Failed to fetch incidents: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun submitReport(request: ReportIncidentRequest): Result<IncidentDto> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.reportIncident(request)
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Submission failed: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun upvote(id: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.upvoteIncident(id)
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(Exception("Upvote failed"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
