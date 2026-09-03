package com.v2ray.ang.handler

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Что мы скачали и куда положили.
 *
 * Список загрузок жил только в памяти и исчезал вместе с процессом — файлы
 * оставались, но приложение переставало о них знать. Из-за этого нельзя было
 * ни показать «просмотренное занимает столько-то», ни убрать его: уборка
 * обязана трогать ТОЛЬКО те файлы, которые скачали мы сами, а для этого о них
 * надо помнить между запусками.
 */
object DownloadRecords {

    private const val KEY = "yt_download_records"
    private const val MAX = 300

    private val gson = Gson()

    data class Rec(
        val uri: String,
        val name: String,
        val sourceUrl: String?,
        val bytes: Long,
        val savedAt: Long,
        /** Тип файла: без него восстановленная строка не открывалась ничем. */
        val mime: String = "application/octet-stream",
        /** Полка списка: «Видео» | «Файл» | «Субтитры». */
        val kind: String = "Видео",
    )

    /**
     * Как запись выглядит В ФАЙЛЕ — где угодно может не быть чего угодно.
     *
     * Gson НЕ применяет умолчания Kotlin: поля, которых нет в JSON, он
     * оставляет пустыми (для строки — null), сколько бы значений по
     * умолчанию ни стояло в объявлении. Записи, сохранённые до появления
     * `mime` и `kind`, этих полей не содержат — и в `Rec` с необнуляемыми
     * полями приезжал null.
     *
     * Стоило это падения приложения при открытии «Видео»: восстановление
     * списка загрузок переехало на путь открытия, и NullPointerException в
     * конструкторе `Entry` ронял процесс — со стороны «открываю YouTube, всё
     * сворачивается». Поэтому читаем в тип, где обнуляемо ВСЁ, и собираем
     * настоящую запись сами.
     */
    private data class RawRec(
        val uri: String? = null,
        val name: String? = null,
        val sourceUrl: String? = null,
        val bytes: Long? = null,
        val savedAt: Long? = null,
        val mime: String? = null,
        val kind: String? = null,
    )

    fun all(): List<Rec> {
        val raw = MmkvManager.decodeHistoryString(KEY) ?: return emptyList()
        val parsed = try {
            gson.fromJson<List<RawRec?>>(
                raw, object : TypeToken<List<RawRec?>>() {}.type,
            ) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        return parsed.mapNotNull { r ->
            // Без адреса и имени запись бесполезна — такую пропускаем, а не
            // подставляем ей выдумку. Остальное восполняем умолчаниями.
            val uri = r?.uri ?: return@mapNotNull null
            val name = r.name ?: return@mapNotNull null
            Rec(
                uri = uri,
                name = name,
                sourceUrl = r.sourceUrl,
                bytes = r.bytes ?: 0L,
                savedAt = r.savedAt ?: 0L,
                mime = r.mime ?: "application/octet-stream",
                kind = r.kind ?: "Видео",
            )
        }
    }

    private fun save(list: List<Rec>) =
        MmkvManager.encodeHistory(KEY, gson.toJson(list.take(MAX)))

    fun add(
        uri: String,
        name: String,
        sourceUrl: String?,
        bytes: Long,
        mime: String = "application/octet-stream",
        kind: String = "Видео",
    ) {
        if (uri.isBlank()) return
        save(
            listOf(Rec(uri, name, sourceUrl, bytes, System.currentTimeMillis(), mime, kind)) +
                all().filterNot { it.uri == uri },
        )
    }

    fun forget(uri: String) = save(all().filterNot { it.uri == uri })

    /** Полная очистка журнала загрузок (имена + исходные YouTube-ссылки) —
     *  для выхода из аккаунта. */
    fun clearAll() = MmkvManager.encodeHistory(KEY, "[]")

    /**
     * Скачанное, что уже досмотрено дольше [days] дней назад.
     *
     * Ролик без адреса-источника (файл со страницы, субтитры) сюда не попадёт
     * никогда: «просмотрен» про него ничего не значит.
     */
    fun watchedOlderThan(days: Int): List<Rec> {
        val edge = System.currentTimeMillis() - days.toLong() * 24 * 60 * 60 * 1000
        return all().filter { r ->
            val src = r.sourceUrl ?: return@filter false
            val w = YouTubeHistory.watchedAt(src)
            w > 0 && w <= edge
        }
    }

    /** Удалить файл и забыть запись. @return сколько байт освободилось. */
    fun delete(context: Context, r: Rec): Long {
        val u = runCatching { android.net.Uri.parse(r.uri) }.getOrNull()
        val ok = runCatching {
            if (u == null) false
            else if (u.scheme == "content") context.contentResolver.delete(u, null, null) > 0
            else u.path?.let { java.io.File(it).delete() } ?: false
        }.getOrDefault(false)
        // Забываем запись, если файл исчез — сами мы его удалили или он уже
        // пропал (стёрли проводником, почистили систему). Держать запись о
        // несуществующем файле нельзя: он вечно всплывал бы в списке как
        // «готово», а «удалить» отчитывалось бы «освободилось 0 Б».
        val gone = ok || !exists(context, r)
        if (gone) forget(r.uri)
        return if (ok) r.bytes else 0L
    }

    /** Есть ли файл ещё на месте. */
    fun exists(context: Context, r: Rec): Boolean = runCatching {
        val u = android.net.Uri.parse(r.uri)
        if (u.scheme == "content") {
            context.contentResolver.openFileDescriptor(u, "r")?.use { true } ?: false
        } else {
            u.path?.let { java.io.File(it).exists() } ?: false
        }
    }.getOrDefault(false)

    /** Автоуборка. Выключена по умолчанию: сама удалять чужие файлы нельзя. */
    var autoDelete: Boolean
        get() = MmkvManager.decodeSettingsBool("yt_auto_delete_watched", false)
        set(v) { MmkvManager.encodeSettings("yt_auto_delete_watched", v) }

    const val AUTO_DAYS = 14
}
