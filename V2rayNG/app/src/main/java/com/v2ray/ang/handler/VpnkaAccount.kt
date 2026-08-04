package com.v2ray.ang.handler

import android.os.Build
import com.google.gson.annotations.SerializedName
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import java.net.InetSocketAddress
import java.net.Proxy
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
        // Already in roubles and already converted server-side — the app
        // does no currency maths.
        @SerializedName("balance_rub") val balanceRub: Int? = null,
        @SerializedName("frozen") val frozen: Boolean = false,
        @SerializedName("subscriptions") val subscriptions: List<Plan>? = null,
        @SerializedName("subscription_token") val subscriptionToken: String? = null,
        // Only present when there is no purchase: how much of the shipped
        // 24h trial is left. The grant is anonymous and keyed by install id,
        // so the server can only answer this when the app says which install
        // is asking.
        @SerializedName("trial_hours_left") val trialHoursLeft: Int? = null,
        // Whether a Telegram is attached. Not the same as being signed in:
        // the app registers an account on first launch, so everyone has a
        // token, and only this says whether they are in their real account.
        @SerializedName("telegram_linked") val telegramLinked: Boolean = false,
        // A free-month trial tariff is configured and live. With no active
        // paid plan, the home screen shows the «Месяц бесплатно» button.
        @SerializedName("free_month_enabled") val freeMonthEnabled: Boolean = false,
        // Whether the SmartDesk surface is unlocked for this account (set per
        // user in the admin). The home screen only shows the button when true.
        @SerializedName("smartdesk_enabled") val smartDeskEnabled: Boolean = false,
        // Expiry-reminder channel prefs + optional contact email, editable on
        // the notifications settings screen (PATCH /app/settings). Default the
        // toggles on so they read "enabled" before the profile has loaded.
        @SerializedName("notify_expiry_in_app") val notifyExpiryInApp: Boolean = true,
        @SerializedName("notify_expiry_in_telegram") val notifyExpiryInTelegram: Boolean = true,
        @SerializedName("email") val email: String? = null,
    )

    data class Plan(
        @SerializedName("group_token") val groupToken: String? = null,
        @SerializedName("tariff") val tariff: String? = null,
        @SerializedName("expires_at") val expiresAt: String? = null,
        @SerializedName("days_left") val daysLeft: Int? = null,
        @SerializedName("devices_used") val devicesUsed: Int? = null,
        @SerializedName("devices_limit") val devicesLimit: Int? = null,
        @SerializedName("frozen") val frozen: Boolean = false,
        // The free month is a trial-tariff plan; a paid plan is not.
        @SerializedName("is_trial") val isTrial: Boolean = false,
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
                Request.Builder()
                    .url("$BASE/app/auth/exchange")
                    // Tells the server which anonymous account this install
                    // was using, so signing in folds it into the real one
                    // instead of leaving an empty client behind.
                    .header("Hwid", MmkvManager.getOrCreateInstallId())
                    .post(body)
                    .build()
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
                MmkvManager.setSessionRevoked(false)
                Result.success(Unit)
            }
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "sign-in failed: ${e.message}")
            Result.failure(e)
        }
    }

    private data class RegisterResponse(
        @SerializedName("token") val token: String?,
        @SerializedName("recovery_code") val recoveryCode: String?,
    )

    /**
     * Create this install's account. Called once, on first launch.
     *
     * There is no form and nothing to choose: an account is simply what the
     * app needs in order to own a subscription, and asking someone to invent
     * credentials before they have seen the product is friction for its own
     * sake.
     *
     * The recovery code comes back exactly once — the server keeps only its
     * hash — so it is stored locally and shown to the user later, before the
     * first payment, when they finally have something to lose.
     */
    suspend fun register(): Boolean = withContext(Dispatchers.IO) {
        if (MmkvManager.getAccountToken() != null) return@withContext true
        // A revoked session means an account already exists and someone
        // decided this device should not reach it. Making a new one hides
        // that decision and loses the account; the profile screen asks the
        // user to sign in again instead.
        if (MmkvManager.wasSessionRevoked()) return@withContext false
        val body = JsonUtil.toJson(mapOf("label" to deviceLabel()))
            .toRequestBody("application/json".toMediaType())
        try {
            http().newCall(
                Request.Builder()
                    .url("$BASE/app/auth/register")
                    // Same install id the subscription fetch already sends.
                    // Recorded server-side for abuse accounting only — it is
                    // never what authorises a request.
                    .header("Hwid", MmkvManager.getOrCreateInstallId())
                    .post(body)
                    .build()
            ).execute().use { resp ->
                if (!resp.isSuccessful) {
                    LogUtil.w(AppConfig.TAG, "register: HTTP ${resp.code}")
                    return@withContext false
                }
                val parsed = JsonUtil.fromJsonSafe(
                    resp.body?.string().orEmpty(), RegisterResponse::class.java
                ) ?: return@withContext false
                val token = parsed.token ?: return@withContext false
                MmkvManager.setAccountToken(token)
                MmkvManager.setRecoveryCode(parsed.recoveryCode)
                true
            }
        } catch (e: Exception) {
            // Offline on first launch is ordinary. The trial subscription
            // still works, and registration is retried on the next launch.
            LogUtil.w(AppConfig.TAG, "register failed: ${e.message}")
            false
        }
    }

    /** Trade a recovery code for a session on this device. */
    suspend fun recover(code: String): Result<Unit> = withContext(Dispatchers.IO) {
        val body = JsonUtil.toJson(
            mapOf("recovery_code" to code, "label" to deviceLabel())
        ).toRequestBody("application/json".toMediaType())
        try {
            http().newCall(
                Request.Builder().url("$BASE/app/auth/recover").post(body).build()
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
                        IllegalStateException("no token")
                    )
                MmkvManager.setAccountToken(token)
                MmkvManager.setSessionRevoked(false)
                Result.success(Unit)
            }
        } catch (e: Exception) {
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
                    // Identifies which install's trial to report on.
                    .header("Hwid", MmkvManager.getOrCreateInstallId())
                    .get()
                    .build()
            ).execute().use { resp ->
                if (resp.code == 401) {
                    LogUtil.w(AppConfig.TAG, "profile: session revoked")
                    MmkvManager.setAccountToken(null)
                    // Remember that this was a revocation, not a fresh
                    // install. Without it the next launch quietly registers
                    // a brand-new empty account and the user is left staring
                    // at «подписка не активна» while their real one — with
                    // the subscriptions and the balance — still exists.
                    MmkvManager.setSessionRevoked(true)
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

    data class SupportMessage(
        @SerializedName("id") val id: Long = 0,
        @SerializedName("from_me") val fromMe: Boolean = false,
        @SerializedName("body") val body: String = "",
        // ISO-8601 UTC; the UI shows a date header and a per-message time.
        @SerializedName("created_at") val createdAt: String = "",
        // Opaque ref of an attached screenshot, or null. Fetched via
        // fetchSupportImage(); the bubble shows it inline.
        @SerializedName("attachment") val attachment: String? = null,
    )

    /** One past conversation, as the history list shows it. */
    data class SupportTicket(
        @SerializedName("id") val id: Long = 0,
        @SerializedName("subject") val subject: String = "",
        @SerializedName("status") val status: String = "",
        @SerializedName("created_at") val createdAt: String = "",
    )

    private data class TicketList(
        @SerializedName("tickets") val tickets: List<SupportTicket>? = null,
    )

    private data class SupportThread(
        @SerializedName("ticket_id") val ticketId: Long? = null,
        @SerializedName("messages") val messages: List<SupportMessage>? = null,
    )

    data class Notice(
        @SerializedName("id") val id: Long = 0,
        @SerializedName("kind") val kind: String = "",
        @SerializedName("body") val body: String = "",
        @SerializedName("read") val read: Boolean = false,
    )

    private data class Inbox(
        @SerializedName("unread") val unread: Int = 0,
        @SerializedName("items") val items: List<Notice>? = null,
    )

    private data class UrlResponse(@SerializedName("url") val url: String?)

    private fun authed(path: String): Request.Builder? {
        val token = MmkvManager.getAccountToken() ?: return null
        return Request.Builder()
            .url("$BASE$path")
            .header("Authorization", "Bearer $token")
    }

    private inline fun <reified T> call(builder: Request.Builder?): T? {
        val req = builder?.build() ?: return null
        return try {
            http().newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    LogUtil.w(AppConfig.TAG, "${req.url}: HTTP ${resp.code}")
                    return null
                }
                JsonUtil.fromJsonSafe(resp.body?.string().orEmpty(), T::class.java)
            }
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "${req.url} failed: ${e.message}")
            null
        }
    }

    /**
     * Whether the SmartDesk backend is reachable right now — drives the
     * red/green dot on the menu button.
     *
     * All SmartDesk network must go through our VPN, so this ping is sent via
     * xray's local HTTP proxy (127.0.0.1:httpPort), NOT the clear net the rest
     * of the app uses. With the VPN off that port isn't listening, the ping
     * fails, and the dot is red — which is the honest state: SmartDesk has no
     * network without the VPN. True only on a clean 200.
     */
    suspend fun smartDeskOnline(): Boolean = withContext(Dispatchers.IO) {
        val req = authed("/app/smartdesk/ping")?.get()?.build() ?: return@withContext false
        // SOCKS на socksPort: на Xray только SOCKS-inbound; HTTP-прокси на этот
        // порт (getHttpPort==getSocksPort) не работает. См. Vault.http().
        val socksPort = SettingsManager.getSocksPort()
        val client = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort)))
            .build()
        try {
            client.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "smartdesk ping failed: ${e.message}")
            false
        }
    }

    suspend fun fetchSupport(): List<SupportMessage> = withContext(Dispatchers.IO) {
        call<SupportThread>(authed("/app/support")?.get())?.messages.orEmpty()
    }

    suspend fun sendSupport(text: String): Boolean = withContext(Dispatchers.IO) {
        val body = JsonUtil.toJson(mapOf("text" to text))
            .toRequestBody("application/json".toMediaType())
        call<SupportThread>(authed("/app/support")?.post(body)) != null
    }

    /** Attach a screenshot to the conversation. Returns true on 2xx. */
    suspend fun sendSupportImage(
        bytes: ByteArray, mime: String, caption: String = "",
    ): Boolean = withContext(Dispatchers.IO) {
        val ext = when (mime) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val part = okhttp3.MultipartBody.Builder()
            .setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart(
                "file", "screenshot.$ext", bytes.toRequestBody(mime.toMediaType()),
            )
            .addFormDataPart("text", caption)
            .build()
        val req = authed("/app/support/image")?.post(part)?.build()
            ?: return@withContext false
        try {
            http().newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "support image failed: ${e.message}")
            false
        }
    }

    /** Download an attached screenshot's bytes (authed), or null. */
    suspend fun fetchSupportImage(ref: String): ByteArray? =
        withContext(Dispatchers.IO) {
            val req = authed("/app/support/attachment/$ref")?.get()?.build()
                ?: return@withContext null
            try {
                http().newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.bytes() else null
                }
            } catch (e: Exception) {
                LogUtil.w(AppConfig.TAG, "fetch image failed: ${e.message}")
                null
            }
        }

    /** Save the expiry-reminder channel prefs + contact email. Empty email
     *  clears it server-side. Returns true on 2xx. */
    suspend fun saveSettings(
        inApp: Boolean, inTelegram: Boolean, email: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val body = JsonUtil.toJson(
            mapOf(
                "notify_expiry_in_app" to inApp,
                "notify_expiry_in_telegram" to inTelegram,
                "email" to email,
            )
        ).toRequestBody("application/json".toMediaType())
        val req = authed("/app/settings")?.method("PATCH", body)?.build()
            ?: return@withContext false
        try {
            http().newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "settings save failed: ${e.message}")
            false
        }
    }

    suspend fun fetchTickets(): List<SupportTicket> = withContext(Dispatchers.IO) {
        call<TicketList>(authed("/app/support/tickets")?.get())?.tickets.orEmpty()
    }

    suspend fun fetchTicket(id: Long): List<SupportMessage> = withContext(Dispatchers.IO) {
        call<SupportThread>(authed("/app/support/tickets/$id")?.get())?.messages.orEmpty()
    }

    suspend fun fetchNotices(): List<Notice> = withContext(Dispatchers.IO) {
        call<Inbox>(authed("/app/notifications")?.get())?.items.orEmpty()
    }

    suspend fun markNoticesRead() = withContext(Dispatchers.IO) {
        val req = authed("/app/notifications/read")
            ?.post(ByteArray(0).toRequestBody())?.build() ?: return@withContext
        try {
            http().newCall(req).execute().close()
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "mark read failed: ${e.message}")
        }
    }

    data class Device(
        @SerializedName("id") val id: Long = 0,
        @SerializedName("label") val label: String = "",
        @SerializedName("last_seen_at") val lastSeenAt: String? = null,
    )

    private data class Devices(@SerializedName("devices") val devices: List<Device>?)

    suspend fun fetchDevices(groupToken: String): List<Device> =
        withContext(Dispatchers.IO) {
            call<Devices>(
                authed("/app/subscriptions/$groupToken/devices")?.get()
            )?.devices.orEmpty()
        }

    /** Free a device slot. True when the server confirmed it. */
    suspend fun revokeDevice(groupToken: String, deviceId: Long): Boolean =
        withContext(Dispatchers.IO) {
            val req = authed("/app/subscriptions/$groupToken/devices/$deviceId/revoke")
                ?.post(ByteArray(0).toRequestBody())?.build() ?: return@withContext false
            try {
                http().newCall(req).execute().use { it.isSuccessful }
            } catch (e: Exception) {
                LogUtil.w(AppConfig.TAG, "revoke failed: ${e.message}")
                false
            }
        }

    /** Give a device a human name (blank clears it). True when saved. */
    suspend fun renameDevice(
        groupToken: String, deviceId: Long, label: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val body = JsonUtil.toJson(mapOf("label" to label))
            .toRequestBody("application/json".toMediaType())
        val req = authed("/app/subscriptions/$groupToken/devices/$deviceId")
            ?.method("PATCH", body)?.build() ?: return@withContext false
        try {
            http().newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "rename device failed: ${e.message}")
            false
        }
    }

    /** The URL this plan's config lives at — what the QR encodes. */
    fun subscriptionUrl(groupToken: String): String = "$BASE/sub/g/$groupToken"

    // --- in-app shop ------------------------------------------------------

    data class Tariff(
        @SerializedName("id") val id: Long = 0,
        @SerializedName("name") val name: String = "",
        @SerializedName("description") val description: String? = null,
        @SerializedName("duration_days") val durationDays: Int = 0,
        @SerializedName("device_limit") val deviceLimit: Int? = null,
        @SerializedName("traffic_limit_gb") val trafficLimitGb: Int? = null,
        @SerializedName("price_rub") val priceRub: Int = 0,
        // Struck-through original when a friend discount is live.
        @SerializedName("price_rub_full") val priceRubFull: Int? = null,
        @SerializedName("can_pay_balance") val canPayBalance: Boolean = false,
        @SerializedName("can_pay_card") val canPayCard: Boolean = false,
    )

    private data class TariffList(
        @SerializedName("tariffs") val tariffs: List<Tariff>?,
    )

    suspend fun fetchTariffs(): List<Tariff> = withContext(Dispatchers.IO) {
        call<TariffList>(authed("/app/tariffs")?.get())?.tariffs.orEmpty()
    }

    /** Outcome of a purchase attempt — settled from balance, a card URL to
     *  open, or a message to show. */
    sealed class PurchaseResult {
        data class Settled(val subscriptionUrl: String?) : PurchaseResult()
        data class PayByCard(val url: String) : PurchaseResult()
        data class Failed(val message: String) : PurchaseResult()
    }

    private data class PurchaseResponse(
        @SerializedName("settled") val settled: Boolean = false,
        @SerializedName("subscription_url") val subscriptionUrl: String? = null,
        @SerializedName("payment_url") val paymentUrl: String? = null,
    )

    private data class TopUpResponse(
        @SerializedName("payment_url") val paymentUrl: String? = null,
    )

    /** Outcome of claiming the free month in the app. */
    sealed class FreeMonthResult {
        data class Issued(val days: Int) : FreeMonthResult()
        data class Already(val days: Int?) : FreeMonthResult()
        object Failed : FreeMonthResult()
    }

    private data class FreeMonthResponse(
        @SerializedName("status") val status: String = "",
        @SerializedName("days") val days: Int? = null,
    )

    /** Claim the free month — same trial the bot issues. */
    suspend fun claimFreeMonth(): FreeMonthResult = withContext(Dispatchers.IO) {
        val req = authed("/app/free-month")
            ?.post(ByteArray(0).toRequestBody())?.build()
            ?: return@withContext FreeMonthResult.Failed
        try {
            http().newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use FreeMonthResult.Failed
                val obj = JsonUtil.fromJsonSafe(
                    resp.body?.string().orEmpty(), FreeMonthResponse::class.java
                )
                when (obj?.status) {
                    "issued" -> FreeMonthResult.Issued(obj.days ?: 30)
                    "already" -> FreeMonthResult.Already(obj.days)
                    else -> FreeMonthResult.Failed
                }
            }
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "free month failed: ${e.message}")
            FreeMonthResult.Failed
        }
    }

    /** Start a balance top-up; returns the RuKassa URL to open, or null. */
    suspend fun topUp(amountRub: Int): String? = withContext(Dispatchers.IO) {
        val body = JsonUtil.toJson(mapOf("amount_rub" to amountRub))
            .toRequestBody("application/json".toMediaType())
        val req = authed("/app/topup")?.post(body)?.build()
            ?: return@withContext null
        try {
            http().newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                JsonUtil.fromJsonSafe(
                    resp.body?.string().orEmpty(), TopUpResponse::class.java
                )?.paymentUrl
            }
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "topup failed: ${e.message}")
            null
        }
    }

    suspend fun purchase(tariffId: Long, method: String): PurchaseResult =
        withContext(Dispatchers.IO) {
            val body = JsonUtil.toJson(
                mapOf("tariff_id" to tariffId, "method" to method)
            ).toRequestBody("application/json".toMediaType())
            val req = authed("/app/purchase")?.post(body)?.build()
                ?: return@withContext PurchaseResult.Failed("Нужен вход в аккаунт")
            try {
                http().newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    when {
                        resp.isSuccessful -> {
                            val obj = JsonUtil.fromJsonSafe(
                                text, PurchaseResponse::class.java
                            )
                            when {
                                obj?.settled == true ->
                                    PurchaseResult.Settled(obj.subscriptionUrl)
                                obj?.paymentUrl != null ->
                                    PurchaseResult.PayByCard(obj.paymentUrl)
                                else ->
                                    PurchaseResult.Failed("Не удалось оформить покупку")
                            }
                        }
                        resp.code == 402 ->
                            PurchaseResult.Failed("Недостаточно средств на балансе")
                        resp.code == 503 ->
                            PurchaseResult.Failed("Платёжная система недоступна, попробуйте позже")
                        else ->
                            PurchaseResult.Failed("Оплата недоступна (код ${resp.code})")
                    }
                }
            } catch (e: Exception) {
                LogUtil.w(AppConfig.TAG, "purchase failed: ${e.message}")
                PurchaseResult.Failed("Нет связи с сервером")
            }
        }


    /**
     * A Telegram link that attaches this account to the user's Telegram.
     *
     * The whole flow is one tap: the user cannot be expected to know the
     * bot's name, let alone find it by search and paste a code into it.
     */
    suspend fun telegramLinkUrl(): String? = withContext(Dispatchers.IO) {
        call<UrlResponse>(
            authed("/app/auth/telegram-link")?.post(ByteArray(0).toRequestBody())
        )?.url
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
        // Drop the account's imported subscriptions and servers too, or the
        // connect screen keeps presenting the paid interface after logout.
        MmkvManager.clearVpnkaSubscriptions()
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
