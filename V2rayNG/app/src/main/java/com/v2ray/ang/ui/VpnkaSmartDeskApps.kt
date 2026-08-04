package com.v2ray.ang.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

/** Dispatch a desktop icon to its real app screen (Phase 2). */
@Composable
fun VpnkaSmartDeskAppScreen(
    appId: String,
    appLabel: String,
    appGlyph: String,
    online: Boolean,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VpnkaColors.BgOffMid),
    ) {
        SmartDeskAppBar(title = appLabel, glyph = appGlyph, online = online, onBack = onBack)
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
        Box(modifier = Modifier.fillMaxSize()) {
            when (appId) {
                "calendar" -> CalendarApp(syncTick, onChanged)
                "contacts" -> ContactsApp(syncTick, onChanged)
                "mail" -> MailApp(syncTick, onChanged)
                "browser" -> BrowserApp()
                else -> EmptyHint("Приложение недоступно")
            }
        }
    }
}

@Composable
private fun SmartDeskAppBar(
    title: String,
    glyph: String,
    online: Boolean,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "‹ Рабочий стол",
            fontFamily = VpnkaFonts.nunito800,
            fontSize = 15.sp,
            color = VpnkaColors.TextStrong,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onBack)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
        Spacer(Modifier.weight(1f))
        Text(text = "$glyph  $title", fontFamily = VpnkaFonts.nunito800, fontSize = 15.sp, color = VpnkaColors.TextStrong)
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (online) VpnkaColors.Green else VpnkaColors.Warning),
        )
    }
}

// ---------------------------------------------------------------- Calendar ---

@Composable
private fun CalendarApp(syncTick: Int, onChanged: () -> Unit) {
    var items by remember(syncTick) { mutableStateOf(SmartDeskStore.calendar()) }
    var editing by remember { mutableStateOf<SmartDeskStore.CalendarEvent?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    fun reload() { items = SmartDeskStore.calendar(); onChanged() }

    AppScaffold(
        empty = items.isEmpty(),
        emptyHint = "Событий пока нет. Добавьте первое.",
        onAdd = { editing = null; showEditor = true },
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            items(items, key = { it.id }) { e ->
                Card(
                    title = e.title.ifBlank { "Без названия" },
                    subtitle = listOf(e.whenText, e.note).filter { it.isNotBlank() }.joinToString(" · "),
                    onClick = { editing = e; showEditor = true },
                )
            }
        }
    }

    if (showEditor) {
        val e = editing
        var title by remember(e) { mutableStateOf(e?.title ?: "") }
        var whenText by remember(e) { mutableStateOf(e?.whenText ?: "") }
        var note by remember(e) { mutableStateOf(e?.note ?: "") }
        EditorDialog(
            heading = if (e == null) "Новое событие" else "Событие",
            canDelete = e != null,
            onDismiss = { showEditor = false },
            onDelete = { e?.let { SmartDeskStore.deleteEvent(it.id) }; reload(); showEditor = false },
            onSave = {
                SmartDeskStore.saveEvent(
                    SmartDeskStore.CalendarEvent(
                        id = e?.id ?: SmartDeskStore.newId(),
                        title = title.trim(), whenText = whenText.trim(), note = note.trim(),
                        updatedAt = nowMillis(),
                    )
                )
                reload(); showEditor = false
            },
        ) {
            DeskField("Название", title) { title = it }
            DeskField("Когда (напр. 12 авг, 15:00)", whenText) { whenText = it }
            DeskField("Заметка", note, minLines = 2) { note = it }
        }
    }
}

// ---------------------------------------------------------------- Contacts ---

@Composable
private fun ContactsApp(syncTick: Int, onChanged: () -> Unit) {
    var items by remember(syncTick) { mutableStateOf(SmartDeskStore.contacts()) }
    var editing by remember { mutableStateOf<SmartDeskStore.Contact?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    fun reload() { items = SmartDeskStore.contacts(); onChanged() }

    AppScaffold(
        empty = items.isEmpty(),
        emptyHint = "Контактов пока нет. Добавьте первый.",
        onAdd = { editing = null; showEditor = true },
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            items(items, key = { it.id }) { c ->
                Card(
                    title = c.name.ifBlank { "Без имени" },
                    subtitle = listOf(c.phone, c.email).filter { it.isNotBlank() }.joinToString(" · "),
                    onClick = { editing = c; showEditor = true },
                )
            }
        }
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
            onDelete = { c?.let { SmartDeskStore.deleteContact(it.id) }; reload(); showEditor = false },
            onSave = {
                SmartDeskStore.saveContact(
                    SmartDeskStore.Contact(
                        id = c?.id ?: SmartDeskStore.newId(),
                        name = name.trim(), phone = phone.trim(), email = email.trim(), note = note.trim(),
                        updatedAt = nowMillis(),
                    )
                )
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

// -------------------------------------------------------------------- Mail ---

@Composable
private fun MailApp(syncTick: Int, onChanged: () -> Unit) {
    var items by remember(syncTick) { mutableStateOf(SmartDeskStore.mail()) }
    var editing by remember { mutableStateOf<SmartDeskStore.MailMessage?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    fun reload() { items = SmartDeskStore.mail(); onChanged() }

    AppScaffold(
        empty = items.isEmpty(),
        emptyHint = "Писем пока нет. Напишите первое — оно отправится при синхронизации.",
        onAdd = { editing = null; showEditor = true },
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            items(items, key = { it.id }) { m ->
                Card(
                    title = m.subject.ifBlank { "(без темы)" },
                    subtitle = listOf(if (m.to.isNotBlank()) "кому: ${m.to}" else "", m.body)
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    onClick = { editing = m; showEditor = true },
                )
            }
        }
    }

    if (showEditor) {
        val m = editing
        var to by remember(m) { mutableStateOf(m?.to ?: "") }
        var subject by remember(m) { mutableStateOf(m?.subject ?: "") }
        var body by remember(m) { mutableStateOf(m?.body ?: "") }
        EditorDialog(
            heading = if (m == null) "Новое письмо" else "Письмо",
            canDelete = m != null,
            onDismiss = { showEditor = false },
            onDelete = { m?.let { SmartDeskStore.deleteMail(it.id) }; reload(); showEditor = false },
            onSave = {
                SmartDeskStore.saveMail(
                    SmartDeskStore.MailMessage(
                        id = m?.id ?: SmartDeskStore.newId(),
                        to = to.trim(), subject = subject.trim(), body = body.trim(),
                        updatedAt = nowMillis(),
                    )
                )
                reload(); showEditor = false
            },
        ) {
            DeskField("Кому (пользователь vpnka)", to) { to = it }
            DeskField("Тема", subject) { subject = it }
            DeskField("Текст", body, minLines = 3) { body = it }
        }
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
@SuppressLint("SetJavaScriptEnabled")
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

    val context = LocalContext.current
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

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
            loadUrl("https://duckduckgo.com/")
        }
    }
    var address by remember { mutableStateOf("https://duckduckgo.com/") }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "‹",
                fontSize = 22.sp,
                color = VpnkaColors.TextStrong,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { if (webView.canGoBack()) webView.goBack() }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
            Box(modifier = Modifier.weight(1f)) {
                DeskField("Адрес или запрос", address) { address = it }
            }
            Text(
                text = "→",
                fontSize = 22.sp,
                color = VpnkaColors.Accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { webView.loadUrl(normalizeUrl(address)) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize().weight(1f),
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

/** Wall-clock millis. Isolated so the desktop code reads cleanly. */
private fun nowMillis(): Long = System.currentTimeMillis()
