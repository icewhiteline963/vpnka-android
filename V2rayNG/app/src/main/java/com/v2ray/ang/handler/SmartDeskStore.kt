package com.v2ray.ang.handler

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tencent.mmkv.MMKV
import java.security.SecureRandom

/**
 * Local-first storage for the SmartDesk apps (calendar / contacts / mail).
 *
 * The three record types live as JSON lists in an ENCRYPTED MMKV instance —
 * this is the on-device container the spec asks for: work is kept here and,
 * in Phase 3, pushed to the server when a connection is available and wiped
 * locally after a confirmed sync. Until then everything the user writes is
 * held here, encrypted at rest.
 *
 * The AES key is generated once and kept in the default settings store. That
 * raises the bar over a plaintext file; Phase 3 moves the key into the Android
 * Keystore so it never leaves secure hardware.
 */
object SmartDeskStore {

    private const val ID_STORE = "vpnka_smartdesk"
    private const val KEY_CRYPT = "vpnka_smartdesk_cryptkey"
    private const val KEY_CALENDAR = "calendar"
    private const val KEY_CONTACTS = "contacts"
    private const val KEY_MAIL = "mail"

    private val gson = Gson()

    private fun cryptKey(): String {
        MmkvManager.decodeSettingsString(KEY_CRYPT)?.let { if (it.isNotBlank()) return it }
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val key = bytes.joinToString("") { "%02x".format(it) }
        MmkvManager.encodeSettings(KEY_CRYPT, key)
        return key
    }

    private val store: MMKV by lazy {
        MMKV.mmkvWithID(ID_STORE, MMKV.SINGLE_PROCESS_MODE, cryptKey())
    }

    // --- Data models. `id` is a client-side uuid; `updatedAt` is epoch millis,
    // both there so Phase 3 sync can dedupe and resolve last-writer-wins. ---

    data class CalendarEvent(
        val id: String,
        val title: String = "",
        val whenText: String = "",   // free-text date/time for the MVP
        val note: String = "",
        val updatedAt: Long = 0L,
    )

    data class Contact(
        val id: String,
        val name: String = "",
        val phone: String = "",
        val email: String = "",
        val note: String = "",
        val updatedAt: Long = 0L,
    )

    data class MailMessage(
        val id: String,
        val to: String = "",         // another vpnka user (internal mail)
        val subject: String = "",
        val body: String = "",
        val updatedAt: Long = 0L,
    )

    fun newId(): String {
        val bytes = ByteArray(8).also { SecureRandom().nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // --- Calendar ---
    fun calendar(): List<CalendarEvent> = readList(KEY_CALENDAR)
    fun saveEvent(e: CalendarEvent) = upsert(KEY_CALENDAR, e, { it.id }) { it.copy() }
    fun deleteEvent(id: String) = delete(KEY_CALENDAR, id) { it.id }

    // --- Contacts ---
    fun contacts(): List<Contact> = readList(KEY_CONTACTS)
    fun saveContact(c: Contact) = upsert(KEY_CONTACTS, c, { it.id }) { it.copy() }
    fun deleteContact(id: String) = delete(KEY_CONTACTS, id) { it.id }

    // --- Mail ---
    fun mail(): List<MailMessage> = readList(KEY_MAIL)
    fun saveMail(m: MailMessage) = upsert(KEY_MAIL, m, { it.id }) { it.copy() }
    fun deleteMail(id: String) = delete(KEY_MAIL, id) { it.id }

    // --- Generic JSON-list helpers ---

    private inline fun <reified T> readList(key: String): List<T> {
        val json = store.decodeString(key) ?: return emptyList()
        return try {
            JsonUtil.gson.fromJson(json, object : TypeToken<List<T>>() {}.type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private inline fun <reified T> writeList(key: String, list: List<T>) {
        store.encode(key, JsonUtil.toJson(list))
    }

    private inline fun <reified T> upsert(
        key: String,
        item: T,
        idOf: (T) -> String,
        copy: (T) -> T,
    ) {
        val list = readList<T>(key).toMutableList()
        val idx = list.indexOfFirst { idOf(it) == idOf(item) }
        if (idx >= 0) list[idx] = copy(item) else list.add(copy(item))
        writeList(key, list)
    }

    private inline fun <reified T> delete(key: String, id: String, idOf: (T) -> String) {
        writeList(key, readList<T>(key).filterNot { idOf(it) == id })
    }
}
