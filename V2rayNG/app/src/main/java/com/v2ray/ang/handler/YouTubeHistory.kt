package com.v2ray.ang.handler

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * История поиска и просмотра — локально, никуда не уходит.
 *
 * Нужна ради одной простой вещи: приложение должно открываться тем, что
 * интересно человеку. Раньше главная запускала зашитый поиск «electronic
 * music», и русскоязычный человек видел стену англоязычной электроники —
 * разговор начинался с чужого.
 */
object YouTubeHistory {
    private const val KEY_Q = "vpnka_youtube_queries"
    private const val KEY_POS = "vpnka_youtube_positions"
    private const val MAX_Q = 8
    private val gson = Gson()

    // ---- запросы ----------------------------------------------------

    fun recentQueries(): List<String> {
        val raw = MmkvManager.decodeSettingsString(KEY_Q) ?: return emptyList()
        return try {
            gson.fromJson<List<String>>(raw, object : TypeToken<List<String>>() {}.type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun lastQuery(): String? = recentQueries().firstOrNull()

    fun rememberQuery(q: String) {
        val clean = q.trim()
        if (clean.isBlank()) return
        val list = recentQueries().toMutableList()
        list.removeAll { it.equals(clean, ignoreCase = true) }
        list.add(0, clean)
        MmkvManager.encodeSettings(KEY_Q, gson.toJson(list.take(MAX_Q)))
    }

    fun clearQueries() = MmkvManager.encodeSettings(KEY_Q, "[]")

    // ---- позиция просмотра ------------------------------------------

    private fun positions(): MutableMap<String, Long> {
        val raw = MmkvManager.decodeSettingsString(KEY_POS) ?: return mutableMapOf()
        return try {
            gson.fromJson<MutableMap<String, Long>>(
                raw, object : TypeToken<MutableMap<String, Long>>() {}.type,
            ) ?: mutableMapOf()
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    /** На какой секунде человек остановился. 0 — не смотрел или досмотрел. */
    fun position(url: String): Long = positions()[url] ?: 0L

    /**
     * Запоминаем, где остановились.
     *
     * Первые полминуты не в счёт: заглянул и закрыл — это не «смотрел».
     * Последние полминуты тоже: досмотренное должно начинаться сначала, а
     * не с титров.
     */
    fun savePosition(url: String, posSec: Long, durationSec: Long) {
        val map = positions()
        // «Досмотрел» — это не «оказался в последних тридцати секундах».
        // У ролика короче минуты правая часть уходила в минус, и условие было
        // истинным ВСЕГДА: открыл пятнадцатисекундный ролик, сразу закрыл — и
        // он уже «просмотрен», а значит попадёт под уборку скачанного.
        val finished = durationSec >= 60 && posSec > durationSec - 30 &&
            posSec > durationSec * 0.6
        if (posSec < 30 || finished) {
            map.remove(url)
        } else {
            map[url] = posSec
        }
        MmkvManager.encodeSettings(KEY_POS, gson.toJson(map))
        // Досмотренное отмечаем ОТДЕЛЬНО. В карте позиций «досмотрел» и
        // «не открывал» — одно и то же значение 0, и отличить их потом
        // невозможно; а уборке скачанного нужно именно это различие.
        if (finished) markWatched(url)
    }

    // ---- что досмотрено (для уборки скачанного) ----------------------

    private const val KEY_WATCHED = "yt_watched_at"

    private fun watched(): MutableMap<String, Long> {
        val raw = MmkvManager.decodeSettingsString(KEY_WATCHED) ?: return mutableMapOf()
        return try {
            gson.fromJson<MutableMap<String, Long>>(
                raw, object : TypeToken<MutableMap<String, Long>>() {}.type,
            ) ?: mutableMapOf()
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    fun markWatched(url: String) {
        val m = watched()
        m[url] = System.currentTimeMillis()
        // Держим последние 300 — карта не должна расти вечно.
        val trimmed = m.entries.sortedByDescending { it.value }.take(300)
            .associate { it.key to it.value }
        MmkvManager.encodeSettings(KEY_WATCHED, gson.toJson(trimmed))
    }

    /** Когда досмотрели. 0 — не досматривали. */
    fun watchedAt(url: String): Long = watched()[url] ?: 0L
}
