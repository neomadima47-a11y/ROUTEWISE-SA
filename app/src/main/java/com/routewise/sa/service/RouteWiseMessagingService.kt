package com.routewise.sa.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.routewise.sa.MainActivity
import com.routewise.sa.R

class RouteWiseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCMToken", "Refreshed FCM Device Token: $token")
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: "⚠️ RouteWise SA Traffic Alert"
        val body = remoteMessage.notification?.body ?: remoteMessage.data["body"] ?: "Traffic or hazard update along your corridor."
        val lat = remoteMessage.data["latitude"]?.toDoubleOrNull() ?: -25.9983
        val lng = remoteMessage.data["longitude"]?.toDoubleOrNull() ?: 28.1278

        // Show High-Priority Heads-Up Android Notification Bar Alert
        showHeadsUpNotification(title, body, lat, lng)

        // Save incoming notification to Firebase Cloud Firestore ("notifications" collection)
        saveNotificationToFirestore(title, body, lat, lng)
    }

    private fun showHeadsUpNotification(title: String, message: String, lat: Double, lng: Double) {
        val channelId = "routewise_traffic_alerts_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "RouteWise SA Traffic Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority traffic alerts and community hazard reports"
                enableVibration(true)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("latitude", lat)
            putExtra("longitude", lng)
            putExtra("notificationTitle", title)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_add_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }

    private fun saveNotificationToFirestore(title: String, message: String, lat: Double, lng: Double) {
        try {
            val firestore = FirebaseFirestore.getInstance()
            val notifMap = hashMapOf(
                "id" to "notif_${System.currentTimeMillis()}",
                "title" to title,
                "message" to message,
                "latitude" to lat,
                "longitude" to lng,
                "timestamp" to System.currentTimeMillis(),
                "isRead" to false,
                "impactBonus" to 25
            )
            firestore.collection("notifications").add(notifMap)
        } catch (e: Exception) {
            Log.e("FCM", "Failed to save notification to Firestore: ${e.message}")
        }
    }
}
