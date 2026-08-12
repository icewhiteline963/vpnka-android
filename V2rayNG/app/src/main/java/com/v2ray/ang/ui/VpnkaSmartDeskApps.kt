package com.v2ray.ang.ui

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
@Composable
fun VpnkaSmartDeskAppScreen(
    appId: String,
    appLabel: String,
    appGlyph: String,
    online: Boolean,
    onBack: () -> Unit,   // back to the SmartDesk desktop
    onExit: () -> Unit,   // out to the vpnka home screen
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VpnkaColors.BgOffMid)
            // Keep the app bar (icon + title) and bottom bar clear of the
            // system status/navigation bars — the host owns this inset so the
            // apps inside don't each have to.
            .systemBarsPadding(),
    ) {
        SmartDeskAppBar(appId = appId, title = appLabel, glyph = appGlyph, online = online)
        // Sync on open (through the VPN) and after every edit; syncTick keys
        // each app's list so it re-reads once the server's view is merged in.
        val scope = rememberCoroutineScope()
        var syncTick by remember { mutableIntStateOf(0) }
        val onChanged = {
            scope.launch { if (online) SmartDeskSync.sync(); syncTick++ }
            Unit
        }
        LaunchedEffect(Unit) {
            if (online && appId != "browser") { SmartDeskSync.sync(); syncTick++ }
        }
        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            when (appId) {
                "calendar" -> CalendarApp(syncTick, onChanged)
                "contacts" -> ContactsApp(syncTick, onChanged)
                "browser" -> BrowserApp()
                "messages" -> VpnkaMessengerApp()
                "store" -> VpnkaStoreApp()
                "help" -> HelpApp()
                else -> EmptyHint("Приложение недоступно")
            }
        }
        SmartDeskBottomBar(onBack = onBack, onExit = onExit)
    }
}

/** Bottom bar shared by the app screens: back to desktop | exit to home. */
@Composable
private fun SmartDeskBottomBar(onBack: () -> Unit, onExit: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.20f)),
    ) {
        Text(
            text = "‹ Рабочий стол",
            fontFamily = VpnkaFonts.nunito800,
            fontSize = 14.sp,
            color = VpnkaColors.TextStrong,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onBack)
                .padding(vertical = 12.dp),
        )
        Box(modifier = Modifier.width(1.dp).height(20.dp).background(Color.White.copy(alpha = 0.15f)))
        Text(
            text = "⌂ На главный экран",
            fontFamily = VpnkaFonts.nunito800,
            fontSize = 14.sp,
            color = VpnkaColors.TextStrong,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onExit)
                .padding(vertical = 12.dp),
        )
    }
}

@Composable
private fun SmartDeskAppBar(
    appId: String,
    title: String,
    glyph: String,
    online: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(androidx.compose.ui.graphics.Brush.linearGradient(appTint(appId))),
            contentAlignment = Alignment.Center,
        ) { Text(text = glyph, fontSize = 20.sp) }
        Spacer(Modifier.width(12.dp))
        Text(text = title, fontFamily = VpnkaFonts.nunito800, fontSize = 18.sp, color = VpnkaColors.TextStrong)
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (online) VpnkaColors.Green else VpnkaColors.Warning),
        )
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
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
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
        ) { Text("+", fontSize = 30.sp, color = Color.White) }
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

    // Contact detail card (call / email actions).
    opened?.let { c ->
        ContactDetail(
            c = c,
            onCall = { openIntent(context, "tel:" + c.phone) },
            onEmail = { openIntent(context, "mailto:" + c.email) },
            onEdit = { editing = c; showEditor = true },
            onBack = { opened = null },
        )
    }

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
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                    items(filtered, key = { it.id }) { c ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(VpnkaColors.CardServer)
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
        ) { Text("+", fontSize = 30.sp, color = Color.White) }
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
                .clip(RoundedCornerShape(20.dp)).background(VpnkaColors.BgOffCentre)
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
private fun AppScaffold(
    empty: Boolean,
    emptyHint: String,
    onAdd: () -> Unit,
    list: @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (empty) EmptyHint(emptyHint) else list()
        // Add button, bottom-right.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .size(56.dp)
                .clip(CircleShape)
                .background(VpnkaColors.Accent)
                .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "+", fontSize = 30.sp, color = androidx.compose.ui.graphics.Color.White)
        }
    }
}

