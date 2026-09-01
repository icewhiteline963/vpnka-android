package com.v2ray.ang.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.content.ContextCompat
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.window.Popup
import androidx.compose.ui.unit.IntOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.v2ray.ang.handler.ChatPrefs
import com.v2ray.ang.handler.CallManager
import com.v2ray.ang.handler.Channels
import com.v2ray.ang.handler.Messenger
import com.v2ray.ang.service.VpnkaLinkService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---- Modern chat look: colourful avatars + timestamps ----

/** Deterministic Telegram-style avatar gradient from a name. */
private fun avatarBrush(name: String): Brush {
    val palettes = listOf(
        listOf(Color(0xFFFF9D6C), Color(0xFFE8560C)),
        listOf(Color(0xFF5FD07E), Color(0xFF059669)),
        listOf(Color(0xFF60A5FA), Color(0xFF2563EB)),
        listOf(Color(0xFFC084FC), Color(0xFF7C3AED)),
        listOf(Color(0xFFFB7185), Color(0xFFE11D48)),
        listOf(Color(0xFF22D3EE), Color(0xFF0891B2)),
        listOf(Color(0xFFFBBF24), Color(0xFFD97706)),
    )
    val idx = (name.hashCode() and Int.MAX_VALUE) % palettes.size
    return Brush.linearGradient(palettes[idx])
}

private fun msgTime(ts: Long): String =
    if (ts <= 0L) "" else SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))


/** Bottom tabs, Telegram-style. Nested screens (a chat, a channel) hide the bar
 *  and come back to whichever tab was open. */
private enum class MsgTab { CHATS, CALLS, CONTACTS, SETTINGS, PROFILE }

/** Полки списка чатов. «Группы» из макета не заводим: групповых чатов у нас
 *  нет, а фильтр, который всегда пуст, — обман. */
private enum class ChatFilter(val label: String) {
    ALL("Все"), CHANNELS("Каналы"), UNREAD("Непрочитанные"), ARCHIVE("Архив"),
}

