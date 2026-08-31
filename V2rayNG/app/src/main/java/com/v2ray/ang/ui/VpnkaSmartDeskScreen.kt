package com.v2ray.ang.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.v2ray.ang.handler.BrowserHistory
import com.v2ray.ang.handler.Messenger
import com.v2ray.ang.handler.SmartDeskSync
import com.v2ray.ang.handler.SmartDeskStore
import androidx.activity.compose.BackHandler
import androidx.compose.ui.window.Popup
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.v2ray.ang.handler.YouTubeNowPlaying
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.v2ray.ang.handler.MmkvManager
import kotlin.math.roundToInt

/**
 * The SmartDesk home surface — a full-screen, Android-like desktop.
 *
 * Everything here runs on the device; the server is only a sync target (the
 * dot up top says whether it's reachable right now). Phase 1 ships the shell:
 * a grid of app icons the user can drag around, a long-press context menu per
 * icon, a long-press-on-empty desktop-settings sheet, and the three apps
 * (Calendar / Contacts / Mail) as placeholders. Their real screens and the
 * sync land in later phases.
 */

private const val KEY_LAYOUT = "vpnka_smartdesk_layout"
private const val KEY_INSTALLED = "vpnka_smartdesk_installed"
private const val COLUMNS = 4

data class DeskApp(
    val id: String,
    val label: String,
    val glyph: String,
    val description: String = "",
    val removable: Boolean = true,
)

/** Everything the vpnka store knows about. «store» is always present. */
val SMARTDESK_CATALOG = listOf(
    DeskApp("store", "vpnka store", "🛍️", "Устанавливайте приложения на рабочий стол", removable = false),
    DeskApp("messages", "VPNka мессенджер", "💬", "Зашифрованный мессенджер через наш сервер"),
    DeskApp("calendar", "Календарь", "📅", "Календарь с событиями и напоминаниями"),
    DeskApp("contacts", "Контакты", "👤", "Ваши контакты: звонки, почта, поиск"),
    DeskApp("browser", "Браузер", "🌐", "Веб-браузер — весь трафик через VPN"),
    DeskApp("youtube", "YouTube", "▶️", "YouTube без рекламы, через VPN"),
    DeskApp("notes", "Заметки", "📝", "Заметки с форматированием и списки покупок"),
    DeskApp("help", "Помощь", "🛡️", "Как устроена приватность VPNka облака"),
)

private val CATALOG_BY_ID = SMARTDESK_CATALOG.associateBy { it.id }
private val DEFAULT_INSTALLED = listOf("store", "messages", "help", "calendar", "contacts", "browser", "youtube", "notes")

// ---- «Android 17» look: gradient wallpapers + colourful squircle app tiles ----

/** Wallpaper ids offered in the pickers, in order. */
private val WALLPAPERS = listOf("flow", "warm", "aurora", "ocean", "night", "forest")

/** Full-screen wallpaper gradient. */
private fun wallpaperBrush(id: String): Brush = when (id) {
    // Полотно из макета: страница → полотно → панель. Тёплое тёмное, не серое.
    "flow" -> Brush.linearGradient(listOf(Color(0xFF100D09), Color(0xFF15110C), Color(0xFF1B160F)))
    "aurora" -> Brush.linearGradient(listOf(Color(0xFF6A11CB), Color(0xFF9D50BB), Color(0xFFF56C6C)))
    "ocean" -> Brush.linearGradient(listOf(Color(0xFF1A2980), Color(0xFF26D0CE)))
    "night" -> Brush.linearGradient(listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)))
    "forest" -> Brush.linearGradient(listOf(Color(0xFF11361F), Color(0xFF2E7D53), Color(0xFF0E2A1A)))
    else -> Brush.linearGradient(listOf(Color(0xFFFFDCA8), Color(0xFFFFB27A), Color(0xFFF5926E)))
}

/** One swatch colour for the picker chip. */
private fun wallpaperSwatch(id: String): Color = when (id) {
    "flow" -> Color(0xFF1B160F)
    "aurora" -> Color(0xFF9D50BB)
    "ocean" -> Color(0xFF1A94A8)
    "night" -> Color(0xFF213A44)
    "forest" -> Color(0xFF2E7D53)
    else -> Color(0xFFFFB27A)
}

/** Warm wallpaper is light → dark ink; the rest are dark → white ink. */
private fun wallpaperLightInk(id: String): Boolean = id != "warm"

/** Per-app tile gradient — Material-You-style colourful icons. */
fun appTint(id: String): List<Color> = when (id) {
    "store" -> listOf(Color(0xFFFFB03A), Color(0xFFE8850C))
    "messages" -> listOf(Color(0xFF34D399), Color(0xFF059669))
    "calendar" -> listOf(Color(0xFFFB7185), Color(0xFFE11D48))
    "contacts" -> listOf(Color(0xFF60A5FA), Color(0xFF2563EB))
    "browser" -> listOf(Color(0xFF22D3EE), Color(0xFF0891B2))
    "youtube" -> listOf(Color(0xFFFF6B6B), Color(0xFFCC0000))
    "notes" -> listOf(Color(0xFFFCD34D), Color(0xFFF59E0B))
    "help" -> listOf(Color(0xFF818CF8), Color(0xFF4F46E5))
    "settings" -> listOf(Color(0xFF94A3B8), Color(0xFF475569))
    else -> listOf(Color(0xFFC4B5FD), Color(0xFF7C3AED))
}

