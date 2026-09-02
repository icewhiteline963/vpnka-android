package com.v2ray.ang.handler

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.nio.ByteBuffer
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.downloader.Request as NpRequest
import org.schabi.newpipe.extractor.downloader.Response as NpResponse
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * YouTube via NewPipeExtractor — no Google account, no ads. Every request
 * (metadata AND video stream) is forced through the local VPN proxy
 * (127.0.0.1:httpPort): the SmartDesk apps are excluded from the TUN, so
 * without this proxy they'd egress directly, breaking the «only through VPN»
 * rule. VPN off → the proxy port isn't listening → it simply fails.
 */
object YouTubeService {
    data class Video(
        val url: String,
        val title: String,
        val uploader: String,
        val durationSec: Long,
        val thumb: String?,
    )

    /** streamUrl is video (muxed or video-only); audioUrl != null means the
     *  video track has no sound and must be merged with this separate audio. */
    data class Playback(val title: String, val streamUrl: String, val pageUrl: String, val audioUrl: String?)

    /** A downloadable quality. audioUrl == null → muxed single file (save as-is);
     *  otherwise videoUrl is video-only mp4 to be remuxed with audioUrl. */
    data class DownloadOption(
        val label: String,
        val videoUrl: String,
        val audioUrl: String?,
        val mime: String,
        val ext: String,
    )

    private fun resRank(res: String?): Int =
        res?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0

    @Volatile private var inited = false

    /** OkHttp client bound to the local VPN proxy. NewPipe captures this once at
     *  init, so the extractor's proxy port is fixed for the process (the HTTP
     *  port is a stable setting). The player builds its own proxied client per
     *  playback and always reads the current port. */
    // Клиент переиспользуем, а не создаём заново на каждый запрос.
    //
    // Новый OkHttpClient — это свой пул соединений и свои потоки: на ленте
    // обложек их набегали десятки, ни одно соединение не переиспользовалось,
    // и каждая картинка открывала отдельный CONNECT через туннель. Порт
    // прокси может смениться в настройках, поэтому клиент пересобирается,
    // когда порт изменился.
    // Порт и клиент — ОДНОЙ ссылкой.
    //
    // Двумя отдельными полями их могли увидеть вразнобой: новый порт со
    // старым клиентом. Запрос ушёл бы на несуществующий локальный порт и
    // упал — а для обложки это ещё и пятиминутная метка «не грузится».
    @Volatile private var shared: Pair<Int, OkHttpClient>? = null

    fun proxiedClient(): OkHttpClient {
        val port = SettingsManager.getHttpPort()
        shared?.let { (p, c) -> if (p == port) return c }
        return synchronized(this) {
            shared?.let { (p, c) -> if (p == port) return@synchronized c }
            val c = OkHttpClient.Builder()
                .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", port)))
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
            shared = port to c
            c
        }
    }

    fun ensureInit() {
        if (inited) return
        synchronized(this) {
            if (inited) return
            NewPipe.init(VpnkaDownloader(proxiedClient()))
            inited = true
        }
    }

    /**
     * Главная страница — то, что YouTube показывает без входа в аккаунт.
     *
     * Приложение открывалось ПОСЛЕДНИМ запросом: человек заходил «посмотреть,
     * что нового», а получал вчерашний поиск, который уже закрыл. Личной
     * ленты у нас быть не может — аккаунта нет, — поэтому берём то же, что
     * YouTube показывает гостю: подборку «В тренде» его сервиса.
     *
     * Blocking — вызывать вне главного потока.
     */
    fun trending(): List<Video> {
        ensureInit()
        val yt = ServiceList.YouTube
        val kiosks = yt.kioskList
        val id = kiosks.defaultKioskId
        val url = kiosks.getListLinkHandlerFactoryByType(id).fromId(id).url
        val info = KioskInfo.getInfo(yt, url)
        return info.relatedItems.filterIsInstance<StreamInfoItem>().map {
            Video(
                url = it.url,
                title = it.name,
                uploader = it.uploaderName ?: "",
                durationSec = it.duration,
                thumb = it.thumbnails.firstOrNull()?.url,
            )
        }
    }

