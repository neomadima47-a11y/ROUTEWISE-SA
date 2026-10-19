package com.routewise.sa.ui.chat

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.routewise.sa.R
import com.routewise.sa.RouteWiseApplication
import com.routewise.sa.databinding.FragmentChatbotBinding
import com.routewise.sa.ui.ViewModelFactory
import com.routewise.sa.ui.map.MapViewModel
import java.util.Locale

class ChatbotFragment : Fragment() {

    private var _binding: FragmentChatbotBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChatbotViewModel by activityViewModels {
        val app = requireActivity().application as RouteWiseApplication
        ViewModelFactory(app.incidentRepository, app.userRepository, app.routeRepository)
    }

    private val mapViewModel: MapViewModel by activityViewModels {
        val app = requireActivity().application as RouteWiseApplication
        ViewModelFactory(app.incidentRepository, app.userRepository, app.routeRepository)
    }

    private lateinit var adapter: ChatAdapter
    private var textToSpeech: TextToSpeech? = null
    private var isVoiceEnabled = true

    private val voiceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrEmpty()) {
                binding.etChatInput.setText(spokenText)
                viewModel.sendMessage(spokenText)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatbotBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initTextToSpeech()

        adapter = ChatAdapter { text ->
            speakText(text)
        }

        binding.rvChatMessages.layoutManager = LinearLayoutManager(requireContext())
        binding.rvChatMessages.adapter = adapter

        binding.btnToggleVoice.setOnClickListener {
            isVoiceEnabled = !isVoiceEnabled
            if (isVoiceEnabled) {
                Toast.makeText(requireContext(), "🔊 Voice Output Enabled", Toast.LENGTH_SHORT).show()
                binding.btnToggleVoice.setImageResource(R.drawable.ic_volume_up)
            } else {
                textToSpeech?.stop()
                Toast.makeText(requireContext(), "🔇 Voice Output Muted", Toast.LENGTH_SHORT).show()
                binding.btnToggleVoice.setImageResource(R.drawable.ic_volume_off)
            }
        }

        binding.btnHandsFreeDictate.setOnClickListener {
            launchVoiceRecognizer()
        }

        binding.btnSendMessage.setOnClickListener {
            val text = binding.etChatInput.text.toString()
            if (text.isNotBlank()) {
                viewModel.sendMessage(text)
                binding.etChatInput.text.clear()
            }
        }

        viewModel.messages.observe(viewLifecycleOwner) { messageList ->
            adapter.submitList(messageList)
            binding.rvChatMessages.scrollToPosition(messageList.size - 1)

            val lastMsg = messageList.lastOrNull()
            if (lastMsg != null && lastMsg.sender == "ai" && isVoiceEnabled) {
                speakText(lastMsg.text)
            }
        }

        viewModel.navigationDestination.observe(viewLifecycleOwner) { dest ->
            if (dest != null) {
                Toast.makeText(requireContext(), "🗺️ Navigation Triggered to $dest", Toast.LENGTH_LONG).show()
                viewModel.clearNavigationTrigger()
                findNavController().navigate(R.id.navigation_map)
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
        val cleanText = text.replace(Regex("[*_#`~]"), "")
        textToSpeech?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "AIScoutVoice")
    }

    private fun launchVoiceRecognizer() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to AI Scout or dictate hazard...")
            }
            voiceLauncher.launch(intent)
        } catch (_: Exception) {
            Toast.makeText(requireContext(), "Voice recognizer not available", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        _binding = null
    }
}
