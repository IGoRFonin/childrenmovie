### Детализация: Этап 2. Реализация слоя данных (Data Layer)

На этом этапе мы научим приложение:
1.  Понимать структуру нашего JSON-файла.
2.  Ходить в интернет за данными.
3.  Парсить HTML-страницы.
4.  Сохранять данные на устройстве (кешировать).
5.  Управлять всем этим процессом.

#### **Задача 2.1: Создание data-классов**

**Что делаем?** Создаем Kotlin-классы, которые будут точным отражением структуры нашего JSON. Библиотека Moshi будет автоматически "переливать" данные из JSON в объекты этих классов.

**Как делаем?**
1.  В панели проекта слева, в пакете `model` (который мы создали на Этапе 1), нажми правой кнопкой мыши и выбери `New` -> `Kotlin Class/File`.
2.  Введи имя файла `ContentData.kt` и выбери `File` из списка.
3.  Скопируй и вставь в этот файл следующий код:

```kotlin
// Файл: model/ContentData.kt

package com.yourpackage.kidsplayer.model // <-- ЗАМЕНИ com.yourpackage.kidsplayer на свой пакет

import com.squareup.moshi.Json

// Этот класс представляет корневую структуру всего JSON-файла
data class ContentRoot(
    @field:Json(name = "version") val version: Double,
    @field:Json(name = "content") val content: List<ContentItem>
)

// Этот класс описывает один элемент в списке "content": либо сериал, либо фильм
data class ContentItem(
    @field:Json(name = "type") val type: String,
    @field:Json(name = "id") val id: String,
    @field:Json(name = "title") val title: String,
    @field:Json(name = "posterUrl") val posterUrl: String,
    @field:Json(name = "description") val description: String,

    // Это поле будет null, если type == "movie"
    @field:Json(name = "episodes") val episodes: List<Episode>? = null,

    // Это поле будет null, если type == "series"
    @field:Json(name = "pageUrl") val pageUrl: String? = null
)

// Этот класс описывает один эпизод внутри сериала
data class Episode(
    @field:Json(name = "id") val id: String,
    @field:Json(name = "title") val title: String,
    @field:Json(name = "pageUrl") val pageUrl: String
)
```
**Что этот код делает?**
*   `@field:Json(name = "...")` — это подсказка для библиотеки Moshi. Она говорит: "когда в JSON встретишь поле `posterUrl`, его значение нужно положить в переменную `posterUrl` этого класса".
*   `episodes: List<Episode>? = null` — `?` означает, что это поле может отсутствовать в JSON (как в случае с фильмом), и тогда оно будет равно `null`.

---

#### **Задача 2.2: Реализация `RemoteDataSource`**

**Что делаем?** Создаем "специалиста по интернету". Этот класс будет уметь только одно: выполнять сетевые запросы и возвращать "сырой" результат (строку с JSON или HTML).

**Как делаем?**
1.  В пакете `data`, нажми правой кнопкой мыши -> `New` -> `Kotlin Class/File`.
2.  Введи имя `RemoteDataSource.kt` и выбери `Class`.
3.  Скопируй и вставь в этот файл следующий код:

