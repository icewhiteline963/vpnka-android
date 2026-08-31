package com.v2ray.ang.ui

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

        /** Живая задача — чтобы начатую загрузку можно было прервать. */
        var job: Job? = null

        /** Из чего собиралась — чтобы «Повторить» не требовало искать заново. */
        var sourceUrl: String? = null
        var sourceTitle: String? = null
        var sourceQuality: String = ""
    }

    val entries = mutableStateListOf<Entry>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gate = Semaphore(2)
    private var nextId = 0L

    private fun add(label: String, mime: String): Entry {
        val e = Entry(nextId++, label, mime)
        entries.add(0, e)
        return e
    }

    fun enqueueVideo(context: Context, option: YouTubeService.DownloadOption, title: String) {
        val ctx = context.applicationContext
        val e = add("$title · ${option.label}", option.mime)
        e.job = scope.launch {
            val self = coroutineContext[Job]
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
                    .onSuccess { e.uri = it; e.state = State.DONE }
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

    /** Прервать начатую загрузку. Времянки чистит сам YouTubeService. */
    fun cancel(e: Entry) {
        if (e.state != State.RUNNING) return
        e.state = State.CANCELLED
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
    fun enqueueVideoByUrl(context: Context, url: String, title: String, quality: String = "") {
        val ctx = context.applicationContext
        val e = add(title, "video/mp4")
        e.sourceUrl = url; e.sourceTitle = title; e.sourceQuality = quality
        e.job = scope.launch {
            val self = coroutineContext[Job]
            gate.withPermit {
                e.state = State.RUNNING
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
                    YouTubeService.download(ctx, opt, title) { d, t, s -> e.done = d; e.total = t; e.speed = s }
                }
                    .onSuccess { e.uri = it; e.state = State.DONE }
                    .onFailure { e.error = it.message ?: it.javaClass.simpleName; e.state = State.FAILED }
            }
        }
    }

    fun enqueueSubtitle(context: Context, sub: YouTubeService.SubtitleOption, title: String) {
        val ctx = context.applicationContext
        val e = add("$title · субтитры (${sub.label})", "text/plain")
        e.job = scope.launch {
            gate.withPermit {
                runCatching { YouTubeService.downloadSubtitle(ctx, sub, title) }
                    .onSuccess { e.uri = it; e.state = State.DONE }
                    .onFailure { e.error = it.message ?: it.javaClass.simpleName; e.state = State.FAILED }
            }
        }
    }

    fun removeFromList(e: Entry) { entries.remove(e) }

    /** Removes the entry AND deletes the saved file. */
    fun deleteFile(context: Context, e: Entry) {
        e.uri?.let { u ->
            runCatching {
                if (u.scheme == "content") context.contentResolver.delete(u, null, null)
                else u.path?.let { java.io.File(it).delete() }
            }
        }
        entries.remove(e)
    }
}