/** «Сообщения» — an E2E messenger in the Telegram mould. */
@Composable
fun VpnkaMessengerApp() {
    var tick by remember { mutableIntStateOf(0) }
    var openId by remember { mutableStateOf<Long?>(null) }
    var handle by remember { mutableStateOf(Messenger.myHandle()) }
    var tab by remember { mutableStateOf(MsgTab.CHATS) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Messenger.Found>>(emptyList()) }
    var channelResults by remember { mutableStateOf<List<Channels.Channel>>(emptyList()) }
    var openChannel by remember { mutableStateOf<Channels.Channel?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(ChatFilter.ALL) }
    var sheetFor by remember { mutableStateOf<Messenger.Contact?>(null) }
    var openProfile by remember { mutableStateOf<Messenger.Contact?>(null) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    // Отдельный счётчик для локальных пометок (закрепить/без звука/архив):
    // общий tick перечитывает и переписку, а тут меняется только полка.
    var prefsTick by remember { mutableIntStateOf(0) }
    var myChannels by remember { mutableStateOf<List<Channels.Channel>>(emptyList()) }
    var typingFrom by remember { mutableStateOf(0L) }
    var typingUntil by remember { mutableStateOf(0L) }
    // Срок жизни строки «печатает…» проверялся только при перерисовке, а
    // назначить её было некому: собеседник начал печатать и передумал —
    // строка висела до следующего события. Гасим по таймеру.
    LaunchedEffect(typingUntil) {
        val left = typingUntil - System.currentTimeMillis()
        if (left > 0) { delay(left + 100); typingFrom = 0L }
    }
    val scope = rememberCoroutineScope()
    val appCtx = LocalContext.current.applicationContext

    // «Назад» из чата возвращает к списку чатов, а не закрывает мессенджер.
    // Три уровня вложенности — список, чат, канал — и ни один раньше не
    // участвовал в системном жесте.
    SmartDeskBackHandler {
        when {
            // Разговор перехватывает «назад» первым. Обработчик стоит ВЫШЕ
            // раннего выхода на экран звонка, и без этой ветки нажатие втихую
            // разбирало стек под звонком: закрывался чат, потом вкладка,
            // потом весь мессенджер — а разговор продолжался без единого
            // элемента управления.
            // В фазе «завершён» «назад» должно закрывать экран, а не молчать:
            // иначе после конца разговора нажатие мертво ещё несколько секунд,
            // а при зависшей фазе — навсегда.
            CallManager.phase == CallManager.Phase.ENDED -> { CallManager.reset(); true }
            CallManager.phase != CallManager.Phase.IDLE -> true
            // Профиль с ключом переехал в лист действий и рисуется на весь
            // экран. Без этой ветки системная «назад» из него выбрасывала
            // сразу на рабочий стол, минуя список чатов.
            openProfile != null -> { openProfile = null; true }
            confirm != null -> { confirm = null; true }
            sheetFor != null -> { sheetFor = null; true }
            showCreate -> { showCreate = false; true }
            // tick++ — то, что раньше делала кнопка «‹» в шапке канала. Без
            // него список каналов не перечитывался, и только что подписанный
            // канал не появлялся на полке.
            openChannel != null -> { openChannel = null; tick++; true }
            openId != null -> { openId = null; true }
            tab != MsgTab.CHATS -> { tab = MsgTab.CHATS; true }
            else -> false
        }
    }

    // Opening the messenger is also when we make sure the background link is
    // up: signing in mid-session would otherwise leave it waiting for the next
    // cold start. No-op when it is already running or switched off.
    LaunchedEffect(Unit) { VpnkaLinkService.start(appCtx) }

    // Register our public key (server assigns @handle from Telegram username
    // or device name), then poll for incoming while this app is open. A
    // WebSocket rides alongside for instant wake + "typing"; polling stays as
    // the fallback so a dropped socket never loses messages.
    // Опрос идёт, только пока экран на переднем плане.
    //
    // Compose не разрушает композицию при сворачивании, поэтому цикл бил по
    // сети каждые 2,5 секунды всё время, пока мессенджер оставался открытым
    // в фоне. Фоновые уведомления за это отвечают отдельно.
    val pollOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        CallManager.attach()  // route incoming call signaling into the engine
        Messenger.refreshMyId()
        handle = Messenger.register("${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        myChannels = Channels.mine()
        pollOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        while (true) {
            run {
                Messenger.connectWs { type, from ->
                    when (type) {
                        "wake" -> scope.launch {
                            var ch = Messenger.poll(); if (Messenger.fetchReceipts()) ch = true
                            if (ch) tick++
                        }
                        "typing" -> { typingFrom = from; typingUntil = System.currentTimeMillis() + 5000 }
                    }
                }
                var changed = Messenger.poll()
                if (Messenger.fetchReceipts()) changed = true
                if (changed) tick++
            }
            delay(2500)
        }
        }
    }
    // Leaving the messenger no longer kills the socket: the link service holds
    // it so a call still rings with the app off screen. Only when that service
    // is switched off does the socket belong to this screen alone.
    //
    // 24.08.2026: флаг ставился на всю жизнь композиции, а Compose не
    // разрушает её при сворачивании приложения. Значит у того, кто последним
    // заходил в «Сообщения», флаг оставался поднятым, служба считала экран
    // видимым и ГЛУШИЛА уведомление о входящем звонке — ровно та фича, ради
    // которой служба и заведена. Теперь флаг следует за ON_RESUME/ON_PAUSE.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> VpnkaLinkService.messengerVisible = true
                Lifecycle.Event.ON_PAUSE -> VpnkaLinkService.messengerVisible = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            VpnkaLinkService.messengerVisible = false
            Messenger.releaseWsEvents()
            if (!VpnkaLinkService.wanted()) Messenger.disconnectWs()
            SmartDeskChrome.barHidden = false
        }
    }
    LaunchedEffect(tick) { myChannels = Channels.mine() }

    // Opened from a message notification: jump into that chat. The contact may
    // not be loaded yet on a cold start — poll() above adds it, and the tick
    // bump then lets openId resolve to it.
    // Ключ — сама просьба: уведомление может прийти, когда мессенджер уже
    // открыт, и однократное чтение её не заметит.
    LaunchedEffect(Messenger.pendingChat) {
        val pending = Messenger.consumePendingChat()
        if (pending != 0L) openId = pending
    }

    // Debounced search over people and channels.
    LaunchedEffect(query) {
        if (query.trim().length < 2) { results = emptyList(); channelResults = emptyList() } else {
            delay(300)
            results = Messenger.searchUsers(query)
            channelResults = Channels.search(query)
        }
    }

    val contacts = remember(tick) { Messenger.contacts() }

    // A voice call takes over the whole screen (over the contact list or any
    // chat). The polling/WS effects above stay mounted, so signaling keeps
    // flowing while the call UI is shown.
    if (CallManager.phase != CallManager.Phase.IDLE) {
        // Во время разговора панель супер-приложения не нужна — под кнопками
        // звонка она только мешала.
        DisposableEffect(Unit) {
            SmartDeskChrome.barHidden = true
            // Снимать флаг обязательно: ветка звонка — ранний выход, и пока
            // она на экране, основной эффект мессенджера из композиции
            // отсутствует. Если приложение умрёт во время разговора, флаг
            // останется поднятым НАВСЕГДА — панель на рабочем столе
            // пропадёт до перезапуска.
            onDispose { SmartDeskChrome.barHidden = false }
        }
        CallScreen()
        return
    }

    // Hide the SmartDesk host bar while a nested screen (chat, channel) is open —
    // those have their own header; the tabbed main screen keeps the bar.
    // Панель супер-приложения прячется на ВСЁ время мессенджера, а не только
    // на вложенных экранах: у него своя пятипунктовая полоса (Чаты · Звонки ·
    // Контакты · Настройки · Профиль), и две такие полосы одна над другой —
    // это не навигация, а стена. Возврат к столу — системной «назад».
    DisposableEffect(Unit) {
        SmartDeskChrome.barHidden = true
        onDispose { SmartDeskChrome.barHidden = false }
    }

    openChannel?.let { ch ->
        ChannelScreen(channel = ch)
        return
    }

    openProfile?.let { c ->
        ContactProfileScreen(contact = c, onBack = { openProfile = null })
        return
    }

    openId?.let { id ->
        val c = contacts.firstOrNull { it.id == id }
        if (c != null) {
            val typing = typingFrom == c.id && System.currentTimeMillis() < typingUntil
            // «Прочитано» — при уходе ИЗ чата и при сворачивании приложения.
            //
            // Ключом стоял `tick`, а он растёт на каждый пришедший пакет:
            // эффект пересоздавался, и отметка ставилась на каждое новое
            // сообщение. Чат, оставленный открытым в свёрнутом приложении,
            // молча гасил непрочитанное у сообщений, которых никто не видел, —
            // а уведомления по ним уже не приходило.
            val chatLifecycle = LocalLifecycleOwner.current
            DisposableEffect(c.id, chatLifecycle) {
                val obs = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_PAUSE) {
                        ChatPrefs.markSeen(c.id); prefsTick++
                    }
                }
                chatLifecycle.lifecycle.addObserver(obs)
                onDispose {
                    chatLifecycle.lifecycle.removeObserver(obs)
                    ChatPrefs.markSeen(c.id); prefsTick++
                }
            }
            ChatScreen(contact = c, tick = tick, typing = typing, onSent = { tick++ })
            return
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            when (tab) {
                MsgTab.CONTACTS -> ContactsTab(
                    contacts = contacts,
                    onOpen = { openId = it },
                    onStartChat = { r -> Messenger.startChat(r); tick++; openId = r.id },
                )
                MsgTab.SETTINGS -> SettingsTab()
                MsgTab.PROFILE -> ProfileTab(handle)
                MsgTab.CHATS -> Column(modifier = Modifier.fillMaxSize()) {
                    // Current user's @handle up top.
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Tap your own name/avatar → your profile (nick, key, settings).
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clip(RoundedCornerShape(11.dp))
                                .clickable { tab = MsgTab.PROFILE }
                                .padding(end = 8.dp, top = 2.dp, bottom = 2.dp),
                        ) {
                            MsgAvatar(if (handle.isBlank()) "?" else handle, size = 36)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = if (handle.isBlank()) "…" else "@$handle",
                                fontFamily = VpnkaFonts.nunito800, fontSize = 16.sp, color = VpnkaColors.TextStrong,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clip(CircleShape).background(VpnkaColors.Accent.copy(alpha = 0.14f))
                                .clickable { showCreate = true }.padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text("＋ Канал", fontFamily = VpnkaFonts.nunito800, fontSize = 13.sp, color = VpnkaColors.Accent)
                        }
                    }
                    // Search people and channels.
                    var showSearchTip by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.weight(1f)) { MsgField("Поиск людей и каналов", query) { query = it } }
                        Spacer(Modifier.width(6.dp))
                        Box {
                            Text(
                                "?",
                                fontFamily = VpnkaFonts.nunito800, fontSize = 16.sp, color = VpnkaColors.Accent,
                                modifier = Modifier.clip(CircleShape).background(VpnkaColors.CardServer)
                                    .clickable { showSearchTip = true }.padding(horizontal = 12.dp, vertical = 10.dp),
                            )
                            if (showSearchTip) {
                                Popup(
                                    alignment = Alignment.TopEnd,
                                    onDismissRequest = { showSearchTip = false },
                                    offset = IntOffset(0, 100),
                                ) {
                                    Box(
                                        modifier = Modifier.widthIn(max = 250.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(VpnkaColors.BgOffCentre)
                                            .padding(14.dp),
                                    ) {
                                        Text(
                                            "Если у человека установлена VPNka, найдите его по нику из Telegram — без символа @. Также ищет по каналам.",
                                            fontFamily = VpnkaFonts.manrope600, fontSize = 12.sp,
                                            color = VpnkaColors.TextStrong, lineHeight = 17.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))

                    // Полки. «Архив» показываем только когда в нём что-то
                    // лежит, иначе он занимает место под пустоту.
                    val hasArchive = remember(prefsTick, tick) { ChatPrefs.archived().isNotEmpty() }
                    // Разархивировали последний чат, стоя в «Архиве», — чип
                    // пропадал, а полка оставалась выбранной: ни один не
                    // подсвечен и надпись «в архиве пусто».
                    LaunchedEffect(hasArchive) {
                        if (!hasArchive && filter == ChatFilter.ARCHIVE) filter = ChatFilter.ALL
                    }
                    if (query.trim().length < 2) {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 14.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ChatFilter.entries.forEach { f ->
                                if (f == ChatFilter.ARCHIVE && !hasArchive) return@forEach
                                val on = filter == f
                                Text(
                                    f.label,
                                    fontFamily = VpnkaFonts.nunito800, fontSize = 11.sp,
                                    color = if (on) VpnkaColors.OnAccent else VpnkaColors.TextMuted,
                                    modifier = Modifier.padding(end = 7.dp)
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(if (on) VpnkaColors.Accent else VpnkaColors.CardServer)
                                        .clickable { filter = f }
                                        .padding(horizontal = 11.dp, vertical = 6.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }

                    if (query.trim().length >= 2) {
                        // Search results: channels then people.
                        if (results.isEmpty() && channelResults.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                                Text("Ничего не найдено", fontFamily = VpnkaFonts.manrope600, fontSize = 14.sp, color = VpnkaColors.TextMuted)
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                                if (channelResults.isNotEmpty()) {
                                    item { SectionLabel("Каналы") }
                                    items(channelResults, key = { "ch" + it.id }) { ch ->
                                        ResultRow(glyph = "📢", title = ch.title, sub = "@${ch.handle}") { openChannel = ch; query = "" }
                                    }
                                }
                                if (results.isNotEmpty()) {
                                    item { SectionLabel("Люди") }
                                    items(results, key = { it.id }) { r ->
                                        ResultRow(glyph = null, title = "@${r.handle}", sub = null) {
                                            Messenger.startChat(r); tick++; query = ""; openId = r.id
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Список чатов. Порядок — закреплённые сверху, дальше по
                        // свежести сообщения: чат, в котором только что ответили,
                        // не должен уезжать вниз только потому, что он старый.
                        val pinnedIds = remember(tick, prefsTick) { ChatPrefs.pinned() }
                        val mutedIds = remember(tick, prefsTick) { ChatPrefs.muted() }
                        val archivedIds = remember(tick, prefsTick) { ChatPrefs.archived() }
                        val unreadOf = remember(tick, prefsTick) {
                            contacts.associate { it.id to ChatPrefs.unread(it.id) }
                        }
                        val lastOf = remember(tick) {
                            contacts.associate { it.id to Messenger.messages(it.id).lastOrNull() }
                        }

                        val visible = contacts
                            .filter { c ->
                                when (filter) {
                                    ChatFilter.ARCHIVE -> archivedIds.contains(c.id)
                                    ChatFilter.UNREAD -> !archivedIds.contains(c.id) && (unreadOf[c.id] ?: 0) > 0
                                    ChatFilter.CHANNELS -> false
                                    else -> !archivedIds.contains(c.id)
                                }
                            }
                            .sortedWith(
                                compareByDescending<Messenger.Contact> { pinnedIds.contains(it.id) }
                                    .thenByDescending { lastOf[it.id]?.ts ?: 0L },
                            )
                        val showChannels =
                            filter == ChatFilter.ALL || filter == ChatFilter.CHANNELS
                        val pinnedShown = visible.filter { pinnedIds.contains(it.id) }
                        val restShown = visible.filterNot { pinnedIds.contains(it.id) }

                        if (visible.isEmpty() && (!showChannels || myChannels.isEmpty())) {
                            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    when (filter) {
                                        ChatFilter.ALL ->
                                            "Чатов пока нет. Найдите человека по нику через поиск выше — переписка шифруется."
                                        ChatFilter.ARCHIVE -> "В архиве пусто."
                                        ChatFilter.UNREAD -> "Непрочитанных нет."
                                        ChatFilter.CHANNELS -> "Вы пока не подписаны ни на один канал."
                                    },
                                    fontFamily = VpnkaFonts.manrope600, fontSize = 14.sp,
                                    color = VpnkaColors.TextMuted, textAlign = TextAlign.Center,
                                )
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                                if (showChannels && myChannels.isNotEmpty()) {
                                    item { SectionLabel("Каналы") }
                                    items(myChannels, key = { "ch" + it.id }) { ch ->
                                        ResultRow(glyph = "📢", title = ch.title, sub = "@${ch.handle}") { openChannel = ch }
                                    }
                                }
                                if (pinnedShown.isNotEmpty()) {
                                    item { SectionLabel("Закреплённые") }
                                    items(pinnedShown, key = { "p" + it.id }) { c ->
                                        ChatRow(
                                            contact = c, last = lastOf[c.id], unread = unreadOf[c.id] ?: 0,
                                            pinned = true, muted = mutedIds.contains(c.id),
                                            onOpen = { openId = c.id }, onLong = { sheetFor = c },
                                        )
                                    }
                                }
                                if (restShown.isNotEmpty()) {
                                    if (pinnedShown.isNotEmpty() || (showChannels && myChannels.isNotEmpty())) {
                                        item { SectionLabel(if (filter == ChatFilter.ARCHIVE) "Архив" else "Все чаты") }
                                    }
                                    items(restShown, key = { it.id }) { c ->
                                        ChatRow(
                                            contact = c, last = lastOf[c.id], unread = unreadOf[c.id] ?: 0,
                                            pinned = false, muted = mutedIds.contains(c.id),
                                            onOpen = { openId = c.id }, onLong = { sheetFor = c },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                MsgTab.CALLS -> CallsTab(
                    contacts = contacts,
                    tick = prefsTick,
                    onCall = { id, name -> CallManager.startCall(appCtx, id, name) },
                    onOpen = { openId = it },
                    onClear = { ChatPrefs.clearCalls(); prefsTick++ },
                )
            }
        }

        MessengerTabBar(current = tab, onSelect = { tab = it })
    }

    // Долгое нажатие на чат — лист действий, как в макете. Разрушающие пункты
    // (очистить, удалить) идут через подтверждение и трогают ТОЛЬКО это
    // устройство: у собеседника переписка остаётся, и текст об этом прямо
    // говорит, чтобы «удалить» не понимали как «удалить у обоих».
    sheetFor?.let { c ->
        val pinnedNow = ChatPrefs.isPinned(c.id)
        val mutedNow = ChatPrefs.isMuted(c.id)
        val archivedNow = ChatPrefs.isArchived(c.id)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { sheetFor = null },
            title = { Text(c.name, fontFamily = VpnkaFonts.nunito800, color = VpnkaColors.TextStrong) },
            text = {
                Column {
                    // Профиль с отпечатком ключа открывался по имени в шапке.
                    // Шапки больше нет — значит место ему здесь, иначе
                    // проверить ключ собеседника стало бы нечем.
                    SheetAction("🪪  Профиль и ключ") { sheetFor = null; openProfile = c }
                    SheetAction(if (pinnedNow) "📌  Открепить" else "📌  Закрепить") {
                        ChatPrefs.setPinned(c.id, !pinnedNow); prefsTick++; sheetFor = null
                    }
                    SheetAction(if (mutedNow) "🔔  Со звуком" else "🔕  Без звука") {
                        ChatPrefs.setMuted(c.id, !mutedNow); prefsTick++; sheetFor = null
                    }
                    SheetAction(if (archivedNow) "📤  Вернуть из архива" else "📥  В архив") {
                        ChatPrefs.setArchived(c.id, !archivedNow); prefsTick++; sheetFor = null
                    }
                    SheetAction("🧹  Очистить историю", danger = true) {
                        sheetFor = null
                        confirm = "Очистить переписку с ${c.name}? Сообщения исчезнут только на этом устройстве." to {
                            Messenger.clearChat(c.id); tick++; prefsTick++
                        }
                    }
                    SheetAction("🗑  Удалить чат", danger = true) {
                        sheetFor = null
                        confirm = "Удалить чат с ${c.name}? Он пропадёт из списка вместе с перепиской — только на этом устройстве." to {
                            Messenger.deleteChat(c.id); ChatPrefs.forget(c.id); tick++; prefsTick++
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { sheetFor = null }) { Text("Закрыть") }
            },
            containerColor = VpnkaColors.BgOffCentre,
        )
    }

    confirm?.let { (text, action) ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Подтвердите", fontFamily = VpnkaFonts.nunito800, color = VpnkaColors.TextStrong) },
            text = { Text(text, fontFamily = VpnkaFonts.manrope600, fontSize = 13.sp, color = VpnkaColors.TextMuted, lineHeight = 18.sp) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { action(); confirm = null }) {
                    Text("Да", color = VpnkaColors.Warning)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirm = null }) { Text("Отмена") }
            },
            containerColor = VpnkaColors.BgOffCentre,
        )
    }

    if (showCreate) {
        var chandle by remember { mutableStateOf("") }
        var ctitle by remember { mutableStateOf("") }
        var cerr by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Новый канал", fontFamily = VpnkaFonts.nunito800, color = VpnkaColors.TextStrong) },
            text = {
                Column {
                    MsgField("Ник канала (латиница)", chandle) { chandle = it.lowercase(); cerr = null }
                    Spacer(Modifier.height(6.dp))
                    MsgField("Название", ctitle) { ctitle = it; cerr = null }
                    if (cerr != null) Text(cerr!!, fontSize = 12.sp, color = VpnkaColors.Warning)
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    scope.launch {
                        val ch = Channels.create(chandle.trim(), ctitle.trim())
                        if (ch == null) cerr = "Ник занят или нет связи"
                        else { myChannels = Channels.mine(); showCreate = false; openChannel = ch }
                    }
                }) { Text("Создать") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { showCreate = false }) { Text("Отмена") } },
            containerColor = VpnkaColors.BgOffCentre,
        )
    }
}

@Composable
private fun SheetAction(label: String, danger: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        fontFamily = VpnkaFonts.nunito800, fontSize = 14.sp,
        color = if (danger) VpnkaColors.Warning else VpnkaColors.TextStrong,
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
    )
}

/**
 * Строка списка чатов — по макету.
 *
 * В макете это НЕ карточка, а строка с волосяным разделителем снизу: список
 * читается как список, а не как стопка плиток. Аватар круглый (46), имя и
 * время в одной строке по базовой линии, под ними предпросмотр и счётчик
 * непрочитанного; «⋯» справа открывает действия — тем, кто не догадается
 * подержать палец.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatRow(
    contact: Messenger.Contact,
    last: Messenger.Msg?,
    unread: Int,
    pinned: Boolean,
    muted: Boolean,
    onOpen: () -> Unit,
    onLong: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth()
                .combinedClickable(onClick = onOpen, onLongClick = onLong)
                .padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MsgAvatar(contact.name, size = 46)
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        contact.name, fontFamily = VpnkaFonts.nunito800, fontSize = 14.sp,
                        color = VpnkaColors.TextStrong, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (pinned) Text(" 📌", fontSize = 10.sp, color = VpnkaColors.TextFaint)
                    if (muted) Text(" 🔕", fontSize = 10.sp, color = VpnkaColors.TextFaint)
                    Spacer(Modifier.weight(1f))
                    if (last != null) {
                        Text(
                            msgTime(last.ts), fontFamily = VpnkaFonts.manrope600, fontSize = 10.sp,
                            color = VpnkaColors.TextFaint,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val preview = when {
                        last == null -> "Нет сообщений"
                        last.k == "image" -> (if (last.mine) "Вы: " else "") + "📷 Фото"
                        last.k == "voice" -> (if (last.mine) "Вы: " else "") + "🎤 Голосовое"
                        last.k == "video" -> (if (last.mine) "Вы: " else "") + "🎬 Видео"
                        else -> (if (last.mine) "Вы: " else "") + last.text
                    }
                    Text(
                        preview, fontFamily = VpnkaFonts.manrope600, fontSize = 12.sp,
                        // Непрочитанное читается ярче — это единственная разница,
                        // жирного начертания в макете здесь нет.
                        color = if (unread > 0) VpnkaColors.TextMuted else VpnkaColors.TextFaint,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (unread > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (unread > 99) "99+" else unread.toString(),
                            fontFamily = VpnkaFonts.nunito800, fontSize = 10.sp,
                            color = if (muted) VpnkaColors.TextStrong else VpnkaColors.OnAccent,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (muted) VpnkaColors.TextFaint.copy(alpha = 0.35f)
                                    else VpnkaColors.Accent,
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Text(
                "⋯", fontSize = 14.sp, color = VpnkaColors.TextFaint,
                modifier = Modifier.clip(CircleShape).clickable(onClick = onLong)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(VpnkaColors.Hairline))
    }
}

/** Журнал звонков. Пишется на отбое (см. CallManager) и живёт только здесь. */
@Composable
private fun CallsTab(
    contacts: List<Messenger.Contact>,
    tick: Int,
    onCall: (Long, String) -> Unit,
    onOpen: (Long) -> Unit,
    onClear: () -> Unit,
) {
    val calls = remember(tick) { ChatPrefs.calls() }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Звонки", fontFamily = VpnkaFonts.nunito800, fontSize = 18.sp, color = VpnkaColors.TextStrong)
            Spacer(Modifier.weight(1f))
            if (calls.isNotEmpty()) {
                Text(
                    "Очистить", fontFamily = VpnkaFonts.nunito800, fontSize = 12.sp, color = VpnkaColors.TextMuted,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClear)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
        if (calls.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Звонков пока не было. Позвонить можно из чата — кнопкой «Позвонить».",
                    fontFamily = VpnkaFonts.manrope600, fontSize = 14.sp,
                    color = VpnkaColors.TextMuted, textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                items(calls.size, key = { calls[it].ts }) { i ->
                    val c = calls[i]
                    val known = contacts.any { it.id == c.peerId }
                    // Имя берём из контактов: исходящий писался как «@ник», а
                    // входящий — как присланное имя без «@», и один человек
                    // оказывался в журнале двумя строками с разными аватарами.
                    val shown = contacts.firstOrNull { it.id == c.peerId }?.name
                        ?: c.name.ifBlank { "Без имени" }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(13.dp)).background(VpnkaColors.CardServer).border(1.dp, VpnkaColors.Hairline, RoundedCornerShape(13.dp))
                            .clickable { if (known) onOpen(c.peerId) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MsgAvatar(shown, size = 42)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                shown,
                                fontFamily = VpnkaFonts.nunito800, fontSize = 15.sp,
                                color = if (c.dir == "missed") VpnkaColors.Warning else VpnkaColors.TextStrong,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            val arrow = when (c.dir) {
                                "outgoing" -> "↗ исходящий"
                                "missed" -> "↙ пропущенный"
                                "declined" -> "↙ отклонён"
                                else -> "↙ входящий"
                            }
                            val dur = if (c.sec > 0) " · ${c.sec / 60}:${"%02d".format(c.sec % 60)}" else ""
                            Text(
                                "$arrow · ${msgTime(c.ts)}$dur",
                                fontFamily = VpnkaFonts.manrope600, fontSize = 12.sp, color = VpnkaColors.TextMuted,
                            )
                        }
                        Text(
                            "☎", fontSize = 18.sp, color = VpnkaColors.Accent,
                            modifier = Modifier.clip(CircleShape)
                                .clickable { onCall(c.peerId, shown) }.padding(10.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    // Капс с разрядкой и приглушённый — в макете это не заголовок, а метка
    // полки: она должна отделять, а не соперничать с именами в списке.
    Text(
        text.uppercase(),
        fontFamily = VpnkaFonts.nunito800,
        fontSize = 10.sp,
        letterSpacing = 1.0.sp,
        color = VpnkaColors.TextFaint,
        modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
    )
}

@Composable
private fun ResultRow(glyph: String?, title: String, sub: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
            .clip(RoundedCornerShape(12.dp)).background(VpnkaColors.CardServer).border(1.dp, VpnkaColors.Hairline, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) {
            Box(modifier = Modifier.size(44.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFFC084FC), Color(0xFF7C3AED)))),
                contentAlignment = Alignment.Center) {
                Text(glyph, fontSize = 20.sp)
            }
        } else {
            MsgAvatar(title.removePrefix("@"))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontFamily = VpnkaFonts.nunito800, fontSize = 16.sp, color = VpnkaColors.TextStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, fontFamily = VpnkaFonts.manrope600, fontSize = 13.sp, color = VpnkaColors.TextMuted)
        }
    }
}

@Composable
private fun ChannelScreen(channel: Channels.Channel) {
    val scope = rememberCoroutineScope()
    var posts by remember { mutableStateOf<List<Channels.Post>>(emptyList()) }
    // The owner can always read/post, even if the subscription flag lags.
    var subscribed by remember { mutableStateOf(channel.subscribed || channel.isOwner) }
    var draft by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(refresh) {
        if (!subscribed) return@LaunchedEffect
        // Догружаем ленту постранично.
        //
        // Клиент всегда просил «с нуля», а сервер отдаёт сотню по возрастанию
        // — после сотого поста новые записи не видел НИКТО и никогда. Идём
        // страницами от последнего известного, пока сервер не перестанет
        // отдавать новое.
        val acc = posts.toMutableList()
        var since = acc.maxOfOrNull { it.id } ?: 0L
        var guard = 0
        while (guard++ < 20) {
            val page = Channels.feed(channel.id, since)
            if (page.isEmpty()) break
            acc.addAll(page.filterNot { p -> acc.any { it.id == p.id } })
            val newest = page.maxOf { it.id }
            if (newest <= since) break
            since = newest
        }
        posts = acc.sortedBy { it.id }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!subscribed) {
            Box(modifier = Modifier.fillMaxSize().weight(1f).padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Шапки у канала больше нет — значит хотя бы здесь надо
                // сказать, на что предлагается подписаться.
                Text(
                    channel.title, fontFamily = VpnkaFonts.nunito800, fontSize = 17.sp,
                    color = VpnkaColors.TextStrong,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "@${channel.handle}", fontFamily = VpnkaFonts.manrope600,
                    fontSize = 12.sp, color = VpnkaColors.TextFaint,
                )
                Spacer(Modifier.height(12.dp))
                Text("Подпишитесь, чтобы читать канал", fontFamily = VpnkaFonts.manrope600, fontSize = 14.sp, color = VpnkaColors.TextMuted)
                    Spacer(Modifier.height(10.dp))
                    Box(modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(VpnkaColors.Accent)
                        .clickable { scope.launch { if (Channels.subscribe(channel.id)) { subscribed = true; refresh++ } } }
                        .padding(horizontal = 20.dp, vertical = 10.dp)) {
                        Text("Подписаться", fontFamily = VpnkaFonts.nunito800, fontSize = 15.sp, color = VpnkaColors.OnAccent)
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = 8.dp, bottom = 6.dp,
                ),
            ) {
                if (posts.isEmpty()) item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Постов пока нет", fontFamily = VpnkaFonts.manrope600, fontSize = 14.sp, color = VpnkaColors.TextMuted)
                    }
                }
                items(posts, key = { it.id }) { p ->
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(11.dp)).background(VpnkaColors.CardServer).border(1.dp, VpnkaColors.Hairline, RoundedCornerShape(11.dp)).padding(12.dp)) {
                        Text(p.body, fontFamily = VpnkaFonts.manrope600, fontSize = 15.sp, color = VpnkaColors.TextStrong)
                    }
                }
            }
            }
            if (channel.isOwner) {
                Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) { MsgField("Написать в канал", draft) { draft = it } }
                    Spacer(Modifier.width(8.dp))
                    Box(modifier = Modifier.size(46.dp).clip(androidx.compose.foundation.shape.CircleShape).background(VpnkaColors.Accent)
                        .clickable {
                            val t = draft.trim()
                            if (t.isNotBlank()) { draft = ""; scope.launch { if (Channels.post(channel.id, t)) refresh++ } }
                        }, contentAlignment = Alignment.Center) { Text("➤", fontSize = 20.sp, color = VpnkaColors.OnAccent) }
                }
            }
        }
    }
}

