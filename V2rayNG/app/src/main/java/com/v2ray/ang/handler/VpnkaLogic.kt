package com.v2ray.ang.handler

/**
 * Чистая логика форка — без Android, MMKV и сети, поэтому её можно
 * проверить обычными тестами.
 *
 * Вынесено сюда не ради красоты. 29-30.08 в приложении нашли восемь
 * дефектов, и ВСЕ — чтением кода: тестов у форка не было вовсе, а то, что
 * ломалось, сидело внутри Compose-экранов и объектов, завязанных на
 * систему. Здесь лежат ровно те куски, ошибки в которых стоили дороже
 * всего, в виде, который можно вызвать из теста.
 */
object VpnkaLogic {

    // ---- Сравнение версий -------------------------------------------

    /**
     * Сравнить «2.9.64.0» с «2.9.7.0» по-человечески, а не по алфавиту.
     *
     * Нечисловой кусок считаем нулём, а НЕ роняем разбор: строка приходит
     * с зеркала, и одна опечатка в манифесте не должна ни валить экран
     * обновления, ни отправлять фоновую задачу в вечный повтор.
     */
    fun compareVersions(a: String, b: String): Int {
        val x = a.removePrefix("v").split(".")
        val y = b.removePrefix("v").split(".")
        for (i in 0 until maxOf(x.size, y.size)) {
            val p = x.getOrNull(i)?.toIntOrNull() ?: 0
            val q = y.getOrNull(i)?.toIntOrNull() ?: 0
            if (p != q) return p - q
        }
        return 0
    }

    /** Строго новее установленной. Равные версии обновлением НЕ считаются. */
    fun isNewer(candidate: String, installed: String): Boolean =
        compareVersions(candidate, installed) > 0

    // ---- Состояние платежа ------------------------------------------

    /**
     * Во что превращается ответ сервера о платеже.
     *
     * `settled` — деньги учтены И подписка названа; `dead` — счёт закрыт
     * без денег либо он не наш; `pending` — ещё платят, либо связи нет.
     *
     * 404 = `dead`, и это не мелочь: так отвечает сервер, когда токен
     * устройства указывает уже на ДРУГОГО клиента (купил картой на
     * анонимной оболочке, потом вошёл по коду из бота). Пока 404 читался
     * как «ещё платят», ключ счёта залипал навсегда и приложение
     * опрашивало сервер каждые три секунды при каждом открытии.
     */
    fun paymentState(
        httpCode: Int,
        settled: Boolean,
        status: String?,
        groupToken: String?,
    ): String = when {
        httpCode == 404 -> "dead"
        httpCode !in 200..299 -> "pending"
        settled && groupToken != null -> "settled"
        settled -> "pending"
        status in setOf("expired", "failed", "refunded") -> "dead"
        else -> "pending"
    }

    // ---- Подписи планов ---------------------------------------------

    /**
     * Названия планов для списка: одинаковые различаем датой окончания.
     *
     * Три купленных подряд месяца зовутся одним тарифом, и в списке их не
     * отличить. Дату добавляем ТОЛЬКО при повторе имени — у кого план
     * один, подпись остаётся прежней.
     *
     * @param plans пары «название тарифа» → «дата окончания ISO», по
     *              порядку показа.
     */
    fun planLabels(plans: List<Pair<String, String?>>): List<String> {
        val seen = plans.groupingBy { it.first }.eachCount()
        return plans.map { (name, expires) ->
            val parts = expires?.take(10)?.split("-")
            if ((seen[name] ?: 0) > 1 && parts?.size == 3) {
                "$name · до ${parts[2]}.${parts[1]}"
            } else {
                name
            }
        }
    }


    // ---- Синхронизация подписок -------------------------------------

    /** Запись в списке подписок: чем её опознать и что в ней лежит. */
    data class SubEntry(val guid: String, val url: String)

