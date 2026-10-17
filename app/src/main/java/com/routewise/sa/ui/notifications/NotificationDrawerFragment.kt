package com.routewise.sa.ui.notifications

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.routewise.sa.R
import com.routewise.sa.RouteWiseApplication
import com.routewise.sa.data.model.IncidentEntity
import com.routewise.sa.ui.ViewModelFactory
import com.routewise.sa.ui.map.MapViewModel

class NotificationDrawerFragment : Fragment() {

    private val mapViewModel: MapViewModel by activityViewModels {
        val app = requireActivity().application as RouteWiseApplication
        ViewModelFactory(app.incidentRepository, app.userRepository, app.routeRepository)
    }

    private lateinit var tvUnreadBadge: TextView
    private lateinit var rvNotifications: RecyclerView
    private val notificationList = mutableListOf<NotificationItem>()
    private lateinit var adapter: NotificationAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_notification_drawer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvUnreadBadge = view.findViewById(R.id.tvUnreadBadge)
        rvNotifications = view.findViewById(R.id.rvNotifications)

        setupRecyclerView()
        setupFcmTopics()
        observeNotificationsFromFirestore()
    }

    private fun setupFcmTopics() {
        // Automatically subscribe driver to province topics
        FirebaseMessaging.getInstance().subscribeToTopic("alerts_gauteng")
            .addOnSuccessListener { Log.d("FCMTopic", "Subscribed to topics/alerts_gauteng") }
        FirebaseMessaging.getInstance().subscribeToTopic("alerts_corridor_n1")
            .addOnSuccessListener { Log.d("FCMTopic", "Subscribed to topics/alerts_corridor_n1") }
    }

    private fun setupRecyclerView() {
        adapter = NotificationAdapter(notificationList) { notif ->
            // Focus on map
            val incident = IncidentEntity(
                id = notif.id,
                type = "Hazard Alert",
                roadName = notif.title,
                city = "Gauteng",
                province = "Gauteng",
                latitude = notif.latitude,
                longitude = notif.longitude,
                timestamp = notif.timestamp,
                reportedBy = "Automated System",
                description = notif.message,
                severity = "High",
                statusBox = "Active Alert",
                upvotes = notif.impactBonus
            )
            mapViewModel.focusIncidentOnMap(incident)
            findNavController().navigate(R.id.navigation_map)
        }
        rvNotifications.layoutManager = LinearLayoutManager(requireContext())
        rvNotifications.adapter = adapter
    }

    private fun observeNotificationsFromFirestore() {
        try {
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("notifications")
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) {
                        loadInitialMockNotifications()
                        return@addSnapshotListener
                    }

                    notificationList.clear()
                    val items = snapshot.documents.mapNotNull { doc ->
                        val id = doc.getString("id") ?: doc.id
                        val title = doc.getString("title") ?: "⚠️ Traffic Alert"
                        val message = doc.getString("message") ?: "Drive cautiously along corridor"
                        val lat = doc.getDouble("latitude") ?: -25.9983
                        val lng = doc.getDouble("longitude") ?: 28.1278
                        val ts = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        val isRead = doc.getBoolean("isRead") ?: false
                        val bonus = doc.getLong("impactBonus")?.toInt() ?: 25

                        NotificationItem(
                            id = id,
                            title = title,
                            message = message,
                            latitude = lat,
                            longitude = lng,
                            timestamp = ts,
                            impactBonus = bonus,
                            isRead = isRead
                        )
                    }

                    if (items.isNotEmpty()) {
                        notificationList.addAll(items.sortedByDescending { it.timestamp })
                        adapter.notifyDataSetChanged()
                        tvUnreadBadge.text = "${notificationList.count { !it.isRead }} Unread"
                    } else {
                        loadInitialMockNotifications()
                    }
                }
        } catch (_: Exception) {
            loadInitialMockNotifications()
        }
    }

    private fun loadInitialMockNotifications() {
        notificationList.clear()
        notificationList.addAll(
            listOf(
                NotificationItem(
                    id = "n1",
                    title = "🕳️ Pothole in Sandton",
                    message = "M1 South near Grayston Dr — deep hole in left lane (+25 Impact Points awarded).",
                    latitude = -26.1076,
                    longitude = 28.0567,
                    timestamp = System.currentTimeMillis() - 600000,
                    impactBonus = 25,
                    isRead = false
                ),
                NotificationItem(
                    id = "n2",
                    title = "💥 Accident in Midrand",
                    message = "N1 North near Allandale Rd — 2 cars blocking middle lane.",
                    latitude = -25.9983,
                    longitude = 28.1278,
                    timestamp = System.currentTimeMillis() - 1800000,
                    impactBonus = 25,
                    isRead = false
                ),
                NotificationItem(
                    id = "n3",
                    title = "🚦 Broken Robot in Pretoria",
                    message = "Corner of Nana Sita & Lilian Ngoyi St — lights out, treat as 4-way stop.",
                    latitude = -25.7483,
                    longitude = 28.1901,
                    timestamp = System.currentTimeMillis() - 3600000,
                    impactBonus = 25,
                    isRead = true
                )
            )
        )
        adapter.notifyDataSetChanged()
        tvUnreadBadge.text = "${notificationList.count { !it.isRead }} Unread"
    }
}
