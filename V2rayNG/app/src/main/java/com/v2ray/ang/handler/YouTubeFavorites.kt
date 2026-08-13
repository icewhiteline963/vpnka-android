package com.v2ray.ang.handler

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Saved YouTube links ("Избранное"), kept in the app's encrypted MMKV settings.
 * Local-only: these are just public video URLs, not synced to the server.
 */
object YouTubeFavorites {
    private const val KEY = "vpnka_youtube_favorites"
    private val gson = Gson()

    data class Fav(val url: String, val title: String, val uploader: String, val durationSec: Long)

    private fun load(): MutableList<Fav> {
        val raw = MmkvManager.decodeSettingsString(KEY) ?: return mutableListOf()
        return try {
            gson.fromJson<MutableList<Fav>>(raw, object : TypeToken<MutableList<Fav>>() {}.type)
                ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    private fun store(list: List<Fav>) {
        MmkvManager.encodeSettings(KEY, gson.toJson(list))
    }

    fun all(): List<Fav> = load()

    fun isFav(url: String): Boolean = load().any { it.url == url }

    /** Adds if absent, removes if present. Returns the new favourited state. */
    fun toggle(f: Fav): Boolean {
        val list = load()
        val idx = list.indexOfFirst { it.url == f.url }
        return if (idx >= 0) {
            list.removeAt(idx); store(list); false
        } else {
            list.add(0, f); store(list); true
        }
    }

    fun remove(url: String) {
        val list = load()
        if (list.removeAll { it.url == url }) store(list)
    }
}
