package com.v2ray.ang.handler

/**
 * Thin wrapper over the reserved «Избранное» playlist so the ★ button keeps
 * working. All state lives in [YouTubePlaylists].
 */
object YouTubeFavorites {
    data class Fav(val url: String, val title: String, val uploader: String, val durationSec: Long)

    fun all(): List<Fav> =
        YouTubePlaylists.get(YouTubePlaylists.FAV_ID)?.videos
            ?.map { Fav(it.url, it.title, it.uploader, it.durationSec) }
            ?: emptyList()

    fun isFav(url: String): Boolean = YouTubePlaylists.contains(YouTubePlaylists.FAV_ID, url)

    /** Adds if absent, removes if present. Returns the new favourited state. */
    fun toggle(f: Fav): Boolean =
        if (isFav(f.url)) {
            YouTubePlaylists.remove(YouTubePlaylists.FAV_ID, f.url); false
        } else {
            YouTubePlaylists.add(
                YouTubePlaylists.FAV_ID,
                YouTubePlaylists.Item(f.url, f.title, f.uploader, f.durationSec),
            ); true
        }

    fun remove(url: String) = YouTubePlaylists.remove(YouTubePlaylists.FAV_ID, url)
}
