package com.routewise.sa.data

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("routewise_session", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_PHONE = "user_phone"
        private const val KEY_USER_PROVINCE = "user_province"
        private const val KEY_USER_CITY = "user_city"
        private const val KEY_AUTH_TOKEN = "auth_token"
    }

    // --- SAVE LOGIN SESSION ---
    fun saveUser(
        uid: String,
        email: String,
        name: String,
        phone: String = "+27 82 456 7890",
        province: String = "Gauteng",
        city: String = "Johannesburg"
    ) {
        prefs.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, true)
            putString(KEY_USER_EMAIL, email)
            putString(KEY_USER_NAME, name)
            putString(KEY_USER_PHONE, phone)
            putString(KEY_USER_PROVINCE, province)
            putString(KEY_USER_CITY, city)
            putString(KEY_AUTH_TOKEN, "rw_jwt_" + System.currentTimeMillis())
            apply()
        }
    }

    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    fun getUserName(): String = prefs.getString(KEY_USER_NAME, "Sipho Dlamini") ?: ""
    fun getUserEmail(): String = prefs.getString(KEY_USER_EMAIL, "user@routewise.co.za") ?: ""
    fun getUserPhone(): String = prefs.getString(KEY_USER_PHONE, "+27 82 456 7890") ?: ""
    fun getUserProvince(): String = prefs.getString(KEY_USER_PROVINCE, "Gauteng") ?: ""
    fun getUserCity(): String = prefs.getString(KEY_USER_CITY, "Johannesburg") ?: ""

    // --- CLEAR LOGOUT SESSION ---
    fun logout() {
        prefs.edit().clear().apply()
    }
}
