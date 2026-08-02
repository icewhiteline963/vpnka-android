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
 * Poll for a support reply and raise a system notification when one lands.
 *
 * The reply already reaches the in-app inbox and the support thread over HTTP,
 * but only someone who happens to open the app would see it — which, for a
 * working tunnel, is nobody. This checks in the background and, on a new
 * "support_reply" notice, posts a notification that opens straight into the
 * chat with the operator.
 *
 * No FCM (Google services aren't a dependency we want on a censorship-circumvention
 * app): the poll is 15 min, WorkManager's floor for periodic work, which is
 * fine latency for a support answer and costs one small request.
 */
object SupportNotifier {

    private const val TASK_NAME = "vpnka_support_notify"
    private const val INTERVAL_MIN = 15L

    /** Highest support-reply notice id we've already raised a notification for. */
    private const val KEY_LAST_ID = "vpnka_support_notified_id"

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
            // No account → nothing to poll.
            MmkvManager.getAccountToken() ?: return Result.success()

            val notices = try {
                VpnkaAccount.fetchNotices()
            } catch (e: Exception) {
                LogUtil.w(AppConfig.TAG, "SupportNotifier: fetch failed: ${e.message}")
                return Result.retry()
            }

            val lastId = MmkvManager.decodeSettingsString(KEY_LAST_ID)
                ?.toLongOrNull() ?: 0L
            // Unread support replies newer than the last one we announced. Read
            // ones mean the user already opened the app and saw them; the id
            // gate stops a still-unread reply from re-notifying every 15 min.
            val fresh = notices
                .filter { it.kind == "support_reply" && !it.read && it.id > lastId }
                .maxByOrNull { it.id }
                ?: return Result.success()

            postNotification(applicationContext, fresh.body)
            MmkvManager.encodeSettings(KEY_LAST_ID, fresh.id.toString())
            return Result.success()
        }
    }

    private fun postNotification(context: Context, body: String) {
        val ch = NotificationChannelType.SUPPORT_REPLY
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
            if (nm.getNotificationChannel(ch.channelId) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(ch.channelId, ch.channelName, ch.importance)
                )
            }
        }

        // Tapping opens the app straight into the support chat.
        val tap = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_SUPPORT)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, 0, tap,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notif = NotificationCompat.Builder(context, ch.channelId)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle("Поддержка ответила")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        // notify() no-ops without the POST_NOTIFICATIONS grant on 33+; guard so
        // a missing permission can't crash the worker.
        runCatching {
            NotificationManagerCompat.from(context).notify(ch.notificationId, notif)
        }
    }
}
