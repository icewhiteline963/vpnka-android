package com.v2ray.ang.handler

import android.os.Build
import com.google.gson.annotations.SerializedName
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * The user's account, as this install sees it.
 *
 * Signing in is a trade: the bot shows a six-character code, the app hands
 * that code to the backend and gets back a token belonging to this install
 * alone. The token is what every later request carries.
 *
 * That indirection is the point. Before it, the app identified its user by
 * the token inside the subscription URL — which meant a lost phone could
 * only be dealt with by rotating that URL for every device the person
 * owned. A per-install token can be cut off on its own, from the bot,
 * without disturbing anything else.
 */
object VpnkaAccount {

    private const val BASE = "https://get.vpnka.io"

    data class Info(
        @SerializedName("active") val active: Boolean = false,
        @SerializedName("expires_at") val expiresAt: String? = null,
        @SerializedName("days_left") val daysLeft: Int? = null,
        @SerializedName("tariff") val tariff: String? = null,
        @SerializedName("devices_used") val devicesUsed: Int? = null,
        @SerializedName("devices_limit") val devicesLimit: Int? = null,
        @SerializedName("traffic_used_bytes") val trafficUsedBytes: Long? = null,
        @SerializedName("balance") val balance: String? = null,
        @SerializedName("frozen") val frozen: Boolean = false,
        @SerializedName("subscriptions") val subscriptions: List<Plan>? = null,
        @SerializedName("subscription_token") val subscriptionToken: String? = null,
    )

    data class Plan(
        @SerializedName("tariff") val tariff: String? = null,
        @SerializedName("expires_at") val expiresAt: String? = null,
        @SerializedName("days_left") val daysLeft: Int? = null,
        @SerializedName("devices_used") val devicesUsed: Int? = null,
        @SerializedName("devices_limit") val devicesLimit: Int? = null,
        @SerializedName("frozen") val frozen: Boolean = false,
    )

    private data class TokenResponse(@SerializedName("token") val token: String?)

    private fun http() = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun isSignedIn(): Boolean = MmkvManager.getAccountToken() != null

    fun signOutLocally() = MmkvManager.setAccountToken(null)

    /**
     * What the bot shows next to this device in «мои устройства».
     *
     * The phone's own name would be friendlier, but reading it needs a
     * permission we don't otherwise want, so the model is what we send.
     * It's enough to tell two phones apart, which is all the label has to
     * do.
     */
    private fun deviceLabel(): String =
        listOf(Build.MANUFACTURER, Build.MODEL)
            .filter { !it.isNullOrBlank() }
            .joinToString(" ")
            .take(64)
            .ifBlank { "Android" }

    /** Trade a one-time code for this device's token. */
    suspend fun signIn(code: String): Result<Unit> = withContext(Dispatchers.IO) {
        val body = JsonUtil.toJson(
            mapOf("code" to code.trim().uppercase(), "label" to deviceLabel())
        ).toRequestBody("application/json".toMediaType())
        try {
            http().newCall(
                Request.Builder().url("$BASE/app/auth/exchange").post(body).build()
            ).execute().use { resp ->
                if (resp.code == 400) {
                    return@withContext Result.failure(InvalidCodeException())
                }
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("HTTP ${resp.code}")
                    )
                }
                val token = JsonUtil
                    .fromJsonSafe(resp.body?.string().orEmpty(), TokenResponse::class.java)
                    ?.token
                    ?: return@withContext Result.failure(
                        IllegalStateException("no token in response")
                    )
                MmkvManager.setAccountToken(token)
                Result.success(Unit)
            }
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "sign-in failed: ${e.message}")
            Result.failure(e)
        }
    }

    /** Wrong, expired and already-used codes all arrive as this. */
    class InvalidCodeException : Exception()

    /**
     * Read the profile.
     *
     * A 401 means the session is gone — either the user revoked this device
     * from the bot or the account was deleted. Either way the stored token
     * is now worthless, so it's dropped and the app falls back to the
     * sign-in screen rather than retrying forever against a dead session.
     */
    suspend fun fetchInfo(): Info? = withContext(Dispatchers.IO) {
        val token = MmkvManager.getAccountToken() ?: return@withContext null
        try {
            http().newCall(
                Request.Builder()
                    .url("$BASE/app/profile")
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()
            ).execute().use { resp ->
                if (resp.code == 401) {
                    LogUtil.w(AppConfig.TAG, "profile: session revoked")
                    MmkvManager.setAccountToken(null)
                    return@withContext null
                }
                if (!resp.isSuccessful) {
                    LogUtil.w(AppConfig.TAG, "profile: HTTP ${resp.code}")
                    return@withContext null
                }
                JsonUtil.fromJsonSafe(
                    resp.body?.string().orEmpty(), Info::class.java
                )
            }
        } catch (e: Exception) {
            // Offline is the common case; the screen offers a retry rather
            // than an error, so this needs no drama.
            LogUtil.w(AppConfig.TAG, "profile failed: ${e.message}")
            null
        }
    }

    /**
     * Tell the backend to forget this device, then forget it locally.
     *
     * The local drop happens either way: a user who taps «выйти» while
     * offline must still end up signed out, and a session they can no
     * longer reach is one they can revoke from the bot.
     */
    suspend fun signOut() = withContext(Dispatchers.IO) {
        val token = MmkvManager.getAccountToken()
        MmkvManager.setAccountToken(null)
        if (token == null) return@withContext
        try {
            http().newCall(
                Request.Builder()
                    .url("$BASE/app/auth/logout")
                    .header("Authorization", "Bearer $token")
                    .post(ByteArray(0).toRequestBody())
                    .build()
            ).execute().close()
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "sign-out failed: ${e.message}")
        }
    }
}
