package com.example.childrenmovie.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@Composable
fun PlayerScreen(videoUrl: String) {
    val context = LocalContext.current

    // Создаем экземпляр ExoPlayer и запоминаем его.
    // Это гарантирует, что плеер не будет пересоздаваться при каждой перерисовке.
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            // Создаем MediaItem из нашего URL
            val mediaItem = MediaItem.fromUri(videoUrl)
            setMediaItem(mediaItem)
            // Готовим плеер к воспроизведению
            prepare()
            // Начинаем воспроизведение, как только он будет готов
            playWhenReady = true
        }
    }

    // Этот эффект управляет жизненным циклом плеера.
    // Он будет вызван, когда PlayerScreen уходит с экрана.
    DisposableEffect(Unit) {
        onDispose {
            // Очень важно освободить ресурсы плеера!
            exoPlayer.release()
        }
    }

    // Используем AndroidView для встраивания классического PlayerView в Compose
    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black), // Черный фон для плеера
        factory = {
            // Создаем PlayerView один раз
            PlayerView(context).apply {
                player = exoPlayer
                useController = true // Показываем стандартные элементы управления
            }
        }
    )
}