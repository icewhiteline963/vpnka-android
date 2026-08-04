package com.v2ray.ang.handler

import com.google.gson.Gson
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
 * Public broadcast channels. Unlike 1-to-1 chat these are not E2E — a channel
 * is a public feed. All traffic still goes through the VPN proxy.
 */
object Channels {

    private const val BASE = "https://get.vpnka.io"
    private val gson = Gson()

    data class Channel(
        val id: Long = 0,
        val handle: String = "",
        val title: String = "",
        @SerializedName("is_owner") val isOwner: Boolean = false,
        val subscribed: Boolean = false,
    )
    data class Post(val id: Long = 0, val body: String = "")
    private data class Feed(val cursor: Long = 0, val posts: List<Post> = emptyList())

    private fun http(): OkHttpClient {
        // Напрямую, как аккаунт-API (без прокси). См. Vault.http().
        return OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private fun authed(path: String): Request.Builder? {
        val token = MmkvManager.getAccountToken() ?: return null
        return Request.Builder().url("$BASE$path").header("Authorization", "Bearer $token")
    }

    private inline fun <reified T> get(path: String, default: T): T = try {
        val req = authed(path)?.get()?.build() ?: return default
        http().newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) default
            else gson.fromJson(resp.body?.string().orEmpty(), T::class.java) ?: default
        }
    } catch (e: Exception) { LogUtil.w(AppConfig.TAG, "channels get $path: ${e.message}"); default }

    private fun postJson(path: String, json: String): Boolean = try {
        val req = authed(path)?.post(json.toRequestBody("application/json".toMediaType()))?.build()
            ?: return false
        http().newCall(req).execute().use { it.isSuccessful }
    } catch (e: Exception) { LogUtil.w(AppConfig.TAG, "channels post $path: ${e.message}"); false }

    suspend fun search(q: String): List<Channel> = withContext(Dispatchers.IO) {
        if (q.trim().length < 2) return@withContext emptyList()
        get("/app/channels/search?q=" + android.net.Uri.encode(q.trim()), emptyArray<Channel>()).toList()
    }

    suspend fun mine(): List<Channel> = withContext(Dispatchers.IO) {
        get("/app/channels/mine", emptyArray<Channel>()).toList()
    }

    suspend fun create(handle: String, title: String): Channel? = withContext(Dispatchers.IO) {
        try {
            val body = gson.toJson(mapOf("handle" to handle, "title" to title))
            val req = authed("/app/channels")?.post(body.toRequestBody("application/json".toMediaType()))?.build()
                ?: return@withContext null
            http().newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null
                else gson.fromJson(resp.body?.string().orEmpty(), Channel::class.java)
            }
        } catch (e: Exception) { null }
    }

    suspend fun subscribe(id: Long): Boolean = withContext(Dispatchers.IO) {
        postJson("/app/channels/$id/subscribe", "{}")
    }

    suspend fun post(id: Long, body: String): Boolean = withContext(Dispatchers.IO) {
        postJson("/app/channels/$id/post", gson.toJson(mapOf("body" to body)))
    }

    suspend fun feed(id: Long, since: Long = 0): List<Post> = withContext(Dispatchers.IO) {
        get("/app/channels/$id/feed?since=$since", Feed()).posts
    }
}
