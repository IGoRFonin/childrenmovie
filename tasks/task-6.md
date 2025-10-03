# Task 6: Модульная система парсеров видео (ok.ru + vkvideo.ru)

## Обзор задачи

Разделить монолитный парсинг видео на модульную систему с поддержкой нескольких провайдеров. Текущий код в `RemoteDataSource.fetchVideoUrl()` жестко привязан к ok.ru. Нужно:

1. Вынести ok.ru парсер в отдельный модуль
2. Добавить новый парсер для vkvideo.ru
3. Создать фабрику для автоматического выбора парсера

## Текущая архитектура

```
RemoteDataSource.fetchVideoUrl(pageUrl)
  └─> Парсинг HTML ok.ru (строки 54-131)
      └─> Возврат прямой ссылки на видео
```

## Целевая архитектура

```
RemoteDataSource.fetchVideoUrl(pageUrl)
  └─> VideoParserFactory.getParser(pageUrl)
      ├─> OkRuParser (ok.ru)
      ├─> VkVideoParser (vkvideo.ru)
      └─> [будущие парсеры...]
```

---

## Этап 1: Создание интерфейса VideoParser

**Файл:** `app/src/main/java/com/example/childrenmovie/data/parsers/VideoParser.kt`

```kotlin
package com.example.childrenmovie.data.parsers

interface VideoParser {
    /**
     * Проверяет, может ли парсер обработать данный URL
     */
    fun canParse(url: String): Boolean

    /**
     * Извлекает прямую ссылку на видео из страницы
     * @param pageUrl URL страницы с видео
     * @return Прямая ссылка на видеофайл (.mp4, .m3u8 и т.д.)
     */
    suspend fun parseVideoUrl(pageUrl: String): String
}
```

**Зачем?** Единый контракт для всех парсеров, упрощает добавление новых провайдеров.

---

## Этап 2: Выделение OkRuParser

**Файл:** `app/src/main/java/com/example/childrenmovie/data/parsers/OkRuParser.kt`

### 2.1 Data-классы для ok.ru

```kotlin
package com.example.childrenmovie.data.parsers

import com.squareup.moshi.Json

// Data-классы для типобезопасного парсинга данных OK.ru
data class OkRuOptions(
    @field:Json(name = "flashvars") val flashvars: FlashVars
)

data class FlashVars(
    @field:Json(name = "metadata") val metadata: String
)

data class VideoMetadata(
    @field:Json(name = "videos") val videos: List<VideoQuality>
)

data class VideoQuality(
    @field:Json(name = "name") val name: String,
    @field:Json(name = "url") val url: String,
    @field:Json(name = "disallowed") val disallowed: Boolean
)
```

### 2.2 Реализация OkRuParser

