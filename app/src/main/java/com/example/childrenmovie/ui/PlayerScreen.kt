package com.example.childrenmovie.ui

import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    videoUrl: String,
    pageUrl: String,
    onPlayerClosed: () -> Unit = {},
    onBackPressed: () -> Unit = {}
) {
    val context = LocalContext.current

    // Перехватываем системную кнопку "Назад"
    BackHandler {
        onBackPressed()
    }

    // Храним ссылку на PlayerView для принудительного обновления layout
    val playerViewRef = remember { mutableStateOf<PlayerView?>(null) }

    // Handler для отложенных обновлений UI
    val handler = remember { Handler(Looper.getMainLooper()) }

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

            // Добавляем слушатель для отслеживания готовности видео
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        // Видео готово к воспроизведению - начинаем агрессивное обновление layout
                        playerViewRef.value?.let { view ->
                            // Множественные отложенные обновления для гарантии правильного центрирования

                            // Немедленное обновление
                            view.requestLayout()
                            view.invalidate()

                            // Отложенные обновления с разными интервалами
                            handler.postDelayed({
                                view.requestLayout()
                                view.invalidate()
                            }, 100)

                            handler.postDelayed({
                                view.requestLayout()
                                view.invalidate()
                            }, 200)

                            handler.postDelayed({
                                // Финальное обновление с переустановкой resizeMode
                                view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                view.requestLayout()
                                view.invalidate()
                            }, 300)
                        }
                    }
                }
            })
        }
    }

    // Этот эффект управляет жизненным циклом плеера.
    // Он будет вызван, когда PlayerScreen уходит с экрана.
    DisposableEffect(Unit) {
        onDispose {
            // Останавливаем все отложенные обновления
            handler.removeCallbacksAndMessages(null)
            // Очень важно освободить ресурсы плеера!
            exoPlayer.release()

            // Уведомляем о завершении очистки
            onPlayerClosed()
        }
    }

    // Дополнительное страховочное обновление через корутины Compose
    LaunchedEffect(Unit) {
        // Ждем пока все инициализируется
        delay(500)
        // Принудительно обновляем layout еще раз
        playerViewRef.value?.apply {
            requestLayout()
            invalidate()
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
                // Явно устанавливаем layoutParams для правильного размера
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )

                player = exoPlayer
                useController = true // Показываем стандартные элементы управления
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT // Масштабирование с сохранением пропорций

                // Сохраняем ссылку на PlayerView
                playerViewRef.value = this

                // Множественные отложенные обновления после инициализации
                post {
                    requestLayout()
                    invalidate()
                }

                handler.postDelayed({
                    requestLayout()
                    invalidate()
                }, 150)

                handler.postDelayed({
                    requestLayout()
                    invalidate()
                }, 300)
            }
        },
        update = { playerView ->
            // Принудительно обновляем layout и перерисовку при любых изменениях
            playerView.requestLayout()
            playerView.invalidate()
        }
    )
}