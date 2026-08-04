package com.v2ray.ang.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
private const val COLUMNS = 4

private data class DeskApp(val id: String, val label: String, val glyph: String)

private val DEFAULT_APPS = listOf(
    DeskApp("calendar", "Календарь", "📅"),
    DeskApp("contacts", "Контакты", "👤"),
    DeskApp("mail", "Почта", "✉️"),
    DeskApp("browser", "Браузер", "🌐"),
)

/** Persist the cell order as "id:cell,id:cell". Missing/garbage → defaults. */
private fun loadOrder(): MutableList<Pair<DeskApp, Int>> {
    val stored = MmkvManager.decodeSettingsString(KEY_LAYOUT).orEmpty()
    val byId = DEFAULT_APPS.associateBy { it.id }
    val parsed = stored.split(",")
        .mapNotNull { chunk ->
            val parts = chunk.split(":")
            val app = byId[parts.getOrNull(0)] ?: return@mapNotNull null
            val cell = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
            app to cell
        }
    // Any app not covered by the stored layout gets appended at the next free
    // cell, so adding an app in a future version never leaves it invisible.
    val placed = parsed.map { it.first.id }.toSet()
    val result = parsed.toMutableList()
    var next = (parsed.maxOfOrNull { it.second } ?: -1) + 1
    DEFAULT_APPS.filter { it.id !in placed }.forEach { result.add(it to next++) }
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
) {
    // Which app is open, or null on the desktop itself.
    var openApp by remember { mutableStateOf<DeskApp?>(null) }
    openApp?.let { app ->
        VpnkaSmartDeskAppScreen(
            appId = app.id,
            appLabel = app.label,
            appGlyph = app.glyph,
            online = online,
            onBack = { openApp = null },
        )
        return
    }

    val order = remember { mutableStateListOf<Pair<DeskApp, Int>>().apply { addAll(loadOrder()) } }
    var contextFor by remember { mutableStateOf<DeskApp?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showShade by remember { mutableStateOf(false) }     // swipe top-left down
    var showControl by remember { mutableStateOf(false) }   // swipe top-right down
    var wallpaper by remember {
        mutableStateOf(MmkvManager.decodeSettingsString("vpnka_smartdesk_wallpaper") ?: "warm")
    }

    val bg = when (wallpaper) {
        "night" -> Color(0xFF141C2B)
        "forest" -> Color(0xFF15251A)
        else -> VpnkaColors.BgOffMid
    }

    Box(modifier = Modifier.fillMaxSize().background(bg)) {
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        // Status bar: title + server-status dot. No back button here — the
        // top edge is reserved for the shade/control swipes; exit is the
        // bottom «⌂ На главный экран» bar and the system back gesture.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            Text(
                text = "SmartDesk",
                fontFamily = VpnkaFonts.nunito800,
                fontSize = 15.sp,
                color = VpnkaColors.TextStrong,
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (online) VpnkaColors.Green else VpnkaColors.Warning),
            )
        }

        // The desktop grid itself. Long-press on empty space opens settings.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = { showSettings = true })
                },
        ) {
            val density = LocalDensity.current
            val cellDp = 88.dp
            val cellPx = with(density) { cellDp.toPx() }

            order.forEach { (app, cell) ->
                key(app.id) {
                var drag by remember { mutableStateOf(Offset.Zero) }
                var dragging by remember { mutableStateOf(false) }
                val col = cell % COLUMNS
                val row = cell / COLUMNS
                val baseX = col * cellPx
                val baseY = row * cellPx

                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (baseX + drag.x).roundToInt(),
                                (baseY + drag.y).roundToInt(),
                            )
                        }
                        .zIndex(if (dragging) 1f else 0f)
                        .size(cellDp)
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
                                    val newCol = ((baseX + drag.x) / cellPx).roundToInt()
                                        .coerceIn(0, COLUMNS - 1)
                                    val newRow = ((baseY + drag.y) / cellPx).roundToInt()
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
                                .size(58.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.85f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = app.glyph, fontSize = 30.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = app.label,
                            fontFamily = VpnkaFonts.manrope600,
                            fontSize = 12.sp,
                            color = VpnkaColors.TextStrong,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
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

        // Bottom bar — old, simple style, as requested.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.20f))
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "⌂ На главный экран",
                fontFamily = VpnkaFonts.nunito800,
                fontSize = 14.sp,
                color = VpnkaColors.TextStrong,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .pointerInput(Unit) { detectTapGestures { onBack() } }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }

        // Top-edge swipe zones: left half pulls the notification shade down,
        // right half pulls the control centre — like a phone. Thin strips at
        // the very top so they don't steal taps from the desktop below.
        Row(modifier = Modifier.fillMaxWidth().height(28.dp)) {
            Box(
                modifier = Modifier.weight(1f).fillMaxSize().pointerInput(Unit) {
                    detectVerticalDragGestures { _, dy -> if (dy > 6f) showShade = true }
                },
            )
            Box(
                modifier = Modifier.weight(1f).fillMaxSize().pointerInput(Unit) {
                    detectVerticalDragGestures { _, dy -> if (dy > 6f) showControl = true }
                },
            )
        }

        if (showShade) {
            NotificationShade(online = online, onDismiss = { showShade = false })
        }
        if (showControl) {
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
                .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
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
                .fillMaxWidth(0.72f)
                .clip(RoundedCornerShape(bottomStart = 20.dp))
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
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    "warm" to Color(0xFFFFEFD2),
                    "night" to Color(0xFF141C2B),
                    "forest" to Color(0xFF15251A),
                ).forEach { (id, colour) ->
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colour)
                            .pointerInput(id) { detectTapGestures { onWallpaper(id) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (id == wallpaper) Text("✓", fontSize = 20.sp, color = VpnkaColors.Accent)
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
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(VpnkaColors.BgOffCentre)
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(20.dp),
        ) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(
                    "warm" to Color(0xFFFFEFD2),
                    "night" to Color(0xFF141C2B),
                    "forest" to Color(0xFF15251A),
                ).forEach { (id, colour) ->
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colour)
                            .pointerInput(id) { detectTapGestures { onPick(id) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (id == current) {
                            Text("✓", fontSize = 22.sp, color = VpnkaColors.Accent)
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
