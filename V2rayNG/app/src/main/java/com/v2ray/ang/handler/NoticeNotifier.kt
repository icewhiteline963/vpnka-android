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
 * Push без Google. Раз в несколько часов в фоне спрашиваем бэкенд, нет ли для
 * этой установки уведомления (`/app/notifications`), и если есть — показываем
 * ЛОКАЛЬНУЮ нотификацию. Нужен, чтобы дотянуться до анонимной установки,
 * которую человек поставил и не открывает: FCM у нас нет принципиально (нет
 * гугл-зависимостей), а `VpnkaLinkService`-сокет живёт только при активном
 * VPN/мессенджере — до закрытого приложения он не долетает.
 *
 * Компромиссы механизма (осознанные): доставка не мгновенная — Android батчит
 * фоновые задачи в Doze, так что уведомление приходит в пределах часов, не
 * секунд; и оно НЕ достучится до приложения, принудительно остановленного
 * (force stop), — WorkManager там не крутится. Обычное закрытие/свайп — норм.
 *
 * Идемпотентно: одно и то же уведомление показываем один раз (метка по id).
 * Прочитанным НЕ помечаем — пусть остаётся видимым и внутри приложения.
 */
object NoticeNotifier {

    private const val TASK_NAME = "vpnka_notice_notify"
    private const val INTERVAL_HOURS = 6L

    /** Наибольший id уведомления, для которого уже показали нотификацию. */
    private const val KEY_LAST_NOTICE_ID = "vpnka_notice_last_shown_id"

    /**
     * Идемпотентно — безопасно на каждом запуске. KEEP не сбрасывает часы
     * существующему расписанию.
     */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<NotifyTask>(
            INTERVAL_HOURS, TimeUnit.HOURS,
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
            // Нет аккаунта (токен ещё не завёлся) — спрашивать нечего.
            if (!VpnkaAccount.isSignedIn()) return Result.success()

            val notices = try {
                VpnkaAccount.fetchNotices()
            } catch (e: Exception) {
                LogUtil.w(AppConfig.TAG, "NoticeNotifier: fetch failed: ${e.message}")
                return Result.retry()
            }

            // Самое свежее непрочитанное с непустым текстом.
            val latest = notices
                .filter { !it.read && it.body.isNotBlank() }
                .maxByOrNull { it.id }
                ?: return Result.success()

            // Уже показывали это (или более свежее) — не дёргаем повторно.
            val lastShown = MmkvManager.decodeSettingsLong(KEY_LAST_NOTICE_ID, 0L)
            if (latest.id <= lastShown) return Result.success()

            NotificationHelper.notify(
                NotificationChannelType.NOTICE,
                applicationContext,
                title = "Впнка",
                content = latest.body,
                // openExtra null → обычный запуск на главный экран; само
                // уведомление останется видимым и в списке уведомлений в апке.
                openExtra = null,
            )
            MmkvManager.encodeSettings(KEY_LAST_NOTICE_ID, latest.id)
            return Result.success()
        }
    }
}
