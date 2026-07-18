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

class AngApplication : Application() {
    companion object {
        lateinit var application: AngApplication

        /**
         * True for the one launch that seeded the trial subscription.
         * MainActivity reads it to fetch that subscription immediately, so
         * a first-time user lands on a populated server list instead of an
         * empty screen with a subscription they'd have to update by hand.
         */
        var vpnkaSeededTrialThisLaunch: Boolean = false
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

        // Hand a brand-new install the 24h trial subscription. MMKV writes
        // only — the actual fetch happens in MainActivity, off the main
        // thread; doing network here would block app startup.
        vpnkaSeededTrialThisLaunch = MmkvManager.seedDefaultSubscriptionIfNeeded()
    }
}
