// Файл: ui/UpdateViewModel.kt

package com.example.childrenmovie.ui

import android.app.Application
import android.app.DownloadManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.childrenmovie.BuildConfig
import com.example.childrenmovie.data.UpdateRepository
import com.example.childrenmovie.model.AppUpdateInfo
import com.example.childrenmovie.utils.ApkInstaller
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class UpdateAvailable(val updateInfo: AppUpdateInfo) : UpdateState()
    object NoUpdateAvailable : UpdateState()
    object Downloading : UpdateState()
    data class DownloadComplete(val file: File) : UpdateState()
    data class Error(val message: String) : UpdateState()
}

class UpdateViewModel(
    application: Application,
    private val updateRepository: UpdateRepository
) : AndroidViewModel(application) {

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val apkInstaller = ApkInstaller(application)
    private var downloadId: Long? = null

    // Проверка наличия обновления
    fun checkForUpdate() {
        viewModelScope.launch {
            _updateState.value = UpdateState.Checking
            try {
                val currentVersion = BuildConfig.VERSION_NAME
                val updateInfo = updateRepository.checkForUpdate(currentVersion)

                if (updateInfo != null && updateInfo.isUpdateAvailable) {
                    _updateState.value = UpdateState.UpdateAvailable(updateInfo)
                } else {
                    _updateState.value = UpdateState.NoUpdateAvailable
                }
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error("Ошибка проверки обновления: ${e.message}")
            }
        }
    }

    // Начать загрузку обновления
    fun downloadUpdate(url: String) {
        viewModelScope.launch {
            try {
                _updateState.value = UpdateState.Downloading
                downloadId = apkInstaller.downloadApk(url, "childrenmovie_update.apk")
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error("Ошибка загрузки: ${e.message}")
            }
        }
    }

    // Проверить статус загрузки
    fun checkDownloadStatus() {
        viewModelScope.launch {
            val id = downloadId ?: return@launch

            when (apkInstaller.getDownloadStatus(id)) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val uri = apkInstaller.getDownloadedFileUri(id)
                    if (uri != null) {
                        // Получаем путь к файлу из URI
                        val file = File(getApplication<Application>().getExternalFilesDir(null), "childrenmovie_update.apk")
                        _updateState.value = UpdateState.DownloadComplete(file)
                    }
                }
                DownloadManager.STATUS_FAILED -> {
                    _updateState.value = UpdateState.Error("Загрузка не удалась")
                }
            }
        }
    }

    // Установить APK
    fun installUpdate(file: File) {
        try {
            apkInstaller.installApk(file)
        } catch (e: Exception) {
            _updateState.value = UpdateState.Error("Ошибка установки: ${e.message}")
        }
    }

    // Сбросить состояние
    fun resetState() {
        _updateState.value = UpdateState.Idle
    }
}
