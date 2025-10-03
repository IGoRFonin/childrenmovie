// Файл: ui/SeriesDetailsScreen.kt
package com.example.childrenmovie.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
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
    private val seriesId: String,
    private val repository: ContentRepository
) : ViewModel() {

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

// Компонент для отображения одного эпизода
@Composable
fun EpisodeCard(
    episodeTitle: String,
    posterUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.padding(8.dp).clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(posterUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = episodeTitle,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentScale = ContentScale.Crop
            )
            Text(
                text = episodeTitle,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// UI для экрана деталей
@Composable
fun SeriesDetailsScreen(
    series: ContentItem,
    onEpisodeClick: (String) -> Unit
) {
    // Сетка эпизодов 3 в ряд
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        items(series.episodes.orEmpty(), key = { it.id }) { episode ->
            EpisodeCard(
                episodeTitle = episode.title,
                posterUrl = episode.posterUrl ?: series.posterUrl,
                onClick = { onEpisodeClick(episode.pageUrl) }
            )
        }
    }
}