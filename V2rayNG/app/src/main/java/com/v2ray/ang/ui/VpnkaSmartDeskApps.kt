package com.v2ray.ang.ui

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.v2ray.ang.handler.BrowserHistory
import com.v2ray.ang.handler.YouTubeService
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.PasswordStore
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SmartDeskStore
import com.v2ray.ang.handler.SmartDeskSync
import kotlinx.coroutines.launch

/**
 * Dispatch a desktop icon to its real app screen. Back to the desktop and exit
 * to the vpnka home both live in the bottom bar (split 50/50), so the top edge
 * stays clear of controls.
 */
/** Lets a SmartDesk app hide the host app bar (back + icon) while a nested
 *  screen is open — set true on entering a chat/nested menu, false on leaving. */
/**
 * Подсказка внизу экрана с одним действием.
 *
 * Системный Toast сообщал «файл добавлен в загрузки» и на этом заканчивался:
 * чтобы попасть в загрузки, надо было выйти, найти «Видео», открыть вкладку.
 * Здесь у сообщения есть кнопка, ведущая туда, о чём оно сообщает.
 *
 * Действие описано СТРОКОЙ, а не лямбдой: рисует подсказку рабочий стол, и
 * он же умеет открывать приложения — передавать ему замыкание из чужого
 * экрана значило бы тащить его состояние наружу.
 */
object SmartDeskToast {
    var text by mutableStateOf<String?>(null)
        private set
    var actionLabel by mutableStateOf<String?>(null)
        private set

    /** Что сделать по кнопке: "downloads" — открыть загрузки. null — нечего. */
    var action by mutableStateOf<String?>(null)
        private set

    /** Растёт с каждым показом — по нему заводится таймер скрытия. */
    var seq by mutableStateOf(0)
        private set

    fun show(message: String, label: String? = null, act: String? = null) {
        text = message
        actionLabel = if (act == null) null else label
        action = act
        seq++
    }

    fun dismiss() { text = null; actionLabel = null; action = null }
}

object SmartDeskChrome {
    var barHidden by mutableStateOf(false)

    // A home-screen shortcut (see VpnkaSmartDeskScreen "Добавить на рабочий
    // стол") deep-links here via MainActivity with the target app id; the
    // desktop consumes it on launch and opens that app.
    // Compose-состояние, а не обычное поле: просьба приходит из уведомления,
    // когда рабочий стол УЖЕ на экране, и новой композиции не будет —
    // однократное чтение при рождении её не замечало, а потом она
    // «выстреливала» при следующем заходе на стол, уводя в давно протухший чат.
    var pendingAppId by mutableStateOf<String?>(null)

    /** Что открыть в плеере: мини-плеер вёл в список, а не к играющему. */
    var pendingPlayback by mutableStateOf<YouTubeService.Playback?>(null)

    fun consumePendingPlayback(): YouTubeService.Playback? {
        val p = pendingPlayback
        pendingPlayback = null
        return p
    }

    /**
     * Куда открыть «Видео»: 2 — сразу на вкладку загрузок (нижняя панель).
     *
     * Compose-состояние, а не обычное поле: если «Видео» УЖЕ открыто, новой
     * композиции не будет, и просьба останется непрочитанной — кнопка
     * «Загрузки» молча не делала ничего, а зависшее значение потом уводило
     * следующий вход в «Видео» не на ту вкладку.
     */
    var pendingYtTab by mutableStateOf<Int?>(null)

    /**
     * Какая полка «Видео» открыта СЕЙЧАС — чтобы нижняя панель знала, где
     * человек находится.
     *
     * Панель считала выбранным пункт по имени приложения, а внутри «Видео»
     * полок несколько. Открыв «Загрузки», человек оставался в том же
     * приложении — значит пункт «Видео» считался текущим, и нажатие по нему
     * панель игнорировала («по текущему не переходим»). Обратно из загрузок
     * не было пути вовсе.
     */
    var ytTab by mutableStateOf(0)

    fun consumePendingYtTab(): Int? {
        val t = pendingYtTab
        pendingYtTab = null
        return t
    }

    fun consumePendingApp(): String? {
        val a = pendingAppId
        pendingAppId = null
        return a
    }
}

@Composable
fun VpnkaSmartDeskAppScreen(
    appId: String,
    /**
     * Возврат к столу. Своей кнопки у приложения нет: обычно это «⌂ Стол» в
     * общей панели, а в мессенджере (он прячет панель ради собственной) —
     * только системная «назад».
     */
    onBack: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(VpnkaColors.BgOffMid)) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            // Только ВЕРХНЯЯ вставка. Нижнюю уже отдал стол вместе с
            // высотой панели (`bottomOverlay`), и systemBarsPadding добавлял
            // её второй раз — внизу каждого приложения оставалась мёртвая
            // полоса высотой ещё одной системной панели.
            .statusBarsPadding()
            // Lift the whole app above the soft keyboard whenever any input
            // (messenger, browser omnibox, YouTube search, editors) opens it,
            // so the bottom controls are never hidden behind it.
            .imePadding(),
    ) {
        // The host app bar (back + icon) shows only on an app's main screen. An
        // app hides it while a nested screen is open (e.g. a chat) via
        // SmartDeskChrome, so its own header isn't stacked under ours. Reset on
        // every app open so a fresh app always starts with the bar shown.
        // Сброс флага — СИНХРОННЫЙ и до эффектов приложения.
        //
        // Был LaunchedEffect: его тело запускается корутиной, то есть ПОЗЖЕ
        // синхронного DisposableEffect мессенджера, который панель прячет.
        // Порядок выходил «спрятать → показать», и в мессенджере всё это
        // время висели две пятипунктовые полосы одна над другой. Правку я
        // объявлял в прошлой версии, но в файл она не попала.
        val shownApp = remember(appId) { appId }
        DisposableEffect(shownApp) {
            SmartDeskChrome.barHidden = false
            onDispose {}
        }
        // Sync on open (through the VPN) and after every edit; syncTick keys
        // each app's list so it re-reads once the server's view is merged in.
        val scope = rememberCoroutineScope()
        var syncTick by remember { mutableIntStateOf(0) }
        val onChanged = {
            // Связь берём из общего состояния: отдельным параметром её сюда
            // передавала верхняя полоса, которой больше нет.
            scope.launch { if (VpnkaColors.connected) SmartDeskSync.sync(); syncTick++ }
            Unit
        }
        LaunchedEffect(Unit) {
            if (VpnkaColors.connected && appId != "browser") { SmartDeskSync.sync(); syncTick++ }
        }
        // Своей верхней полосы у приложения больше нет.
        //
        // Там были «‹», значок приложения и точка связи — и всё это уже есть
        // внизу: «⌂ Стол» возвращает туда же, куда «‹», значок открытого
        // приложения подсвечен в панели, состояние связи — на карточке стола.
        // Полоса занимала полсантиметра сверху и не давала ничего нового.
        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            Box(modifier = Modifier.fillMaxSize()) {
            when (appId) {
                "calendar" -> CalendarApp(syncTick, onChanged)
                "contacts" -> ContactsApp(syncTick, onChanged)
                "browser" -> BrowserApp()
                "youtube" -> YouTubeApp()
                "notes" -> VpnkaNotesApp(syncTick, onChanged)
                "messages" -> VpnkaMessengerApp()
                "store" -> VpnkaStoreApp()
                "help" -> HelpApp()
                else -> EmptyHint("Приложение недоступно")
            }
            }
        }
        // Общие вкладки приложения — как в макете «Поток».
        //
        // Раньше из «Видео» в «Чаты» можно было попасть только через
        // главный экран: закрыть приложение, найти значок, открыть. Пять
        // разделов, между которыми люди ходят чаще всего, стоят внизу и
        // переключаются одним нажатием.
        //
        // Панель прячется, когда приложение занимает экран целиком (чат,
        // плеер) — там свои элементы у нижнего края, и вторая полоса поверх
        // них мешала бы.
        // В браузере нижней панели нет.
        //
        // У него своя строка адреса и свои кнопки у нижнего края, и вторая
        // полоса поверх них — это две панели одна на другой. Выход из
        // браузера остаётся системной «назад»: она ведёт на главный экран.
        if (!SmartDeskChrome.barHidden && appId != "browser") {
            SmartDeskAppTabs(
                current = appId,
                onOpen = { id ->
                    when (id) {
                        "home" -> onBack()
                        // «Загрузки» — не отдельное приложение, а полка
                        // внутри «Видео»: очередь и скачанное живут там.
                        // Вкладка открывает «Видео» сразу на ней.
                        "downloads" -> {
                            SmartDeskChrome.pendingYtTab = 2
                            SmartDeskChrome.pendingAppId = "youtube"
                        }
                        // «Видео» из загрузок — обратно на ленту. Раньше это
                        // была просьба открыть приложение, которое уже
                        // открыто: ничего не происходило.
                        "youtube" -> {
                            SmartDeskChrome.pendingYtTab = 0
                            SmartDeskChrome.pendingAppId = "youtube"
                        }
                        else -> SmartDeskChrome.pendingAppId = id
                    }
                },
            )
        }
    }
        SmartDeskStatusScrim()
    }
}

/**
 * Нижние вкладки супер-приложения.
 *
 * Только то, что относится к самому разделу: видео, его загрузки и выход
 * на главный экран. «Чаты» и «Браузер» отсюда убраны — это отдельные
 * приложения со своими значками на главном, и их место в нижней панели
 * означало, что из загрузчика видео человек одним касанием уходит в чужой
 * раздел, а обратно возвращается уже другим путём.
 *
 * «Главная» возвращает на экран с подключением — там же подписка,
 * настройки и остальные значки. Отдельного экрана профиля не заводим:
 * всё, что макет кладёт в профиль, у нас уже есть на главном.
 */
