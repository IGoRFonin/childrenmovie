// Файл: data/RemoteDataSource.kt

package com.example.childrenmovie.data

import android.util.Log
import com.example.childrenmovie.data.parsers.VideoParserFactory
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class RemoteDataSource(
    private val client: OkHttpClient,
    private val moshi: Moshi
) {
    companion object {
        private const val TAG = "RemoteDataSource"
    }

    // Создаем фабрику парсеров
    private val parserFactory = VideoParserFactory(client, moshi)

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

    // Функция для получения прямой ссылки на видео
    suspend fun fetchVideoUrl(pageUrl: String): String {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "▶️ Получение видео из: $pageUrl")

                // Выбираем подходящий парсер через фабрику
                val parser = parserFactory.getParser(pageUrl)
                Log.d(TAG, "🔧 Используем парсер: ${parser::class.simpleName}")

                // Делегируем парсинг
                val videoUrl = parser.parseVideoUrl(pageUrl)
                Log.d(TAG, "✅ Успешно получен URL: $videoUrl")

                return@withContext videoUrl
            } catch (e: Exception) {
                Log.e(TAG, "❌ Ошибка при получении видео: ${e.message}", e)
                throw e
            }
        }
    }
}