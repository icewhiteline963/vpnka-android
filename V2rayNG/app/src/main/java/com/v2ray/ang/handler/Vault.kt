package com.v2ray.ang.handler

import android.util.Base64
import com.google.gson.Gson
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
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Zero-knowledge vault (client side).
 *
 * A random 256-bit master key (MK) encrypts everything the user stores. MK is
 * wrapped twice — under a key derived from the user's passphrase (PBKDF2-
 * HMAC-SHA256) and under one derived from a recovery key — so either opens it.
 * The wrapped blobs live on our server; the server holds no passphrase, no
 * recovery key and no MK, so it can decrypt nothing.
 *
 * Once unlocked on a device, MK is cached in the on-device encrypted container
 * (protected at rest like the rest of SmartDesk) so the passphrase is asked
 * only on first setup and on a fresh device — not every launch.
 */
object Vault {

    private const val ID_STORE = "vpnka_vault"
    private const val KEY_CRYPT = "vpnka_vault_cryptkey"
    private const val KEY_MK = "mk"          // cached unlocked master key (base64)
    private const val ITERS = 210_000
    private const val BASE = "https://get.vpnka.io"

    private val gson = Gson()
    private val b64 = Base64.NO_WRAP

    private fun cryptKey(): String {
        MmkvManager.decodeSettingsString(KEY_CRYPT)?.let { if (it.isNotBlank()) return it }
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val key = bytes.joinToString("") { "%02x".format(it) }
        MmkvManager.encodeSettings(KEY_CRYPT, key)
        return key
    }

    private val store: MMKV by lazy { MMKV.mmkvWithID(ID_STORE, MMKV.SINGLE_PROCESS_MODE, cryptKey()) }

    // In-memory master key once unlocked (also cached in `store`).
    @Volatile private var mk: ByteArray? = null

    fun isUnlocked(): Boolean {
        if (mk != null) return true
        val cached = store.decodeString(KEY_MK) ?: return false
        return try { mk = Base64.decode(cached, b64); true } catch (e: Exception) { false }
    }

    private fun cacheMk(key: ByteArray) { mk = key; store.encode(KEY_MK, Base64.encodeToString(key, b64)) }

    fun lock() { mk = null; store.remove(KEY_MK) }

    // --- primitives ---

