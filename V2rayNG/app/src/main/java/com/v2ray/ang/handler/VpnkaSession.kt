package com.v2ray.ang.handler

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.MessageUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * How much this session has carried, published across the process boundary.
 *
 * The core runs in `:RunSoLibV2RayDaemon`. Its traffic counters exist only
 * in that process, so the activity — which lives in the main one — read
 * nothing and the cards sat at zero no matter how much data moved. That was
 * the whole bug: the config was right, the UI was right, and they were in
 * different processes.
 *
 * So the polling happens here, in the core's process, and the totals are
 * broadcast to the UI.
 *
 * Totals rather than a rate: a rate is honestly zero whenever nobody is
 * downloading, which reads as a broken counter on a screen people open to
 * check the VPN is working.
 */
object VpnkaSession {

    private const val TICK_MS = 1000L

    private var job: Job? = null
    private var downTotal = 0L
    private var upTotal = 0L
    private var startedAtMs = 0L

    /** Called from the core's process when the tunnel comes up. */
    fun start(context: Context) {
        if (job != null) return
        downTotal = 0L
        upTotal = 0L
        startedAtMs = System.currentTimeMillis()

        job = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                // Self-guard: if the core stopped by a path that forgot to
                // call stop(), the timer must not keep counting a session
                // that has ended.
                if (!CoreServiceManager.isRunning()) {
                    stop()
                    return@launch
                }
                accumulate()
                MessageUtil.sendMsg2UI(
                    context,
                    AppConfig.MSG_TRAFFIC_TOTALS,
                    // Serializable payload: down, up, seconds. A longArray
                    // rather than a data class so nothing has to stay in
                    // step across the two processes.
                    longArrayOf(
                        downTotal,
                        upTotal,
                        (System.currentTimeMillis() - startedAtMs) / 1000L,
                    ),
                )
                delay(TICK_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        downTotal = 0L
        upTotal = 0L
        startedAtMs = 0L
    }

    /**
     * Add what moved since the last read.
     *
     * Reading the counters resets them, so each call returns a delta and the
     * running total has to be kept here. This is also why only one thing may
     * poll: with the speed notification switched on (it is off by default)
     * the two split the same bytes.
     */
    private fun accumulate() {
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
    }
}
