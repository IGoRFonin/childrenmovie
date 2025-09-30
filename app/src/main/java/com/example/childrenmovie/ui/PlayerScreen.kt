package com.example.childrenmovie.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@Composable
fun PlayerScreen(videoUrl: String, pageUrl: String) {
    val context = LocalContext.current

    // Храним ссылку на PlayerView для принудительного обновления layout
    val playerViewRef = remember { mutableStateOf<PlayerView?>(null) }

    // Создаем экземпляр ExoPlayer и запоминаем его.
    // Это гарантирует, что плеер не будет пересоздаваться при каждой перерисовке.
    val exoPlayer = remember {
        // Настраиваем HTTP заголовки для обхода блокировки OK.ru
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf(
                // Указываем что запрос идет с ok.ru
                "Referer" to pageUrl,
                // Притворяемся браузером
                "User-Agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36"
            ))

        // Создаем data source factory который будет использовать наши заголовки
        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        // Создаем media source с нашей data source factory
        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(videoUrl))

        ExoPlayer.Builder(context)
            .setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT)
            .build().apply {
            // Устанавливаем media source вместо media item
            setMediaSource(mediaSource)
            // Готовим плеер к воспроизведению
            prepare()
            // Начинаем воспроизведение, как только он будет готов
            playWhenReady = true

            // Добавляем слушатель для отслеживания рендера первого кадра
            addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    // Принудительно обновляем layout когда первый кадр отрендерен
                    playerViewRef.value?.apply {
                        requestLayout()
                        invalidate()
                    }
                }
            })
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
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT // Масштабирование с сохранением пропорций
                // Сохраняем ссылку на PlayerView
                playerViewRef.value = this
                // Отложенный requestLayout после полной инициализации view
                post {
                    requestLayout()
                    invalidate()
                }
            }
        },
        update = { playerView ->
            // Принудительно обновляем layout и перерисовку при любых изменениях
            playerView.requestLayout()
            playerView.invalidate()
        }
    )
}