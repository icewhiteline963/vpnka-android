package com.v2ray.ang.handler

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Кольцевой буфер последних событий приложения — «хлебные крошки».
 *
 * Прикладывается к отчёту о сбое/зависании ([CrashLog]). Для «иногда
 * зависает» цепочка действий перед этим часто важнее самого стека: по ней
 * видно, что человек делал за секунды до того, как всё встало.
 *
 * Только в памяти, потолок фиксирован, потокобезопасно и НИКОГДА не бросает.
 * Секреты сюда класть нельзя — крошки уезжают на сервер вместе с отчётом;
 * кладём только события («connect:tap», «import:start»), не данные.
 */
object Breadcrumbs {
    private const val MAX = 50
    private val ring = ArrayDeque<String>(MAX)
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    /** Записать событие. Обрезаем и не бросаем — крошки не должны сами ничего ломать. */
    fun add(msg: String) {
        val line = runCatching { "${fmt.format(Date())} ${msg.take(120)}" }
            .getOrDefault(msg.take(120))
        synchronized(ring) {
            if (ring.size >= MAX) ring.removeFirst()
            ring.addLast(line)
        }
    }

    /** Все крошки одной строкой (по одной на линию), от старых к новым. */
    fun snapshot(): String = synchronized(ring) { ring.joinToString("\n") }
}
