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
 * «Моя подписка» — what the bot's card shows, without leaving the app.
 *
 * Read-only by design. Renewal, payment methods and refunds live in the
 * bot, where they already work; duplicating a payment flow here would mean
 * a second place for money to go wrong. So this states the facts and hands
 * off for anything that changes them.
 */
@Composable
fun VpnkaSubscriptionScreen(
    loading: Boolean,
    info: VpnkaAccount.Info?,
    hasSubscription: Boolean,
    onRenew: () -> Unit,
    onSupport: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Моя подписка",
            fontSize = 24.sp,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(20.dp))

        when {
            loading -> {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }

            // Never went through the bot: they're on the shipped trial. Say
            // so plainly instead of "не активна", which reads as a fault.
            !hasSubscription -> {
                Text(
                    text = "Сейчас работает пробный доступ на сутки.\n" +
                        "Месяц бесплатно — в боте.",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

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
            }

            else -> {
                val days = info.daysLeft
                VpnkaInfoRow(
                    "Состояние",
                    if (info.frozen) "Заморожена" else "Активна",
                )
                if (days != null) {
                    VpnkaInfoRow("Осталось", "$days ${pluralDays(days)}")
                }
                info.tariff?.let { VpnkaInfoRow("Тариф", it) }
                if (info.devicesLimit != null) {
                    VpnkaInfoRow(
                        "Устройства",
                        "${info.devicesUsed ?: 0} из ${info.devicesLimit}",
                    )
                }
                info.balance?.let { raw ->
                    // Comes as a decimal string from the API; show whole
                    // roubles — twelve decimal places is accounting detail,
                    // not something a user needs on this screen.
                    val whole = raw.substringBefore('.')
                    VpnkaInfoRow("Баланс", "$whole ₽")
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        TextButton(onClick = onRenew) { Text("Продлить в боте") }
        TextButton(onClick = onSupport) { Text("Связаться с оператором") }

        Spacer(Modifier.weight(1f))
        TextButton(onClick = onBack) { Text("← Назад") }
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
