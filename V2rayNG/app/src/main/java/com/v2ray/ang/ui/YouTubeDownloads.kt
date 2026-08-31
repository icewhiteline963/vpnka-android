package com.v2ray.ang.ui

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.v2ray.ang.handler.DownloadRecords
import com.v2ray.ang.handler.YouTubeLater
import com.v2ray.ang.handler.YouTubeService
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * A tiny in-memory download manager for the YouTube app: tracks each save with
 * live progress + speed, keeps the resulting file Uri for open/share, and runs
 * at most two at a time (all through the VPN proxy, inside YouTubeService).
 * State is lost on process death — the files stay in the public Downloads folder.
 */
object YouTubeDownloads {
    enum class State { QUEUED, RUNNING, DONE, FAILED, CANCELLED }

    class Entry(val id: Long, label: String, val mime: String) {
        var label by mutableStateOf(label)
        var state by mutableStateOf(State.QUEUED)
        var done by mutableStateOf(0L)
        var total by mutableStateOf(0L)
        var speed by mutableStateOf(0L)
        var uri by mutableStateOf<Uri?>(null)
        var error by mutableStateOf<String?>(null)

        /** Почему стоим: «Ждёт Wi-Fi» / «Ждёт ночи». null — не ждём. */
        var waitReason by mutableStateOf<String?>(null)

        /** Человек нажал «Скачать сейчас» — правила очереди для этой строки сняты. */
        var bypass by mutableStateOf(false)

        /** «Видео» | «Файл» | «Субтитры» — для полок в списке загрузок. */
        var kind: String = "Видео"

        /** Живая задача — чтобы начатую загрузку можно было прервать. */
        var job: Job? = null

        /** Из чего собиралась — чтобы «Повторить» не требовало искать заново. */
        var sourceUrl: String? = null
        var sourceTitle: String? = null
        var sourceQuality: String = ""
    }

    val entries = mutableStateListOf<Entry>()

    /**
     * Поднять из журнала уже скачанное — список жил только в памяти, и после
     * перезапуска вкладка «Загрузки» была пустой, хотя файлы на месте.
     * Восстанавливаем как готовые: прогресс и скорость у них уже в прошлом.
     */
    fun restore() {
        if (restored) return
        restored = true
        DownloadRecords.all().sortedBy { it.savedAt }.forEach { r ->
            if (entries.any { it.uri?.toString() == r.uri }) return@forEach
            val e = Entry(nextId++, r.name, "application/octet-stream")
            e.state = State.DONE
            e.uri = android.net.Uri.parse(r.uri)
            e.total = r.bytes
            e.done = r.bytes
            e.sourceUrl = r.sourceUrl
            e.sourceTitle = r.name
            entries.add(e)
        }
    }

    private var restored = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gate = Semaphore(2)
    private var nextId = 0L

    /**
     * Ворота очереди: «только по Wi-Fi» и «только ночью».
     *
     * Галка «только по Wi-Fi» существовала и раньше — но НИЧЕГО не делала:
     * значение записывалось и нигде не читалось, а подпись обещала, что
     * очередь дождётся дома. Теперь обещание выполняется.
     *
     * Из ожидания всегда есть выход: у строки появляется «Скачать сейчас».
     * Правило, которое нельзя обойти, в нужный момент превращается в ловушку.
     */
    private suspend fun awaitWindow(ctx: Context, e: Entry) {
        while (!e.bypass) {
            val reason = blockReason(ctx) ?: break
            e.waitReason = reason
            e.state = State.QUEUED
            // Спим короткими шагами: «Сейчас» должно срабатывать сразу, а не
            // ждать конца получасовой дрёмы.
            repeat(15) {
                if (e.bypass) return@repeat
                kotlinx.coroutines.delay(2_000)
            }
        }
        e.waitReason = null
    }

    private fun blockReason(ctx: Context): String? {
        if (YouTubeLater.wifiOnly && !onWifi(ctx)) return "Ждёт Wi-Fi"
        if (YouTubeLater.nightOnly && !isNight()) {
            return "Ждёт ночи (%02d:00–%02d:00)".format(YouTubeLater.NIGHT_FROM, YouTubeLater.NIGHT_TO)
        }
        return null
    }

    private fun isNight(): Boolean {
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val from = YouTubeLater.NIGHT_FROM
        val to = YouTubeLater.NIGHT_TO
        return if (from <= to) h in from until to else h >= from || h < to
    }