@Composable
private fun SmartDeskAppTabs(current: String, onOpen: (String) -> Unit) {
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Порядок по решению владельца: сначала выход на главную, потом сам
    // раздел и его полка.
    val items = listOf(
        Triple("home", "⌂", "Главная"),
        Triple("youtube", "▶", "Видео"),
        Triple("downloads", "↓", "Загрузки"),
    )
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(VpnkaColors.BgOffCentre)
            .padding(top = 3.dp, bottom = 3.dp + navInset),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { (id, glyph, label) ->
            // Внутри «Видео» выбранность решает полка, а не приложение.
            val selected = when {
                current != "youtube" -> id == current
                id == "downloads" -> SmartDeskChrome.ytTab == 2
                id == "youtube" -> SmartDeskChrome.ytTab != 2
                else -> false
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                    .clickable { if (!selected) onOpen(id) }
                    .padding(horizontal = 12.dp, vertical = 3.dp),
            ) {
                Text(
                    glyph, fontSize = 14.sp,
                    color = if (selected) VpnkaColors.Accent else VpnkaColors.TextMuted,
                )
                Text(
                    label, fontFamily = VpnkaFonts.manrope700, fontSize = 9.5.sp,
                    lineHeight = 11.sp,
                    color = if (selected) VpnkaColors.Accent else VpnkaColors.TextMuted,
                )
            }
        }
    }
}

/**
 * Тёмная полоска за системным статус-баром Android. Белые иконки (часы,
 * батарея, сеть) невидимы на светлом/жёлтом фоне SmartDesk, поэтому под них
 * подкладываем тёмный скрим ровно в высоту статус-бара. Рисуется поверх
 * контента, у самого верха экрана.
 */
@Composable
fun SmartDeskStatusScrim() {
    val h = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    if (h > 0.dp) {
        Box(modifier = Modifier.fillMaxWidth().height(h).background(Color(0xFF181B21)))
    }
}


// ---------------------------------------------------------------- Calendar ---

private val MONTHS_RU = listOf(
    "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
    "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
)
private val WEEKDAYS_RU = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

@Composable
private fun CalendarApp(syncTick: Int, onChanged: () -> Unit) {
    var items by remember(syncTick) { mutableStateOf(SmartDeskStore.calendar()) }
    var month by remember { mutableStateOf(java.time.YearMonth.now()) }
    var selected by remember { mutableStateOf(java.time.LocalDate.now()) }
    var editing by remember { mutableStateOf<SmartDeskStore.CalendarEvent?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    fun reload() { items = SmartDeskStore.calendar(); onChanged() }

    val byDay = items.filter { it.dateIso.isNotBlank() }.groupBy { it.dateIso }
    val dayEvents = byDay[selected.toString()].orEmpty()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            // Month header with prev/next.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "‹",
                    fontSize = 24.sp,
                    color = VpnkaColors.TextStrong,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp))
                        .clickable { month = month.minusMonths(1) }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
                Text(
                    text = "${MONTHS_RU[month.monthValue - 1]} ${month.year}",
                    fontFamily = VpnkaFonts.nunito800,
                    fontSize = 18.sp,
                    color = VpnkaColors.TextStrong,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Text(
                    text = "›",
                    fontSize = 24.sp,
                    color = VpnkaColors.TextStrong,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp))
                        .clickable { month = month.plusMonths(1) }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            // Weekday header.
            Row(modifier = Modifier.fillMaxWidth()) {
                WEEKDAYS_RU.forEach { d ->
                    Text(
                        text = d,
                        fontFamily = VpnkaFonts.manrope600,
                        fontSize = 12.sp,
                        color = VpnkaColors.TextMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            // Day grid: 6 rows × 7. Monday-based offset.
            val first = month.atDay(1)
            val offset = (first.dayOfWeek.value - 1) // Mon=0
            val daysInMonth = month.lengthOfMonth()
            val today = java.time.LocalDate.now()
            for (week in 0 until 6) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (dow in 0 until 7) {
                        val dayNum = week * 7 + dow - offset + 1
                        val inMonth = dayNum in 1..daysInMonth
                        val date = if (inMonth) month.atDay(dayNum) else null
                        val iso = date?.toString()
                        val has = iso != null && byDay.containsKey(iso)
                        val isSel = date != null && date == selected
                        val isToday = date != null && date == today
                        Box(
                            modifier = Modifier.weight(1f).height(44.dp)
                                .padding(2.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    when {
                                        isSel -> VpnkaColors.Accent.copy(alpha = 0.85f)
                                        isToday -> VpnkaColors.Accent.copy(alpha = 0.15f)
                                        else -> Color.Transparent
                                    }
                                )
                                .clickable(enabled = inMonth) { if (date != null) selected = date },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (inMonth) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "$dayNum",
                                        fontFamily = VpnkaFonts.nunito800,
                                        fontSize = 14.sp,
                                        color = if (isSel) Color.White else VpnkaColors.TextStrong,
                                    )
                                    if (has) {
                                        Box(
                                            modifier = Modifier.size(5.dp).clip(CircleShape)
                                                .background(if (isSel) Color.White else VpnkaColors.Accent),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            // Events for the selected day.
            Text(
                text = "${selected.dayOfMonth} ${MONTHS_RU[selected.monthValue - 1].lowercase()}",
                fontFamily = VpnkaFonts.nunito800,
                fontSize = 15.sp,
                color = VpnkaColors.TextStrong,
            )
            Spacer(Modifier.height(6.dp))
            if (dayEvents.isEmpty()) {
                Text(
                    text = "На этот день событий нет",
                    fontFamily = VpnkaFonts.manrope600,
                    fontSize = 13.sp,
                    color = VpnkaColors.TextMuted,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    // Место под плавающую «+» — иначе последняя запись лежит
                    // под кнопкой и касание по ней открывает редактор.
                    contentPadding = PaddingValues(bottom = 76.dp),
                ) {
                    items(dayEvents, key = { it.id }) { e ->
                        Card(
                            title = e.title.ifBlank { "Без названия" },
                            subtitle = listOf(e.whenText, e.note).filter { it.isNotBlank() }.joinToString(" · "),
                            onClick = { editing = e; showEditor = true },
                        )
                    }
                }
            }
        }
        // Add on the selected day.
        Box(
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).size(56.dp)
                .clip(CircleShape).background(VpnkaColors.Accent)
                .clickable { editing = null; showEditor = true },
            contentAlignment = Alignment.Center,
        ) { Text("+", fontSize = 30.sp, color = VpnkaColors.OnAccent) }
    }

    if (showEditor) {
        val e = editing
        var title by remember(e) { mutableStateOf(e?.title ?: "") }
        var whenText by remember(e) { mutableStateOf(e?.whenText ?: "") }
        var note by remember(e) { mutableStateOf(e?.note ?: "") }
        EditorDialog(
            heading = if (e == null) "Событие · ${selected}" else "Событие",
            canDelete = e != null,
            onDismiss = { showEditor = false },
            onDelete = { e?.let { SmartDeskStore.deleteEvent(it.id) }; reload(); showEditor = false },
            onSave = {
                SmartDeskStore.saveEvent(
                    SmartDeskStore.CalendarEvent(
                        id = e?.id ?: SmartDeskStore.newId(),
                        title = title.trim(),
                        dateIso = e?.dateIso?.ifBlank { selected.toString() } ?: selected.toString(),
                        whenText = whenText.trim(),
                        note = note.trim(),
                        updatedAt = nowMillis(),
                    )
                )
                reload(); showEditor = false
            },
        ) {
            DeskField("Название", title) { title = it }
            DeskField("Время (напр. 15:00)", whenText) { whenText = it }
            DeskField("Заметка", note, minLines = 2) { note = it }
        }
    }
}

// ---------------------------------------------------------------- Contacts ---

@Composable
private fun ContactsApp(syncTick: Int, onChanged: () -> Unit) {
    val context = LocalContext.current
    var all by remember(syncTick) { mutableStateOf(SmartDeskStore.contacts()) }
    var query by remember { mutableStateOf("") }
    var opened by remember { mutableStateOf<SmartDeskStore.Contact?>(null) }
    var editing by remember { mutableStateOf<SmartDeskStore.Contact?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    fun reload() { all = SmartDeskStore.contacts(); onChanged() }

    val filtered = all
        .filter {
            query.isBlank() ||
                it.name.contains(query, true) ||
                it.phone.contains(query, true) ||
                it.email.contains(query, true)
        }
        .sortedBy { it.name.lowercase() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                DeskField("Поиск", query) { query = it }
            }
            if (filtered.isEmpty()) {
                EmptyHint(if (all.isEmpty()) "Контактов пока нет. Добавьте первый." else "Ничего не найдено")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    // Место под плавающую «+»: без него последняя строка
                    // лежала под кнопкой, и касание по ней открывало
                    // редактор вместо самой записи.
                    contentPadding = PaddingValues(bottom = 76.dp),
                ) {
                    items(filtered, key = { it.id }) { c ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(VpnkaColors.CardServer)
                                .border(1.dp, VpnkaColors.Hairline, RoundedCornerShape(12.dp))
                                .clickable { opened = c }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(c.name)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = c.name.ifBlank { "Без имени" },
                                    fontFamily = VpnkaFonts.nunito800,
                                    fontSize = 16.sp,
                                    color = VpnkaColors.TextStrong,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                val sub = listOf(c.phone, c.email).firstOrNull { it.isNotBlank() }.orEmpty()
                                if (sub.isNotBlank()) {
                                    Text(sub, fontFamily = VpnkaFonts.manrope600, fontSize = 13.sp, color = VpnkaColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
        Box(
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).size(56.dp)
                .clip(CircleShape).background(VpnkaColors.Accent)
                .clickable { editing = null; showEditor = true },
            contentAlignment = Alignment.Center,
        ) { Text("+", fontSize = 30.sp, color = VpnkaColors.OnAccent) }
    }

    // Карточка контакта — ПОВЕРХ списка и с перехватом «назад».
    //
    // Раньше она объявлялась ДО списка, а в Compose позже объявленный
    // ребёнок рисуется сверху: карточка оказывалась под списком, тап по
    // контакту визуально не делал ничего, а «назад» закрывал всё приложение,
    // потому что своего обработчика у контактов не было вовсе.
    opened?.let { c ->
        SmartDeskBackHandler { opened = null; true }
        ContactDetail(
            c = c,
            onCall = { openIntent(context, "tel:" + c.phone) },
            onEmail = { openIntent(context, "mailto:" + c.email) },
            onEdit = { editing = c; showEditor = true },
            onBack = { opened = null },
        )
    }

    if (showEditor) {
        val c = editing
        var name by remember(c) { mutableStateOf(c?.name ?: "") }
        var phone by remember(c) { mutableStateOf(c?.phone ?: "") }
        var email by remember(c) { mutableStateOf(c?.email ?: "") }
        var note by remember(c) { mutableStateOf(c?.note ?: "") }
        EditorDialog(
            heading = if (c == null) "Новый контакт" else "Контакт",
            canDelete = c != null,
            onDismiss = { showEditor = false },
            onDelete = { c?.let { SmartDeskStore.deleteContact(it.id) }; opened = null; reload(); showEditor = false },
            onSave = {
                val saved = SmartDeskStore.Contact(
                    id = c?.id ?: SmartDeskStore.newId(),
                    name = name.trim(), phone = phone.trim(), email = email.trim(), note = note.trim(),
                    updatedAt = nowMillis(),
                )
                SmartDeskStore.saveContact(saved)
                if (opened != null) opened = saved
                reload(); showEditor = false
            },
        ) {
            DeskField("Имя", name) { name = it }
            DeskField("Телефон", phone) { phone = it }
            DeskField("Email", email) { email = it }
            DeskField("Заметка", note, minLines = 2) { note = it }
        }
    }
}

@Composable
private fun Avatar(name: String) {
    val initials = name.trim().split(" ").filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.first().uppercase() }.ifBlank { "?" }
    Box(
        modifier = Modifier.size(42.dp).clip(CircleShape).background(VpnkaColors.Accent.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, fontFamily = VpnkaFonts.nunito800, fontSize = 15.sp, color = Color.White)
    }
}

@Composable
private fun ContactDetail(
    c: SmartDeskStore.Contact,
    onCall: () -> Unit,
    onEmail: () -> Unit,
    onEdit: () -> Unit,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp)
                .clip(RoundedCornerShape(14.dp)).background(VpnkaColors.BgOffCentre)
                .clickable(enabled = false) {}
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Avatar(c.name)
            Spacer(Modifier.height(10.dp))
            Text(c.name.ifBlank { "Без имени" }, fontFamily = VpnkaFonts.nunito900, fontSize = 20.sp, color = VpnkaColors.TextStrong)
            Spacer(Modifier.height(14.dp))
            if (c.phone.isNotBlank()) DetailRow("📞", c.phone, onCall)
            if (c.email.isNotBlank()) DetailRow("✉️", c.email, onEmail)
            if (c.note.isNotBlank()) DetailRow("📝", c.note, null)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                VpnkaSecondaryButton(text = "Изменить", onClick = onEdit)
                VpnkaSecondaryButton(text = "Закрыть", onClick = onBack)
            }
        }
    }
}

