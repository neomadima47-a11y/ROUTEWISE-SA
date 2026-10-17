package com.routewise.sa

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.routewise.sa.data.local.RouteWiseDatabase
import com.routewise.sa.data.remote.RetrofitClient
import com.routewise.sa.data.remote.datasource.IncidentRemoteDataSource
import com.routewise.sa.data.remote.datasource.RouteRemoteDataSource
import com.routewise.sa.data.repository.IncidentRepository
import com.routewise.sa.data.repository.RouteRepository
import com.routewise.sa.data.repository.UserRepository

class RouteWiseApplication : Application() {

    val database by lazy { RouteWiseDatabase.getInstance(this) }
    
    val userRepository by lazy { UserRepository(database.userDao()) }
    
    val incidentRepository by lazy {
        IncidentRepository(
            localDao = database.incidentDao(),
            remoteSource = IncidentRemoteDataSource(RetrofitClient.apiService)
        )
    }

    val routeRepository by lazy {
        RouteRepository(
            remoteSource = RouteRemoteDataSource(RetrofitClient.apiService)
        )
    }

    override fun onCreate() {
        super.onCreate()
        try {
            FirebaseApp.initializeApp(this)
            Log.d("RouteWiseApp", "🔥 FirebaseApp initialized successfully!")
        } catch (e: Exception) {
            Log.e("RouteWiseApp", "Firebase initialization error: ${e.message}")
        }
    }
}
