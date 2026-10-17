package com.routewise.sa.ui.plan

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routewise.sa.data.model.StopSuggestionEntity
import com.routewise.sa.data.repository.CalculatedRoute
import com.routewise.sa.data.repository.RouteRepository
import kotlinx.coroutines.launch

class RoutePlanningViewModel(
    private val routeRepository: RouteRepository
) : ViewModel() {

    private val _routeResult = MutableLiveData<Result<CalculatedRoute>>()
    val routeResult: LiveData<Result<CalculatedRoute>> = _routeResult

    private val _selectedStops = MutableLiveData<MutableList<StopSuggestionEntity>>(mutableListOf())
    val selectedStops: LiveData<MutableList<StopSuggestionEntity>> = _selectedStops

    private val _selectedStrategy = MutableLiveData<String>("fastest")
    val selectedStrategy: LiveData<String> = _selectedStrategy

    fun setStrategy(strategy: String) {
        _selectedStrategy.value = strategy
    }

    fun addStop(stop: StopSuggestionEntity) {
        val current = _selectedStops.value ?: mutableListOf()
        current.add(stop)
        _selectedStops.value = current
    }

    fun removeStop(stop: StopSuggestionEntity) {
        val current = _selectedStops.value ?: mutableListOf()
        current.remove(stop)
        _selectedStops.value = current
    }

    fun planTrip(origin: String, destination: String, avoidTolls: Boolean) {
        viewModelScope.launch {
            val result = routeRepository.calculateRoute(
                origin = origin,
                destination = destination,
                waypoints = _selectedStops.value ?: emptyList(),
                avoidTolls = avoidTolls,
                strategy = _selectedStrategy.value ?: "fastest"
            )
            _routeResult.postValue(result)
        }
    }
}
