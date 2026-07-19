package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.util.LogUtil

/**
 * How long this session has run, and how fast it is going right now.
 *
 * The home screen shows both. Neither existed: the app knew whether the
 * tunnel was up, not since when, and the traffic counters were only ever
 * read by the (off-by-default) speed notification.
 */
object VpnkaSession {

    private var startedAtMs: Long = 0L
    private var lastSampleAtMs: Long = 0L

    /** Seconds since the tunnel came up, or 0 when it isn't up. */
    fun elapsedSeconds(isRunning: Boolean, nowMs: Long): Long {
        if (!isRunning) {
            startedAtMs = 0L
            lastSampleAtMs = 0L
            return 0L
        }
        if (startedAtMs == 0L) startedAtMs = nowMs
        return (nowMs - startedAtMs) / 1000L
    }

    data class Speed(val downMbps: Double, val upMbps: Double)

    /**
     * Megabits per second since the previous call, or null if we can't say.
     *
     * Reading the core's counters **resets** them — that is how the speed
     * notification computes a rate — so this must be the only thing polling
     * while the screen is open. If the user has switched on the speed
     * notification (off by default), the two pollers split the same bytes
     * and both read low; that is a known trade rather than a bug to hunt.
     *
     * The first call after connecting returns null: with no previous sample
     * there is no interval to divide by, and a number derived from
     * "everything since the tunnel started" would read as a spike.
     */
    fun sampleSpeed(nowMs: Long): Speed? {
        val previous = lastSampleAtMs
        lastSampleAtMs = nowMs
        if (previous == 0L) return null

        val seconds = (nowMs - previous) / 1000.0
        if (seconds <= 0.0) return null

        var up = 0L
        var down = 0L
        try {
            CoreServiceManager.queryAllOutboundTrafficStats().forEach { stat ->
                // Proxy tags only. Direct traffic bypasses the tunnel — it is
                // the user's own connection, and counting it would show speed
                // the VPN isn't providing.
                if (stat.tag.startsWith(AppConfig.TAG_PROXY)) {
                    when (stat.direction) {
                        AppConfig.UPLINK -> up += stat.value
                        AppConfig.DOWNLINK -> down += stat.value
                    }
                }
            }
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "traffic sample failed: ${e.message}")
            return null
        }

        // Bytes → megabits.
        return Speed(
            downMbps = down * 8.0 / 1_000_000.0 / seconds,
            upMbps = up * 8.0 / 1_000_000.0 / seconds,
        )
    }
}