    /**
     * Что сделать со списком подписок.
     *
     * @param remove    какие записи удалить (по guid)
     * @param upsert    какие адреса должны существовать, по порядку показа
     * @param selectUrl какой адрес сделать активным; `null` — оставить как есть
     */
    data class SyncPlan(
        val remove: List<String>,
        val upsert: List<Pair<String, String>>,
        val selectUrl: String?,
    )

    /**
     * Решение о синхронизации подписок — без хранилища.
     *
     * Это самое дорогое место приложения: именно здесь выбирается, какая
     * подписка станет активной после покупки. Ошибка тут не роняет
     * ничего — она просто оставляет человека на старом плане, и покупка
     * выглядит несостоявшейся. Так и было дважды: сначала выбиралась
     * «самая долгоживущая» вместо купленной, потом точный ответ сервера
     * выбрасывался при первой же рассинхронизации.
     *
     * @param wanted    адреса и подписи по порядку; ПЕРВЫЙ считается
     *                  запасным выбором (вызывающий сортирует по остатку дней)
     * @param preferUrl адрес, названный сервером как купленный, — бьёт любые
     *                  догадки
     * @param preferNewest активировать первый, даже если текущий выбор жив
     */
    fun syncPlan(
        existing: List<SubEntry>,
        wanted: List<Pair<String, String>>,
        trialUrl: String,
        ourPrefix: String,
        selectedGuid: String?,
        preferNewest: Boolean,
        preferUrl: String?,
    ): SyncPlan {
        if (wanted.isEmpty()) return SyncPlan(emptyList(), emptyList(), null)

        val wantedUrls = wanted.map { it.first }.toSet()
        // Убираем шипованный триал и наши подписки, которых у аккаунта уже
        // нет. Чужие записи (добавленные вручную не по нашему адресу) не
        // трогаем — они не наши, чтобы ими распоряжаться.
        val remove = existing
            .filter { it.url == trialUrl || (it.url.startsWith(ourPrefix) && it.url !in wantedUrls) }
            .map { it.guid }

        // Жив ли текущий выбор ПОСЛЕ уборки: удалённая запись выбором быть
        // не может, а уцелевшая — может, даже если её нет в `wanted`
        // (например, подписка, добавленная вручную).
        val survives = existing.any { it.guid == selectedGuid && it.guid !in remove }

        val selectUrl = when {
            // Точный ответ сервера. Если названной подписки в списке ещё
            // нет — НЕ выбираем ничего и не сбрасываем на догадку: пусть
            // решение примет следующий круг, когда профиль догонит.
            preferUrl != null && preferUrl in wantedUrls -> preferUrl
            preferUrl != null -> null
            preferNewest || !survives -> wanted.first().first
            else -> null
        }
        return SyncPlan(remove = remove, upsert = wanted, selectUrl = selectUrl)
    }

    // ---- Незакрытый счёт --------------------------------------------

    /** Как счёт хранится на диске: «номер:когда_заведён». */
    fun formatPending(paymentId: Long, nowMs: Long): String = "$paymentId:$nowMs"

    /** Номер счёта из записи, или null. */
    fun pendingId(stored: String?): Long? =
        stored?.substringBefore(':')?.toLongOrNull()

    /**
     * Пора ли забыть счёт по сроку.
     *
     * Ответ «ещё платят» приходит и когда связи нет, и когда сервер
     * отвечает непонятно. Без крайнего срока такой счёт опрашивался бы
     * вечно. Запись без отметки времени (старый формат) не протухает —
     * её добьёт ответ сервера.
     */
    fun pendingExpired(stored: String?, nowMs: Long, ttlMs: Long): Boolean {
        val born = stored?.substringAfter(':', "")?.toLongOrNull() ?: return false
        return nowMs - born > ttlMs
    }

    /**
     * Наш ли это счёт — перед тем как стирать запись.
     *
     * Ключ один, а циклов ожидания может быть несколько: человек успел
     * завести второй счёт, пока первый ещё висел. Безусловное стирание
     * затирало указатель на новый счёт, и его оплату никто не отслеживал.
     */
    fun pendingIsOurs(stored: String?, paymentId: Long): Boolean =
        pendingId(stored) == paymentId
}