@Composable
private fun DetailRow(glyph: String, value: String, onClick: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(glyph, fontSize = 18.sp)
        Spacer(Modifier.width(12.dp))
        Text(value, fontFamily = VpnkaFonts.manrope600, fontSize = 15.sp, color = if (onClick != null) VpnkaColors.Accent else VpnkaColors.TextStrong)
    }
}

private fun openIntent(context: android.content.Context, uri: String) {
    try {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uri))
        )
    } catch (e: Exception) {
        // No dialer/mail app — silently ignore; the value is still visible.
    }
}

// ----------------------------------------------------------- shared pieces ---


@Composable
private fun Card(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(VpnkaColors.CardServer)
            .border(1.dp, VpnkaColors.Hairline, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = title,
            fontFamily = VpnkaFonts.nunito800,
            fontSize = 16.sp,
            color = VpnkaColors.TextStrong,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontFamily = VpnkaFonts.manrope600,
                fontSize = 13.sp,
                color = VpnkaColors.TextMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            fontFamily = VpnkaFonts.manrope600,
            fontSize = 14.sp,
            color = VpnkaColors.TextMuted,
        )
    }
}

/** «Помощь» — a short, persuasive privacy pitch for SmartDesk. */
@Composable
private fun HelpApp() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Text("🛡️", fontSize = 40.sp)
        Spacer(Modifier.height(8.dp))
        Text("VPNka облако", fontFamily = VpnkaFonts.nunito900, fontSize = 26.sp, color = VpnkaColors.TextStrong)
        Spacer(Modifier.height(4.dp))
        Text(
            "Скрытый рабочий стол для тех, кому важна приватность. О нём знаете только вы — снаружи это обычное VPN-приложение.",
            fontFamily = VpnkaFonts.manrope600, fontSize = 14.sp, color = VpnkaColors.TextMuted,
        )
        Spacer(Modifier.height(18.dp))
        HelpCard(
            "🔐", "Шифрование на устройстве",
            "Заметки, контакты, переписка шифруются прямо в телефоне, в защищённом контейнере. Ключи никогда не покидают устройство — у сервера их нет.",
        )
        HelpCard(
            "☁️", "Сразу в облако",
            "Данные мгновенно уходят в облако уже зашифрованными. Отобрали телефон — внутри пусто: расшифровать нечем.",
        )
        HelpCard(
            "🧹", "Самоочистка",
            "На самом телефоне ничего не оседает: после синхронизации локальная копия стирается. Остаётся лишь зашифрованный контейнер, который без вашего ключа — просто шум.",
        )
        HelpCard(
            "🕶️", "Только для своих",
            "Раздел не виден посторонним и не оставляет следов. Весь трафик идёт через наш VPN — ни провайдер, ни кто-либо ещё не видит, чем вы пользуетесь.",
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Приватность здесь — не галочка в настройках, а то, как всё устроено с самого начала.",
            fontFamily = VpnkaFonts.nunito800, fontSize = 14.sp, color = VpnkaColors.Accent,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HelpCard(glyph: String, title: String, body: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(VpnkaColors.CardServer)
            .border(1.dp, VpnkaColors.Hairline, RoundedCornerShape(13.dp))
            .padding(16.dp),
    ) {
        Text(glyph, fontSize = 26.sp)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, fontFamily = VpnkaFonts.nunito800, fontSize = 15.sp, color = VpnkaColors.TextStrong)
            Spacer(Modifier.height(3.dp))
            Text(body, fontFamily = VpnkaFonts.manrope600, fontSize = 13.sp, color = VpnkaColors.TextMuted)
        }
    }
}

@Composable
private fun EditorDialog(
    heading: String,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: () -> Unit,
    fields: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(heading, fontFamily = VpnkaFonts.nunito800, color = VpnkaColors.TextStrong) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { fields() }
        },
        confirmButton = { TextButton(onClick = onSave) { Text("Сохранить") } },
        dismissButton = {
            Row {
                if (canDelete) TextButton(onClick = onDelete) { Text("Удалить", color = VpnkaColors.Warning) }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
        containerColor = VpnkaColors.BgOffCentre,
    )
}