    /**
     * Wi-Fi ли сейчас. Тонкость нашего случая: при включённом ВПН активная
     * сеть — это наш же tun, и спрашивать её о Wi-Fi бессмысленно. Поэтому
     * при VPN-транспорте смотрим на сети ПОД ним.
     */
    private fun onWifi(ctx: Context): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            ?: return false
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        if (!caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)) {
            return caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
        }
        // Требуем ПРОВЕРЕННУЮ сеть: подключённый Wi-Fi без интернета system
        // оставляет в списке, трафик при этом идёт по сотовой — и очередь
        // качала бы гигабайты за деньги, считая, что мы дома.
        return cm.allNetworks.any { n ->
            val c = cm.getNetworkCapabilities(n) ?: return@any false
            !c.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) &&
                c.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) &&
                c.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
    }

    /** Снять правила очереди для одной строки и качать немедленно. */
    fun forceNow(e: Entry) { e.bypass = true; e.waitReason = null }

    private fun add(label: String, mime: String): Entry {
        val e = Entry(nextId++, label, mime)
        entries.add(0, e)
        return e
    }

    fun enqueueVideo(
        context: Context,
        option: YouTubeService.DownloadOption,
        title: String,
        /** Страница ролика — без неё запись не знает, что смотрели, и уборка
         *  просмотренного её не видит, а «Повторить» не работает. */
        pageUrl: String? = null,
    ) {
        val ctx = context.applicationContext
        val e = add("$title · ${option.label}", option.mime)
        e.sourceUrl = pageUrl; e.sourceTitle = title
        e.job = scope.launch {
            val self = coroutineContext[Job]
            awaitWindow(ctx, e)
            gate.withPermit {
                // До этой точки задача ЖДАЛА очереди. Раньше она всё это
                // время рисовала бегущую полосу: после «Скачать всё» на
                // тридцати роликах человек видел тридцать одинаковых полос,
                // из которых работали две.
                e.state = State.RUNNING
                runCatching {
                    YouTubeService.download(
                        ctx, option, title,
                        isCancelled = { self?.isActive != true },
                    ) { d, t, s -> e.done = d; e.total = t; e.speed = s }
                }
                    .onSuccess {
                        e.uri = it; e.state = State.DONE
                        // Помним, что скачали: список в памяти умрёт вместе с
                        // процессом, а файл останется.
                        DownloadRecords.add(it.toString(), e.label, e.sourceUrl, e.total)
                    }
                    .onFailure { e.state = failureState(it, e) }
            }
        }
    }

    /**
     * Отмена — не ошибка.
     *
     * Показывать её красным «не удалось» значит врать человеку о том, что
     * произошло: он сам нажал «отменить».
     */
    private fun failureState(t: Throwable, e: Entry): State =
        if (t is YouTubeService.Cancelled || t is kotlinx.coroutines.CancellationException) {
            State.CANCELLED
        } else {
            e.error = t.message ?: t.javaClass.simpleName
            State.FAILED
        }

    /**
     * Повторить упавшую загрузку.
     *
     * Без этого перекачать сорвавшийся файл значило найти видео заново и
     * заново выбрать качество — при том что всё нужное у нас уже записано.
     */
    fun retry(context: Context, e: Entry) {
        if (e.state != State.FAILED && e.state != State.CANCELLED) return
        val src = e.sourceUrl ?: return
        entries.remove(e)
        enqueueVideoByUrl(context, src, e.sourceTitle ?: e.label, e.sourceQuality)
    }

    /**
     * Прервать загрузку — идущую или ещё ждущую.
     *
     * Раньше отмена работала только для RUNNING, а кнопка «Отменить» стоит и
     * у ждущей строки: нажатие не делало ничего, задача продолжала ждать
     * ворота и всё равно качала.
     */
    fun cancel(e: Entry) {
        if (e.state != State.RUNNING && e.state != State.QUEUED) return
        e.state = State.CANCELLED
        e.waitReason = null
        e.job?.cancel()
        e.job = null
    }

    /** Resolves the best quality for a bare video URL, then downloads it. */
    /**
     * @param quality «480p» / «720p» / «1080p» / «♪» / пусто — максимум.
     *
     * Раньше всегда бралось максимальное: «Скачать всё» на плейлисте из
     * тридцати роликов означало десятки гигабайт через наши ноды по одному
     * тапу. Теперь качество выбирает человек, у каждой строки своё.
     */
    fun enqueueVideoByUrl(
        context: Context,
        url: String,
        title: String,
        quality: String = "",
        /**
         * Убрать из очереди «позже» — но ТОЛЬКО когда загрузка реально
         * началась. Раньше строку вычёркивали сразу при постановке: список
         * «позже» лежит на диске, а очередь загрузок — в памяти, и если
         * процесс умирал во время ожидания Wi-Fi или ночи (а ждать там
         * положено часами), ролик оказывался и не скачан, и вычеркнут.
         */
        clearLater: String? = null,
    ) {
        val ctx = context.applicationContext
        val e = add(title, "video/mp4")
        e.sourceUrl = url; e.sourceTitle = title; e.sourceQuality = quality
        e.job = scope.launch {
            val self = coroutineContext[Job]
            awaitWindow(ctx, e)
            gate.withPermit {
                e.state = State.RUNNING
                clearLater?.let { YouTubeLater.remove(it) }
                val opt = runCatching {
                    if (quality == "♪") {
                        YouTubeService.audioDownload(url)
                    } else {
                        val all = YouTubeService.videoStreams(url)
                        if (quality.isBlank()) all.firstOrNull()
                        else all.firstOrNull { it.label.contains(quality, ignoreCase = true) }
                            ?: all.firstOrNull()
                    }
                }.getOrNull()
                if (opt == null) { e.error = "Нет форматов"; e.state = State.FAILED; return@withPermit }
                e.label = "$title · ${opt.label}"
                runCatching {
                    // Без isCancelled отменённая загрузка дочитывалась до
                    // конца (трафик через наши ноды!) и перетирала
                    // «Отменено» на «Готово».
                    YouTubeService.download(
                        ctx, opt, title,
                        isCancelled = { self?.isActive != true },
                    ) { d, t, s -> e.done = d; e.total = t; e.speed = s }
                }
                    .onSuccess {
                        e.uri = it; e.state = State.DONE
                        // Помним, что скачали: список в памяти умрёт вместе с
                        // процессом, а файл останется.
                        DownloadRecords.add(it.toString(), e.label, e.sourceUrl, e.total)
                    }
                    .onFailure { e.state = failureState(it, e) }
            }
        }
    }

    /**
     * Файл со страницы браузера — тем же путём, что и видео.
     *
     * Системный менеджер загрузок Android пошёл бы в сеть НАПРЯМУЮ, мимо
     * туннеля: для ВПН-приложения это утечка и адреса, и содержимого.
     */
    fun enqueueFile(context: Context, url: String, name: String) {
        val ctx = context.applicationContext
        val e = add(name, "application/octet-stream")
        e.kind = "Файл"
        e.sourceUrl = url; e.sourceTitle = name
        e.job = scope.launch {
            val self = coroutineContext[Job]
            awaitWindow(ctx, e)
            gate.withPermit {
                e.state = State.RUNNING
                runCatching {
                    YouTubeService.downloadFile(
                        ctx, url, name,
                        isCancelled = { self?.isActive != true },
                    ) { d, t, s -> e.done = d; e.total = t; e.speed = s }
                }
                    .onSuccess {
                        e.uri = it; e.state = State.DONE
                        // Помним, что скачали: список в памяти умрёт вместе с
                        // процессом, а файл останется.
                        DownloadRecords.add(it.toString(), e.label, e.sourceUrl, e.total)
                    }
                    .onFailure { e.state = failureState(it, e) }
            }
        }
    }

    fun enqueueSubtitle(context: Context, sub: YouTubeService.SubtitleOption, title: String) {
        val ctx = context.applicationContext
        val e = add("$title · субтитры (${sub.label})", "text/plain")
        e.kind = "Субтитры"
        e.job = scope.launch {
            gate.withPermit {
                // Субтитры — килобайты, ворота очереди им ни к чему, но
                // состояние показывать надо: строка висела «В очереди» до
                // самого конца и не отменялась.
                e.state = State.RUNNING
                runCatching { YouTubeService.downloadSubtitle(ctx, sub, title) }
                    .onSuccess {
                        e.uri = it; e.state = State.DONE
                        // Помним, что скачали: список в памяти умрёт вместе с
                        // процессом, а файл останется.
                        DownloadRecords.add(it.toString(), e.label, e.sourceUrl, e.total)
                    }
                    .onFailure { e.error = it.message ?: it.javaClass.simpleName; e.state = State.FAILED }
            }
        }
    }

    fun removeFromList(e: Entry) { entries.remove(e) }

    /** Removes the entry AND deletes the saved file. */
    fun deleteFile(context: Context, e: Entry) {
        e.uri?.let { u -> DownloadRecords.forget(u.toString()) }
        e.uri?.let { u ->
            runCatching {
                if (u.scheme == "content") context.contentResolver.delete(u, null, null)
                else u.path?.let { java.io.File(it).delete() }
            }
        }
        entries.remove(e)
    }
}