```kotlin
package com.example.childrenmovie.data.parsers

import android.util.Log
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

class OkRuParser(
    private val client: OkHttpClient,
    private val moshi: Moshi
) : VideoParser {

    companion object {
        private const val TAG = "OkRuParser"
    }

    override fun canParse(url: String): Boolean {
        return url.contains("ok.ru", ignoreCase = true)
    }

    override suspend fun parseVideoUrl(pageUrl: String): String {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "▶️ Начинаем загрузку видео из: $pageUrl")

                val request = Request.Builder()
                    .url(pageUrl)
                    .addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36")
                    .build()

                val response = client.newCall(request).execute()
                Log.d(TAG, "📡 HTTP статус: ${response.code}")

                if (!response.isSuccessful) {
                    throw Exception("Failed to download OK.ru page. Code: ${response.code}")
                }
                val html = response.body?.string() ?: throw Exception("Response body is null")
                Log.d(TAG, "📄 HTML загружен, размер: ${html.length} символов")

                // Алгоритм парсинга ok.ru:
                // 1. Найти элемент с data-module="OKVideo"
                // 2. Извлечь JSON из data-options
                // 3. Распарсить flashvars.metadata
                // 4. Выбрать лучшее качество из videos[]

                val doc = Jsoup.parse(html)
                val videoElement = doc.select("[data-module=OKVideo]").first()
                    ?: throw Exception("Could not find video element with data-module=OKVideo")

                val optionsJson = videoElement.attr("data-options")
                Log.d(TAG, "🔍 Найден data-options, размер: ${optionsJson.length} символов")

                val optionsAdapter = moshi.adapter(OkRuOptions::class.java)
                val options = optionsAdapter.fromJson(optionsJson)
                    ?: throw Exception("Could not parse data-options")

                val metadataJson = options.flashvars.metadata
                Log.d(TAG, "📦 metadata JSON: ${metadataJson.take(300)}...")

                val metadataAdapter = moshi.adapter(VideoMetadata::class.java)
                val metadata = metadataAdapter.fromJson(metadataJson)
                    ?: throw Exception("Could not parse metadata")

                val videos = metadata.videos
                Log.d(TAG, "🎬 Найдено видео: ${videos.size} качеств")
                videos.forEach { video ->
                    Log.d(TAG, "  - ${video.name}: ${if (video.disallowed) "ЗАБЛОКИРОВАНО" else "доступно"}")
                }

                // Приоритет качества: full > hd > sd > low > lowest > mobile
                val qualityPriority = listOf("full", "hd", "sd", "low", "lowest", "mobile")

                var bestVideo: String? = null
                for (quality in qualityPriority) {
                    val video = videos.find { it.name == quality && !it.disallowed }
                    if (video != null) {
                        bestVideo = video.url
                        Log.d(TAG, "✅ Выбрано качество: $quality")
                        break
                    }
                }

                val finalUrl = bestVideo ?: throw Exception("Could not find any video URL")
                Log.d(TAG, "🎥 Финальный URL видео: $finalUrl")

                return@withContext finalUrl
            } catch (e: Exception) {
                Log.e(TAG, "❌ Ошибка при загрузке видео: ${e.message}", e)
                throw e
            }
        }
    }
}
```

---

## Этап 3: Создание VkVideoParser

**Файл:** `app/src/main/java/com/example/childrenmovie/data/parsers/VkVideoParser.kt`

### 3.1 Алгоритм парсинга vkvideo.ru

**Входные данные:** HTML-страница vkvideo.ru
**Цель:** Извлечь прямые ссылки на видео из JavaScript-объекта `playerParams`

**Предварительные требования:**
- 🍪 Обязательная кука `remixdsid` (случайная строка 15+ символов)
- User-Agent современного браузера
- HTTPS-соединение

**Шаги:**
1. Сгенерировать случайную куку `remixdsid` (например, `aTc9aaWrJ5mUHdaa4`)
2. Загрузить HTML с кукой в заголовке `Cookie: remixdsid=...`
3. Найти маркер `var playerParams = ` в HTML
4. Извлечь JavaScript объект с **балансировкой скобок** (подсчет `{` и `}`)
5. Распарсить JSON **напрямую** с помощью Moshi (без дополнительной очистки)
6. Извлечь массив `params[0]` с URL всех качеств
7. Извлечь URL для всех качеств (url144, url240, ..., url1080, hls, dash)
8. Выбрать лучшее доступное качество

⚠️ **Критично:**
- **Без куки `remixdsid` парсинг не работает** - сервер не вернёт `playerParams`
- Использовать балансировку скобок вместо regex - паттерн `\{[\\s\\S]*?\}` остановится на первой `}` внутри объекта!

### 3.2 Data-классы для vkvideo.ru

```kotlin
package com.example.childrenmovie.data.parsers

import com.squareup.moshi.Json

data class VkPlayerParams(
    @field:Json(name = "params") val params: List<VkVideoData>
)

data class VkVideoData(
    @field:Json(name = "url144") val url144: String? = null,
    @field:Json(name = "url240") val url240: String? = null,
    @field:Json(name = "url360") val url360: String? = null,
    @field:Json(name = "url480") val url480: String? = null,
    @field:Json(name = "url720") val url720: String? = null,
    @field:Json(name = "url1080") val url1080: String? = null,
    @field:Json(name = "hls") val hls: String? = null,
    @field:Json(name = "dash_sep") val dashSep: String? = null,
    @field:Json(name = "dash_webm") val dashWebm: String? = null,
    @field:Json(name = "dash_webm_av1") val dashWebmAv1: String? = null
)
```