```kotlin
// Файл: data/RemoteDataSource.kt

package com.yourpackage.kidsplayer.data // <-- ЗАМЕНИ com.yourpackage.kidsplayer на свой пакет

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

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

class RemoteDataSource(
    private val client: OkHttpClient, // OkHttp-клиент для запросов
    private val moshi: Moshi // Moshi для парсинга JSON
) {

    // Функция для скачивания JSON-файла с контентом
    suspend fun fetchContent(url: String): String {
        return withContext(Dispatchers.IO) { // Выполняем в фоновом потоке
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw Exception("Failed to download content JSON. Code: ${response.code}")
            }
            response.body?.string() ?: throw Exception("Response body is null")
        }
    }

    // Функция для скачивания и парсинга HTML-страницы ok.ru
    suspend fun fetchVideoUrl(pageUrl: String): String {
        return withContext(Dispatchers.IO) { // Выполняем в фоновом потоке
            val request = Request.Builder()
                .url(pageUrl)
                // Важно! Притворяемся браузером, чтобы ok.ru нас не заблокировал
                .addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw Exception("Failed to download OK.ru page. Code: ${response.code}")
            }
            val html = response.body?.string() ?: throw Exception("Response body is null")

            // !!! ВАЖНАЯ ЧАСТЬ: Парсинг страницы OK.ru !!!
            // Алгоритм извлечения ссылки на видео:
            // 1. Находим элемент с атрибутом data-module="OKVideo"
            // 2. Парсим JSON из атрибута data-options
            // 3. Внутри полученного объекта парсим JSON из flashvars.metadata
            // 4. В metadata находим массив videos с разными качествами видео
            // 5. Выбираем лучшее доступное качество

            val doc = Jsoup.parse(html)
            val videoElement = doc.select("[data-module=OKVideo]").first()
                ?: throw Exception("Could not find video element with data-module=OKVideo")

            // Типобезопасный парсинг с использованием data-классов
            val optionsJson = videoElement.attr("data-options")
            val optionsAdapter = moshi.adapter(OkRuOptions::class.java)
            val options = optionsAdapter.fromJson(optionsJson)
                ?: throw Exception("Could not parse data-options")

            val metadataJson = options.flashvars.metadata
            val metadataAdapter = moshi.adapter(VideoMetadata::class.java)
            val metadata = metadataAdapter.fromJson(metadataJson)
                ?: throw Exception("Could not parse metadata")

            val videos = metadata.videos

            // Приоритет качества: full > hd > sd > low > lowest > mobile
            val qualityPriority = listOf("full", "hd", "sd", "low", "lowest", "mobile")

            var bestVideo: String? = null
            for (quality in qualityPriority) {
                val video = videos.find { it.name == quality && !it.disallowed }
                if (video != null) {
                    bestVideo = video.url
                    break
                }
            }

            return@withContext bestVideo ?: throw Exception("Could not find any video URL")
        }
    }
}
```
**Что этот код делает?**
*   `suspend` означает, что это долгая операция, и ее нужно запускать в корутине (в фоновом потоке).
*   `withContext(Dispatchers.IO)` — это команда "выполнить этот блок кода в специальном потоке для сетевых и дисковых операций", чтобы не тормозить интерфейс.
*   `fetchContent` просто скачивает файл по URL.
*   `fetchVideoUrl` делает то же самое, но потом использует `Jsoup` для поиска нужного элемента на HTML-странице и типобезопасный парсинг JSON с помощью Moshi.
*   Data-классы (`OkRuOptions`, `FlashVars`, `VideoMetadata`, `VideoQuality`) делают код более надежным и понятным, заменяя небезопасные приведения типов.

**Важно:** В `MainActivity` (Этап 3) нужно будет создавать `RemoteDataSource` с двумя параметрами: `RemoteDataSource(okHttpClient, moshi)`, не забудь передать оба!

---

#### **Задача 2.3: Реализация `LocalDataSource`**

**Что делаем?** Создаем "кладовщика". Этот класс умеет сохранять строку с JSON в файл и читать ее из файла.

**Как делаем?**
1.  В пакете `data`, нажми правой кнопкой мыши -> `New` -> `Kotlin Class/File`.
2.  Введи имя `LocalDataSource.kt` и выбери `Class`.
3.  Скопируй и вставь в этот файл следующий код:

