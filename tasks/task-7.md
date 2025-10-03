# Task 7: Устранение наложения интерфейса при закрытии плеера

## Описание проблемы

При закрытии плеера происходит визуальный баг: плеер не успевает полностью закрыться, а UI главного экрана уже начинает отображаться, что приводит к наложению интерфейсов.

### Симптомы
- Пользователь видит одновременно элементы плеера и главной галереи
- Переход выглядит "рваным" и непрофессиональным
- Ухудшается пользовательский опыт

### Причина
При вызове `navController.popBackStack()` навигация происходит мгновенно - Jetpack Navigation сразу удаляет плеерный экран из стека и показывает предыдущий. ExoPlayer не успевает корректно освободить ресурсы и скрыться до того, как появится новый UI.

## Текущая архитектура

### Файл: `MainActivity.kt:240-257`

```kotlin
when (val state = uiState) {
    is PlayerUiState.Loading -> {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize().background(Color.Black)
        ) {
            CircularProgressIndicator()
        }
    }
    is PlayerUiState.Success -> {
        PlayerScreen(videoUrl = state.videoUrl, pageUrl = state.pageUrl)
    }
    is PlayerUiState.Error -> {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text("Ошибка загрузки видео: ${state.message}")
        }
    }
}
```

### Файл: `PlayerScreen.kt:105-112`

```kotlin
DisposableEffect(Unit) {
    onDispose {
        // Останавливаем все отложенные обновления
        handler.removeCallbacksAndMessages(null)
        // Очень важно освободить ресурсы плеера!
        exoPlayer.release()
    }
}
```

**Проблема:** `onDispose` вызывается синхронно при удалении Composable из дерева, но UI уже переключается на другой экран параллельно.

---

## Целевая архитектура

### Решение: Отложенная навигация с фазой закрытия

**Суть:** Добавить промежуточное состояние `Closing` в UI плеера, которое:
1. Скрывает видео (показывает черный экран)
2. Останавливает плеер
3. Освобождает ресурсы
4. Только после завершения уведомляет навигацию о возможности перехода

### Новые состояния

```kotlin
sealed interface PlayerUiState {
    object Loading : PlayerUiState
    data class Success(val videoUrl: String, val pageUrl: String) : PlayerUiState
    object Closing : PlayerUiState  // НОВОЕ: промежуточная фаза закрытия
    data class Error(val message: String) : PlayerUiState
}
```

---

## План реализации

### Этап 1: Добавление механизма обратного вызова в PlayerScreen

**Файл:** `PlayerScreen.kt`

#### 1.1 Добавить параметр callback

```kotlin
@Composable
fun PlayerScreen(
    videoUrl: String,
    pageUrl: String,
    onPlayerClosed: () -> Unit = {}  // НОВЫЙ параметр для уведомления о закрытии
)
```

**Зачем:** Позволяет плееру сообщить внешнему миру, что он завершил очистку ресурсов.

#### 1.2 Вызывать callback после освобождения ресурсов

```kotlin
DisposableEffect(Unit) {
    onDispose {
        // Останавливаем все отложенные обновления
        handler.removeCallbacksAndMessages(null)

        // Освобождаем ресурсы плеера
        exoPlayer.release()

        // НОВОЕ: уведомляем о завершении очистки
        onPlayerClosed()
    }
}
```

**Важно:** Callback вызывается **после** `exoPlayer.release()`, когда все ресурсы уже освобождены.

---

### Этап 2: Добавление состояния Closing в PlayerViewModel

**Файл:** `PlayerViewModel.kt`

#### 2.1 Расширить sealed interface

```kotlin
sealed interface PlayerUiState {
    object Loading : PlayerUiState
    data class Success(val videoUrl: String, val pageUrl: String) : PlayerUiState
    object Closing : PlayerUiState  // НОВОЕ: фаза закрытия плеера
    data class Error(val message: String) : PlayerUiState
}
```

#### 2.2 Добавить метод для инициации закрытия

```kotlin
class PlayerViewModel(
    encodedPageUrl: String,
    private val repository: ContentRepository
) : ViewModel() {

    // ... существующий код ...

    /**
     * Инициирует процесс закрытия плеера
     * Переводит UI в состояние Closing для корректной очистки ресурсов
     */
    fun initiateClosing() {
        Log.d(TAG, "🚪 Инициировано закрытие плеера")
        _uiState.value = PlayerUiState.Closing
    }

    /**
     * Вызывается PlayerScreen после полного освобождения ресурсов
     */
    fun onPlayerFullyClosed() {
        Log.d(TAG, "✅ Плеер полностью закрыт, ресурсы освобождены")
    }
}
```

