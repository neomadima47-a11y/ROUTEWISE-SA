package com.routewise.sa.ui.plan

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.routewise.sa.R
import com.routewise.sa.RouteWiseApplication
import com.routewise.sa.databinding.FragmentRoutePlanningBinding
import com.routewise.sa.ui.ViewModelFactory
import com.routewise.sa.ui.map.MapViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

class RoutePlanningFragment : Fragment() {

    private var _binding: FragmentRoutePlanningBinding? = null
    private val binding get() = _binding!!

    private val waypoints = mutableListOf<String>()
    private val httpClient = OkHttpClient()

    private val viewModel: MapViewModel by activityViewModels {
        val app = requireActivity().application as RouteWiseApplication
        ViewModelFactory(app.incidentRepository, app.userRepository, app.routeRepository)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRoutePlanningBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupCorridors()
        setupSearchAssistance()
        setupQuickAddChips()
        setupTransportModes()
        setupStrategies()
        setupCalculation()
    }

    private fun setupSearchAssistance() {
        val placeSuggestions = mutableListOf(
            "🛍️ Menlyn Park Shopping Centre, Pretoria",
            "🛍️ Sandton City, Johannesburg",
            "🛍️ Mall of Africa, Midrand",
            "✈️ Cape Town International Airport",
            "🏙️ V&A Waterfront, Cape Town",
            "🛍️ Gateway Theatre of Shopping, Durban",
            "✈️ OR Tambo International Airport, Kempton Park",
            "🛍️ Rosebank Mall, Johannesburg",
            "🏙️ Soweto, Johannesburg",
            "🛍️ Centurion Mall, Centurion",
            "🏙️ Hatfield, Pretoria",
            "🏙️ Stellenbosch, Western Cape",
            "🏙️ Umhlanga Rocks, KwaZulu-Natal",
            "🏙️ Polokwane CBD, Limpopo",
            "🏙️ Bloemfontein, Free State",
            "🏙️ Gqeberha (Port Elizabeth), Eastern Cape",
            "🏙️ Nelspruit (Mbombela), Mpumalanga",
            "🏙️ Rustenburg, North West",
            "⛽ Engen 1-Stop Midrand, N1",
            "⛽ Sasol Fuel Stop, Centurion",
            "🏥 Netcare Unitas Hospital, Centurion",
            "🏥 Steve Biko Academic Hospital, Pretoria"
        )

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, placeSuggestions)
        binding.etStartLocation.setAdapter(adapter)
        binding.etDestination.setAdapter(adapter)

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                if (query.length >= 3) {
                    fetchLiveSuggestions(query, adapter)
                }
            }
        }

        binding.etStartLocation.addTextChangedListener(watcher)
        binding.etDestination.addTextChangedListener(watcher)
    }

    private fun fetchLiveSuggestions(query: String, adapter: ArrayAdapter<String>) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode("$query, South Africa", "UTF-8")
                val url = "https://photon.komoot.io/api/?q=$encoded&limit=5"
                val request = Request.Builder().url(url).header("User-Agent", "RouteWiseSA/1.0").build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val jsonStr = response.body?.string() ?: return@use
                        val root = JSONObject(jsonStr)
                        val features = root.optJSONArray("features")
                        val liveList = mutableListOf<String>()
                        if (features != null) {
                            for (i in 0 until features.length()) {
                                val props = features.getJSONObject(i).getJSONObject("properties")
                                val name = props.optString("name")
                                val city = props.optString("city")
                                val state = props.optString("state")
                                val osmValue = props.optString("osm_value")

                                val categoryIcon = when {
                                    osmValue.contains("fuel") -> "⛽ "
                                    osmValue.contains("restaurant") || osmValue.contains("fast_food") -> "🍔 "
                                    osmValue.contains("mall") || osmValue.contains("shop") -> "🛍️ "
                                    osmValue.contains("hospital") || osmValue.contains("clinic") -> "🏥 "
                                    osmValue.contains("aeroway") -> "✈️ "
                                    osmValue.contains("parking") -> "🅿️ "
                                    else -> "🏙️ "
                                }

                                val label = listOfNotNull(
                                    name.takeIf { it.isNotEmpty() },
                                    city.takeIf { it.isNotEmpty() },
                                    state.takeIf { it.isNotEmpty() }
                                ).joinToString(", ")

                                if (label.isNotEmpty()) {
                                    val formattedLabel = "$categoryIcon$label"
                                    if (!liveList.contains(formattedLabel)) {
                                        liveList.add(formattedLabel)
                                    }
                                }
                            }
                        }
                        if (liveList.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                liveList.forEach { item ->
                                    if (adapter.getPosition(item) < 0) {
                                        adapter.add(item)
                                    }
                                }
                                adapter.notifyDataSetChanged()
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun setupCorridors() {
        binding.corridorN1.setOnClickListener {
            binding.etStartLocation.setText("Sandton CBD, Johannesburg")
            binding.etDestination.setText("Menlyn Park, Pretoria")
            Toast.makeText(requireContext(), "Selected N1 North Highway Corridor", Toast.LENGTH_SHORT).show()
        }

        binding.corridorN2.setOnClickListener {
            binding.etStartLocation.setText("V&A Waterfront, Cape Town")
            binding.etDestination.setText("Cape Town International Airport")
            Toast.makeText(requireContext(), "Selected N2 Settlers Way Corridor", Toast.LENGTH_SHORT).show()
        }

        binding.corridorM4.setOnClickListener {
            binding.etStartLocation.setText("Durban Beachfront, Durban")
            binding.etDestination.setText("Umhlanga Rocks, KZN")
            Toast.makeText(requireContext(), "Selected M4 Ruth First Corridor", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupQuickAddChips() {
        binding.chipAddFood.setOnClickListener {
            addWaypoint("Nando's Drive-Thru Midrand (+4 min detour)")
        }
        binding.chipAddCoffee.setOnClickListener {
            addWaypoint("Vida e Caffè (Engen 1-Stop) (+1 min detour)")
        }
        binding.chipAddGas.setOnClickListener {
            addWaypoint("Sasol Fuel Stop (+2 min detour)")
        }
        binding.chipAddAtm.setOnClickListener {
            addWaypoint("FNB Express ATM (+2 min detour)")
        }
    }

    private fun addWaypoint(name: String) {
        waypoints.add(name)
        val index = waypoints.size

        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(12, 12, 12, 12)
            setBackgroundResource(R.drawable.bg_rounded_slate)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 16) }
        }

        val badge = TextView(requireContext()).apply {
            text = index.toString()
            setTextColor(Color.parseColor("#10b981"))
            setBackgroundResource(R.drawable.bg_circle_slate)
            setPadding(12, 4, 12, 4)
            gravity = Gravity.CENTER
        }

        val title = TextView(requireContext()).apply {
            text = name
            setTextColor(Color.WHITE)
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 16
            }
        }

        val btnDelete = ImageView(requireContext()).apply {
            setImageResource(R.drawable.ic_close)
            setColorFilter(Color.parseColor("#94a3b8"))
            setOnClickListener {
                binding.containerWaypoints.removeView(row)
                waypoints.remove(name)
            }
        }

        row.addView(badge)
        row.addView(title)
        row.addView(btnDelete)
        binding.containerWaypoints.addView(row)
    }

    private fun setupTransportModes() {
        val modes = listOf(binding.modeCar, binding.modeTaxi, binding.modeBike, binding.modeWalk)
        modes.forEach { selected ->
            selected.setOnClickListener {
                modes.forEach { m ->
                    if (m == selected) {
                        m.setBackgroundResource(R.drawable.bg_card_selected)
                    } else {
                        m.setBackgroundResource(R.drawable.bg_rounded_slate)
                    }
                }
            }
        }
    }

    private fun setupStrategies() {
        val strats = listOf(binding.stratFastest, binding.stratShortest, binding.stratEco)
        strats.forEach { selected ->
            selected.setOnClickListener {
                strats.forEach { s ->
                    if (s == selected) {
                        s.setBackgroundResource(R.drawable.bg_severity_selected)
                        s.setTextColor(Color.WHITE)
                    } else {
                        s.setBackgroundResource(R.drawable.bg_rounded_slate)
                        s.setTextColor(Color.parseColor("#94a3b8"))
                    }
                }
            }
        }
    }

    private fun setupCalculation() {
        binding.btnCalculateRoute.setOnClickListener {
            val dest = binding.etDestination.text.toString().trim()
            val origin = binding.etStartLocation.text.toString().trim()
            if (dest.isEmpty()) {
                binding.etDestination.error = "Destination is required"
                return@setOnClickListener
            }

            viewModel.planTrip(origin, dest, false)

            Toast.makeText(requireContext(), "🚀 Route calculated! Starting turn-by-turn navigation...", Toast.LENGTH_LONG).show()
            findNavController().navigate(R.id.navigation_map)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
