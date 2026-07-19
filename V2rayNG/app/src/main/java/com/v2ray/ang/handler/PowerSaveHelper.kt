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
        val direct = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        )
        val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        try {
            context.startActivity(direct)
        } catch (_: Exception) {
            runCatching { context.startActivity(fallback) }
        }
    }
}
