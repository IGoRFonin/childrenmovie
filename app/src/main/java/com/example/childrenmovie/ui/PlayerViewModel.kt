package com.example.childrenmovie.ui

import androidx.lifecycle.SavedStateHandle
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
    data class Success(val videoUrl: String) : PlayerUiState
    data class Error(val message: String) : PlayerUiState
}

class PlayerViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: ContentRepository
) : ViewModel() {

    // Декодируем URL, который мы передали через навигацию
    private val encodedPageUrl: String = savedStateHandle.get<String>("encodedUrl")!!
    private val pageUrl: String = URLDecoder.decode(encodedPageUrl, StandardCharsets.UTF_8.name())

    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    init {
        loadVideoUrl()
    }

    private fun loadVideoUrl() {
        viewModelScope.launch {
            _uiState.value = PlayerUiState.Loading
            try {
                // Запрашиваем прямую ссылку на видео у репозитория
                val directVideoUrl = repository.getVideoUrl(pageUrl)
                _uiState.value = PlayerUiState.Success(directVideoUrl)
            } catch (e: Exception) {
                _uiState.value = PlayerUiState.Error(e.message ?: "Failed to get video URL")
            }
        }
    }
}