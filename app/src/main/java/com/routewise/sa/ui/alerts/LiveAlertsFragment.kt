package com.routewise.sa.ui.alerts

import android.os.Bundle
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.routewise.sa.R
import com.routewise.sa.RouteWiseApplication
import com.routewise.sa.data.model.IncidentEntity
import com.routewise.sa.databinding.FragmentLiveAlertsBinding
import com.routewise.sa.ui.ViewModelFactory
import com.routewise.sa.ui.map.MapViewModel

class LiveAlertsFragment : Fragment() {

    private val viewModel: LiveAlertsViewModel by viewModels {
        val app = requireActivity().application as RouteWiseApplication
        ViewModelFactory(app.incidentRepository, app.userRepository)
    }

    private val mapViewModel: MapViewModel by activityViewModels {
        val app = requireActivity().application as RouteWiseApplication
        ViewModelFactory(app.incidentRepository, app.userRepository, app.routeRepository)
    }

    private var _binding: FragmentLiveAlertsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var adapter: IncidentAdapter
    private var fullList: List<IncidentEntity> = emptyList()

    private val provinces = arrayOf(
        "All", "Gauteng", "Western Cape", "KwaZulu-Natal",
        "Eastern Cape", "Free State", "Limpopo", "Mpumalanga", "North West", "Northern Cape"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLiveAlertsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupProvinceChips()
        setupFilters()
        observeData()
    }

    override fun onResume() {
        super.onResume()
        viewModel.observeIncidents()
        applyFilters()
    }

    private fun observeData() {
        viewModel.incidents.observe(viewLifecycleOwner) { incidents ->
            fullList = incidents
            applyFilters()
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            // Update UI to show loading state if needed
        }
    }

    private fun setupRecyclerView() {
        adapter = IncidentAdapter(
            onUpvote = { incident ->
                Toast.makeText(requireContext(), "Marked as helpful!", Toast.LENGTH_SHORT).show()
                viewModel.upvoteIncident(incident.id)
            },
            onDismiss = { incident ->
                // Local dismiss logic if needed
            },
            onViewOnMap = { incident ->
                mapViewModel.focusIncidentOnMap(incident)
                findNavController().navigate(R.id.navigation_map)
            }
        )
        binding.rvIncidents.layoutManager = LinearLayoutManager(requireContext())
        binding.rvIncidents.adapter = adapter
    }

    private fun setupProvinceChips() {
        provinces.forEach { province ->
            val chip = Chip(requireContext()).apply {
                text = province
                isCheckable = true
                isChecked = province == "All"
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        val filter = if (province == "All") "All Provinces" else province
                        viewModel.setProvinceFilter(filter)
                    }
                }
            }
            binding.chipGroupProvinces.addView(chip)
        }
    }

    private fun setupFilters() {
        binding.etSearchAlerts.addTextChangedListener { applyFilters() }
        
        binding.chipGroupTypes.setOnCheckedStateChangeListener { _, _ ->
            applyFilters()
        }

        binding.btnClearAll.setOnClickListener {
            adapter.submitList(emptyList())
            binding.tvActiveCount.text = "0 Active"
        }
    }

    private fun applyFilters() {
        val query = binding.etSearchAlerts.text.toString().lowercase()
        val twelveHoursAgo = System.currentTimeMillis() - (12 * 60 * 60 * 1000L)
        
        // Province Filter
        val checkedProvinceId = binding.chipGroupProvinces.checkedChipId
        val selectedProvince = if (checkedProvinceId != View.NO_ID) {
            binding.chipGroupProvinces.findViewById<Chip>(checkedProvinceId).text.toString()
        } else "All"

        // Type Filter
        val selectedType = when (binding.chipGroupTypes.checkedChipId) {
            R.id.chipTypeAccident -> "Accident"
            R.id.chipTypeRobot -> "Broken Robot"
            R.id.chipTypePothole -> "Pothole"
            R.id.chipTypePolice -> "Police Trap"
            else -> "All"
        }

        val filtered = fullList.filter { incident ->
            val isWithin12Hours = incident.timestamp >= twelveHoursAgo

            val matchesQuery = incident.roadName.lowercase().contains(query) || 
                             incident.description.lowercase().contains(query) ||
                             incident.city.lowercase().contains(query)
            
            val matchesProvince = selectedProvince == "All" || incident.province == selectedProvince
            val matchesType = selectedType == "All" || incident.type == selectedType

            isWithin12Hours && matchesQuery && matchesProvince && matchesType
        }

        adapter.submitList(filtered)
        binding.tvActiveCount.text = getString(R.string.active_count_format, filtered.size)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
