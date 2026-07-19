package com.v2ray.ang

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkManager
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import com.v2ray.ang.compose.ThemeManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.UpdatePrefetcher

class AngApplication : Application() {
    companion object {
        lateinit var application: AngApplication

        /**
         * True when this launch found nothing to connect to and wants the
         * trial fetched. MainActivity reads it and pulls the subscription,
         * so the user lands on a populated server list instead of an empty
         * screen. Set on every such launch, not just the first — a fetch
         * that failed for want of network must get another try.
         */
        var vpnkaNeedsTrialFetch: Boolean = false

        /**
         * Set when the app was reopened by the post-payment link.
         *
         * The subscription itself arrives through the payment webhook, not
         * through the link, so there is nothing to install here — this only
         * tells MainActivity to show the profile and re-read it, which is
         * what someone who just paid is looking for.
         */
        var vpnkaJustPaid: Boolean = false
    }

    /**
     * Attaches the base context to the application.
     * @param base The base context.
     */
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        application = this
    }

    private val workManagerConfiguration: Configuration = Configuration.Builder()
        .setDefaultProcessName("${ANG_PACKAGE}:bg")
        .build()

    /**
     * Initializes the application.
     */
    override fun onCreate() {
        super.onCreate()

        MMKV.initialize(this)

        // Initialize WorkManager with the custom configuration
        WorkManager.initialize(this, workManagerConfiguration)

        // Ensure critical preference defaults are present in MMKV early
        SettingsManager.initApp(this)

        // Initialize theme state from MMKV
        ThemeManager.refresh()

        // Make sure an install with no servers has our 24h trial to fall
        // back on. MMKV writes only — the fetch happens in MainActivity, off
        // the main thread; network here would block app startup.
        // Re-fetch after an update as well as when there's nothing to
        // connect to: the config format follows the version we announce, so
        // an install that updates without refreshing keeps the old one.
        vpnkaNeedsTrialFetch = MmkvManager.ensureTrialSubscription() or
            MmkvManager.consumeVersionChanged(BuildConfig.VERSION_NAME)

        // Pull a new version down in the background so installing it later is
        // a single tap. Wi-Fi only — see UpdatePrefetcher for why that matters
        // more for us than for most apps.
        UpdatePrefetcher.schedule(this)
    }
}
