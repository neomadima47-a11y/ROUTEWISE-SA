package com.routewise.sa.ui.alerts

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routewise.sa.data.model.IncidentEntity
import com.routewise.sa.data.repository.IncidentRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LiveAlertsViewModel(
    private val incidentRepository: IncidentRepository
) : ViewModel() {

    private val _selectedProvince = MutableLiveData<String>("All Provinces")
    val selectedProvince: LiveData<String> = _selectedProvince

    private val _incidents = MutableLiveData<List<IncidentEntity>>()
    val incidents: LiveData<List<IncidentEntity>> = _incidents

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    init {
        observeIncidents()
    }

    fun setProvinceFilter(province: String) {
        _selectedProvince.value = province
        observeIncidents()
    }

    fun observeIncidents() {
        viewModelScope.launch {
            _isLoading.value = true
            incidentRepository.getIncidents(_selectedProvince.value).collectLatest {
                _incidents.postValue(it)
                _isLoading.postValue(false)
            }
        }
    }

    fun upvoteIncident(id: String) {
        viewModelScope.launch {
            incidentRepository.upvote(id)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            incidentRepository.syncRemoteIncidents(_selectedProvince.value)
            _isLoading.value = false
        }
    }
}
