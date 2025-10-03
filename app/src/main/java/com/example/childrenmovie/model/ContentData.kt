// Файл: model/ContentData.kt

package com.example.childrenmovie.model

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

    // Это поле будет null, если type == "movie"
    @field:Json(name = "episodes") val episodes: List<Episode>? = null,

    // Это поле будет null, если type == "series"
    @field:Json(name = "pageUrl") val pageUrl: String? = null
)

// Этот класс описывает один эпизод внутри сериала
data class Episode(
    @field:Json(name = "id") val id: String,
    @field:Json(name = "title") val title: String,
    @field:Json(name = "pageUrl") val pageUrl: String,
    @field:Json(name = "posterUrl") val posterUrl: String? = null
)