    /** PBKDF2-HMAC-SHA256, one 32-byte block (dkLen = hLen). Cross-platform. */
    private fun pbkdf2(pass: ByteArray, salt: ByteArray, iters: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(pass, "HmacSHA256")) }
        var u = mac.doFinal(salt + byteArrayOf(0, 0, 0, 1))
        val t = u.copyOf()
        for (i in 2..iters) {
            u = mac.doFinal(u)
            for (j in t.indices) t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
        }
        return t
    }

    private fun aesEnc(key: ByteArray, plain: ByteArray): String {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        }
        val ct = c.doFinal(plain)
        return gson.toJson(Env(Base64.encodeToString(iv, b64), Base64.encodeToString(ct, b64)))
    }

    private fun aesDec(key: ByteArray, envJson: String): ByteArray {
        val e = gson.fromJson(envJson, Env::class.java)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, Base64.decode(e.iv, b64)))
        }
        return c.doFinal(Base64.decode(e.ct, b64))
    }

    private data class Env(val iv: String, val ct: String)

    /** Encrypt an app string with MK. Null if the vault is locked. */
    fun encrypt(plain: String): String? {
        val key = mk ?: return null
        return aesEnc(key, plain.toByteArray())
    }

    fun decrypt(envJson: String): String? {
        val key = mk ?: return null
        return try { String(aesDec(key, envJson)) } catch (e: Exception) { null }
    }

    // --- recovery key: 128 bits, Crockford base32, grouped ---

    private const val B32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private fun genRecovery(): String {
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val sb = StringBuilder()
        var buffer = 0; var bits = 0
        for (byte in bytes) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xff); bits += 8
            while (bits >= 5) { bits -= 5; sb.append(B32[(buffer shr bits) and 0x1f]) }
        }
        // 26 chars → groups of 4 for readability.
        return sb.toString().chunked(4).joinToString("-")
    }

    private fun recoveryToKey(recovery: String, salt: ByteArray): ByteArray =
        pbkdf2(recovery.replace("-", "").uppercase().toByteArray(), salt, ITERS)

    // --- setup / unlock ---

    data class SetupResult(val recoveryKey: String)

    /**
     * First-time setup: generate MK + recovery key, wrap MK under the
     * passphrase and the recovery key, upload the vault. Returns the recovery
     * key to show the user once.
     */
    suspend fun setup(passphrase: String): SetupResult? = withContext(Dispatchers.IO) {
        val master = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val recovery = genRecovery()
        val passKey = pbkdf2(passphrase.toByteArray(), salt, ITERS)
        val recKey = recoveryToKey(recovery, salt)
        val body = VaultBody(
            kdf_salt = Base64.encodeToString(salt, b64),
            kdf_iters = ITERS,
            wrapped_mk_pass = aesEnc(passKey, master),
            wrapped_mk_recovery = aesEnc(recKey, master),
            wrapped_privkey = null,
        )
        if (!putVault(body)) return@withContext null
        cacheMk(master)
        SetupResult(recovery)
    }

    /**
     * Server-side vault state: "exists" | "absent" | "offline". Distinguishing
     * a confirmed 404 (offer setup) from an unreachable server (do NOT offer
     * setup) is critical — offering setup while a vault exists but is
     * unreachable would overwrite it and lose the user's data forever.
     */
    suspend fun status(): String = withContext(Dispatchers.IO) {
        val req = authed("/app/vault")?.get()?.build() ?: return@withContext "offline"
        try {
            http().newCall(req).execute().use { resp ->
                when {
                    resp.isSuccessful -> "exists"
                    resp.code == 404 -> "absent"
                    else -> "offline"
                }
            }
        } catch (e: Exception) { "offline" }
    }

    /** Unlock with the passphrase. @return true on success. */
    suspend fun unlock(passphrase: String): Boolean = withContext(Dispatchers.IO) {
        val v = fetchVault() ?: return@withContext false
        val salt = Base64.decode(v.kdf_salt, b64)
        val passKey = pbkdf2(passphrase.toByteArray(), salt, v.kdf_iters)
        try { cacheMk(aesDec(passKey, v.wrapped_mk_pass)); true } catch (e: Exception) { false }
    }

    /** Unlock with the recovery key (when the passphrase is forgotten). */
    suspend fun unlockRecovery(recovery: String): Boolean = withContext(Dispatchers.IO) {
        val v = fetchVault() ?: return@withContext false
        val salt = Base64.decode(v.kdf_salt, b64)
        val recKey = recoveryToKey(recovery, salt)
        try { cacheMk(aesDec(recKey, v.wrapped_mk_recovery)); true } catch (e: Exception) { false }
    }

    /**
     * Re-wrap the (already unlocked) master key under a new passphrase, keeping
     * the same salt and recovery wrap. Used after a recovery-key login so the
     * user can log in by password again.
     */
    suspend fun changePassphrase(newPass: String): Boolean = withContext(Dispatchers.IO) {
        val master = mk ?: return@withContext false
        val v = fetchVault() ?: return@withContext false
        val salt = Base64.decode(v.kdf_salt, b64)
        val passKey = pbkdf2(newPass.toByteArray(), salt, v.kdf_iters)
        putVault(
            VaultBody(
                kdf_salt = v.kdf_salt,
                kdf_iters = v.kdf_iters,
                wrapped_mk_pass = aesEnc(passKey, master),
                wrapped_mk_recovery = v.wrapped_mk_recovery,
                wrapped_privkey = v.wrapped_privkey,
            )
        )
    }

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

    private fun fetchVault(): VaultBody? {
        val req = authed("/app/vault")?.get()?.build() ?: return null
        return try {
            http().newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null
                else gson.fromJson(resp.body?.string().orEmpty(), VaultBody::class.java)
            }
        } catch (e: Exception) { LogUtil.w(AppConfig.TAG, "vault fetch: ${e.message}"); null }
    }

    private fun putVault(body: VaultBody): Boolean {
        val req = authed("/app/vault")
            ?.put(gson.toJson(body).toRequestBody("application/json".toMediaType()))?.build()
            ?: return false
        return try { http().newCall(req).execute().use { it.isSuccessful } }
        catch (e: Exception) { LogUtil.w(AppConfig.TAG, "vault put: ${e.message}"); false }
    }

    private data class VaultBody(
        val kdf_salt: String,
        val kdf_iters: Int,
        val wrapped_mk_pass: String,
        val wrapped_mk_recovery: String,
        val wrapped_privkey: String?,
    )
}
