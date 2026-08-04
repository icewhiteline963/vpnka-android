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
import java.net.InetSocketAddress
import java.net.Proxy
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
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
    private const val KEY_PRIV = "priv"
    private const val KEY_PUB = "pub"
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
    data class Msg(val id: Long, val mine: Boolean, val text: String, val ts: Long)

    // --- keys ---

    private fun ensureKeys(): Pair<PrivateKey, PublicKey> {
        val privB64 = store.decodeString(KEY_PRIV)
        val pubB64 = store.decodeString(KEY_PUB)
        if (privB64 != null && pubB64 != null) {
            val kf = KeyFactory.getInstance("RSA")
            val priv = kf.generatePrivate(PKCS8EncodedKeySpec(Base64.decode(privB64, b64f)))
            val pub = kf.generatePublic(X509EncodedKeySpec(Base64.decode(pubB64, b64f)))
            return priv to pub
        }
        val gen = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
        val kp = gen.generateKeyPair()
        store.encode(KEY_PRIV, Base64.encodeToString(kp.private.encoded, b64f))
        store.encode(KEY_PUB, Base64.encodeToString(kp.public.encoded, b64f))
        return kp.private to kp.public
    }

    fun myPublicKey(): String { ensureKeys(); return store.decodeString(KEY_PUB) ?: "" }

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

    private fun appendMessage(contactId: Long, m: Msg) {
        val list = messages(contactId).toMutableList()
        if (list.any { it.id == m.id && m.id != 0L }) return
        list.add(m)
        store.encode("msg_$contactId", gson.toJson(list))
    }

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
        val (priv, _) = ensureKeys()
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
        val port = SettingsManager.getHttpPort()
        return OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", port)))
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

    /** Send a plaintext message to a contact. Stores our own copy locally. */
    suspend fun send(contactId: Long, text: String): Boolean = withContext(Dispatchers.IO) {
        val c = contact(contactId) ?: return@withContext false
        val ciphertext = try { seal(text, c.pubKey) } catch (e: Exception) { return@withContext false }
        val bodyJson = gson.toJson(SendReq(to = contactId, ciphertext = ciphertext))
        val req = authed("/app/messenger/send")
            ?.post(bodyJson.toRequestBody("application/json".toMediaType()))?.build()
            ?: return@withContext false
        try {
            http().newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext false
                appendMessage(contactId, Msg(id = 0L, mine = true, text = text, ts = System.currentTimeMillis()))
                true
            }
        } catch (e: Exception) { false }
    }

    /** Poll incoming, decrypt, file into the sender's chat. @return true if anything new. */
    suspend fun poll(): Boolean = withContext(Dispatchers.IO) {
        val since = store.decodeLong(KEY_CURSOR, 0L)
        val req = authed("/app/messenger/poll?since=$since")
            ?.post(ByteArray(0).toRequestBody())?.build() ?: return@withContext false
        try {
            http().newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext false
                val out = gson.fromJson(resp.body?.string().orEmpty(), PollResp::class.java)
                    ?: return@withContext false
                var changed = false
                for (m in out.messages) {
                    val text = open(m.ciphertext) ?: continue
                    // First message from someone new: pull their handle + key
                    // so the reply can be encrypted. Fall back to a stub.
                    if (contact(m.fromClient) == null) {
                        val prof = getProfile(m.fromClient)
                        addContact(
                            if (prof != null) Contact(prof.id, "@" + prof.handle, prof.pubKey)
                            else Contact(m.fromClient, "Контакт ${m.fromClient}", "")
                        )
                    }
                    appendMessage(m.fromClient, Msg(id = m.id, mine = false, text = text, ts = System.currentTimeMillis()))
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
    private data class PollMsg(
        val id: Long = 0,
        @SerializedName("from_client") val fromClient: Long = 0,
        val ciphertext: String = "",
    )
    private data class PollResp(val cursor: Long = 0, val messages: List<PollMsg> = emptyList())
}
