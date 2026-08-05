package com.v2ray.ang.handler

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.net.InetSocketAddress
import java.net.Proxy
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-end encrypted messenger, client side.
 *
 * The server is a blind relay (stores ciphertext only). Each device holds an
 * RSA keypair; a contact's public key travels in their invite code. A message
 * is sealed with a fresh AES-GCM key, and that key is RSA-OAEP-wrapped to the
 * recipient's public key — so only the recipient can open it. The sender keeps
 * its own plaintext locally (it never comes back from the server).
 *
 * All network goes through xray's local proxy, so it only works over our VPN.
 */
object Messenger {

    private const val ID_STORE = "vpnka_messenger"
    private const val KEY_CRYPT = "vpnka_messenger_cryptkey"
    private const val KEY_MYID = "my_client_id"
    private const val KEY_HANDLE = "my_handle"
    private const val KEY_CONTACTS = "contacts"
    private const val KEY_CURSOR = "cursor"
    private const val BASE = "https://get.vpnka.io"

    private val gson = Gson()
    private val b64f = Base64.NO_WRAP

    // --- storage ---

    private fun cryptKey(): String {
        MmkvManager.decodeSettingsString(KEY_CRYPT)?.let { if (it.isNotBlank()) return it }
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val key = bytes.joinToString("") { "%02x".format(it) }
        MmkvManager.encodeSettings(KEY_CRYPT, key)
        return key
    }

    private val store: MMKV by lazy { MMKV.mmkvWithID(ID_STORE, MMKV.SINGLE_PROCESS_MODE, cryptKey()) }

    data class Contact(val id: Long, val name: String, val pubKey: String)
    data class Msg(
        val id: Long, val mine: Boolean, val text: String, val ts: Long,
        val u: String = "", val k: String = "text", val img: String = "",
    )

    // --- keys: the RSA identity now lives in the VAULT, shared across devices
    //     (phone + web speak as the same @handle). `ensureIdentity()` loads and
    //     caches it; the messenger no longer keeps a keypair of its own. ---

    @Volatile private var identity: Vault.Identity? = null

    /** Load (once) the shared RSA identity from the vault. Needs MK unlocked. */
    suspend fun ensureIdentity(): Boolean {
        if (identity != null) return true
        identity = Vault.messengerIdentity()
        return identity != null
    }

