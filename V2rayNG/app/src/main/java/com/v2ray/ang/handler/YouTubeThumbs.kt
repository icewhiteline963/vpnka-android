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

    fun cached(url: String): ImageBitmap? = cache.get(url)?.asImageBitmap()

    /**
     * Когда обожглись в последний раз.
     *
     * Раньше адрес заносился в «неудачные» НАВСЕГДА: пролистал ленту с
     * выключенным ВПН — и эти обложки не грузились до перезапуска
     * приложения, даже когда связь вернулась. Держим отметку пять минут.
     */
    private const val FAIL_TTL_MS = 5 * 60 * 1000L
    private val failedAt = java.util.Collections.synchronizedMap(mutableMapOf<String, Long>())

    suspend fun load(url: String): ImageBitmap? {
        cache.get(url)?.let { return it.asImageBitmap() }
        val burned = failedAt[url]
        if (burned != null && System.currentTimeMillis() - burned < FAIL_TTL_MS) return null
        return withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", YouTubeService.USER_AGENT_DESKTOP)
                .build()
            // Вызов держим, чтобы оборвать его при уходе строки с экрана:
            // отмена корутины сама по себе блокирующий execute() не
            // прерывает, и быстрая прокрутка догружала все обложки подряд
            // через наши ноды.
            val call = YouTubeService.proxiedClient().newCall(req)
            try {
                kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]?.invokeOnCompletion {
                    if (it != null) runCatching { call.cancel() }
                }
                runCatching {
                    call.execute().use { resp ->
                        if (!resp.isSuccessful) return@use null
                        val bytes = resp.body?.bytes() ?: return@use null
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                }.getOrNull()
                    ?.also { cache.put(url, it); failedAt.remove(url) }
                    ?.asImageBitmap()
                    ?: run { failedAt[url] = System.currentTimeMillis(); null }
            } finally {
                runCatching { if (!call.isExecuted()) call.cancel() }
            }
        }
    }
}
