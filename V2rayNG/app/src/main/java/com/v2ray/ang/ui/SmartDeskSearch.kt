package com.v2ray.ang.ui

import com.v2ray.ang.handler.Messenger
import com.v2ray.ang.handler.SmartDeskStore
import com.v2ray.ang.handler.YouTubeHistory
import com.v2ray.ang.handler.YouTubeLater
import com.v2ray.ang.handler.YouTubeMarks
import com.v2ray.ang.handler.YouTubePlaylists

/**
 * Общий поиск по рабочему столу — «везде в приложении».
 *
 * Это подпись супер-приложения из макета: человек не должен помнить, в
 * каком именно приложении лежит нужное. Ищем по тому, что уже хранится
 * локально, и НЕ ходим в сеть: поиск по чужим видео — отдельное действие,
 * его человек запускает сам.
 */
object SmartDeskSearch {

    /** Куда вести по нажатию. Приложение стола плюс необязательный запрос. */
    enum class Target { NOTES, CONTACTS, CALENDAR, MESSENGER, YOUTUBE, DOWNLOADS }

    data class Hit(
        val icon: String,
        val title: String,
        val subtitle: String,
        val tag: String,
        val target: Target,
        /** Для чата — с кем: без этого «перейти к переписке» вело в общий список. */
        val chatId: Long? = null,
    )

    /**
     * @param q что ищем; пустая строка — пустой список, подсказки рисует
     *          вызывающий.
     */
    fun search(q0: String, limit: Int = 40): List<Hit> {
        val q = q0.trim()
        if (q.length < 2) return emptyList()
        val hits = mutableListOf<Hit>()

        fun match(vararg fields: String?) =
            fields.any { it != null && it.contains(q, ignoreCase = true) }

        runCatching {
            SmartDeskStore.notes().forEach { n ->
                if (match(n.title, n.body)) {
                    hits.add(
                        Hit("✎", n.title.ifBlank { "Без названия" },
                            n.body.take(60), "заметка", Target.NOTES)
                    )
                }
            }
        }
        runCatching {
            SmartDeskStore.contacts().forEach { c ->
                if (match(c.name, c.phone, c.note)) {
                    hits.add(Hit("☎", c.name, c.phone, "контакт", Target.CONTACTS))
                }
            }
        }
        runCatching {
            SmartDeskStore.calendar().forEach { e ->
                if (match(e.title, e.note)) {
                    hits.add(Hit("▦", e.title, e.note.take(60), "событие", Target.CALENDAR))
                }
            }
        }
        runCatching {
            Messenger.contacts().forEach { c ->
                if (match(c.name)) {
                    hits.add(Hit("✉", c.name, "перейти к переписке", "чат", Target.MESSENGER, chatId = c.id))
                }
            }
        }
        runCatching {
            YouTubeDownloads.entries.forEach { d ->
                if (match(d.label)) {
                    hits.add(Hit("↓", d.label, "загрузка", "файл", Target.DOWNLOADS))
                }
            }
        }
        runCatching {
            YouTubeLater.all().forEach { i ->
                if (match(i.title, i.uploader)) {
                    hits.add(Hit("⏱", i.title, "скачать позже", "видео", Target.DOWNLOADS))
                }
            }
        }
        runCatching {
            YouTubePlaylists.all().forEach { pl ->
                pl.videos.forEach { v ->
                    if (match(v.title, v.uploader)) {
                        hits.add(Hit("▶", v.title, pl.name, "видео", Target.YOUTUBE, v.title))
                    }
                }
            }
        }
        runCatching {
            YouTubeMarks.allNotes().forEach { n ->
                if (match(n.text, n.title)) {
                    hits.add(Hit("◆", n.text, n.title, "заметка к видео", Target.YOUTUBE, n.title))
                }
            }
        }
        runCatching {
            YouTubeHistory.recentQueries().forEach { r ->
                if (match(r)) {
                    hits.add(Hit("⌕", r, "искали раньше", "поиск", Target.YOUTUBE, r))
                }
            }
        }

        // Найденное в сети — не здесь: это отдельное действие, и оно стоит
        // человеку трафика через наши ноды. Вызывающий добавляет строку
        // «искать на YouTube» сам.
        return hits.take(limit)
    }
}
