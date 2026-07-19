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

    data class Traffic(val downBytes: Long, val upBytes: Long)

    private var downTotal = 0L
    private var upTotal = 0L

    /**
     * Bytes carried by the tunnel this session, both ways.
     *
     * Accumulated rather than read: querying the core's counters **resets**
     * them, so each call returns only what moved since the last one and the
     * running total has to be kept here.
     *
     * Totals rather than a rate, because a rate is honestly zero whenever
     * the user isn't actively downloading — which reads as a broken counter
     * on a screen someone opens to check that the VPN is working. Bytes only
     * ever go up.
     */
    fun sampleTraffic(nowMs: Long, isRunning: Boolean): Traffic {
        if (!isRunning) {
            downTotal = 0L
            upTotal = 0L
            lastSampleAtMs = 0L
            return Traffic(0L, 0L)
        }
        lastSampleAtMs = nowMs

        try {
            CoreServiceManager.queryAllOutboundTrafficStats().forEach { stat ->
                // Proxy tags only. Direct traffic bypasses the tunnel — it is
                // the user's own connection, and counting it would credit the
                // VPN with data it never carried.
                if (stat.tag.startsWith(AppConfig.TAG_PROXY)) {
                    when (stat.direction) {
                        AppConfig.UPLINK -> upTotal += stat.value
                        AppConfig.DOWNLINK -> downTotal += stat.value
                    }
                }
            }
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "traffic sample failed: ${e.message}")
        }
        return Traffic(downTotal, upTotal)
    }
}
