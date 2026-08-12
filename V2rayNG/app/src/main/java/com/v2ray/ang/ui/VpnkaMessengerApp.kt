package com.v2ray.ang.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.v2ray.ang.handler.Channels
import com.v2ray.ang.handler.Messenger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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


/** «Сообщения» — an E2E messenger in the Telegram mould. */
@Composable
fun VpnkaMessengerApp() {
    var tick by remember { mutableIntStateOf(0) }
    var openId by remember { mutableStateOf<Long?>(null) }
    var handle by remember { mutableStateOf(Messenger.myHandle()) }
    var showMyProfile by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Messenger.Found>>(emptyList()) }
    var channelResults by remember { mutableStateOf<List<Channels.Channel>>(emptyList()) }
    var openChannel by remember { mutableStateOf<Channels.Channel?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var myChannels by remember { mutableStateOf<List<Channels.Channel>>(emptyList()) }
    var typingFrom by remember { mutableStateOf(0L) }
    var typingUntil by remember { mutableStateOf(0L) }
    val scope = rememberCoroutineScope()

    // Register our public key (server assigns @handle from Telegram username
    // or device name), then poll for incoming while this app is open. A
    // WebSocket rides alongside for instant wake + "typing"; polling stays as
    // the fallback so a dropped socket never loses messages.
    LaunchedEffect(Unit) {
        Messenger.refreshMyId()
        handle = Messenger.register("${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        myChannels = Channels.mine()
        while (true) {
            if (VpnkaColors.connected) {
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
    DisposableEffect(Unit) { onDispose { Messenger.disconnectWs() } }
    LaunchedEffect(tick) { myChannels = Channels.mine() }

    // Opened from a message notification: jump into that chat. The contact may
    // not be loaded yet on a cold start — poll() above adds it, and the tick
    // bump then lets openId resolve to it.
    LaunchedEffect(Unit) {
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

    if (showMyProfile) {
        MyProfileScreen(handle = handle, onBack = { showMyProfile = false })
        return
    }

    openChannel?.let { ch ->
        ChannelScreen(channel = ch, onBack = { openChannel = null; tick++ })
        return
    }

    openId?.let { id ->
        val c = contacts.firstOrNull { it.id == id }
        if (c != null) {
            val typing = typingFrom == c.id && System.currentTimeMillis() < typingUntil
            ChatScreen(contact = c, tick = tick, typing = typing, onSent = { tick++ }, onBack = { openId = null })
            return
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Current user's @handle up top.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Tap your own name/avatar → your profile (nick, key, settings).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clip(RoundedCornerShape(14.dp))
                    .clickable { showMyProfile = true }
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
        Box(modifier = Modifier.padding(horizontal = 12.dp)) {
            MsgField("Поиск людей и каналов", query) { query = it }
        }
        Spacer(Modifier.height(4.dp))

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
        } else if (contacts.isEmpty() && myChannels.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Чатов пока нет. Найдите человека по нику через поиск выше — переписка шифруется.",
                    fontFamily = VpnkaFonts.manrope600, fontSize = 14.sp, color = VpnkaColors.TextMuted,
                )
            }
        } else {
            // Chats + subscribed channels.
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                if (myChannels.isNotEmpty()) {
                    item { SectionLabel("Каналы") }
                    items(myChannels, key = { "ch" + it.id }) { ch ->
                        ResultRow(glyph = "📢", title = ch.title, sub = "@${ch.handle}") { openChannel = ch }
                    }
                    if (contacts.isNotEmpty()) item { SectionLabel("Чаты") }
                }
                items(contacts, key = { it.id }) { c ->
                    val last = Messenger.messages(c.id).lastOrNull()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(18.dp)).background(VpnkaColors.CardServer)
                            .clickable { openId = c.id }.padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MsgAvatar(c.name, size = 50)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    c.name, fontFamily = VpnkaFonts.nunito800, fontSize = 16.sp,
                                    color = VpnkaColors.TextStrong, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (last != null) {
                                    Text(msgTime(last.ts), fontFamily = VpnkaFonts.manrope600, fontSize = 11.sp, color = VpnkaColors.TextFaint)
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            val preview = when {
                                last == null -> "Нет сообщений"
                                last.k == "image" -> (if (last.mine) "Вы: " else "") + "📷 Фото"
                                else -> (if (last.mine) "Вы: " else "") + last.text
                            }
                            Text(
                                preview, fontFamily = VpnkaFonts.manrope600, fontSize = 13.sp,
                                color = VpnkaColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
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
private fun SectionLabel(text: String) {
    Text(text, fontFamily = VpnkaFonts.manrope600, fontSize = 12.sp, color = VpnkaColors.TextMuted,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
}

@Composable
private fun ResultRow(glyph: String?, title: String, sub: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
            .clip(RoundedCornerShape(16.dp)).background(VpnkaColors.CardServer)
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
private fun ChannelScreen(channel: Channels.Channel, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var posts by remember { mutableStateOf<List<Channels.Post>>(emptyList()) }
    // The owner can always read/post, even if the subscription flag lags.
    var subscribed by remember { mutableStateOf(channel.subscribed || channel.isOwner) }
    var draft by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(refresh) {
        if (subscribed) posts = Channels.feed(channel.id)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", fontSize = 24.sp, color = VpnkaColors.TextStrong,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onBack).padding(horizontal = 8.dp, vertical = 4.dp))
            Spacer(Modifier.width(6.dp))
            Text("📢", fontSize = 20.sp)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(channel.title, fontFamily = VpnkaFonts.nunito800, fontSize = 16.sp, color = VpnkaColors.TextStrong)
                Text("@${channel.handle}", fontFamily = VpnkaFonts.manrope600, fontSize = 12.sp, color = VpnkaColors.TextMuted)
            }
        }
        if (!subscribed) {
            Box(modifier = Modifier.fillMaxSize().weight(1f).padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Подпишитесь, чтобы читать канал", fontFamily = VpnkaFonts.manrope600, fontSize = 14.sp, color = VpnkaColors.TextMuted)
                    Spacer(Modifier.height(10.dp))
                    Box(modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(VpnkaColors.Accent)
                        .clickable { scope.launch { if (Channels.subscribe(channel.id)) { subscribed = true; refresh++ } } }
                        .padding(horizontal = 20.dp, vertical = 10.dp)) {
                        Text("Подписаться", fontFamily = VpnkaFonts.nunito800, fontSize = 15.sp, color = Color.White)
                    }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().weight(1f).padding(horizontal = 12.dp)) {
                if (posts.isEmpty()) item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Постов пока нет", fontFamily = VpnkaFonts.manrope600, fontSize = 14.sp, color = VpnkaColors.TextMuted)
                    }
                }
                items(posts, key = { it.id }) { p ->
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp)).background(VpnkaColors.CardServer).padding(12.dp)) {
                        Text(p.body, fontFamily = VpnkaFonts.manrope600, fontSize = 15.sp, color = VpnkaColors.TextStrong)
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
                        }, contentAlignment = Alignment.Center) { Text("➤", fontSize = 20.sp, color = Color.White) }
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
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf("") }
    val msgs = remember(tick, contact.id) { Messenger.messages(contact.id) }
    val receipt = remember(tick, contact.id) { Messenger.receiptFor(contact.id) }
    // null = no photo being sent; 0..100 = upload progress; -1 = failed.
    var sendPct by remember { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()
    var showProfile by remember { mutableStateOf(false) }
    // Follow the conversation: when a message is sent OR arrives (msgs grows),
    // and while a photo uploads, keep the newest row on screen.
    LaunchedEffect(msgs.size, sendPct) {
        val total = msgs.size + (if (sendPct != null) 1 else 0)
        if (total > 0) listState.animateScrollToItem(total - 1)
    }

    // Opening the chat (and each new incoming message) marks it read (✓✓).
    LaunchedEffect(tick, contact.id) {
        val maxIn = msgs.filter { !it.mine }.maxOfOrNull { it.id } ?: 0L
        if (maxIn > 0L) Messenger.markRead(contact.id, maxIn)
    }

    val context = LocalContext.current
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

    if (showProfile) {
        ContactProfileScreen(contact = contact, onBack = { showProfile = false })
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("‹", fontSize = 24.sp, color = VpnkaColors.TextStrong,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onBack).padding(horizontal = 8.dp, vertical = 4.dp))
            Spacer(Modifier.width(4.dp))
            MsgAvatar(contact.name)
            Spacer(Modifier.width(10.dp))
            // Tapping the name (or the "typing…" line under it) opens the
            // contact's profile with the E2E key fingerprint.
            Column(modifier = Modifier.clickable { showProfile = true }) {
                Text(contact.name, fontFamily = VpnkaFonts.nunito800, fontSize = 16.sp, color = VpnkaColors.TextStrong)
                if (typing) {
                    Text("печатает…", fontFamily = VpnkaFonts.manrope600, fontSize = 12.sp, color = VpnkaColors.Accent)
                }
            }
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().weight(1f).padding(horizontal = 12.dp)) {
            // Outgoing messages all carry id=0 and can share a millisecond, so
            // the list index guarantees a unique key (Compose crashes on dupes).
            itemsIndexed(msgs, key = { i, m -> "$i:${m.id}:${m.ts}" }) { _, m ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start,
                ) {
                    Box(
                        modifier = Modifier.widthIn(max = 284.dp)
                            .clip(
                                RoundedCornerShape(
                                    topStart = 18.dp, topEnd = 18.dp,
                                    bottomStart = if (m.mine) 18.dp else 5.dp,
                                    bottomEnd = if (m.mine) 5.dp else 18.dp,
                                )
                            )
                            .then(
                                if (m.mine)
                                    Modifier.background(Brush.linearGradient(listOf(Color(0xFF2FAE4F), Color(0xFF10B981))))
                                else Modifier.background(VpnkaColors.CardServer)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
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
                                        color = if (m.mine) Color.White else VpnkaColors.TextStrong)
                                }
                            } else {
                                Text(m.text, fontFamily = VpnkaFonts.manrope600, fontSize = 15.sp,
                                    color = if (m.mine) Color.White else VpnkaColors.TextStrong)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.align(Alignment.End).padding(top = 3.dp),
                            ) {
                                Text(
                                    msgTime(m.ts), fontSize = 10.sp,
                                    color = if (m.mine) Color.White.copy(alpha = 0.85f) else VpnkaColors.TextFaint,
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
            // Photo-send status: uploading a photo is dozens of small requests
            // over the shaped RU leg, so show progress rather than a frozen UI.
            sendPct?.let { p ->
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(16.dp))
                                .background(VpnkaColors.Green.copy(alpha = 0.85f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                if (p < 0) "⚠️ Не удалось отправить фото" else "📷 Отправка… $p%",
                                fontFamily = VpnkaFonts.manrope600, fontSize = 14.sp, color = Color.White,
                            )
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape)
                    .background(VpnkaColors.CardSettings)
                    .clickable { picker.launch("image/*") },
                contentAlignment = Alignment.Center,
            ) { Text("📎", fontSize = 20.sp) }
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) { MsgField("Сообщение", draft) { draft = it; Messenger.sendTyping(contact.id) } }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier.size(46.dp).clip(CircleShape).background(VpnkaColors.Accent)
                    .clickable {
                        val t = draft.trim()
                        if (t.isNotBlank()) {
                            draft = ""
                            scope.launch { if (Messenger.send(contact.id, t)) onSent() }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) { Text("➤", fontSize = 20.sp, color = Color.White) }
        }
    }
}

/** Short, human-readable fingerprint of a contact's public key (first 8
 *  bytes of its SHA-256, hex). Two people compare it to be sure no one
 *  substituted the key — the safety-number idea, kept compact. */
private fun keyFingerprint(pubKey: String): String = try {
    val h = java.security.MessageDigest.getInstance("SHA-256").digest(pubKey.toByteArray())
    h.take(8).joinToString(" ") { "%02X".format(it) }
} catch (e: Exception) { "—" }

/** «Мой профиль» — your own identity, encryption key and messenger settings.
 *  Opened by tapping your @handle on the chat list. */
@Composable
private fun MyProfileScreen(handle: String, onBack: () -> Unit) {
    val myKey = remember { Messenger.myPublicKey() }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("‹", fontSize = 24.sp, color = VpnkaColors.TextStrong,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onBack).padding(horizontal = 8.dp, vertical = 4.dp))
            Spacer(Modifier.width(6.dp))
            Text("Мой профиль", fontFamily = VpnkaFonts.nunito800, fontSize = 16.sp, color = VpnkaColors.TextStrong)
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MsgAvatar(if (handle.isBlank()) "?" else handle, size = 96)
            Spacer(Modifier.height(12.dp))
            Text(if (handle.isBlank()) "…" else "@$handle", fontFamily = VpnkaFonts.nunito800, fontSize = 22.sp, color = VpnkaColors.TextStrong)
            Spacer(Modifier.height(2.dp))
            Text("Ваш постоянный ник в мессенджере", fontFamily = VpnkaFonts.manrope600, fontSize = 12.sp, color = VpnkaColors.TextMuted)
        }
        Spacer(Modifier.height(14.dp))
        ProfileCard("🔒 Безопасность") {
            Text("Отпечаток вашего ключа шифрования", fontFamily = VpnkaFonts.manrope600, fontSize = 12.sp, color = VpnkaColors.TextMuted)
            Spacer(Modifier.height(4.dp))
            Text(keyFingerprint(myKey), fontFamily = VpnkaFonts.nunito800, fontSize = 16.sp, color = VpnkaColors.Accent)
            Spacer(Modifier.height(8.dp))
            Text(
                "Переписка шифруется прямо на вашем устройстве. Сервер видит только зашифрованные пакеты — ни сообщений, ни ключей у него нет.",
                fontFamily = VpnkaFonts.manrope600, fontSize = 12.sp, color = VpnkaColors.TextMuted,
            )
        }
        ProfileCard("⚙️ Настройки") {
            MyProfileToggle("Уведомлять о новых сообщениях", "notify")
            MyProfileToggle("Отправлять «печатает…»", "typing")
            MyProfileToggle("Отправлять отметку о прочтении", "read")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ProfileCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp)).background(VpnkaColors.CardServer).padding(16.dp),
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
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(VpnkaColors.CardServer).padding(16.dp),
            ) {
                Text("🔒 Ключ шифрования", fontFamily = VpnkaFonts.nunito800, fontSize = 14.sp, color = VpnkaColors.TextStrong)
                Spacer(Modifier.height(8.dp))
                Text(keyFingerprint(contact.pubKey), fontFamily = VpnkaFonts.nunito800, fontSize = 16.sp, color = VpnkaColors.Accent)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Если этот код совпадает у вас и у собеседника — переписку никто не подменил.",
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
        shape = RoundedCornerShape(18.dp),
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