/** Ids installed on the desktop; «store» is forced in so it can never vanish. */
private const val KEY_HELP_SEEDED = "vpnka_smartdesk_help_seeded"
private const val KEY_YOUTUBE_SEEDED = "vpnka_smartdesk_youtube_seeded"
private const val KEY_NOTES_SEEDED = "vpnka_smartdesk_notes_seeded"

fun installedIds(): List<String> {
    val stored = MmkvManager.decodeSettingsString(KEY_INSTALLED)
    var ids = if (stored == null) DEFAULT_INSTALLED
        else stored.split(",").filter { it.isNotBlank() && it in CATALOG_BY_ID }
    // Fresh install already has these via DEFAULT_INSTALLED — mark them seeded so
    // that if the user later removes one, the migration below never re-adds it.
    if (stored == null) {
        MmkvManager.encodeSettings(KEY_HELP_SEEDED, "1")
        MmkvManager.encodeSettings(KEY_YOUTUBE_SEEDED, "1")
        MmkvManager.encodeSettings(KEY_NOTES_SEEDED, "1")
    }
    var seeded = false
    // One-time seeds: existing installs (a stored list without a newly-shipped
    // app) get it added once, so it appears on update without a manual store
    // install. If the user later removes it, the flag stays set, so we never
    // force it back.
    if (stored != null && MmkvManager.decodeSettingsString(KEY_HELP_SEEDED) == null) {
        if ("help" !in ids) ids = ids + "help"
        MmkvManager.encodeSettings(KEY_HELP_SEEDED, "1")
        seeded = true
    }
    if (stored != null && MmkvManager.decodeSettingsString(KEY_YOUTUBE_SEEDED) == null) {
        if ("youtube" !in ids) ids = ids + "youtube"
        MmkvManager.encodeSettings(KEY_YOUTUBE_SEEDED, "1")
        seeded = true
    }
    if (stored != null && MmkvManager.decodeSettingsString(KEY_NOTES_SEEDED) == null) {
        if ("notes" !in ids) ids = ids + "notes"
        MmkvManager.encodeSettings(KEY_NOTES_SEEDED, "1")
        seeded = true
    }
    if (seeded) {
        MmkvManager.encodeSettings(KEY_INSTALLED, (listOf("store") + ids).distinct().joinToString(","))
    }
    return (listOf("store") + ids).distinct()
}

fun setInstalled(ids: List<String>) {
    MmkvManager.encodeSettings(
        KEY_INSTALLED,
        (listOf("store") + ids).distinct().joinToString(","),
    )
}

private fun installedApps(): List<DeskApp> = installedIds().mapNotNull { CATALOG_BY_ID[it] }

/** Persist the cell order as "id:cell,id:cell". Only installed apps appear. */
private fun loadOrder(): MutableList<Pair<DeskApp, Int>> {
    val apps = installedApps()
    val stored = MmkvManager.decodeSettingsString(KEY_LAYOUT).orEmpty()
    val byId = apps.associateBy { it.id }
    val parsed = stored.split(",")
        .mapNotNull { chunk ->
            val parts = chunk.split(":")
            val app = byId[parts.getOrNull(0)] ?: return@mapNotNull null
            val cell = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
            app to cell
        }
    // Newly-installed apps (not in the stored layout) get the next free cell.
    val placed = parsed.map { it.first.id }.toSet()
    val result = parsed.toMutableList()
    var next = (parsed.maxOfOrNull { it.second } ?: -1) + 1
    apps.filter { it.id !in placed }.forEach { result.add(it to next++) }
    return result
}

private fun saveOrder(order: List<Pair<DeskApp, Int>>) {
    MmkvManager.encodeSettings(
        KEY_LAYOUT,
        order.joinToString(",") { "${it.first.id}:${it.second}" },
    )
}