**Логика:**
1. `initiateClosing()` - переключает UI в состояние `Closing`
2. PlayerScreen видит новое состояние → начинает очистку
3. `onPlayerFullyClosed()` - подтверждение завершения (для логов/метрик)

---

### Этап 3: Обработка состояния Closing в MainActivity

**Файл:** `MainActivity.kt:240-257`

#### 3.1 Добавить обработку нового состояния

```kotlin
when (val state = uiState) {
    is PlayerUiState.Loading -> {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize().background(Color.Black)
        ) {
            CircularProgressIndicator()
        }
    }
    is PlayerUiState.Success -> {
        PlayerScreen(
            videoUrl = state.videoUrl,
            pageUrl = state.pageUrl,
            onPlayerClosed = {
                // Плеер сообщает, что ресурсы освобождены
                viewModel.onPlayerFullyClosed()

                // КРИТИЧНО: навигация происходит только ПОСЛЕ полной очистки
                navController.popBackStack()
            }
        )
    }
    is PlayerUiState.Closing -> {
        // НОВОЕ: показываем черный экран во время закрытия
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        )
        // PlayerScreen будет удален из композиции → запустится onDispose
    }
    is PlayerUiState.Error -> {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text("Ошибка загрузки видео: ${state.message}")
        }
    }
}
```

**Важные моменты:**
- `Closing` показывает **полностью черный экран** без элементов управления
- `navController.popBackStack()` вызывается **только после** `onPlayerClosed()`
- Это гарантирует, что переход произойдет после полной очистки

---

### Этап 4: Добавление кнопки "Назад" в PlayerScreen

**Файл:** `PlayerScreen.kt`

#### 4.1 Добавить параметр для обработки Back

```kotlin
@Composable
fun PlayerScreen(
    videoUrl: String,
    pageUrl: String,
    onPlayerClosed: () -> Unit = {},
    onBackPressed: () -> Unit = {}  // НОВОЕ: обработка нажатия "Назад"
)
```

#### 4.2 Добавить обработчик системной кнопки Back

```kotlin
import androidx.activity.compose.BackHandler

@Composable
fun PlayerScreen(
    videoUrl: String,
    pageUrl: String,
    onPlayerClosed: () -> Unit,
    onBackPressed: () -> Unit
) {
    // НОВОЕ: перехватываем системную кнопку "Назад"
    BackHandler {
        onBackPressed()
    }

    // ... остальной код PlayerScreen ...
}
```

**Зачем:** Позволяет корректно обработать нажатие кнопки "Назад" на устройстве, запустив процесс контролируемого закрытия.

#### 4.3 Обновить вызов в MainActivity

```kotlin
is PlayerUiState.Success -> {
    PlayerScreen(
        videoUrl = state.videoUrl,
        pageUrl = state.pageUrl,
        onBackPressed = {
            // Инициируем контролируемое закрытие
            viewModel.initiateClosing()
        },
        onPlayerClosed = {
            viewModel.onPlayerFullyClosed()
            navController.popBackStack()
        }
    )
}
```

---

## Диаграмма потока выполнения

### Текущее поведение (баг):
```
Пользователь нажимает "Назад"
    ↓
navController.popBackStack() (мгновенно)
    ↓
PlayerScreen удаляется из композиции
    ↓
onDispose → exoPlayer.release() (асинхронно)
    ║
    ║ В это же время:
    ║
    ↓
GalleryScreen появляется на экране
    ↓
НАЛОЖЕНИЕ: Виден и плеер, и галерея одновременно
```

### Новое поведение (исправлено):
```
Пользователь нажимает "Назад"
    ↓
viewModel.initiateClosing()
    ↓
uiState = Closing
    ↓
MainActivity показывает черный экран
    ↓
PlayerScreen удаляется из композиции
    ↓
onDispose → exoPlayer.release()
    ↓
onPlayerClosed() вызывается
    ↓
viewModel.onPlayerFullyClosed()
    ↓
navController.popBackStack() ← ТЕПЕРЬ ЗДЕСЬ
    ↓
GalleryScreen появляется на чистом месте
    ↓
✅ ПЛАВНЫЙ ПЕРЕХОД БЕЗ НАЛОЖЕНИЯ
```

---

## Тестирование

### Шаг 1: Проверка базового сценария
1. Открыть любое видео из галереи
2. Дождаться начала воспроизведения
3. Нажать системную кнопку "Назад"
4. **Ожидаемый результат:**
   - Появляется черный экран
   - Через ~100-300ms возвращается галерея
   - НЕТ наложения интерфейсов

