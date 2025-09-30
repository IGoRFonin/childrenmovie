// Файл: data/RemoteDataSource.kt

package com.example.childrenmovie.data

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

class RemoteDataSource(
    private val client: OkHttpClient, // OkHttp-клиент для запросов
    private val moshi: Moshi // Moshi для парсинга JSON
) {
    companion object {
        private const val TAG = "PlayerDebug"
    }

    // Функция для скачивания JSON-файла с контентом
    suspend fun fetchContent(url: String): String {
        return withContext(Dispatchers.IO) { // Выполняем в фоновом потоке
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw Exception("Failed to download content JSON. Code: ${response.code}")
            }
            response.body?.string() ?: throw Exception("Response body is null")
        }
    }

    // Функция для скачивания и парсинга HTML-страницы ok.ru
    suspend fun fetchVideoUrl(pageUrl: String): String {
        return withContext(Dispatchers.IO) { // Выполняем в фоновом потоке
            try {
                Log.d(TAG, "▶️ Начинаем загрузку видео из: $pageUrl")

                val request = Request.Builder()
                    .url(pageUrl)
                    // Важно! Притворяемся браузером, чтобы ok.ru нас не заблокировал
                    .addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36")
                    .build()

                val response = client.newCall(request).execute()
                Log.d(TAG, "📡 HTTP статус: ${response.code}")

                if (!response.isSuccessful) {
                    throw Exception("Failed to download OK.ru page. Code: ${response.code}")
                }
                val html = response.body?.string() ?: throw Exception("Response body is null")
                Log.d(TAG, "📄 HTML загружен, размер: ${html.length} символов")
                Log.d(TAG, "📄 Начало HTML: ${html.take(500)}")

                // !!! ВАЖНАЯ ЧАСТЬ: Парсинг страницы OK.ru !!!
                // Алгоритм извлечения ссылки на видео:
                // 1. Находим элемент с атрибутом data-module="OKVideo"
                // 2. Парсим JSON из атрибута data-options
                // 3. Внутри полученного объекта парсим JSON из flashvars.metadata
                // 4. В metadata находим массив videos с разными качествами видео
                // 5. Выбираем лучшее доступное качество

                val doc = Jsoup.parse(html)
                val videoElement = doc.select("[data-module=OKVideo]").first()
                    ?: throw Exception("Could not find video element with data-module=OKVideo")

                // Типобезопасный парсинг с использованием data-классов
                val optionsJson = videoElement.attr("data-options")
                Log.d(TAG, "🔍 Найден data-options, размер: ${optionsJson.length} символов")
                Log.d(TAG, "🔍 data-options: ${optionsJson.take(300)}...")

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