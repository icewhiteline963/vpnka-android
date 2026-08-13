package com.v2ray.ang.ui

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Transcodes a picked video into a small H.264/AAC mp4 — the messenger keeps
 * media minimal. `maxHeight` sets the target resolution (480 = minimum, 720 =
 * normal). Runs on the main thread (Transformer requires a Looper) and suspends
 * until export finishes.
 */
object MediaCompress {
    @androidx.annotation.OptIn(markerClass = [UnstableApi::class])
    suspend fun compressVideo(context: Context, input: Uri, maxHeight: Int): File? =
        withContext(Dispatchers.Main) {
            val out = File(context.cacheDir, "vid_${System.currentTimeMillis()}.mp4")
            suspendCancellableCoroutine { cont ->
                val edited = EditedMediaItem.Builder(MediaItem.fromUri(input))
                    .setEffects(Effects(emptyList(), listOf(Presentation.createForHeight(maxHeight))))
                    .build()
                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, result: ExportResult) {
                            if (cont.isActive) cont.resumeWith(Result.success(out))
                        }

                        override fun onError(composition: Composition, result: ExportResult, exception: ExportException) {
                            out.delete()
                            if (cont.isActive) cont.resumeWith(Result.success(null))
                        }
                    })
                    .build()
                try {
                    transformer.start(edited, out.absolutePath)
                } catch (e: Exception) {
                    out.delete()
                    if (cont.isActive) cont.resumeWith(Result.success(null))
                }
                cont.invokeOnCancellation { runCatching { transformer.cancel() } }
            }
        }
}