@Composable
fun VpnkaSmartDeskScreen(
    online: Boolean,
    onBack: () -> Unit,
    onToggleVpn: () -> Unit = {},
    /** Что показывать в шапке: страна и задержка, как в макете. */
    serverName: String = "",
    serverDelay: String = "",
    setBackHandler: ((() -> Boolean)?) -> Unit = {},
) {
    val context = LocalContext.current
    // Which app is open, or null on the desktop itself.
    var openApp by remember { mutableStateOf<DeskApp?>(null) }
    // Bumped whenever we return to the desktop, so the icon grid re-reads the
    // installed set — the store (a child screen) may have installed/removed
    // apps while this composable stayed in composition.
    var deskTick by remember { mutableIntStateOf(0) }
    // The open app is rendered as an animated overlay over the desktop (below),
    // not an early return — so its open/close zoom can play. `lastApp` keeps the
    // app around for the closing animation after `openApp` goes null.
    var lastApp by remember { mutableStateOf<DeskApp?>(null) }
    openApp?.let { lastApp = it }

    // Opened by tapping a message notification: jump straight into the
    // messenger. The chat itself is consumed inside VpnkaMessengerApp.
    LaunchedEffect(Unit) {
        if (Messenger.peekPendingChat() != 0L) {
            CATALOG_BY_ID["messages"]?.let { openApp = it }
        }
        // A home-screen shortcut («Добавить на рабочий стол») deep-linked us to
        // a specific app — open it straight away.
        SmartDeskChrome.consumePendingApp()?.let { id -> CATALOG_BY_ID[id]?.let { openApp = it } }
    }

    val order = remember(deskTick) { mutableStateListOf<Pair<DeskApp, Int>>().apply { addAll(loadOrder()) } }
    var contextFor by remember { mutableStateOf<DeskApp?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showShade by remember { mutableStateOf(false) }     // swipe top-left down
    var showControl by remember { mutableStateOf(false) }   // swipe top-right down
    var wallpaper by remember {
        mutableStateOf(MmkvManager.decodeSettingsString("vpnka_smartdesk_wallpaper") ?: "flow")
    }

    // The activity intercepts the system back before Compose's BackHandler, so
    // a BackHandler here never fires. Instead we hand the activity an
    // internal-back callback it consults first: pop overlays / the open app and
    // report handled, or report «not handled» from the bare desktop so the
    // activity then closes SmartDesk. One step, never a jump.
    DisposableEffect(Unit) {
        setBackHandler {
            when {
                showShade -> { showShade = false; true }
                showControl -> { showControl = false; true }
                showSettings -> { showSettings = false; true }
                // Сначала спрашиваем само приложение: браузер вернётся на
                // предыдущую страницу, заметки закроют редактор с
                // сохранением, мессенджер — вернётся к списку чатов. И
                // только если внутри идти некуда — закрываем приложение.
                openApp != null -> {
                    if (SmartDeskBackStack.handle()) true
                    else { openApp = null; deskTick++; true }
                }
                else -> false
            }
        }
        onDispose { setBackHandler(null) }
    }

    // Desktop text colour adapts to the wallpaper so labels read on any of them.
    val ink = if (wallpaperLightInk(wallpaper)) Color.White else Color(0xFF4A3312)

    // Maximum-privacy self-wipe. Leaving SmartDesk (onDispose) or the app going
    // to background / screen-lock (ON_STOP) pushes any pending edits and then
    // erases the local copy. Between sessions the phone holds no SmartDesk data;
    // the next open re-pulls it from the encrypted server. Offline → nothing is
    // wiped (see SmartDeskSync.syncAndWipe), so no edits are lost.
    // Пока открыт рабочий стол — палитра «Поток» из макетов. Снимается на
    // выходе: главный экран VPNka остаётся в своей, светлой.
    DisposableEffect(Unit) {
        VpnkaColors.flow = true
        onDispose { VpnkaColors.flow = false }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) SmartDeskSync.scheduleSyncAndWipe()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            SmartDeskSync.scheduleSyncAndWipe()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(wallpaperBrush(wallpaper))) {
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        // Server-status pill only — the clock and date were removed. It sits a
        // bit lower (top padding), and the top edge stays clear for the
        // shade/control swipes; exit is the bottom dock.
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 22.dp, end = 18.dp, top = 46.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            SmartDeskCloudButton(online = online, ink = ink)
            Spacer(Modifier.width(8.dp))
            // Шапка — ИНДИКАТОР, а не переключатель.
            //
            // Раньше одно касание этой пилюли отключало ВПН всему телефону,
            // причём подписана она была состоянием («На связи»), а не
            // действием. Человек трогал её, чтобы посмотреть статус, и рвал
            // соединение — а SmartDesk без ВПН не работает вовсе.
            // Переключатель теперь в центре управления, где ему и место.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(ink.copy(alpha = 0.16f))
                    .padding(horizontal = 11.dp, vertical = 6.dp),
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (online) VpnkaColors.Green else VpnkaColors.Warning))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = when {
                        !online -> "Выключен"
                        serverName.isBlank() -> "На связи"
                        serverDelay.isBlank() -> serverName
                        else -> "$serverName · $serverDelay"
                    },
                    fontFamily = VpnkaFonts.manrope700, fontSize = 12.sp, color = ink,
                    maxLines = 1,
                )
            }
        }

        // Общий поиск — «везде в приложении».
        //
        // Подпись супер-приложения из макета: человек не должен помнить, в
        // каком приложении лежит нужное. Ищем только по локальному и НЕ
        // ходим в сеть — поиск по чужим видео стоит трафика через наши ноды
        // и остаётся отдельным действием.
        var deskQuery by remember { mutableStateOf("") }
        val deskHits = remember(deskQuery) { SmartDeskSearch.search(deskQuery) }
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
            OutlinedTextField(
                value = deskQuery,
                onValueChange = { deskQuery = it },
                singleLine = true,
                placeholder = {
                    Text("Заметки, контакты, чаты, файлы", color = VpnkaColors.TextMuted, fontSize = 13.sp)
                },
                leadingIcon = { Text("⌕", fontSize = 15.sp, color = VpnkaColors.TextMuted) },
                trailingIcon = {
                    if (deskQuery.isNotEmpty()) {
                        Text(
                            "✕", fontSize = 14.sp, color = VpnkaColors.TextMuted,
                            modifier = Modifier.clip(CircleShape)
                                .clickable { deskQuery = "" }.padding(8.dp),
                        )
                    }
                },
                textStyle = androidx.compose.material3.LocalTextStyle.current
                    .copy(color = VpnkaColors.TextStrong, fontSize = 14.sp),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = VpnkaColors.TextStrong,
                    unfocusedTextColor = VpnkaColors.TextStrong,
                    cursorColor = VpnkaColors.Accent,
                    focusedBorderColor = VpnkaColors.Accent,
                    // В макете строка поиска обведена акцентом и в покое — она
                    // тут главный вход, а не рядовое поле.
                    unfocusedBorderColor = VpnkaColors.Accent.copy(alpha = 0.45f),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            if (deskQuery.trim().length >= 2) {
                if (deskHits.isEmpty()) {
                    Text(
                        "Ничего не нашлось. Поиск идёт по тому, что уже на устройстве.",
                        fontFamily = VpnkaFonts.manrope600, fontSize = 12.sp,
                        color = VpnkaColors.TextMuted,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                        items(deskHits) { h ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        deskQuery = ""
                                        // Идентификаторы — из каталога стола,
                                        // а не выдуманные: мессенджер там
                                        // зовётся «messages», и промах именем
                                        // молча ничего бы не открыл.
                                        val id = when (h.target) {
                                            SmartDeskSearch.Target.NOTES -> "notes"
                                            SmartDeskSearch.Target.CONTACTS -> "contacts"
                                            SmartDeskSearch.Target.CALENDAR -> "calendar"
                                            SmartDeskSearch.Target.MESSENGER -> "messages"
                                            else -> "youtube"
                                        }
                                        openApp = SMARTDESK_CATALOG.firstOrNull { it.id == id }
                                    }
                                    .padding(horizontal = 6.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(h.icon, fontSize = 15.sp, color = VpnkaColors.Accent)
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        h.title, fontFamily = VpnkaFonts.nunito800, fontSize = 13.sp,
                                        color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                    if (h.subtitle.isNotBlank()) {
                                        Text(
                                            h.subtitle, fontFamily = VpnkaFonts.manrope600,
                                            fontSize = 11.sp, color = ink.copy(alpha = 0.6f),
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                Text(
                                    h.tag, fontFamily = VpnkaFonts.manrope600, fontSize = 10.sp,
                                    color = ink.copy(alpha = 0.45f),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Две карточки состояния и метка полки — как в макете.
        //
        // Карточка ВПН ОТКРЫВАЕТ центр управления, а не переключает туннель:
        // мы уже наступали на это шапкой-пилюлей, где касание «посмотреть
        // статус» рвало соединение всему телефону.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(VpnkaColors.CardServer)
                    .border(1.dp, VpnkaColors.Hairline, RoundedCornerShape(14.dp))
                    .clickable { showControl = true }
                    .padding(13.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "ВПН", fontFamily = VpnkaFonts.nunito800, fontSize = 12.sp,
                        color = VpnkaColors.TextStrong, modifier = Modifier.weight(1f),
                    )
                    Box(
                        Modifier.size(8.dp).clip(CircleShape)
                            .background(if (online) VpnkaColors.Green else VpnkaColors.Warning),
                    )
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    when {
                        !online -> "Выключен"
                        serverName.isBlank() -> "На связи"
                        serverDelay.isBlank() -> serverName
                        else -> "$serverName · $serverDelay"
                    },
                    fontFamily = VpnkaFonts.manrope600, fontSize = 10.sp,
                    color = VpnkaColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(
                modifier = Modifier.weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(VpnkaColors.CardSpeed)
                    .border(1.dp, VpnkaColors.Hairline, RoundedCornerShape(14.dp))
                    .padding(13.dp),
            ) {
                Text(
                    "Заблокировано", fontFamily = VpnkaFonts.nunito800, fontSize = 12.sp,
                    color = VpnkaColors.TextStrong, maxLines = 1,
                )
                Spacer(Modifier.height(7.dp))
                // Число настоящее: счётчик ведёт сам блокировщик браузера.
                Text(
                    AdBlocker.blocked.toString(),
                    fontFamily = VpnkaFonts.nunito900, fontSize = 17.sp, color = VpnkaColors.Accent,
                )
            }
        }

        Text(
            "БЫСТРЫЙ ДОСТУП",
            fontFamily = VpnkaFonts.nunito800, fontSize = 10.sp, letterSpacing = 1.0.sp,
            color = ink.copy(alpha = 0.45f),
            modifier = Modifier.padding(start = 20.dp, bottom = 2.dp),
        )

        // The desktop grid. Long-press empty → settings. A downward swipe
        // ANYWHERE on the grid opens the shade (left half) or control centre
        // (right half) — it starts mid-screen, not at the top edge, so it
        // doesn't fight Android's own status-bar pull-down.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .padding(top = 14.dp, start = 12.dp, end = 12.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = { showSettings = true })
                }
                .pointerInput(Unit) {
                    var startX = 0f
                    var dy = 0f
                    detectVerticalDragGestures(
                        onDragStart = { off -> startX = off.x; dy = 0f },
                        onVerticalDrag = { _, delta ->
                            dy += delta
                            if (dy > 60f) {
                                if (startX < size.width / 2f) showShade = true
                                else showControl = true
                            }
                        },
                    )
                },
        ) {
            val density = LocalDensity.current
            // Non-square cells: width keeps all 4 columns on-screen, height is
            // taller so each row has more room (fits one row fewer — fine).
            val cellW = 88.dp
            val cellH = 106.dp
            val cellWPx = with(density) { cellW.toPx() }
            val cellHPx = with(density) { cellH.toPx() }

            order.forEach { (app, cell) ->
                key(app.id) {
                var drag by remember { mutableStateOf(Offset.Zero) }
                var dragging by remember { mutableStateOf(false) }
                val col = cell % COLUMNS
                val row = cell / COLUMNS
                val baseX = col * cellWPx
                val baseY = row * cellHPx

                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (baseX + drag.x).roundToInt(),
                                (baseY + drag.y).roundToInt(),
                            )
                        }
                        .zIndex(if (dragging) 1f else 0f)
                        .size(cellW, cellH)
                        .pointerInput(app.id) {
                            detectTapGestures(
                                onTap = { openApp = app },
                                onLongPress = { contextFor = app },
                            )
                        }
                        .pointerInput(app.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { dragging = true },
                                onDrag = { change, delta ->
                                    change.consume()
                                    drag += delta
                                },
                                onDragEnd = {
                                    // Snap to the nearest cell; if occupied, swap.
                                    val newCol = ((baseX + drag.x) / cellWPx).roundToInt()
                                        .coerceIn(0, COLUMNS - 1)
                                    val newRow = ((baseY + drag.y) / cellHPx).roundToInt()
                                        .coerceAtLeast(0)
                                    val target = newRow * COLUMNS + newCol
                                    val idx = order.indexOfFirst { it.first.id == app.id }
                                    val occupied = order.indexOfFirst { it.second == target }
                                    if (occupied >= 0 && occupied != idx) {
                                        order[occupied] = order[occupied].first to cell
                                    }
                                    order[idx] = app to target
                                    saveOrder(order)
                                    drag = Offset.Zero
                                    dragging = false
                                },
                                onDragCancel = { drag = Offset.Zero; dragging = false },
                            )
                        },
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(62.dp)
                                .shadow(12.dp, RoundedCornerShape(14.dp), clip = false)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Brush.linearGradient(appTint(app.id))),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = app.glyph, fontSize = 30.sp)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = app.label,
                            fontFamily = VpnkaFonts.manrope600,
                            fontSize = 12.sp,
                            color = ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            lineHeight = 14.sp,
                        )
                    }

                    DropdownMenu(
                        expanded = contextFor?.id == app.id,
                        onDismissRequest = { contextFor = null },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Открыть") },
                            onClick = { contextFor = null; openApp = app },
                        )
                        DropdownMenuItem(
                            text = { Text("Добавить на рабочий стол") },
                            onClick = { contextFor = null; pinAppToHome(context, app) },
                        )
                        DropdownMenuItem(
                            text = { Text("Настройки рабочего стола") },
                            onClick = { contextFor = null; showSettings = true },
                        )
                    }
                }
                }
            }

            if (showSettings) {
                DesktopSettingsSheet(
                    current = wallpaper,
                    onPick = { choice ->
                        wallpaper = choice
                        MmkvManager.encodeSettings("vpnka_smartdesk_wallpaper", choice)
                    },
                    onDismiss = { showSettings = false },
                )
            }
        }

        // «Продолжить» — последние страницы браузера, как в макете.
        //
        // Берём именно журнал браузера: это единственная вещь, которую человек
        // бросает на полпути и ищет потом. Инкогнито сюда не попадает — его
        // страницы в журнал не пишутся вовсе.
        // Сколько строк показать, зависит от высоты экрана: карточки, метка и
        // «Продолжить» откусывают у сетки значков, и на невысоком телефоне
        // три строки оставили бы от неё один ряд.
        val screenH = LocalConfiguration.current.screenHeightDp
        val recentMax = when {
            screenH >= 760 -> 3
            screenH >= 660 -> 2
            else -> 0
        }
        val recent = remember(deskTick, recentMax) {
            if (recentMax == 0) emptyList() else BrowserHistory.all().take(recentMax)
        }
        if (recent.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 18.dp, bottom = 6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    "ПРОДОЛЖИТЬ",
                    fontFamily = VpnkaFonts.nunito800, fontSize = 10.sp, letterSpacing = 1.0.sp,
                    color = ink.copy(alpha = 0.45f), modifier = Modifier.weight(1f),
                )
                Text(
                    "Все", fontFamily = VpnkaFonts.nunito800, fontSize = 11.sp,
                    color = VpnkaColors.Accent,
                    modifier = Modifier.clip(RoundedCornerShape(9.dp))
                        .clickable { CATALOG_BY_ID["browser"]?.let { openApp = it } }
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                recent.forEach { e ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(VpnkaColors.CardSpeed)
                            .border(1.dp, VpnkaColors.Hairline, RoundedCornerShape(12.dp))
                            .clickable {
                                SmartDeskChrome.pendingUrl = e.url
                                CATALOG_BY_ID["browser"]?.let { openApp = it }
                            }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val host = remember(e.url) {
                            runCatching { android.net.Uri.parse(e.url).host.orEmpty() }
                                .getOrDefault("").removePrefix("www.")
                        }
                        Box(
                            modifier = Modifier.size(30.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(VpnkaColors.AccentLight, VpnkaColors.Accent),
                                    ),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                host.take(1).uppercase().ifBlank { "•" },
                                fontFamily = VpnkaFonts.nunito800, fontSize = 13.sp,
                                color = VpnkaColors.OnAccent,
                            )
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                e.title.ifBlank { host.ifBlank { e.url } },
                                fontFamily = VpnkaFonts.nunito800, fontSize = 12.sp,
                                color = VpnkaColors.TextStrong,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                host, fontFamily = VpnkaFonts.manrope600, fontSize = 10.sp,
                                color = VpnkaColors.TextFaint,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        // Место под нижнюю панель — она рисуется поверх, у самого низа окна.
        Spacer(Modifier.height(BAR_HEIGHT))
    }

        AnimatedVisibility(
            visible = showShade,
            enter = fadeIn(tween(160)) + slideInVertically(tween(220)) { -it / 5 },
            exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { -it / 5 },
        ) {
            NotificationShade(online = online, onDismiss = { showShade = false })
        }
        AnimatedVisibility(
            visible = showControl,
            enter = fadeIn(tween(160)) + slideInVertically(tween(220)) { -it / 5 },
            exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { -it / 5 },
        ) {
            ControlCentre(
                online = online,
                onToggleVpn = onToggleVpn,
                wallpaper = wallpaper,
                onWallpaper = { choice ->
                    wallpaper = choice
                    MmkvManager.encodeSettings("vpnka_smartdesk_wallpaper", choice)
                },
                onExit = { showControl = false; onBack() },
                onDesktopSettings = { showControl = false; showSettings = true },
                onDismiss = { showControl = false },
            )
        }
        // App open/close over the desktop — launcher-style zoom + fade.
        AnimatedVisibility(
            visible = openApp != null,
            enter = scaleIn(tween(220), initialScale = 0.85f) + fadeIn(tween(180)),
            exit = scaleOut(tween(200), targetScale = 0.90f) + fadeOut(tween(160)),
        ) {
            lastApp?.let { app ->
              // Приложение рисуется НАД панелью, поэтому оставляем ей место:
              // иначе нижняя строка приложения уходила бы под панель.
              Box(modifier = Modifier.padding(bottom = if (SmartDeskChrome.barHidden) 0.dp else BAR_HEIGHT)) {
                VpnkaSmartDeskAppScreen(
                    appId = app.id,
                    appLabel = app.label,
                    appGlyph = app.glyph,
                    online = online,
                    onBack = { openApp = null; deskTick++ },
                    onExit = onBack,
                )
              }
            }
        }
        // Нижняя панель супер-приложения: одно касание вместо «назад → значок».
        // Прячется на вложенных экранах (чат, канал) — там своя шапка и свои
        // жесты, вторая панель внизу только мешает.
        if (!SmartDeskChrome.barHidden) {
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            ) {
                // Мини-плеер: фоновый звук продолжает играть, когда «Видео»
                // закрыто, и до сих пор остановить его можно было, только
                // вернувшись в приложение. Строка та же, что внутри «Видео», —
                // с настоящими паузой и стопом через MediaController.
                if (YouTubeNowPlaying.current != null && openApp?.id != "youtube") {
                    Box(modifier = Modifier.padding(horizontal = 10.dp)) {
                        NowPlayingBar(onOpen = { CATALOG_BY_ID["youtube"]?.let { a -> openApp = a } })
                    }
                }
                // Подсказка с действием — над панелью, чтобы кнопка «Открыть»
                // не спорила с ней за нижний край.
                SmartDeskToast.text?.let { msg ->
                    LaunchedEffect(SmartDeskToast.seq) {
                        kotlinx.coroutines.delay(5000)
                        SmartDeskToast.dismiss()
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(VpnkaColors.BgOffCentre)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            msg, fontFamily = VpnkaFonts.manrope600, fontSize = 13.sp,
                            color = VpnkaColors.TextStrong, maxLines = 2,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                        SmartDeskToast.actionLabel?.let { label ->
                            Text(
                                label, fontFamily = VpnkaFonts.nunito800, fontSize = 13.sp,
                                color = VpnkaColors.Accent,
                                modifier = Modifier.clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        if (SmartDeskToast.action == "downloads") {
                                            SmartDeskChrome.pendingYtTab = 2
                                            CATALOG_BY_ID["youtube"]?.let { a -> openApp = a }
                                        }
                                        SmartDeskToast.dismiss()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
                SmartDeskTabBar(
                    current = openApp?.id,
                    onDesk = { openApp = null; deskTick++ },
                    onApp = { id, ytTab ->
                        SmartDeskChrome.pendingYtTab = ytTab
                        CATALOG_BY_ID[id]?.let { openApp = it }
                    },
                    onExit = onBack,
                )
            }
        }
        // Dark strip behind the Android status bar so its white icons (clock,
        // battery, signal) stay visible over the light SmartDesk wallpaper —
        // drawn topmost so it also covers open apps, the shade and control
        // centre.
        SmartDeskStatusScrim()
    }
}

/**
 * Cloud/sync status as a tap-for-tooltip button. When everything is uploaded it
 * reassures the user that nothing is kept on the device — the local copy is
 * wiped after each sync and lives only in the encrypted cloud. Tapping also
 * forces a sync if anything is still pending.
 */
/** Высота нижней панели — под неё отводится место и на столе, и в приложении. */
private val BAR_HEIGHT = 58.dp

/**
 * Нижняя панель супер-приложения по макету: Стол · Видео · Чаты · Браузер ·
 * Загрузки, и отдельно выход на главный экран VPNka.
 *
 * «Загрузки» ведут в то же приложение «Видео», сразу на нужную вкладку —
 * отдельного приложения загрузок у нас нет, и заводить его ради одной кнопки
 * незачем.
 */
@Composable
private fun SmartDeskTabBar(
    current: String?,
    onDesk: () -> Unit,
    onApp: (String, Int?) -> Unit,
    onExit: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().background(VpnkaColors.BgOffCentre)) {
        // Волосяная черта сверху — в макете панель отделена от содержимого
        // именно ею, а не тенью.
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(VpnkaColors.Hairline))
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // РОВНО пять пунктов, как в эталоне. Шестым висел «Выход», из-за него
        // подписи жались и панель читалась как свалка. Выход остался у
        // системной кнопки «назад» с рабочего стола и в центре управления.
        BarItem("⌂", "Стол", current == null, Modifier.weight(1f)) { onDesk() }
        BarItem("▶", "Видео", current == "youtube", Modifier.weight(1f)) { onApp("youtube", null) }
        BarItem("✎", "Чаты", current == "messages", Modifier.weight(1f)) { onApp("messages", null) }
        BarItem("◍", "Браузер", current == "browser", Modifier.weight(1f)) { onApp("browser", null) }
        BarItem("↓", "Загрузки", false, Modifier.weight(1f)) { onApp("youtube", 2) }
    }
    }
}

