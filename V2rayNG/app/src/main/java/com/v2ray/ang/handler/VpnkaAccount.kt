package com.v2ray.ang.handler

import com.google.gson.annotations.SerializedName
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Reads the user's subscription state from the backend.
 *
 * There is no account and no login here. The token inside the subscription
 * URL the app already holds is what identifies the client — the same token
 * that authorises fetching the VPN keys — so `<sub-url>/info` needs nothing
 * else to answer.
 */
object VpnkaAccount {

    data class Info(
        @SerializedName("active") val active: Boolean = false,
        @SerializedName("expires_at") val expiresAt: String? = null,
        @SerializedName("days_left") val daysLeft: Int? = null,
        @SerializedName("tariff") val tariff: String? = null,
        @SerializedName("devices_used") val devicesUsed: Int? = null,
        @SerializedName("devices_limit") val devicesLimit: Int? = null,
        @SerializedName("balance") val balance: String? = null,
        @SerializedName("frozen") val frozen: Boolean = false,
    )

    /**
     * The user's real subscription URL, if they have one.
     *
     * The trial the app ships with is deliberately skipped: it's an
     * anonymous grant with no client behind it, so `/info` has nothing to
     * report and the screen would show "не активна" to someone who is in
     * fact connected. A user who never went through the bot genuinely has
     * no subscription to manage, and the screen should say exactly that.
     */
    private fun infoUrl(): String? {
        val sub = MmkvManager.decodeSubscriptions()
            .map { it.subscription.url }
            .firstOrNull { it.contains("/sub/") && !it.contains("/qr/") }
            ?: return null
        return sub.trimEnd('/').substringBefore('?') + "/info"
    }

    fun hasSubscription(): Boolean = infoUrl() != null

    suspend fun fetchInfo(): Info? = withContext(Dispatchers.IO) {
        val url = infoUrl() ?: return@withContext null
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        try {
            client.newCall(Request.Builder().url(url).get().build()).execute()
                .use { resp ->
                    if (!resp.isSuccessful) {
                        LogUtil.w(AppConfig.TAG, "sub info: HTTP ${resp.code}")
                        return@withContext null
                    }
                    val body = resp.body?.string() ?: return@withContext null
                    JsonUtil.fromJsonSafe(body, Info::class.java)
                }
        } catch (e: Exception) {
            // Offline is the common case here — the screen shows a retry
            // rather than an error, so a failure needs no drama.
            LogUtil.w(AppConfig.TAG, "sub info failed: ${e.message}")
            null
        }
    }
}
