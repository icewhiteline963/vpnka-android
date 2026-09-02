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
    private const val KEY_WIFI = "vpnka_youtube_later_wifi"
    private val gson = Gson()

    data class Item(
        // У ВСЕХ полей значения по умолчанию — намеренно: иначе Kotlin не
        // создаёт беспараметрический конструктор, Gson собирает объект в
        // обход конструктора, и поля, которых нет в старых записях,
        // получают НЕ котлиновское умолчание, а пустое значение JVM (для
        // строки — null). Именно так падало открытие «Видео»: у записи без
        // `mime` в конструктор приезжал null. См. DownloadRecords.
        val url: String = "",
        val title: String = "",
        val uploader: String = "",
        val addedAt: Long = 0L,
        /** Качество, выбранное для ЭТОЙ строки: «720p», «1080p», «4K», «audio». */
        var quality: String = "",
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

    /** У каждой строки очереди своё качество — так в макете, и так честнее:
     *  лекцию берут в 480p, а фильм в 1080p, и решают это по-разному. */
    fun setQuality(url: String, quality: String) {
        val list = load()
        list.firstOrNull { it.url == url }?.quality = quality
        store(list)
    }

    /**
     * Только по Wi-Fi: очередь дождётся дома, а не съест мобильный трафик.
     *
     * По умолчанию ВЫКЛЮЧЕНО. Пока правило ничего не делало, значение стояло
     * «включено» и никого не задевало; с того дня, как оно заработало, всем,
     * кто настройку не трогал, загрузки на мобильной сети молча вставали бы
     * в ожидание. Менять поведение у всех разом без спроса нельзя.
     */
    var wifiOnly: Boolean
        get() = MmkvManager.decodeSettingsBool(KEY_WIFI, false)
        set(v) { MmkvManager.encodeSettings(KEY_WIFI, v) }

    /** Качать только ночью: 00:00–07:00. Полезно там, где днём канал занят. */
    var nightOnly: Boolean
        get() = MmkvManager.decodeSettingsBool("yt_night_only", false)
        set(v) { MmkvManager.encodeSettings("yt_night_only", v) }

    /** Ночное окно — час начала и час конца (конец не включительно). */
    const val NIGHT_FROM = 0
    const val NIGHT_TO = 7
}
