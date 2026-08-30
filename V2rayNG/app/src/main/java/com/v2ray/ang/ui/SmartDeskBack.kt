package com.v2ray.ang.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

/**
 * «Назад» внутри приложений рабочего стола.
 *
 * Раньше обработчик стола знал только про свои оверлеи и про факт «открыто
 * какое-то приложение» — а что происходит ВНУТРИ него, не знал. Поэтому
 * системная «назад» выбрасывала из приложения целиком:
 *
 *  · в браузере вместо предыдущей страницы — вылет на стол, причём с
 *    потерей ВСЕХ вкладок (при выходе они принудительно перезагружаются);
 *  · в заметках — молча стирала написанное, потому что сохраняет только
 *    кнопка «Готово»;
 *  · в мессенджере — закрывала мессенджер, а не возвращала к списку чатов;
 *  · в YouTube — уводила на стол прямо из полноэкранного просмотра.
 *
 * «Назад» — самый частый жест на Android, и именно это владелец имел в
 * виду, говоря «навигация страдает».
 *
 * Теперь приложение может заявить свой обработчик. Стол спрашивает верхний
 * из заявленных, и только если никто не взялся — закрывает приложение.
 */
object SmartDeskBackStack {
    private val handlers = mutableListOf<() -> Boolean>()

    fun push(handler: () -> Boolean) {
        handlers.add(handler)
    }

    fun remove(handler: () -> Boolean) {
        handlers.remove(handler)
    }

    /**
     * Спросить обработчики сверху вниз. `true` — кто-то взялся, и стол
     * трогать приложение не должен.
     */
    fun handle(): Boolean {
        // Копия: обработчик может сняться прямо во время вызова.
        for (h in handlers.toList().asReversed()) {
            if (h()) return true
        }
        return false
    }
}

/**
 * Объявить обработчик «назад» для текущего экрана приложения.
 *
 * Верните `true`, если шаг назад сделан внутри приложения, и `false`, если
 * идти дальше некуда — тогда стол закроет приложение сам.
 */
@Composable
fun SmartDeskBackHandler(onBack: () -> Boolean) {
    val current by rememberUpdatedState(onBack)
    DisposableEffect(Unit) {
        // Обёртка нужна, чтобы снять ИМЕННО свой обработчик: лямбда,
        // созданная заново на каждой перерисовке, не равна прежней.
        val handler: () -> Boolean = { current() }
        SmartDeskBackStack.push(handler)
        onDispose { SmartDeskBackStack.remove(handler) }
    }
}
