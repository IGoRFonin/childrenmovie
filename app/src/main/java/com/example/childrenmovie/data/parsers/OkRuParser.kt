package com.example.childrenmovie.data.parsers

import android.util.Log
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

// Data-классы для типобезопасного парсинга данных OK.ru
data class OkRuOptions(
    @field:Json(name = "flashvars") val flashvars: FlashVars
)

data class FlashVars(
    @field:Json(name = "metadata") val metadata: String
)

data class VideoMetadata(
    @field:Json(name = "videos") val videos: List<VideoQuality>
)

data class VideoQuality(
    @field:Json(name = "name") val name: String,
    @field:Json(name = "url") val url: String,
    @field:Json(name = "disallowed") val disallowed: Boolean
)

class OkRuParser(
    private val client: OkHttpClient,
    private val moshi: Moshi
) : VideoParser {

    companion object {
        private const val TAG = "OkRuParser"
    }

    override fun canParse(url: String): Boolean {
        return url.contains("ok.ru", ignoreCase = true)
    }

    override suspend fun parseVideoUrl(pageUrl: String): String {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "▶️ Начинаем загрузку видео из: $pageUrl")

                val request = Request.Builder()
                    .url(pageUrl)
                    .addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36")
                    .build()

                val response = client.newCall(request).execute()
                Log.d(TAG, "📡 HTTP статус: ${response.code}")

                if (!response.isSuccessful) {
                    throw Exception("Failed to download OK.ru page. Code: ${response.code}")
                }
                val html = response.body?.string() ?: throw Exception("Response body is null")
                Log.d(TAG, "📄 HTML загружен, размер: ${html.length} символов")

                // Алгоритм парсинга ok.ru:
                // 1. Найти элемент с data-module="OKVideo"
                // 2. Извлечь JSON из data-options
                // 3. Распарсить flashvars.metadata
                // 4. Выбрать лучшее качество из videos[]

                val doc = Jsoup.parse(html)
                val videoElement = doc.select("[data-module=OKVideo]").first()
                    ?: throw Exception("Could not find video element with data-module=OKVideo")

                val optionsJson = videoElement.attr("data-options")
                Log.d(TAG, "🔍 Найден data-options, размер: ${optionsJson.length} символов")

                val optionsAdapter = moshi.adapter(OkRuOptions::class.java)
                val options = optionsAdapter.fromJson(optionsJson)
                    ?: throw Exception("Could not parse data-options")

                val metadataJson = options.flashvars.metadata
                Log.d(TAG, "📦 metadata JSON: ${metadataJson.take(300)}...")

                val metadataAdapter = moshi.adapter(VideoMetadata::class.java)
                val metadata = metadataAdapter.fromJson(metadataJson)
                    ?: throw Exception("Could not parse metadata")

                val videos = metadata.videos
                Log.d(TAG, "🎬 Найдено видео: ${videos.size} качеств")
                videos.forEach { video ->
                    Log.d(TAG, "  - ${video.name}: ${if (video.disallowed) "ЗАБЛОКИРОВАНО" else "доступно"}")
                }

                // Приоритет качества: full > hd > sd > low > lowest > mobile
                val qualityPriority = listOf("full", "hd", "sd", "low", "lowest", "mobile")

                var bestVideo: String? = null
                for (quality in qualityPriority) {
                    val video = videos.find { it.name == quality && !it.disallowed }
                    if (video != null) {
                        bestVideo = video.url
                        Log.d(TAG, "✅ Выбрано качество: $quality")
                        break
                    }
                }

                val finalUrl = bestVideo ?: throw Exception("Could not find any video URL")
                Log.d(TAG, "🎥 Финальный URL видео: $finalUrl")

                return@withContext finalUrl
            } catch (e: Exception) {
                Log.e(TAG, "❌ Ошибка при загрузке видео: ${e.message}", e)
                throw e
            }
        }
    }
}