@Composable
private fun DeskField(label: String, value: String, minLines: Int = 1, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, color = VpnkaColors.TextMuted) },
        singleLine = minLines == 1,
        minLines = minLines,
        textStyle = LocalTextStyle.current.copy(color = VpnkaColors.TextStrong, fontSize = 15.sp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = VpnkaColors.TextStrong,
            unfocusedTextColor = VpnkaColors.TextStrong,
            cursorColor = VpnkaColors.Accent,
            focusedBorderColor = VpnkaColors.Accent,
            unfocusedBorderColor = VpnkaColors.TextFaint,
            focusedLabelColor = VpnkaColors.Accent,
            unfocusedLabelColor = VpnkaColors.TextMuted,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

// ----------------------------------------------------------------- Browser ---

/**
 * In-desktop browser whose traffic goes ONLY through our VPN.
 *
 * The app excludes itself from the VPN's TUN (so the core doesn't loop), which
 * means our own WebView traffic would otherwise egress in the clear. Instead
 * we point the WebView at xray's local HTTP proxy (127.0.0.1:httpPort) via
 * ProxyController — that forwards through the tunnel to our servers. No direct
 * fallback rule is added, so with the VPN off (proxy port not listening) pages
 * simply fail to load: fail-closed, exactly the "exclusively through our VPN"
 * requirement. The screen is also gated on the VPN being up for a clear message
 * rather than a raw error.
 */
/** Lightweight ad/tracker blocker for the SmartDesk browser: drops WebView
 *  resource requests to known ad/analytics domains (and their subdomains).
 *  On by default; toggled from the browser menu. */
internal object AdBlocker {
    private const val KEY = "vpnka_browser_adblock"
    private const val KEY_COUNT = "vpnka_browser_adblock_count"

    // Счётчик копится в памяти и сбрасывается на диск пачками: запись в MMKV
    // на каждый заблокированный запрос — это сотни записей на одну страницу.
    //
    // Считаем ПОД замком: shouldInterceptRequest зовётся WebView с фоновых
    // потоков, при нескольких вкладках — параллельно, и без синхронизации
    // инкременты просто терялись.
    private val lock = Any()
    private var unsaved = 0
    private var counted = 0L

    /** Compose-состояние: плитка «Заблокировано» обновляется живьём. */
    var blocked by mutableStateOf(-1L)
        private set

    private fun ensureLoaded() {
        if (blocked < 0) {
            counted = MmkvManager.decodeSettingsString(KEY_COUNT)?.toLongOrNull() ?: 0L
            blocked = counted
        }
    }

    private fun countOne() {
        synchronized(lock) {
            ensureLoaded()
            counted += 1
            unsaved++
            if (unsaved >= 20) {
                MmkvManager.encodeSettings(KEY_COUNT, counted.toString())
                unsaved = 0
                // Состояние обновляем ПАЧКАМИ, а не на каждый запрос: оно
                // читается плиткой на столе, а стол остаётся живым под
                // открытым браузером — иначе выходили сотни перерисовок на
                // одну страницу.
                blocked = counted
            }
        }
    }

    /** Дописать хвост на диск — при уходе с экрана, чтобы он не пропадал. */
    fun flush() {
        synchronized(lock) {
            if (unsaved > 0) {
                MmkvManager.encodeSettings(KEY_COUNT, counted.toString())
                unsaved = 0
                blocked = counted
            }
        }
    }

    /** Показание для интерфейса (подгружает с диска при первом обращении). */
    fun blockedNow(): Long {
        synchronized(lock) { ensureLoaded() }
        return blocked
    }

    /** Сбросить хвост на диск при уходе с экрана — иначе он теряется. */
    fun flushOnLeave() = flush()
    var enabled: Boolean
        get() = MmkvManager.decodeSettingsString(KEY) != "0"
        set(v) { MmkvManager.encodeSettings(KEY, if (v) "1" else "0") }

    // Registrable domains of major ad / tracking networks — subdomains match too
    // (we walk the host up to its registrable domain).
    private val BLOCKED = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "google-analytics.com", "googletagmanager.com", "googletagservices.com",
        "adservice.google.com", "app-measurement.com", "admob.com",
        "amazon-adsystem.com", "adnxs.com", "adsrvr.org", "rubiconproject.com",
        "pubmatic.com", "openx.net", "criteo.com", "criteo.net", "taboola.com",
        "outbrain.com", "scorecardresearch.com", "quantserve.com", "quantcount.com",
        "moatads.com", "adform.net", "casalemedia.com", "smartadserver.com",
        "yieldmo.com", "sharethrough.com", "teads.tv", "3lift.com", "bidswitch.net",
        "serving-sys.com", "adroll.com", "bluekai.com", "demdex.net", "rlcdn.com",
        "crwdcntrl.net", "agkn.com", "mathtag.com", "advertising.com", "adtechus.com",
        "contextweb.com", "gumgum.com", "inmobi.com", "applovin.com",
        "chartboost.com", "vungle.com", "connect.facebook.net", "ads-twitter.com",
        "an.yandex.ru", "mc.yandex.ru", "yandexadexchange.net", "adfox.ru",
        "ads.mail.ru", "rs.mail.ru", "top-fwz1.mail.ru", "ad.mail.ru", "ads.vk.com",
        "hotjar.com", "mixpanel.com", "segment.com", "amplitude.com", "branch.io",
        "appsflyer.com", "adjust.com", "kochava.com", "onesignal.com", "pushwoosh.com",
        "flurry.com", "adcolony.com", "startapp.com", "mopub.com", "smaato.net",
        "mgid.com", "propellerads.com", "popads.net", "exoclick.com", "juicyads.com",
        "trafficjunky.net", "revcontent.com",
    )

    fun maybeBlock(url: String): WebResourceResponse? {
        if (!enabled) return null
        val host = try { android.net.Uri.parse(url).host?.lowercase() } catch (e: Exception) { null } ?: return null
        var h = host
        while (h.contains('.')) {
            if (h in BLOCKED) { countOne(); return blank() }
            h = h.substringAfter('.')
        }
        // Path-based trackers on otherwise-legit hosts.
        if (url.contains("facebook.com/tr") || url.contains("vk.com/rtrg")) {
            countOne(); return blank()
        }
        return null
    }

    // A fresh empty response per block — a WebResourceResponse's stream is
    // single-use, so it can't be shared across requests.
    private fun blank(): WebResourceResponse =
        WebResourceResponse("text/plain", "utf-8", java.io.ByteArrayInputStream(ByteArray(0)))
}

/** One browser tab: a live WebView plus the reactive state the chrome reads. */
@SuppressLint("SetJavaScriptEnabled")
/**
 * Режим чтения: оставить на странице текст и убрать всё остальное.
 *
 * Намеренно простой и без библиотек — берём самый «текстовый» блок страницы
 * и показываем только его. Это не сработает на всём, поэтому обратный путь
 * — обычная перезагрузка, а не попытка вернуть разметку.
 */
private const val READER_JS = """
(function(){
  try {
    var best=null, bestLen=0;
    var cand=document.querySelectorAll('article,main,[role=main],.post,.article,#content,.content');
    for (var i=0;i<cand.length;i++){
      var len=(cand[i].innerText||'').length;
      if(len>bestLen){bestLen=len;best=cand[i];}
    }
    if(!best||bestLen<400){
      var ps=document.getElementsByTagName('p'), acc=null, accLen=0;
      for(var j=0;j<ps.length;j++){
        var pr=ps[j].parentElement; if(!pr) continue;
        var l=(pr.innerText||'').length;
        if(l>accLen){accLen=l;acc=pr;}
      }
      best=acc; bestLen=accLen;
    }
    if(!best) return 'no';
    var html=best.innerHTML;
    document.body.innerHTML='<div id="vpnka-reader">'+html+'</div>';
    var st=document.createElement('style');
    st.textContent='html,body{background:#15110c!important;margin:0!important;}'+
      '#vpnka-reader{max-width:720px;margin:0 auto;padding:22px 18px 60px;'+
      'color:#f8f1e6;font:400 17px/1.62 -apple-system,Roboto,sans-serif;}'+
      '#vpnka-reader img,#vpnka-reader video{max-width:100%;height:auto;border-radius:10px;}'+
      '#vpnka-reader a{color:#ffb655;}'+
      '#vpnka-reader h1,#vpnka-reader h2,#vpnka-reader h3{line-height:1.25;}';
    document.head.appendChild(st);
    return 'ok';
  } catch(e){ return 'err'; }
})();
"""