### Шаг 2: Проверка быстрого закрытия
1. Открыть видео
2. **НЕ дожидаясь загрузки** нажать "Назад" (состояние `Loading`)
3. **Ожидаемый результат:**
   - Мгновенный возврат (нет плеера для очистки)
   - Без ошибок

### Шаг 3: Проверка закрытия при ошибке
1. Добавить в JSON невалидный `pageUrl` (например, `https://invalid-url.com`)
2. Попытаться открыть → появится ошибка
3. Нажать "Назад"
4. **Ожидаемый результат:**
   - Корректный возврат на галерею
   - Без краша приложения

### Шаг 4: Стресс-тест
1. Быстро открывать и закрывать разные видео подряд (5-10 раз)
2. **Ожидаемый результат:**
   - Каждый переход плавный
   - Нет утечек памяти (проверить через Android Profiler)
   - Логи показывают корректную последовательность:
     ```
     🚪 Инициировано закрытие плеера
     ✅ Плеер полностью закрыт, ресурсы освобождены
     ```

### Шаг 5: Проверка логов

После каждого закрытия плеера в Logcat должна быть такая последовательность:

```
D/PlayerDebug: 🚪 Инициировано закрытие плеера
D/PlayerDebug: ✅ Плеер полностью закрыт, ресурсы освобождены
```

**Если порядок нарушен** - значит есть race condition!

---

## Альтернативные решения (отклонены)

### ❌ Вариант 1: Добавить задержку перед popBackStack()
```kotlin
Handler(Looper.getMainLooper()).postDelayed({
    navController.popBackStack()
}, 300)
```

**Почему отклонено:**
- Магические числа (300ms) - ненадежно
- На медленных устройствах может быть недостаточно
- На быстрых - избыточная задержка
- Не гарантирует завершение `exoPlayer.release()`

### ❌ Вариант 2: Использовать Animated Navigation
```kotlin
AnimatedNavHost {
    fadeOut(animationSpec = tween(500))
}
```

**Почему отклонено:**
- Не решает проблему - только маскирует её анимацией
- Ресурсы всё равно освобождаются параллельно с анимацией
- Увеличивает время перехода для пользователя

### ✅ Выбранное решение: Состояние Closing с callback
- Гарантирует освобождение ресурсов **до** навигации
- Не зависит от таймеров
- Работает одинаково на любых устройствах
- Явный контроль потока выполнения

---

## Риски и ограничения

⚠️ **Зависимость от корректной работы `onDispose`**
- Если Compose не вызовет `onDispose` по какой-то причине, навигация зависнет
- Решение: добавить timeout в Production-версии (через `Handler.postDelayed` как fallback)

⚠️ **Увеличение времени закрытия на ~100-200ms**
- Пользователь заметит небольшую задержку
- Решение: показывать черный экран (визуально быстрее, чем наложение)

⚠️ **Дополнительный код и состояние**
- Усложнение логики навигации
- Решение: тщательное документирование и тесты

---

## Чеклист реализации

- [ ] **Этап 1:** Добавить параметр `onPlayerClosed` в `PlayerScreen.kt`
- [ ] **Этап 1:** Вызывать callback в `onDispose` после `exoPlayer.release()`
- [ ] **Этап 2:** Добавить состояние `Closing` в `PlayerUiState`
- [ ] **Этап 2:** Создать метод `initiateClosing()` в `PlayerViewModel`
- [ ] **Этап 2:** Создать метод `onPlayerFullyClosed()` в `PlayerViewModel`
- [ ] **Этап 3:** Обработать `Closing` в `MainActivity` (показывать черный экран)
- [ ] **Этап 3:** Переместить `popBackStack()` в callback `onPlayerClosed`
- [ ] **Этап 4:** Добавить `BackHandler` в `PlayerScreen`
- [ ] **Этап 4:** Связать кнопку "Назад" с `viewModel.initiateClosing()`
- [ ] **Тестирование:** Проверить все 5 сценариев
- [ ] **Тестирование:** Проверить последовательность в логах
- [ ] **Документация:** Обновить комментарии в коде

---

## Ожидаемый результат

После реализации:
- ✅ При нажатии "Назад" плеер сначала полностью закрывается, затем появляется галерея
- ✅ Нет визуального наложения интерфейсов
- ✅ Переход выглядит профессионально (черный экран → галерея)
- ✅ Гарантировано освобождение ресурсов ExoPlayer до навигации
- ✅ Работает стабильно на всех устройствах независимо от производительности
