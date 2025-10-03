// Файл: data/LocalDataSource.kt

package com.example.childrenmovie.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException

private const val CACHE_FILE_NAME = "content_cache.json"
private const val CACHE_VERSION_FILE = "content_cache_version.txt"
private const val CACHE_URL_FILE = "content_cache_url.txt"

class LocalDataSource(
    private val context: Context // Нужен для доступа к файловой системе приложения
) {

    // Сохраняет JSON в приватный файл приложения
    suspend fun saveContent(json: String) {
        withContext(Dispatchers.IO) {
            context.openFileOutput(CACHE_FILE_NAME, Context.MODE_PRIVATE).use {
                it.write(json.toByteArray())
            }
        }
    }

    // Загружает JSON из файла. Возвращает null, если файла нет.
    suspend fun loadContent(): String? {
        return withContext(Dispatchers.IO) {
            try {
                context.openFileInput(CACHE_FILE_NAME).bufferedReader().use {
                    it.readText()
                }
            } catch (e: FileNotFoundException) {
                null // Это нормальная ситуация при первом запуске
            }
        }
    }

    // Сохраняет версию кеша
    suspend fun saveCacheVersion(version: Double) {
        withContext(Dispatchers.IO) {
            context.openFileOutput(CACHE_VERSION_FILE, Context.MODE_PRIVATE).use {
                it.write(version.toString().toByteArray())
            }
        }
    }

    // Загружает версию кеша. Возвращает null, если файла нет.
    suspend fun loadCacheVersion(): Double? {
        return withContext(Dispatchers.IO) {
            try {
                context.openFileInput(CACHE_VERSION_FILE).bufferedReader().use {
                    it.readText().toDoubleOrNull()
                }
            } catch (e: FileNotFoundException) {
                null
            }
        }
    }

    // Сохраняет URL, с которого был загружен кеш
    suspend fun saveCacheUrl(url: String) {
        withContext(Dispatchers.IO) {
            context.openFileOutput(CACHE_URL_FILE, Context.MODE_PRIVATE).use {
                it.write(url.toByteArray())
            }
        }
    }

    // Загружает URL кеша. Возвращает null, если файла нет.
    suspend fun loadCacheUrl(): String? {
        return withContext(Dispatchers.IO) {
            try {
                context.openFileInput(CACHE_URL_FILE).bufferedReader().use {
                    it.readText()
                }
            } catch (e: FileNotFoundException) {
                null
            }
        }
    }

    // Очищает весь кеш (JSON, версию и URL)
    suspend fun clearCache() {
        withContext(Dispatchers.IO) {
            context.deleteFile(CACHE_FILE_NAME)
            context.deleteFile(CACHE_VERSION_FILE)
            context.deleteFile(CACHE_URL_FILE)
        }
    }
}