// ✓ отправлено, ✓✓ серые — доставлено, ✓✓ голубые — прочитано.
private fun msgrTicks(m: Messenger.Msg, r: Messenger.Receipt?): Pair<String, Color> {
    if (r == null || m.id <= 0L) return "✓" to Color.White.copy(alpha = 0.5f)
    return when {
        m.id <= r.read -> "✓✓" to Color(0xFF8FE3FF)
        m.id <= r.delivered -> "✓✓" to Color.White.copy(alpha = 0.7f)
        else -> "✓" to Color.White.copy(alpha = 0.5f)
    }
}

// Downscale to ≤1280px and JPEG-compress before encrypting — keeps blobs small.
private fun resizeJpeg(raw: ByteArray, max: Int = 1280, q: Int = 80): ByteArray {
    val bm = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return raw
    val w = bm.width; val h = bm.height
    val scaled = if (w > max || h > max) {
        val s = max.toFloat() / maxOf(w, h)
        Bitmap.createScaledBitmap(bm, (w * s).toInt().coerceAtLeast(1), (h * s).toInt().coerceAtLeast(1), true)
    } else bm
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, q, out)
    return out.toByteArray()
}

@Composable
private fun ChatScreen(
    contact: Messenger.Contact,
    tick: Int,
    typing: Boolean,
    onSent: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf("") }
    val msgs = remember(tick, contact.id) { Messenger.messages(contact.id) }
    val receipt = remember(tick, contact.id) { Messenger.receiptFor(contact.id) }
    // null = no photo being sent; 0..100 = upload progress; -1 = failed.
    var sendPct by remember { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()
    // Follow the conversation: when a message is sent OR arrives (msgs grows),
    // and while a photo uploads, keep the newest row on screen.

    // Отчёт о прочтении — только когда экран РЕАЛЬНО на переднем плане.
    // Свёрнутое приложение с открытым чатом продолжало слать собеседнику ✓✓
    // на сообщения, которых никто не читал.
    val readLifecycle = LocalLifecycleOwner.current
    var chatResumed by remember { mutableStateOf(true) }
    DisposableEffect(readLifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> chatResumed = true
                Lifecycle.Event.ON_PAUSE -> chatResumed = false
                else -> Unit
            }
        }
        readLifecycle.lifecycle.addObserver(obs)
        onDispose { readLifecycle.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(tick, contact.id, chatResumed) {
        if (!chatResumed) return@LaunchedEffect
        val maxIn = msgs.filter { !it.mine }.maxOfOrNull { it.id } ?: 0L
        if (maxIn > 0L) Messenger.markRead(contact.id, maxIn)
    }

    val context = LocalContext.current
    // Mic permission gate for placing a call from the 📞 header button.
    val callMicLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) CallManager.startCall(context, contact.id, contact.name) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            try {
                val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
                sendPct = 0
                val ok = Messenger.sendImage(contact.id, resizeJpeg(raw)) { p -> sendPct = p }
                sendPct = if (ok) null else -1
                if (ok) onSent()
            } catch (e: Exception) { sendPct = -1 }
        }
    }

    var compressing by remember { mutableStateOf(false) }

    // Следим за концом переписки: новое сообщение, отправка фото и строка
    // «Сжатие видео…» — всё это должно оставаться на виду.
    LaunchedEffect(msgs.size, sendPct, compressing) {
        val total = msgs.size + (if (sendPct != null) 1 else 0) + (if (compressing) 1 else 0)
        if (total > 0) listState.animateScrollToItem(total - 1)
    }
    var showAttach by remember { mutableStateOf(false) }
    var playVideo by remember { mutableStateOf<String?>(null) }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            compressing = true
            val maxH = if (Messenger.setting("media_min", true)) 480 else 720
            val file = MediaCompress.compressVideo(context, uri, maxH)
            compressing = false
            if (file == null) {
                android.widget.Toast.makeText(context, "Не удалось обработать видео", android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }
            val bytes = withContext(Dispatchers.IO) { runCatching { file.readBytes() }.getOrNull() }
            val dur = withContext(Dispatchers.IO) { videoDurationSec(file.absolutePath) }
            file.delete()
            if (bytes == null) return@launch
            if (bytes.size > 12 * 1024 * 1024) {
                android.widget.Toast.makeText(context, "Видео слишком большое — снимите короче", android.widget.Toast.LENGTH_LONG).show()
                return@launch
            }
            sendPct = 0
            val ok = Messenger.sendVideo(contact.id, bytes, dur) { p -> sendPct = p }
            sendPct = if (ok) null else -1
            if (ok) onSent()
        }
    }

    var recording by remember { mutableStateOf(false) }
    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { micGranted = it }
    DisposableEffect(Unit) {
        onDispose { MessengerVoice.stopPlayback(); if (MessengerVoice.isRecording) MessengerVoice.cancelRecording() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = 8.dp, bottom = 6.dp,
            ),
        ) {
            // Outgoing messages all carry id=0 and can share a millisecond, so
            // the list index guarantees a unique key (Compose crashes on dupes).
            itemsIndexed(msgs, key = { i, m -> "$i:${m.id}:${m.ts}" }) { _, m ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start,
                ) {
                    // Пузырь по макету: 16px со срезанным «хвостиком» 5px со
                    // своей стороны. Своё сообщение — не зелёная плашка с белым
                    // текстом, а подложка акцентом навылет (22 %) с рамкой
                    // (45 %); текст в обоих случаях обычный. Так своё и чужое
                    // отличаются оттенком фона, а не двумя разными типографиками.
                    val bubbleShape = RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomStart = if (m.mine) 16.dp else 5.dp,
                        bottomEnd = if (m.mine) 5.dp else 16.dp,
                    )
                    Box(
                        modifier = Modifier.widthIn(max = 270.dp)
                            .clip(bubbleShape)
                            .then(
                                if (m.mine)
                                    Modifier.background(VpnkaColors.Accent.copy(alpha = 0.22f))
                                        .border(1.dp, VpnkaColors.Accent.copy(alpha = 0.45f), bubbleShape)
                                else Modifier.background(VpnkaColors.CardServer)
                                    .border(1.dp, VpnkaColors.Hairline, bubbleShape)
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.Start) {
                            if (m.k == "image" && m.img.isNotEmpty()) {
                                val bmp = remember(m.img) {
                                    try {
                                        val b = Base64.decode(m.img, Base64.NO_WRAP)
                                        BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap()
                                    } catch (e: Exception) { null }
                                }
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp, contentDescription = null, contentScale = ContentScale.Fit,
                                        modifier = Modifier.widthIn(max = 220.dp).clip(RoundedCornerShape(10.dp)),
                                    )
                                } else {
                                    Text("🖼 фото", fontFamily = VpnkaFonts.manrope600, fontSize = 15.sp,
                                        color = VpnkaColors.TextStrong)
                                }
                            } else if (m.k == "voice") {
                                val playing = MessengerVoice.playingId == m.id
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clickable(enabled = m.img.isNotEmpty()) { MessengerVoice.toggle(context, m.id, m.img) }
                                        .padding(vertical = 2.dp),
                                ) {
                                    Text(
                                        if (playing) "⏸" else "▶", fontSize = 22.sp,
                                        color = VpnkaColors.Accent,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        "🎤  " + fmtVoice(m.dur),
                                        fontFamily = VpnkaFonts.manrope600, fontSize = 15.sp,
                                        color = VpnkaColors.TextStrong,
                                    )
                                }
                            } else if (m.k == "video") {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clickable(enabled = m.img.isNotEmpty()) { playVideo = m.img }
                                        .padding(vertical = 2.dp),
                                ) {
                                    Box(
                                        modifier = Modifier.size(56.dp, 40.dp).clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF1F2937)),
                                        contentAlignment = Alignment.Center,
                                    ) { Text("▶", fontSize = 20.sp, color = Color.White) }
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        "🎬  Видео" + if (m.dur > 0) "  ·  ${fmtVoice(m.dur)}" else "",
                                        fontFamily = VpnkaFonts.manrope600, fontSize = 15.sp,
                                        color = VpnkaColors.TextStrong,
                                    )
                                }
                            } else {
                                Text(m.text, fontFamily = VpnkaFonts.manrope600, fontSize = 15.sp,
                                    color = VpnkaColors.TextStrong)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.align(Alignment.End).padding(top = 3.dp),
                            ) {
                                // Авторство не доказано: подписи нет или она
                                // не сошлась. Сообщение показываем — старые
                                // версии подписей не ставят, — но за
                                // проверенное не выдаём.
                                if (!m.mine && !m.verified) {
                                    Text(
                                        "не подтверждено  ",
                                        fontFamily = VpnkaFonts.manrope600, fontSize = 10.sp,
                                        color = VpnkaColors.Amber,
                                    )
                                }
                                Text(
                                    msgTime(m.ts), fontSize = 10.sp,
                                    color = VpnkaColors.TextFaint,
                                )
                                if (m.mine) {
                                    Spacer(Modifier.width(4.dp))
                                    val (mark, tint) = msgrTicks(m, receipt)
                                    Text(mark, fontSize = 10.sp, color = tint)
                                }
                            }
                        }
                    }
                }
            }
            if (compressing) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(12.dp))
                                .background(VpnkaColors.Green.copy(alpha = 0.85f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text("🎬 Сжатие видео…", fontFamily = VpnkaFonts.manrope600, fontSize = 14.sp, color = Color.White)
                        }
                    }
                }
            }
            // Send status: uploading media is dozens of small requests over the
            // shaped RU leg, so show progress rather than a frozen UI.
            sendPct?.let { p ->
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(12.dp))
                                .background(VpnkaColors.Green.copy(alpha = 0.85f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                if (p < 0) "⚠️ Не удалось отправить" else "⬆ Отправка… $p%",
                                fontFamily = VpnkaFonts.manrope600, fontSize = 14.sp, color = Color.White,
                            )
                        }
                    }
                }
            }
        }
        }
        // «Печатает…» — строкой над полем ввода.
        //
        // Раньше она жила под именем в шапке; шапки не стало, а само событие
        // приходит по-прежнему. Здесь она появляется только на те секунды,
        // пока собеседник печатает, и в покое места не занимает.
        if (typing) {
            Text(
                "печатает…",
                fontFamily = VpnkaFonts.manrope600, fontSize = 11.sp,
                color = VpnkaColors.Accent,
                modifier = Modifier.padding(start = 14.dp, bottom = 2.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Звонок переехал сюда из убранной шапки: строка ввода — это
            // единственное, что есть на экране чата всегда.
            if (!recording) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape)
                        .background(VpnkaColors.CardSettings)
                        .clickable {
                            if (ContextCompat.checkSelfPermission(
                                    context, android.Manifest.permission.RECORD_AUDIO
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                CallManager.startCall(context, contact.id, contact.name)
                            } else {
                                callMicLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) { Text("📞", fontSize = 17.sp) }
                Spacer(Modifier.width(6.dp))
            }
            if (recording) {
                Text("🔴", fontSize = 18.sp, modifier = Modifier.padding(start = 8.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "Идёт запись… отпустите, чтобы отправить",
                    fontFamily = VpnkaFonts.manrope600, fontSize = 14.sp, color = VpnkaColors.TextMuted,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Box {
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape)
                            .background(VpnkaColors.CardSettings)
                            .clickable { showAttach = true },
                        contentAlignment = Alignment.Center,
                    ) { Text("📎", fontSize = 20.sp) }
                    DropdownMenu(expanded = showAttach, onDismissRequest = { showAttach = false }) {
                        DropdownMenuItem(text = { Text("📷 Фото") }, onClick = { showAttach = false; picker.launch("image/*") })
                        DropdownMenuItem(text = { Text("🎬 Видео") }, onClick = { showAttach = false; videoPicker.launch("video/*") })
                    }
                }
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) { MsgField("Сообщение", draft) { draft = it; Messenger.sendTyping(contact.id) } }
            }
            Spacer(Modifier.width(8.dp))
            if (draft.trim().isBlank()) {
                // Hold to record, release to send.
                Box(
                    modifier = Modifier.size(46.dp).clip(CircleShape)
                        .background(if (recording) VpnkaColors.Warning else VpnkaColors.Accent)
                        .pointerInput(micGranted) {
                            detectTapGestures(
                                onPress = {
                                    if (!micGranted) {
                                        micLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                        return@detectTapGestures
                                    }
                                    val started = MessengerVoice.startRecording(context)
                                    if (started) recording = true
                                    tryAwaitRelease()
                                    if (started) {
                                        recording = false
                                        val res = MessengerVoice.stopRecording()
                                        if (res != null) {
                                            val (bytes, dur) = res
                                            scope.launch(Dispatchers.IO) {
                                                sendPct = 0
                                                val ok = Messenger.sendVoice(contact.id, bytes, dur) { p -> sendPct = p }
                                                sendPct = if (ok) null else -1
                                                if (ok) onSent()
                                            }
                                        }
                                    }
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) { Text("🎤", fontSize = 20.sp, color = VpnkaColors.OnAccent) }
            } else {
                Box(
                    modifier = Modifier.size(46.dp).clip(CircleShape).background(VpnkaColors.Accent)
                        .clickable {
                            val t = draft.trim()
                            if (t.isNotBlank()) {
                                draft = ""
                                scope.launch {
                                    if (Messenger.send(contact.id, t)) onSent()
                                    else {
                                        // Не ушло — возвращаем текст в поле.
                                        // Раньше он исчезал молча: человек был
                                        // уверен, что отправил.
                                        draft = t
                                        android.widget.Toast.makeText(
                                            context, "Не отправилось — попробуйте ещё раз",
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) { Text("➤", fontSize = 20.sp, color = VpnkaColors.OnAccent) }
            }
        }
    }

    playVideo?.let { b64 ->
        VideoPlayDialog(b64) { playVideo = null }
    }
}

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@Composable
private fun VideoPlayDialog(base64: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val file = remember(base64) {
        java.io.File(context.cacheDir, "play_vid_${System.currentTimeMillis()}.mp4").apply {
            runCatching { writeBytes(android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)) }
        }
    }
    val player = remember {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(android.net.Uri.fromFile(file)))
            prepare(); playWhenReady = true
        }
    }
    DisposableEffect(Unit) { onDispose { player.release(); file.delete() } }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black).clickable { onClose() },
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { ctx ->
                    androidx.media3.ui.PlayerView(ctx).apply { this.player = player; useController = true }
                },
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            )
        }
    }
}

