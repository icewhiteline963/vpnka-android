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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    DeskApp("help", "Помощь", "🛡️", "Как устроена приватность SmartDesk"),
)

private val CATALOG_BY_ID = SMARTDESK_CATALOG.associateBy { it.id }
private val DEFAULT_INSTALLED = listOf("store", "messages", "help", "calendar", "contacts", "browser", "youtube")

// ---- «Android 17» look: gradient wallpapers + colourful squircle app tiles ----

/** Wallpaper ids offered in the pickers, in order. */
private val WALLPAPERS = listOf("warm", "aurora", "ocean", "night", "forest")

/** Full-screen wallpaper gradient. */
private fun wallpaperBrush(id: String): Brush = when (id) {
    "aurora" -> Brush.linearGradient(listOf(Color(0xFF6A11CB), Color(0xFF9D50BB), Color(0xFFF56C6C)))
    "ocean" -> Brush.linearGradient(listOf(Color(0xFF1A2980), Color(0xFF26D0CE)))
    "night" -> Brush.linearGradient(listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)))
    "forest" -> Brush.linearGradient(listOf(Color(0xFF11361F), Color(0xFF2E7D53), Color(0xFF0E2A1A)))
    else -> Brush.linearGradient(listOf(Color(0xFFFFDCA8), Color(0xFFFFB27A), Color(0xFFF5926E)))
}

/** One swatch colour for the picker chip. */
private fun wallpaperSwatch(id: String): Color = when (id) {
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
    "help" -> listOf(Color(0xFF818CF8), Color(0xFF4F46E5))
    "settings" -> listOf(Color(0xFF94A3B8), Color(0xFF475569))
    else -> listOf(Color(0xFFC4B5FD), Color(0xFF7C3AED))
}

/** Ids installed on the desktop; «store» is forced in so it can never vanish. */
private const val KEY_HELP_SEEDED = "vpnka_smartdesk_help_seeded"
private const val KEY_YOUTUBE_SEEDED = "vpnka_smartdesk_youtube_seeded"

fun installedIds(): List<String> {
    val stored = MmkvManager.decodeSettingsString(KEY_INSTALLED)
    var ids = if (stored == null) DEFAULT_INSTALLED
        else stored.split(",").filter { it.isNotBlank() && it in CATALOG_BY_ID }
    // Fresh install already has these via DEFAULT_INSTALLED — mark them seeded so
    // that if the user later removes one, the migration below never re-adds it.
    if (stored == null) {
        MmkvManager.encodeSettings(KEY_HELP_SEEDED, "1")
        MmkvManager.encodeSettings(KEY_YOUTUBE_SEEDED, "1")
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
) {
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
    }

    val order = remember(deskTick) { mutableStateListOf<Pair<DeskApp, Int>>().apply { addAll(loadOrder()) } }
    var contextFor by remember { mutableStateOf<DeskApp?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showShade by remember { mutableStateOf(false) }     // swipe top-left down
    var showControl by remember { mutableStateOf(false) }   // swipe top-right down
    var wallpaper by remember {
        mutableStateOf(MmkvManager.decodeSettingsString("vpnka_smartdesk_wallpaper") ?: "warm")
    }

    // System back steps WITHIN SmartDesk (overlay → open app → desktop) and only
    // leaves to the vpnka home from the bare desktop — one step, never a jump.
    BackHandler {
        when {
            showShade -> showShade = false
            showControl -> showControl = false
            showSettings -> showSettings = false
            openApp != null -> { openApp = null; deskTick++ }
            else -> onBack()
        }
    }

    // Desktop text colour adapts to the wallpaper so labels read on any of them.
    val ink = if (wallpaperLightInk(wallpaper)) Color.White else Color(0xFF4A3312)

    // Maximum-privacy self-wipe. Leaving SmartDesk (onDispose) or the app going
    // to background / screen-lock (ON_STOP) pushes any pending edits and then
    // erases the local copy. Between sessions the phone holds no SmartDesk data;
    // the next open re-pulls it from the encrypted server. Offline → nothing is
    // wiped (see SmartDeskSync.syncAndWipe), so no edits are lost.
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(ink.copy(alpha = 0.16f))
                    // Tap to toggle the VPN — offline → connect, online → disconnect.
                    .clickable { onToggleVpn() }
                    .padding(horizontal = 11.dp, vertical = 6.dp),
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (online) VpnkaColors.Green else VpnkaColors.Warning))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (online) "На связи" else "Подключить",
                    fontFamily = VpnkaFonts.manrope700, fontSize = 12.sp, color = ink,
                )
            }
        }

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
                                .shadow(12.dp, RoundedCornerShape(20.dp), clip = false)
                                .clip(RoundedCornerShape(20.dp))
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

        // Floating home dock — a translucent pill, launcher-style.
        Box(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp, top = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(ink.copy(alpha = 0.18f))
                    .clickable { onBack() }
                    .padding(horizontal = 24.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("⌂", fontSize = 18.sp, color = ink)
                Spacer(Modifier.width(8.dp))
                Text("На главный экран", fontFamily = VpnkaFonts.nunito800, fontSize = 14.sp, color = ink)
            }
        }
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
                wallpaper = wallpaper,
                onWallpaper = { choice ->
                    wallpaper = choice
                    MmkvManager.encodeSettings("vpnka_smartdesk_wallpaper", choice)
                },
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
                        .clip(RoundedCornerShape(14.dp))
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
            .clip(RoundedCornerShape(14.dp))
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
    wallpaper: String,
    onWallpaper: (String) -> Unit,
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
            // VPN status tile.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (online) VpnkaColors.Green.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.10f))
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
                            .clip(RoundedCornerShape(16.dp))
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
