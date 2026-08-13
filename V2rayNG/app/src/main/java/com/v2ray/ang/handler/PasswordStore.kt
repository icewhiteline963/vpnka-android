package com.v2ray.ang.handler

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * SmartDesk browser password manager. Stores one login per host in the app's
 * encrypted MMKV settings. Lookups are always keyed by the trusted current-page
 * host supplied by the app (never by a host string coming from page JS), so a
 * site can only ever read back its OWN saved credentials.
 */
object PasswordStore {
    private const val KEY = "vpnka_browser_passwords"
    private val gson = Gson()

    data class Cred(val host: String, val username: String, val password: String)

    private fun load(): MutableList<Cred> {
        val raw = MmkvManager.decodeSettingsString(KEY) ?: return mutableListOf()
        return try {
            gson.fromJson<MutableList<Cred>>(raw, object : TypeToken<MutableList<Cred>>() {}.type)
                ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    private fun store(list: List<Cred>) {
        MmkvManager.encodeSettings(KEY, gson.toJson(list))
    }

    fun all(): List<Cred> = load()

    fun forHost(host: String): Cred? = load().firstOrNull { it.host.equals(host, ignoreCase = true) }

    fun save(host: String, username: String, password: String) {
        if (host.isBlank() || password.isBlank()) return
        val list = load()
        list.removeAll { it.host.equals(host, ignoreCase = true) }
        list.add(0, Cred(host, username, password))
        store(list)
    }

    fun remove(host: String) {
        val list = load()
        if (list.removeAll { it.host.equals(host, ignoreCase = true) }) store(list)
    }

    /** JSON `{"u":..,"p":..}` for the autofill script, or null if nothing saved. */
    fun credentialsJson(host: String): String? {
        val c = forHost(host) ?: return null
        return gson.toJson(mapOf("u" to c.username, "p" to c.password))
    }
}
