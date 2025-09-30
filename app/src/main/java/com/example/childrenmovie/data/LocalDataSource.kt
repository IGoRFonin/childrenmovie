// Файл: data/LocalDataSource.kt

package com.example.childrenmovie.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException

private const val CACHE_FILE_NAME = "content_cache.json"

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
}