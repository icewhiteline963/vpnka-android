package com.v2ray.ang

import com.v2ray.ang.handler.VpnkaLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тесты на логику форка.
 *
 * До 30.08 у приложения не было ни одного собственного теста, и все восемь
 * дефектов, найденных за два дня, нашлись чтением кода. Каждый случай ниже
 * соответствует настоящей поломке или настоящей ловушке — это не покрытие
 * ради процента.
 */
class VpnkaLogicTest {

    // ---- Сравнение версий -------------------------------------------

    @Test
    fun `версии сравниваются по числам, а не по алфавиту`() {
        // По алфавиту «2.9.7» больше «2.9.64» — и обновление бы не пришло.
        assertTrue(VpnkaLogic.isNewer("2.9.64.0", "2.9.7.0"))
        assertTrue(VpnkaLogic.isNewer("2.10.0.0", "2.9.99.0"))
        assertFalse(VpnkaLogic.isNewer("2.9.63.0", "2.9.64.0"))
    }

    @Test
    fun `равная версия обновлением не считается`() {
        // Иначе установленное обновление предлагает само себя — так и было
        // в 2.9.60.0: файл оставался в кэше и звал поставить то, что стоит.
        assertFalse(VpnkaLogic.isNewer("2.9.64.0", "2.9.64.0"))
    }

    @Test
    fun `префикс v с зеркала не мешает`() {
        // В манифесте tag_name лежит как «v2.9.64.0».
        assertTrue(VpnkaLogic.isNewer("v2.9.65.0", "2.9.64.0"))
        assertFalse(VpnkaLogic.isNewer("v2.9.64.0", "2.9.64.0"))
    }

    @Test
    fun `нечисловой кусок не роняет разбор`() {
        // toInt() бросал бы NumberFormatException, и фоновая задача
        // обновления уходила в вечный повтор — по мобильному трафику тоже.
        assertFalse(VpnkaLogic.isNewer("2.9.64.0-rc1", "2.9.64.0"))
        assertTrue(VpnkaLogic.isNewer("2.9.65.0", "2.9.64.0-rc1"))
        assertEquals(0, VpnkaLogic.compareVersions("мусор", "тоже мусор"))
    }

    @Test
    fun `разная длина версий не путает`() {
        assertFalse(VpnkaLogic.isNewer("2.9.64", "2.9.64.0"))
        assertTrue(VpnkaLogic.isNewer("2.9.64.1", "2.9.64"))
    }

    // ---- Состояние платежа ------------------------------------------

    @Test
    fun `оплачено считается только вместе с названной подпиской`() {
        // Учёт денег опережает выдачу ключа на секунды. Поверить «settled»
        // раньше времени значит обновить экран, на котором ещё ничего нет.
        assertEquals("settled", VpnkaLogic.paymentState(200, true, "paid", "gr-1"))
        assertEquals("pending", VpnkaLogic.paymentState(200, true, "paid", null))
    }

    @Test
    fun `чужой счёт мёртв, а не ожидает`() {
        // 404 приходит, когда токен устройства указывает уже на другого
        // клиента. Пока это читалось как «ещё платят», ключ залипал
        // навсегда и приложение опрашивало сервер вечно.
        assertEquals("dead", VpnkaLogic.paymentState(404, false, null, null))
    }

    @Test
    fun `закрытые без денег счета мёртвые`() {
        for (s in listOf("expired", "failed", "refunded")) {
            assertEquals("dead", VpnkaLogic.paymentState(200, false, s, null))
        }
    }

    @Test
    fun `сетевой сбой не убивает ожидание`() {
        // Один обрыв не должен стоить всего цикла ожидания оплаты.
        assertEquals("pending", VpnkaLogic.paymentState(500, false, null, null))
        assertEquals("pending", VpnkaLogic.paymentState(0, false, null, null))
    }

    @Test
    fun `недоплата остаётся ожиданием`() {
        assertEquals("pending", VpnkaLogic.paymentState(200, false, "underpaid", null))
    }

    // ---- Подписи планов ---------------------------------------------

    @Test
    fun `одинаковые планы различаются датой`() {
        val out = VpnkaLogic.planLabels(
            listOf(
                "🚀 Месяц" to "2026-09-28T18:02:40Z",
                "🚀 Месяц" to "2026-09-27T17:41:50Z",
            )
        )
        assertEquals(listOf("🚀 Месяц · до 28.09", "🚀 Месяц · до 27.09"), out)
    }

    @Test
    fun `единственный план подпись не меняет`() {
        // У кого план один, дата в названии — лишний шум.
        val out = VpnkaLogic.planLabels(listOf("🦁 Год" to "2027-07-11T06:34:40Z"))
        assertEquals(listOf("🦁 Год"), out)
    }

    @Test
    fun `повтор без даты остаётся как есть`() {
        val out = VpnkaLogic.planLabels(listOf("Месяц" to null, "Месяц" to null))
        assertEquals(listOf("Месяц", "Месяц"), out)
    }

    // ---- Незакрытый счёт --------------------------------------------

    @Test
    fun `запись счёта разбирается обратно`() {
        val rec = VpnkaLogic.formatPending(60, 1_700_000_000_000)
        assertEquals(60L, VpnkaLogic.pendingId(rec))
    }

    @Test
    fun `старый формат без отметки времени понимается`() {
        // На дисках уже лежат записи вида «60», без времени.
        assertEquals(60L, VpnkaLogic.pendingId("60"))
        assertFalse(VpnkaLogic.pendingExpired("60", 9_999_999, 1000))
    }

    @Test
    fun `пустая запись не даёт номера`() {
        assertNull(VpnkaLogic.pendingId(""))
        assertNull(VpnkaLogic.pendingId(null))
    }

    @Test
    fun `счёт протухает по сроку`() {
        val day = 24L * 60 * 60 * 1000
        val rec = VpnkaLogic.formatPending(60, 0)
        assertFalse(VpnkaLogic.pendingExpired(rec, day - 1, day))
        assertTrue(VpnkaLogic.pendingExpired(rec, day + 1, day))
    }

    @Test
    fun `чужую запись не стираем`() {
        // Ключ один, а циклов ожидания может быть несколько: старый цикл
        // затирал указатель на новый счёт, и его оплату никто не отслеживал.
        val rec = VpnkaLogic.formatPending(61, 123)
        assertTrue(VpnkaLogic.pendingIsOurs(rec, 61))
        assertFalse(VpnkaLogic.pendingIsOurs(rec, 60))
    }
}
