package com.v2ray.ang.handler

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * Two-way sync between the local SmartDesk container and our server.
 *
 * Runs ONLY through the VPN: the request goes via xray's local HTTP proxy
 * (127.0.0.1:httpPort), so with the VPN off it simply fails and the work stays
 * queued in the encrypted container until next time. Pushes the pending
 * changes, pulls everything new since our cursor, applies it locally, advances
 * the cursor and clears the queue on success.
 */
object SmartDeskSync {

    private const val BASE = "https://get.vpnka.io"
    private val gson = Gson()

    private data class ChangeOut(
        val kind: String,
        val id: String,
        @SerializedName("updated_at_ms") val updatedAtMs: Long,
        val deleted: Boolean,
        val payload: JsonElement,
    )

    private data class SyncRequest(
        val since: Long,
        val changes: List<ChangeOut>,
    )

    private data class ItemIn(
        val kind: String = "",
        val id: String = "",
        val deleted: Boolean = false,
        val payload: JsonElement? = null,
    )

    private data class SyncResponse(
        val cursor: Long = 0,
        val items: List<ItemIn> = emptyList(),
    )

    /** @return true on a clean sync, false if offline / not authorised / error. */
    suspend fun sync(): Boolean = withContext(Dispatchers.IO) {
        val token = MmkvManager.getAccountToken() ?: return@withContext false

        val changes = SmartDeskStore.pending().map { c ->
            ChangeOut(
                kind = c.kind,
                id = c.id,
                updatedAtMs = c.updatedAtMs,
                deleted = c.deleted,
                payload = runCatching { JsonParser.parseString(c.payloadJson) }
                    .getOrElse { JsonParser.parseString("{}") },
            )
        }
        val reqBody = gson.toJson(SyncRequest(since = SmartDeskStore.cursor(), changes = changes))

        val httpPort = SettingsManager.getHttpPort()
        val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", httpPort)))
            .build()

        val request = Request.Builder()
            .url("$BASE/app/smartdesk/sync")
            .header("Authorization", "Bearer $token")
            .post(reqBody.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    LogUtil.w(AppConfig.TAG, "smartdesk sync: HTTP ${resp.code}")
                    return@withContext false
                }
                val parsed = gson.fromJson(resp.body?.string().orEmpty(), SyncResponse::class.java)
                    ?: return@withContext false
                // Apply the server's view, then advance the cursor and drop the
                // queue we just uploaded. Order matters: only clear pending
                // after a confirmed 200, or an offline attempt would lose edits.
                for (item in parsed.items) {
                    SmartDeskStore.applyRemote(
                        kind = item.kind,
                        id = item.id,
                        deleted = item.deleted,
                        payloadJson = item.payload?.toString() ?: "{}",
                    )
                }
                SmartDeskStore.setCursor(parsed.cursor)
                SmartDeskStore.clearPending()
                true
            }
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "smartdesk sync failed: ${e.message}")
            false
        }
    }
}
