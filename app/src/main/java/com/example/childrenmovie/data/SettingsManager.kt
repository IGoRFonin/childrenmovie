// Файл: data/SettingsManager.kt
package com.example.childrenmovie.data

import android.content.Context
import android.content.SharedPreferences
import com.example.childrenmovie.model.DEFAULT_CONTENT_URL

private const val PREFS_NAME = "app_settings"
private const val KEY_CONTENT_URL = "content_url"
private const val KEY_LAST_CHECKED_APK_VERSION = "last_checked_apk_version"

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveContentUrl(url: String) {
        prefs.edit().putString(KEY_CONTENT_URL, url).apply()
    }

    fun getContentUrl(): String {
        return prefs.getString(KEY_CONTENT_URL, DEFAULT_CONTENT_URL) ?: DEFAULT_CONTENT_URL
    }

    fun resetToDefault() {
        prefs.edit().remove(KEY_CONTENT_URL).apply()
    }

    fun saveLastCheckedApkVersion(version: String) {
        prefs.edit().putString(KEY_LAST_CHECKED_APK_VERSION, version).apply()
    }

    fun getLastCheckedApkVersion(): String? {
        return prefs.getString(KEY_LAST_CHECKED_APK_VERSION, null)
    }
}