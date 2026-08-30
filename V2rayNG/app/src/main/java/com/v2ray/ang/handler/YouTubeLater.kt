package com.v2ray.ang.handler

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Очередь «скачать позже».
 *
 * Скачивание идёт через наши ноды и на мобильном интернете стоит человеку
 * денег, поэтому решение «взять с собой» и решение «качать прямо сейчас»
 * должны быть разными. Раньше их было не разделить: либо качаешь немедленно,
 * либо не отмечаешь ролик никак.
 *
 * Лежит в том же зашифрованном хранилище, что и плейлисты: это просто
 * публичные ссылки, но привычка держать их вместе с остальным правильная.
 */
object YouTubeLater {
    private const val KEY = "vpnka_youtube_later"
    private val gson = Gson()

    data class Item(
        val url: String,
        val title: String,
        val uploader: String = "",
        val addedAt: Long = 0L,
    )

    private fun load(): MutableList<Item> {
        val raw = MmkvManager.decodeSettingsString(KEY) ?: return mutableListOf()
        return try {
            gson.fromJson<MutableList<Item>>(raw, object : TypeToken<MutableList<Item>>() {}.type)
                ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    private fun store(list: List<Item>) {
        MmkvManager.encodeSettings(KEY, gson.toJson(list))
    }

    fun all(): List<Item> = load()

    fun count(): Int = load().size

    fun has(url: String): Boolean = load().any { it.url == url }

    /** Повторное добавление не плодит дублей — оно просто ничего не меняет. */
    fun add(url: String, title: String, uploader: String = "") {
        val list = load()
        if (list.any { it.url == url }) return
        list.add(Item(url, title, uploader, System.currentTimeMillis()))
        store(list)
    }

    fun remove(url: String) {
        store(load().filterNot { it.url == url })
    }

    fun clear() = store(emptyList())
}
