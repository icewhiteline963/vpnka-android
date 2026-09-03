package com.v2ray.ang.handler

import com.google.gson.annotations.SerializedName
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.JsonUtil
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Which node the «🌍 Авто» balancer is exiting through right now.
 *
 * The balancer chooses the exit inside the xray core, per connection — the
 * app never sees that choice from the config. So we ask from the far side: a
 * request that rides the tunnel reaches /whoami from the node's egress IP,
 * which the server maps back to a country.
 *
 * The subtlety this handler exists for: the app excludes itself from its own
 * VPN (CoreVpnService.configurePerAppProxy → addDisallowedApplication(self)),
 * so a plain request would leave the phone outside the tunnel and read the
 * real IP. We send it through xray's local HTTP inbound (LOOPBACK:httpPort)
 * instead, exactly like the in-app browser and YouTube do, so it exits via
 * whichever node the balancer is favouring this second.
 */
object VpnkaExit {

    private const val BASE = "https://get.vpnka.io"

    data class Exit(
        @SerializedName("on_vpn") val onVpn: Boolean = false,
        @SerializedName("flag") val flag: String? = null,
        @SerializedName("name") val name: String? = null,
        @SerializedName("code") val code: String? = null,
    )

    /**
     * Клиент на порт, а не на вызов.
     *
     * Строился заново на каждый опрос — шесть штук в минуту, и каждый
     * уносил свой пул, державший простаивающие соединения пять минут.
     * Порт локального прокси меняется при перезапуске ядра, поэтому
     * держим пару «порт → клиент», а не один на всё время жизни.
     */
    @Volatile
    private var cached: Pair<Int, OkHttpClient>? = null

    @Synchronized
    private fun clientFor(port: Int): OkHttpClient {
        cached?.let { (p, c) ->
            if (p == port) return c
            // Порт сменился — старый клиент гасим, иначе его пул держит
            // соединения на прежний, уже мёртвый прокси ещё пять минут.
            c.dispatcher.executorService.shutdown()
            c.connectionPool.evictAll()
        }
        val built = OkHttpClient.Builder()
            .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(AppConfig.LOOPBACK, port)))
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
        cached = port to built
        return built
    }

    /**
     * The current exit, or null when it can't be determined (VPN down, local
     * proxy not up yet, or the probe timed out). Callers should keep the last
     * good value rather than flicker to "unknown" on a single miss.
     */
    suspend fun current(): Exit? = withContext(Dispatchers.IO) {
        val port = SettingsManager.getHttpPort()
        if (port == 0) return@withContext null
        val client = clientFor(port)
        runCatching {
            client.newCall(Request.Builder().url("$BASE/whoami").build()).execute()
                .use { resp ->
                    val body = resp.body?.string().orEmpty()
                    JsonUtil.fromJsonSafe(body, Exit::class.java)
                }
        }.getOrNull()
    }
}
