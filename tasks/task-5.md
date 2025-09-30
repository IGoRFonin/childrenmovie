Отличный выбор! Этот последний этап превратит твое приложение из функционального прототипа в законченный и приятный в использовании продукт. Здесь мы добавим "блеск": настройки, защиту и визуальные улучшения.

Вот подробнейшая инструкция по **Этапу 5**.

---

### Детализация: Этап 5. Настройки и завершение

#### **Задача 5.1: Создание экрана настроек**

**Что делаем?** Создаем простой UI, где родитель сможет ввести URL своего JSON-файла.

**Как делаем?**
1.  В пакете `ui` создай новый Kotlin-файл `SettingsScreen.kt`.
2.  Скопируй и вставь в него следующий код:

```kotlin
// Файл: ui/SettingsScreen.kt
package com.yourpackage.kidsplayer.ui // <-- ЗАМЕНИ

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    currentUrl: String,
    onSave: (String) -> Unit
) {
    // Локальное состояние для текста в поле ввода
    var urlText by remember { mutableStateOf(currentUrl) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Настройки", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = urlText,
            onValueChange = { urlText = it },
            label = { Text("URL файла с контентом") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { onSave(urlText) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Сохранить и обновить")
        }
    }
}
```

#### **Задача 5.2: Создание файла с константами**

**Что делаем?** Создаем отдельный файл для хранения всех констант приложения (URL по умолчанию, PIN-код и т.д.).

**Как делаем?**
1.  В пакете `model` (или в корневом пакете) создай новый Kotlin-файл `Constants.kt`.
2.  Скопируй и вставь в него следующий код:
    ```kotlin
    // Файл: model/Constants.kt
    package com.yourpackage.kidsplayer.model // <-- ЗАМЕНИ

    // URL к JSON-файлу с контентом по умолчанию
    const val DEFAULT_CONTENT_URL = "https://your.server.com/path/to/your/content.json"

    // PIN-код для доступа к настройкам
    const val PARENTAL_PIN = "1234"
    ```

#### **Задача 5.3: Реализация сохранения настроек**

**Что делаем?** Создаем класс, который будет сохранять URL на диске устройства с помощью `SharedPreferences` (стандартный механизм Android для хранения простых данных).

**Как делаем?**
1.  **Создаем `SettingsManager`.**
    В пакете `data` создай новый Kotlin-файл `SettingsManager.kt`.
    ```kotlin
    // Файл: data/SettingsManager.kt
    package com.yourpackage.kidsplayer.data // <-- ЗАМЕНИ

    import android.content.Context
    import android.content.SharedPreferences
    import com.yourpackage.kidsplayer.model.DEFAULT_CONTENT_URL // <-- ИМПОРТИРУЕМ КОНСТАНТУ

    private const val PREFS_NAME = "app_settings"
    private const val KEY_CONTENT_URL = "content_url"

    class SettingsManager(context: Context) {
        private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        fun saveContentUrl(url: String) {
            prefs.edit().putString(KEY_CONTENT_URL, url).apply()
        }

        fun getContentUrl(): String {
            return prefs.getString(KEY_CONTENT_URL, DEFAULT_CONTENT_URL) ?: DEFAULT_CONTENT_URL
        }
    }
    ```

2.  **Добавляем экран настроек в навигацию.**
    Сначала открой `ui/Navigation.kt` и добавь маршрут для настроек:
    ```kotlin
    // Файл: ui/Navigation.kt
    sealed class Screen(val route: String) {
        object Gallery : Screen("gallery")
        object SeriesDetails : Screen("series_details/{seriesId}") {
            fun createRoute(seriesId: String) = "series_details/$seriesId"
        }
        object Player : Screen("player/{encodedUrl}") {
            fun createRoute(pageUrl: String): String {
                val encodedUrl = URLEncoder.encode(pageUrl, StandardCharsets.UTF_8.name())
                return "player/$encodedUrl"
            }
        }
        object Settings : Screen("settings") // <-- ДОБАВЛЯЕМ НОВЫЙ ЭКРАН
    }
    ```

