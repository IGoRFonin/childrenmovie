package com.example.childrenmovie.data.parsers

import android.util.Log
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class VkPlayerParams(
    @field:Json(name = "params") val params: List<VkVideoData>
)

data class VkVideoData(
    @field:Json(name = "url144") val url144: String? = null,
    @field:Json(name = "url240") val url240: String? = null,
    @field:Json(name = "url360") val url360: String? = null,
    @field:Json(name = "url480") val url480: String? = null,
    @field:Json(name = "url720") val url720: String? = null,
    @field:Json(name = "url1080") val url1080: String? = null,
    @field:Json(name = "hls") val hls: String? = null,
    @field:Json(name = "dash_sep") val dashSep: String? = null,
    @field:Json(name = "dash_webm") val dashWebm: String? = null,
    @field:Json(name = "dash_webm_av1") val dashWebmAv1: String? = null
)

class VkVideoParser(
    private val client: OkHttpClient,
    private val moshi: Moshi
) : VideoParser {

    companion object {
        private const val TAG = "VkVideoParser"

        // Символы для генерации случайной куки
        private const val COOKIE_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        private const val COOKIE_LENGTH = 15
    }

    override fun canParse(url: String): Boolean {
        return url.contains("vkvideo.ru", ignoreCase = true) ||
               url.contains("vk.com/video", ignoreCase = true)
    }

    /**
     * Генерирует случайную куку remixdsid для обхода защиты VK Video
     * Формат: 15 символов (буквы + цифры)
     */
    private fun generateRandomRemixdsid(): String {
        return (1..COOKIE_LENGTH)
            .map { COOKIE_CHARS.random() }
            .joinToString("")
    }

    override suspend fun parseVideoUrl(pageUrl: String): String {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "▶️ Начинаем загрузку видео из vkvideo.ru: $pageUrl")

                // Генерация случайной куки remixdsid для обхода защиты VK
                val remixdsid = generateRandomRemixdsid()
                Log.d(TAG, "🍪 Сгенерирована кука: remixdsid=$remixdsid")

                val request = Request.Builder()
                    .url(pageUrl)
                    .addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36")
                    .addHeader("Cookie", "remixdsid=$remixdsid")
                    .build()

                val response = client.newCall(request).execute()
                Log.d(TAG, "📡 HTTP статус: ${response.code}")

                if (!response.isSuccessful) {
                    throw Exception("Failed to download vkvideo.ru page. Code: ${response.code}")
                }
                val html = response.body?.string() ?: throw Exception("Response body is null")
                Log.d(TAG, "📄 HTML загружен, размер: ${html.length} символов")

                // Шаг 1: Найти начало playerParams
                val startMarker = "var playerParams = "
                val startIndex = html.indexOf(startMarker)
                if (startIndex == -1) {
                    throw Exception("Could not find playerParams in HTML")
                }

                Log.d(TAG, "🔍 Найден playerParams на позиции: $startIndex")

                // Шаг 2: Извлечь JSON с балансировкой скобок
                val jsonStart = startIndex + startMarker.length
                var braceCount = 0
                var jsonEnd = jsonStart

                for (i in jsonStart until html.length) {
                    when (html[i]) {
                        '{' -> braceCount++
                        '}' -> {
                            braceCount--
                            if (braceCount == 0) {
                                jsonEnd = i + 1
                                break
                            }
                        }
                    }
                }

                if (jsonEnd == jsonStart) {
                    throw Exception("Could not find closing brace for playerParams")
                }

                val playerParamsJson = html.substring(jsonStart, jsonEnd)
                Log.d(TAG, "📦 Извлечен JSON, размер: ${playerParamsJson.length} символов")

                // Шаг 3: Распарсить с Moshi (без дополнительной очистки)
                val adapter = moshi.adapter(VkPlayerParams::class.java)
                val playerParams = adapter.fromJson(playerParamsJson)
                    ?: throw Exception("Could not parse playerParams JSON")

                val videoData = playerParams.params.firstOrNull()
                    ?: throw Exception("playerParams.params is empty")

                // Шаг 4: Собрать все доступные URL
                val availableUrls = mutableMapOf<String, String>()

                videoData.url1080?.let { availableUrls["mp4_1080"] = it }
                videoData.url720?.let { availableUrls["mp4_720"] = it }
                videoData.url480?.let { availableUrls["mp4_480"] = it }
                videoData.url360?.let { availableUrls["mp4_360"] = it }
                videoData.url240?.let { availableUrls["mp4_240"] = it }
                videoData.url144?.let { availableUrls["mp4_144"] = it }
                videoData.hls?.let { availableUrls["hls"] = it }
                videoData.dashSep?.let { availableUrls["dash_sep"] = it }
                videoData.dashWebm?.let { availableUrls["dash_webm"] = it }
                videoData.dashWebmAv1?.let { availableUrls["dash_webm_av1"] = it }

                Log.d(TAG, "🎬 Найдено видео: ${availableUrls.size} форматов")
                availableUrls.forEach { (format, url) ->
                    Log.d(TAG, "  - $format: ${url.take(60)}...")
                }

                // Шаг 5: Выбрать лучшее качество
                // Приоритет: mp4_1080 > mp4_720 > mp4_480 > hls > mp4_360 > mp4_240 > mp4_144
                val qualityPriority = listOf(
                    "mp4_1080", "mp4_720", "mp4_480", "hls",
                    "mp4_360", "mp4_240", "mp4_144"
                )

                var bestUrl: String? = null
                for (quality in qualityPriority) {
                    if (availableUrls.containsKey(quality)) {
                        bestUrl = availableUrls[quality]
                        Log.d(TAG, "✅ Выбрано качество: $quality")
                        break
                    }
                }

                val finalUrl = bestUrl ?: throw Exception("Could not find any video URL")
                Log.d(TAG, "🎥 Финальный URL видео: $finalUrl")

                return@withContext finalUrl
            } catch (e: Exception) {
                Log.e(TAG, "❌ Ошибка при загрузке видео: ${e.message}", e)
                throw e
            }
        }
    }
}
