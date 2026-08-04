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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val KEY_MY_NAME = "vpnka_messenger_myname"

/** «Сообщения» — an E2E messenger in the Telegram mould. */
@Composable
fun VpnkaMessengerApp() {
    var tick by remember { mutableIntStateOf(0) }
    var openId by remember { mutableStateOf<Long?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var showMyCode by remember { mutableStateOf(false) }

    // Learn our id once, then poll for incoming while this app is open.
    LaunchedEffect(Unit) {
        Messenger.refreshMyId()
        while (true) {
            if (VpnkaColors.connected) {
                if (Messenger.poll()) tick++
            }
            delay(2500)
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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Toolbar: add contact + my code.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                VpnkaSecondaryButton(text = "＋ Добавить", onClick = { showAdd = true }, modifier = Modifier.weight(1f))
                VpnkaSecondaryButton(text = "Мой код", onClick = { showMyCode = true }, modifier = Modifier.weight(1f))
            }
            if (contacts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Чатов пока нет. Добавьте собеседника по его коду, или дайте свой код — код содержит ваш ключ шифрования.",
                        fontFamily = VpnkaFonts.manrope600, fontSize = 14.sp, color = VpnkaColors.TextMuted,
                    )
                }
            } else {
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

    if (showAdd) {
        var code by remember { mutableStateOf("") }
        var err by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Добавить собеседника", fontFamily = VpnkaFonts.nunito800, color = VpnkaColors.TextStrong) },
            text = {
                Column {
                    Text("Вставьте код собеседника:", fontSize = 13.sp, color = VpnkaColors.TextMuted)
                    Spacer(Modifier.height(8.dp))
                    MsgField("Код", code) { code = it; err = false }
                    if (err) Text("Код не распознан", fontSize = 12.sp, color = VpnkaColors.Warning)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val c = Messenger.parseInvite(code)
                    if (c == null) err = true else { Messenger.addContact(c); tick++; showAdd = false }
                }) { Text("Добавить") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Отмена") } },
            containerColor = VpnkaColors.BgOffCentre,
        )
    }

    if (showMyCode) {
        var name by remember { mutableStateOf(MmkvManager.decodeSettingsString(KEY_MY_NAME) ?: "") }
        val code = remember(name, tick) { Messenger.myInviteCode(name.ifBlank { "Пользователь" }) }
        AlertDialog(
            onDismissRequest = { showMyCode = false },
            title = { Text("Мой код", fontFamily = VpnkaFonts.nunito800, color = VpnkaColors.TextStrong) },
            text = {
                Column {
                    MsgField("Ваше имя", name) { name = it; MmkvManager.encodeSettings(KEY_MY_NAME, it) }
                    Spacer(Modifier.height(10.dp))
                    Text("Передайте этот код собеседнику — он содержит ваш ключ шифрования:", fontSize = 12.sp, color = VpnkaColors.TextMuted)
                    Spacer(Modifier.height(6.dp))
                    Text(code, fontSize = 11.sp, color = VpnkaColors.TextStrong)
                }
            },
            confirmButton = {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                TextButton(onClick = {
                    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    cm?.setPrimaryClip(android.content.ClipData.newPlainText("vpnka", code))
                    showMyCode = false
                }) { Text("Скопировать") }
            },
            dismissButton = { TextButton(onClick = { showMyCode = false }) { Text("Закрыть") } },
            containerColor = VpnkaColors.BgOffCentre,
        )
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