3.  **Интегрируем `SettingsManager` в приложение.**
    Открой `MainActivity.kt` и внеси следующие изменения:
    *   Создай экземпляр `SettingsManager`.
    *   Передай его в `Repository`, чтобы он знал, откуда брать URL.
    *   Передай его в `SettingsScreen`, чтобы он мог читать и сохранять URL.

    **Обнови `MainActivity.kt`:**
    ```kotlin
    // В файле MainActivity.kt
    // ... импорты ...
    import com.yourpackage.kidsplayer.data.SettingsManager // <-- ДОБАВЬ ИМПОРТ
    import android.widget.Toast // <-- ДОБАВЬ ИМПОРТ

    class MainActivity : ComponentActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            // --- СОЗДАНИЕ ЗАВИСИМОСТЕЙ ---
            val settingsManager = SettingsManager(applicationContext) // <-- СОЗДАЕМ
            val okHttpClient = OkHttpClient()
            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val remoteDataSource = RemoteDataSource(okHttpClient, moshi) // Передаем moshi!
            val localDataSource = LocalDataSource(applicationContext)
            val repository = ContentRepository(remoteDataSource, localDataSource, moshi, settingsManager) // <-- ПЕРЕДАЕМ

            setContent {
                // ...
                NavHost(...) {
                    // ... composable для Gallery ...
                    // ... composable для SeriesDetails ...
                    // ... composable для Player ...

                    // ДОБАВЛЯЕМ МАРШРУТ ДЛЯ НАСТРОЕК
                    composable(Screen.Settings.route) {
                        SettingsScreen(
                            currentUrl = settingsManager.getContentUrl(),
                            onSave = { newUrl ->
                                settingsManager.saveContentUrl(newUrl)
                                Toast.makeText(applicationContext, "Настройки сохранены", Toast.LENGTH_SHORT).show()
                                navController.popBackStack() // Возвращаемся на предыдущий экран
                            }
                        )
                    }
                }
            }
        }
    }
    ```

4.  **Обновляем `ContentRepository` и `ViewModel`.**
    Теперь репозиторий должен брать URL из настроек, а не получать как аргумент.
    *   **В `ContentRepository.kt`:**
        ```kotlin
        // Файл: data/ContentRepository.kt
        class ContentRepository(
            private val remoteDataSource: RemoteDataSource,
            private val localDataSource: LocalDataSource,
            private val moshi: Moshi,
            private val settingsManager: SettingsManager // <-- Добавляем зависимость
        ) {
            // Убираем 'url: String' из аргументов функции
            fun getContent(): Flow<List<ContentItem>> = flow {
                // 1. Берем URL из настроек
                val contentUrl = settingsManager.getContentUrl()

                // 2. Сначала пытаемся загрузить данные из кеша
                val cachedJson = localDataSource.loadContent()
                if (cachedJson != null) {
                    try {
                        val cachedContent = parseJson(cachedJson)
                        emit(cachedContent.content) // Отдаем кешированные данные
                    } catch (e: Exception) {
                        // Ошибка парсинга кеша, идем в сеть
                    }
                }

                // 3. Затем идем в сеть за свежими данными
                try {
                    val remoteJson = remoteDataSource.fetchContent(contentUrl)
                    val remoteContent = parseJson(remoteJson)
                    localDataSource.saveContent(remoteJson)
                    emit(remoteContent.content)
                } catch (e: Exception) {
                    if (cachedJson == null) {
                        throw e
                    }
                }
            }.flowOn(Dispatchers.IO)

            // ... остальные методы без изменений ...
        }
        ```
    *   **В `GalleryViewModel.kt`:** измени вызов на `repository.getContent()` (без параметра url):
        ```kotlin
        private fun loadContent() {
            viewModelScope.launch {
                repository.getContent() // <-- Убрали параметр url
                    .onStart { _uiState.value = GalleryUiState.Loading }
                    .catch { e -> _uiState.value = GalleryUiState.Error(e.message ?: "Unknown error") }
                    .collect { contentList ->
                        _uiState.value = GalleryUiState.Success(contentList)
                    }
            }
        }
        ```
    *   **В `SeriesDetailsViewModel.kt`:** также убери параметр url:
        ```kotlin
        private fun loadSeriesDetails() {
            viewModelScope.launch {
                repository.getContent() // <-- Убрали параметр url
                    .map { list -> list.find { it.id == seriesId } }
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
        ```

#### **Задача 5.4: Реализация PIN-кода**

**Что делаем?** Перед переходом на экран настроек показываем диалоговое окно с запросом PIN-кода.

