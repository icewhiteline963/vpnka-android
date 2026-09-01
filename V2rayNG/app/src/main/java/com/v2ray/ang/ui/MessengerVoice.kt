package com.v2ray.ang.ui

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/**
 * Voice-message recording + playback for the messenger. Records a small mono
 * AAC/m4a clip (low bitrate — media is kept minimal), and plays a decrypted
 * clip back. `playingId` is Compose-observable so bubbles show play/stop state.
 */
object MessengerVoice {
    private var recorder: MediaRecorder? = null
    private var recFile: File? = null
    private var recStart = 0L

    val isRecording get() = recorder != null

    fun startRecording(context: Context): Boolean {
        stopPlayback()
        val f = File(context.cacheDir, "voice_rec_${System.currentTimeMillis()}.m4a")
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(44100)
            r.setAudioEncodingBitRate(32000)
            r.setOutputFile(f.absolutePath)
            r.prepare(); r.start()
            recorder = r; recFile = f; recStart = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            runCatching { r.release() }; f.delete(); false
        }
    }

    /** Stops recording. Returns (m4a bytes, seconds) or null on error/too short. */
    fun stopRecording(): Pair<ByteArray, Int>? {
        val r = recorder ?: return null
        val f = recFile
        recorder = null; recFile = null
        val dur = ((System.currentTimeMillis() - recStart) / 1000).toInt().coerceAtLeast(1)
        return try {
            r.stop(); r.release()
            val bytes = f?.readBytes()
            f?.delete()
            if (bytes == null || bytes.isEmpty()) null else bytes to dur
        } catch (e: Exception) {
            runCatching { r.release() }; f?.delete(); null
        }
    }

    fun cancelRecording() {
        val r = recorder ?: return
        val f = recFile
        recorder = null; recFile = null
        runCatching { r.stop() }; runCatching { r.release() }; f?.delete()
    }

    // ---- playback ----
    private var player: MediaPlayer? = null
    var playingId by mutableStateOf<Long?>(null)
        private set

    fun toggle(context: Context, msgId: Long, audioB64: String) {
        if (playingId == msgId) { stopPlayback(); return }
        stopPlayback()
        val bytes = try { Base64.decode(audioB64, Base64.NO_WRAP) } catch (e: Exception) { return }
        if (bytes.isEmpty()) return
        val f = File(context.cacheDir, "voice_play.m4a")
        runCatching { f.writeBytes(bytes) }.getOrElse { return }
        playFile = f
        val mp = MediaPlayer()
        try {
            mp.setDataSource(f.absolutePath); mp.prepare(); mp.start()
            mp.setOnCompletionListener { stopPlayback() }
            player = mp; playingId = msgId
        } catch (e: Exception) {
            runCatching { mp.release() }
        }
    }

    fun stopPlayback() {
        player?.let { runCatching { it.stop() }; runCatching { it.release() } }
        player = null; playingId = null
        // Расшифрованное голосовое стираем с диска.
        //
        // Файл писался в кэш и НЕ удалялся никогда: последнее прослушанное
        // сообщение лежало открытым текстом бессрочно — в приложении, которое
        // обещает сквозное шифрование.
        runCatching { playFile?.delete() }
        playFile = null
    }

    /** Времянка последнего проигранного — чтобы её было чем удалить. */
    private var playFile: File? = null
}
