package com.v2ray.ang.handler

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Локальные свойства чатов: закреплён / без звука / в архиве, отметка «докуда
 * прочитано» и журнал звонков.
 *
 * Всё это живёт ТОЛЬКО на устройстве и намеренно: сервер у нас слепой релей,
 * он не знает ни кто с кем переписывается, ни что человек закрепил. Плата за
 * это — настройки не переезжают на второе устройство. Обещать синхронизацию,
 * не сломав слепоту сервера, нельзя, поэтому и не обещаем.
 */
object ChatPrefs {

    private val gson = Gson()

    private fun ids(key: String): MutableSet<Long> {
        val raw = MmkvManager.decodeSettingsString(key) ?: return mutableSetOf()
        return try {
            gson.fromJson<List<Long>>(raw, object : TypeToken<List<Long>>() {}.type)
                ?.toMutableSet() ?: mutableSetOf()
        } catch (e: Exception) {
            mutableSetOf()
        }
    }

    private fun save(key: String, set: Set<Long>) =
        MmkvManager.encodeSettings(key, gson.toJson(set.toList()))

    private fun toggle(key: String, id: Long, on: Boolean) {
        val s = ids(key)
        if (on) s.add(id) else s.remove(id)
        save(key, s)
    }

    fun pinned(): Set<Long> = ids("chat_pinned")
    fun isPinned(id: Long) = pinned().contains(id)
    fun setPinned(id: Long, on: Boolean) = toggle("chat_pinned", id, on)

    fun muted(): Set<Long> = ids("chat_muted")
    fun isMuted(id: Long) = muted().contains(id)
    fun setMuted(id: Long, on: Boolean) = toggle("chat_muted", id, on)

    fun archived(): Set<Long> = ids("chat_archived")
    fun isArchived(id: Long) = archived().contains(id)
    fun setArchived(id: Long, on: Boolean) = toggle("chat_archived", id, on)

    // --- непрочитанное ---
    //
    // Считаем по последнему ВИДЕННОМУ времени, а не по идентификатору: у
    // сообщений, отправленных с этого устройства, id приходит с сервера, а у
    // черновых копий его может не быть вовсе. Время есть у всех.

    private fun seenKey(id: Long) = "chat_seen_$id"

    /**
     * Отметка «прочитано» — по НОМЕРУ сообщения, а не по времени.
     *
     * Время теперь ставит отправитель. С отставшими часами его сообщения
     * оказывались «старее» отметки и не считались непрочитанными вовсе; с
     * убежавшими вперёд отметка уезжала в будущее и глушила счётчик до тех
     * пор, пока настоящее время её не догонит. Номера у входящих выдаёт
     * сервер, и они строго растут.
     */
    fun markSeen(contactId: Long) {
        val last = Messenger.messages(contactId).filter { !it.mine }.maxOfOrNull { it.id } ?: return
        MmkvManager.encodeSettings(seenKey(contactId), last.toString())
    }

    fun unread(contactId: Long): Int {
        val seen = MmkvManager.decodeSettingsString(seenKey(contactId))?.toLongOrNull() ?: 0L
        return Messenger.messages(contactId).count { !it.mine && it.id > seen }
    }

    // --- журнал звонков ---

    data class Call(
        val peerId: Long,
        val name: String,
        /** outgoing | incoming | missed */
        val dir: String,
        val ts: Long,
        /** Длительность разговора в секундах; 0 — не состоялся. */
        val sec: Int = 0,
        /** Id групповой сессии; "" — обычный 1:1 звонок. */
        val group: String = "",
        /** Сколько ЕЩЁ участников было в звонке, кроме peerId и меня. */
        val others: Int = 0,
    )

    private const val CALLS = "chat_calls"
    private const val CALLS_MAX = 100

    fun calls(): List<Call> {
        val raw = MmkvManager.decodeSettingsString(CALLS) ?: return emptyList()
        return try {
            gson.fromJson<List<Call>>(raw, object : TypeToken<List<Call>>() {}.type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addCall(c: Call) {
        if (c.peerId == 0L) return
        val list = (listOf(c) + calls()).take(CALLS_MAX)
        MmkvManager.encodeSettings(CALLS, gson.toJson(list))
    }

    fun clearCalls() = MmkvManager.encodeSettings(CALLS, "[]")

    /** Забыть всё локальное про удалённый чат, чтобы он не воскрес пустым. */
    fun forget(contactId: Long) {
        setPinned(contactId, false)
        setMuted(contactId, false)
        setArchived(contactId, false)
        MmkvManager.encodeSettings(seenKey(contactId), "")
    }
}