### 3.3 Реализация VkVideoParser

```kotlin
package com.example.childrenmovie.data.parsers

import android.util.Log
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class VkVideoParser(
    private val client: OkHttpClient,
    private val moshi: Moshi
) : VideoParser {

    companion object {
        private const val TAG = "VkVideoParser"

        // Символы для генерации случайной куки
        private const val COOKIE_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        private const val COOKIE_LENGTH = 15
    }

    override fun canParse(url: String): Boolean {
        return url.contains("vkvideo.ru", ignoreCase = true) ||
               url.contains("vk.com/video", ignoreCase = true)
    }

    /**
     * Генерирует случайную куку remixdsid для обхода защиты VK Video
     * Формат: 15 символов (буквы + цифры)
     */
    private fun generateRandomRemixdsid(): String {
        return (1..COOKIE_LENGTH)
            .map { COOKIE_CHARS.random() }
            .joinToString("")
    }

    override suspend fun parseVideoUrl(pageUrl: String): String {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "▶️ Начинаем загрузку видео из vkvideo.ru: $pageUrl")

                // Генерация случайной куки remixdsid для обхода защиты VK
                val remixdsid = generateRandomRemixdsid()
                Log.d(TAG, "🍪 Сгенерирована кука: remixdsid=$remixdsid")

                val request = Request.Builder()
                    .url(pageUrl)
                    .addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36")
                    .addHeader("Cookie", "remixdsid=$remixdsid")
                    .build()

                val response = client.newCall(request).execute()
                Log.d(TAG, "📡 HTTP статус: ${response.code}")

                if (!response.isSuccessful) {
                    throw Exception("Failed to download vkvideo.ru page. Code: ${response.code}")
                }
                val html = response.body?.string() ?: throw Exception("Response body is null")
                Log.d(TAG, "📄 HTML загружен, размер: ${html.length} символов")

                // Шаг 1: Найти начало playerParams
                val startMarker = "var playerParams = "
                val startIndex = html.indexOf(startMarker)
                if (startIndex == -1) {
                    throw Exception("Could not find playerParams in HTML")
                }

                Log.d(TAG, "🔍 Найден playerParams на позиции: $startIndex")

                // Шаг 2: Извлечь JSON с балансировкой скобок
                val jsonStart = startIndex + startMarker.length
                var braceCount = 0
                var jsonEnd = jsonStart

                for (i in jsonStart until html.length) {
                    when (html[i]) {
                        '{' -> braceCount++
                        '}' -> {
                            braceCount--
                            if (braceCount == 0) {
                                jsonEnd = i + 1
                                break
                            }
                        }
                    }
                }

                if (jsonEnd == jsonStart) {
                    throw Exception("Could not find closing brace for playerParams")
                }

                val playerParamsJson = html.substring(jsonStart, jsonEnd)
                Log.d(TAG, "📦 Извлечен JSON, размер: ${playerParamsJson.length} символов")

                // Шаг 3: Распарсить с Moshi (без дополнительной очистки)
                val adapter = moshi.adapter(VkPlayerParams::class.java)
                val playerParams = adapter.fromJson(playerParamsJson)
                    ?: throw Exception("Could not parse playerParams JSON")

                val videoData = playerParams.params.firstOrNull()
                    ?: throw Exception("playerParams.params is empty")

                // Шаг 4: Собрать все доступные URL
                val availableUrls = mutableMapOf<String, String>()

                videoData.url1080?.let { availableUrls["mp4_1080"] = it }
                videoData.url720?.let { availableUrls["mp4_720"] = it }
                videoData.url480?.let { availableUrls["mp4_480"] = it }
                videoData.url360?.let { availableUrls["mp4_360"] = it }
                videoData.url240?.let { availableUrls["mp4_240"] = it }
                videoData.url144?.let { availableUrls["mp4_144"] = it }
                videoData.hls?.let { availableUrls["hls"] = it }
                videoData.dashSep?.let { availableUrls["dash_sep"] = it }
                videoData.dashWebm?.let { availableUrls["dash_webm"] = it }
                videoData.dashWebmAv1?.let { availableUrls["dash_webm_av1"] = it }

                Log.d(TAG, "🎬 Найдено видео: ${availableUrls.size} форматов")
                availableUrls.forEach { (format, url) ->
                    Log.d(TAG, "  - $format: ${url.take(60)}...")
                }

                // Шаг 5: Выбрать лучшее качество
                // Приоритет: mp4_1080 > mp4_720 > mp4_480 > hls > mp4_360 > mp4_240 > mp4_144
                val qualityPriority = listOf(
                    "mp4_1080", "mp4_720", "mp4_480", "hls",
                    "mp4_360", "mp4_240", "mp4_144"
                )

                var bestUrl: String? = null
                for (quality in qualityPriority) {
                    if (availableUrls.containsKey(quality)) {
                        bestUrl = availableUrls[quality]
                        Log.d(TAG, "✅ Выбрано качество: $quality")
                        break
                    }
                }

                val finalUrl = bestUrl ?: throw Exception("Could not find any video URL")
                Log.d(TAG, "🎥 Финальный URL видео: $finalUrl")

                return@withContext finalUrl
            } catch (e: Exception) {
                Log.e(TAG, "❌ Ошибка при загрузке видео: ${e.message}", e)
                throw e
            }
        }
    }
}
```