@Composable
private fun BarItem(
    glyph: String,
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    // Активный пункт — оранжевым ТЕКСТОМ, без заливки. Плашка другого оттенка
    // выбивалась из панели и читалась как ошибка вёрстки.
    val tint = if (selected) VpnkaColors.AccentLight else VpnkaColors.TextFaint
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(glyph, fontSize = 16.sp, color = tint)
        Spacer(Modifier.height(5.dp))
        Text(
            label,
            fontFamily = if (selected) VpnkaFonts.nunito800 else VpnkaFonts.manrope600,
            fontSize = 10.sp,
            maxLines = 1,
            color = tint,
        )
    }
}

@Composable
private fun SmartDeskCloudButton(online: Boolean, ink: Color) {
    val scope = rememberCoroutineScope()
    var syncing by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var showTip by remember { mutableStateOf(false) }
    val pending = remember(tick, syncing, showTip) { SmartDeskStore.pending().size }
    val glyph = when {
        syncing -> "↻"
        pending == 0 && online -> "☁"
        else -> "↑"
    }
    Box {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(ink.copy(alpha = 0.16f))
                .clickable {
                    showTip = true
                    if (online && !syncing && pending > 0) {
                        syncing = true
                        scope.launch { SmartDeskSync.sync(); syncing = false; tick++ }
                    }
                }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) { Text(glyph, fontSize = 13.sp, color = ink) }

        if (showTip) {
            Popup(
                alignment = Alignment.TopEnd,
                onDismissRequest = { showTip = false },
                offset = IntOffset(0, 92),
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 250.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(VpnkaColors.BgOffCentre)
                        .padding(14.dp),
                ) {
                    val title = when {
                        syncing -> "Синхронизация…"
                        !online -> "Нет связи"
                        pending == 0 -> "☁ Данные в облаке"
                        else -> "↑ Ожидает отправки: $pending"
                    }
                    Text(title, fontFamily = VpnkaFonts.nunito800, fontSize = 14.sp, color = VpnkaColors.TextStrong)
                    Spacer(Modifier.height(5.dp))
                    val sub = when {
                        syncing -> "Отправляем зашифрованные данные на сервер."
                        !online -> "Данные сохранятся локально и уйдут в облако при подключении."
                        pending == 0 -> "🔒 На устройстве данные не хранятся — после синхронизации локальная копия стёрта, всё лежит только в зашифрованном облаке."
                        else -> "Нажмите, чтобы отправить в облако. После синхронизации локальная копия будет стёрта."
                    }
                    Text(sub, fontFamily = VpnkaFonts.manrope600, fontSize = 12.sp,
                        color = VpnkaColors.TextMuted, lineHeight = 17.sp)
                }
            }
        }
    }
}

