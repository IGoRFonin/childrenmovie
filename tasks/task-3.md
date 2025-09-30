### Детализация: Этап 3. Реализация UI (Главный экран и список серий)

#### **Задача 3.1: Создание UI-компонентов**

**Что делаем?** Создаем "кирпичики" нашего интерфейса с помощью Jetpack Compose. Мы сделаем компонент для одного постера и компонент для сетки из этих постеров.

**Как делаем?**
1.  В пакете `ui`, нажми правой кнопкой мыши -> `New` -> `Kotlin Class/File`.
2.  Введи имя файла `GalleryScreen.kt` и выбери `File`.
3.  Скопируй и вставь в этот файл следующий код:

```kotlin
// Файл: ui/GalleryScreen.kt

package com.yourpackage.kidsplayer.ui // <-- ЗАМЕНИ com.yourpackage.kidsplayer на свой пакет

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
import com.yourpackage.kidsplayer.model.ContentItem

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
```

---

#### **Задача 3.2: Создание `GalleryViewModel`**

**Что делаем?** Создаем "мозг" для главного экрана. `ViewModel` будет запрашивать данные у `Repository` и хранить состояние экрана (загрузка, успех, ошибка).

**Как делаем?**
1.  В пакете `ui`, создай новый Kotlin-файл `GalleryViewModel.kt`.
2.  Скопируй и вставь в него этот код. **Тебе нужно будет создать `ContentRepository` в `MainActivity` и передать его сюда. Мы сделаем это на следующем шаге.**

```kotlin
// Файл: ui/GalleryViewModel.kt
package com.yourpackage.kidsplayer.ui // <-- ЗАМЕНИ

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourpackage.kidsplayer.data.ContentRepository
import com.yourpackage.kidsplayer.model.ContentItem
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
            // URL к твоему JSON-файлу. Желательно вынести в настройки, но пока так.
            val contentUrl = "https://your.server.com/path/to/your/content.json"

            repository.getContent(contentUrl)
                .onStart { _uiState.value = GalleryUiState.Loading }
                .catch { e -> _uiState.value = GalleryUiState.Error(e.message ?: "Unknown error") }
                .collect { contentList ->
                    _uiState.value = GalleryUiState.Success(contentList)
                }
        }
    }
}
```

---

#### **Задача 3.3: Настройка навигации**

**Что делаем?** Учим приложение переключаться между экранами. Мы используем библиотеку Navigation Compose.

**Как делаем?**
1.  **Создаем файл для описания маршрутов.**
    В пакете `ui` создай файл `Navigation.kt` и вставь в него этот код:

    ```kotlin
    // Файл: ui/Navigation.kt
    package com.yourpackage.kidsplayer.ui // <-- ЗАМЕНИ

    // Определяет все экраны в приложении для безопасной навигации
    sealed class Screen(val route: String) {
        object Gallery : Screen("gallery")
        object SeriesDetails : Screen("series_details/{seriesId}") {
            fun createRoute(seriesId: String) = "series_details/$seriesId"
        }
        // TODO: Добавить маршрут для плеера
    }
    ```

2.  **Настраиваем `MainActivity.kt`**.
    Открой `MainActivity.kt`. Сейчас мы свяжем все вместе: создадим зависимости (`Repository`), настроим `NavHost` (контейнер для экранов) и отобразим наш первый экран. Замени всё содержимое файла на этот код:

    ```kotlin
    // Файл: MainActivity.kt
    package com.yourpackage.kidsplayer // <-- ЗАМЕНИ

    import android.os.Bundle
    import androidx.activity.ComponentActivity
    import androidx.activity.compose.setContent
    import androidx.compose.foundation.layout.Box
    import androidx.compose.foundation.layout.fillMaxSize
    import androidx.compose.material3.*
    import androidx.compose.runtime.Composable
    import androidx.compose.runtime.collectAsState
    import androidx.compose.runtime.getValue
    import androidx.compose.ui.Alignment
    import androidx.compose.ui.Modifier
    import androidx.lifecycle.createSavedStateHandle
    import androidx.lifecycle.viewmodel.compose.viewModel
    import androidx.navigation.NavType
    import androidx.navigation.compose.*
    import androidx.navigation.navArgument
    import com.squareup.moshi.Moshi
    import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
    import com.yourpackage.kidsplayer.data.ContentRepository
    import com.yourpackage.kidsplayer.data.LocalDataSource
    import com.yourpackage.kidsplayer.data.RemoteDataSource
    import com.yourpackage.kidsplayer.ui.GalleryGrid
    import com.yourpackage.kidsplayer.ui.GalleryUiState
    import com.yourpackage.kidsplayer.ui.GalleryViewModel
    import com.yourpackage.kidsplayer.ui.Screen
    import okhttp3.OkHttpClient

    class MainActivity : ComponentActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            // --- СОЗДАНИЕ ЗАВИСИМОСТЕЙ (РУЧНОЕ ВНЕДРЕНИЕ) ---
            // В больших проектах для этого используют Hilt/Dagger
            val okHttpClient = OkHttpClient()
            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val remoteDataSource = RemoteDataSource(okHttpClient, moshi) // Передаем moshi!
            val localDataSource = LocalDataSource(applicationContext)
            val repository = ContentRepository(remoteDataSource, localDataSource, moshi)

            setContent {
                MaterialTheme { // Или твоя тема приложения
                    Surface(modifier = Modifier.fillMaxSize()) {
                        val navController = rememberNavController()
                        NavHost(navController = navController, startDestination = Screen.Gallery.route) {

                            // Маршрут для главного экрана (галереи)
                            composable(Screen.Gallery.route) {
                                // Создаем ViewModel с помощью фабрики, чтобы передать репозиторий
                                val viewModel: GalleryViewModel = viewModel(
                                    factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                            return GalleryViewModel(repository) as T
                                        }
                                    }
                                )
                                val uiState by viewModel.uiState.collectAsState()

                                when (val state = uiState) {
                                    is GalleryUiState.Loading -> {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            CircularProgressIndicator()
                                        }
                                    }
                                    is GalleryUiState.Success -> {
                                        GalleryGrid(
                                            contentList = state.content,
                                            onContentClick = { item ->
                                                // Логика навигации
                                                if (item.type == "series") {
                                                    navController.navigate(Screen.SeriesDetails.createRoute(item.id))
                                                } else {
                                                    // TODO: Навигация на плеер для фильмов
                                                }
                                            }
                                        )
                                    }
                                    is GalleryUiState.Error -> {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Text("Ошибка: ${state.message}")
                                        }
                                    }
                                }
                            }

                            // Маршрут для экрана с эпизодами (пока пустой)
                            composable(Screen.SeriesDetails.route) {
                                // TODO: Здесь будет наш экран со списком серий
                                Text("Экран деталей сериала")
                            }
                        }
                    }
                }
            }
        }
    }
    ```
    **Запусти приложение сейчас!** Если ты правильно указал URL в `GalleryViewModel`, ты должен увидеть либо индикатор загрузки, либо сетку постеров, либо сообщение об ошибке. Нажатие на постер сериала перебросит тебя на текстовую заглушку "Экран деталей сериала".

