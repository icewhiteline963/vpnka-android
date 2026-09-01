package com.v2ray.ang.handler

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tencent.mmkv.MMKV
import java.security.SecureRandom

/**
 * Менеджер паролей браузера SmartDesk: по одной записи на узел.
 *
 * Хранилище ЗАШИФРОВАНО — как у мессенджера и рабочего стола. Раньше пароли
 * лежали в общих настройках без ключа, хотя и диалог сохранения, и эта
 * страница обещали обратное: при снятом образе или на руте они читались как
 * есть. Обещание и хранение теперь совпадают.
 *
 * Отдавать пароль странице по запросу нельзя: объект, добавленный в WebView,
 * виден всем фреймам, включая чужой рекламный iframe. Подстановку делает сам
 * браузер, впрыском в главный фрейм и только по https.
 */
object PasswordStore {
    private const val KEY = "vpnka_browser_passwords"
    private const val KEY_CRYPT = "vpnka_pwd_cryptkey"
    private const val ID_STORE = "vpnka_passwords"
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

    data class Cred(val host: String, val username: String, val password: String)

    private fun load(): MutableList<Cred> {
        // Переезд со старого незашифрованного хранилища: перекладываем и
        // стираем оттуда, чтобы открытая копия не осталась лежать.
        MmkvManager.decodeSettingsString(KEY)?.let { legacy ->
            if (legacy.isNotBlank()) {
                store.encode(KEY, legacy)
                MmkvManager.encodeSettings(KEY, "")
            }
        }
        val raw = store.decodeString(KEY) ?: return mutableListOf()
        return try {
            gson.fromJson<MutableList<Cred>>(raw, object : TypeToken<MutableList<Cred>>() {}.type)
                ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    private fun store(list: List<Cred>) {
        store.encode(KEY, gson.toJson(list))
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
