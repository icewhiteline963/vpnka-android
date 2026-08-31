package com.v2ray.ang.handler

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Журнал посещений браузера. Живёт только на устройстве.
 *
 * Инкогнито сюда НЕ пишет — иначе окно «не оставляет следов» оставляло бы
 * главный след. Проверка стоит на стороне вкладки: у вкладки-инкогнито
 * [add] просто не вызывается.
 */
object BrowserHistory {

    private const val KEY = "browser_history"
    private const val MAX = 500

    private val gson = Gson()

    data class Entry(val url: String, val title: String, val ts: Long)

    fun all(): List<Entry> {
        val raw = MmkvManager.decodeSettingsString(KEY) ?: return emptyList()
        return try {
            gson.fromJson<List<Entry>>(raw, object : TypeToken<List<Entry>>() {}.type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun save(list: List<Entry>) =
        MmkvManager.encodeSettings(KEY, gson.toJson(list.take(MAX)))

    /**
     * Записать посещение. Повторный заход на тот же адрес не плодит строки —
     * поднимает существующую наверх: иначе журнал за день забивался бы одной
     * страницей, на которую человек возвращался десять раз.
     */
    fun add(url: String, title: String) {
        val u = url.trim()
        if (u.isEmpty() || u == "about:blank") return
        if (u.startsWith("data:") || u.startsWith("javascript:")) return
        val rest = all().filterNot { it.url == u }
        save(listOf(Entry(u, title.trim(), System.currentTimeMillis())) + rest)
    }

    /** Заголовок приходит позже адреса — дописываем его к уже записанной строке. */
    fun retitle(url: String, title: String) {
        if (title.isBlank()) return
        val list = all()
        val i = list.indexOfFirst { it.url == url.trim() }
        if (i < 0 || list[i].title == title.trim()) return
        save(list.toMutableList().also { it[i] = it[i].copy(title = title.trim()) })
    }

    fun remove(url: String) = save(all().filterNot { it.url == url })

    fun clear() = MmkvManager.encodeSettings(KEY, "[]")

    fun search(q: String): List<Entry> {
        val needle = q.trim().lowercase()
        if (needle.isEmpty()) return all()
        return all().filter {
            it.title.lowercase().contains(needle) || it.url.lowercase().contains(needle)
        }
    }
}
