package com.v2ray.ang.ui

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.handler.Channels
import com.v2ray.ang.handler.Messenger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


/** «Сообщения» — an E2E messenger in the Telegram mould. */
@Composable
fun VpnkaMessengerApp() {
    var tick by remember { mutableIntStateOf(0) }
    var openId by remember { mutableStateOf<Long?>(null) }
    var handle by remember { mutableStateOf(Messenger.myHandle()) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Messenger.Found>>(emptyList()) }
    var channelResults by remember { mutableStateOf<List<Channels.Channel>>(emptyList()) }
    var openChannel by remember { mutableStateOf<Channels.Channel?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var myChannels by remember { mutableStateOf<List<Channels.Channel>>(emptyList()) }

    // Register our public key (server assigns @handle from Telegram username
    // or device name), then poll for incoming while this app is open.
    LaunchedEffect(Unit) {
        Messenger.refreshMyId()
        handle = Messenger.register("${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        myChannels = Channels.mine()
        while (true) {
            if (VpnkaColors.connected) {
                var changed = Messenger.poll()
                if (Messenger.fetchReceipts()) changed = true
                if (changed) tick++
            }
            delay(2500)
        }
    }
    LaunchedEffect(tick) { myChannels = Channels.mine() }

    // Debounced search over people and channels.
    LaunchedEffect(query) {
        if (query.trim().length < 2) { results = emptyList(); channelResults = emptyList() } else {
            delay(300)
            results = Messenger.searchUsers(query)
            channelResults = Channels.search(query)
        }
    }

    val contacts = remember(tick) { Messenger.contacts() }

    openChannel?.let { ch ->
        ChannelScreen(channel = ch, onBack = { openChannel = null; tick++ })
        return
    }

    openId?.let { id ->
        val c = contacts.firstOrNull { it.id == id }
        if (c != null) {
            ChatScreen(contact = c, tick = tick, onSent = { tick++ }, onBack = { openId = null })
            return
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Current user's @handle up top.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (handle.isBlank()) "…" else "@$handle",
                fontFamily = VpnkaFonts.nunito800, fontSize = 16.sp, color = VpnkaColors.TextStrong,
            )
            Spacer(Modifier.weight(1f))
            Text("＋ Канал", fontFamily = VpnkaFonts.nunito800, fontSize = 13.sp, color = VpnkaColors.Accent,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { showCreate = true }.padding(horizontal = 10.dp, vertical = 4.dp))
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
                        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
                            .clip(RoundedCornerShape(16.dp)).background(VpnkaColors.CardServer)
                            .clickable { openId = c.id }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MsgAvatar(c.name)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(c.name, fontFamily = VpnkaFonts.nunito800, fontSize = 16.sp, color = VpnkaColors.TextStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (last != null) {
                                Text(
                                    (if (last.mine) "Вы: " else "") + last.text,
                                    fontFamily = VpnkaFonts.manrope600, fontSize = 13.sp, color = VpnkaColors.TextMuted,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
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
            Box(modifier = Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape)
                .background(VpnkaColors.Accent.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                Text(glyph, fontSize = 18.sp)
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

@Composable
private fun ChatScreen(
    contact: Messenger.Contact,
    tick: Int,
    onSent: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf("") }
    val msgs = remember(tick, contact.id) { Messenger.messages(contact.id) }
    val receipt = remember(tick, contact.id) { Messenger.receiptFor(contact.id) }

    // Opening the chat (and each new incoming message) marks it read (✓✓).
    LaunchedEffect(tick, contact.id) {
        val maxIn = msgs.filter { !it.mine }.maxOfOrNull { it.id } ?: 0L
        if (maxIn > 0L) Messenger.markRead(contact.id, maxIn)
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
            Text(contact.name, fontFamily = VpnkaFonts.nunito800, fontSize = 16.sp, color = VpnkaColors.TextStrong)
        }
        LazyColumn(modifier = Modifier.fillMaxSize().weight(1f).padding(horizontal = 12.dp)) {
            // Outgoing messages all carry id=0 and can share a millisecond, so
            // the list index guarantees a unique key (Compose crashes on dupes).
            itemsIndexed(msgs, key = { i, m -> "$i:${m.id}:${m.ts}" }) { _, m ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start,
                ) {
                    Box(
                        modifier = Modifier.widthIn(max = 260.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (m.mine) VpnkaColors.Green.copy(alpha = 0.85f) else VpnkaColors.CardServer)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        if (m.mine) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(m.text, fontFamily = VpnkaFonts.manrope600, fontSize = 15.sp, color = Color.White)
                                val (mark, tint) = msgrTicks(m, receipt)
                                Text(mark, fontSize = 10.sp, color = tint, modifier = Modifier.padding(top = 1.dp))
                            }
                        } else {
                            Text(m.text, fontFamily = VpnkaFonts.manrope600, fontSize = 15.sp, color = VpnkaColors.TextStrong)
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) { MsgField("Сообщение", draft) { draft = it } }
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

@Composable
private fun MsgAvatar(name: String) {
    val initials = name.trim().split(" ").filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.first().uppercase() }.ifBlank { "?" }
    Box(
        modifier = Modifier.size(40.dp).clip(CircleShape).background(VpnkaColors.Accent.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center,
    ) { Text(initials, fontFamily = VpnkaFonts.nunito800, fontSize = 14.sp, color = Color.White) }
}

@Composable
private fun MsgField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, color = VpnkaColors.TextMuted) },
        singleLine = true,
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
