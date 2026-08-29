package com.v2ray.ang.handler

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import java.util.concurrent.TimeUnit

/**
 * Fetch a new version in the background so installing it is one tap.
 *
 * Without this, "update" means tapping and then watching 32 MB arrive over a
 * mobile link — long enough that people back out. Downloading ahead of time
 * turns the update screen into a single button, which is the behaviour users
 * already know from Telegram.
 */
object UpdatePrefetcher {

    private const val TASK_NAME = "vpnka_update_prefetch"

    /** Roughly daily; WorkManager will batch it with other wakeups anyway. */
    private const val INTERVAL_HOURS = 24L

    /** Версия, которую мы впервые увидели, и когда именно. */
    private const val KEY_SEEN_VERSION = "vpnka_update_seen_version"
    private const val KEY_SEEN_AT = "vpnka_update_seen_at"

    /**
     * Сколько ждём Wi-Fi, прежде чем скачать по мобильному.
     *
     * Ограничение «только Wi-Fi» поставлено не из осторожности: 43 МБ по
     * сотовой связи это и грубо, и при поднятом ВПН идёт через наши же
     * ноды с их месячными лимитами. Но у ограничения есть следствие, ради
     * которого всё и переделано: у кого Wi-Fi не бывает, тот не обновится
     * НИКОГДА. Через две недели ожидания одна загрузка по мобильному
     * дешевле, чем клиент, навсегда застрявший на старой сборке.
     */
    private const val METERED_FALLBACK_DAYS = 14L

    /**
     * Schedule the background check. Idempotent — safe to call on every
     * launch, KEEP leaves an already-scheduled run alone.
     */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<PrefetchTask>(
            INTERVAL_HOURS, TimeUnit.HOURS,
        )
            .setConstraints(
                Constraints.Builder()
                    // UNMETERED, not CONNECTED, and this is the important
                    // line. Silently pulling 32 MB over someone's mobile data
                    // is rude on its own — and while the VPN is up that
                    // traffic also runs through our own nodes, which have
                    // monthly caps we have already blown once. On Wi-Fi it
                    // costs the user nothing and us nothing.
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .setRequiresBatteryNotLow(true)
                    .setRequiresStorageNotLow(true)
                    .build()
            )
            .addTag(TASK_NAME)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            TASK_NAME, ExistingPeriodicWorkPolicy.KEEP, request,
        )
    }

    /**
     * Ask for a download now, rather than waiting out the daily cycle.
     *
     * Called when a launch-time check found something. Still UNMETERED: the
     * point is to shorten the wait until the next Wi-Fi moment, not to spend
     * someone's mobile data because they happened to open the app.
     *
     * KEEP, so repeatedly opening the app doesn't queue the same download
     * over and over.
     */
    fun requestPrefetchNow(context: Context, allowMetered: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<PrefetchTask>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(
                        if (allowMetered) NetworkType.CONNECTED
                        else NetworkType.UNMETERED
                    )
                    .setRequiresStorageNotLow(true)
                    .build()
            )
            .addTag(TASK_NAME)
            .build()
        // Разные имена: иначе KEEP на уже стоящей в очереди Wi-Fi-задаче
        // отменил бы запасной ход — а он нужен ровно тогда, когда та
        // задача ждёт Wi-Fi, которого не будет.
        WorkManager.getInstance(context).enqueueUniqueWork(
            TASK_NAME + if (allowMetered) "_now_metered" else "_now",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * Проверка при запуске нашла версию [version].
     *
     * Запоминаем, когда увидели её впервые, и решаем, ждать ли дальше
     * Wi-Fi. Проверка манифеста — несколько сотен байт и идёт по любой
     * сети, поэтому именно отсюда и виден момент «ждём слишком долго»:
     * суточная фоновая задача при отсутствии Wi-Fi не запускается вовсе и
     * заметить ничего не может.
     */
    fun noteAvailable(context: Context, version: String) {
        val known = MmkvManager.decodeSettingsString(KEY_SEEN_VERSION)
        val now = System.currentTimeMillis()
        if (known != version) {
            MmkvManager.encodeSettings(KEY_SEEN_VERSION, version)
            MmkvManager.encodeSettings(KEY_SEEN_AT, now.toString())
            requestPrefetchNow(context, allowMetered = false)
            return
        }
        val since = MmkvManager.decodeSettingsString(KEY_SEEN_AT)
            ?.toLongOrNull() ?: now
        val waited = now - since
        val overdue = waited > METERED_FALLBACK_DAYS * 24 * 60 * 60 * 1000L
        // Уже скачано — тратить чужой трафик не на что.
        val staged = ApkUpdateInstaller.readyUpdate(context)?.first == version
        requestPrefetchNow(context, allowMetered = overdue && !staged)
    }

    class PrefetchTask(context: Context, params: WorkerParameters) :
        CoroutineWorker(context, params) {

        override suspend fun doWork(): Result {
            return try {
                val check = UpdateCheckerManager.checkForUpdate(includePreRelease = false)
                if (!check.hasUpdate) {
                    // Nothing pending — drop anything we'd staged earlier so a
                    // pulled release can't sit in the cache offering itself.
                    ApkUpdateInstaller.clearReady(applicationContext)
                    return Result.success()
                }

                val version = check.latestVersion ?: return Result.success()
                val url = check.downloadUrl ?: return Result.success()

                // Already staged this exact version: don't re-download it
                // every day for a user who keeps declining the install.
                if (ApkUpdateInstaller.readyUpdate(applicationContext)?.first == version) {
                    return Result.success()
                }

                LogUtil.i(AppConfig.TAG, "Prefetching update $version")
                ApkUpdateInstaller.download(applicationContext, url) { }
                ApkUpdateInstaller.markReady(version)
                LogUtil.i(AppConfig.TAG, "Update $version staged for install")
                Result.success()
            } catch (e: Exception) {
                // Retry rather than fail: the usual cause is the mirror being
                // briefly unreachable, and WorkManager will back off for us.
                LogUtil.w(AppConfig.TAG, "Update prefetch failed: ${e.message}")
                Result.retry()
            }
        }
    }
}
