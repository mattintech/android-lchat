package com.mattintech.lchat.utils

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveUserName(name: String) {
        if (name.isBlank()) {
            clearUserName()
        } else {
            sharedPreferences.edit().putString(KEY_USER_NAME, name).apply()
        }
    }

    fun getUserName(): String? {
        return sharedPreferences.getString(KEY_USER_NAME, null)
    }

    fun clearUserName() {
        sharedPreferences.edit().remove(KEY_USER_NAME).apply()
    }

    companion object {
        private const val PREFS_NAME = "lchat_preferences"
        private const val KEY_USER_NAME = "user_name"
    }
}