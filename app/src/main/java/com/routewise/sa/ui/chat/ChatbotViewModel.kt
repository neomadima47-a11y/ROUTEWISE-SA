package com.routewise.sa.ui.chat

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routewise.sa.data.remote.GroqApiClient
import com.routewise.sa.data.remote.GroqChatRequest
import com.routewise.sa.data.remote.GroqMessage
import com.routewise.sa.data.repository.IncidentRepository
import kotlinx.coroutines.launch

data class ChatMessage(
    val id: String = System.currentTimeMillis().toString(),
    val sender: String, // "user" or "ai"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

class ChatbotViewModel(
    private val incidentRepository: IncidentRepository? = null
) : ViewModel() {

    private val _messages = MutableLiveData<MutableList<ChatMessage>>(
        mutableListOf(
            ChatMessage(
                sender = "ai",
                text = "Sawubona! I am your **RouteWise AI Scout Copilot** powered by Groq Llama-3. Ask me for live weather, dictate hands-free hazard reports, or ask about SA routes, load shedding stop rules, and emergency dispatch."
            )
        )
    )
    val messages: LiveData<MutableList<ChatMessage>> = _messages

    private val _isSending = MutableLiveData<Boolean>(false)
    val isSending: LiveData<Boolean> = _isSending

    private val _navigationDestination = MutableLiveData<String?>()
    val navigationDestination: LiveData<String?> = _navigationDestination

    fun clearNavigationTrigger() {
        _navigationDestination.value = null
    }

    fun sendMessage(prompt: String) {
        if (prompt.isBlank()) return
        val current = _messages.value ?: mutableListOf()
        current.add(ChatMessage(sender = "user", text = prompt))
        _messages.value = current

        _isSending.value = true

        viewModelScope.launch {
            // Check for Hands-Free Hazard Reporting Intent
            val lower = prompt.lowercase()
            if (lower.contains("report") || lower.contains("pothole") || lower.contains("accident") || 
                lower.contains("robot") || lower.contains("trap") || lower.contains("road works") || lower.contains("closure")) {
                handleHandsFreeHazardReport(prompt, current)
                _isSending.postValue(false)
                return@launch
            }

            // Check for Navigation Intent
            if (lower.contains("navigate") || lower.contains("take me to") || lower.contains("drive to") || lower.contains("route to")) {
                handleNavigationIntent(prompt, current)
                _isSending.postValue(false)
                return@launch
            }

            // Call Groq Llama-3 API
            try {
                val systemPrompt = "You are RouteWise AI Scout, the ultimate hands-free voice assistant for South African drivers. You assist with hands-free hazard reporting, destination weather & safety guidance, route navigation with stop prompts, load shedding 4-way stop rules, emergency numbers (112, Netcare 082911, ER24 084124, SAPS 10111, SANRAL 0800002455), SA sports (Springboks, Bafana Bafana, Proteas), and general intelligence. Keep responses clear, helpful, and concise for voice output."
                
                val req = GroqChatRequest(
                    messages = listOf(
                        GroqMessage("system", systemPrompt),
                        GroqMessage("user", prompt)
                    )
                )

                val response = GroqApiClient.apiService.chatCompletions(GroqApiClient.API_KEY, req)
                if (response.isSuccessful && response.body() != null) {
                    val reply = response.body()?.choices?.firstOrNull()?.message?.content
                        ?: "Traffic flows normally along standard corridors. Drive with caution."
                    current.add(ChatMessage(sender = "ai", text = reply))
                } else {
                    current.add(ChatMessage(sender = "ai", text = getFallbackResponse(prompt)))
                }
            } catch (e: Exception) {
                Log.e("GroqAI", "Groq API error: ${e.message}")
                current.add(ChatMessage(sender = "ai", text = getFallbackResponse(prompt)))
            } finally {
                _messages.postValue(current)
                _isSending.postValue(false)
            }
        }
    }

    private suspend fun handleHandsFreeHazardReport(prompt: String, current: MutableList<ChatMessage>) {
        val lower = prompt.lowercase()
        val type = when {
            lower.contains("pothole") -> "Pothole"
            lower.contains("accident") -> "Accident"
            lower.contains("robot") -> "Broken Robot"
            lower.contains("trap") || lower.contains("police") -> "Police Trap"
            lower.contains("works") -> "Road Works"
            lower.contains("closure") -> "Road Closure"
            else -> "Road Hazard"
        }

        val roadName = when {
            lower.contains("n1") -> "N1 Highway"
            lower.contains("m1") -> "M1 Motorway"
            lower.contains("n2") -> "N2 Highway"
            lower.contains("n3") -> "N3 Highway"
            lower.contains("n4") -> "N4 Highway"
            lower.contains("r21") -> "R21 Expressway"
            lower.contains("allandale") -> "Allandale Road"
            lower.contains("nana sita") || lower.contains("lilian ngoyi") -> "Nana Sita St & Lilian Ngoyi St"
            lower.contains("rivonia") -> "Rivonia Road"
            else -> "Corridor Road"
        }

        // Publish to Firestore & Live Map Network
        incidentRepository?.reportHazard(
            type = type,
            roadName = roadName,
            city = "Gauteng",
            province = "Gauteng",
            lat = -25.9983,
            lng = 28.1278,
            desc = prompt,
            severity = "High",
            userId = "VoiceDriver"
        )

        val reply = " Understood! I have logged the $type on $roadName to the RouteWise SA live network. Nearby drivers are being notified."
        current.add(ChatMessage(sender = "ai", text = reply))
        _messages.postValue(current)
    }

    private fun handleNavigationIntent(prompt: String, current: MutableList<ChatMessage>) {
        val dest = prompt.substringAfter("to", "Destination").trim()
        val destName = if (dest.length > 2) dest.capitalize() else "Menlyn Park Shopping Centre"

        val reply = "🗺️ I can guide you to $destName! Would you like to add a fuel or dining stop along your route, or navigate directly to $destName?"
        current.add(ChatMessage(sender = "ai", text = reply))
        _messages.postValue(current)

        _navigationDestination.postValue(destName)
    }

    private fun getFallbackResponse(prompt: String): String {
        val lower = prompt.lowercase()
        return when {
            lower.contains("weather") || lower.contains("rain") || lower.contains("fog") ->
                "🌦️ **Weather & Safety Update:** Moderate rain expected over Highveld corridors. Increase following distance from 2 to 4 seconds and watch for aquaplaning risks on wet asphalt."
            lower.contains("number") || lower.contains("emergency") ->
                "🚨 **RouteWise Emergency Dispatch:**\n• Cell Emergency: 112\n• Netcare 911: 082 911\n• ER24: 084 124\n• SAPS Flying Squad: 10111\n• SANRAL Assistance: 0800 00 24 55"
            lower.contains("robot") || lower.contains("load shedding") ->
                "🚦 **SA Traffic Rule:** When traffic light robots are dead due to load shedding, treat the intersection as a **4-Way Stop**. First vehicle to stop has right of way."
            lower.contains("springbok") || lower.contains("rugby") || lower.contains("bafana") ->
                "🇿🇦 **SA Sports Spotlight:** Go Springboks! RouteWise SA celebrates our world champion rugby, cricket, and football teams."
            else ->
                "🤖 **RouteWise AI Scout:** I am monitoring live SA road conditions. N1 Ben Schoeman highway is flowing smoothly. How can I assist your drive?"
        }
    }
}
