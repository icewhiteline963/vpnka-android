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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.v2ray.ang.handler.Vault
import kotlinx.coroutines.launch

/**
 * Passphrase gate for the encrypted cloud. First time: create a cloud
 * passphrase and save the recovery key. On a fresh device / after lock: enter
 * the passphrase (or use the recovery key). Once unlocked the master key is
 * cached on-device, so this appears only when it must.
 */
@Composable
fun VpnkaVaultGate(onUnlocked: () -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    // "checking" | "setup" | "unlock" | "recovery" | "showRecovery"
    var mode by remember { mutableStateOf("checking") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    var recovery by remember { mutableStateOf("") }
    var shownRecovery by remember { mutableStateOf("") }

    suspend fun probe() {
        // Сначала спрашиваем СЕРВЕР, потом смотрим свой кэш.
        //
        // Раньше первым шёл кэш: раз ключ лежит на телефоне — заходим. Но
        // сейф на сервере могли сбросить (или это уже другой аккаунт), и
        // тогда телефон входил со своим ключом в пустоту: расшифровывать
        // нечего, а предложить завести новый сейф экран не мог — он же
        // «уже разблокирован». Заново создать сейф становилось нельзя
        // ничем, кроме выхода из аккаунта.
        //
        // Порядок обратный, но осторожный: «нет сейфа» мы принимаем только
        // от подтверждённого 404 на запрос С ТОКЕНОМ. Недоступный сервер
        // отвечает «offline», и там кэш по-прежнему главный — иначе сейф
        // переставал открываться без сети.
        when (Vault.status()) {
            "exists" -> if (Vault.isUnlocked()) { onUnlocked(); return } else mode = "unlock"
            "absent" -> {
                // Ключ на телефоне осиротел: расшифровывать им нечего.
                Vault.lock()
                mode = "setup"
            }
            else -> if (Vault.isUnlocked()) { onUnlocked(); return } else mode = "offline"
        }
    }
    LaunchedEffect(Unit) { probe() }

    Box(modifier = Modifier.fillMaxSize().background(VpnkaColors.BgOffMid)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text("‹ Назад", fontFamily = VpnkaFonts.nunito800, fontSize = 15.sp, color = VpnkaColors.TextStrong,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onBack).padding(8.dp))
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("🔐", fontSize = 44.sp)
            Spacer(Modifier.height(10.dp))
            when (mode) {
                "checking" -> CircularProgressIndicator(color = VpnkaColors.Accent)

                "offline" -> {
                    Head("Нет связи с облаком", "Включите VPN на главном экране — облако работает только через него.")
                    Primary("Повторить", false) { mode = "checking"; scope.launch { probe() } }
                }

                "setup" -> {
                    Head("Облачный пароль", "Придумайте пароль для облака. Им шифруются ваши данные — мы его не храним и восстановить не сможем, поэтому сохраните ключ восстановления на следующем шаге.")
                    Field("Пароль", p1, true) { p1 = it; error = null }
                    Field("Повторите пароль", p2, true) { p2 = it; error = null }
                    Err(error)
                    Primary(if (busy) "…" else "Создать", busy) {
                        when {
                            p1.length < 6 -> error = "Минимум 6 символов"
                            p1 != p2 -> error = "Пароли не совпадают"
                            else -> { busy = true; scope.launch {
                                val r = Vault.setup(p1)
                                busy = false
                                if (r == null) error = "Нет связи с сервером — включите VPN"
                                else { shownRecovery = r.recoveryKey; mode = "showRecovery" }
                            } }
                        }
                    }
                }

                "showRecovery" -> {
                    Head("Ключ восстановления", "Сохраните его. Если забудете пароль — только этот ключ вернёт доступ к данным. Мы его не храним.")
                    val clip = androidx.compose.ui.platform.LocalClipboardManager.current
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    fun copyKey() {
                        clip.setText(androidx.compose.ui.text.AnnotatedString(shownRecovery))
                        android.widget.Toast.makeText(ctx, "Ключ скопирован", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.85f)).clickable { copyKey() }.padding(16.dp)) {
                        Text(shownRecovery, fontFamily = VpnkaFonts.nunito800, fontSize = 18.sp, color = VpnkaColors.TextStrong)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "📋  Копировать ключ",
                        fontFamily = VpnkaFonts.nunito800, fontSize = 14.sp, color = VpnkaColors.Accent,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { copyKey() }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Primary("Я сохранил, продолжить", false) { onUnlocked() }
                }

                "unlock" -> {
                    Head("Введите пароль облака", "Данные зашифрованы вашим паролем.")
                    Field("Пароль", p1, true) { p1 = it; error = null }
                    Err(error)
                    Primary(if (busy) "…" else "Войти", busy) {
                        busy = true; scope.launch {
                            val ok = Vault.unlock(p1); busy = false
                            when (ok) {
                                true -> onUnlocked()
                                false -> error = "Неверный пароль"
                                null -> error = "Нет связи с облаком — включите VPN"
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Забыли пароль? Войти по ключу восстановления",
                        fontFamily = VpnkaFonts.manrope600, fontSize = 13.sp, color = VpnkaColors.Accent,
                        modifier = Modifier.clickable { error = null; mode = "recovery" }.padding(8.dp))
                }

                "recovery" -> {
                    Head("Ключ восстановления", "Введите ключ, который вы сохранили при создании пароля.")
                    Field("Ключ восстановления", recovery, false) { recovery = it; error = null }
                    Err(error)
                    Primary(if (busy) "…" else "Восстановить", busy) {
                        busy = true; scope.launch {
                            val ok = Vault.unlockRecovery(recovery); busy = false
                            when (ok) {
                                true -> mode = "reset"
                                false -> error = "Ключ не подошёл"
                                null -> error = "Нет связи с облаком — включите VPN"
                            }
                        }
                    }
                }

                "reset" -> {
                    // Recovered — force a new passphrase so future logins work by password again.
                    Head("Новый пароль", "Доступ восстановлен. Задайте новый пароль облака.")
                    Field("Новый пароль", p1, true) { p1 = it; error = null }
                    Field("Повторите", p2, true) { p2 = it; error = null }
                    Err(error)
                    Primary(if (busy) "…" else "Сохранить и войти", busy) {
                        when {
                            p1.length < 6 -> error = "Минимум 6 символов"
                            p1 != p2 -> error = "Пароли не совпадают"
                            else -> { busy = true; scope.launch {
                                val ok = Vault.changePassphrase(p1); busy = false
                                if (ok) onUnlocked() else error = "Нет связи — включите VPN"
                            } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Head(title: String, sub: String) {
    Text(title, fontFamily = VpnkaFonts.nunito900, fontSize = 20.sp, color = VpnkaColors.TextStrong)
    Spacer(Modifier.height(6.dp))
    Text(sub, fontFamily = VpnkaFonts.manrope600, fontSize = 13.sp, color = VpnkaColors.TextMuted,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun Err(e: String?) {
    if (e != null) { Text(e, fontSize = 13.sp, color = VpnkaColors.Warning); Spacer(Modifier.height(6.dp)) }
}

@Composable
private fun Field(label: String, value: String, password: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label, color = VpnkaColors.TextMuted) },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        textStyle = LocalTextStyle.current.copy(color = VpnkaColors.TextStrong, fontSize = 15.sp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = VpnkaColors.TextStrong, unfocusedTextColor = VpnkaColors.TextStrong,
            cursorColor = VpnkaColors.Accent, focusedBorderColor = VpnkaColors.Accent,
            unfocusedBorderColor = VpnkaColors.TextFaint, focusedLabelColor = VpnkaColors.Accent,
            unfocusedLabelColor = VpnkaColors.TextMuted,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Primary(text: String, busy: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(VpnkaColors.Accent).clickable(enabled = !busy, onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontFamily = VpnkaFonts.nunito800, fontSize = 15.sp, color = Color.White) }
}
