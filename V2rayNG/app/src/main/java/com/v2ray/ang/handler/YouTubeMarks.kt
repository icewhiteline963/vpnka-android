package com.v2ray.ang.handler

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Закладки и заметки на таймкодах — то, ради чего видео смотрят как учебный
 * материал, а не просто листают.
 *
 * Хранится локально, рядом с плейлистами: это привязка к публичной ссылке и
 * собственный текст человека, серверу тут делать нечего.
 */
object YouTubeMarks {
    private const val KEY_MARKS = "vpnka_youtube_marks"
    private const val KEY_NOTES = "vpnka_youtube_notes"
    private val gson = Gson()

    /** Закладка: место в ролике, к которому хочется вернуться. */
    data class Mark(val url: String, val atSec: Long, val title: String, val addedAt: Long)

    /** Заметка: то же место плюс собственный текст. */
    data class Note(
        val id: Long,
        val url: String,
        val atSec: Long,
        val title: String,
        var text: String,
        val addedAt: Long,
    )

    private fun <T> load(key: String, type: java.lang.reflect.Type): MutableList<T> {
        val raw = MmkvManager.decodeSettingsString(key) ?: return mutableListOf()
        return try {
            gson.fromJson<MutableList<T>>(raw, type) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    private fun marksAll(): MutableList<Mark> =
        load(KEY_MARKS, object : TypeToken<MutableList<Mark>>() {}.type)

    private fun notesAll(): MutableList<Note> =
        load(KEY_NOTES, object : TypeToken<MutableList<Note>>() {}.type)

    // ---- закладки ---------------------------------------------------

    fun marks(url: String): List<Mark> = marksAll().filter { it.url == url }.sortedBy { it.atSec }

    fun allMarks(): List<Mark> = marksAll().sortedByDescending { it.addedAt }

    /**
     * Ставит закладку. Ближе секунды к существующей — считаем, что человек
     * промахнулся по той же точке, и ничего не плодим.
     */
    fun addMark(url: String, atSec: Long, title: String) {
        val list = marksAll()
        if (list.any { it.url == url && kotlin.math.abs(it.atSec - atSec) <= 1 }) return
        list.add(Mark(url, atSec, title, System.currentTimeMillis()))
        MmkvManager.encodeSettings(KEY_MARKS, gson.toJson(list))
    }

    fun removeMark(url: String, atSec: Long) {
        MmkvManager.encodeSettings(
            KEY_MARKS,
            gson.toJson(marksAll().filterNot { it.url == url && it.atSec == atSec }),
        )
    }

    // ---- заметки ----------------------------------------------------

    fun notes(url: String): List<Note> = notesAll().filter { it.url == url }.sortedBy { it.atSec }

    fun allNotes(): List<Note> = notesAll().sortedByDescending { it.addedAt }

    fun addNote(url: String, atSec: Long, title: String, text: String): Note {
        val list = notesAll()
        val note = Note(
            id = System.currentTimeMillis(),
            url = url, atSec = atSec, title = title, text = text,
            addedAt = System.currentTimeMillis(),
        )
        list.add(note)
        MmkvManager.encodeSettings(KEY_NOTES, gson.toJson(list))
        return note
    }

    fun updateNote(id: Long, text: String) {
        val list = notesAll()
        list.firstOrNull { it.id == id }?.text = text
        MmkvManager.encodeSettings(KEY_NOTES, gson.toJson(list))
    }

    fun removeNote(id: Long) {
        MmkvManager.encodeSettings(KEY_NOTES, gson.toJson(notesAll().filterNot { it.id == id }))
    }
}
