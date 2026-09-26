package com.v2ray.ang.handler

import android.os.Handler
import android.os.Looper
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Сторож главного потока — Фаза 1 отчётов об ошибках.
 *
 * Ловит **зависание (ANR)**, которое обычный краш-хендлер [CrashLog] не видит:
 * главный поток не отвечает дольше порога, но процесс жив — система может его
 * не убить, и системный журнал смертей молчит. Снимаем стек главного потока
 * (где именно встал) и шлём `kind="hang"`. Это покрывает жалобу «иногда виснет,
 * зелёная кнопка не гаснет», КОГДА причина — заблокированный главный поток.
 *
 * Только ЧТЕНИЕ + отправка отчёта, в приложении ничего не меняет. Один
 * демон-поток на процесс, всё в runCatching, со штормконтролем (один отчёт в
 * 5 мин). Отчёт уходит с ЭТОГО фонового потока (сеть на фоне), поэтому зависший
 * главный поток отправке не мешает.
 *
 * ⚠ Детектор «рассинхрона зелёной кнопки» был убран: `CoreServiceManager` —
 * object НА КАЖДЫЙ ПРОЦЕСС, ядро живёт в `:RunSoLibV2RayDaemon`, и в UI-процессе
 * `isRunning()` всегда false → сравнение давало ложный отчёт каждому
 * подключённому. Правильный детектор (сверять мнение UI с реальной живостью
 * туннеля через IPC/пинг сервиса) — задача Фазы 2.
 */
object AnrWatchdog {
    private const val TICK_MS = 2000L
    private const val HANG_MS = 6000L                  // порог зависания главного потока
    private const val REPORT_COOLDOWN_MS = 5 * 60_000L // не чаще раза в 5 мин

    @Volatile private var started = false

    /** Запустить один раз, из главного процесса (см. AngApplication). */
    fun start() {
        if (started) return
        started = true
        Thread({ loop() }, "vpnka-watchdog").apply { isDaemon = true }.start()
    }

    private fun loop() {
        val main = Handler(Looper.getMainLooper())
        var lastHangReport = 0L
        while (true) {
            runCatching {
                // Пост в главный поток должен выполниться в пределах HANG_MS.
                val done = AtomicBoolean(false)
                main.post { done.set(true) }
                var waited = 0L
                while (!done.get() && waited < HANG_MS) {
                    Thread.sleep(200); waited += 200
                }
                if (!done.get()) {
                    val now = System.currentTimeMillis()
                    if (now - lastHangReport > REPORT_COOLDOWN_MS) {
                        lastHangReport = now
                        val stack = Looper.getMainLooper().thread.stackTrace
                            .joinToString("\n") { "\tat $it" }
                        CrashLog.reportNow(
                            "hang",
                            "главный поток не отвечает >${HANG_MS}мс\n$stack",
                        )
                    }
                    // Дождаться восстановления, чтобы не слать один хенг пачкой.
                    while (!done.get()) Thread.sleep(500)
                }
            }.onFailure { LogUtil.w(AppConfig.TAG, "watchdog tick: ${it.message}") }
            runCatching { Thread.sleep(TICK_MS) }
        }
    }
}