### 3.4 Критические моменты реализации

⚠️ **Важно для корректной работы:**

1. **Кука `remixdsid` обязательна** 🍪
   - VK Video **требует** куку `remixdsid` для возврата HTML с `playerParams`
   - Без куки сервер возвращает ограниченный HTML или редирект
   - Формат: случайная строка из 15+ символов (буквы + цифры)
   - Пример: `remixdsid=aTc9aaWrJ5mUHdaa4`
   - Решение: генерировать новую куку для **каждого** запроса
   ```kotlin
   .addHeader("Cookie", "remixdsid=$remixdsid")
   ```

2. **Балансировка скобок обязательна**
   - Regex `\{[\\s\\S]*?\}` НЕ работает - остановится на первой `}` внутри объекта
   - Non-greedy квантификатор `*?` не подходит для вложенных структур
   - Решение: ручной подсчет `{` и `}` для извлечения полного JSON

3. **НЕ удалять все escape-символы**
   - Строка `.replace("\\", "")` ломает валидный JSON (`\"`, `\n`, `\t`, `\/`)
   - В реальных данных vkvideo.ru escape-символы уже корректны
   - Решение: парсить JSON напрямую без дополнительной очистки

4. **Проверка структуры ответа**
   ```kotlin
   val params = playerParams.params
   if (params.isEmpty()) {
       throw Exception("params array is empty")
   }
   val videoData = params[0] // Берём первый элемент массива
   ```

5. **Fallback для форматов**
   - Приоритет: `mp4_1080` → `mp4_720` → `mp4_480` → `hls` → остальные
   - HLS (`m3u8`) универсально поддерживается ExoPlayer
   - DASH форматы требуют дополнительной обработки

6. **Обработка URL**
   - URL содержат параметры с `&` - не требуют дополнительного кодирования
   - Проверять наличие протокола `https://`
   - Логировать итоговый URL для отладки

---

## Этап 4: Создание VideoParserFactory

**Файл:** `app/src/main/java/com/example/childrenmovie/data/parsers/VideoParserFactory.kt`