/** Журнал посещений: поиск, открыть, забыть строку, очистить всё. */
@Composable
private fun BrowserHistorySheet(onOpen: (String) -> Unit, onClose: () -> Unit) {
    var q by remember { mutableStateOf("") }
    var tick by remember { mutableIntStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    val items = remember(q, tick) { BrowserHistory.search(q) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Журнал", fontFamily = VpnkaFonts.nunito800, color = VpnkaColors.TextStrong)
                Spacer(Modifier.weight(1f))
                if (BrowserHistory.all().isNotEmpty()) {
                    Text(
                        "Очистить", fontFamily = VpnkaFonts.nunito800, fontSize = 12.sp,
                        color = VpnkaColors.Warning,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp))
                            .clickable { confirmClear = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp)) {
                OutlinedTextField(
                    value = q,
                    onValueChange = { q = it },
                    singleLine = true,
                    placeholder = { Text("Поиск по журналу", color = VpnkaColors.TextMuted) },
                    textStyle = androidx.compose.material3.LocalTextStyle.current
                        .copy(color = VpnkaColors.TextStrong),
                    shape = RoundedCornerShape(13.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = VpnkaColors.TextStrong,
                        unfocusedTextColor = VpnkaColors.TextStrong,
                        cursorColor = VpnkaColors.Accent,
                        focusedBorderColor = VpnkaColors.Accent,
                        unfocusedBorderColor = VpnkaColors.CardServer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                if (items.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (q.isBlank())
                                "Журнал пуст. Страницы, открытые в инкогнито, сюда не попадают."
                            else "Ничего не найдено",
                            fontFamily = VpnkaFonts.manrope600, fontSize = 13.sp,
                            color = VpnkaColors.TextMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                } else {
                    LazyColumn {
                        items(items.size, key = { items[it].url }) { i ->
                            val e = items[i]
                            val day = historyDay(e.ts)
                            // Заголовок дня считаем от ПРЕДЫДУЩЕЙ строки, а не
                            // накопительной переменной: ленивый список рисует
                            // только видимое и в произвольном порядке, счётчик
                            // в нём врёт при прокрутке.
                            if (i == 0 || historyDay(items[i - 1].ts) != day) {
                                Text(
                                    day.uppercase(),
                                    fontFamily = VpnkaFonts.nunito800, fontSize = 11.sp,
                                    color = VpnkaColors.TextFaint,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                    .clip(RoundedCornerShape(11.dp)).background(VpnkaColors.CardServer).border(1.dp, VpnkaColors.Hairline, RoundedCornerShape(11.dp))
                                    .clickable { onOpen(e.url) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        e.title.ifBlank { e.url },
                                        fontFamily = VpnkaFonts.nunito800, fontSize = 13.sp,
                                        color = VpnkaColors.TextStrong,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        runCatching { android.net.Uri.parse(e.url).host }.getOrNull()
                                            ?: e.url,
                                        fontFamily = VpnkaFonts.manrope600, fontSize = 11.sp,
                                        color = VpnkaColors.TextMuted,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    historyTime(e.ts), fontFamily = VpnkaFonts.manrope600,
                                    fontSize = 11.sp, color = VpnkaColors.TextFaint,
                                )
                                Text(
                                    "✕", fontSize = 13.sp, color = VpnkaColors.TextFaint,
                                    modifier = Modifier.clip(CircleShape)
                                        .clickable { BrowserHistory.remove(e.url); tick++ }
                                        .padding(8.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onClose) { Text("Закрыть") }
        },
        containerColor = VpnkaColors.BgOffCentre,
    )

    if (confirmClear) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Очистить журнал?", fontFamily = VpnkaFonts.nunito800, color = VpnkaColors.TextStrong) },
            text = {
                Text(
                    "Список посещённых страниц будет стёрт. Сохранённые пароли и вкладки останутся.",
                    fontFamily = VpnkaFonts.manrope600, fontSize = 13.sp, color = VpnkaColors.TextMuted,
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    BrowserHistory.clear(); confirmClear = false; tick++
                }) { Text("Очистить", color = VpnkaColors.Warning) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmClear = false }) { Text("Отмена") }
            },
            containerColor = VpnkaColors.BgOffCentre,
        )
    }
}

private fun historyDay(ts: Long): String {
    val now = java.util.Calendar.getInstance()
    val then = java.util.Calendar.getInstance().apply { timeInMillis = ts }
    val sameYear = now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR)
    val dayDiff = now.get(java.util.Calendar.DAY_OF_YEAR) - then.get(java.util.Calendar.DAY_OF_YEAR)
    return when {
        sameYear && dayDiff == 0 -> "Сегодня"
        sameYear && dayDiff == 1 -> "Вчера"
        else -> java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale("ru")).format(java.util.Date(ts))
    }
}

private fun historyTime(ts: Long): String =
    java.text.SimpleDateFormat("HH:mm", java.util.Locale("ru")).format(java.util.Date(ts))

private class BrowserTab(
    context: Context,
    val id: Int,
    startUrl: String,
    onOfferSave: (host: String, user: String, pass: String) -> Unit,
    /**
     * Инкогнито: не пишем журнал, не держим кэш и формы, а при закрытии
     * вкладки стираем то, что вообще в нашей власти. Куки у WebView общие на
     * всё приложение — их мы не трогаем, и «невидимку» не обещаем.
     */
    val incognito: Boolean = false,
    onDownload: ((url: String, name: String) -> Unit)? = null,
) {
    val url = mutableStateOf(startUrl)
    val title = mutableStateOf("")
    val progress = mutableStateOf(0)
    val canBack = mutableStateOf(false)
    val canFwd = mutableStateOf(false)
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /** Пропуск главного фрейма к мосту сохранения паролей.
     *
     * Свой на вкладку и на всё её время. Обновлять на каждую загрузку
     * нельзя: обработчик отправки формы ставится один раз на документ и
     * держит пропуск в замыкании — после смены сохранение перестало бы
     * работать вовсе. Чужому фрейму он недоступен и так: значение кладётся
     * только в главный фрейм и тут же убирается со страницы. */
    private val pwdNonce: String = java.util.UUID.randomUUID().toString()

    /** The host we actually trust is the tab's current URL — NOT anything the
     *  page's JS passes us. This is what stops a site reading other sites' creds. */
    private fun currentHost(): String? = try {
        android.net.Uri.parse(url.value).host
    } catch (e: Exception) { null }

    val webView: WebView = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = !incognito
        // В инкогнито не оставляем ни кэша, ни форм, ни паролей. Полной
        // изоляции WebView не даёт (куки живут общие), и обещать «невидимку»
        // нельзя — но всё, что зависит от нас, здесь выключено.
        if (incognito) {
            settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
            settings.saveFormData = false
        }
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        // Видео-сайты: то, без чего они не открываются или молчат.
        //
        // * Автовоспроизведение. По умолчанию WebView требует «жеста» на
        //   КАЖДЫЙ элемент media, и плеер, который стартует сам, просто
        //   стоит чёрным прямоугольником.
        // * Чужие куки. У WebView сторонние куки выключены, а у видео-сайтов
        //   на них висит и согласие, и сам плеер (домен CDN — чужой), и
        //   страница уходит в бесконечную заглушку «примите условия».
        // * Смешанное содержимое. Страница по https, а картинки и куски
        //   видео у многих до сих пор по http: WebView их режет молча, и
        //   получается пустой каркас без единого ролика.
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode =
            android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        runCatching {
            android.webkit.CookieManager.getInstance()
                .setAcceptThirdPartyCookies(this, !incognito)
        }
        // В инкогнито менеджер паролей молчит: подставлять сохранённый
        // пароль и предлагать записать новый в режиме «без следов» —
        // ровно то, чего от него не ждут.
        //
        // ЧИТАТЬ пароли страница больше НЕ МОЖЕТ. Объект, добавленный через
        // addJavascriptInterface, виден ВСЕМ фреймам, включая чужой рекламный
        // iframe, а хост мы брали из адреса ВЕРХНЕГО документа — то есть
        // сторонний код на странице банка мог попросить пароль от банка и
        // получить его без единого действия человека. Теперь подстановку
        // делаем мы сами: значения уезжают в главный фрейм скриптом, который
        // впрыскиваем после загрузки, а мост оставлен только на предложение
        // сохранить — там всё равно решает человек в диалоге.
        if (!incognito) addJavascriptInterface(object {
            @android.webkit.JavascriptInterface
            fun promptSave(pass: String, user: String, secret: String) {
                // Мост виден ВСЕМ фреймам, включая чужие рекламные.
                //
                // Прочитать пароль через него уже нельзя, но предложить
                // сохранить — было можно: сторонний скрипт в iframe подсовывал
                // свою пару, диалог показывался от имени ВЕРХНЕГО сайта, и
                // человек одним «Сохранить» портил себе настоящую запись.
                // Пропуск на каждую загрузку кладём только в главный фрейм —
                // из чужого источника его не прочитать.
                if (secret.isEmpty() || secret != pwdNonce) return
                val host = currentHost() ?: return
                mainHandler.post { onOfferSave(host, user, pass) }
            }
        }, "VpnkaPwd")
        // Файл со страницы — в наши «Загрузки», а не в системный менеджер:
        // тот пошёл бы в сеть НАПРЯМУЮ, мимо туннеля.
        setDownloadListener { dUrl, _, contentDisposition, mime, _ ->
            val name = runCatching {
                android.webkit.URLUtil.guessFileName(dUrl, contentDisposition, mime)
            }.getOrDefault("файл")
            onDownload?.invoke(dUrl, name)
        }
        // Скрипт-заглушка WebRTC ставится ДО документа, если WebView это
        // умеет; иначе — первым делом на старте страницы. Второй путь чуть
        // слабее (скрипт страницы теоретически успевает раньше), но лучше,
        // чем ничего.
        // Запасной путь включается по ФАКТУ установки, а не по поддержке.
        //
        // Раньше проверяли только «умеет ли WebView»: если умеет, но вызов
        // бросил, скрипт не ставился НИКУДА — запасная ветка молчала, потому
        // что смотрела на ту же поддержку. WebRTC при этом оставался живым, и
        // страница видела настоящий адрес.
        val startScriptInstalled = androidx.webkit.WebViewFeature.isFeatureSupported(
            androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT,
        ) && runCatching {
            androidx.webkit.WebViewCompat.addDocumentStartJavaScript(
                this, NO_WEBRTC_JS, setOf("*"),
            )
            true
        }.getOrDefault(false)
        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, u: String?, favicon: android.graphics.Bitmap?) {
                if (!startScriptInstalled) view?.evaluateJavascript(NO_WEBRTC_JS, null)
                u?.let { this@BrowserTab.url.value = it }; this@BrowserTab.canBack.value = canGoBack(); this@BrowserTab.canFwd.value = canGoForward()
            }
            override fun onPageFinished(view: WebView?, u: String?) {
                u?.let { this@BrowserTab.url.value = it }; this@BrowserTab.canBack.value = canGoBack(); this@BrowserTab.canFwd.value = canGoForward()
                if (!incognito) {
                    // Пароли отдаём ТОЛЬКО по https: на http страницу может
                    // подменить кто угодно между выходной нодой и сайтом, и
                    // тогда наш же автозаполнитель отдаст ему пароль.
                    val host = runCatching { android.net.Uri.parse(u ?: "").host }.getOrNull()
                    val creds = if (u?.startsWith("https://") == true && host != null) {
                        // Повреждённое хранилище не должно ронять приложение
                        // на КАЖДОЙ https-странице.
                        runCatching { PasswordStore.credentialsJson(host) }.getOrNull()
                    } else {
                        null
                    }
                    // evaluateJavascript выполняется в ГЛАВНОМ фрейме —
                    // чужому iframe эти значения не достаются.
                    view?.evaluateJavascript(
                        "(function(){window.__vpnkaCreds=" + (creds ?: "null") +
                            ";window.__vpnkaN=" + org.json.JSONObject.quote(pwdNonce) + ";})();",
                        null,
                    )
                    view?.evaluateJavascript(PWD_JS, null)
                }
                // Журнал посещений. Инкогнито молчит — иначе окно «без следов»
                // оставляло бы главный след.
                if (!incognito) u?.let { BrowserHistory.add(it, this@BrowserTab.title.value) }
            }
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val u = request?.url?.toString()
                return (u?.let { AdBlocker.maybeBlock(it) }) ?: super.shouldInterceptRequest(view, request)
            }

            /**
             * Чужие схемы не роняем в ошибку.
             *
             * Ссылки `intent://`, `market://`, `tg://` попадались в WebView
             * как обычный адрес: он пытался их загрузить и показывал
             * страницу ошибки поверх сайта. У видео-сайтов такие ссылки —
             * обычное дело (кнопка «открыть в приложении», редирект
             * рекламной сети), и человек получал ошибку вместо страницы.
             */
            override fun shouldOverrideUrlLoading(
                view: WebView?, request: WebResourceRequest?,
            ): Boolean {
                val u = request?.url ?: return false
                val scheme = u.scheme?.lowercase()
                if (scheme == "http" || scheme == "https" || scheme == "about") return false
                // У intent-ссылок бывает запасной http-адрес — им и идём.
                val fallback = runCatching {
                    Regex("S\\.browser_fallback_url=([^;]+)")
                        .find(u.toString())?.groupValues?.get(1)
                        ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                }.getOrNull()
                if (fallback != null && fallback.startsWith("http")) {
                    view?.loadUrl(fallback)
                }
                return true
            }

            /**
             * Ошибку ГЛАВНОГО документа говорим словами.
             *
             * «Не открывается» без причины — это тупик и для человека, и для
             * разбора: имя не разрешилось, соединение отвергнуто и «сайт
             * ответил 403» лечатся по-разному, а выглядели одинаково — белым
             * экраном.
             */
            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: android.webkit.WebResourceError?,
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame != true) return
                val what = runCatching { error?.description?.toString() }.getOrNull()
                val code = runCatching { error?.errorCode }.getOrNull()
                SmartDeskToast.show(
                    "Страница не открылась: ${what ?: "нет ответа"}" +
                        (if (code != null) " ($code)" else "")
                )
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                response: WebResourceResponse?,
            ) {
                super.onReceivedHttpError(view, request, response)
                if (request?.isForMainFrame != true) return
                val code = runCatching { response?.statusCode }.getOrNull() ?: return
                if (code >= 400) SmartDeskToast.show("Сайт ответил ошибкой $code")
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, p: Int) { this@BrowserTab.progress.value = p }
            override fun onReceivedTitle(view: WebView?, t: String?) {
                if (t.isNullOrBlank()) return
                this@BrowserTab.title.value = t
                // Заголовок приходит после адреса, поэтому строку журнала
                // дописываем задним числом.
                if (!incognito) BrowserHistory.retitle(this@BrowserTab.url.value, t)
            }
        }
        loadUrl(startUrl)
    }
    fun go(input: String) = webView.loadUrl(normalizeUrl(input))
}

