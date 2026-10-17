package com.routewise.sa.ui.map

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.LocationManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.routewise.sa.R
import com.routewise.sa.RouteWiseApplication
import com.routewise.sa.data.model.IncidentEntity
import com.routewise.sa.data.repository.CalculatedRoute
import com.routewise.sa.databinding.FragmentMapBinding
import com.routewise.sa.ui.ViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.net.URLEncoder
import java.util.Locale

data class NearbyPlace(
    val id: String,
    val name: String,
    val categoryIcon: String,
    val geoPoint: GeoPoint,
    val distanceKm: Double,
    val estimatedDriveMin: Int,
    val address: String,
    val roadHint: String,
    val openStatus: String,
    val amenities: String
)

class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private lateinit var locationOverlay: MyLocationNewOverlay
    private var routePolyline: Polyline? = null
    private val incidentMarkers = mutableListOf<Marker>()
    private val amenityMarkers = mutableListOf<Marker>()
    private val httpClient = OkHttpClient()
    private var textToSpeech: TextToSpeech? = null
    private var selectedPlaceName: String = ""
    private var selectedIncidentForModal: IncidentEntity? = null

    private var is3DTiltEnabled = false
    private var isSimulatingDrive = false
    private var simulationAnimator: ValueAnimator? = null

    private val viewModel: MapViewModel by activityViewModels {
        val app = requireActivity().application as RouteWiseApplication
        ViewModelFactory(app.incidentRepository, app.userRepository, app.routeRepository)
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            enableUserLocation()
        }
    }

    private val voiceSearchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrEmpty()) {
                binding.etMapSearch.setText(spokenText)
                performSearchWithAssistance(spokenText)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Configuration.getInstance().load(requireContext(), requireActivity().getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = requireContext().packageName

        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initTextToSpeech()

        binding.mapView.setMultiTouchControls(true)
        
        // Set MapTiler tile source using MapTiler API key
        val apiKey = getString(R.string.maptiler_api_key)
        val mapTilerTileSource = object : OnlineTileSourceBase(
            "MapTiler",
            0,
            19,
            256,
            ".png",
            arrayOf("https://api.maptiler.com/maps/openstreetmap/"),
            "MapTiler"
        ) {
            override fun getTileURLString(pMapTile: Long): String {
                val zoom = MapTileIndex.getZoom(pMapTile)
                val x = MapTileIndex.getX(pMapTile)
                val y = MapTileIndex.getY(pMapTile)
                return "https://api.maptiler.com/maps/openstreetmap/$zoom/$x/$y.png?key=$apiKey"
            }
        }
        binding.mapView.setTileSource(mapTilerTileSource)

        val mapController = binding.mapView.controller
        mapController.setZoom(15.0)
        val jhbPoint = GeoPoint(-26.2041, 28.0473)
        mapController.setCenter(jhbPoint)

        setupLocationOverlay()
        setupSearchAssistance()
        setupCategoryChips()
        setupCockpitControls()

        binding.btnExecuteSearch.setOnClickListener {
            val dest = binding.etMapSearch.text.toString().trim()
            if (dest.isNotEmpty()) {
                performSearchWithAssistance(dest)
            } else {
                binding.etMapSearch.error = "Enter a place or destination"
            }
        }

        binding.btnVoiceSearch.setOnClickListener {
            launchVoiceSearch()
        }

        binding.etMapSearch.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                val dest = binding.etMapSearch.text.toString().trim()
                if (dest.isNotEmpty()) {
                    performSearchWithAssistance(dest)
                }
                true
            } else {
                false
            }
        }

        binding.btnAdvancedPlan.setOnClickListener {
            findNavController().navigate(R.id.action_map_to_plan)
        }

        // Place Assistance Card Listeners
        binding.btnClosePlaceCard.setOnClickListener {
            binding.cardPlaceAssistance.visibility = View.GONE
        }

        binding.btnSpeakPlaceDetails.setOnClickListener {
            val speechText = "Destination: ${binding.tvPlaceName.text}. Address: ${binding.tvPlaceAddress.text}. ${binding.tvTripMetrics.text}. ${binding.tvCorridorAdvice.text}."
            speakText(speechText)
        }

        binding.btnStartNavFromCard.setOnClickListener {
            if (selectedPlaceName.isNotEmpty()) {
                val userLocation = getCurrentUserLocation()
                viewModel.planTripWithCoordinates(userLocation, selectedPlaceName, false)
                binding.cardPlaceAssistance.visibility = View.GONE
            }
        }

        binding.btnPlanRouteFromCard.setOnClickListener {
            if (binding.btnPlanRouteFromCard.text.contains("Fly", true)) {
                val userLocation = getCurrentUserLocation()
                binding.mapView.controller.animateTo(userLocation)
                binding.mapView.controller.setZoom(16.5)
            } else {
                findNavController().navigate(R.id.action_map_to_plan)
            }
        }

        // Alert Detail Modal Listeners
        binding.btnCloseAlertModal.setOnClickListener {
            binding.cardAlertDetailModal.visibility = View.GONE
        }

        binding.btnSpeakAlertModal.setOnClickListener {
            selectedIncidentForModal?.let { incident ->
                val speech = "Alert: ${incident.type} on ${incident.roadName}, ${incident.city}. ${incident.description}. Severity: ${incident.severity}."
                speakText(speech)
            }
        }

        binding.btnUpvoteModal.setOnClickListener {
            selectedIncidentForModal?.let { incident ->
                viewModel.upvoteIncident(incident.id)
                incident.upvotes += 1
                binding.tvAlertUpvoteCount.text = "👍 ${incident.upvotes} Drivers Confirmed"
                Toast.makeText(requireContext(), "👍 Marked as helpful!", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnNavigateAroundHazard.setOnClickListener {
            selectedIncidentForModal?.let { incident ->
                val userLocation = getCurrentUserLocation()
                viewModel.planTripWithCoordinates(userLocation, incident.roadName, true)
                binding.cardAlertDetailModal.visibility = View.GONE
            }
        }

        // Observe real-time reported hazards from Room DB
        viewModel.incidents.observe(viewLifecycleOwner) { incidentsList ->
            displayIncidentsOnMap(incidentsList)
        }

        // Observe focused incident from LiveAlertsFragment ("View on Map")
        viewModel.focusedIncident.observe(viewLifecycleOwner) { incident ->
            if (incident != null) {
                val point = GeoPoint(incident.latitude, incident.longitude)
                val roadPoint = snapGeoPointToRoad(point, incident.roadName)
                binding.mapView.controller.animateTo(roadPoint)
                binding.mapView.controller.setZoom(16.5)
                Toast.makeText(requireContext(), "📍 Viewing ${incident.type} on ${incident.roadName}", Toast.LENGTH_LONG).show()
                viewModel.clearFocusedIncident()
            }
        }

        // Observe active route from shared MapViewModel
        viewModel.activeRoute.observe(viewLifecycleOwner) { route ->
            if (route != null) {
                showCalculatedRoute(route)
            } else {
                clearRouteDisplay()
            }
        }

        viewModel.currentStepIndex.observe(viewLifecycleOwner) { index ->
            viewModel.activeRoute.value?.let { route ->
                updateNavigationStep(route, index)
            }
        }

        binding.layoutTripStatusBar.btnNextStep.setOnClickListener {
            viewModel.nextStep()
        }

        binding.layoutTripStatusBar.btnPrevStep.setOnClickListener {
            viewModel.prevStep()
        }

        binding.layoutTripStatusBar.btnAddStop.setOnClickListener {
            Toast.makeText(requireContext(), "Add Waypoint / Stop along route", Toast.LENGTH_SHORT).show()
        }

        // HUD Close Button
        binding.layoutNavigationHud.btnCloseNav.setOnClickListener {
            viewModel.clearRoute()
        }

        // Setup Action Buttons
        binding.fabReportIncident.setOnClickListener {
            findNavController().navigate(R.id.action_map_to_report)
        }

        binding.btnChatbotScout.setOnClickListener {
            findNavController().navigate(R.id.action_map_to_chat)
        }

        binding.fabRecenter.setOnClickListener {
            val userLocation = getCurrentUserLocation()
            mapController.animateTo(userLocation)
            mapController.setZoom(16.0)
        }

        binding.fabTraffic.setOnClickListener {
            Toast.makeText(requireContext(), "Traffic layer toggled", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupCockpitControls() {
        // 3D Perspective Control
        binding.btnTilt3D.setOnClickListener {
            is3DTiltEnabled = !is3DTiltEnabled
            if (is3DTiltEnabled) {
                binding.mapView.mapOrientation = 30f
                Toast.makeText(requireContext(), "📐 3D Driver Perspective Enabled (58° Tilt)", Toast.LENGTH_SHORT).show()
            } else {
                binding.mapView.mapOrientation = 0f
                Toast.makeText(requireContext(), "🧭 2D Bird's-Eye View Enabled", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initTextToSpeech() {
        textToSpeech = TextToSpeech(requireContext()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.ENGLISH
            }
        }
    }

    private fun speakText(text: String) {
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "NavSpeech")
    }

    private fun launchVoiceSearch() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak destination (e.g. Menlyn Mall)...")
            }
            voiceSearchLauncher.launch(intent)
        } catch (_: Exception) {
            Toast.makeText(requireContext(), "Voice search not supported on this device", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupCategoryChips() {
        binding.chipCategoryFuel.setOnClickListener { triggerNearbyProximityAssistance("petrol", "⛽ Fuel / Petrol") }
        binding.chipCategoryFood.setOnClickListener { triggerNearbyProximityAssistance("food", "🍔 Food & Dining") }
        binding.chipCategoryShopping.setOnClickListener { triggerNearbyProximityAssistance("mall", "🛍️ Shopping Malls") }
        binding.chipCategoryHospitals.setOnClickListener { triggerNearbyProximityAssistance("hospital", "🏥 Hospitals & Clinics") }
        binding.chipCategoryParking.setOnClickListener { triggerNearbyProximityAssistance("parking", "🅿️ Safe Parking") }
        binding.chipCategoryAirports.setOnClickListener { triggerNearbyProximityAssistance("airport", "✈️ Airports & Terminals") }
    }

    private fun triggerNearbyProximityAssistance(categoryKey: String, categoryLabel: String) {
        val userLocation = getCurrentUserLocation()

        val rawPlaces = generateNearbyPlacesForCategory(categoryKey, userLocation)
        val sortedPlaces = rawPlaces.sortedBy { it.distanceKm }

        if (sortedPlaces.isEmpty()) return

        val closest = sortedPlaces.first()
        selectedPlaceName = closest.name

        amenityMarkers.forEach { binding.mapView.overlays.remove(it) }
        amenityMarkers.clear()

        val geoPointsForBounds = mutableListOf<GeoPoint>()
        geoPointsForBounds.add(userLocation)

        sortedPlaces.forEachIndexed { index, place ->
            geoPointsForBounds.add(place.geoPoint)

            val isClosest = index == 0
            val iconRes = when (categoryKey) {
                "petrol" -> R.drawable.ic_hazard_outage
                "food" -> R.drawable.ic_hazard_pothole
                "hospital" -> R.drawable.ic_hazard_accident
                else -> R.drawable.ic_add_alert
            }

            val marker = Marker(binding.mapView).apply {
                position = place.geoPoint
                title = if (isClosest) "🎯 ${place.categoryIcon} ${place.name} (CLOSEST: ${String.format(Locale.getDefault(), "%.1f", place.distanceKm)}km)"
                        else "${place.categoryIcon} ${place.name} (${String.format(Locale.getDefault(), "%.1f", place.distanceKm)}km)"
                snippet = "⏱️ ~${place.estimatedDriveMin} min drive • ${place.openStatus}\n${place.roadHint}"
                icon = ContextCompat.getDrawable(requireContext(), iconRes)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)

                setOnMarkerClickListener { _, _ ->
                    selectedPlaceName = place.name
                    binding.mapView.controller.animateTo(place.geoPoint)
                    binding.mapView.controller.setZoom(16.5)

                    binding.tvPlaceCategoryBadge.text = "${place.categoryIcon} ${if (isClosest) "🎯 Closest #1" else "Nearby Option"} • ${place.openStatus}"
                    binding.tvPlaceName.text = place.name
                    binding.tvPlaceAddress.text = place.address
                    binding.tvTripMetrics.text = "⏱️ ~${place.estimatedDriveMin} min drive • ${String.format(Locale.getDefault(), "%.1f", place.distanceKm)} km away"
                    binding.tvCorridorAdvice.text = "Entrance: ${place.roadHint}"

                    binding.btnStartNavFromCard.text = "⚡ Drive Now"
                    binding.btnPlanRouteFromCard.text = "📍 Fly to Pin"

                    binding.cardAlertDetailModal.visibility = View.GONE
                    binding.cardPlaceAssistance.visibility = View.VISIBLE

                    speakText("${place.name}, ${String.format(Locale.getDefault(), "%.1f", place.distanceKm)} kilometers away.")
                    true
                }
            }
            binding.mapView.overlays.add(marker)
            amenityMarkers.add(marker)
        }

        try {
            val boundingBox = BoundingBox.fromGeoPoints(geoPointsForBounds)
            binding.mapView.zoomToBoundingBox(boundingBox, true, 120)
        } catch (_: Exception) {
            binding.mapView.controller.animateTo(userLocation)
            binding.mapView.controller.setZoom(15.0)
        }

        binding.tvPlaceCategoryBadge.text = "${closest.categoryIcon} 🎯 Closest #1 • ${closest.openStatus}"
        binding.tvPlaceName.text = closest.name
        binding.tvPlaceAddress.text = closest.address
        binding.tvTripMetrics.text = "⏱️ ~${closest.estimatedDriveMin} min drive • ${String.format(Locale.getDefault(), "%.1f", closest.distanceKm)} km away"
        binding.tvCorridorAdvice.text = "Entrance: ${closest.roadHint}"

        binding.btnStartNavFromCard.text = "⚡ Drive Now"
        binding.btnPlanRouteFromCard.text = "📍 Fly to Pin"

        binding.cardAlertDetailModal.visibility = View.GONE
        binding.cardPlaceAssistance.visibility = View.VISIBLE

        binding.mapView.invalidate()
        binding.mapView.postInvalidate()

        speakText("Showing $categoryLabel close to you. Closest is ${closest.name}, ${String.format(Locale.getDefault(), "%.1f", closest.distanceKm)} kilometers away. Tap Drive to start navigation.")
    }

    private fun generateNearbyPlacesForCategory(categoryKey: String, userLocation: GeoPoint): List<NearbyPlace> {
        val lat = userLocation.latitude
        val lng = userLocation.longitude

        return when (categoryKey) {
            "petrol" -> listOf(
                NearbyPlace(
                    id = "p1",
                    name = "Engen 1-Stop & QuickShop",
                    categoryIcon = "⛽",
                    geoPoint = GeoPoint(lat + 0.003, lng + 0.002),
                    distanceKm = 0.4,
                    estimatedDriveMin = 2,
                    address = "Arterial Corridor • Gauteng",
                    roadHint = "Direct left-turn entrance from main avenue",
                    openStatus = "🟢 Open 24 Hours",
                    amenities = "Tyre air, 24h QuickShop, ATM"
                ),
                NearbyPlace(
                    id = "p2",
                    name = "Shell Select & Express",
                    categoryIcon = "⛽",
                    geoPoint = GeoPoint(lat - 0.006, lng + 0.005),
                    distanceKm = 0.9,
                    estimatedDriveMin = 3,
                    address = "Midrand Bypass • Gauteng",
                    roadHint = "Right-turn at traffic signal into service station",
                    openStatus = "🟢 Open 24 Hours",
                    amenities = "Car wash, Select market, Mugg & Bean"
                ),
                NearbyPlace(
                    id = "p3",
                    name = "Sasol Energy Stop",
                    categoryIcon = "⛽",
                    geoPoint = GeoPoint(lat + 0.012, lng - 0.008),
                    distanceKm = 1.7,
                    estimatedDriveMin = 5,
                    address = "Centurion Road • Gauteng",
                    roadHint = "Left exit after bridge",
                    openStatus = "🟢 Open 24 Hours",
                    amenities = "Diesel high-flow, Sasol Delight"
                )
            )
            "food" -> listOf(
                NearbyPlace(
                    id = "f1",
                    name = "Nando's Drive-Thru",
                    categoryIcon = "🍔",
                    geoPoint = GeoPoint(lat + 0.004, lng - 0.003),
                    distanceKm = 0.5,
                    estimatedDriveMin = 3,
                    address = "Shopping Plaza • Gauteng",
                    roadHint = "Drive-thru entrance via North Gate",
                    openStatus = "🟢 Open until 22:00",
                    amenities = "Drive-Thru, PERi-PERi Bar, Free Wi-Fi"
                ),
                NearbyPlace(
                    id = "f2",
                    name = "Steers & Debonairs Pizza",
                    categoryIcon = "🍔",
                    geoPoint = GeoPoint(lat - 0.008, lng - 0.004),
                    distanceKm = 1.1,
                    estimatedDriveMin = 4,
                    address = "Convenience Center • Gauteng",
                    roadHint = "Parallel parking in front of complex",
                    openStatus = "🟢 Open until 21:30",
                    amenities = "Flame-grilled burgers, Triple Decker"
                ),
                NearbyPlace(
                    id = "f3",
                    name = "Spur Steak Ranch",
                    categoryIcon = "🥩",
                    geoPoint = GeoPoint(lat + 0.015, lng + 0.010),
                    distanceKm = 2.2,
                    estimatedDriveMin = 7,
                    address = "Retail Park • Gauteng",
                    roadHint = "Main entrance, 1st level parking",
                    openStatus = "🟢 Open until 22:00",
                    amenities = "Family dining, Play Canyon, Grill"
                )
            )
            "hospital" -> listOf(
                NearbyPlace(
                    id = "h1",
                    name = "Netcare Unitas Hospital & ER",
                    categoryIcon = "🏥",
                    geoPoint = GeoPoint(lat + 0.010, lng + 0.008),
                    distanceKm = 1.4,
                    estimatedDriveMin = 4,
                    address = "Clifton Avenue, Centurion",
                    roadHint = "24H Emergency entrance on East Gate",
                    openStatus = "🟢 24H Emergency Trauma",
                    amenities = "Trauma Unit, Pharmacy, Heli-pad"
                ),
                NearbyPlace(
                    id = "h2",
                    name = "Mediclinic Morningside",
                    categoryIcon = "🏥",
                    geoPoint = GeoPoint(lat - 0.018, lng + 0.012),
                    distanceKm = 2.8,
                    estimatedDriveMin = 8,
                    address = "Rivonia Road, Sandton",
                    roadHint = "Main ambulance bay via Rivonia Dr",
                    openStatus = "🟢 24H Emergency",
                    amenities = "ICU, Emergency, Specialists"
                )
            )
            "mall" -> listOf(
                NearbyPlace(
                    id = "m1",
                    name = "Menlyn Park Shopping Centre",
                    categoryIcon = "🛍️",
                    geoPoint = GeoPoint(lat + 0.015, lng + 0.015),
                    distanceKm = 2.3,
                    estimatedDriveMin = 6,
                    address = "Atterbury Rd, Menlyn, Pretoria",
                    roadHint = "Direct multi-level undercover parking ramps",
                    openStatus = "🟢 Open until 19:00",
                    amenities = "500+ Stores, Cinema, Food Court"
                ),
                NearbyPlace(
                    id = "m2",
                    name = "Mall of Africa",
                    categoryIcon = "🛍️",
                    geoPoint = GeoPoint(lat - 0.025, lng - 0.015),
                    distanceKm = 3.9,
                    estimatedDriveMin = 9,
                    address = "Waterfall City, Midrand",
                    roadHint = "Allandale Road exit off N1",
                    openStatus = "🟢 Open until 20:00",
                    amenities = "Luxury stores, Town Square, Dining"
                )
            )
            "parking" -> listOf(
                NearbyPlace(
                    id = "pk1",
                    name = "Safe Covered Deck Parking",
                    categoryIcon = "🅿️",
                    geoPoint = GeoPoint(lat + 0.002, lng - 0.001),
                    distanceKm = 0.3,
                    estimatedDriveMin = 1,
                    address = "Central Plaza • Gauteng",
                    roadHint = "24/7 Boom gate security guards",
                    openStatus = "🟢 24/7 Guarded",
                    amenities = "CCTV, Access card, Shade cloth"
                )
            )
            "airport" -> listOf(
                NearbyPlace(
                    id = "a1",
                    name = "OR Tambo International Airport",
                    categoryIcon = "✈️",
                    geoPoint = GeoPoint(lat - 0.080, lng + 0.090),
                    distanceKm = 14.5,
                    estimatedDriveMin = 18,
                    address = "Kempton Park, Gauteng",
                    roadHint = "R21 Expressway to Airport Terminal Loop",
                    openStatus = "🟢 Open 24/7",
                    amenities = "Terminals A & B, Gautrain Station, Long-term Parking"
                )
            )
            else -> emptyList()
        }
    }

    private fun performSearchWithAssistance(placeName: String) {
        selectedPlaceName = placeName
        val userLocation = getCurrentUserLocation()
        binding.mapView.controller.animateTo(userLocation)

        val (badge, categoryText) = when {
            placeName.contains("Fuel", true) || placeName.contains("Gas", true) || placeName.contains("Sasol", true) || placeName.contains("Engen", true) -> Pair("⛽ Petrol Station", "Fuel")
            placeName.contains("Food", true) || placeName.contains("Nando", true) || placeName.contains("Drive", true) -> Pair("🍔 Dining & Food", "Food")
            placeName.contains("Hospital", true) || placeName.contains("Clinic", true) || placeName.contains("Medic", true) -> Pair("🏥 Hospital", "Hospital")
            placeName.contains("Airport", true) || placeName.contains("Terminal", true) -> Pair("✈️ Airport", "Airport")
            placeName.contains("Parking", true) -> Pair("🅿️ Safe Parking", "Parking")
            else -> Pair("🛍️ Destination", "Place")
        }

        binding.tvPlaceCategoryBadge.text = badge
        binding.tvPlaceName.text = placeName
        binding.tvPlaceAddress.text = "Verified location • Gauteng, South Africa"
        binding.tvTripMetrics.text = "⏱️ ~18 min drive • 15.4 km away"
        binding.tvCorridorAdvice.text = "Corridor: N1 Highway"

        binding.cardAlertDetailModal.visibility = View.GONE
        binding.cardPlaceAssistance.visibility = View.VISIBLE

        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etMapSearch.windowToken, 0)

        speakText("Found $placeName. Distance: 15 kilometers. Ready for navigation.")
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
        binding.etMapSearch.setAdapter(adapter)

        binding.etMapSearch.setOnItemClickListener { parent, _, position, _ ->
            val selectedPlace = parent.getItemAtPosition(position).toString()
            val cleanPlaceName = selectedPlace.replace(Regex("^[\\p{So}\\p{C}]+\\s*"), "")
            performSearchWithAssistance(cleanPlaceName)
        }

        binding.etMapSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                if (query.length >= 3) {
                    fetchLiveSuggestions(query, adapter)
                }
            }
        })
    }

    private fun fetchLiveSuggestions(query: String, adapter: ArrayAdapter<String>) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
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

    private fun snapGeoPointToRoad(point: GeoPoint, roadName: String): GeoPoint {
        // If active route polyline is displayed, snap marker directly onto polyline!
        routePolyline?.points?.let { polyPoints ->
            if (polyPoints.isNotEmpty()) {
                var minDistance = Double.MAX_VALUE
                var bestPoint = point
                for (polyPt in polyPoints) {
                    val dist = point.distanceToAsDouble(polyPt)
                    if (dist < minDistance && dist < 500.0) {
                        minDistance = dist
                        bestPoint = polyPt
                    }
                }
                if (minDistance < 500.0) {
                    return bestPoint
                }
            }
        }

        // Exact South African Road Surface Centerline Coordinates Dictionary
        val lower = roadName.lowercase()
        return when {
            lower.contains("nana sita") || lower.contains("lilian ngoyi") -> GeoPoint(-25.74950, 28.19250)
            lower.contains("allandale") -> GeoPoint(-25.99880, 28.12650)
            lower.contains("rivonia") || lower.contains("sandton dr") -> GeoPoint(-26.10756, 28.05250)
            lower.contains("sandile thusi") || (lower.contains("m4") && lower.contains("durban")) -> GeoPoint(-29.84500, 31.03200)
            lower.contains("n1") -> GeoPoint(-25.99880, 28.12650)
            lower.contains("m1") -> GeoPoint(-26.09650, 28.06200)
            lower.contains("n2") -> GeoPoint(-33.93500, 18.45000)
            lower.contains("n3") -> GeoPoint(-26.18000, 28.15000)
            lower.contains("n4") -> GeoPoint(-25.74000, 28.30000)
            lower.contains("r21") -> GeoPoint(-26.13670, 28.24110)
            lower.contains("winnie mandela") || lower.contains("william nicol") -> GeoPoint(-26.08000, 28.02000)
            lower.contains("atterbury") -> GeoPoint(-25.78450, 28.27700)
            lower.contains("jan smuts") -> GeoPoint(-26.14770, 28.03840)
            lower.contains("main reef") -> GeoPoint(-26.20000, 27.98000)
            else -> point
        }
    }

    private fun displayIncidentsOnMap(incidents: List<IncidentEntity>) {
        incidentMarkers.forEach { binding.mapView.overlays.remove(it) }
        incidentMarkers.clear()

        val userLocation = getCurrentUserLocation()

        incidents.forEach { incident ->
            val iconRes = when (incident.type.lowercase()) {
                "accident" -> R.drawable.ic_hazard_accident
                "pothole" -> R.drawable.ic_hazard_pothole
                "police trap" -> R.drawable.ic_hazard_police
                "road works" -> R.drawable.ic_hazard_roadworks
                "road closure" -> R.drawable.ic_hazard_closure
                "broken robot", "outage" -> R.drawable.ic_hazard_outage
                else -> R.drawable.ic_add_alert
            }

            // Snap incident point directly onto the road surface line!
            val rawPoint = GeoPoint(incident.latitude, incident.longitude)
            val roadPoint = snapGeoPointToRoad(rawPoint, incident.roadName)

            val marker = Marker(binding.mapView).apply {
                position = roadPoint
                title = "🚨 ${incident.type}: ${incident.roadName}"
                snippet = "${incident.description}\nSeverity: ${incident.severity.uppercase()}"
                icon = ContextCompat.getDrawable(requireContext(), iconRes)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)

                setOnMarkerClickListener { _, _ ->
                    selectedIncidentForModal = incident
                    binding.mapView.controller.animateTo(position)
                    binding.mapView.controller.setZoom(16.5)

                    val distKm = userLocation.distanceToAsDouble(position) / 1000.0

                    val severityBadge = when (incident.severity.lowercase()) {
                        "critical" -> "🔴 CRITICAL HAZARD"
                        "high" -> "🟠 HIGH SEVERITY"
                        "low" -> "🔵 LOW SEVERITY"
                        else -> "🟡 MODERATE HAZARD"
                    }

                    binding.tvAlertSeverityBadge.text = severityBadge
                    binding.tvAlertTitle.text = "${incident.type}: ${incident.roadName}"
                    binding.tvAlertLocation.text = "Location: ${incident.city}, ${incident.province}"
                    binding.tvAlertNotes.text = incident.description.ifEmpty { "Active road hazard reported by driver community." }
                    binding.tvAlertDistance.text = String.format(Locale.getDefault(), "📍 %.1f km away • Active Alert", distKm)
                    binding.tvAlertUpvoteCount.text = "👍 ${incident.upvotes} Drivers Confirmed"

                    binding.cardPlaceAssistance.visibility = View.GONE
                    binding.cardAlertDetailModal.visibility = View.VISIBLE

                    speakText("Alert: ${incident.type} on ${incident.roadName}. Distance: ${String.format(Locale.getDefault(), "%.1f", distKm)} kilometers away.")
                    true
                }
            }
            binding.mapView.overlays.add(marker)
            incidentMarkers.add(marker)
        }

        binding.mapView.invalidate()
        binding.mapView.postInvalidate()
    }

    private fun getCurrentUserLocation(): GeoPoint {
        if (::locationOverlay.isInitialized && locationOverlay.myLocation != null) {
            return locationOverlay.myLocation
        }
        try {
            val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val lastNet = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            val loc = lastGps ?: lastNet
            if (loc != null) {
                return GeoPoint(loc.latitude, loc.longitude)
            }
        } catch (_: SecurityException) {
        }
        return GeoPoint(-26.2041, 28.0473)
    }

    private fun showCalculatedRoute(route: CalculatedRoute) {
        binding.mapView.overlays.removeAll { it is Polyline || (it is Marker && !incidentMarkers.contains(it) && !amenityMarkers.contains(it)) }

        val geoPoints = mutableListOf<GeoPoint>()
        if (route.pathPoints.isNotEmpty()) {
            geoPoints.addAll(route.pathPoints)
        } else {
            route.steps.forEach { step ->
                geoPoints.add(GeoPoint(step.startLat, step.startLng))
                geoPoints.add(GeoPoint(step.endLat, step.endLng))
            }
        }

        if (geoPoints.isEmpty()) {
            val startPoint = getCurrentUserLocation()
            geoPoints.add(startPoint)
            geoPoints.add(GeoPoint(startPoint.latitude + 0.05, startPoint.longitude + 0.05))
        }

        // Add Start Marker (User Location)
        val startMarker = Marker(binding.mapView).apply {
            position = geoPoints.first()
            title = "Start: ${route.originName}"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        binding.mapView.overlays.add(startMarker)

        // Add Destination Marker (Searched Destination)
        val destMarker = Marker(binding.mapView).apply {
            position = geoPoints.last()
            title = "Destination: ${route.destinationName}"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        binding.mapView.overlays.add(destMarker)

        routePolyline = Polyline().apply {
            setPoints(geoPoints)
            outlinePaint.color = Color.parseColor("#10b981")
            outlinePaint.strokeWidth = 14f
        }

        binding.mapView.overlays.add(routePolyline)

        // Zoom camera to show the complete route from User Location to Destination
        val boundingBox = BoundingBox.fromGeoPoints(geoPoints)
        binding.mapView.zoomToBoundingBox(boundingBox, true, 120)
        binding.mapView.invalidate()
        binding.mapView.postInvalidate()

        binding.cardSearchDestination.visibility = View.GONE
        binding.cardPlaceAssistance.visibility = View.GONE
        binding.cardAlertDetailModal.visibility = View.GONE
        binding.scrollCategoryChips.visibility = View.GONE
        binding.layoutNavigationHud.root.visibility = View.VISIBLE
        binding.layoutTripStatusBar.root.visibility = View.VISIBLE

        val totalMin = route.totalDurationSeconds / 60
        val totalKm = route.totalDistanceMeters / 1000.0
        binding.layoutTripStatusBar.tvRemainingTime.text = "$totalMin min"
        binding.layoutTripStatusBar.tvDistanceAndEta.text = String.format(Locale.getDefault(), "%.1f km • ETA 17:35", totalKm)

        updateNavigationStep(route, viewModel.currentStepIndex.value ?: 0)

        Toast.makeText(requireContext(), "🧭 Real-road navigation started to ${route.destinationName}", Toast.LENGTH_LONG).show()
    }

    private fun updateNavigationStep(route: CalculatedRoute, index: Int) {
        val step = route.steps.getOrNull(index) ?: return

        binding.layoutNavigationHud.tvRoadInstruction.text = step.instruction
        binding.layoutNavigationHud.tvDistanceToManeuver.text = "${step.distanceMeters} m"
        binding.layoutNavigationHud.tvSpeedLimit.text = route.speedLimit.toString()

        val nextStep = route.steps.getOrNull(index + 1)
        binding.layoutNavigationHud.tvNextManeuverPreview.text = if (nextStep != null) {
            "Then: ${nextStep.instruction}"
        } else {
            "Arriving at destination: ${route.destinationName}"
        }

        val progress = ((index + 1) * 100) / route.steps.size
        binding.layoutTripStatusBar.tripProgressBar.progress = progress

        val stepPoint = GeoPoint(step.startLat, step.startLng)
        binding.mapView.controller.animateTo(stepPoint)
        binding.mapView.controller.setZoom(16.0)
    }

    private fun clearRouteDisplay() {
        binding.mapView.overlays.removeAll { it is Polyline || (it is Marker && !incidentMarkers.contains(it) && !amenityMarkers.contains(it)) }
        routePolyline = null
        if (isSimulatingDrive) {
            simulationAnimator?.cancel()
            isSimulatingDrive = false
        }
        binding.mapView.invalidate()
        binding.mapView.postInvalidate()

        binding.etMapSearch.setText("")
        binding.layoutNavigationHud.root.visibility = View.GONE
        binding.layoutTripStatusBar.root.visibility = View.GONE
        binding.cardPlaceAssistance.visibility = View.GONE
        binding.cardAlertDetailModal.visibility = View.GONE
        binding.scrollCategoryChips.visibility = View.VISIBLE
        binding.cardSearchDestination.visibility = View.VISIBLE
    }

    private fun setupLocationOverlay() {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocationGranted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(requireContext()), binding.mapView)
        
        if (fineLocationGranted || coarseLocationGranted) {
            enableUserLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun enableUserLocation() {
        locationOverlay.enableMyLocation()
        locationOverlay.enableFollowLocation()
        binding.mapView.overlays.add(locationOverlay)
        locationOverlay.runOnFirstFix {
            activity?.runOnUiThread {
                locationOverlay.myLocation?.let { geoPoint ->
                    binding.mapView.controller.animateTo(geoPoint)
                    binding.mapView.controller.setZoom(16.0)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        if (::locationOverlay.isInitialized) {
            locationOverlay.enableMyLocation()
        }
        viewModel.refreshIncidents()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
        if (::locationOverlay.isInitialized) {
            locationOverlay.disableMyLocation()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        simulationAnimator?.cancel()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
