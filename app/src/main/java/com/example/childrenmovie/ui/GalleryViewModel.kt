// Файл: ui/GalleryViewModel.kt
package com.example.childrenmovie.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.childrenmovie.data.ContentRepository
import com.example.childrenmovie.model.ContentItem
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// Описывает все возможные состояния экрана галереи
sealed interface GalleryUiState {
    object Loading : GalleryUiState
    data class Success(val content: List<ContentItem>) : GalleryUiState
    data class Error(val message: String) : GalleryUiState
}

class GalleryViewModel(
    private val repository: ContentRepository
) : ViewModel() {

    // Приватный, изменяемый StateFlow для внутреннего использования
    private val _uiState = MutableStateFlow<GalleryUiState>(GalleryUiState.Loading)
    // Публичный, неизменяемый StateFlow, на который подпишется UI
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    // Блок init выполняется один раз при создании ViewModel
    init {
        loadContent()
    }

    private fun loadContent() {
        viewModelScope.launch {
            repository.getContent()
                .onStart { _uiState.value = GalleryUiState.Loading }
                .catch { e -> _uiState.value = GalleryUiState.Error(e.message ?: "Unknown error") }
                .collect { contentList ->
                    _uiState.value = GalleryUiState.Success(contentList)
                }
        }
    }
}