    private fun privateKey(): PrivateKey? {
        val id = identity ?: return null
        return try {
            KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(id.privPkcs8))
        } catch (e: Exception) { null }
    }

    fun myPublicKey(): String = identity?.let { Base64.encodeToString(it.pubSpki, b64f) } ?: ""

    fun myClientId(): Long = store.decodeLong(KEY_MYID, 0L)
    private fun setMyClientId(id: Long) = store.encode(KEY_MYID, id)

    fun myHandle(): String = store.decodeString(KEY_HANDLE) ?: ""
    private fun setHandle(h: String) = store.encode(KEY_HANDLE, h)

    data class Found(val id: Long, val handle: String, val pubKey: String)

    /** The shareable invite code: base64(JSON{id,name,pub}). */
    fun myInviteCode(name: String): String {
        val payload = gson.toJson(Invite(id = myClientId(), name = name, pub = myPublicKey()))
        return Base64.encodeToString(payload.toByteArray(), b64f)
    }

    fun parseInvite(code: String): Contact? = try {
        val json = String(Base64.decode(code.trim(), b64f))
        val inv = gson.fromJson(json, Invite::class.java)
        if (inv.id > 0 && inv.pub.isNotBlank()) Contact(inv.id, inv.name.ifBlank { "Контакт" }, inv.pub) else null
    } catch (e: Exception) { null }

    private data class Invite(val id: Long, val name: String, val pub: String)

    // --- contacts ---

    fun contacts(): List<Contact> {
        val json = store.decodeString(KEY_CONTACTS) ?: return emptyList()
        return try { gson.fromJson(json, object : TypeToken<List<Contact>>() {}.type) ?: emptyList() }
        catch (e: Exception) { emptyList() }
    }

    fun addContact(c: Contact) {
        val list = contacts().filterNot { it.id == c.id }.toMutableList()
        list.add(c)
        store.encode(KEY_CONTACTS, gson.toJson(list))
    }

    private fun contact(id: Long): Contact? = contacts().firstOrNull { it.id == id }

    // --- per-contact message log (local, plaintext, encrypted at rest) ---

    fun messages(contactId: Long): List<Msg> {
        val json = store.decodeString("msg_$contactId") ?: return emptyList()
        return try { gson.fromJson(json, object : TypeToken<List<Msg>>() {}.type) ?: emptyList() }
        catch (e: Exception) { emptyList() }
    }

    /** @return true if the message was actually stored (not a dedup hit). */
    private fun appendMessage(contactId: Long, m: Msg): Boolean {
        val list = messages(contactId).toMutableList()
        // Dedup by uuid first (the sent-copy that syncs history carries the same
        // uuid as the local copy), then by server id for legacy messages.
        if (m.u.isNotEmpty() && list.any { it.u == m.u }) return false
        if (m.id != 0L && list.any { it.id == m.id }) return false
        list.add(m)
        store.encode("msg_$contactId", gson.toJson(list))
        return true
    }

    /** A newly-arrived incoming message, for the background notifier. */
    data class NewIncoming(val contactId: Long, val name: String, val preview: String)

    // Deep-link target: the notifier sets the chat to open, the messenger UI
    // consumes it on first composition. 0 = nothing pending.
    @Volatile private var pendingChat: Long = 0L

    /** Ask the UI to open a given chat next time SmartDesk/messenger draws. */
    fun requestOpenChat(id: Long) { pendingChat = id }

    /** Is a chat waiting to be opened? (SmartDesk uses this to open Messages.) */
    fun peekPendingChat(): Long = pendingChat

    /** Take and clear the pending chat (the messenger screen calls this). */
    fun consumePendingChat(): Long { val v = pendingChat; pendingChat = 0L; return v }

    // --- crypto ---

    private fun seal(plaintext: String, recipientPubB64: String): String {
        val kf = KeyFactory.getInstance("RSA")
        val pub = kf.generatePublic(X509EncodedKeySpec(Base64.decode(recipientPubB64, b64f)))
        val aes = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val gcm = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, aes, GCMParameterSpec(128, iv))
        }
        val ct = gcm.doFinal(plaintext.toByteArray())
        val rsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding").apply {
            init(Cipher.ENCRYPT_MODE, pub)
        }
        val wrappedKey = rsa.doFinal(aes.encoded)
        val env = Envelope(
            k = Base64.encodeToString(wrappedKey, b64f),
            iv = Base64.encodeToString(iv, b64f),
            c = Base64.encodeToString(ct, b64f),
        )
        return Base64.encodeToString(gson.toJson(env).toByteArray(), b64f)
    }

    private fun open(ciphertext: String): String? = try {
        val priv = privateKey() ?: return null
        val env = gson.fromJson(String(Base64.decode(ciphertext, b64f)), Envelope::class.java)
        val rsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding").apply {
            init(Cipher.DECRYPT_MODE, priv)
        }
        val aesBytes = rsa.doFinal(Base64.decode(env.k, b64f))
        val aes = SecretKeySpec(aesBytes, "AES")
        val gcm = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, aes, GCMParameterSpec(128, Base64.decode(env.iv, b64f)))
        }
        String(gcm.doFinal(Base64.decode(env.c, b64f)))
    } catch (e: Exception) {
        LogUtil.w(AppConfig.TAG, "messenger decrypt failed: ${e.message}")
        null
    }

    private data class Envelope(val k: String, val iv: String, val c: String)

    // --- networking (through the VPN proxy) ---

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

    /** Learn (and cache) my own client id from the server. */
    suspend fun refreshMyId(): Long = withContext(Dispatchers.IO) {
        val req = authed("/app/messenger/me")?.get()?.build() ?: return@withContext 0L
        try {
            http().newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext myClientId()
                val me = gson.fromJson(resp.body?.string().orEmpty(), MeResp::class.java)
                if (me != null && me.clientId > 0) setMyClientId(me.clientId)
                myClientId()
            }
        } catch (e: Exception) { myClientId() }
    }

    /**
     * Publish our public key and get our auto-assigned @handle (derived
     * server-side from the Telegram username or the device name). Caches it.
     */
    suspend fun register(deviceName: String): String = withContext(Dispatchers.IO) {
        if (!ensureIdentity()) return@withContext myHandle()
        val bodyJson = gson.toJson(RegReq(publicKey = myPublicKey(), deviceName = deviceName))
        val req = authed("/app/messenger/register")
            ?.post(bodyJson.toRequestBody("application/json".toMediaType()))?.build()
            ?: return@withContext myHandle()
        try {
            http().newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext myHandle()
                val r = gson.fromJson(resp.body?.string().orEmpty(), RegResp::class.java)
                if (r != null && r.handle.isNotBlank()) setHandle(r.handle)
                myHandle()
            }
        } catch (e: Exception) { myHandle() }
    }

    /** Search people by @handle prefix. */
    suspend fun searchUsers(q: String): List<Found> = withContext(Dispatchers.IO) {
        if (q.trim().length < 2) return@withContext emptyList()
        val req = authed("/app/messenger/search?q=" + android.net.Uri.encode(q.trim()))
            ?.get()?.build() ?: return@withContext emptyList()
        try {
            http().newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val arr = gson.fromJson(resp.body?.string().orEmpty(), Array<SearchItem>::class.java)
                    ?: return@withContext emptyList()
                arr.map { Found(it.clientId, it.handle, it.publicKey) }
            }
        } catch (e: Exception) { emptyList() }
    }

    /** Look up a handle + public key by client id (to reply to a new sender). */
    private suspend fun getProfile(id: Long): Found? = withContext(Dispatchers.IO) {
        val req = authed("/app/messenger/profile/$id")?.get()?.build() ?: return@withContext null
        try {
            http().newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val it = gson.fromJson(resp.body?.string().orEmpty(), SearchItem::class.java)
                    ?: return@withContext null
                Found(it.clientId, it.handle, it.publicKey)
            }
        } catch (e: Exception) { null }
    }

    /** Open a chat with a searched user (stores them as a contact). */
    fun startChat(found: Found) = addContact(Contact(found.id, "@" + found.handle, found.pubKey))

    private fun postMessage(to: Long, ciphertext: String): Long? {
        val body = gson.toJson(SendReq(to = to, ciphertext = ciphertext))
        val req = authed("/app/messenger/send")
            ?.post(body.toRequestBody("application/json".toMediaType()))?.build() ?: return null
        return try {
            http().newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null
                else gson.fromJson(resp.body?.string().orEmpty(), SendResp::class.java)?.id ?: 0L
            }
        } catch (e: Exception) { null }
    }

    /** Send a message. Stores our copy locally + a self-copy so our other
     *  devices sync the same history. */
    suspend fun send(contactId: Long, text: String): Boolean = withContext(Dispatchers.IO) {
        val c = contact(contactId) ?: return@withContext false
        val u = java.util.UUID.randomUUID().toString()
        val ct = try { seal(gson.toJson(MsgPayload(u = u, k = "text", t = text)), c.pubKey) }
            catch (e: Exception) { return@withContext false }
        val mid = postMessage(contactId, ct) ?: return@withContext false
        appendMessage(contactId, Msg(id = mid, mine = true, text = text, ts = System.currentTimeMillis(), u = u))
        // A copy to ourselves (sealed to our own key), carrying the peer + the
        // recipient-copy id so other devices show it as sent with correct ticks.
        val myId = myClientId()
        if (myId > 0) {
            try {
                val self = gson.toJson(
                    MsgPayload(u = u, k = "text", t = text, peer = contactId, self = true, mid = mid)
                )
                postMessage(myId, seal(self, myPublicKey()))
            } catch (e: Exception) { /* best-effort sync */ }
        }
        true
    }

    // --- delivery/read receipts (✓/✓✓) ---

    data class Receipt(val delivered: Long, val read: Long)

    // reader/contact id -> how far they've received/read MY messages.
    @Volatile private var receipts: Map<Long, Receipt> = emptyMap()

    fun receiptFor(contactId: Long): Receipt? = receipts[contactId]

    // --- settings (Telegram-style privacy toggles), stored in app settings ---
    fun setting(key: String, def: Boolean): Boolean {
        val v = MmkvManager.decodeSettingsString("msgr_$key") ?: return def
        return v == "1"
    }

    fun setSetting(key: String, on: Boolean) =
        MmkvManager.encodeSettings("msgr_$key", if (on) "1" else "0")

    /** Wipe local chat history on this device (server + peers unchanged). */
    fun clearHistory() {
        store.allKeys()?.filter { it.startsWith("msg_") }?.forEach { store.removeValueForKey(it) }
        store.remove(KEY_CURSOR)
        store.remove(KEY_CONTACTS)
    }

    /** Tell the server we've read messages from `peer` up to `upTo` (✓✓). */
    suspend fun markRead(peer: Long, upTo: Long) = withContext(Dispatchers.IO) {
        if (upTo <= 0L || !setting("read", true)) return@withContext
        val body = gson.toJson(ReadReq(peer = peer, upTo = upTo))
        val req = authed("/app/messenger/read")
            ?.post(body.toRequestBody("application/json".toMediaType()))?.build()
            ?: return@withContext
        try { http().newCall(req).execute().use { } } catch (e: Exception) { }
    }

    /** Refresh the ✓/✓✓ state of our sent messages. @return true if changed. */
    suspend fun fetchReceipts(): Boolean = withContext(Dispatchers.IO) {
        val req = authed("/app/messenger/receipts")?.get()?.build() ?: return@withContext false
        try {
            http().newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext false
                val arr = gson.fromJson(resp.body?.string().orEmpty(), Array<ReceiptItem>::class.java)
                    ?: return@withContext false
                val next = arr.associate { it.reader to Receipt(it.delivered, it.read) }
                val changed = next != receipts
                receipts = next
                changed
            }
        } catch (e: Exception) { false }
    }

    // --- real-time channel (WebSocket): wake + typing. Polling stays as the
    //     fallback, so a dropped socket never loses messages. ---

    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var wsCallback: ((String, Long) -> Unit)? = null
    @Volatile private var lastTyping = 0L
    private val wsClient by lazy {
        OkHttpClient.Builder().pingInterval(25, TimeUnit.SECONDS).build()
    }

    /**
     * Keep a socket open; idempotent, so the UI's poll loop can call it every
     * tick to auto-reconnect. `onEvent("wake", 0)` = poll now; `onEvent(
     * "typing", fromId)` = that peer is typing.
     */
    fun connectWs(onEvent: (String, Long) -> Unit) {
        wsCallback = onEvent
        if (webSocket != null) return
        val token = MmkvManager.getAccountToken() ?: return
        val url = "wss://get.vpnka.io/app/messenger/ws?token=" +
            java.net.URLEncoder.encode(token, "UTF-8")
        webSocket = wsClient.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onMessage(ws: WebSocket, text: String) {
                    try {
                        val f = gson.fromJson(text, WsFrame::class.java) ?: return
                        when (f.t) {
                            "wake" -> wsCallback?.invoke("wake", 0L)
                            "typing" -> wsCallback?.invoke("typing", f.from)
                        }
                    } catch (e: Exception) { /* ignore malformed frame */ }
                }
                override fun onFailure(ws: WebSocket, t: Throwable, r: okhttp3.Response?) {
                    if (webSocket === ws) webSocket = null
                }
                override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                    if (webSocket === ws) webSocket = null
                }
            },
        )
    }

    fun disconnectWs() {
        webSocket?.close(1000, null)
        webSocket = null
        wsCallback = null
    }

    /** Tell the peer we're typing (throttled to one ping per 3s). */
    fun sendTyping(to: Long) {
        if (!setting("typing", true)) return
        val now = System.currentTimeMillis()
        if (now - lastTyping < 3000) return
        lastTyping = now
        webSocket?.send(gson.toJson(WsTyping(to = to)))
    }

    private data class WsFrame(val t: String = "", val from: Long = 0)
    private data class WsTyping(val t: String = "typing", val to: Long)

    // The E2E plaintext is a JSON payload: uuid + kind + text (+ peer/mid on a
    // self-copy). Legacy messages were raw text, so a parse failure falls back.
    private data class MsgPayload(
        val u: String = "", val k: String = "text", val t: String = "",
        val peer: Long = 0, val self: Boolean = false, val mid: Long = 0,
        // media (k == "image"): the blob is fetched by `media` id and decrypted
        // with `key` (AES-256 base64). Cross-platform envelope {iv,ct}.
        val media: Long = 0, val key: String = "", val mime: String = "",
    )

    // AES-GCM for media blobs — {iv,ct} envelope, byte-compatible with the web.
    private data class MediaEnv(val iv: String, val ct: String)

    private fun aesGcm(key: ByteArray, plain: ByteArray): String {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        }
        return gson.toJson(
            MediaEnv(Base64.encodeToString(iv, b64f), Base64.encodeToString(c.doFinal(plain), b64f))
        )
    }

    private fun aesGcmDec(key: ByteArray, envJson: String): ByteArray {
        val e = gson.fromJson(envJson, MediaEnv::class.java)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, Base64.decode(e.iv, b64f)))
        }
        return c.doFinal(Base64.decode(e.ct, b64f))
    }

    private data class MediaReq(val to: Long, val blob: String)
    private data class MediaResp(val id: Long = 0)
    private data class MediaBlob(val blob: String = "")

    private fun uploadMedia(to: Long, blob: String): Long? {
        val req = authed("/app/messenger/media")
            ?.post(gson.toJson(MediaReq(to, blob)).toRequestBody("application/json".toMediaType()))
            ?.build() ?: return null
        return try {
            http().newCall(req).execute().use { r ->
                if (!r.isSuccessful) null
                else gson.fromJson(r.body?.string().orEmpty(), MediaResp::class.java)?.id
            }
        } catch (e: Exception) { null }
    }

    private fun downloadMedia(id: Long): String? {
        val req = authed("/app/messenger/media/$id")?.get()?.build() ?: return null
        return try {
            http().newCall(req).execute().use { r ->
                if (!r.isSuccessful) null
                else gson.fromJson(r.body?.string().orEmpty(), MediaBlob::class.java)?.blob
            }
        } catch (e: Exception) { null }
    }

    /** Send a JPEG. Encrypts it, uploads the blob, sends the reference + self-copy. */
    suspend fun sendImage(contactId: Long, jpeg: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val c = contact(contactId) ?: return@withContext false
        val u = java.util.UUID.randomUUID().toString()
        val k = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val mediaId = uploadMedia(contactId, aesGcm(k, jpeg)) ?: return@withContext false
        val keyB = Base64.encodeToString(k, b64f)
        val ct = try {
            seal(gson.toJson(MsgPayload(u = u, k = "image", media = mediaId, key = keyB, mime = "image/jpeg")), c.pubKey)
        } catch (e: Exception) { return@withContext false }
        val mid = postMessage(contactId, ct) ?: return@withContext false
        appendMessage(contactId, Msg(id = mid, mine = true, text = "", ts = System.currentTimeMillis(), u = u, k = "image", img = Base64.encodeToString(jpeg, b64f)))
        val myId = myClientId()
        if (myId > 0) {
            try {
                val self = gson.toJson(MsgPayload(u = u, k = "image", media = mediaId, key = keyB, mime = "image/jpeg", peer = contactId, self = true, mid = mid))
                postMessage(myId, seal(self, myPublicKey()))
            } catch (e: Exception) { /* best-effort sync */ }
        }
        true
    }

    private fun parsePayload(raw: String): MsgPayload = try {
        val o = gson.fromJson(raw, MsgPayload::class.java)
        if (o != null && o.k.isNotEmpty()) o else MsgPayload(k = "text", t = raw)
    } catch (e: Exception) { MsgPayload(k = "text", t = raw) }

    /**
     * Poll incoming, decrypt, file into the sender's chat. @return true if
     * anything new. Pass [collect] to gather freshly-arrived incoming messages
     * (from other people, not our own history sync) for the notifier.
     */
    suspend fun poll(collect: MutableList<NewIncoming>? = null): Boolean = withContext(Dispatchers.IO) {
        if (!ensureIdentity()) return@withContext false
        val since = store.decodeLong(KEY_CURSOR, 0L)
        val req = authed("/app/messenger/poll?since=$since")
            ?.post(ByteArray(0).toRequestBody())?.build() ?: return@withContext false
        try {
            http().newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext false
                val out = gson.fromJson(resp.body?.string().orEmpty(), PollResp::class.java)
                    ?: return@withContext false
                var changed = false
                val me = myClientId()
                for (m in out.messages) {
                    val raw = open(m.ciphertext) ?: continue
                    val o = parsePayload(raw)
                    // A message from ourselves is a sent-copy syncing history:
                    // file it into the peer's chat on the sent side. Otherwise
                    // it's a real incoming message.
                    val chat: Long; val mine: Boolean; val id: Long
                    if (m.fromClient == me) {
                        if (o.peer <= 0) continue
                        chat = o.peer; mine = true; id = if (o.mid > 0) o.mid else m.id
                    } else {
                        chat = m.fromClient; mine = false; id = m.id
                    }
                    if (contact(chat) == null) {
                        val prof = getProfile(chat)
                        addContact(
                            if (prof != null) Contact(prof.id, "@" + prof.handle, prof.pubKey)
                            else Contact(chat, "Контакт $chat", "")
                        )
                    }
                    val added: Boolean
                    val preview: String
                    if (o.k == "image" && o.media > 0 && o.key.isNotEmpty()) {
                        val blob = downloadMedia(o.media)
                        val img = if (blob != null) try {
                            Base64.encodeToString(aesGcmDec(Base64.decode(o.key, b64f), blob), b64f)
                        } catch (e: Exception) { "" } else ""
                        added = appendMessage(chat, Msg(id = id, mine = mine, text = "", ts = System.currentTimeMillis(), u = o.u, k = "image", img = img))
                        preview = "📷 Фото"
                    } else {
                        added = appendMessage(chat, Msg(id = id, mine = mine, text = o.t, ts = System.currentTimeMillis(), u = o.u))
                        preview = o.t
                    }
                    // Only genuinely-new messages from other people are worth a
                    // system notification — not our own history sync, not dups.
                    if (added && !mine) {
                        collect?.add(NewIncoming(chat, contact(chat)?.name ?: "Контакт $chat", preview))
                    }
                    changed = true
                }
                store.encode(KEY_CURSOR, out.cursor)
                changed
            }
        } catch (e: Exception) { false }
    }

    private data class MeResp(@SerializedName("client_id") val clientId: Long = 0)
    private data class RegReq(
        @SerializedName("public_key") val publicKey: String,
        @SerializedName("device_name") val deviceName: String,
    )
    private data class RegResp(val handle: String = "")
    private data class SearchItem(
        @SerializedName("client_id") val clientId: Long = 0,
        val handle: String = "",
        @SerializedName("public_key") val publicKey: String = "",
    )
    private data class SendReq(val to: Long, val ciphertext: String)
    private data class SendResp(val ok: Boolean = false, val id: Long = 0)
    private data class ReadReq(val peer: Long, @SerializedName("up_to") val upTo: Long)
    private data class ReceiptItem(
        val reader: Long = 0,
        @SerializedName("delivered_up_to") val delivered: Long = 0,
        @SerializedName("read_up_to") val read: Long = 0,
    )
    private data class PollMsg(
        val id: Long = 0,
        @SerializedName("from_client") val fromClient: Long = 0,
        val ciphertext: String = "",
    )
    private data class PollResp(val cursor: Long = 0, val messages: List<PollMsg> = emptyList())
}
