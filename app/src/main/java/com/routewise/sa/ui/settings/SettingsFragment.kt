package com.routewise.sa.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.routewise.sa.R

class SettingsFragment : Fragment() {

    private var btnUnitKm: TextView? = null
    private var btnUnitMph: TextView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        btnUnitKm = view.findViewById(R.id.btnUnitKm)
        btnUnitMph = view.findViewById(R.id.btnUnitMph)
        
        setupClickListeners()
    }

    private fun setupClickListeners() {
        btnUnitKm?.setOnClickListener { 
            updateUnitSelection(isKm = true)
        }
        
        btnUnitMph?.setOnClickListener { 
            updateUnitSelection(isKm = false)
        }
    }

    private fun updateUnitSelection(isKm: Boolean) {
        // Update UI state
        btnUnitKm?.apply {
            setBackgroundResource(if (isKm) R.drawable.bg_card_selected else 0)
            setTextColor(if (isKm) 0xFF34D399.toInt() else 0xFF94A3B8.toInt())
        }
        
        btnUnitMph?.apply {
            setBackgroundResource(if (!isKm) R.drawable.bg_card_selected else 0)
            setTextColor(if (!isKm) 0xFF34D399.toInt() else 0xFF94A3B8.toInt())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        btnUnitKm = null
        btnUnitMph = null
    }
}
