package com.v2ray.ang.handler

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.enums.NotificationChannelType
import com.v2ray.ang.ui.MainActivity
import com.v2ray.ang.util.LogUtil
import java.util.concurrent.TimeUnit

/**
 * Poll the E2E messenger in the background and raise a system notification for
 * a new incoming message, tapping it straight into that chat.
 *
 * Same shape as [SupportNotifier]: no FCM (we don't want Google services on a
 * censorship-circumvention app), so the check is WorkManager's 15-min floor for
 * periodic work. The messenger UI, when open, polls every 2.5s and advances the
 * shared cursor first — so this only fires for messages that arrived while the
 * app wasn't being looked at, which is exactly when a notification is wanted.
 *
 * Zero-knowledge caveat: decrypting needs the vault unlocked (the RSA private
 * key lives there). On a cold process the master key isn't cached, `poll()`
 * returns nothing, and no notification is raised until the user next unlocks —
 * an acceptable trade for never holding the key where the server could reach it.
 */
object MessengerNotifier {

    /** Диапазон для уведомлений переписки: выше всех фиксированных id каналов. */
    private const val MSG_NOTIFICATION_BASE = 100_000

    private const val TASK_NAME = "vpnka_messenger_notify"
    private const val INTERVAL_MIN = 15L

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<NotifyTask>(
            INTERVAL_MIN, TimeUnit.MINUTES,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag(TASK_NAME)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            TASK_NAME, ExistingPeriodicWorkPolicy.KEEP, request,
        )
    }

    class NotifyTask(context: Context, params: WorkerParameters) :
        CoroutineWorker(context, params) {

        override suspend fun doWork(): Result {
            // No account or messenger notifications turned off → nothing to do.
            MmkvManager.getAccountToken() ?: return Result.success()
            if (!Messenger.setting("notify", true)) return Result.success()

            // Peek without the vault: a locked device (cold WorkManager run,
            // master key gone) can't decrypt, but it can still see that a new
            // ciphertext arrived and raise a generic notification.
            val fresh = try {
                Messenger.checkIncomingForNotify()
            } catch (e: Exception) {
                LogUtil.w(AppConfig.TAG, "MessengerNotifier: check failed: ${e.message}")
                return Result.retry()
            }
            if (fresh.isEmpty()) return Result.success()

            // One notification per sender. The content stays generic — we never
            // decrypt in the background — so the body is a fixed "Новое сообщение".
            fresh.distinctBy { it.contactId }.forEach { it ->
                postNotification(applicationContext, it.contactId, it.name, "Новое сообщение")
            }
            return Result.success()
        }
    }

    private fun postNotification(context: Context, chatId: Long, name: String, body: String) {
        val ch = NotificationChannelType.MESSENGER
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
            if (nm.getNotificationChannel(ch.channelId) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(ch.channelId, ch.channelName, ch.importance)
                )
            }
        }

        // Tapping opens the app → SmartDesk → the messenger, on this chat.
        val tap = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_MESSENGER)
            putExtra(MainActivity.EXTRA_CHAT, chatId)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        // Distinct requestCode per chat so per-sender intents don't collide.
        val pi = PendingIntent.getActivity(
            context, chatId.toInt(), tap,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notif = NotificationCompat.Builder(context, ch.channelId)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(name)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        // notify() no-ops without POST_NOTIFICATIONS on 33+; guard so a missing
        // grant can't crash the worker. Per-chat id keeps senders separate.
        //
        // 24.08.2026: id считался как 17 + chatId, а 18 и 19 заняты фоновой
        // службой и ВХОДЯЩИМ ЗВОНКОМ. Клиентские id — маленькие числа, так что
        // сообщение от собеседника №2 стирало уведомление о звонке прямо во
        // время звонка. Уводим переписку в диапазон, где своих нет.
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(MSG_NOTIFICATION_BASE + (chatId.toInt() and 0xFFFF), notif)
        }
    }
}
