### Детализация: Этапа 4. Интеграция видеоплеера

#### **Задача 4.1: Создание экрана плеера**

**Что делаем?** Создаем Composable-экран, который будет содержать `PlayerView` из библиотеки ExoPlayer. Так как `PlayerView` — это классический Android View, а не Composable, мы используем специальный "адаптер" `AndroidView`.

**Как делаем?**
1.  В пакете `ui` создай новый Kotlin-файл `PlayerScreen.kt`.
2.  Скопируй и вставь в него следующий код. Он содержит Composable-функцию, которая умеет отображать плеер и, что **очень важно**, правильно управляет его жизненным циклом (создает при появлении и уничтожает при уходе с экрана, чтобы не расходовать батарею).

```kotlin
// Файл: ui/PlayerScreen.kt

package com.yourpackage.kidsplayer.ui // <-- ЗАМЕНИ

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
```

---

#### **Задача 4.2: Создание `PlayerViewModel`**

**Что делаем?** Создаем `ViewModel` для экрана плеера. Его задача — взять `pageUrl` (ссылку на страницу `ok.ru`), запросить у `Repository` прямую ссылку на видеофайл и передать ее на UI.

**Как делаем?**
1.  В пакете `ui` создай новый Kotlin-файл `PlayerViewModel.kt`.
2.  Скопируй и вставь в него этот код:

```kotlin
// Файл: ui/PlayerViewModel.kt

package com.yourpackage.kidsplayer.ui // <-- ЗАМЕНИ

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourpackage.kidsplayer.data.ContentRepository
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
```
**Важный момент:** URL-адреса часто содержат символы (`/`, `?`, `&`), которые ломают систему навигации. Поэтому мы будем передавать их в *закодированном* виде, а в `ViewModel` — *декодировать* обратно.

---

#### **Задача 4.3: Логика работы плеера и интеграция в навигацию**

**Что делаем?** Соединяем все части вместе: обновляем навигацию, чтобы она знала о новом экране, и вызываем эту навигацию по клику на мультик.

**Как делаем?**
1.  **Обновляем `Navigation.kt`**.
    Открой файл `ui/Navigation.kt` и добавь маршрут для плеера.

    ```kotlin
    // Файл: ui/Navigation.kt

    package com.yourpackage.kidsplayer.ui // <-- ЗАМЕНИ

    import java.net.URLEncoder
    import java.nio.charset.StandardCharsets

    sealed class Screen(val route: String) {
        object Gallery : Screen("gallery")
        object SeriesDetails : Screen("series_details/{seriesId}") {
            fun createRoute(seriesId: String) = "series_details/$seriesId"
        }
        // ДОБАВЛЯЕМ НОВЫЙ ЭКРАН
        object Player : Screen("player/{encodedUrl}") {
            fun createRoute(pageUrl: String): String {
                // Кодируем URL перед тем, как вставить его в маршрут
                val encodedUrl = URLEncoder.encode(pageUrl, StandardCharsets.UTF_8.name())
                return "player/$encodedUrl"
            }
        }
    }
    ```

2.  **Добавляем экран плеера в `NavHost` в `MainActivity.kt`**.
    Открой `MainActivity.kt` и добавь новый `composable` блок для плеера внутри `NavHost`. Если ты еще не добавил эти импорты на Этапе 3, добавь их сейчас:
    ```kotlin
    import androidx.lifecycle.createSavedStateHandle
    import androidx.navigation.NavType
    import androidx.navigation.navArgument
    import androidx.compose.ui.graphics.Color
    ```

    Теперь добавь маршрут плеера:
    ```kotlin
    // В файле MainActivity.kt, внутри NavHost

    // ... composable для галереи ...
    // ... composable для деталей сериала ...

    // ДОБАВЛЯЕМ МАРШРУТ ДЛЯ ПЛЕЕРА
    composable(
        route = Screen.Player.route,
        arguments = listOf(navArgument("encodedUrl") { type = NavType.StringType })
    ) { backStackEntry ->
        val viewModel: PlayerViewModel = viewModel(
            factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    // ВАЖНО: используем createSavedStateHandle для получения encodedUrl из навигации
                    val savedStateHandle = androidx.lifecycle.createSavedStateHandle()
                    return PlayerViewModel(savedStateHandle, repository) as T
                }
            }
        )
        val uiState by viewModel.uiState.collectAsState()

        when (val state = uiState) {
            is PlayerUiState.Loading -> {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    CircularProgressIndicator()
                }
            }
            is PlayerUiState.Success -> {
                PlayerScreen(videoUrl = state.videoUrl)
            }
            is PlayerUiState.Error -> {
                 Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text("Ошибка загрузки видео: ${state.message}")
                }
            }
        }
    }
    ```

3.  **Вызываем навигацию по клику.**
    Теперь осталось только заставить кнопки переходить на плеер.

    *   **Для фильмов (на главном экране):**
        В `MainActivity.kt`, найди `composable(Screen.Gallery.route)` и замени `// TODO: Навигация на плеер для фильмов` на реальный вызов:

        ```kotlin
        // В MainActivity.kt, в лямбде onContentClick для GalleryGrid
        ...
        onContentClick = { item ->
            if (item.type == "series") {
                navController.navigate(Screen.SeriesDetails.createRoute(item.id))
            } else {
                // ЗАМЕНЯЕМ TODO
                item.pageUrl?.let { url ->
                    navController.navigate(Screen.Player.createRoute(url))
                }
            }
        }
        ...
        ```
    *   **Для эпизодов (на экране деталей):**
        Сначала нужно передать `navController` в `SeriesDetailsScreen`. Обнови `MainActivity.kt` в `composable` для деталей сериала:

        ```kotlin
        // В MainActivity.kt, в composable для SeriesDetails
        ...
        is SeriesUiState.Success -> {
            SeriesDetailsScreen(
                series = state.series,
                onEpisodeClick = { episodePageUrl ->
                    // ЗАМЕНЯЕМ TODO
                    navController.navigate(Screen.Player.createRoute(episodePageUrl))
                }
            )
        }
        ...
        ```
        И теперь обнови сам `SeriesDetailsScreen.kt`, чтобы он принимал лямбду для клика по эпизоду.

        ```kotlin
        // Файл: ui/SeriesDetailsScreen.kt

        // В composable SeriesDetailsScreen
        ...
        items(series.episodes.orEmpty()) { episode ->
            Text(
                text = episode.title,
                modifier = Modifier
                    .fillMaxWidth()
                    // ДОБАВЛЯЕМ КЛИКАБЕЛЬНОСТЬ
                    .clickable { onEpisodeClick(episode.pageUrl) }
                    .padding(vertical = 12.dp)
            )
        }
        ...
        ```

**Готово!**

Теперь, если ты запустишь приложение, то сможешь:
1.  Нажать на постер **фильма** на главном экране.
2.  Увидеть экран загрузки, пока парсится страница `ok.ru`.
3.  Увидеть, как запускается плеер и начинает воспроизведение.
4.  То же самое произойдет, если зайти в **сериал** и нажать на **эпизод**.

**Важнейшее замечание:** Успех этого этапа на 90% зависит от того, насколько правильно ты реализуешь логику парсинга HTML в `RemoteDataSource` (тот самый `TODO`, который мы оставили в Задаче 2.2). Если плеер не запускается, в первую очередь ищи проблему там.
