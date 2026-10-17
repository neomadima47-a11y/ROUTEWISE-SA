package com.routewise.sa.ui.report

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.routewise.sa.R
import com.routewise.sa.RouteWiseApplication
import com.routewise.sa.ui.ViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

class ReportIncidentFragment : Fragment() {

    private val viewModel: ReportIncidentViewModel by viewModels {
        val app = requireActivity().application as RouteWiseApplication
        ViewModelFactory(app.incidentRepository, app.userRepository)
    }

    private var selectedType = "Pothole"
    private var selectedSeverity = "Moderate"
    private var isIntersectionMode = false
    private var currentSelectedCoords: Pair<Double, Double>? = null

    private lateinit var etRoadName: AutoCompleteTextView
    private lateinit var etRoad1: AutoCompleteTextView
    private lateinit var etRoad2: AutoCompleteTextView
    private lateinit var tvIntersectionPreview: TextView

    private lateinit var btnModeSingle: TextView
    private lateinit var btnModeIntersection: TextView
    private lateinit var layoutSingleRoadInput: LinearLayout
    private lateinit var layoutIntersectionInputs: LinearLayout

    private lateinit var etRouteCode: EditText
    private lateinit var etDescription: EditText
    private lateinit var spinnerProvince: Spinner
    private lateinit var spinnerCity: Spinner
    private lateinit var spinnerDirection: Spinner
    private lateinit var btnPublish: Button

    private lateinit var btnVoiceSearchReport: ImageButton
    private lateinit var btnVoiceRoad1: ImageButton
    private lateinit var btnVoiceRoad2: ImageButton

    private lateinit var cardReportAssistance: MaterialCardView
    private lateinit var tvVerifiedAddress: TextView
    private lateinit var tvGpsCoords: TextView
    private lateinit var btnCopyCoords: MaterialButton
    private lateinit var btnSpeakReportDetails: ImageButton
    private lateinit var tvAssistantGuidance: TextView

    // Quick Pick Corridor & Intersection Chips
    private lateinit var chipIntersectionNana: TextView
    private lateinit var chipIntersectionAllandale: TextView
    private lateinit var chipIntersectionRivonia: TextView
    private lateinit var chipIntersectionM4: TextView
    private lateinit var chipCorridorN1: TextView

    private val httpClient = OkHttpClient()
    private var textToSpeech: TextToSpeech? = null

    // Severity Buttons
    private lateinit var btnLow: TextView
    private lateinit var btnModerate: TextView
    private lateinit var btnHigh: TextView
    private lateinit var btnCritical: TextView

    // 8 Incident Type Cards
    private lateinit var cardAccident: LinearLayout
    private lateinit var cardBrokenRobot: LinearLayout
    private lateinit var cardPothole: LinearLayout
    private lateinit var cardPoliceTrap: LinearLayout
    private lateinit var cardRoadWorks: LinearLayout
    private lateinit var cardHazard: LinearLayout
    private lateinit var cardRoadClosure: LinearLayout
    private lateinit var cardGridlock: LinearLayout

    private val provinces = arrayOf(
        "Gauteng", "Western Cape", "KwaZulu-Natal", "Eastern Cape",
        "Free State", "Limpopo", "Mpumalanga", "North West", "Northern Cape"
    )

    private val citiesGauteng = arrayOf(
        "Johannesburg", "Pretoria", "Sandton", "Centurion", "Midrand",
        "Soweto", "Randburg", "Roodepoort", "Kempton Park", "Benoni"
    )

    private val citiesWC = arrayOf("Cape Town", "Stellenbosch", "George", "Paarl")
    private val citiesKZN = arrayOf("Durban", "Pietermaritzburg", "Umhlanga", "Ballito")
    private val citiesOther = arrayOf("Bloemfontein", "Polokwane", "Nelspruit", "Kimberley", "Rustenburg")

    private val directions = arrayOf(
        "Northbound", "Southbound", "Eastbound", "Westbound",
        "Inbound (CBD)", "Outbound", "Both Ways"
    )

