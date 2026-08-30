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
import com.v2ray.ang.ui.MainActivity
import com.v2ray.ang.util.NotificationHelper
import java.util.concurrent.TimeUnit

/**
 * Once a day, ask whether a newer version exists and, if so, tell the user.
 *
 * [UpdatePrefetcher] already runs daily, but only on Wi-Fi — because it
 * downloads tens of megabytes. Most of our users are on mobile data most of
 * the time, so on its own the prefetcher can go a long while without ever
 * noticing a release. The *check* is different: it fetches one small JSON
 * manifest (a few hundred bytes), which is cheap on any network, so this
 * worker runs on CONNECTED and posts a notification the moment a release is
 * out — the "предложить обновиться" the prefetcher can't promise.
 *
 * The heavy download stays Wi-Fi-only: when we do find something we nudge the
 * prefetcher ([UpdatePrefetcher.requestPrefetchNow]) so the eventual install
 * is still one tap, but we never pull the APK over metered data ourselves.
 *
 * Fires once per version, tracked by the version string, so a daily check
 * can't turn into a daily nag for someone who keeps putting off the update.
 */
object UpdateNotifier {

    private const val TASK_NAME = "vpnka_update_notify"
    private const val INTERVAL_HOURS = 24L

    /** Last version we already showed a notification for. */
    private const val KEY_NOTIFIED_VERSION = "vpnka_update_notified_version"

    /**
     * Idempotent — safe on every launch. KEEP leaves an existing schedule
     * alone rather than resetting its clock, so it still runs for someone who
     * opens the app often.
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
            val check = try {
                UpdateCheckerManager.checkForUpdate(includePreRelease = false)
            } catch (e: Exception) {
                // A missed check on a flaky network shouldn't cost the user the
                // notice — let WorkManager back off and try again.
                LogUtil.w(AppConfig.TAG, "UpdateNotifier: check failed: ${e.message}")
                return Result.retry()
            }

            if (!check.hasUpdate) {
                // Caught up — forget the marker so the next release notifies
                // again rather than being suppressed as "already seen".
                MmkvManager.encodeSettings(KEY_NOTIFIED_VERSION, "")
                return Result.success()
            }

            val version = check.latestVersion ?: return Result.success()

            // Start pulling the APK now (still Wi-Fi-only inside the prefetcher)
            // so that when the user acts on the notice, the install is one tap.
            UpdatePrefetcher.requestPrefetchNow(applicationContext)

            // Already told them about this exact version — don't nag daily.
            if (MmkvManager.decodeSettingsString(KEY_NOTIFIED_VERSION) == version) {
                return Result.success()
            }

            NotificationHelper.notify(
                NotificationChannelType.UPDATE_AVAILABLE,
                applicationContext,
                title = "Доступно обновление VPNka $version",
                content = "Нажмите, чтобы установить.",
                openExtra = MainActivity.OPEN_UPDATE,
            )
            MmkvManager.encodeSettings(KEY_NOTIFIED_VERSION, version)
            return Result.success()
        }
    }
}