private fun videoDurationSec(path: String): Int = try {
    val r = android.media.MediaMetadataRetriever()
    r.setDataSource(path)
    val ms = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    r.release()
    (ms / 1000).toInt()
} catch (e: Exception) { 0 }

private fun fmtVoice(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)

/** Short, human-readable fingerprint of a contact's public key (first 8
 *  bytes of its SHA-256, hex). Two people compare it to be sure no one
 *  substituted the key — the safety-number idea, kept compact. */
private fun keyFingerprint(pubKey: String): String = try {
    val h = java.security.MessageDigest.getInstance("SHA-256").digest(pubKey.toByteArray())
    h.take(8).joinToString(" ") { "%02X".format(it) }
} catch (e: Exception) { "—" }

/** «Профиль» — your own identity and encryption key. A bottom tab, so no
 *  back arrow: the tab bar is the way out. */
@Composable
private fun ProfileTab(handle: String) {
    val myKey = remember { Messenger.myPublicKey() }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MsgAvatar(if (handle.isBlank()) "?" else handle, size = 96)
            Spacer(Modifier.height(12.dp))
            Text(if (handle.isBlank()) "…" else "@$handle", fontFamily = VpnkaFonts.nunito800, fontSize = 22.sp, color = VpnkaColors.TextStrong)
            Spacer(Modifier.height(2.dp))
            Text("Ваш постоянный ник в мессенджере", fontFamily = VpnkaFonts.manrope600, fontSize = 12.sp, color = VpnkaColors.TextMuted)
        }
        ProfileCard("🔒 Безопасность") {
            Text("Отпечаток вашего ключа шифрования", fontFamily = VpnkaFonts.manrope600, fontSize = 12.sp, color = VpnkaColors.TextMuted)
            Spacer(Modifier.height(4.dp))
            Text(keyFingerprint(myKey), fontFamily = VpnkaFonts.nunito800, fontSize = 16.sp, color = VpnkaColors.Accent)
            Spacer(Modifier.height(8.dp))
            Text(
                "Это ВАШ отпечаток. Продиктуйте его собеседнику — он сверит с тем, что видит у вас в вашем профиле. " +
                    "Переписка шифруется прямо на устройстве: сервер видит только зашифрованные пакеты, ни сообщений, ни ключей у него нет.",
                fontFamily = VpnkaFonts.manrope600, fontSize = 12.sp, color = VpnkaColors.TextMuted,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** «Настройки» — messenger switches, split out of the profile so each bottom
 *  tab holds one thing. */
@Composable
private fun SettingsTab() {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(10.dp))
        ProfileCard("⚙️ Уведомления и статусы") {
            MyProfileToggle("Уведомлять о новых сообщениях", "notify")
            MyProfileToggle("Отправлять «печатает…»", "typing")
            MyProfileToggle("Отправлять отметку о прочтении", "read")
        }
        ProfileCard("📞 Звонки") {
            val ctx = LocalContext.current
            var bg by remember { mutableStateOf(Messenger.setting(VpnkaLinkService.SETTING, true)) }
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Принимать звонки при свёрнутом приложении",
                    fontFamily = VpnkaFonts.manrope600, fontSize = 14.sp,
                    color = VpnkaColors.TextStrong, modifier = Modifier.weight(1f),
                )
                androidx.compose.material3.Switch(
                    checked = bg,
                    onCheckedChange = {
                        bg = it
                        Messenger.setSetting(VpnkaLinkService.SETTING, it)
                        if (it) VpnkaLinkService.start(ctx) else VpnkaLinkService.stop(ctx)
                    },
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Приложение держит связь с сервером и показывает уведомление «ВПНка на связи» — без него Android закрывает связь, и звонок не дойдёт. Выключите, если звонки нужны только при открытом мессенджере.",
                fontFamily = VpnkaFonts.manrope600, fontSize = 12.sp, color = VpnkaColors.TextMuted,
            )
        }
        ProfileCard("🖼 Медиа") {
            MyProfileToggle("Сжимать медиа до минимума", "media_min")
            Spacer(Modifier.height(6.dp))
            Text(
                "Фото и видео отправляются в компактном качестве, чтобы быстро доходили. Выключите — будет отправляться повыше (480p → 720p для видео).",
                fontFamily = VpnkaFonts.manrope600, fontSize = 12.sp, color = VpnkaColors.TextMuted,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** «Контакты» — people you already talk to, plus a people-only search so a new
 *  contact can be added without going through the chats tab. */
@Composable
private fun ContactsTab(
    contacts: List<Messenger.Contact>,
    onOpen: (Long) -> Unit,
    onStartChat: (Messenger.Found) -> Unit,
) {
    var q by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<Messenger.Found>>(emptyList()) }
    LaunchedEffect(q) {
        if (q.trim().length < 2) found = emptyList() else {
            delay(300)
            found = Messenger.searchUsers(q)
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            MsgField("Найти человека по нику", q) { q = it }
        }
        if (q.trim().length >= 2) {
            if (found.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("Никого не нашли", fontFamily = VpnkaFonts.manrope600, fontSize = 14.sp, color = VpnkaColors.TextMuted)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                    items(found, key = { it.id }) { r ->
                        ResultRow(glyph = null, title = "@${r.handle}", sub = null) { onStartChat(r); q = "" }
                    }
                }
            }
        } else if (contacts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Контактов пока нет. Найдите человека по нику из Telegram — без символа @.",
                    fontFamily = VpnkaFonts.manrope600, fontSize = 14.sp, color = VpnkaColors.TextMuted,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                items(contacts.sortedBy { it.name.lowercase() }, key = { it.id }) { c ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(13.dp)).background(VpnkaColors.CardServer).border(1.dp, VpnkaColors.Hairline, RoundedCornerShape(13.dp))
                            .clickable { onOpen(c.id) }.padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MsgAvatar(c.name, size = 44)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            c.name, fontFamily = VpnkaFonts.nunito800, fontSize = 16.sp,
                            color = VpnkaColors.TextStrong, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** Telegram-style bottom tabs. Sits above the SmartDesk host bar, so it stays
 *  compact — the host owns the system navigation inset. */
@Composable
private fun MessengerTabBar(current: MsgTab, onSelect: (MsgTab) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(VpnkaColors.BgOffCentre)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MsgTabItem("💬", "Чаты", current == MsgTab.CHATS, Modifier.weight(1f)) { onSelect(MsgTab.CHATS) }
        MsgTabItem("☎", "Звонки", current == MsgTab.CALLS, Modifier.weight(1f)) { onSelect(MsgTab.CALLS) }
        MsgTabItem("👤", "Контакты", current == MsgTab.CONTACTS, Modifier.weight(1f)) { onSelect(MsgTab.CONTACTS) }
        MsgTabItem("⚙️", "Настройки", current == MsgTab.SETTINGS, Modifier.weight(1f)) { onSelect(MsgTab.SETTINGS) }
        MsgTabItem("🪪", "Профиль", current == MsgTab.PROFILE, Modifier.weight(1f)) { onSelect(MsgTab.PROFILE) }
    }
}

/** One tab. The glyph is an emoji and keeps its own colours, so the selected
 *  state has to read from the label and the pill behind it. */
@Composable
private fun MsgTabItem(
    glyph: String,
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) VpnkaColors.Accent.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(glyph, fontSize = 17.sp, color = VpnkaColors.TextStrong)
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            fontFamily = VpnkaFonts.nunito800,
            fontSize = 11.sp,
            color = if (selected) VpnkaColors.Accent else VpnkaColors.TextMuted,
        )
    }
}

