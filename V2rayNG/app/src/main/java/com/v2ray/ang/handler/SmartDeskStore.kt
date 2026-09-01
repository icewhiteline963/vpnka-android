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
    private const val KEY_NOTES = "notes"

    private val gson = Gson()

    /** Every record carries a client-side id + updatedAt for Phase 3 sync. */
    interface DeskItem {
        val id: String
        val updatedAt: Long
    }

    data class CalendarEvent(
        override val id: String,
        val title: String = "",
        val dateIso: String = "",    // yyyy-MM-dd — the day this event sits on
        val whenText: String = "",   // free-text time / details
        val note: String = "",
        override val updatedAt: Long = 0L,
    ) : DeskItem

    data class Contact(
        override val id: String,
        val name: String = "",
        val phone: String = "",
        val email: String = "",
        val note: String = "",
        override val updatedAt: Long = 0L,
    ) : DeskItem

    data class MailMessage(
        override val id: String,
        val to: String = "",         // another vpnka user (internal mail)
        val subject: String = "",
        val body: String = "",
        override val updatedAt: Long = 0L,
    ) : DeskItem

    /** An inline text-style run (bold/italic/underline/strike) over [start, end). */
    data class NoteSpan(val start: Int = 0, val end: Int = 0, val style: String = "")

    /** One line of a checklist / shopping list. */
    data class CheckItem(val text: String = "", val done: Boolean = false)

    /** A note: rich text ("text") or a checklist ("list"). */
    data class Note(
        override val id: String,
        val title: String = "",
        val kind: String = "text",             // "text" | "list"
        val body: String = "",                 // text notes: raw characters
        val spans: List<NoteSpan> = emptyList(),  // text notes: style runs
        val items: List<CheckItem> = emptyList(), // list notes: checklist rows
        override val updatedAt: Long = 0L,
    ) : DeskItem

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

    fun newId(): String {
        val bytes = ByteArray(8).also { SecureRandom().nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // --- Calendar --- (public writes queue a pending change for sync)
    fun calendar(): List<CalendarEvent> = readList(KEY_CALENDAR)
    fun saveEvent(e: CalendarEvent) { upsert(KEY_CALENDAR, e); queue("calendar", e.id, e.updatedAt, false, gson.toJson(e)) }
    fun deleteEvent(id: String) { delete<CalendarEvent>(KEY_CALENDAR, id); queue("calendar", id, nowMs(), true, "{}") }

    // --- Contacts ---
    fun contacts(): List<Contact> = readList(KEY_CONTACTS)
    fun saveContact(c: Contact) { upsert(KEY_CONTACTS, c); queue("contact", c.id, c.updatedAt, false, gson.toJson(c)) }
    fun deleteContact(id: String) { delete<Contact>(KEY_CONTACTS, id); queue("contact", id, nowMs(), true, "{}") }

    // --- Notes ---
    fun notes(): List<Note> = readList(KEY_NOTES)
    fun saveNote(n: Note) { upsert(KEY_NOTES, n); queue("note", n.id, n.updatedAt, false, gson.toJson(n)) }
    fun deleteNote(id: String) {
        deletedIds.add(id)
        delete<Note>(KEY_NOTES, id); queue("note", id, nowMs(), true, "{}")
    }

    /**
     * Удалённые в этом сеансе — чтобы редактор не воскресил заметку
     * сохранением при уходе с экрана.
     */
    private val deletedIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    fun isDeleted(id: String): Boolean = deletedIds.contains(id)

    // --- Mail ---
    fun mail(): List<MailMessage> = readList(KEY_MAIL)
    fun saveMail(m: MailMessage) { upsert(KEY_MAIL, m); queue("mail", m.id, m.updatedAt, false, gson.toJson(m)) }
    fun deleteMail(id: String) { delete<MailMessage>(KEY_MAIL, id); queue("mail", id, nowMs(), true, "{}") }

    // --- Sync plumbing -------------------------------------------------------
    // A change is what a public write records: pushed to the server on the next
    // sync, then cleared. `cursor` is the server position we've pulled up to.

    private const val KEY_PENDING = "pending"
    private const val KEY_CURSOR = "vpnka_smartdesk_cursor"

    data class Change(
        val kind: String,
        val id: String,
        val updatedAtMs: Long,
        val deleted: Boolean,
        val payloadJson: String,
    )

    private fun nowMs(): Long = System.currentTimeMillis()

    fun cursor(): Long = MmkvManager.decodeSettingsString(KEY_CURSOR)?.toLongOrNull() ?: 0L
    fun setCursor(c: Long) { MmkvManager.encodeSettings(KEY_CURSOR, c.toString()) }

    fun pending(): List<Change> = readChanges()
    fun clearPending() { store.remove(KEY_PENDING) }

    /**
     * Снять из очереди только те записи, что реально уехали.
     *
     * `clearPending` сносила очередь ЦЕЛИКОМ, включая правки, поставленные
     * пока запрос был в полёте: они не уходили на сервер и стирались
     * следующей самоочисткой. Синхронизация запускается на каждое
     * сохранение, так что параллельные вызовы — обычное дело.
     */
    fun clearPendingKeys(sent: List<Pair<String, String>>) {
        if (sent.isEmpty()) return
        val gone = sent.toSet()
        val left = readChanges().filterNot { (it.kind to it.id) in gone }
        if (left.isEmpty()) store.remove(KEY_PENDING)
        else store.encode(KEY_PENDING, gson.toJson(left))
    }

    /** Maximum-privacy self-wipe. After a confirmed sync (queue empty, so nothing
     *  is lost) the local records are erased and the cursor reset, so the next
     *  open re-pulls everything from the encrypted server. Between sessions the
     *  phone holds no SmartDesk data at all — not even ciphertext. */
    fun wipeLocal() {
        store.remove(KEY_CALENDAR)
        // Заметки тоже: их пропускали, а обещание «на телефоне ничего не
        // оседает» касается в первую очередь именно их.
        store.remove(KEY_NOTES)
        store.remove(KEY_CONTACTS)
        store.remove(KEY_MAIL)
        setCursor(0)
    }

    private fun readChanges(): List<Change> {
        val json = store.decodeString(KEY_PENDING) ?: return emptyList()
        return try {
            gson.fromJson(json, object : TypeToken<List<Change>>() {}.type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Record a pending change, collapsing repeated edits of the same item. */
    private fun queue(kind: String, id: String, updatedAtMs: Long, deleted: Boolean, payloadJson: String) {
        val list = readChanges().filterNot { it.kind == kind && it.id == id }.toMutableList()
        list.add(Change(kind, id, updatedAtMs, deleted, payloadJson))
        store.encode(KEY_PENDING, gson.toJson(list))
    }

    /**
     * Apply an item the server sent down. Does NOT re-queue it as pending —
     * it came from the server, we're just catching up. Delete = tombstone.
     */
    fun applyRemote(kind: String, id: String, deleted: Boolean, payloadJson: String) {
        when (kind) {
            "calendar" -> if (deleted) delete<CalendarEvent>(KEY_CALENDAR, id)
                else parse<CalendarEvent>(payloadJson)?.let { upsert(KEY_CALENDAR, it) }
            "contact" -> if (deleted) delete<Contact>(KEY_CONTACTS, id)
                else parse<Contact>(payloadJson)?.let { upsert(KEY_CONTACTS, it) }
            "mail" -> if (deleted) delete<MailMessage>(KEY_MAIL, id)
                else parse<MailMessage>(payloadJson)?.let { upsert(KEY_MAIL, it) }
            "note" -> if (deleted) delete<Note>(KEY_NOTES, id)
                else parse<Note>(payloadJson)?.let { upsert(KEY_NOTES, it) }
        }
    }

    /**
     * Deserialize a pulled record, rejecting anything Gson would leave with a
     * null `id`. That happens when the payload is `"{}"` — which
     * `decodePayload` returns whenever decryption fails (key mismatch after a
     * re-setup) — or a truncated/partial object. Gson bypasses the Kotlin
     * constructor, so those nulls would otherwise slip into non-null String
     * fields and crash Calendar/Contacts (`it.dateIso.isNotBlank()` → NPE), or
     * overwrite a good record with a blank stub. Dropping them is safe: the
     * real record is retried on the next sync of that item.
     */
    private inline fun <reified T : DeskItem> parse(json: String): T? = try {
        @Suppress("SENSELESS_COMPARISON")
        gson.fromJson(json, T::class.java)?.takeIf { it.id != null && it.id.isNotBlank() }
    } catch (e: Exception) { null }

    // --- Generic JSON-list helpers ---

    private inline fun <reified T> readList(key: String): List<T> {
        val json = store.decodeString(key) ?: return emptyList()
        return try {
            gson.fromJson(json, object : TypeToken<List<T>>() {}.type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private inline fun <reified T> writeList(key: String, list: List<T>) {
        store.encode(key, gson.toJson(list))
    }

    private inline fun <reified T : DeskItem> upsert(key: String, item: T) {
        val list = readList<T>(key).toMutableList()
        val idx = list.indexOfFirst { it.id == item.id }
        if (idx >= 0) list[idx] = item else list.add(item)
        writeList(key, list)
    }

    private inline fun <reified T : DeskItem> delete(key: String, id: String) {
        writeList(key, readList<T>(key).filterNot { it.id == id })
    }
}
