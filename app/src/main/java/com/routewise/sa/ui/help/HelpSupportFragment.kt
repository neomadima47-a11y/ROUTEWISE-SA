package com.routewise.sa.ui.help

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.routewise.sa.R
import com.routewise.sa.databinding.FragmentHelpSupportBinding
import com.routewise.sa.databinding.ItemFaqBinding

class HelpSupportFragment : Fragment() {

    private var _binding: FragmentHelpSupportBinding? = null
    private val helpBinding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHelpSupportBinding.inflate(inflater, container, false)
        return helpBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupFaqs()
        setupForm()
        
        helpBinding.btnQuickAi.setOnClickListener {
            findNavController().navigate(R.id.navigation_chat)
        }
    }

    private fun setupFaqs() {
        val faqs = listOf(
            "Why require my location?" to "RouteWise needs GPS for turn-by-turn guidance and real-time hazard alerts.",
            "Is my data shared?" to "No. Your logs and details remain private and encrypted on your device.",
            "Load shedding handling?" to "Dead robots are automatically flagged as 4-Way Stops with voice warnings.",
            "Earn Impact Points?" to "Get +10 pts for hazard reports and +2 when others verify your alerts."
        )

        faqs.forEach { (q, a) ->
            val faqBinding = ItemFaqBinding.inflate(layoutInflater, helpBinding.containerFaq, true)
            faqBinding.tvFaqQuestion.text = q
            faqBinding.tvFaqAnswer.text = a
            
            faqBinding.layoutFaqHeader.setOnClickListener {
                val isVisible = faqBinding.tvFaqAnswer.visibility == View.VISIBLE
                faqBinding.tvFaqAnswer.visibility = if (isVisible) View.GONE else View.VISIBLE
                faqBinding.ivFaqChevron.rotation = if (isVisible) 0f else 90f
            }
        }
    }

    private fun setupForm() {
        val categories = arrayOf("Map Inaccuracy", "Voice Guidance Issue", "App Bug", "Feature Request")
        helpBinding.spinnerFaqCategory.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, categories)

        helpBinding.btnSubmitTicket.setOnClickListener {
            if (helpBinding.etSupportDesc.text.isBlank()) {
                helpBinding.etSupportDesc.error = "Description required"
                return@setOnClickListener
            }
            Toast.makeText(requireContext(), "Support Ticket Submitted!", Toast.LENGTH_SHORT).show()
            helpBinding.etSupportDesc.text.clear()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