@Composable
private fun NotificationShade(online: Boolean, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                .background(VpnkaColors.BgOffCentre)
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(16.dp),
        ) {
            Text("Уведомления", fontFamily = VpnkaFonts.nunito800, fontSize = 17.sp, color = VpnkaColors.TextStrong)
            Spacer(Modifier.height(12.dp))
            ShadeRow("🔐", "VPNka", if (online) "Защита включена, стол на связи" else "Нет связи — работа сохранится локально")
            ShadeRow("📅", "Календарь", "Новых напоминаний нет")
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Нажмите вне шторки, чтобы закрыть",
                fontFamily = VpnkaFonts.manrope600,
                fontSize = 12.sp,
                color = VpnkaColors.TextFaint,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun ShadeRow(glyph: String, title: String, body: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = glyph, fontSize = 22.sp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(text = title, fontFamily = VpnkaFonts.nunito800, fontSize = 14.sp, color = VpnkaColors.TextStrong)
            Text(text = body, fontFamily = VpnkaFonts.manrope600, fontSize = 12.sp, color = VpnkaColors.TextMuted)
        }
    }
}

@Composable
private fun ControlCentre(
    online: Boolean,
    onToggleVpn: () -> Unit,
    wallpaper: String,
    onWallpaper: (String) -> Unit,
    onExit: () -> Unit,
    onDesktopSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxWidth(0.74f)
                .clip(RoundedCornerShape(bottomStart = 28.dp))
                .background(VpnkaColors.BgOffCentre)
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(16.dp),
        ) {
            Text("Центр управления", fontFamily = VpnkaFonts.nunito800, fontSize = 16.sp, color = VpnkaColors.TextStrong)
            Spacer(Modifier.height(12.dp))
            // Плитка ВПН — теперь ДЕЙСТВУЮЩАЯ.
            //
            // Она выглядела переключателем (зелёная подсветка, индикатор), но
            // нажатие не делало ничего: настоящее переключение висело на
            // пилюле в шапке, подписанной состоянием. Поменяли местами —
            // действие туда, где оно выглядит действием.
            //
            // Отключение спрашивает подтверждение: SmartDesk без ВПН не
            // работает, и рвать соединение случайным касанием нельзя.
            var askDisconnect by remember { mutableStateOf(false) }
            if (askDisconnect) {
                AlertDialog(
                    onDismissRequest = { askDisconnect = false },
                    title = { Text("Отключить VPN?", fontFamily = VpnkaFonts.nunito800, color = VpnkaColors.TextStrong) },
                    text = {
                        Text(
                            "Приложения рабочего стола ходят в сеть только через VPN — без него они перестанут работать.",
                            fontFamily = VpnkaFonts.manrope600, color = VpnkaColors.TextMuted,
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { askDisconnect = false; onToggleVpn() }) { Text("Отключить") }
                    },
                    dismissButton = { TextButton(onClick = { askDisconnect = false }) { Text("Отмена") } },
                    containerColor = VpnkaColors.BgOffCentre,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (online) VpnkaColors.Green.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.10f))
                    .clickable { if (online) askDisconnect = true else onToggleVpn() }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(if (online) VpnkaColors.Green else VpnkaColors.Warning))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (online) "VPN подключён" else "VPN отключён",
                    fontFamily = VpnkaFonts.nunito800,
                    fontSize = 14.sp,
                    color = VpnkaColors.TextStrong,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text("Обои", fontFamily = VpnkaFonts.manrope600, fontSize = 13.sp, color = VpnkaColors.TextMuted)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WALLPAPERS.forEach { id ->
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(wallpaperBrush(id))
                            .pointerInput(id) { detectTapGestures { onWallpaper(id) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (id == wallpaper) Text("✓", fontSize = 18.sp, color = Color.White)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "⚙  Настройки рабочего стола",
                fontFamily = VpnkaFonts.nunito800,
                fontSize = 14.sp,
                color = VpnkaColors.TextStrong,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .pointerInput(Unit) { detectTapGestures { onDesktopSettings() } }
                    .padding(vertical = 6.dp),
            )
            // Выход переехал сюда из нижней панели: в эталоне у неё ровно пять
            // пунктов, а шестой ужимал подписи. С самого стола выход по-прежнему
            // делает системная кнопка «назад».
            Text(
                text = "↩  На главный экран VPNka",
                fontFamily = VpnkaFonts.nunito800,
                fontSize = 14.sp,
                color = VpnkaColors.TextStrong,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .pointerInput(Unit) { detectTapGestures { onExit() } }
                    .padding(vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun DesktopSettingsSheet(
    current: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Simple in-place panel (not a modal sheet) to keep Phase 1 dependency-light.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(VpnkaColors.BgOffCentre)
                .pointerInput(Unit) { detectTapGestures { } }
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 14.dp)
                    .width(40.dp).height(4.dp)
                    .clip(CircleShape)
                    .background(VpnkaColors.TextFaint.copy(alpha = 0.5f)),
            )
            Text(
                text = "Настройки рабочего стола",
                fontFamily = VpnkaFonts.nunito800,
                fontSize = 17.sp,
                color = VpnkaColors.TextStrong,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Обои",
                fontFamily = VpnkaFonts.manrope600,
                fontSize = 13.sp,
                color = VpnkaColors.TextMuted,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WALLPAPERS.forEach { id ->
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(wallpaperBrush(id))
                            .pointerInput(id) { detectTapGestures { onPick(id) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (id == current) {
                            Text("✓", fontSize = 20.sp, color = Color.White)
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text("VPNka мессенджер", fontFamily = VpnkaFonts.manrope600, fontSize = 13.sp, color = VpnkaColors.TextMuted)
            Spacer(Modifier.height(8.dp))
            Text(
                "Ваш ник: " + Messenger.myHandle().let { if (it.isNotEmpty()) "@$it" else "—" },
                fontFamily = VpnkaFonts.manrope600, fontSize = 14.sp, color = VpnkaColors.TextStrong,
            )
            Spacer(Modifier.height(8.dp))
            MsgrToggle("Уведомлять о новых сообщениях", "notify")
            MsgrToggle("Отправлять «печатает…»", "typing")
            MsgrToggle("Отправлять отметку о прочтении", "read")
            Spacer(Modifier.height(6.dp))
            var cleared by remember { mutableStateOf(false) }
            Text(
                if (cleared) "История очищена" else "Очистить историю переписки на устройстве",
                fontFamily = VpnkaFonts.nunito800, fontSize = 14.sp,
                color = if (cleared) VpnkaColors.TextMuted else Color(0xFFD32F2F),
                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = !cleared) { Messenger.clearHistory(); cleared = true }
                    .padding(vertical = 6.dp),
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun MsgrToggle(label: String, key: String) {
    var on by remember { mutableStateOf(Messenger.setting(key, true)) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontFamily = VpnkaFonts.manrope600, fontSize = 14.sp,
            color = VpnkaColors.TextStrong, modifier = Modifier.weight(1f))
        Switch(checked = on, onCheckedChange = { on = it; Messenger.setSetting(key, it) })
    }
}

/**
 * Pin a SmartDesk app to the Android home screen. The shortcut deep-links into
 * MainActivity with EXTRA_OPEN = "desk:<id>", which opens SmartDesk straight on
 * that app (e.g. YouTube). Icon = the app's emoji glyph on the brand tile.
 */
private fun pinAppToHome(context: Context, app: DeskApp) {
    if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
        Toast.makeText(context, "Лаунчер не поддерживает ярлыки на столе", Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_DESK_PREFIX + app.id)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
    val info = ShortcutInfoCompat.Builder(context, "desk_${app.id}")
        .setShortLabel(app.label)
        .setIcon(IconCompat.createWithBitmap(deskGlyphBitmap(app.glyph)))
        .setIntent(intent)
        .build()
    ShortcutManagerCompat.requestPinShortcut(context, info, null)
}

private fun deskGlyphBitmap(glyph: String): Bitmap {
    val size = 192
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8850C.toInt() }
    c.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), 42f, 42f, bg)
    val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 108f; textAlign = Paint.Align.CENTER }
    val baseline = size / 2f - (tp.descent() + tp.ascent()) / 2f
    c.drawText(glyph, size / 2f, baseline, tp)
    return bmp
}