---

#### **Задача 3.4: Создание экрана со списком серий**

**Что делаем?** Создаем второй экран по образу и подобию первого.

**Как делаем?**
1.  **Создаем `ViewModel` и `Screen`.**
    В пакете `ui` создай новый файл `SeriesDetailsScreen.kt` и вставь туда **сразу весь код** для `ViewModel` и UI этого экрана:

    ```kotlin
    // Файл: ui/SeriesDetailsScreen.kt
    package com.yourpackage.kidsplayer.ui // <-- ЗАМЕНИ

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
    import com.yourpackage.kidsplayer.data.ContentRepository
    import com.yourpackage.kidsplayer.model.ContentItem
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
                // Тот же URL, что и на главном экране
                val contentUrl = "https://your.server.com/path/to/your/content.json"
                repository.getContent(contentUrl)
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
                Divider(modifier = Modifier.padding(vertical = 8.dp))
            }
            // Список эпизодов
            items(series.episodes.orEmpty()) { episode ->
                Text(
                    text = episode.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                )
            }
        }
    }
    ```

2.  **Интегрируем экран в навигацию.**
    Вернись в `MainActivity.kt` и замени заглушку `// TODO: ...` на вызов нашего нового экрана:

    ```kotlin
    // В файле MainActivity.kt, внутри NavHost

    // ... composable для галереи ...

    // Маршрут для экрана с эпизодами (теперь не пустой)
    composable(
        route = Screen.SeriesDetails.route,
        arguments = listOf(navArgument("seriesId") { type = NavType.StringType })
    ) { backStackEntry ->
        // ViewModel для этого экрана создается с правильным SavedStateHandle
        val viewModel: SeriesDetailsViewModel = viewModel(
            factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    // ВАЖНО: передаем savedStateHandle из NavBackStackEntry
                    val savedStateHandle = androidx.lifecycle.createSavedStateHandle()
                    return SeriesDetailsViewModel(
                        savedStateHandle,
                        repository
                    ) as T
                }
            }
        )
        val uiState by viewModel.uiState.collectAsState()

        when (val state = uiState) {
            is SeriesUiState.Loading -> {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator()
                }
            }
            is SeriesUiState.Success -> {
                SeriesDetailsScreen(
                    series = state.series,
                    onEpisodeClick = { episodePageUrl ->
                        // TODO: Навигация на плеер
                    }
                )
            }
            is SeriesUiState.Error -> {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text("Ошибка: ${state.message}")
                }
            }
        }
    }
    ```
    **Обрати внимание:**
    - Для `SeriesDetailsViewModel` мы используем `createSavedStateHandle()` внутри фабрики. Эта функция автоматически получает аргументы навигации (`seriesId`) из `NavBackStackEntry` и помещает их в `SavedStateHandle`.
    - `SavedStateHandle` — это специальный механизм Android, который позволяет ViewModel получать параметры из навигации и сохранять состояние при смене конфигурации (например, при повороте экрана).
    - Важно использовать `{ backStackEntry ->` в лямбде `composable`, хотя мы не используем `backStackEntry` напрямую — это необходимо для правильной работы `createSavedStateHandle()`.
