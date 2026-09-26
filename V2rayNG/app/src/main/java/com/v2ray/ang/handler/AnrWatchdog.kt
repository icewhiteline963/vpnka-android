package com.v2ray.ang.handler

import android.os.Handler
import android.os.Looper
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.ui.VpnkaColors
import com.v2ray.ang.util.LogUtil
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Сторож главного потока и рассинхрона состояния — Фаза 1 отчётов об ошибках.
 *
 * Ловит два класса сбоев, которые обычный краш-хендлер [CrashLog] НЕ видит,
 * потому что процесс при них не умирает:
 *
 *  1. **Зависание (ANR).** Главный поток не отвечает дольше порога, но процесс
 *     жив — система может его не убить, и системный журнал смертей молчит.
 *     Снимаем стек главного потока (где именно встал) и шлём `kind="hang"`.
 *  2. **Рассинхрон кнопки.** UI считает «подключено» ([VpnkaColors.connected] —
 *     то, что рисует зелёную кнопку), а ядро на деле не работает
 *     ([CoreServiceManager.isRunning]) дольше порога — ровно «кнопка не
 *     отключается». Шлём `kind="state_desync"`.
 *
 * Только ЧТЕНИЕ состояния + отправка отчёта: в приложении ничего не меняет.
 * Один демон-поток на процесс, всё в runCatching, со штормконтролем — сбой не
 * должен долбить сервер. Отчёты уходят с ЭТОГО фонового потока (сеть на фоне),
 * поэтому зависший главный поток отправке не мешает.
 */
object AnrWatchdog {
    private const val TICK_MS = 2000L
    private const val HANG_MS = 6000L                  // порог зависания главного потока
    private const val REPORT_COOLDOWN_MS = 5 * 60_000L // не чаще раза в 5 мин на класс
    private const val DESYNC_CONFIRM_TICKS = 8         // ~16с подряд, чтобы не ловить переходы

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
        var lastDesyncReport = 0L
        var desyncTicks = 0
        while (true) {
            runCatching {
                // 1) ANR: пост в главный поток должен выполниться в пределах HANG_MS.
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

                // 2) Рассинхрон: кнопка зелёная, а ядро не работает.
                val uiConnected = VpnkaColors.connected
                val coreRunning = runCatching { CoreServiceManager.isRunning() }
                    .getOrDefault(true) // при сомнении НЕ шумим
                if (uiConnected && !coreRunning) {
                    desyncTicks++
                    val now = System.currentTimeMillis()
                    if (desyncTicks >= DESYNC_CONFIRM_TICKS &&
                        now - lastDesyncReport > REPORT_COOLDOWN_MS
                    ) {
                        lastDesyncReport = now
                        val secs = desyncTicks * TICK_MS / 1000
                        CrashLog.reportNow(
                            "state_desync",
                            "UI показывает подключение, ядро не работает ~${secs}с " +
                                "(зелёная кнопка не гаснет).",
                        )
                    }
                } else {
                    desyncTicks = 0
                }
            }.onFailure { LogUtil.w(AppConfig.TAG, "watchdog tick: ${it.message}") }
            runCatching { Thread.sleep(TICK_MS) }
        }
    }
}
