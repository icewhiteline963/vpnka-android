package com.v2ray.ang.handler

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.v2ray.ang.AppConfig
import com.v2ray.ang.enums.NotificationChannelType
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.NotificationHelper
import java.util.concurrent.TimeUnit

/**
 * Tells the user their subscription is about to end, before it does.
 *
 * The banner on the main screen only reaches people who happen to open the
 * app, which is nobody once the tunnel is working — so the first sign of an
 * expiry was the connection stopping. That is both a bad day for the user
 * and a lost renewal.
 *
 * Two notifications: three days out, and inside the last twenty-four hours.
 * Each fires once per subscription period, tracked by the day count it was
 * sent for, so a daily check cannot turn into a daily nag.
 */
object ExpiryReminder {

    private const val TASK_NAME = "vpnka_expiry_reminder"
    private const val INTERVAL_HOURS = 12L

    /** The thresholds, in days remaining. Order matters: nearest first. */
    private val STAGES = listOf(1, 3)

    /**
     * Schedule the check. Idempotent — safe on every launch, KEEP leaves an
     * existing schedule alone rather than resetting its clock, which would
     * mean it never runs for someone who opens the app often.
     */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<ReminderTask>(
            INTERVAL_HOURS, TimeUnit.HOURS,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            TASK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    class ReminderTask(
        context: Context,
        params: WorkerParameters,
    ) : CoroutineWorker(context, params) {

        override suspend fun doWork(): Result {
            // No account, nothing to remind about: the shipped trial is not
            // a purchase and has its own countdown on the screen.
            MmkvManager.getAccountToken() ?: return Result.success()

            val info = try {
                VpnkaAccount.fetchInfo()
            } catch (e: Exception) {
                LogUtil.w(AppConfig.TAG, "ExpiryReminder: fetch failed: ${e.message}")
                // Retry rather than success: a missed check on a flaky
                // network should not cost the user the warning.
                return Result.retry()
            } ?: return Result.success()

            // Respect the in-app notification toggle. The server still delivers
            // the same expiry notice to the in-app inbox and (if enabled) to
            // Telegram — this only governs the on-device push.
            if (!info.notifyExpiryInApp) return Result.success()

            // По самому ДАЛЬНЕМУ сроку: доступ кончается тогда, когда истечёт
            // последний план. Брался ближайший — и человек с годовой
            // подпиской плюс бесплатным месяцем получал системное
            // уведомление «подписка кончается сегодня», хотя впереди год.
            val daysLeft = info.subscriptions.orEmpty()
                // Пробные не считаем: суточный пробник первого запуска даёт
                // ноль дней, и в первые часы после установки человеку
                // приходило «подписка кончается сегодня, продлите в боте». То
                // же каждый месяц у тех, кто живёт на бесплатном месяце.
                .filter { !it.frozen && !it.isTrial }
                .mapNotNull { it.daysLeft }
                .maxOrNull()
                ?: return Result.success()

            val stage = STAGES.firstOrNull { daysLeft <= it } ?: run {
                // Comfortably far out — clear the marker so the next period
                // warns again after a renewal.
                MmkvManager.setExpiryReminded(0)
                return Result.success()
            }

            if (MmkvManager.getExpiryReminded() == stage) return Result.success()

            NotificationHelper.notify(
                NotificationChannelType.EXPIRY_REMINDER,
                applicationContext,
                title = if (stage == 1) {
                    "Подписка кончается сегодня"
                } else {
                    "Подписка кончается через $daysLeft дн."
                },
                content = "Откройте бота VPNka и продлите, чтобы связь не прервалась.",
            )
            MmkvManager.setExpiryReminded(stage)
            return Result.success()
        }
    }
}
