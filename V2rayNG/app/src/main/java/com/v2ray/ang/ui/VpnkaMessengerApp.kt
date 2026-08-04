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

    // Register our public key (server assigns @handle from Telegram username
    // or device name), then poll for incoming while this app is open.
    LaunchedEffect(Unit) {
        Messenger.refreshMyId()
        handle = Messenger.register("${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        while (true) {
            if (VpnkaColors.connected) {
                if (Messenger.poll()) tick++
            }
            delay(2500)
        }
    }

    // Debounced user/channel search.
    LaunchedEffect(query) {
        if (query.trim().length < 2) { results = emptyList() } else {
            delay(300)
            results = Messenger.searchUsers(query)
        }
    }

    val contacts = remember(tick) { Messenger.contacts() }

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
            Text("Сообщения", fontFamily = VpnkaFonts.manrope600, fontSize = 12.sp, color = VpnkaColors.TextMuted)
        }
        // Search people and channels.
        Box(modifier = Modifier.padding(horizontal = 12.dp)) {
            MsgField("Поиск людей и каналов", query) { query = it }
        }
        Spacer(Modifier.height(4.dp))

        if (query.trim().length >= 2) {
            // Search results.
            if (results.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("Никого не найдено", fontFamily = VpnkaFonts.manrope600, fontSize = 14.sp, color = VpnkaColors.TextMuted)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                    items(results, key = { it.id }) { r ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
                                .clip(RoundedCornerShape(16.dp)).background(VpnkaColors.CardServer)
                                .clickable {
                                    Messenger.startChat(r); tick++; query = ""; openId = r.id
                                }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MsgAvatar(r.handle)
                            Spacer(Modifier.width(12.dp))
                            Text("@${r.handle}", fontFamily = VpnkaFonts.nunito800, fontSize = 16.sp, color = VpnkaColors.TextStrong)
                        }
                    }
                }
            }
        } else if (contacts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Чатов пока нет. Найдите человека по нику через поиск выше — переписка шифруется.",
                    fontFamily = VpnkaFonts.manrope600, fontSize = 14.sp, color = VpnkaColors.TextMuted,
                )
            }
        } else {
            // Chat list.
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
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
            items(msgs, key = { it.id.toString() + it.ts }) { m ->
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
                        Text(m.text, fontFamily = VpnkaFonts.manrope600, fontSize = 15.sp,
                            color = if (m.mine) Color.White else VpnkaColors.TextStrong)
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