@Composable
private fun ProfileCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp)).background(VpnkaColors.CardServer).border(1.dp, VpnkaColors.Hairline, RoundedCornerShape(12.dp)).padding(16.dp),
    ) {
        Text(title, fontFamily = VpnkaFonts.nunito800, fontSize = 14.sp, color = VpnkaColors.TextStrong)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun MyProfileToggle(label: String, key: String) {
    var on by remember { mutableStateOf(Messenger.setting(key, true)) }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontFamily = VpnkaFonts.manrope600, fontSize = 14.sp,
            color = VpnkaColors.TextStrong, modifier = Modifier.weight(1f))
        androidx.compose.material3.Switch(checked = on, onCheckedChange = { on = it; Messenger.setSetting(key, it) })
    }
}

/** Contact profile: avatar, name, and the E2E key fingerprint. */
@Composable
private fun ContactProfileScreen(contact: Messenger.Contact, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("‹", fontSize = 24.sp, color = VpnkaColors.TextStrong,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onBack).padding(horizontal = 8.dp, vertical = 4.dp))
            Spacer(Modifier.width(6.dp))
            Text("Профиль", fontFamily = VpnkaFonts.nunito800, fontSize = 16.sp, color = VpnkaColors.TextStrong)
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MsgAvatar(contact.name, size = 96)
            Spacer(Modifier.height(14.dp))
            Text(contact.name, fontFamily = VpnkaFonts.nunito800, fontSize = 22.sp, color = VpnkaColors.TextStrong)
            Spacer(Modifier.height(24.dp))
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(VpnkaColors.CardServer)
                    .border(1.dp, VpnkaColors.Hairline, RoundedCornerShape(12.dp)).padding(16.dp),
            ) {
                Text("🔒 Ключ шифрования собеседника", fontFamily = VpnkaFonts.nunito800, fontSize = 14.sp, color = VpnkaColors.TextStrong)
                Spacer(Modifier.height(8.dp))
                Text(keyFingerprint(contact.pubKey), fontFamily = VpnkaFonts.nunito800, fontSize = 16.sp, color = VpnkaColors.Accent)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Это отпечаток ключа собеседника — у вас и у него он РАЗНЫЙ, так и должно быть. " +
                        "Чтобы проверить, что переписку никто не подменил, попросите собеседника открыть у себя «Мой профиль» " +
                        "и продиктовать свой отпечаток. Он должен совпасть с кодом выше.",
                    fontFamily = VpnkaFonts.manrope600, fontSize = 12.sp, color = VpnkaColors.TextMuted,
                )
            }
        }
    }
}

