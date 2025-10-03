// Файл: data/UpdateRepository.kt

package com.example.childrenmovie.data

import com.example.childrenmovie.model.AppUpdateInfo
import com.squareup.moshi.Moshi
import com.example.childrenmovie.model.ContentRoot

class UpdateRepository(
    private val remoteDataSource: RemoteDataSource,
    private val settingsManager: SettingsManager,
    private val moshi: Moshi
) {

    // Проверяет наличие обновления APK
    suspend fun checkForUpdate(currentVersion: String): AppUpdateInfo? {
        return try {
            val contentUrl = settingsManager.getContentUrl()
            val remoteJson = remoteDataSource.fetchContent(contentUrl)
            val contentRoot = parseJson(remoteJson)

            val remoteApkVersion = contentRoot.apkVersion
            val remoteApkUrl = contentRoot.apkUrl

            if (remoteApkVersion != null && remoteApkUrl != null) {
                val isUpdateAvailable = compareVersions(currentVersion, remoteApkVersion) < 0
                AppUpdateInfo(
                    currentVersion = currentVersion,
                    availableVersion = remoteApkVersion,
                    downloadUrl = remoteApkUrl,
                    isUpdateAvailable = isUpdateAvailable
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    // Парсинг JSON
    private fun parseJson(json: String): ContentRoot {
        val adapter = moshi.adapter(ContentRoot::class.java)
        return adapter.fromJson(json) ?: throw Exception("Failed to parse JSON")
    }

    // Сравнивает версии (простое лексикографическое сравнение)
    // Возвращает: -1 если v1 < v2, 0 если равны, 1 если v1 > v2
    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }

        val maxLength = maxOf(parts1.size, parts2.size)

        for (i in 0 until maxLength) {
            val part1 = parts1.getOrNull(i) ?: 0
            val part2 = parts2.getOrNull(i) ?: 0

            if (part1 < part2) return -1
            if (part1 > part2) return 1
        }

        return 0
    }
}
