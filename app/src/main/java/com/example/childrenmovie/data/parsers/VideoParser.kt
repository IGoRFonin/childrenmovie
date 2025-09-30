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
