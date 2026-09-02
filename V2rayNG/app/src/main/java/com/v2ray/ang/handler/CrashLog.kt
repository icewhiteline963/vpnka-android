package com.v2ray.ang.handler

import android.os.Build
import com.v2ray.ang.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.TimeUnit

/**
 * Последняя авария — чтобы её было кому рассказать.
 *
 * Видимости падений у нас не было вообще: Sentry выключен, крашлитики нет,
 * и падение на телефоне доходило до нас только словами — «открываю YouTube,
 * всё сворачивается». По такому описанию причина ищется чтением диффов и не
 * находится.
 *
 * Работает в два шага, потому что в момент падения сеть трогать нельзя:
 * процесс уже умирает, и попытка отправки чаще всего не успевает.
 *
 *  1. В обработчике — записать трассировку в MMKV и отдать управление
 *     прежнему обработчику, чтобы система повела себя как обычно.
 *  2. При следующем запуске — отправить и стереть.
 */
object CrashLog {

    private const val KEY = "vpnka_last_crash"
    private const val MAX_STACK = 7500

    /** Поставить перехватчик. Зовётся из Application для каждого процесса. */
    fun install() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { store(error) }
            // Прежний обработчик — последним и обязательно: без него не
            // будет ни системного диалога, ни записи в logcat, а процесс
            // повиснет вместо того, чтобы честно умереть.
            prev?.uncaughtException(thread, error)
        }
    }

    private fun store(error: Throwable) {
        val sw = StringWriter()
        error.printStackTrace(PrintWriter(sw))
        val obj = JSONObject()
            .put("version", BuildConfig.VERSION_NAME)
            .put("android", Build.VERSION.RELEASE ?: "")
            .put("device", "${Build.MANUFACTURER} ${Build.MODEL}".take(96))
            .put("at", System.currentTimeMillis())
            .put("kind", (error.toString()).take(256))
            .put("stack", sw.toString().take(MAX_STACK))
        MmkvManager.encodeSettings(KEY, obj.toString())
    }

    /**
     * Отправить отложенную аварию, если она есть.
     *
     * Стираем ДО отправки: не дошло — потеряли один отчёт, а не получили
     * телефон, который шлёт одну и ту же трассировку при каждом запуске.
     */
    fun flush(context: android.content.Context? = null) {
        context?.let { runCatching { flushSystemExits(it) } }
        val raw = MmkvManager.decodeSettingsString(KEY) ?: return
        if (raw.isBlank()) return
        // Стираем ТОЛЬКО после успешной отправки.
        //
        // Сначала было наоборот — «не дошло, потеряли один отчёт». Но именно
        // это и произошло бы в самом частом случае: приложение падает,
        // человек открывает его снова сразу, сети ещё нет — и единственная
        // трассировка, ради которой всё затевалось, исчезает молча. Не
        // ушло — попробуем при следующем запуске.
        if (send(raw)) MmkvManager.encodeSettings(KEY, "")
    }

    private const val KEY_EXIT = "vpnka_last_exit_ts"

    /**
     * Аварии, о которых знает САМА система.
     *
     * Свой перехватчик появляется только в той версии, где его добавили, —
     * а знать надо и о падении предыдущей. Android с 11-й помнит причины
     * последних смертей процесса и хранит для падений трассировку: это
     * ровно то, что иначе пришлось бы просить воспроизвести.
     *
     * Помним отметку времени последнего отправленного, чтобы одно и то же
     * падение не уезжало при каждом запуске.
     */
    private fun flushSystemExits(context: android.content.Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val am = context.getSystemService(android.app.ActivityManager::class.java) ?: return
        val last = MmkvManager.decodeSettingsString(KEY_EXIT)?.toLongOrNull() ?: 0L
        val exits = am.getHistoricalProcessExitReasons(context.packageName, 0, 10)
        var newest = last
        for (e in exits) {
            if (e.timestamp <= last) continue
            if (e.reason != android.app.ApplicationExitInfo.REASON_CRASH &&
                e.reason != android.app.ApplicationExitInfo.REASON_CRASH_NATIVE &&
                e.reason != android.app.ApplicationExitInfo.REASON_ANR
            ) continue
            val trace = runCatching {
                e.traceInputStream?.bufferedReader()?.use { it.readText() }
            }.getOrNull().orEmpty()
            val obj = JSONObject()
                .put("version", BuildConfig.VERSION_NAME)
                .put("android", Build.VERSION.RELEASE ?: "")
                .put("device", "${Build.MANUFACTURER} ${Build.MODEL}".take(96))
                .put("at", e.timestamp)
                .put("kind", "система: ${e.reason} ${e.description.orEmpty()}".take(256))
                .put("stack", (trace.ifBlank { e.description.orEmpty() }).take(MAX_STACK))
            // Отметку двигаем только по ОТПРАВЛЕННЫМ: иначе неудачная
            // попытка (нет сети) навсегда объявляла бы аварию прочитанной.
            if (send(obj.toString()) && e.timestamp > newest) newest = e.timestamp
        }
        if (newest > last) MmkvManager.encodeSettings(KEY_EXIT, newest.toString())
    }

    /** Отправить. `true` — сервер принял. */
    private fun send(body: String): Boolean = runCatching {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .callTimeout(20, TimeUnit.SECONDS)
                .build()
            client.newCall(
                Request.Builder()
                    .url("https://get.vpnka.io/app/diag/crash")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()
            ).execute().use { it.isSuccessful }
    }.getOrDefault(false)
}