@Composable
private fun MsgAvatar(name: String, size: Int = 44) {
    val initials = name.trim().split(" ").filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { name.trim().dropWhile { it == '@' }.take(1).uppercase().ifBlank { "?" } }
    Box(
        modifier = Modifier.size(size.dp).clip(CircleShape).background(avatarBrush(name)),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, fontFamily = VpnkaFonts.nunito800, fontSize = (size * 0.36f).sp, color = Color.White)
    }
}

@Composable
private fun MsgField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, color = VpnkaColors.TextMuted) },
        singleLine = true,
        shape = RoundedCornerShape(13.dp),
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

/**
 * Full-screen call UI. Reads [CallManager] state directly and drives it back
 * (accept / decline / mute / speaker / hangup). Shown by the messenger root
 * whenever a call is not IDLE.
 */
@Composable
private fun CallScreen() {
    val context = LocalContext.current
    val phase = CallManager.phase
    val name = CallManager.peerName.ifBlank { "Собеседник" }

    // 24.08.2026: исходящий звонок разрешение спрашивал, а приём — нет. Кто ни
    // разу не звонил сам, принимал звонок, слышал собеседника, а его не
    // слышали — и понять это было невозможно, ошибки не показывалось.
    val acceptMicLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) CallManager.accept(context) else CallManager.decline() }

    // Live call duration once connected.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(phase, CallManager.connectedAt) {
        while (phase == CallManager.Phase.ACTIVE) { now = System.currentTimeMillis(); delay(1000) }
    }
    val status = when (phase) {
        CallManager.Phase.OUTGOING -> "Звоним…"
        CallManager.Phase.INCOMING -> "Входящий звонок"
        CallManager.Phase.ACTIVE ->
            if (CallManager.connectedAt > 0) fmtVoice(((now - CallManager.connectedAt) / 1000).toInt().coerceAtLeast(0)) else "Соединение…"
        CallManager.Phase.ENDED -> CallManager.endReason.ifBlank { "Звонок завершён" }
        CallManager.Phase.IDLE -> ""
    }

    // Экран звонка по макету: полотно, вверху крупный аватар с именем и
    // состоянием акцентом, внизу — ряд круглых кнопок. Никаких подписей под
    // кнопками: в макете их нет, и на экране, где всё решается одним касанием,
    // они только шумят. Исключение — входящий: «принять» и «отклонить»
    // отличаются не только цветом, и ошибиться тут дороже.
    Column(
        modifier = Modifier.fillMaxSize().background(VpnkaColors.BgOffMid)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.padding(top = 70.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MsgAvatar(name, size = 84)
            Spacer(Modifier.height(14.dp))
            Text(
                name, fontFamily = VpnkaFonts.nunito800, fontSize = 20.sp,
                color = VpnkaColors.TextStrong, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                status, fontFamily = VpnkaFonts.manrope700, fontSize = 12.sp,
                color = VpnkaColors.Accent,
            )
            Spacer(Modifier.height(10.dp))
            // Замок оставлен: разговор идёт через наш сервер вслепую, и это
            // единственное место, где об этом можно сказать человеку.
            Text(
                "🔒 сквозное шифрование", fontFamily = VpnkaFonts.manrope600, fontSize = 11.sp,
                color = VpnkaColors.TextFaint,
            )
        }

        Row(
            modifier = Modifier.padding(bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (phase) {
                CallManager.Phase.INCOMING -> {
                    CallCircle("✕", 64.dp, VpnkaColors.Warning, Color.White, "Отклонить") {
                        CallManager.decline()
                    }
                    CallCircle("📞", 64.dp, VpnkaColors.Green, Color.White, "Принять") {
                        if (ContextCompat.checkSelfPermission(
                                context, android.Manifest.permission.RECORD_AUDIO
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                            CallManager.accept(context)
                        } else {
                            acceptMicLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                        }
                    }
                }
                CallManager.Phase.ENDED -> Unit
                else -> {
                    CallCircle(
                        if (CallManager.muted) "🔇" else "🎙", 52.dp,
                        VpnkaColors.CardServer, VpnkaColors.TextStrong, null,
                    ) { CallManager.toggleMute() }
                    CallCircle("✕", 64.dp, VpnkaColors.Warning, Color.White, null) {
                        CallManager.hangup()
                    }
                    CallCircle(
                        if (CallManager.speaker) "🔊" else "📢", 52.dp,
                        VpnkaColors.CardServer, VpnkaColors.TextStrong, null,
                    ) { CallManager.toggleSpeaker() }
                }
            }
        }
    }
}

/** Круглая кнопка звонка. Подпись — только там, где промах дорого стоит. */
@Composable
private fun CallCircle(
    glyph: String,
    size: androidx.compose.ui.unit.Dp,
    bg: Color,
    fg: Color,
    label: String?,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(size).clip(CircleShape).background(bg).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Text(glyph, fontSize = 18.sp, color = fg) }
        if (label != null) {
            Spacer(Modifier.height(8.dp))
            Text(label, fontFamily = VpnkaFonts.manrope600, fontSize = 12.sp, color = VpnkaColors.TextMuted)
        }
    }
}


