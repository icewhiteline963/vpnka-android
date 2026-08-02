package com.v2ray.ang.handler

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Keep Android from putting the tunnel to sleep.
 *
 * Doze and app-standby suspend background work aggressively, and a VPN that
 * gets suspended looks exactly like a broken VPN: the connection is "on" but
 * traffic stops, usually minutes after the screen goes off. Users report it
 * as "приложение засыпает" and blame the servers.
 *
 * Being on the battery-optimization exemption list is what stops that. We
 * can only ask — the user grants it in a system dialog.
 */
object PowerSaveHelper {

    private const val KEY_ASKED = "vpnka_battery_exemption_asked"

    /** True when Android has agreed to leave us running in the background. */
    fun isExempt(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Ask on the first launch that isn't exempt, then never unprompted again.
     *
     * A permission prompt that reappears every launch trains people to
     * dismiss it without reading, which costs us the one time it matters.
     * The settings screen keeps a row for it, so someone who declined and
     * later hits the disconnects has somewhere to go.
     */
    fun shouldPrompt(context: Context): Boolean {
        if (isExempt(context)) return false
        return !MmkvManager.decodeSettingsBool(KEY_ASKED, false)
    }

    fun markPrompted() {
        MmkvManager.encodeSettings(KEY_ASKED, true)
    }

    /**
     * Open the exemption request.
     *
     * Prefers the one-tap system dialog. Some vendor ROMs don't implement
     * that action, so fall back to the battery-optimization list, where the
     * user can find us manually — worse, but not a dead end.
     */
    fun openExemptionRequest(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

        val candidates = mutableListOf<Intent>()
        if (!isExempt(context)) {
            // Not exempt yet: the one-tap grant dialog.
            candidates += Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}"),
            )
        }
        // If we're already exempt the request dialog just flashes and closes
        // with nothing to grant — which is the "button does nothing" report.
        // Send the user somewhere they can actually see the state: the battery
        // list, then the app's own settings as a last resort (always resolves).
        candidates += Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        candidates += Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        )

        for (intent in candidates) {
            // A settings screen belongs in its own task; without this flag the
            // call silently no-ops when the caller isn't an Activity — the
            // other half of the dead-button report.
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(intent) }.isSuccess) return
        }
    }
}
