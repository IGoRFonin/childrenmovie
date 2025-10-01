package com.example.childrenmovie.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.childrenmovie.data.ContentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

// Состояния для экрана плеера
sealed interface PlayerUiState {
    object Loading : PlayerUiState
    data class Success(val videoUrl: String, val pageUrl: String) : PlayerUiState
    object Closing : PlayerUiState  // Промежуточная фаза закрытия плеера
    data class Error(val message: String) : PlayerUiState
}

class PlayerViewModel(
    encodedPageUrl: String,
    private val repository: ContentRepository
) : ViewModel() {

    companion object {
        private const val TAG = "PlayerDebug"
    }

    // Декодируем URL, который мы передали через навигацию
    private val pageUrl: String = URLDecoder.decode(encodedPageUrl, StandardCharsets.UTF_8.name()).also {
        Log.d(TAG, "🎬 PlayerViewModel создан с pageUrl: $it")
    }

    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    init {
        loadVideoUrl()
    }

    private fun loadVideoUrl() {
        viewModelScope.launch {
            _uiState.value = PlayerUiState.Loading
            Log.d(TAG, "⏳ Начинаем загрузку URL видео...")
            try {
                // Запрашиваем прямую ссылку на видео у репозитория
                val directVideoUrl = repository.getVideoUrl(pageUrl)
                Log.d(TAG, "✅ Успешно получен URL видео: $directVideoUrl")
                _uiState.value = PlayerUiState.Success(directVideoUrl, pageUrl)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Ошибка при получении URL видео: ${e.message}", e)
                Log.e(TAG, "❌ Stack trace: ${e.stackTraceToString()}")
                _uiState.value = PlayerUiState.Error(e.message ?: "Failed to get video URL")
            }
        }
    }

    /**
     * Инициирует процесс закрытия плеера
     * Переводит UI в состояние Closing для корректной очистки ресурсов
     */
    fun initiateClosing() {
        Log.d(TAG, "🚪 Инициировано закрытие плеера")
        _uiState.value = PlayerUiState.Closing
    }

    /**
     * Вызывается PlayerScreen после полного освобождения ресурсов
     */
    fun onPlayerFullyClosed() {
        Log.d(TAG, "✅ Плеер полностью закрыт, ресурсы освобождены")
    }
}