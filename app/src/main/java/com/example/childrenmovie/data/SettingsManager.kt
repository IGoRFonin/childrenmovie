// Файл: data/SettingsManager.kt
package com.example.childrenmovie.data

import android.content.Context
import android.content.SharedPreferences
import com.example.childrenmovie.model.DEFAULT_CONTENT_URL

private const val PREFS_NAME = "app_settings"
private const val KEY_CONTENT_URL = "content_url"

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveContentUrl(url: String) {
        prefs.edit().putString(KEY_CONTENT_URL, url).apply()
    }

    fun getContentUrl(): String {
        return prefs.getString(KEY_CONTENT_URL, DEFAULT_CONTENT_URL) ?: DEFAULT_CONTENT_URL
    }
}