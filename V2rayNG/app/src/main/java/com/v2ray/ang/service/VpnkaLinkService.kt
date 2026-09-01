package com.v2ray.ang.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.enums.NotificationChannelType
import com.v2ray.ang.handler.CallManager
import com.v2ray.ang.handler.Messenger
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.VpnkaAccount
import com.v2ray.ang.ui.MainActivity
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Holds the messenger socket open while the app is off screen, so a call rings
 * instead of arriving after the fact.
 *
 * Why a service at all: call signaling is pushed over the WebSocket, and that
 * socket only existed while the messenger screen was composed. Minimise the app
 * and the socket goes with it — the relay has no queue, so the offer reaches
 * nobody and the caller is told the peer is offline. Polling cannot replace it:
 * [com.v2ray.ang.handler.MessengerNotifier] runs on WorkManager's 15-minute
 * floor, which is fine for "you have a message" and useless for a ringing phone.
 *
 * Deliberately in the default process, unlike the core services: it shares the
 * singletons that make a call possible — `Messenger` (socket, contacts, and its
 * single-process MMKV store), `CallManager` (the engine and its state), and the
 * unlocked vault key. In the `:RunSoLibV2RayDaemon` process none of that exists.
 *
 * Zero-knowledge limit, and it is a real one: a call frame is opened (RSA, key
 * from the vault) BEFORE it reaches the engine, so with the vault locked there
 * is no ring at all — not even a nameless one. In practice this holds because
 * the service keeps the process, and with it the cached master key, alive: the
 * user unlocks once and calls ring from then on. After a reboot or a kill, the
 * app must be opened once before calls can arrive again. Ringing without the
 * key would mean trusting the server's word that a frame is a call, which is
 * exactly the trust this design refuses.
 */
