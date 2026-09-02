package com.v2ray.ang.handler

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Named YouTube playlists, in the app's encrypted MMKV. «Избранное» is a
 * reserved, non-deletable playlist (FAV_ID); legacy standalone favourites are
 * migrated into it once. Local-only: these are just public video links.
 */
object YouTubePlaylists {
    private const val KEY = "vpnka_youtube_playlists"
    private const val LEGACY_FAV_KEY = "vpnka_youtube_favorites"
    const val FAV_ID = "fav"
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
        val durationSec: Long = 0L,
        val addedAt: Long = 0L,
    )
    // Умолчания у всех полей — см. пояснение у `Item` выше. Здесь важнее
    // всего `videos`: без конструктора Gson оставлял бы список null, и
    // открытие плейлиста роняло приложение.
    data class Playlist(
        val id: String = "",
        var name: String = "",
        val videos: MutableList<Item> = mutableListOf(),
    )

    private fun load(): MutableList<Playlist> {
        val raw = MmkvManager.decodeSettingsString(KEY)
        // Повреждённые данные НЕ затираем.
        //
        // Раньше любой сбой разбора давал пустой список, тут же не находил
        // «Избранное» и СОХРАНЯЛ поверх исходных данных пустышку: все
        // плейлисты и избранное исчезали безвозвратно при первом же чтении —
        // а читается это прямо из отрисовки списка.
        var broken = false
        val list: MutableList<Playlist> = if (raw == null) mutableListOf()
        else try {
            gson.fromJson<MutableList<Playlist>>(raw, object : TypeToken<MutableList<Playlist>>() {}.type)
                ?: mutableListOf()
        } catch (e: Exception) {
            broken = true
            mutableListOf()
        }
        // Guarantee «Избранное» exists (first), migrating any legacy favourites.
        if (list.none { it.id == FAV_ID }) {
            list.add(0, Playlist(FAV_ID, "Избранное", migrateLegacyFavs().toMutableList()))
            if (!broken) store(list)
        }
        return list
    }

    private fun migrateLegacyFavs(): List<Item> {
        val raw = MmkvManager.decodeSettingsString(LEGACY_FAV_KEY) ?: return emptyList()
        return try {
            gson.fromJson<List<Item>>(raw, object : TypeToken<List<Item>>() {}.type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun store(list: List<Playlist>) {
        MmkvManager.encodeSettings(KEY, gson.toJson(list))
    }

    /** «Избранное» first, then user playlists in creation order. */
    fun all(): List<Playlist> = load()

    fun get(id: String): Playlist? = load().firstOrNull { it.id == id }

    fun create(name: String): String {
        val list = load()
        val id = "pl_" + System.currentTimeMillis()
        list.add(Playlist(id, name.trim().ifBlank { "Плейлист" }))
        store(list)
        return id
    }

    fun rename(id: String, name: String) {
        val list = load()
        list.firstOrNull { it.id == id }?.let { it.name = name.trim().ifBlank { it.name } }
        store(list)
    }

    /** Deletes a user playlist. «Избранное» can never be removed. */
    fun delete(id: String) {
        if (id == FAV_ID) return
        val list = load()
        if (list.removeAll { it.id == id }) store(list)
    }

    fun contains(id: String, url: String): Boolean =
        get(id)?.videos?.any { it.url == url } == true

    fun add(id: String, item: Item) {
        val list = load()
        val pl = list.firstOrNull { it.id == id } ?: return
        if (pl.videos.none { it.url == item.url }) {
            pl.videos.add(0, if (item.addedAt == 0L) item.copy(addedAt = System.currentTimeMillis()) else item)
            store(list)
        }
    }

    fun remove(id: String, url: String) {
        val list = load()
        val pl = list.firstOrNull { it.id == id } ?: return
        if (pl.videos.removeAll { it.url == url }) store(list)
    }
}