    /** Blocking — call off the main thread. */
    fun search(query: String): List<Video> {
        ensureInit()
        val yt = ServiceList.YouTube
        val handler = yt.searchQHFactory.fromQuery(query)
        val info = SearchInfo.getInfo(yt, handler)
        return info.relatedItems.filterIsInstance<StreamInfoItem>().map {
            Video(
                url = it.url,
                title = it.name,
                uploader = it.uploaderName ?: "",
                durationSec = it.duration,
                thumb = it.thumbnails.firstOrNull()?.url,
            )
        }
    }

    /** Blocking — off main. Prefers adaptive (best video-only + best audio) for
     *  real quality; falls back to a muxed stream. */
    // Playback resolution ceiling. Over the VPN tunnel to a foreign node a
    // 1080p+/VP9/AV1 adaptive stream buffers forever (YouTube also throttles
    // high-bitrate adaptive streams hardest), and non-H.264 codecs aren't HW-
    // decoded on every device → "video won't play / loads forever". 720p H.264
    // plays smoothly everywhere; the download path still offers full quality.
    private const val PLAY_MAX_RES = 720

    fun resolve(videoUrl: String): Playback {
        ensureInit()
        val si = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)
        val videoOnly = si.videoOnlyStreams.filter { it.content.isNotBlank() }
        // Prefer the highest H.264/mp4 track at/below the cap; then any track at
        // /below the cap; then the smallest available (all were above the cap).
        val bestVideoOnly =
            videoOnly.filter {
                resRank(it.resolution) in 1..PLAY_MAX_RES && isMp4(it.format?.suffix, it.format?.mimeType)
            }.maxByOrNull { resRank(it.resolution) }
                ?: videoOnly.filter { resRank(it.resolution) in 1..PLAY_MAX_RES }
                    .maxByOrNull { resRank(it.resolution) }
                ?: videoOnly.minByOrNull { resRank(it.resolution) }
        // Pair with mp4/m4a audio when possible (keeps the container consistent
        // for Media3); otherwise the loudest track.
        val bestAudio = si.audioStreams
            .filter { it.content.isNotBlank() && isMp4(it.format?.suffix, it.format?.mimeType) }
            .maxByOrNull { it.averageBitrate }
            ?: si.audioStreams.filter { it.content.isNotBlank() }.maxByOrNull { it.averageBitrate }
        if (bestVideoOnly != null && bestAudio != null) {
            return Playback(si.name, bestVideoOnly.content, videoUrl, bestAudio.content)
        }
        // Muxed fallback: prefer H.264 at/below the cap (muxed tops out ~720p).
        val muxedStreams = si.videoStreams.filter { !it.isVideoOnly && it.content.isNotBlank() }
        val muxed = muxedStreams.filter { resRank(it.resolution) in 1..PLAY_MAX_RES }
            .maxByOrNull { resRank(it.resolution) }
            ?: muxedStreams.minByOrNull { resRank(it.resolution) }
            ?: si.videoStreams.firstOrNull()
            ?: throw IllegalStateException("no playable stream")
        return Playback(si.name, muxed.content, videoUrl, null)
    }

    /** Download qualities, best-first. High res comes from mp4 video-only tracks
     *  (remuxed with audio on save); muxed streams are the low fallbacks. */
    fun videoStreams(videoUrl: String): List<DownloadOption> {
        ensureInit()
        val si = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)
        val bestAudio = si.audioStreams
            .filter { it.content.isNotBlank() && isMp4(it.format?.suffix, it.format?.mimeType) }
            .maxByOrNull { it.averageBitrate }
            ?: si.audioStreams.filter { it.content.isNotBlank() }.maxByOrNull { it.averageBitrate }

        val adaptive = if (bestAudio != null) {
            si.videoOnlyStreams
                .filter { it.content.isNotBlank() && isMp4(it.format?.suffix, it.format?.mimeType) }
                .map {
                    DownloadOption(
                        label = it.resolution ?: "видео",
                        videoUrl = it.content,
                        audioUrl = bestAudio.content,
                        mime = "video/mp4",
                        ext = "mp4",
                    )
                }
        } else emptyList()

        val muxed = si.videoStreams
            .filter { !it.isVideoOnly && it.content.isNotBlank() }
            .map {
                DownloadOption(
                    label = it.resolution ?: "видео",
                    videoUrl = it.content,
                    audioUrl = null,
                    mime = it.format?.mimeType ?: "video/mp4",
                    ext = it.format?.suffix ?: "mp4",
                )
            }

        return (adaptive + muxed)
            .distinctBy { it.label }
            .sortedByDescending { resRank(it.label) }
    }

    private fun isMp4(suffix: String?, mime: String?): Boolean =
        suffix == "mp4" || suffix == "m4a" || mime?.contains("mp4") == true

    data class SubtitleOption(val label: String, val url: String, val ext: String)

    /** Subtitle tracks available for the video. Off main. */
    fun subtitles(videoUrl: String): List<SubtitleOption> {
        ensureInit()
        val si = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)
        return si.subtitles
            .filter { it.content.isNotBlank() }
            .map {
                val name = it.displayLanguageName ?: it.languageTag ?: "sub"
                SubtitleOption(
                    label = if (it.isAutoGenerated) "$name (авто)" else name,
                    url = it.content,
                    ext = it.format?.suffix ?: "vtt",
                )
            }
    }

    // ---- Главы и транскрипт -----------------------------------------

    /** Глава ролика: с какой секунды и как называется. */
    data class Chapter(val startSec: Long, val title: String)

    /** Строка транскрипта: таймкод и текст. */
    data class Cue(val atSec: Long, val text: String)

    /**
     * Главы, если автор их разметил. Пусто — значит их нет, это норма.
     */
    fun chapters(videoUrl: String): List<Chapter> {
        ensureInit()
        val si = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)
        return runCatching {
            si.streamSegments.map { Chapter(it.startTimeSeconds.toLong(), it.title ?: "") }
        }.getOrDefault(emptyList())
    }

    /**
     * Транскрипт — это субтитры, разобранные на строки с таймкодами.
     *
     * Отдельного API транскриптов у нас нет и быть не может, но субтитры мы
     * и так умеем качать. Берём первую дорожку (или предпочтённый язык),
     * тянем её через прокси и разбираем.
     */
    fun transcript(videoUrl: String, preferLang: String? = null): List<Cue> {
        val subs = subtitles(videoUrl)
        if (subs.isEmpty()) return emptyList()
        val pick = preferLang
            ?.let { lang -> subs.firstOrNull { it.label.contains(lang, ignoreCase = true) } }
            ?: subs.firstOrNull { it.label.contains("рус", true) || it.label.contains("rus", true) }
            ?: subs.first()
        val body = runCatching {
            val req = okhttp3.Request.Builder()
                .url(pick.url)
                .header("User-Agent", USER_AGENT_DESKTOP)
                .build()
            proxiedClient().newCall(req).execute().use { r ->
                if (!r.isSuccessful) null else r.body?.string()
            }
        }.getOrNull() ?: return emptyList()
        return parseCues(body)
    }

    /**
     * Разбор WebVTT и TTML — двух форматов, которые отдаёт YouTube.
     *
     * Намеренно снисходительный: чужой формат меняется без предупреждения, и
     * пустой транскрипт лучше исключения посреди экрана.
     */
    fun parseCues(raw: String): List<Cue> {
        val out = mutableListOf<Cue>()

        // TTML: <p begin="12.5s" ...>текст</p>
        if (raw.contains("<tt", ignoreCase = true) || raw.contains("<p ", ignoreCase = true)) {
            val rx = Regex("""<p[^>]*begin="([^"]+)"[^>]*>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
            for (m in rx.findAll(raw)) {
                val t = parseTime(m.groupValues[1])
                val text = m.groupValues[2]
                    .replace(Regex("<[^>]+>"), " ")
                    .replace("&amp;", "&").replace("&quot;", "\"")
                    .replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
                    .replace(Regex("\\s+"), " ").trim()
                if (t >= 0 && text.isNotEmpty()) out.add(Cue(t, text))
            }
            if (out.isNotEmpty()) return out
        }

        // WebVTT: 00:00:12.500 --> 00:00:15.000
        val lines = raw.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.contains("-->")) {
                val start = line.substringBefore("-->").trim()
                val t = parseTime(start)
                val text = buildString {
                    var j = i + 1
                    while (j < lines.size && lines[j].isNotBlank()) {
                        if (isNotEmpty()) append(' ')
                        append(lines[j].replace(Regex("<[^>]+>"), "").trim())
                        j++
                    }
                    i = j
                }.trim()
                if (t >= 0 && text.isNotEmpty()) out.add(Cue(t, text))
            }
            i++
        }
        return out
    }

    /** «00:01:12.500», «12.5s», «72» → секунды. -1, если не разобрали. */
    fun parseTime(v: String): Long {
        val s = v.trim().removeSuffix("s")
        if (s.contains(":")) {
            val parts = s.split(":").map { it.replace(",", ".") }
            val nums = parts.mapNotNull { it.toDoubleOrNull() }
            if (nums.size != parts.size) return -1
            var sec = 0.0
            for (n in nums) sec = sec * 60 + n
            return sec.toLong()
        }
        return s.replace(",", ".").toDoubleOrNull()?.toLong() ?: -1
    }

    /**
     * Скачать произвольный файл со страницы — через прокси, как и всё
     * остальное. Возвращает адрес в системных «Загрузках».
     */
    fun downloadFile(
        context: Context,
        url: String,
        fileName: String,
        isCancelled: () -> Boolean = { false },
        onProgress: (done: Long, total: Long, speedBps: Long) -> Unit = { _, _, _ -> },
    ): android.net.Uri {
        val safe = fileName.replace(Regex("[^\\p{L}\\p{N} ._-]"), "_").trim().take(120)
            .ifBlank { "file" }
        val client = proxiedClient().newBuilder()
            .readTimeout(0, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .build()
        val tmp = File(context.cacheDir, "yt_f_${System.currentTimeMillis()}")
        try {
            downloadTo(client, url, tmp, isCancelled, onProgress)
            return saveFileToDownloads(context, safe, "application/octet-stream", tmp, "VPNka/Файлы")
        } finally {
            tmp.delete()
        }
    }

    /** Saves a subtitle track (text) to Downloads THROUGH the VPN proxy. Off main. */
    fun downloadSubtitle(context: Context, sub: SubtitleOption, title: String): android.net.Uri {
        ensureInit()
        val safe = title.replace(Regex("[^\\p{L}\\p{N} ._-]"), "_").trim().take(80).ifBlank { "video" }
        val fileName = "$safe.${sub.ext}"
        val tmp = File(context.cacheDir, "yt_sub_${System.currentTimeMillis()}.${sub.ext}")
        try {
            downloadTo(proxiedClient(), sub.url, tmp)
            return saveFileToDownloads(context, fileName, "text/plain", tmp, "VPNka/Субтитры")
        } finally {
            tmp.delete()
        }
    }

    /** Best audio-only track as a single-file download (native m4a/AAC — no
     *  transcode, so not literally .mp3). Null if none. Off main. */
    fun audioDownload(videoUrl: String): DownloadOption? {
        ensureInit()
        val si = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)
        val a = si.audioStreams.filter { it.content.isNotBlank() }.maxByOrNull { it.averageBitrate } ?: return null
        return DownloadOption(
            label = "Аудио",
            videoUrl = a.content,
            audioUrl = null,
            mime = a.format?.mimeType ?: "audio/mp4",
            ext = a.format?.suffix ?: "m4a",
        )
    }

    /** Saves the chosen quality to public Downloads THROUGH the VPN proxy. For
     *  adaptive options the video-only + audio are downloaded to cache and
     *  remuxed (no re-encode) into one mp4. Blocking — off main. */
    /**
     * Загрузку отменил человек — это не ошибка, и показывать её как ошибку
     * нельзя. Отдельный тип, чтобы вызывающий отличил одно от другого.
     */
    class Cancelled : IOException("отменено")

    /**
     * Сколько места свободно там, где мы работаем.
     *
     * Меряем ИМЕННО внутреннюю память: в ней живут времянки и склейка, а их
     * втрое больше самого файла. Копия в «Загрузках» — отдельный том, и
     * ответ про него был бы не о том месте, где заканчивается диск.
     */
    fun freeWorkBytes(context: Context): Long = runCatching {
        android.os.StatFs(context.cacheDir.absolutePath).availableBytes
    }.getOrDefault(Long.MAX_VALUE)

    /** Ниже этого порога за загрузку лучше не браться. */
    const val MIN_FREE_BYTES = 500L * 1024 * 1024

    fun download(
        context: Context,
        option: DownloadOption,
        title: String,
        isCancelled: () -> Boolean = { false },
        onProgress: (done: Long, total: Long, speedBps: Long) -> Unit = { _, _, _ -> },
    ): android.net.Uri {
        ensureInit()
        val safe = title.replace(Regex("[^\\p{L}\\p{N} ._-]"), "_").trim().take(80).ifBlank { "video" }
        val fileName = "$safe.${option.ext}"
        val client = proxiedClient().newBuilder()
            .readTimeout(0, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .build()
        val ts = System.currentTimeMillis()
        val tmpV = File(context.cacheDir, "yt_v_$ts.mp4")
        try {
            downloadTo(client, option.videoUrl, tmpV, isCancelled, onProgress)
            return if (option.audioUrl == null) {
                saveFileToDownloads(context, fileName, option.mime, tmpV, "VPNka/Видео")
            } else {
                val tmpA = File(context.cacheDir, "yt_a_$ts.m4a")
                val tmpOut = File(context.cacheDir, "yt_out_$ts.mp4")
                try {
                    downloadTo(client, option.audioUrl, tmpA, isCancelled, onProgress)
                    remux(tmpV, tmpA, tmpOut)
                    saveFileToDownloads(context, fileName, "video/mp4", tmpOut, "VPNka/Видео")
                } finally {
                    tmpA.delete(); tmpOut.delete()
                }
            }
        } finally {
            tmpV.delete()
        }
    }

    private fun downloadTo(
        client: OkHttpClient,
        url: String,
        dest: File,
        isCancelled: () -> Boolean = { false },
        onProgress: (done: Long, total: Long, speedBps: Long) -> Unit = { _, _, _ -> },
    ) {
        val req = okhttp3.Request.Builder().url(url).header("User-Agent", USER_AGENT_DESKTOP).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("empty body")
            val total = body.contentLength()
            dest.outputStream().use { out ->
                val input = body.byteStream()
                val buf = ByteArray(64 * 1024)
                var done = 0L
                var windowStart = System.currentTimeMillis()
                var windowBytes = 0L
                var speed = 0L
                while (true) {
                    // Проверяем отмену на КАЖДОМ куске. Чтение блокирующее, и
                    // само по себе оно на отмену корутины не отзовётся: без
                    // этой строки начатую загрузку остановить нечем — она
                    // качает до конца, даже если человек передумал.
                    if (isCancelled()) throw Cancelled()
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n; windowBytes += n
                    val now = System.currentTimeMillis()
                    if (now - windowStart >= 400) {
                        speed = windowBytes * 1000 / (now - windowStart)
                        windowStart = now; windowBytes = 0
                        onProgress(done, total, speed)
                    }
                }
                onProgress(done, if (total > 0) total else done, speed)
            }
        }
    }

    /**
     * @param folder подпапка внутри «Загрузок»: `VPNka/Видео`, `VPNka/Файлы`,
     *   `VPNka/Субтитры`. Раньше всё валилось в общую кучу вперемешку с
     *   загрузками других приложений — найти вчерашний ролик было нечем.
     *   Папку выбирает не человек, а вид файла: выбор на каждую загрузку —
     *   лишний вопрос там, где ответ всегда один.
     */
    /**
     * Подмести времянки, оставшиеся от прерванных загрузок.
     *
     * `finally { tmp.delete() }` не выполняется, если процесс убили посреди
     * скачивания, а времянка — это полный размер ролика (для раздельных
     * дорожек вдвое больше). Зовём при открытии приложения.
     */
    fun sweepTemp(context: Context) {
        runCatching {
            val edge = System.currentTimeMillis() - 6 * 60 * 60 * 1000L
            context.cacheDir.listFiles()?.forEach { f ->
                val n = f.name
                if ((n.startsWith("yt_v_") || n.startsWith("yt_a_") ||
                        n.startsWith("yt_out_") || n.startsWith("yt_f_") ||
                        n.startsWith("yt_sub_")) && f.lastModified() < edge
                ) {
                    f.delete()
                }
            }
        }
    }

    private fun saveFileToDownloads(
        context: Context,
        fileName: String,
        mime: String,
        file: File,
        folder: String = "VPNka",
    ): android.net.Uri {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$folder")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("insert failed")
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: throw IOException("openOutputStream null")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                folder,
            )
            if (!dir.exists()) dir.mkdirs()
            val out = File(dir, fileName)
            out.outputStream().use { o -> file.inputStream().use { it.copyTo(o) } }
            return android.net.Uri.fromFile(out)
        }
    }

    /** Container-level mux of an mp4 (H.264) video track + an m4a (AAC) audio
     *  track into one mp4 — no decode/encode, so it's fast. */
    private fun remux(videoFile: File, audioFile: File, outFile: File) {
        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val vEx = MediaExtractor().also { it.setDataSource(videoFile.absolutePath) }
        val aEx = MediaExtractor().also { it.setDataSource(audioFile.absolutePath) }
        try {
            val vTrack = firstTrack(vEx, "video/")
            val aTrack = firstTrack(aEx, "audio/")
            vEx.selectTrack(vTrack); aEx.selectTrack(aTrack)
            val outV = muxer.addTrack(vEx.getTrackFormat(vTrack))
            val outA = muxer.addTrack(aEx.getTrackFormat(aTrack))
            muxer.start()
            val buf = ByteBuffer.allocate(4 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            copyTrack(vEx, muxer, outV, buf, info)
            copyTrack(aEx, muxer, outA, buf, info)
            muxer.stop()
        } finally {
            runCatching { muxer.release() }
            vEx.release(); aEx.release()
        }
    }

    private fun firstTrack(ex: MediaExtractor, prefix: String): Int {
        for (i in 0 until ex.trackCount) {
            val mime = ex.getTrackFormat(i).getString(android.media.MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith(prefix)) return i
        }
        throw IOException("no $prefix track")
    }

    private fun copyTrack(
        ex: MediaExtractor,
        muxer: MediaMuxer,
        outIndex: Int,
        buf: ByteBuffer,
        info: MediaCodec.BufferInfo,
    ) {
        while (true) {
            val size = ex.readSampleData(buf, 0)
            if (size < 0) break
            info.offset = 0
            info.size = size
            info.presentationTimeUs = ex.sampleTime
            info.flags = if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)
                MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
            muxer.writeSampleData(outIndex, buf, info)
            ex.advance()
        }
    }

    const val USER_AGENT_DESKTOP =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
}

/** NewPipeExtractor Downloader backed by our proxied OkHttp client. */
class VpnkaDownloader(private val client: OkHttpClient) : Downloader() {
    override fun execute(request: NpRequest): NpResponse {
        val rb = okhttp3.Request.Builder().url(request.url())
        request.headers().forEach { (k, values) -> values.forEach { rb.addHeader(k, it) } }
        val data = request.dataToSend()
        val body = data?.toRequestBody()
        when (request.httpMethod()) {
            "POST" -> rb.post(body ?: ByteArray(0).toRequestBody())
            "HEAD" -> rb.head()
            "GET" -> rb.get()
            else -> rb.method(request.httpMethod(), body)
        }
        // NewPipeExtractor scrapes YouTube's DESKTOP HTML (its ytInitialData
        // regex is desktop-shaped). A mobile UA makes YouTube return the m.
        // layout → "Could not get ytInitialData". Match NewPipe's reference
        // Downloader and default to a desktop Firefox UA when the extractor
        // didn't set one itself.
        if (request.headers().keys.none { it.equals("User-Agent", ignoreCase = true) }) {
            rb.header("User-Agent", USER_AGENT)
        }
        client.newCall(rb.build()).execute().use { resp ->
            val respBody = resp.body?.string() ?: ""
            return NpResponse(resp.code, resp.message, resp.headers.toMultimap(), respBody, resp.request.url.toString())
        }
    }

    companion object {
        // Desktop UA — matches NewPipe's reference DownloaderImpl.
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
    }
}
