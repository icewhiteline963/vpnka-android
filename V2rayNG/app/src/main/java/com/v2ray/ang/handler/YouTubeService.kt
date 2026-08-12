package com.v2ray.ang.handler

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
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

    data class Playback(val title: String, val streamUrl: String, val pageUrl: String)

    /** A muxed (audio+video) progressive stream the user can save as one file. */
    data class DownloadOption(val label: String, val url: String, val mime: String, val ext: String)

    @Volatile private var inited = false

    /** OkHttp client bound to the local VPN proxy. NewPipe captures this once at
     *  init, so the extractor's proxy port is fixed for the process (the HTTP
     *  port is a stable setting). The player builds its own proxied client per
     *  playback and always reads the current port. */
    fun proxiedClient(): OkHttpClient {
        val port = SettingsManager.getHttpPort()
        return OkHttpClient.Builder()
            .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", port)))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun ensureInit() {
        if (inited) return
        synchronized(this) {
            if (inited) return
            NewPipe.init(VpnkaDownloader(proxiedClient()))
            inited = true
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

    /** Blocking — call off the main thread. Picks a muxed (audio+video)
     *  progressive stream so a single URL plays without merging tracks. */
    fun resolve(videoUrl: String): Playback {
        ensureInit()
        val si = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)
        val stream = si.videoStreams.firstOrNull { !it.isVideoOnly }
            ?: si.videoStreams.firstOrNull()
            ?: throw IllegalStateException("no playable stream")
        return Playback(si.name, stream.content, videoUrl)
    }

    /** Muxed streams offered for download, best-first. Blocking — off main. */
    fun videoStreams(videoUrl: String): List<DownloadOption> {
        ensureInit()
        val si = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)
        return si.videoStreams
            .filter { !it.isVideoOnly && it.content.isNotBlank() }
            .map {
                DownloadOption(
                    label = it.resolution ?: "видео",
                    url = it.content,
                    mime = it.format?.mimeType ?: "video/mp4",
                    ext = it.format?.suffix ?: "mp4",
                )
            }
            .distinctBy { it.label }
    }

    /** Streams the chosen quality to the public Downloads folder THROUGH the VPN
     *  proxy. Blocking — off main. Returns the saved file name. */
    fun download(context: Context, option: DownloadOption, title: String): String {
        ensureInit()
        val safe = title.replace(Regex("[^\\p{L}\\p{N} ._-]"), "_").trim().take(80).ifBlank { "video" }
        val fileName = "$safe.${option.ext}"
        val client = proxiedClient().newBuilder()
            .readTimeout(0, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .build()
        val req = okhttp3.Request.Builder()
            .url(option.url)
            .header("User-Agent", USER_AGENT_DESKTOP)
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("empty body")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, option.mime)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IOException("insert failed")
                resolver.openOutputStream(uri)?.use { out -> body.byteStream().copyTo(out) }
                    ?: throw IOException("openOutputStream null")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!dir.exists()) dir.mkdirs()
                File(dir, fileName).outputStream().use { out -> body.byteStream().copyTo(out) }
            }
        }
        return fileName
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