/** Injected after each page load: autofills a saved login and, on form submit,
 *  hands the entered username/password back to the app to offer saving. Reads
 *  only the top document's inputs; the app supplies the trusted origin. */
/**
 * Гасим WebRTC в браузере — до единой строки страницы.
 *
 * Прокси WebView заворачивает только загрузку по HTTP; UDP-сокеты WebRTC он
 * не трогает вовсе, а собственное приложение исключено из туннеля
 * (addDisallowedApplication в CoreVpnService — иначе ядро зациклилось бы на
 * себе). Значит любая страница могла собрать ICE-кандидатов напрямую и
 * узнать НАСТОЯЩИЙ адрес человека, пока всё остальное шло через ноду. Для
 * ВПН-браузера это худший из возможных проколов: он не мешает работе и никак
 * не виден.
 *
 * Ставим заглушки, а не удаляем свойства: сайт, который просто проверяет
 * наличие RTCPeerConnection, не должен падать.
 */
private const val NO_WEBRTC_JS = """
(function(){
  try{
    var block = function(){ throw new DOMException('WebRTC отключён', 'NotAllowedError'); };
    var names = ['RTCPeerConnection','webkitRTCPeerConnection','mozRTCPeerConnection',
                 'RTCDataChannel','webkitRTCDataChannel'];
    for (var i=0;i<names.length;i++){
      try { Object.defineProperty(window, names[i], { value: block, writable:false, configurable:false }); } catch(e){}
    }
    if (navigator.mediaDevices) {
      try { navigator.mediaDevices.getUserMedia = function(){ return Promise.reject(new DOMException('Отключено','NotAllowedError')); }; } catch(e){}
      try { navigator.mediaDevices.enumerateDevices = function(){ return Promise.resolve([]); }; } catch(e){}
    }
    try { navigator.getUserMedia = undefined; } catch(e){}
  }catch(e){}
})();
"""

private const val PWD_JS = """
(function(){
 try{
  var raw=null;
  try{
    raw = window.__vpnkaCreds ? JSON.stringify(window.__vpnkaCreds) : null;
    // Сразу забираем значение со страницы: пока оно лежало в глобальной
    // переменной, его читал любой сторонний скрипт главного фрейма.
    try { delete window.__vpnkaCreds; } catch(e) { window.__vpnkaCreds = null; }
  }catch(e){}
  if(raw){ try{ var c=JSON.parse(raw);
    var pw=document.querySelector('input[type=password]');
    if(pw){ pw.value=c.p;
      var ins=document.querySelectorAll('input'); var uf=null;
      for(var i=0;i<ins.length;i++){ if(ins[i]===pw) break; var t=(ins[i].type||'text').toLowerCase(); if(t=='text'||t=='email'||t=='tel') uf=ins[i]; }
      if(uf&&c.u) uf.value=c.u;
    }
  }catch(e){} }
  // Пропуск забираем СРАЗУ и держим в замыкании: со страницы он исчезает,
  // и сторонний скрипт главного фрейма его тоже не прочитает.
  var __n='';
  try{ __n = window.__vpnkaN || ''; delete window.__vpnkaN; }catch(e){ try{ window.__vpnkaN=null; }catch(e2){} }
  if(!window.__vpnkaPwdHook){ window.__vpnkaPwdHook=true;
   document.addEventListener('submit', function(ev){
    try{ var f=ev.target; var pw=f&&f.querySelector?f.querySelector('input[type=password]'):null; if(!pw||!pw.value) return;
      var ins=f.querySelectorAll('input'); var u='';
      for(var i=0;i<ins.length;i++){ if(ins[i]===pw) break; var t=(ins[i].type||'text').toLowerCase(); if(t=='text'||t=='email'||t=='tel') u=ins[i].value; }
      VpnkaPwd.promptSave(pw.value, u, __n);
    }catch(e){}
   }, true);
  }
 }catch(e){}
})();
"""

/** Bare domain («example.com») for the omnibox. */
private fun domainOf(url: String): String = try {
    (android.net.Uri.parse(url).host ?: url).removePrefix("www.")
} catch (e: Exception) { url }

