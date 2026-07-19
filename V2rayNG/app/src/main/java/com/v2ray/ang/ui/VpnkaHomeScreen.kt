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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.handler.VpnkaAccount

/** One entry in the server picker. */
data class VpnkaServerOption(
    val guid: String,
    val name: String,
    val delay: String,
)

/**
 * The screen VPNka opens on: connect, pick an exit, check for updates.
 *
 * v2rayNG's own screen is a power tool — per-app rules, routing, latency
 * tests, import menus. Nearly all of our users want none of it. This is a new
 * screen placed in front of theirs rather than a rewrite of it, so taking
 * upstream releases stays a matter of merging their files unchanged.
 */
@Composable
fun VpnkaHomeScreen(
    isRunning: Boolean,
    isLoading: Boolean,
    isTesting: Boolean,
    servers: List<VpnkaServerOption>,
    selectedGuid: String?,
    onToggle: () -> Unit,
    onSelectServer: (String) -> Unit,
    onRefreshSubscription: () -> Unit,
    onSpeedTest: () -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSubscription: () -> Unit,
    updateVersion: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = servers.firstOrNull { it.guid == selectedGuid }
    val connectedColor = Color(0xFF2E7D32)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            text = "VPNka",
            fontSize = 26.sp,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.onSurface,
        )

        // Shown when the launch-time check found something. A banner rather
        // than a dialog: an update is worth telling someone about, but not
        // worth standing between them and the connect button.
        if (updateVersion != null) {
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(onClick = onCheckUpdate)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "Доступно обновление $updateVersion — нажмите, чтобы установить",
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Box(
            modifier = Modifier
                .size(190.dp)
                .clip(CircleShape)
                .background(
                    if (isRunning) connectedColor
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                // The whole circle is the target, not an icon inside it: this
                // is the one control on the screen and should be impossible
                // to miss.
                .clickable(enabled = !isLoading, onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(44.dp),
                )
            } else {
                Text(
                    text = if (isRunning) "Отключить" else "Подключить",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    color = if (isRunning) Color.White
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = if (isRunning) "Защищено" else "Не подключено",
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            color = if (isRunning) connectedColor
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(28.dp))

        // Current exit, tap to expand. Collapsed by default: «Авто» is the
        // right answer for almost everyone, and a list of ten servers on the
        // first screen is the clutter this screen exists to remove.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(enabled = servers.isNotEmpty()) { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = selected?.name
                        ?: if (servers.isEmpty()) "Загрузка серверов…" else "Выбрать сервер",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (expanded) "▲" else "▼",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        // Right under the picker, because that's the list it refills. Without
        // it the only way to re-fetch is the hidden advanced screen — and a
        // stale list is exactly what a user stares at when the server set
        // changed on our side.
        TextButton(onClick = onRefreshSubscription, enabled = !isLoading) {
            Text(if (isLoading) "Обновляем…" else "Обновить список серверов")
        }

        if (expanded && servers.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // Capped so a long subscription can't push the update
                    // button off the screen; the list scrolls inside.
                    .heightIn(max = 260.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                LazyColumn {
                    items(servers, key = { it.guid }) { option ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectServer(option.guid)
                                    expanded = false
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = option.name,
                                    fontSize = 15.sp,
                                    fontWeight = if (option.guid == selectedGuid)
                                        FontWeight.Bold else FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                if (option.delay.isNotBlank()) {
                                    Text(
                                        text = option.delay,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.surface,
                            thickness = 1.dp,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Ping every server and write the result into the list above, so the
        // picker stops being a list of names and becomes a list of choices.
        TextButton(onClick = onSpeedTest, enabled = !isTesting) {
            Text(if (isTesting) "Проверяем…" else "Тест скорости")
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TextButton(onClick = onOpenSubscription) { Text("Подписка") }
            TextButton(onClick = onOpenSettings) { Text("Настройки") }
            TextButton(onClick = onCheckUpdate) { Text("Обновление") }
        }
    }
}

/**
 * A short settings list — the few things a VPNka user actually changes.
 *
 * Every row opens a screen v2rayNG already has; none of this is new
 * behaviour, it's about reach. The full upstream UI is the last row rather
 * than the default, and rather than the long-press it used to be: hiding it
 * behind a gesture meant "how do I get to X" had no answer you could give
 * over the phone.
 */
@Composable
fun VpnkaSettingsScreen(
    onPerAppProxy: () -> Unit,
    batteryExempt: Boolean,
    onFixBattery: () -> Unit,
    onRoutingSettings: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Настройки",
            fontSize = 24.sp,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(24.dp))

        // First row when it's not granted: this is the setting behind most
        // "VPN keeps dropping" reports, and it's invisible from inside the
        // app until someone goes looking for it.
        VpnkaSettingsRow(
            title = if (batteryExempt) "Работа в фоне: разрешена"
            else "Работа в фоне: ограничена",
            subtitle = if (batteryExempt)
                "Android не усыпляет приложение — соединение держится"
            else "Android может усыплять приложение и обрывать VPN. Нажмите, чтобы разрешить",
            onClick = onFixBattery,
        )
        VpnkaSettingsRow(
            title = "Приложения через VPN",
            subtitle = "Выбрать, каким приложениям идти через VPN, а каким напрямую",
            onClick = onPerAppProxy,
        )
        VpnkaSettingsRow(
            title = "Маршрутизация",
            subtitle = "Какие сайты идут напрямую, минуя VPN",
            onClick = onRoutingSettings,
        )
        VpnkaSettingsRow(
            title = "Расширенный режим",
            subtitle = "Полный интерфейс: подписки, импорт, журнал",
            onClick = onOpenAdvanced,
        )

        Spacer(Modifier.weight(1f))
        TextButton(onClick = onBack) { Text("← Назад") }
    }
}

@Composable
private fun VpnkaSettingsRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
    ) {
        Text(
            text = title,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = subtitle,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * «Профиль» — the account, its subscriptions, and the way in and out of it.
 *
 * Signing in is deliberately a code typed by hand rather than a password.
 * There are no passwords on this service: the bot *is* the account, and the
 * shortest honest bridge from a Telegram identity to a phone is a code shown
 * in one and typed into the other. It buys the thing that matters — a
 * credential belonging to this install alone, which the user can revoke from
 * the bot without disturbing their other devices.
 *
 * Everything that moves money stays in the bot, where payment and refunds
 * already work; a second payment flow here would be a second place for money
 * to go wrong. So this screen states facts and hands off for anything else.
 */
@Composable
fun VpnkaSubscriptionScreen(
    loading: Boolean,
    signedIn: Boolean,
    signingIn: Boolean,
    signInError: String?,
    info: VpnkaAccount.Info?,
    onSignIn: (String) -> Unit,
    onSignOut: () -> Unit,
    onGetCode: () -> Unit,
    onRenew: () -> Unit,
    onSupport: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Профиль",
            fontSize = 24.sp,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(20.dp))

        if (!signedIn) {
            VpnkaSignIn(
                signingIn = signingIn,
                error = signInError,
                onSignIn = onSignIn,
                onGetCode = onGetCode,
            )
        } else {
            when {
                loading -> CircularProgressIndicator(modifier = Modifier.size(32.dp))

                info == null -> {
                    Text(
                        text = "Не удалось получить данные — проверьте интернет",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onRetry) { Text("Повторить") }
                }

                !info.active -> {
                    Text(
                        text = "Подписка не активна",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    info.balance?.let {
                        Spacer(Modifier.height(12.dp))
                        VpnkaInfoRow("Баланс", "${it.substringBefore('.')} ₽")
                    }
                }

                else -> {
                    // Every live purchase, not just the newest. Some accounts
                    // hold more than one, and showing a single subscription
                    // leaves the user counting days on one they aren't using.
                    val plans = info.subscriptions.orEmpty()
                    if (plans.size > 1) {
                        plans.forEachIndexed { index, plan ->
                            if (index > 0) Spacer(Modifier.height(4.dp))
                            VpnkaPlanCard(plan)
                        }
                    } else {
                        VpnkaInfoRow(
                            "Состояние",
                            if (info.frozen) "Заморожена" else "Активна",
                        )
                        info.daysLeft?.let {
                            VpnkaInfoRow("Осталось", "$it ${pluralDays(it)}")
                        }
                        info.tariff?.let { VpnkaInfoRow("Тариф", it) }
                        if (info.devicesLimit != null) {
                            VpnkaInfoRow(
                                "Устройства",
                                "${info.devicesUsed ?: 0} из ${info.devicesLimit}",
                            )
                        }
                    }
                    info.balance?.let {
                        VpnkaInfoRow("Баланс", "${it.substringBefore('.')} ₽")
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        TextButton(onClick = onRenew) { Text("Продлить в боте") }
        TextButton(onClick = onSupport) { Text("Связаться с оператором") }
        if (signedIn) {
            TextButton(onClick = onSignOut) { Text("Выйти из аккаунта") }
        } else {
            // Signed out means running on the shipped 24h trial: say what
            // they're actually on, so nothing above reads as a fault.
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Сейчас работает пробный доступ на сутки.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onBack) { Text("← Назад") }
    }
}

@Composable
private fun VpnkaSignIn(
    signingIn: Boolean,
    error: String?,
    onSignIn: (String) -> Unit,
    onGetCode: () -> Unit,
) {
    var code by remember { mutableStateOf("") }

    Text(
        text = "Войдите, чтобы видеть свои подписки здесь и в боте, " +
            "и чтобы это устройство можно было отключить отдельно.",
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(20.dp))

    OutlinedTextField(
        value = code,
        // The code is six characters from a fixed alphabet, so anything
        // longer is a typo rather than input worth keeping. Upper-casing
        // here — not just on send — means the field shows the user exactly
        // what the bot showed them.
        onValueChange = { code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(6) },
        label = { Text("Код из бота") },
        singleLine = true,
        enabled = !signingIn,
        modifier = Modifier.fillMaxWidth(),
    )

    if (error != null) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = error,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.error,
        )
    }

    Spacer(Modifier.height(16.dp))
    Button(
        onClick = { onSignIn(code) },
        enabled = code.length == 6 && !signingIn,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (signingIn) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text("Войти")
        }
    }
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = onGetCode, modifier = Modifier.fillMaxWidth()) {
        Text("Получить код в боте")
    }
}

@Composable
private fun VpnkaPlanCard(plan: VpnkaAccount.Plan) {
    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        Text(
            text = plan.tariff ?: "Подписка",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        val days = plan.daysLeft
        val devices = if (plan.devicesLimit != null) {
            " · ${plan.devicesUsed ?: 0}/${plan.devicesLimit} устройств"
        } else {
            ""
        }
        Text(
            text = buildString {
                if (plan.frozen) append("заморожена") else if (days != null) {
                    append("$days ${pluralDays(days)}")
                } else {
                    append("активна")
                }
                append(devices)
            },
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun pluralDays(n: Int): String {
    val a = kotlin.math.abs(n)
    return when {
        a % 10 == 1 && a % 100 != 11 -> "день"
        a % 10 in 2..4 && a % 100 !in 12..14 -> "дня"
        else -> "дней"
    }
}

@Composable
private fun VpnkaInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