class VpnkaLinkService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(
            NotificationChannelType.MESSENGER_LINK.notificationId,
            linkNotification(),
        )

        CallManager.attach()
        CallManager.onIncomingCall = { _, name -> ring(name) }
        CallManager.onCallCleared = { stopRinging() }

        // Idempotent: `connectWs` returns immediately while a socket is live and
        // rebuilds it after the listener nulled a dead one. The messenger screen
        // calls the same thing on its own tick, and they share one socket.
        scope.launch {
            while (true) {
                // Разговор рвать нельзя. disconnectWs обнуляет и сам сокет,
                // и приёмник событий, а поднять его обратно теперь нельзя —
                // connectWs тоже за гейтом. Дожидаемся конца звонка.
                val busy = CallManager.phase == CallManager.Phase.ACTIVE ||
                    CallManager.phase == CallManager.Phase.OUTGOING ||
                    CallManager.phase == CallManager.Phase.INCOMING
                if (!wanted() && !busy) {
                    runCatching { Messenger.disconnectWs() }
                    // Токен пропал или SmartDesk отключили — уходим сами.
                    // Служба переживает и то и другое: система поднимает её
                    // обратно, и без этой проверки она осталась бы висеть
                    // форграунд-уведомлением, переподключаясь впустую.
                    stopSelf()
                    return@launch
                }
                runCatching { Messenger.ensureWs() }
                    .onFailure { LogUtil.w(AppConfig.TAG, "link: ws failed: ${it.message}") }
                delay(RECONNECT_MS)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DECLINE -> {
                CallManager.decline()
                stopRinging()
            }
            ACTION_STOP -> stopSelf()
        }
        // Restart if the system reclaims us: a socket that stays down is the one
        // failure this service exists to prevent.
        return START_STICKY
    }

    override fun onDestroy() {
        // Only our own hooks — `onCallSignal` belongs to CallManager.attach()
        // and the messenger screen still needs it.
        CallManager.onIncomingCall = null
        CallManager.onCallCleared = null
        stopRinging()
        scope.cancel()
        super.onDestroy()
    }

    // --- notifications ---

    private fun channel(type: NotificationChannelType) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(type.channelId) == null) {
            nm.createNotificationChannel(
                NotificationChannel(type.channelId, type.channelName, type.importance)
            )
        }
    }

    private fun openAppIntent(what: String): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN, what)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this, what.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun linkNotification() = run {
        channel(NotificationChannelType.MESSENGER_LINK)
        NotificationCompat.Builder(this, NotificationChannelType.MESSENGER_LINK.channelId)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle("ВПНка на связи")
            .setContentText("Звонки приходят, пока приложение свёрнуто")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(openAppIntent(MainActivity.OPEN_MESSENGER))
            .build()
    }

    /** Звонок и вибрация — пока идёт вызов. */
    private var ringtone: android.media.Ringtone? = null
    private var vibrator: android.os.Vibrator? = null

    /**
     * Позвонить по-настоящему.
     *
     * Раньше звонка НЕ БЫЛО ВОВСЕ: при открытом мессенджере вызов был
     * беззвучным, при закрытом — один короткий сигнал канала уведомлений на
     * всё шестидесятисекундное окно дозвона. То есть входящий звонок можно
     * было увидеть, только глядя на экран.
     *
     * Уважаем режим телефона: в беззвучном не звоним, в «только вибрация» —
     * только вибрируем. Своей громкости не навязываем.
     */
    private fun startRinging() {
        val am = getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
        val mode = am?.ringerMode ?: android.media.AudioManager.RINGER_MODE_NORMAL
        if (mode == android.media.AudioManager.RINGER_MODE_NORMAL) {
            runCatching {
                val uri = android.media.RingtoneManager
                    .getActualDefaultRingtoneUri(this, android.media.RingtoneManager.TYPE_RINGTONE)
                    ?: android.media.RingtoneManager
                        .getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)
                ringtone = android.media.RingtoneManager.getRingtone(this, uri)?.apply {
                    audioAttributes = android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
                    play()
                }
            }
        }
        if (mode != android.media.AudioManager.RINGER_MODE_SILENT) {
            runCatching {
                val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                        as? android.os.VibratorManager)?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                }
                vibrator = v
                val pattern = longArrayOf(0, 800, 900)
                v?.vibrate(
                    android.os.VibrationEffect.createWaveform(pattern, 0),
                )
            }
        }
    }

    private fun stopRingingSound() {
        runCatching { ringtone?.stop() }
        ringtone = null
        runCatching { vibrator?.cancel() }
        vibrator = null
    }

    private fun ring(name: String) {
        // Звучим в любом случае: даже когда мессенджер открыт и рисует свой
        // экран звонка, звук нужен — человек мог отложить телефон.
        startRinging()
        // The messenger draws its own full call screen; a heads-up on top of it
        // would just be the same call twice. Everywhere else — home screen,
        // another app, screen off — the notification is the only thing there is.
        if (messengerVisible) return
        channel(NotificationChannelType.CALL_INCOMING)
        val answer = openAppIntent(MainActivity.OPEN_CALL)
        val decline = PendingIntent.getService(
            this, 1,
            Intent(this, VpnkaLinkService::class.java).setAction(ACTION_DECLINE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(this, NotificationChannelType.CALL_INCOMING.channelId)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(name)
            .setContentText("Входящий звонок")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setContentIntent(answer)
            // Wakes the screen where the system allows it; degrades to a
            // heads-up banner where it does not.
            .setFullScreenIntent(answer, true)
            .addAction(0, "Ответить", answer)
            .addAction(0, "Отклонить", decline)
            .build()
        runCatching {
            NotificationManagerCompat.from(this)
                .notify(NotificationChannelType.CALL_INCOMING.notificationId, notif)
        }
    }

    private fun stopRinging() {
        stopRingingSound()
        runCatching {
            NotificationManagerCompat.from(this)
                .cancel(NotificationChannelType.CALL_INCOMING.notificationId)
        }
    }

    companion object {
        const val ACTION_DECLINE = "com.v2ray.ang.action.CALL_DECLINE"
        const val ACTION_STOP = "com.v2ray.ang.action.LINK_STOP"

        /** Messenger setting key: keep the socket up while off screen. */
        const val SETTING = "bg_calls"

        /** Set by the messenger screen while it is composed (it owns the call UI). */
        @Volatile
        var messengerVisible = false

        private const val RECONNECT_MS = 5_000L

        fun wanted(): Boolean =
            MmkvManager.getAccountToken() != null &&
                // Служба держит сокет мессенджера, а мессенджер живёт внутри
                // SmartDesk. Раньше она поднималась у всех при каждом
                // запуске приложения и переподключалась вечно у тех, кого
                // сервер не пустит никогда.
                VpnkaAccount.smartDeskAllowed() &&
                Messenger.setting(SETTING, true)

        /** Start (or leave running) the link, when it is wanted at all. */
        fun start(context: Context) {
            if (!wanted()) return
            runCatching {
                ContextCompat.startForegroundService(
                    context, Intent(context, VpnkaLinkService::class.java)
                )
            }.onFailure { LogUtil.w(AppConfig.TAG, "link: start failed: ${it.message}") }
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, VpnkaLinkService::class.java).setAction(ACTION_STOP)
                )
            }
        }
    }
}
