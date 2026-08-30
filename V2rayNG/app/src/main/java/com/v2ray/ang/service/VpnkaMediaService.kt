package com.v2ray.ang.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.MainActivity
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy

/**
 * Плеер YouTube, который переживает уход с экрана.
 *
 * До этого ExoPlayer жил внутри Compose-экрана и умирал вместе с ним:
 * свернул приложение — звук кончился. Именно ради фонового воспроизведения
 * люди ставили Vanced, там это единственная платная функция Google, которую
 * он разблокировал. У нас своего клиента, поэтому пишем сами.
 *
 * Плеер живёт ЗДЕСЬ, а экран лишь подключается к нему контроллером. Отсюда
 * же берутся управление с экрана блокировки, кнопки на наушниках и
 * картинка-в-картинке — всё это даёт `MediaSession`.
 *
 * Весь трафик по-прежнему идёт через локальный прокси ВПН: подмена
 * источника данных сделана в одном месте, и обойти её нельзя.
 */
@UnstableApi
class VpnkaMediaService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val proxied = OkHttpClient.Builder()
            .proxy(
                Proxy(
                    Proxy.Type.HTTP,
                    InetSocketAddress("127.0.0.1", SettingsManager.getHttpPort()),
                )
            )
            .build()
        val dataSource = OkHttpDataSource.Factory(proxied)

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(MergingSourceFactory(dataSource))
            // Пауза при звонке или чужом воспроизведении — иначе два звука
            // играют поверх друг друга.
            .setHandleAudioBecomingNoisy(true)
            .build()

        // Нажатие на уведомление плеера возвращает в приложение.
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(open)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        session

    /**
     * Пользователь смахнул приложение из недавних.
     *
     * Если ничего не играет — уходим совсем, чтобы не висеть уведомлением.
     * Если играет — остаёмся: человек смахнул окно, а не музыку.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    /**
     * Склеивает видео со звуком, когда YouTube отдаёт их раздельно.
     *
     * Выше 720p муксованных потоков у YouTube нет: видео и звук приходят
     * отдельными дорожками, и соединять их должен плеер. Адрес звука едет в
     * `extras` элемента — это Bundle, он переживает передачу из экрана в
     * службу, а обычный `tag` не переживает.
     *
     * `MediaSource.Factory` — НЕ функциональный интерфейс (у него четыре
     * метода), лямбдой его не задать; поэтому обычный класс с делегатом.
     */
    private class MergingSourceFactory(
        dataSource: OkHttpDataSource.Factory,
    ) : MediaSource.Factory {

        private val delegate = ProgressiveMediaSource.Factory(dataSource)

        override fun setDrmSessionManagerProvider(
            provider: DrmSessionManagerProvider,
        ): MediaSource.Factory = apply { delegate.setDrmSessionManagerProvider(provider) }

        override fun setLoadErrorHandlingPolicy(
            policy: LoadErrorHandlingPolicy,
        ): MediaSource.Factory = apply { delegate.setLoadErrorHandlingPolicy(policy) }

        override fun getSupportedTypes(): IntArray = delegate.supportedTypes

        override fun createMediaSource(mediaItem: MediaItem): MediaSource {
            val video = delegate.createMediaSource(mediaItem)
            val audioUrl = mediaItem.requestMetadata.extras?.getString(EXTRA_AUDIO_URL)
            return if (audioUrl.isNullOrBlank()) {
                video
            } else {
                MergingMediaSource(
                    video,
                    delegate.createMediaSource(MediaItem.fromUri(audioUrl)),
                )
            }
        }
    }

    companion object {
        /** Ключ адреса звуковой дорожки в extras элемента. */
        const val EXTRA_AUDIO_URL = "vpnka_audio_url"
    }
}
