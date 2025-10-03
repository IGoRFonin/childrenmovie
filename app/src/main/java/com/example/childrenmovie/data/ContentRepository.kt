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

        // 2. Проверяем кеш и его метаданные
        val cachedJson = localDataSource.loadContent()
        val cachedUrl = localDataSource.loadCacheUrl()
        val cachedVersion = localDataSource.loadCacheVersion()

        // 3. Проверяем, актуален ли кеш (URL не изменился)
        var shouldUseCachedData = false
        if (cachedJson != null && cachedUrl == contentUrl) {
            // URL совпадает, кеш актуален
            try {
                val cachedContent = parseJson(cachedJson)
                emit(cachedContent.content) // Отдаем кешированные данные
                shouldUseCachedData = true
            } catch (e: Exception) {
                // Ошибка парсинга кеша, очистим его и загрузим из сети
                localDataSource.clearCache()
            }
        } else if (cachedJson != null && cachedUrl != contentUrl) {
            // URL изменился, очищаем старый кеш
            localDataSource.clearCache()
        }

        // 4. Загружаем данные из сети
        try {
            val remoteJson = remoteDataSource.fetchContent(contentUrl)
            val remoteContent = parseJson(remoteJson)

            // 5. Проверяем версию и обновляем кеш только если нужно
            val shouldUpdateCache = cachedVersion == null ||
                                   remoteContent.version > cachedVersion ||
                                   cachedUrl != contentUrl

            if (shouldUpdateCache) {
                localDataSource.saveContent(remoteJson)
                localDataSource.saveCacheVersion(remoteContent.version)
                localDataSource.saveCacheUrl(contentUrl)
            }

            // 6. Эмитим данные из сети только если они отличаются от кеша
            if (!shouldUseCachedData || remoteContent.version > (cachedVersion ?: 0.0)) {
                emit(remoteContent.content)
            }
        } catch (e: Exception) {
            // Если кеша не было и сеть не удалась, пробрасываем ошибку
            if (!shouldUseCachedData) {
                throw e
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

    // Очистка кеша
    suspend fun clearCache() {
        localDataSource.clearCache()
    }
}