**Как делаем?**
1.  **Создаем диалог для ввода PIN.**
    В пакете `ui` создай файл `PinDialog.kt`.
    ```kotlin
    // Файл: ui/PinDialog.kt
    package com.yourpackage.kidsplayer.ui // <-- ЗАМЕНИ

    import androidx.compose.foundation.text.KeyboardOptions
    import androidx.compose.material3.*
    import androidx.compose.runtime.*
    import androidx.compose.ui.text.input.KeyboardType
    import androidx.compose.ui.text.input.PasswordVisualTransformation

    @Composable
    fun PinDialog(
        onDismiss: () -> Unit,
        onConfirm: (String) -> Unit
    ) {
        var pin by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Родительский контроль") },
            text = {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it },
                    label = { Text("Введите PIN-код") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation()
                )
            },
            confirmButton = {
                Button(onClick = { onConfirm(pin) }) { Text("OK") }
            },
            dismissButton = {
                Button(onClick = onDismiss) { Text("Отмена") }
            }
        )
    }
    ```

2.  **Добавляем логику вызова диалога.**
    В `MainActivity.kt`, в `composable` для `GalleryScreen`, добавляем скрытый способ вызова настроек (например, долгое нажатие на заголовок) и логику показа диалога.

    ```kotlin
    // В файле MainActivity.kt
    // ... импорты ...
    import androidx.compose.foundation.gestures.detectTapGestures
    import androidx.compose.ui.input.pointer.pointerInput
    import android.widget.Toast // <-- Уже добавлен выше
    import com.yourpackage.kidsplayer.model.PARENTAL_PIN // <-- ИМПОРТИРУЕМ КОНСТАНТУ

    // ...
    composable(Screen.Gallery.route) {
        // ... создание viewModel ...

        var showPinDialog by remember { mutableStateOf(false) }

        if (showPinDialog) {
            PinDialog(
                onDismiss = { showPinDialog = false },
                onConfirm = { pin ->
                    showPinDialog = false
                    if (pin == PARENTAL_PIN) { // Используем константу из Constants.kt
                        navController.navigate(Screen.Settings.route)
                    } else {
                        Toast.makeText(applicationContext, "Неверный PIN", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        Scaffold( // Используем Scaffold для TopAppBar
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Детский Кинозал",
                            modifier = Modifier.pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = {
                                        // По долгому нажатию показываем диалог
                                        showPinDialog = true
                                    }
                                )
                            }
                        )
                    }
                )
            }
        ) { paddingValues ->
             // ... твой when (state) ...
             // Важно: добавь paddingValues к контейнеру с контентом
             // Например: Box(modifier = Modifier.padding(paddingValues).fillMaxSize())
        }
    }
    // ...
    ```

#### **Задача 5.5: Финальная полировка**

**Что делаем?** Доводим приложение до идеала: принудительный ландшафтный режим, полноэкранный вид и красивая иконка.

**Как делаем?**
1.  **Принудительная ландшафтная ориентация.**
    *   Открой `app/src/main/AndroidManifest.xml`.
    *   Найди тег `<activity android:name=".MainActivity" ...>`.
    *   Добавь в него атрибут `android:screenOrientation="sensorLandscape"`. Он разрешит оба варианта альбомной ориентации.
    ```xml
    <activity
        android:name=".MainActivity"
        android:exported="true"
        android:screenOrientation="sensorLandscape"
        android:theme="@style/Theme.KidsPlayer">
        ...
    </activity>
    ```

2.  **Полноэкранный режим (Immersive Mode).**
    *   Открой `MainActivity.kt`.
    *   В методе `onCreate`, **перед** `setContent`, добавь следующий код, чтобы скрыть системные панели (статус-бар и навигационную панель).
    ```kotlin
    // В файле MainActivity.kt
    import androidx.core.view.WindowCompat // <-- ДОБАВЬ ИМПОРТ

    class MainActivity : ComponentActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            // Включаем полноэкранный режим
            WindowCompat.setDecorFitsSystemWindows(window, false)

            // ... остальной код onCreate ...
        }
    }
    ```

3.  **Добавление иконки приложения.**
    *   В Android Studio, в панели проекта слева, нажми правой кнопкой мыши на папку `res` (`app/src/main/res`).
    *   Выбери `New` -> `Image Asset`.
    *   Откроется мастер создания иконок.
    *   В поле `Source Asset` ты можешь выбрать:
        *   `Clipart`: выбрать одну из стандартных иконок Google.
        *   `Image`: загрузить свою картинку (например, 512x512 пикселей).
    *   Настрой цвет фона, размер иконки. Мастер сам создаст иконки всех необходимых размеров для разных версий Android.
    *   Нажми `Next`, а затем `Finish`.

**ПОЗДРАВЛЯЮ!**
После выполнения всех этих шагов твое приложение полностью готово. Оно функционально, безопасно для ребенка и выглядит как законченный продукт.
