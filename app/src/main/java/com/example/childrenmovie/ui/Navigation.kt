// Файл: ui/Navigation.kt
package com.example.childrenmovie.ui

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

// Определяет все экраны в приложении для безопасной навигации
sealed class Screen(val route: String) {
    object Gallery : Screen("gallery")
    object SeriesDetails : Screen("series_details/{seriesId}") {
        fun createRoute(seriesId: String) = "series_details/$seriesId"
    }
    object Player : Screen("player/{encodedUrl}") {
        fun createRoute(pageUrl: String): String {
            // Кодируем URL перед тем, как вставить его в маршрут
            val encodedUrl = URLEncoder.encode(pageUrl, StandardCharsets.UTF_8.name())
            return "player/$encodedUrl"
        }
    }
    object Settings : Screen("settings")
}