@Composable
private fun BrowserApp() {
    // Заглушку «нужен VPN» показываем ПОВЕРХ, а не вместо.
    //
    // Раньше здесь стоял ранний выход, и любое моргание туннеля (смена
    // сервера, короткий обрыв) на один кадр выносило из композиции ВСЕ
    // вкладки вместе с их историей и введёнными в формы данными: браузер
    // собирался заново с одной страницей. Пять открытых вкладок исчезали от
    // случайного переподключения.
    val connected = VpnkaColors.connected

    // Fail-closed: if this device's WebView can't be forced through our proxy,
    // do NOT open a browser at all — otherwise traffic would egress directly,
    // breaking the «only through VPN» guarantee.
    val proxyOk = remember { WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE) }
    if (!proxyOk) {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🔒", fontSize = 44.sp)
                Spacer(Modifier.height(12.dp))
                Text("Браузер недоступен на этом устройстве", fontFamily = VpnkaFonts.nunito800,
                    fontSize = 17.sp, color = VpnkaColors.TextStrong)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Обновите «Android System WebView» в Google Play. Без него мы не можем гарантировать, что трафик идёт только через VPN.",
                    fontFamily = VpnkaFonts.manrope600, fontSize = 14.sp, color = VpnkaColors.TextMuted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        return
    }

    val context = LocalContext.current
    // Браузер грузит произвольные сайты — их нужно гнать через VPN. На нашем
    // applicationId isXray()=false, значит HTTP-inbound создаётся на httpPort;
    // проксируем WebView через него (HTTP-прокси доказанно работает).
    val httpPort = remember { SettingsManager.getHttpPort() }

    // Force every WebView request through the local proxy while this screen is
    // up; drop the override when leaving so no other WebView is affected.
    // Прокси накладывается ДО первой загрузки.
    //
    // Раньше вкладка создавалась в `remember`, а её конструктор сразу звал
    // loadUrl — то есть первая страница уходила НАПРЯМУЮ: провайдер видел и
    // DNS-запрос, и адрес узла в открытом виде. Наложение вдобавок
    // асинхронное, и колбэк ошибки был пустой: если бы оно не применилось,
    // браузер спокойно работал бы мимо туннеля.
    var proxyReady by remember { mutableStateOf(false) }
    var proxyFailed by remember { mutableStateOf(false) }
    DisposableEffect(httpPort) {
        val cfg = ProxyConfig.Builder()
            .addProxyRule("127.0.0.1:$httpPort")
            .build()
        runCatching {
            ProxyController.getInstance().setProxyOverride(
                cfg, { it.run() }, { proxyReady = true },
            )
        }.onFailure { proxyFailed = true }
        onDispose {
            runCatching {
                ProxyController.getInstance().clearProxyOverride({ it.run() }, {})
            }
            proxyReady = false
        }
    }

    if (proxyFailed) {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "Не удалось направить браузер через VPN. Страницы не открываются — " +
                    "выпускать их мимо туннеля мы не станем.",
                fontFamily = VpnkaFonts.manrope600, fontSize = 14.sp,
                color = VpnkaColors.TextMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        return
    }
    if (!proxyReady) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = VpnkaColors.Accent)
        }
        return
    }

    // Password manager: a page offer to save creds, and the manager sheet.
    var pendingSave by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var showPwds by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    val onOfferSave = remember {
        { host: String, user: String, pass: String -> pendingSave = Triple(host, user, pass) }
    }

    val home = "https://duckduckgo.com/"
    // Загрузка со страницы идёт нашим загрузчиком: системный менеджер пошёл
    // бы в сеть напрямую, мимо туннеля.
    val onPageDownload: (String, String) -> Unit = { u, name ->
        YouTubeDownloads.enqueueFile(context, u, name)
        SmartDeskToast.show("Файл добавлен в загрузки", "Открыть", "downloads")
    }
    val tabs = remember {
        mutableStateListOf(BrowserTab(context, 0, home, onOfferSave, false, onPageDownload))
    }
    var nextId by remember { mutableIntStateOf(1) }
    var activeId by remember { mutableIntStateOf(0) }
    val active = tabs.firstOrNull { it.id == activeId } ?: tabs.first()
    // Фоновые вкладки СТАВИМ НА ПАУЗУ.
    //
    // Закрытую вкладку глушили, а просто переключённую — нет: она оставалась
    // живой и продолжала крутить скрипты, звук и сеть. При включённом ВПН
    // это ещё и трафик, за который человек платит, из страницы, которую он
    // уже не смотрит.
    LaunchedEffect(activeId, tabs.size) {
        tabs.forEach { t ->
            runCatching {
                if (t.id == activeId) { t.webView.onResume(); t.webView.resumeTimers() }
                else t.webView.onPause()
            }
        }
    }
    var editing by remember { mutableStateOf(false) }
    var omni by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue("")) }
    var showTabs by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var adblock by remember { mutableStateOf(AdBlocker.enabled) }

    fun openTab(u: String, incognito: Boolean = false) {
        tabs.add(BrowserTab(context, nextId, u, onOfferSave, incognito, onPageDownload))
        activeId = nextId; nextId++; showTabs = false
    }
    fun closeTab(t: BrowserTab) {
        val i = tabs.indexOf(t); tabs.remove(t)
        if (tabs.isEmpty()) {
            tabs.add(BrowserTab(context, nextId, home, onOfferSave, false, onPageDownload))
            activeId = nextId; nextId++
        }
        else if (activeId == t.id) activeId = tabs[i.coerceAtMost(tabs.lastIndex)].id
        // Halt the closed tab so it stops running JS/media/network in the bg.
        runCatching { t.webView.loadUrl("about:blank"); t.webView.onPause() }
        // У инкогнито-вкладки дочищаем то, что в нашей власти: кэш, формы и
        // её собственную историю переходов. Куки у WebView общие на всё
        // приложение — их не трогаем и «невидимку» не обещаем.
        if (t.incognito) {
            runCatching {
                t.webView.clearCache(true)
                t.webView.clearFormData()
                t.webView.clearHistory()
            }
        }
    }

    // «Назад» в браузере — это ПРЕДЫДУЩАЯ СТРАНИЦА.
    //
    // Раньше самый частый жест на Android выбрасывал на рабочий стол, да ещё
    // и уносил все вкладки: ниже они принудительно перезагружались. Пять
    // открытых вкладок исчезали от одного случайного движения. А переход по
    // истории лежал в меню «⋮» — самое частое действие стоило двух нажатий.
    var findQuery by remember { mutableStateOf<String?>(null) }
    var reading by remember { mutableStateOf(false) }

    SmartDeskBackHandler {
        when {
            showHistory -> { showHistory = false; true }
            menuOpen -> { menuOpen = false; true }
            showTabs -> { showTabs = false; true }
            findQuery != null -> { findQuery = null; active.webView.clearMatches(); true }
            editing -> { editing = false; true }
            active.canBack.value -> { runCatching { active.webView.goBack() }; true }
            else -> false
        }
    }

    // Leaving the browser: pause every tab so nothing keeps running after exit.
    DisposableEffect(Unit) {
        onDispose { tabs.forEach { runCatching { it.webView.onPause() } } }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Поиск по странице — прямо над содержимым, с числом совпадений и
        // переходом между ними. WebView умеет это сам, надо только дать
        // человеку строку и кнопки.
        findQuery?.let { fq ->
            var matches by remember { mutableStateOf(0) }
            LaunchedEffect(Unit) {
                active.webView.setFindListener { active_, total, isDone ->
                    if (isDone) matches = total
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = fq,
                    onValueChange = {
                        findQuery = it
                        if (it.isBlank()) active.webView.clearMatches()
                        else active.webView.findAllAsync(it)
                    },
                    singleLine = true,
                    placeholder = { Text("Найти на странице", color = VpnkaColors.TextMuted) },
                    textStyle = androidx.compose.material3.LocalTextStyle.current
                        .copy(color = VpnkaColors.TextStrong),
                    shape = RoundedCornerShape(13.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = VpnkaColors.TextStrong,
                        unfocusedTextColor = VpnkaColors.TextStrong,
                        cursorColor = VpnkaColors.Accent,
                        focusedBorderColor = VpnkaColors.Accent,
                        unfocusedBorderColor = VpnkaColors.CardServer,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                if (matches > 0) {
                    Text(
                        "$matches", fontFamily = VpnkaFonts.manrope600, fontSize = 12.sp,
                        color = VpnkaColors.TextMuted,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text("↑", fontSize = 18.sp, color = VpnkaColors.TextStrong,
                    modifier = Modifier.clip(CircleShape)
                        .clickable { active.webView.findNext(false) }.padding(8.dp))
                Text("↓", fontSize = 18.sp, color = VpnkaColors.TextStrong,
                    modifier = Modifier.clip(CircleShape)
                        .clickable { active.webView.findNext(true) }.padding(8.dp))
                Text("✕", fontSize = 16.sp, color = VpnkaColors.TextMuted,
                    modifier = Modifier.clip(CircleShape)
                        .clickable { findQuery = null; active.webView.clearMatches() }.padding(8.dp))
            }
        }

        // Chrome-style top bar: omnibox (lock + domain) · tab count · menu.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (editing) {
                val focus = remember { FocusRequester() }
                var hadFocus by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { focus.requestFocus() }
                OutlinedTextField(
                    value = omni,
                    onValueChange = { omni = it },
                    singleLine = true,
                    leadingIcon = { Text("🔒", fontSize = 12.sp) },
                    trailingIcon = {
                        if (omni.text.isNotEmpty()) Text("✕", fontSize = 16.sp, color = VpnkaColors.TextMuted,
                            modifier = Modifier.clickable { omni = androidx.compose.ui.text.input.TextFieldValue("") }.padding(horizontal = 10.dp, vertical = 4.dp))
                    },
                    textStyle = LocalTextStyle.current.copy(color = VpnkaColors.TextStrong, fontSize = 15.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { active.go(omni.text); editing = false }),
                    shape = RoundedCornerShape(15.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = VpnkaColors.TextStrong, unfocusedTextColor = VpnkaColors.TextStrong,
                        cursorColor = VpnkaColors.Accent, focusedBorderColor = VpnkaColors.Accent,
                        unfocusedBorderColor = VpnkaColors.Accent,
                    ),
                    modifier = Modifier.weight(1f)
                        .focusRequester(focus)
                        .onFocusChanged { if (it.isFocused) hadFocus = true else if (hadFocus) editing = false },
                )
            } else {
                Row(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(15.dp))
                        .background(VpnkaColors.CardServer)
                        .border(1.dp, VpnkaColors.Hairline, RoundedCornerShape(15.dp))
                        .clickable {
                            val u = active.url.value
                            // Select the whole URL so the first keystroke replaces it.
                            omni = androidx.compose.ui.text.input.TextFieldValue(
                                text = u,
                                selection = androidx.compose.ui.text.TextRange(0, u.length),
                            )
                            editing = true
                        }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Замок · домен · приглушённый путь · «A» (чтение) · ↻.
                    // В макете именно так: адрес читается доменом, а хвост
                    // страницы гасится — он нужен глазу, но не спорит с ним.
                    // Признак инкогнито прямо в адресной строке: раньше
                    // приватную вкладку нельзя было отличить от обычной, а
                    // цена ошибки здесь — ровно приватность.
                    if (active.incognito) {
                        Text("🕶", fontSize = 12.sp)
                    } else {
                        Text(if (active.url.value.startsWith("https")) "🔒" else "⚠", fontSize = 12.sp)
                    }
                    Spacer(Modifier.width(9.dp))
                    Text(
                        text = domainOf(active.url.value),
                        fontFamily = VpnkaFonts.manrope600, fontSize = 13.sp, color = VpnkaColors.TextStrong,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    val path = remember(active.url.value) {
                        runCatching {
                            val u = android.net.Uri.parse(active.url.value)
                            (u.path.orEmpty() + (u.query?.let { "?" + it } ?: "")).take(40)
                        }.getOrDefault("")
                    }
                    if (path.isNotBlank() && path != "/") {
                        Text(
                            path, fontFamily = VpnkaFonts.manrope600, fontSize = 13.sp,
                            color = VpnkaColors.TextFaint, maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "A", fontFamily = VpnkaFonts.nunito800, fontSize = 13.sp,
                        color = if (reading) VpnkaColors.Accent else VpnkaColors.TextMuted,
                        modifier = Modifier.clip(CircleShape)
                            .clickable {
                                if (reading) { reading = false; active.webView.reload() }
                                else { reading = true; active.webView.evaluateJavascript(READER_JS, null) }
                            }
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                    Text(
                        "↻", fontSize = 13.sp, color = VpnkaColors.TextMuted,
                        modifier = Modifier.clip(CircleShape)
                            .clickable { active.webView.reload() }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier.size(30.dp).clip(RoundedCornerShape(7.dp))
                    .background(VpnkaColors.CardServer)
                    .border(1.dp, VpnkaColors.Hairline, RoundedCornerShape(7.dp)).clickable { showTabs = true },
                contentAlignment = Alignment.Center,
            ) { Text("${tabs.size}", fontFamily = VpnkaFonts.nunito800, fontSize = 13.sp, color = VpnkaColors.TextStrong) }
            Spacer(Modifier.width(2.dp))
            Box {
                Text("⋮", fontSize = 22.sp, color = VpnkaColors.TextStrong,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { menuOpen = true }.padding(horizontal = 8.dp, vertical = 2.dp))
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("← Назад") }, enabled = active.canBack.value, onClick = { menuOpen = false; active.webView.goBack() })
                    DropdownMenuItem(text = { Text("→ Вперёд") }, enabled = active.canFwd.value, onClick = { menuOpen = false; active.webView.goForward() })
                    DropdownMenuItem(text = { Text("⟳ Обновить") }, onClick = { menuOpen = false; active.webView.reload() })
                    DropdownMenuItem(text = { Text("＋ Новая вкладка") }, onClick = { menuOpen = false; openTab(home) })
                    DropdownMenuItem(
                        text = { Text("🕶 Открыть инкогнито") },
                        onClick = { menuOpen = false; openTab(home, incognito = true) },
                    )
                    DropdownMenuItem(
                        text = { Text("⌕ Найти на странице") },
                        onClick = { menuOpen = false; findQuery = "" },
                    )
                    DropdownMenuItem(
                        text = { Text(if (reading) "📄 Обычный вид" else "📄 Режим чтения") },
                        onClick = {
                            menuOpen = false
                            reading = !reading
                            // Режим чтения — не «загрузить AMP-версию», а
                            // упрощение уже открытой страницы на месте:
                            // убираем всё, кроме основного текста. Работает
                            // не везде, поэтому обычный вид возвращается
                            // перезагрузкой.
                            if (reading) {
                                active.webView.evaluateJavascript(READER_JS, null)
                            } else {
                                active.webView.reload()
                            }
                        },
                    )
                    DropdownMenuItem(text = { Text("🏠 Домой") }, onClick = { menuOpen = false; active.webView.loadUrl(home) })
                    DropdownMenuItem(
                        text = { Text(if (adblock) "🛡 Блокировка рекламы: вкл" else "🛡 Блокировка рекламы: выкл") },
                        onClick = { menuOpen = false; AdBlocker.enabled = !adblock; adblock = !adblock; active.webView.reload() },
                    )
                    DropdownMenuItem(text = { Text("🕘 Журнал") }, onClick = { menuOpen = false; showHistory = true })
                    DropdownMenuItem(text = { Text("🔑 Пароли") }, onClick = { menuOpen = false; showPwds = true })
                }
            }
        }
        // Thin load progress under the bar (custom — no version-sensitive widget).
        if (active.progress.value in 1..99) {
            Box(modifier = Modifier.fillMaxWidth(active.progress.value / 100f).height(2.5.dp).background(VpnkaColors.Accent))
        }
        // Active tab's WebView. key() gives a fresh host on tab switch so the
        // (detached) WebView re-attaches here without a double-parent crash.
        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            key(active.id) {
                AndroidView(
                    factory = {
                        (active.webView.parent as? android.view.ViewGroup)?.removeView(active.webView)
                        active.webView
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (showTabs) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showTabs = false },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { openTab(home) }) { Text("＋ Новая") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { showTabs = false }) { Text("Закрыть") } },
            title = { Text("Вкладки · ${tabs.size}", fontFamily = VpnkaFonts.nunito800, color = VpnkaColors.TextStrong) },
            text = {
                LazyColumn {
                    items(tabs, key = { it.id }) { t ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (t.id == activeId) VpnkaColors.Accent.copy(alpha = 0.18f) else VpnkaColors.CardServer)
                                .clickable { activeId = t.id; showTabs = false }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(t.title.value.ifBlank { domainOf(t.url.value) }, fontFamily = VpnkaFonts.nunito800,
                                    fontSize = 14.sp, color = VpnkaColors.TextStrong, maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Text(domainOf(t.url.value), fontFamily = VpnkaFonts.manrope600, fontSize = 12.sp,
                                    color = VpnkaColors.TextMuted, maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                            Text("✕", fontSize = 16.sp, color = VpnkaColors.TextMuted,
                                modifier = Modifier.clickable { closeTab(t) }.padding(start = 8.dp))
                        }
                    }
                }
            },
            containerColor = VpnkaColors.BgOffCentre,
        )
    }

    // Offer to remember a login captured from a form submit.
    pendingSave?.let { offer ->
        val (host, user, pass) = offer
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingSave = null },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    PasswordStore.save(host, user, pass); pendingSave = null
                }) { Text("Сохранить") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { pendingSave = null }) { Text("Не сейчас") } },
            title = { Text("Сохранить пароль?", fontFamily = VpnkaFonts.nunito800, color = VpnkaColors.TextStrong) },
            text = {
                val who = if (user.isNotBlank()) "$host ($user)" else host
                Text(
                    "Запомнить логин для $who? Пароль хранится в зашифрованном виде на устройстве.",
                    fontFamily = VpnkaFonts.manrope600, color = VpnkaColors.TextMuted,
                )
            },
            containerColor = VpnkaColors.BgOffCentre,
        )
    }

    // Saved-password manager.
    if (showHistory) {
        BrowserHistorySheet(
            onOpen = { u -> showHistory = false; active.go(u) },
            onClose = { showHistory = false },
        )
    }

    // Нет туннеля — закрываем содержимое ЗАСЛОНКОЙ, но вкладки живут.
    if (!connected) {
        // Заслонка перехватывает и касания, и «назад».
        //
        // Без своего обработчика ввода Compose пропускал нажатия НАСКВОЗЬ:
        // по невидимой странице можно было листать, жать ссылки и вводить в
        // формы, а «назад» ходила по её истории вместо выхода из браузера.
        SmartDeskBackHandler { true }
        Box(
            modifier = Modifier.fillMaxSize().background(VpnkaColors.BgOffMid)
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🔒", fontSize = 44.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Браузер работает только через VPN",
                    fontFamily = VpnkaFonts.nunito800, fontSize = 17.sp,
                    color = VpnkaColors.TextStrong,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Включите подключение на главном экране — вкладки останутся на месте.",
                    fontFamily = VpnkaFonts.manrope600, fontSize = 14.sp,
                    color = VpnkaColors.TextMuted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }

    if (showPwds) {
        var creds by remember { mutableStateOf(PasswordStore.all()) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showPwds = false },
            confirmButton = {},
            dismissButton = { androidx.compose.material3.TextButton(onClick = { showPwds = false }) { Text("Закрыть") } },
            title = { Text("Сохранённые пароли", fontFamily = VpnkaFonts.nunito800, color = VpnkaColors.TextStrong) },
            text = {
                if (creds.isEmpty()) {
                    Text(
                        "Пока ничего не сохранено. При входе на сайт мы предложим запомнить пароль и подставим его в следующий раз.",
                        fontFamily = VpnkaFonts.manrope600, color = VpnkaColors.TextMuted,
                    )
                } else {
                    LazyColumn {
                        items(creds, key = { it.host }) { c ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(c.host, fontFamily = VpnkaFonts.nunito800, fontSize = 14.sp,
                                        color = VpnkaColors.TextStrong, maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    if (c.username.isNotBlank())
                                        Text(c.username, fontFamily = VpnkaFonts.manrope600, fontSize = 12.sp,
                                            color = VpnkaColors.TextMuted, maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                                Text("Удалить", fontFamily = VpnkaFonts.manrope600, fontSize = 13.sp,
                                    color = VpnkaColors.Warning,
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                        .clickable { PasswordStore.remove(c.host); creds = PasswordStore.all() }
                                        .padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                    }
                }
            },
            containerColor = VpnkaColors.BgOffCentre,
        )
    }
}

/** Turn a raw address/query into a loadable URL. */
private fun normalizeUrl(input: String): String {
    val s = input.trim()
    if (s.isEmpty()) return "https://duckduckgo.com/"
    val looksLikeUrl = !s.contains(" ") && s.contains(".")
    return when {
        s.startsWith("http://") || s.startsWith("https://") -> s
        looksLikeUrl -> "https://$s"
        else -> "https://duckduckgo.com/?q=" + android.net.Uri.encode(s)
    }
}

// ------------------------------------------------------------- vpnka store ---

@Composable
private fun VpnkaStoreApp() {
    var installed by remember { mutableStateOf(installedIds().toSet()) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        item {
            Text(
                text = "Устанавливайте приложения на рабочий стол. Уже установленные можно удалить.",
                fontFamily = VpnkaFonts.manrope600,
                fontSize = 13.sp,
                color = VpnkaColors.TextMuted,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
        items(SMARTDESK_VISIBLE.filter { it.removable }, key = { it.id }) { app ->
            val isIn = app.id in installed
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
                    .clip(RoundedCornerShape(12.dp)).background(VpnkaColors.CardServer).border(1.dp, VpnkaColors.Hairline, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center,
                ) { Text(app.glyph, fontSize = 24.sp) }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(app.label, fontFamily = VpnkaFonts.nunito800, fontSize = 16.sp, color = VpnkaColors.TextStrong)
                    Text(app.description, fontFamily = VpnkaFonts.manrope600, fontSize = 12.sp, color = VpnkaColors.TextMuted)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isIn) "Удалить" else "Установить",
                    fontFamily = VpnkaFonts.nunito800,
                    fontSize = 13.sp,
                    color = if (isIn) VpnkaColors.Warning else VpnkaColors.Accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isIn) Color.Transparent else VpnkaColors.Accent.copy(alpha = 0.12f))
                        .clickable {
                            val next = if (isIn) installed - app.id else installed + app.id
                            setInstalled(next.toList())
                            installed = next
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** Wall-clock millis. Isolated so the desktop code reads cleanly. */
private fun nowMillis(): Long = System.currentTimeMillis()