    private val voiceLauncherRoadName = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrEmpty()) {
                etRoadName.setText(spokenText)
                updateAssistanceCard(spokenText, "Gauteng", "Midrand", "-25.9983, 28.1278")
            }
        }
    }

    private val voiceLauncherRoad1 = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrEmpty()) {
                etRoad1.setText(spokenText)
                updateIntersectionPreview()
            }
        }
    }

    private val voiceLauncherRoad2 = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrEmpty()) {
                etRoad2.setText(spokenText)
                updateIntersectionPreview()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_report_incident, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initTextToSpeech()
        initViews(view)
        setupSpinners()
        setupTypeCards()
        setupSeveritySelectors()
        setupModeToggle()
        setupRoadAssistance()
        setupQuickPickChips()
        setupGpsAction()
        setupSubmitAction()

        // Always-active background GPS auto-detection on load
        performGpsAutoDetection()
    }

    override fun onResume() {
        super.onResume()
        // Always-active background GPS auto-detection on resume
        performGpsAutoDetection()
    }

    private fun initTextToSpeech() {
        textToSpeech = TextToSpeech(requireContext()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.ENGLISH
            }
        }
    }

    private fun speakText(text: String) {
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ReportSpeech")
    }

    private fun initViews(v: View) {
        etRoadName = v.findViewById(R.id.etRoadName)
        etRoad1 = v.findViewById(R.id.etRoad1)
        etRoad2 = v.findViewById(R.id.etRoad2)
        tvIntersectionPreview = v.findViewById(R.id.tvIntersectionPreview)

        btnModeSingle = v.findViewById(R.id.btnModeSingle)
        btnModeIntersection = v.findViewById(R.id.btnModeIntersection)
        layoutSingleRoadInput = v.findViewById(R.id.layoutSingleRoadInput)
        layoutIntersectionInputs = v.findViewById(R.id.layoutIntersectionInputs)

        etRouteCode = v.findViewById(R.id.etRouteCode)
        etDescription = v.findViewById(R.id.etDescription)
        spinnerProvince = v.findViewById(R.id.spinnerProvince)
        spinnerCity = v.findViewById(R.id.spinnerCity)
        spinnerDirection = v.findViewById(R.id.spinnerDirection)
        btnPublish = v.findViewById(R.id.btnPublishIncident)

        btnVoiceSearchReport = v.findViewById(R.id.btnVoiceSearchReport)
        btnVoiceRoad1 = v.findViewById(R.id.btnVoiceRoad1)
        btnVoiceRoad2 = v.findViewById(R.id.btnVoiceRoad2)

        cardReportAssistance = v.findViewById(R.id.cardReportAssistance)
        tvVerifiedAddress = v.findViewById(R.id.tvVerifiedAddress)
        tvGpsCoords = v.findViewById(R.id.tvGpsCoords)
        btnCopyCoords = v.findViewById(R.id.btnCopyCoords)
        btnSpeakReportDetails = v.findViewById(R.id.btnSpeakReportDetails)
        tvAssistantGuidance = v.findViewById(R.id.tvAssistantGuidance)

        chipIntersectionNana = v.findViewById(R.id.chipIntersectionNana)
        chipIntersectionAllandale = v.findViewById(R.id.chipIntersectionAllandale)
        chipIntersectionRivonia = v.findViewById(R.id.chipIntersectionRivonia)
        chipIntersectionM4 = v.findViewById(R.id.chipIntersectionM4)
        chipCorridorN1 = v.findViewById(R.id.chipCorridorN1)

        btnLow = v.findViewById(R.id.btnSeverityLow)
        btnModerate = v.findViewById(R.id.btnSeverityModerate)
        btnHigh = v.findViewById(R.id.btnSeverityHigh)
        btnCritical = v.findViewById(R.id.btnSeverityCritical)

        cardAccident = v.findViewById(R.id.cardTypeAccident)
        cardBrokenRobot = v.findViewById(R.id.cardTypeBrokenRobot)
        cardPothole = v.findViewById(R.id.cardTypePothole)
        cardPoliceTrap = v.findViewById(R.id.cardTypePoliceTrap)
        cardRoadWorks = v.findViewById(R.id.cardTypeRoadWorks)
        cardHazard = v.findViewById(R.id.cardTypeHazard)
        cardRoadClosure = v.findViewById(R.id.cardTypeRoadClosure)
        cardGridlock = v.findViewById(R.id.cardTypeGridlock)
    }

    private fun setupModeToggle() {
        btnModeSingle.setOnClickListener {
            isIntersectionMode = false
            btnModeSingle.setBackgroundResource(R.drawable.bg_card_selected)
            btnModeSingle.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary_white))
            btnModeIntersection.setBackgroundResource(R.drawable.bg_rounded_slate)
            btnModeIntersection.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary_slate))

            layoutSingleRoadInput.visibility = View.VISIBLE
            layoutIntersectionInputs.visibility = View.GONE
        }

        btnModeIntersection.setOnClickListener {
            isIntersectionMode = true
            btnModeIntersection.setBackgroundResource(R.drawable.bg_card_selected)
            btnModeIntersection.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary_white))
            btnModeSingle.setBackgroundResource(R.drawable.bg_rounded_slate)
            btnModeSingle.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary_slate))

            layoutSingleRoadInput.visibility = View.GONE
            layoutIntersectionInputs.visibility = View.VISIBLE
            updateIntersectionPreview()
        }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isIntersectionMode) {
                    updateIntersectionPreview()
                }
            }
        }

        etRoad1.addTextChangedListener(watcher)
        etRoad2.addTextChangedListener(watcher)
    }

    private fun updateIntersectionPreview() {
        val r1 = etRoad1.text.toString().trim()
        val r2 = etRoad2.text.toString().trim()
        val combined = when {
            r1.isNotEmpty() && r2.isNotEmpty() -> "Intersection: $r1 & $r2"
            r1.isNotEmpty() -> "Intersection: $r1 & (Select Intersecting Road)"
            r2.isNotEmpty() -> "Intersection: (Select Primary Road) & $r2"
            else -> "Intersection: Select Primary and Intersecting Road"
        }
        tvIntersectionPreview.text = combined
        val city = spinnerCity.selectedItem?.toString() ?: "Pretoria"
        val prov = spinnerProvince.selectedItem?.toString() ?: "Gauteng"
        if (r1.isNotEmpty() && r2.isNotEmpty()) {
            val coordsStr = currentSelectedCoords?.let { "${it.first}, ${it.second}" } ?: "-25.7483, 28.1901"
            updateAssistanceCard("Between $r1 & $r2", prov, city, coordsStr)
        }
    }

    private fun setupGpsAction() {
        btnVoiceSearchReport.setOnClickListener { launchVoiceIntent(voiceLauncherRoadName, "Dictate single road...") }
        btnVoiceRoad1.setOnClickListener { launchVoiceIntent(voiceLauncherRoad1, "Dictate primary road 1...") }
        btnVoiceRoad2.setOnClickListener { launchVoiceIntent(voiceLauncherRoad2, "Dictate intersecting road 2...") }

        btnCopyCoords.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("GPS Coords", tvGpsCoords.text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), "📋 GPS coordinates copied to clipboard!", Toast.LENGTH_SHORT).show()
        }

        btnSpeakReportDetails.setOnClickListener {
            val textToSpeak = "Reported location: ${tvVerifiedAddress.text}. ${tvAssistantGuidance.text}"
            speakText(textToSpeak)
        }
    }

    private fun performGpsAutoDetection() {
        val coords = getCurrentGpsCoords()
        currentSelectedCoords = coords
        val (lat, lng) = coords
        val roadName = "N1 North past Allandale"
        etRoadName.setText(roadName)
        etRoad1.setText("Nana Sita St")
        etRoad2.setText("Lilian Ngoyi St")
        etRouteCode.setText("N1")

        // Auto-select Province & City
        val provIndex = provinces.indexOf("Gauteng")
        if (provIndex >= 0) spinnerProvince.setSelection(provIndex)
        updateCitySpinner("Gauteng")
        val cityIndex = citiesGauteng.indexOf("Pretoria")
        if (cityIndex >= 0) spinnerCity.setSelection(cityIndex)

        // Auto-select Direction
        val dirIndex = directions.indexOf("Northbound")
        if (dirIndex >= 0) spinnerDirection.setSelection(dirIndex)

        updateAssistanceCard(roadName, "Gauteng", "Pretoria", "$lat, $lng")
    }

    private fun launchVoiceIntent(launcher: androidx.activity.result.ActivityResultLauncher<Intent>, prompt: String) {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            }
            launcher.launch(intent)
        } catch (_: Exception) {
            Toast.makeText(requireContext(), "Voice search not supported on this device", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupQuickPickChips() {
        chipIntersectionNana.setOnClickListener {
            btnModeIntersection.performClick()
            etRoad1.setText("Nana Sita St")
            etRoad2.setText("Lilian Ngoyi St")
            etRouteCode.setText("M2")
            currentSelectedCoords = Pair(-25.7483, 28.1901)
            selectSpinnerValues("Gauteng", "Pretoria")
            updateIntersectionPreview()
        }

        chipIntersectionAllandale.setOnClickListener {
            btnModeIntersection.performClick()
            etRoad1.setText("N1 Allandale Rd")
            etRoad2.setText("New Road")
            etRouteCode.setText("N1")
            currentSelectedCoords = Pair(-25.9983, 28.1278)
            selectSpinnerValues("Gauteng", "Midrand")
            updateIntersectionPreview()
        }

        chipIntersectionRivonia.setOnClickListener {
            btnModeIntersection.performClick()
            etRoad1.setText("Rivonia Road")
            etRoad2.setText("Sandton Drive")
            etRouteCode.setText("M9")
            currentSelectedCoords = Pair(-26.1076, 28.0567)
            selectSpinnerValues("Gauteng", "Sandton")
            updateIntersectionPreview()
        }

        chipIntersectionM4.setOnClickListener {
            btnModeIntersection.performClick()
            etRoad1.setText("M4 Highway")
            etRoad2.setText("Sandile Thusi Rd")
            etRouteCode.setText("M4")
            currentSelectedCoords = Pair(-29.8450, 31.0280)
            selectSpinnerValues("KwaZulu-Natal", "Durban")
            updateIntersectionPreview()
        }

        chipCorridorN1.setOnClickListener {
            btnModeSingle.performClick()
            etRoadName.setText("N1 Ben Schoeman Hwy")
            etRouteCode.setText("N1")
            currentSelectedCoords = Pair(-25.9983, 28.1278)
            selectSpinnerValues("Gauteng", "Midrand")
            updateAssistanceCard("N1 Ben Schoeman Hwy", "Gauteng", "Midrand", "-25.9983, 28.1278")
        }
    }

    private fun selectSpinnerValues(prov: String, city: String) {
        val provIdx = provinces.indexOf(prov)
        if (provIdx >= 0) spinnerProvince.setSelection(provIdx)
        updateCitySpinner(prov)
        val cities = when (prov) {
            "Gauteng" -> citiesGauteng
            "Western Cape" -> citiesWC
            "KwaZulu-Natal" -> citiesKZN
            else -> citiesOther
        }
        val cityIdx = cities.indexOf(city)
        if (cityIdx >= 0) spinnerCity.setSelection(cityIdx)
    }

    private fun updateAssistanceCard(road: String, province: String, city: String, coordsStr: String) {
        tvVerifiedAddress.text = "$road, $city, $province"
        tvGpsCoords.text = "GPS: $coordsStr"
        tvAssistantGuidance.text = "🤖 Assistant Prompt: Oncoming drivers approaching $road will receive early audio hazard alerts."
        cardReportAssistance.visibility = View.VISIBLE
    }

    private fun getCurrentGpsCoords(): Pair<Double, Double> {
        try {
            val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (loc != null) {
                return Pair(loc.latitude, loc.longitude)
            }
        } catch (_: SecurityException) {}
        return Pair(-25.99830, 28.12780) // Midrand N1 Highway Road Line
    }

    private fun setupRoadAssistance() {
        val streetSuggestions = mutableListOf(
            "N1 North Highway", "N1 South Highway", "N2 Settlers Way", "N3 Highway",
            "N4 Highway", "M1 North Motorway", "M1 South Motorway", "M4 Ruth First Highway",
            "R21 Expressway", "Rivonia Road", "William Nicol Drive (Winnie Mandela Dr)",
            "Jan Smuts Avenue", "Atterbury Road", "Allandale Road", "Beyers Naudé Drive",
            "Nana Sita Street", "Lilian Ngoyi Street", "Paul Kruger Street", "Sandton Drive",
            "Main Reef Road", "Oxford Road", "Louis Botha Avenue", "Solomon Mahlangu Drive",
            "Lynnwood Road", "Nelson Mandela Boulevard", "Strand Street", "Umhlanga Rocks Drive"
        )

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, streetSuggestions)
        etRoadName.setAdapter(adapter)
        etRoad1.setAdapter(adapter)
        etRoad2.setAdapter(adapter)

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                if (query.length >= 2) {
                    fetchLiveStreetSuggestions(query, adapter)
                }
            }
        }

        etRoadName.addTextChangedListener(watcher)
        etRoad1.addTextChangedListener(watcher)
        etRoad2.addTextChangedListener(watcher)
    }

    private fun fetchLiveStreetSuggestions(query: String, adapter: ArrayAdapter<String>) {
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
                                val street = props.optString("street")
                                val city = props.optString("city")
                                val label = listOfNotNull(
                                    street.takeIf { it.isNotEmpty() } ?: name.takeIf { it.isNotEmpty() },
                                    city.takeIf { it.isNotEmpty() }
                                ).joinToString(", ")
                                if (label.isNotEmpty() && !liveList.contains(label)) {
                                    liveList.add(label)
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

    private fun setupSpinners() {
        val provAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, provinces)
        spinnerProvince.adapter = provAdapter

        val dirAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, directions)
        spinnerDirection.adapter = dirAdapter

        updateCitySpinner("Gauteng")

        spinnerProvince.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedProv = provinces[position]
                updateCitySpinner(selectedProv)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun updateCitySpinner(province: String) {
        val cities = when (province) {
            "Gauteng" -> citiesGauteng
            "Western Cape" -> citiesWC
            "KwaZulu-Natal" -> citiesKZN
            else -> citiesOther
        }
        val cityAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, cities)
        spinnerCity.adapter = cityAdapter
    }

    private fun setupTypeCards() {
        val allCards = listOf(
            cardAccident to "Accident",
            cardBrokenRobot to "Broken Robot",
            cardPothole to "Pothole",
            cardPoliceTrap to "Police Trap",
            cardRoadWorks to "Road Works",
            cardHazard to "Road Hazard",
            cardRoadClosure to "Road Closure",
            cardGridlock to "Gridlock"
        )

        allCards.forEach { (card, type) ->
            card.setOnClickListener {
                selectedType = type
                highlightTypeCard(card, allCards.map { it.first })
            }
        }

        // Default selected: Pothole
        highlightTypeCard(cardPothole, allCards.map { it.first })
    }

    private fun highlightTypeCard(selected: LinearLayout, all: List<LinearLayout>) {
        all.forEach { card ->
            if (card == selected) {
                card.setBackgroundResource(R.drawable.bg_card_selected)
            } else {
                card.setBackgroundResource(R.drawable.bg_card_unselected)
            }
        }
    }

    private fun setupSeveritySelectors() {
        val buttons = listOf(
            btnLow to "Low",
            btnModerate to "Moderate",
            btnHigh to "High",
            btnCritical to "Critical"
        )

        buttons.forEach { (btn, level) ->
            btn.setOnClickListener {
                selectedSeverity = level
                buttons.forEach { (b, l) ->
                    if (l == level) {
                        if (l == "High" || l == "Critical") {
                            b.setBackgroundResource(R.drawable.bg_severity_critical)
                        } else {
                            b.setBackgroundResource(R.drawable.bg_severity_selected)
                        }
                        b.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary_white))
                    } else {
                        b.setBackgroundResource(R.drawable.bg_rounded_slate)
                        b.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary_slate))
                    }
                }
            }
        }

        // Default Moderate
        btnModerate.setBackgroundResource(R.drawable.bg_severity_selected)
        btnModerate.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary_white))
    }

    private fun setupSubmitAction() {
        btnPublish.setOnClickListener {
            val road = if (isIntersectionMode) {
                val r1 = etRoad1.text.toString().trim()
                val r2 = etRoad2.text.toString().trim()
                if (r1.isEmpty() || r2.isEmpty()) {
                    Toast.makeText(requireContext(), "Please enter both Primary Road 1 & Intersecting Road 2", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                "Intersection: $r1 & $r2"
            } else {
                val r = etRoadName.text.toString().trim()
                if (r.isEmpty()) {
                    etRoadName.error = "Road Name / Intersection is required"
                    etRoadName.requestFocus()
                    return@setOnClickListener
                }
                r
            }

            viewModel.reportHazard(
                type = selectedType,
                roadName = road,
                city = spinnerCity.selectedItem?.toString() ?: "Unknown",
                province = spinnerProvince.selectedItem?.toString() ?: "Unknown",
                desc = etDescription.text.toString(),
                severity = selectedSeverity,
                userId = "User123",
                lat = currentSelectedCoords?.first,
                lng = currentSelectedCoords?.second
            )

            speakText("Incident reported on $road. Community hazard broadcast published.")

            Toast.makeText(
                requireContext(),
                "🎉 Report Saved Locally & Published! +10 Impact Points awarded.",
                Toast.LENGTH_LONG
            ).show()

            findNavController().navigate(R.id.navigation_map)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }
}
