package com.v2ray.ang.handler

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Обложки видео — через тот же прокси, что и всё остальное.
 *
 * Готовой библиотеки загрузки картинок в проекте нет, и это оказалось
 * удачей: любая из них ходила бы в сеть НАПРЯМУЮ, мимо туннеля. Для
 * ВПН-приложения это утечка — по одним только запросам к обложкам видно,
 * что человек смотрит. Здесь тот же `proxiedClient`, что у метаданных и
 * потоков: нет ВПН — нет картинок, и это правильное поведение.
 *
 * Кэш в памяти на 12 МБ: лента прокручивается туда-сюда, и перекачивать
 * одно и то же через наши ноды незачем.
 */
object YouTubeThumbs {

    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** Адреса, на которых уже обожглись, — не долбим их на каждой прокрутке. */
    private val failed = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    fun cached(url: String): ImageBitmap? = cache.get(url)?.asImageBitmap()

    suspend fun load(url: String): ImageBitmap? {
        cache.get(url)?.let { return it.asImageBitmap() }
        if (url in failed) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", YouTubeService.USER_AGENT_DESKTOP)
                    .build()
                YouTubeService.proxiedClient().newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val bytes = resp.body?.bytes() ?: return@use null
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            }.getOrNull()
                ?.also { cache.put(url, it) }
                ?.asImageBitmap()
                ?: run { failed.add(url); null }
        }
    }
}
