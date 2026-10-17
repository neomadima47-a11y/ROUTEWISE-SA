package com.routewise.sa.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.routewise.sa.data.repository.IncidentRepository
import com.routewise.sa.data.repository.RouteRepository
import com.routewise.sa.data.repository.UserRepository
import com.routewise.sa.ui.alerts.LiveAlertsViewModel
import com.routewise.sa.ui.map.MapViewModel
import com.routewise.sa.ui.report.ReportIncidentViewModel

class ViewModelFactory(
    private val incidentRepository: IncidentRepository,
    private val userRepository: UserRepository,
    private val routeRepository: RouteRepository? = null
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(LiveAlertsViewModel::class.java) -> {
                LiveAlertsViewModel(incidentRepository) as T
            }
            modelClass.isAssignableFrom(ReportIncidentViewModel::class.java) -> {
                ReportIncidentViewModel(incidentRepository) as T
            }
            modelClass.isAssignableFrom(MapViewModel::class.java) -> {
                MapViewModel(routeRepository ?: throw IllegalStateException("RouteRepository required for MapViewModel"), incidentRepository) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
