// Файл: ui/GalleryScreen.kt

package com.example.childrenmovie.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.childrenmovie.model.ContentItem

/**
 * Компонент для отображения одного постера с названием.
 * @param item - Данные для отображения (мультик/сериал).
 * @param onClick - Действие при нажатии на постер.
 */
@Composable
fun ContentPosterItem(
    item: ContentItem,
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
                    .data(item.posterUrl)
                    .crossfade(true) // Плавное появление картинки
                    .build(),
                contentDescription = item.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f), // Соотношение сторон постера
                contentScale = ContentScale.Crop // Масштабирование
            )
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(8.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Компонент для отображения сетки постеров.
 * @param contentList - Список мультиков/сериалов для отображения.
 * @param onContentClick - Действие при нажатии на постер.
 */
@Composable
fun GalleryGrid(
    contentList: List<ContentItem>,
    onContentClick: (ContentItem) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp), // Сетка сама подберет кол-во колонок
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp)
    ) {
        items(contentList, key = { it.id }) { item ->
            ContentPosterItem(
                item = item,
                onClick = { onContentClick(item) }
            )
        }
    }
}