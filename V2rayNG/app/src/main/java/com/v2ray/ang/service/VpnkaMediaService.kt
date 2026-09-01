package com.v2ray.ang.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.C
import androidx.media3.common.AudioAttributes
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
            // Пауза при выдёргивании наушников.
            .setHandleAudioBecomingNoisy(true)
            // И — отдельно — уступать звук чужим.
            //
            // Прежний комментарий здесь утверждал, что предыдущая строка даёт
            // паузу при звонке. Неправда: она реагирует ТОЛЬКО на наушники.
            // Без управления фокусом наше видео орёт поверх звонка, будильника
            // и чужой музыки — а с фоновым воспроизведением это стало
            // ежедневным.
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .build()

        // Нажатие на уведомление плеера возвращает в приложение.
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // История просмотра пишется и тогда, когда экран плеера уже закрыт.
        //
        // Раньше позицию и отметку «досмотрел» сохранял только экран, а
        // ролик, дослушанный в фоне, оставался «недосмотренным»: при
        // следующем открытии видео отматывалось на старое место, а уборка
        // скачанного его не видела.
        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // Ссылки на потоки привязаны ко времени и к адресу выхода:
                // пауза на час, смена ноды или переподключение туннеля делают
                // их недействительными. Раньше плеер просто замирал —
                // картинка стоит, кнопка показывает «играет», сообщения нет.
                // Одна попытка пересобрать, дальше отдаём состояние экрану.
                runCatching { session?.player?.prepare() }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state != androidx.media3.common.Player.STATE_ENDED) return
                val p = session?.player ?: return
                // ENDED приходит и от очистки очереди («стоп» в мини-плеере),
                // а не только от досмотренного ролика. Отличаем по позиции:
                // без этого брошенный на второй минуте ролик пометился бы
                // досмотренным и попал под автоуборку скачанного.
                val dur = p.duration
                if (dur <= 0 || p.currentPosition < dur - 30_000) return
                com.v2ray.ang.handler.YouTubeNowPlaying.current?.pageUrl?.let {
                    com.v2ray.ang.handler.YouTubeHistory.markWatched(it)
                }
            }
        })

        session = MediaSession.Builder(this, player)
            .setSessionActivity(open)
            .build()
    }

    /** Запомнить, где остановились. Зовём, пока плеер ещё жив. */
    private fun savePosition() {
        val p = session?.player ?: return
        val page = com.v2ray.ang.handler.YouTubeNowPlaying.current?.pageUrl ?: return
        if (p.duration <= 0) return
        runCatching {
            com.v2ray.ang.handler.YouTubeHistory.savePosition(
                page, p.currentPosition / 1000, p.duration / 1000,
            )
        }
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
        savePosition()
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        savePosition()
        // Служба ушла — строки «сейчас играет» быть не должно, иначе она
        // предлагает остановить то, чего уже нет.
        com.v2ray.ang.handler.YouTubeNowPlaying.current = null
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
