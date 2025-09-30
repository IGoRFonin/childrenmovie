// Файл: ui/SeriesDetailsScreen.kt
package com.example.childrenmovie.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.childrenmovie.data.ContentRepository
import com.example.childrenmovie.model.ContentItem
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// Состояния для экрана деталей
sealed interface SeriesUiState {
    object Loading : SeriesUiState
    data class Success(val series: ContentItem) : SeriesUiState
    data class Error(val message: String) : SeriesUiState
}

// ViewModel для экрана деталей
class SeriesDetailsViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: ContentRepository
) : ViewModel() {
    private val seriesId: String = savedStateHandle.get<String>("seriesId")!!

    private val _uiState = MutableStateFlow<SeriesUiState>(SeriesUiState.Loading)
    val uiState: StateFlow<SeriesUiState> = _uiState.asStateFlow()

    init {
        loadSeriesDetails()
    }

    private fun loadSeriesDetails() {
        viewModelScope.launch {
            repository.getContent()
                .map { list -> list.find { it.id == seriesId } } // Находим нужный сериал по ID
                .catch { e -> _uiState.value = SeriesUiState.Error(e.message ?: "Unknown Error") }
                .collect { series ->
                    if (series != null) {
                        _uiState.value = SeriesUiState.Success(series)
                    } else {
                        _uiState.value = SeriesUiState.Error("Сериал с ID $seriesId не найден")
                    }
                }
        }
    }
}

// UI для экрана деталей
@Composable
fun SeriesDetailsScreen(
    series: ContentItem,
    onEpisodeClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        // Заголовок
        item {
            Text(series.title, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(series.description, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Text("Эпизоды:", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }
        // Список эпизодов
        items(series.episodes.orEmpty()) { episode ->
            Text(
                text = episode.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEpisodeClick(episode.pageUrl) }
                    .padding(vertical = 12.dp)
            )
        }
    }
}