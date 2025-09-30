// Файл: data/ContentRepository.kt

package com.example.childrenmovie.data

import com.squareup.moshi.Moshi
import com.example.childrenmovie.model.ContentItem
import com.example.childrenmovie.model.ContentRoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class ContentRepository(
    private val remoteDataSource: RemoteDataSource,
    private val localDataSource: LocalDataSource,
    private val moshi: Moshi,
    private val settingsManager: SettingsManager
) {

    // Функция, которая возвращает поток данных
    fun getContent(): Flow<List<ContentItem>> = flow {
        // 1. Берем URL из настроек
        val contentUrl = settingsManager.getContentUrl()

        // 2. Сначала пытаемся загрузить данные из кеша и сразу отдать их
        val cachedJson = localDataSource.loadContent()
        if (cachedJson != null) {
            try {
                val cachedContent = parseJson(cachedJson)
                emit(cachedContent.content) // Отдаем кешированные данные
            } catch (e: Exception) {
                // Ошибка парсинга кеша, ничего страшного, просто идем в сеть
            }
        }

        // 3. Затем всегда идем в сеть за свежими данными
        try {
            val remoteJson = remoteDataSource.fetchContent(contentUrl)
            val remoteContent = parseJson(remoteJson)

            // 3. Сохраняем свежие данные в кеш
            localDataSource.saveContent(remoteJson)

            // 4. Отдаем свежие данные
            emit(remoteContent.content)
        } catch (e: Exception) {
            // Если кеша не было и сеть не удалась, можно сообщить об ошибке
            if (cachedJson == null) {
                throw e // Пробрасываем ошибку дальше
            }
        }
    }.flowOn(Dispatchers.IO) // Весь flow будет выполняться в фоновом потоке

    // Обертка для парсера Moshi
    private fun parseJson(json: String): ContentRoot {
        val adapter = moshi.adapter(ContentRoot::class.java)
        return adapter.fromJson(json) ?: throw Exception("Failed to parse JSON")
    }

    // Простая обертка для получения URL видео
    suspend fun getVideoUrl(pageUrl: String): String {
        return remoteDataSource.fetchVideoUrl(pageUrl)
    }
}