```kotlin
// Файл: data/LocalDataSource.kt

package com.yourpackage.kidsplayer.data // <-- ЗАМЕНИ com.yourpackage.kidsplayer на свой пакет

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException

private const val CACHE_FILE_NAME = "content_cache.json"

class LocalDataSource(
    private val context: Context // Нужен для доступа к файловой системе приложения
) {

    // Сохраняет JSON в приватный файл приложения
    suspend fun saveContent(json: String) {
        withContext(Dispatchers.IO) {
            context.openFileOutput(CACHE_FILE_NAME, Context.MODE_PRIVATE).use {
                it.write(json.toByteArray())
            }
        }
    }

    // Загружает JSON из файла. Возвращает null, если файла нет.
    suspend fun loadContent(): String? {
        return withContext(Dispatchers.IO) {
            try {
                context.openFileInput(CACHE_FILE_NAME).bufferedReader().use {
                    it.readText()
                }
            } catch (e: FileNotFoundException) {
                null // Это нормальная ситуация при первом запуске
            }
        }
    }
}
```

---

#### **Задача 2.4: Реализация `Repository`**

**Что делаем?** Создаем "менеджера". Он будет главным для `ViewModel`. Репозиторий будет решать, когда обратиться к "специалисту по интернету", а когда к "кладовщику", и преобразует "сырые" данные в готовые Kotlin-объекты.

**Как делаем?**
1.  В пакете `data`, нажми правой кнопкой мыши -> `New` -> `Kotlin Class/File`.
2.  Введи имя `ContentRepository.kt` и выбери `Class`.
3.  Скопируй и вставь в этот файл следующий код:

```kotlin
// Файл: data/ContentRepository.kt

package com.yourpackage.kidsplayer.data // <-- ЗАМЕНИ com.yourpackage.kidsplayer на свой пакет

import com.squareup.moshi.Moshi
import com.yourpackage.kidsplayer.model.ContentItem
import com.yourpackage.kidsplayer.model.ContentRoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class ContentRepository(
    private val remoteDataSource: RemoteDataSource,
    private val localDataSource: LocalDataSource,
    private val moshi: Moshi
) {

    // Функция, которая возвращает поток данных
    fun getContent(url: String): Flow<List<ContentItem>> = flow {
        // 1. Сначала пытаемся загрузить данные из кеша и сразу отдать их
        val cachedJson = localDataSource.loadContent()
        if (cachedJson != null) {
            try {
                val cachedContent = parseJson(cachedJson)
                emit(cachedContent.content) // Отдаем кешированные данные
            } catch (e: Exception) {
                // Ошибка парсинга кеша, ничего страшного, просто идем в сеть
            }
        }

        // 2. Затем всегда идем в сеть за свежими данными
        try {
            val remoteJson = remoteDataSource.fetchContent(url)
            val remoteContent = parseJson(remoteJson)

            // 3. Сохраняем свежие данные в кеш
            localDataSource.saveContent(remoteJson)

            // 4. Отдаем свежие данные
            emit(remoteContent.content)
        } catch (e: Exception) {
            // Если кеша не было и сеть не удалась, можно сообщить об ошибке
            if (cachedJson == null) {
                throw e // Пробрасываем ошибку дальше
            }
        }
    }.flowOn(Dispatchers.IO) // Весь flow будет выполняться в фоновом потоке

    // Обертка для парсера Moshi
    private fun parseJson(json: String): ContentRoot {
        val adapter = moshi.adapter(ContentRoot::class.java)
        return adapter.fromJson(json) ?: throw Exception("Failed to parse JSON")
    }

    // Простая обертка для получения URL видео
    suspend fun getVideoUrl(pageUrl: String): String {
        return remoteDataSource.fetchVideoUrl(pageUrl)
    }
}
```
**Что этот код делает?**
*   `getContent` использует `Flow` — это как "конвейер", который может выдавать данные несколько раз. Сначала он выдает данные из кеша (если есть), а потом — свежие данные из сети. UI подпишется на этот `Flow` и автоматически обновится, когда придут новые данные.
*   `getVideoUrl` — просто передает запрос в `RemoteDataSource`, так как кешировать ссылки на видео не нужно.
