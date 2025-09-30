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