```kotlin
package com.example.childrenmovie.data.parsers

import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient

class VideoParserFactory(
    private val client: OkHttpClient,
    private val moshi: Moshi
) {
    private val parsers: List<VideoParser> = listOf(
        OkRuParser(client, moshi),
        VkVideoParser(client, moshi)
        // Здесь можно добавлять новые парсеры
    )

    /**
     * Выбирает подходящий парсер для данного URL
     * @throws Exception если подходящий парсер не найден
     */
    fun getParser(url: String): VideoParser {
        return parsers.firstOrNull { it.canParse(url) }
            ?: throw Exception("No parser found for URL: $url")
    }
}
```

---

## Этап 5: Обновление RemoteDataSource

**Файл:** `app/src/main/java/com/example/childrenmovie/data/RemoteDataSource.kt`

### Изменения:

```kotlin
package com.example.childrenmovie.data

import android.util.Log
import com.example.childrenmovie.data.parsers.VideoParserFactory
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class RemoteDataSource(
    private val client: OkHttpClient,
    private val moshi: Moshi
) {
    companion object {
        private const val TAG = "RemoteDataSource"
    }

    // Создаем фабрику парсеров
    private val parserFactory = VideoParserFactory(client, moshi)

    // Функция для скачивания JSON-файла с контентом
    suspend fun fetchContent(url: String): String {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw Exception("Failed to download content JSON. Code: ${response.code}")
            }
            response.body?.string() ?: throw Exception("Response body is null")
        }
    }

    // Функция для получения прямой ссылки на видео
    suspend fun fetchVideoUrl(pageUrl: String): String {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "▶️ Получение видео из: $pageUrl")

                // Выбираем подходящий парсер через фабрику
                val parser = parserFactory.getParser(pageUrl)
                Log.d(TAG, "🔧 Используем парсер: ${parser::class.simpleName}")

                // Делегируем парсинг
                val videoUrl = parser.parseVideoUrl(pageUrl)
                Log.d(TAG, "✅ Успешно получен URL: $videoUrl")

                return@withContext videoUrl
            } catch (e: Exception) {
                Log.e(TAG, "❌ Ошибка при получении видео: ${e.message}", e)
                throw e
            }
        }
    }
}
```

**Что удалить:**
- Data-классы `OkRuOptions`, `FlashVars`, `VideoMetadata`, `VideoQuality` (перенесены в OkRuParser.kt)
- Весь код парсинга из метода `fetchVideoUrl()` (строки 56-125)

---

## Этап 6: Обновление структуры проекта

### До рефакторинга:
```
app/src/main/java/com/example/childrenmovie/
├── data/
│   ├── RemoteDataSource.kt (парсинг ok.ru внутри)
│   ├── LocalDataSource.kt
│   └── ContentRepository.kt
```

### После рефакторинга:
```
app/src/main/java/com/example/childrenmovie/
├── data/
│   ├── RemoteDataSource.kt (использует VideoParserFactory)
│   ├── LocalDataSource.kt
│   ├── ContentRepository.kt
│   └── parsers/
│       ├── VideoParser.kt (интерфейс)
│       ├── VideoParserFactory.kt
│       ├── OkRuParser.kt (логика ok.ru)
│       └── VkVideoParser.kt (логика vkvideo.ru)
```

---

## Тестирование

### Шаг 1: Проверка ok.ru (регрессия)
1. Запустить приложение
2. Открыть любой контент с ok.ru из текущего JSON
3. Убедиться, что видео воспроизводится

### Шаг 2: Проверка vkvideo.ru
1. Добавить в JSON тестовый элемент с pageUrl на vkvideo.ru
2. Открыть этот контент в приложении
3. Проверить логи:
   - Должна появиться строка `🍪 Сгенерирована кука: remixdsid=...`
   - Должен выбраться `VkVideoParser`
   - Должен быть найден `playerParams` на определённой позиции
   - Должен быть извлечён JSON с размером
4. Убедиться, что видео воспроизводится
5. **Проверка без куки (для отладки):**
   - Временно закомментировать строку `.addHeader("Cookie", "remixdsid=$remixdsid")`
   - Запустить - должна быть ошибка `Could not find playerParams`
   - Вернуть куку - всё работает