@Composable
private fun Card(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(VpnkaColors.CardServer)
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
        Text("SmartDesk", fontFamily = VpnkaFonts.nunito900, fontSize = 26.sp, color = VpnkaColors.TextStrong)
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
            .clip(RoundedCornerShape(18.dp))
            .background(VpnkaColors.CardServer)
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
/** One browser tab: a live WebView plus the reactive state the chrome reads. */
@SuppressLint("SetJavaScriptEnabled")
private class BrowserTab(context: Context, val id: Int, startUrl: String) {
    val url = mutableStateOf(startUrl)
    val title = mutableStateOf("")
    val progress = mutableStateOf(0)
    val canBack = mutableStateOf(false)
    val canFwd = mutableStateOf(false)
    val webView: WebView = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, u: String?, favicon: android.graphics.Bitmap?) {
                u?.let { this@BrowserTab.url.value = it }; this@BrowserTab.canBack.value = canGoBack(); this@BrowserTab.canFwd.value = canGoForward()
            }
            override fun onPageFinished(view: WebView?, u: String?) {
                u?.let { this@BrowserTab.url.value = it }; this@BrowserTab.canBack.value = canGoBack(); this@BrowserTab.canFwd.value = canGoForward()
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, p: Int) { this@BrowserTab.progress.value = p }
            override fun onReceivedTitle(view: WebView?, t: String?) { if (!t.isNullOrBlank()) this@BrowserTab.title.value = t }
        }
        loadUrl(startUrl)
    }
    fun go(input: String) = webView.loadUrl(normalizeUrl(input))
}

/** Bare domain («example.com») for the omnibox. */
private fun domainOf(url: String): String = try {
    (android.net.Uri.parse(url).host ?: url).removePrefix("www.")
} catch (e: Exception) { url }

@Composable
private fun BrowserApp() {
    val connected = VpnkaColors.connected
    if (!connected) {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🔒", fontSize = 44.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Браузер работает только через VPN",
                    fontFamily = VpnkaFonts.nunito800,
                    fontSize = 17.sp,
                    color = VpnkaColors.TextStrong,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Включите подключение на главном экране — весь трафик пойдёт через наш VPN.",
                    fontFamily = VpnkaFonts.manrope600,
                    fontSize = 14.sp,
                    color = VpnkaColors.TextMuted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        return
    }

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
    DisposableEffect(httpPort) {
        val supported = WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)
        if (supported) {
            val cfg = ProxyConfig.Builder()
                .addProxyRule("127.0.0.1:$httpPort")
                .build()
            ProxyController.getInstance().setProxyOverride(cfg, { it.run() }, {})
        }
        onDispose {
            if (supported) {
                ProxyController.getInstance().clearProxyOverride({ it.run() }, {})
            }
        }
    }

    val home = "https://duckduckgo.com/"
    val tabs = remember { mutableStateListOf(BrowserTab(context, 0, home)) }
    var nextId by remember { mutableIntStateOf(1) }
    var activeId by remember { mutableIntStateOf(0) }
    val active = tabs.firstOrNull { it.id == activeId } ?: tabs.first()
    var editing by remember { mutableStateOf(false) }
    var omni by remember { mutableStateOf("") }
    var showTabs by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    fun openTab(u: String) { tabs.add(BrowserTab(context, nextId, u)); activeId = nextId; nextId++; showTabs = false }
    fun closeTab(t: BrowserTab) {
        val i = tabs.indexOf(t); tabs.remove(t)
        if (tabs.isEmpty()) { tabs.add(BrowserTab(context, nextId, home)); activeId = nextId; nextId++ }
        else if (activeId == t.id) activeId = tabs[i.coerceAtMost(tabs.lastIndex)].id
        // Halt the closed tab so it stops running JS/media/network in the bg.
        runCatching { t.webView.loadUrl("about:blank"); t.webView.onPause() }
    }

    // Leaving the browser: pause every tab so nothing keeps running after exit.
    DisposableEffect(Unit) {
        onDispose { tabs.forEach { runCatching { it.webView.onPause(); it.webView.loadUrl("about:blank") } } }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Chrome-style top bar: omnibox (lock + domain) · tab count · menu.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(22.dp))
                    .background(VpnkaColors.CardServer)
                    .clickable { omni = active.url.value; editing = true }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🔒", fontSize = 12.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = domainOf(active.url.value),
                    fontFamily = VpnkaFonts.manrope600, fontSize = 14.sp, color = VpnkaColors.TextStrong,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier.size(30.dp).clip(RoundedCornerShape(7.dp))
                    .background(VpnkaColors.CardServer).clickable { showTabs = true },
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
                    DropdownMenuItem(text = { Text("🏠 Домой") }, onClick = { menuOpen = false; active.webView.loadUrl(home) })
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

    if (editing) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { editing = false },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { active.go(omni); editing = false }) { Text("Открыть") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { editing = false }) { Text("Отмена") } },
            title = { Text("Адрес или поиск", fontFamily = VpnkaFonts.nunito800, color = VpnkaColors.TextStrong) },
            text = { DeskField("Адрес или запрос", omni) { omni = it } },
            containerColor = VpnkaColors.BgOffCentre,
        )
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
        items(SMARTDESK_CATALOG.filter { it.removable }, key = { it.id }) { app ->
            val isIn = app.id in installed
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
                    .clip(RoundedCornerShape(16.dp)).background(VpnkaColors.CardServer)
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
