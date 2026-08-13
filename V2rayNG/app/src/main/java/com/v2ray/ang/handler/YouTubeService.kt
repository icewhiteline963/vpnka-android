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

    /** Blocking — off main. Prefers adaptive (best video-only + best audio) for
     *  real quality; falls back to a muxed stream. */
    fun resolve(videoUrl: String): Playback {
        ensureInit()
        val si = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)
        val bestVideoOnly = si.videoOnlyStreams
            .filter { it.content.isNotBlank() }
            .maxByOrNull { resRank(it.resolution) }
        val bestAudio = si.audioStreams
            .filter { it.content.isNotBlank() }
            .maxByOrNull { it.averageBitrate }
        if (bestVideoOnly != null && bestAudio != null) {
            return Playback(si.name, bestVideoOnly.content, videoUrl, bestAudio.content)
        }
        val muxed = si.videoStreams.firstOrNull { !it.isVideoOnly }
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

    /** Saves a subtitle track (text) to Downloads THROUGH the VPN proxy. Off main. */
    fun downloadSubtitle(context: Context, sub: SubtitleOption, title: String): String {
        ensureInit()
        val safe = title.replace(Regex("[^\\p{L}\\p{N} ._-]"), "_").trim().take(80).ifBlank { "video" }
        val fileName = "$safe.${sub.ext}"
        val tmp = File(context.cacheDir, "yt_sub_${System.currentTimeMillis()}.${sub.ext}")
        try {
            downloadTo(proxiedClient(), sub.url, tmp)
            saveFileToDownloads(context, fileName, "text/plain", tmp)
        } finally {
            tmp.delete()
        }
        return fileName
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
    fun download(context: Context, option: DownloadOption, title: String): String {
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
            downloadTo(client, option.videoUrl, tmpV)
            if (option.audioUrl == null) {
                saveFileToDownloads(context, fileName, option.mime, tmpV)
            } else {
                val tmpA = File(context.cacheDir, "yt_a_$ts.m4a")
                val tmpOut = File(context.cacheDir, "yt_out_$ts.mp4")
                try {
                    downloadTo(client, option.audioUrl, tmpA)
                    remux(tmpV, tmpA, tmpOut)
                    saveFileToDownloads(context, fileName, "video/mp4", tmpOut)
                } finally {
                    tmpA.delete(); tmpOut.delete()
                }
            }
        } finally {
            tmpV.delete()
        }
        return fileName
    }

    private fun downloadTo(client: OkHttpClient, url: String, dest: File) {
        val req = okhttp3.Request.Builder().url(url).header("User-Agent", USER_AGENT_DESKTOP).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("empty body")
            dest.outputStream().use { out -> body.byteStream().copyTo(out) }
        }
    }

    private fun saveFileToDownloads(context: Context, fileName: String, mime: String, file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mime)
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
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!dir.exists()) dir.mkdirs()
            File(dir, fileName).outputStream().use { out -> file.inputStream().use { it.copyTo(out) } }
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