### Шаг 3: Проверка ошибок
1. Подставить невалидный URL (например, youtube.com)
2. Должна быть ошибка "No parser found for URL"

---

## Пример JSON для тестирования

Добавить в `content.json`:

```json
{
  "type": "movie",
  "id": "vk-test-1",
  "title": "Тест VK Video",
  "posterUrl": "https://example.com/poster.jpg",
  "pageUrl": "https://vkvideo.ru/video-73154028_456244345"
}
```

## Ручное тестирование парсинга (curl)

Для проверки необходимости куки можно использовать curl:

**С кукой (работает):**
```bash
curl 'https://vkvideo.ru/video_ext.php?oid=-73154028&id=456244345&hd=4&autoplay=1' \
  -b 'remixdsid=aTc9aaWrJ5mUHdaa4' \
  -H 'user-agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36' \
  | grep -o 'var playerParams = {.*};' | head -c 200
```
Вернёт: `var playerParams = {"type":"vk","params":[{...`

**Без куки (не работает):**
```bash
curl 'https://vkvideo.ru/video_ext.php?oid=-73154028&id=456244345&hd=4&autoplay=1' \
  -H 'user-agent: Mozilla/5.0' \
  | grep -o 'var playerParams'
```
Вернёт: (пустой результат или редирект)

---

## Преимущества новой архитектуры

✅ **Масштабируемость** - добавление новых провайдеров без изменения существующего кода
✅ **Тестируемость** - каждый парсер можно тестировать изолированно
✅ **Читаемость** - логика разделена по файлам
✅ **Поддерживаемость** - изменения в ok.ru не затронут vkvideo.ru
✅ **Расширяемость** - легко добавить YouTube, Rutube и другие провайдеры

---

## Риски и ограничения

### Общие риски для всех парсеров

⚠️ **Зависимость от верстки** - оба парсера зависят от структуры HTML
⚠️ **Блокировки** - провайдеры могут блокировать автоматические запросы (rate limiting)
⚠️ **Недоступность контента** - видео могут быть удалены или приватизированы
⚠️ **Капча** - при частых запросах может появиться защита от ботов

### Технические риски VK Video парсера

⚠️ **Изменение структуры `playerParams`**
- VK может переименовать поля (`url720` → `video_720`)
- Может измениться вложенность JSON (`params[0]` → `videoData`)
- Решение: версионирование парсера, быстрое обновление при поломке

⚠️ **Динамическая загрузка через JavaScript**
- Если `playerParams` генерируется клиентским скриптом (не в HTML)
- Потребуется WebView с JavaScript runtime
- Решение: мониторинг изменений, переход на WebView при необходимости

⚠️ **Антибот защита и требование куки**
- VK Video **требует** куку `remixdsid` в каждом запросе
- Без куки сервер возвращает ограниченный HTML или HTTP 403
- При массовых запросах с одной кукой возможна блокировка
- User-Agent и заголовки должны имитировать браузер
- Решение:
  - Генерировать новую `remixdsid` для каждого запроса
  - Ротация User-Agent при высокой частоте запросов
  - Добавление реферера `Referer: https://vkvideo.ru/`

⚠️ **Геоблокировка контента**
- Видео может быть недоступно в определённых регионах
- URL могут содержать токены с истечением срока действия
- Решение: кеширование URL с проверкой TTL, обработка ошибок 403/451

**Рекомендация:** Добавить fallback-механизм, уведомления об ошибках для родителей и логирование для быстрой диагностики.

---

## Следующие шаги

1. ✅ Создать все файлы из этого плана
2. ✅ Протестировать с реальными URL
3. 🔮 Добавить кеширование извлеченных URL (Task 7)
4. 🔮 Добавить поддержку Rutube (Task 8)
5. 🔮 Реализовать graceful degradation при ошибках парсинга (Task 9)
