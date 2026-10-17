package com.routewise.sa.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.routewise.sa.data.SessionManager
import com.routewise.sa.databinding.FragmentProfileBinding
import com.routewise.sa.ui.auth.LoginActivity
import java.util.Locale

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val profileBinding get() = _binding!!
    private lateinit var sessionManager: SessionManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return profileBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sessionManager = SessionManager(requireContext())

        setupUserData()

        profileBinding.btnSaveProfile.setOnClickListener {
            val name = profileBinding.etFullName.text.toString()
            profileBinding.tvProfileName.text = name
            profileBinding.tvProfileInitial.text = name.take(1).uppercase(Locale.getDefault())
            
            Toast.makeText(requireContext(), "Profile changes saved successfully!", Toast.LENGTH_SHORT).show()
        }

        profileBinding.btnLogout.setOnClickListener {
            sessionManager.logout()
            Toast.makeText(requireContext(), "Logged out of RouteWise SA", Toast.LENGTH_SHORT).show()

            val intent = Intent(requireContext(), LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            requireActivity().finish()
        }
    }

    private fun setupUserData() {
        val userName = sessionManager.getUserName()
        val userEmail = sessionManager.getUserEmail()

        profileBinding.tvProfileName.text = userName
        profileBinding.tvProfileEmail.text = userEmail
        profileBinding.etFullName.setText(userName)
        
        if (userName.isNotEmpty()) {
            profileBinding.tvProfileInitial.text = userName.take(1).uppercase(Locale.getDefault())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
