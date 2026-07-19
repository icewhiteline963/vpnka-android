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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
/** One subscription the account holds, as the home picker shows it. */
data class VpnkaSubOption(val guid: String, val name: String)

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
    subscriptions: List<VpnkaSubOption> = emptyList(),
    selectedSubGuid: String? = null,
    onSelectSubscription: (String) -> Unit = {},
    updateVersion: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    var subExpanded by remember { mutableStateOf(false) }
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
            fontWeight = VpnkaWeight.Extra,
            color = VpnkaColors.TextStrong,
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
                    .background(VpnkaColors.CardServer)
                    .clickable(onClick = onCheckUpdate)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "Доступно обновление $updateVersion — нажмите, чтобы установить",
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    color = VpnkaColors.TextStrong,
                )
            }
        }

        // Which plan is carrying the traffic, above the button that starts
        // it. Only shown when there is a real choice: a single subscription
        // needs no picker, and the trial-only case is already explained on
        // the profile screen.
        if (subscriptions.size > 1) {
            Spacer(Modifier.height(20.dp))
            val currentSub = subscriptions.firstOrNull { it.guid == selectedSubGuid }
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(VpnkaColors.CardSpeed)
                        .clickable { subExpanded = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = currentSub?.name ?: "Подписка",
                        fontSize = 14.sp,
                        color = VpnkaColors.TextStrong,
                    )
                    Text(
                        text = "▾",
                        fontSize = 14.sp,
                        color = VpnkaColors.TextMuted,
                    )
                }
                DropdownMenu(
                    expanded = subExpanded,
                    onDismissRequest = { subExpanded = false },
                ) {
                    subscriptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.name, fontSize = 14.sp) },
                            onClick = {
                                subExpanded = false
                                onSelectSubscription(option.guid)
                            },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        Box(
            modifier = Modifier
                .size(190.dp)
                // The whole shape is the target, not an icon inside it: this
                // is the one control on the screen and should be impossible
                // to miss. Same size and same position as the plain circle
                // it replaced — the picture changed, not what the user has
                // to work out.
                .clip(CircleShape)
                .clickable(enabled = !isLoading, onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            VpnkaFlower(
                isRunning = isRunning,
                isLoading = isLoading,
                modifier = Modifier.fillMaxSize(),
            )
            if (!isLoading) {
                Text(
                    text = if (isRunning) "Отключить" else "Подключить",
                    fontSize = 21.sp,
                    fontWeight = VpnkaWeight.Extra,
                    textAlign = TextAlign.Center,
                    // Always the surface colour, never white. White worked
                    // on the solid green circle this replaced; on a
                    // translucent flower the page shows through, so in the
                    // light theme white text would sit on a near-white
                    // background and vanish.
                    color = VpnkaColors.TextStrong,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = if (isRunning) "Защищено" else "Не подключено",
            fontSize = 17.sp,
            fontWeight = VpnkaWeight.Extra,
            color = if (isRunning) connectedColor
            else VpnkaColors.TextMuted,
        )

        Spacer(Modifier.height(28.dp))

        // Current exit, tap to expand. Collapsed by default: «Авто» is the
        // right answer for almost everyone, and a list of ten servers on the
        // first screen is the clutter this screen exists to remove.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(VpnkaColors.CardSpeed)
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
                    color = VpnkaColors.TextStrong,
                )
                Text(
                    text = if (expanded) "▲" else "▼",
                    fontSize = 13.sp,
                    color = VpnkaColors.TextMuted,
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
                    .background(VpnkaColors.CardSpeed),
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
                                    color = VpnkaColors.TextStrong,
                                )
                                if (option.delay.isNotBlank()) {
                                    Text(
                                        text = option.delay,
                                        fontSize = 13.sp,
                                        color = VpnkaColors.TextMuted,
                                    )
                                }
                            }
                        }
                        HorizontalDivider(
                            color = androidx.compose.ui.graphics.Color.White,
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
    notificationsEnabled: Boolean,
    onFixNotifications: () -> Unit,
    onRoutingSettings: () -> Unit,
    onCheckUpdate: () -> Unit,
    onBack: () -> Unit,
) {
    VpnkaPage(title = "Настройки", onBack = onBack) {
        Column(modifier = Modifier.fillMaxSize()) {
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
        // Asked for once at first launch, and silently declined by many.
        // Without it the expiry reminder never arrives, and the first sign
        // of the subscription ending is a connection that stops.
        VpnkaSettingsRow(
            title = if (notificationsEnabled) "Уведомления: включены"
            else "Уведомления: выключены",
            subtitle = if (notificationsEnabled)
                "Напомним за 3 дня и за сутки до конца подписки"
            else "Без них не придёт напоминание об окончании подписки. Нажмите, чтобы включить",
            onClick = onFixNotifications,
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
            title = "Проверить обновление",
            subtitle = "Скачать и установить свежую версию",
            onClick = onCheckUpdate,
        )

        Spacer(Modifier.weight(1f))
        }
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
            color = VpnkaColors.TextStrong,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = subtitle,
            fontFamily = VpnkaFonts.manrope600,
            fontWeight = VpnkaWeight.Semi,
            fontSize = 14.sp,
            color = VpnkaColors.TextMuted,
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
    onTopUp: () -> Unit,
    onShowRecovery: () -> Unit,
    onOpenSettings: () -> Unit,
    onLinkTelegram: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    var showSignIn by remember { mutableStateOf(false) }

    VpnkaPage(title = "Профиль", onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {

        if (!signedIn || showSignIn) {
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
                        color = VpnkaColors.TextMuted,
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onRetry) { Text("Повторить") }
                }

                !info.active -> {
                    Text(
                        text = "Подписка не активна",
                        fontSize = 17.sp,
                        fontWeight = VpnkaWeight.Extra,
                        color = VpnkaColors.TextMuted,
                    )
                    info.balanceRub?.let {
                        Spacer(Modifier.height(12.dp))
                        VpnkaInfoRow("Баланс", "$it ₽")
                    }
                }

                else -> {
                    // The plans themselves live behind «Мои подписки» on the
                    // main screen. Repeating the list here meant two places
                    // to keep in step and two places to read the same thing.
                    VpnkaInfoRow(
                        "Подписок",
                        "${info.subscriptions.orEmpty().size}",
                    )
                    info.balanceRub?.let {
                        VpnkaInfoRow("Баланс", "$it ₽")
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        // Own rows rather than Material TextButtons. Those carry no colour
        // or size of their own — they take the theme's, which is the small
        // pale grey that kept coming back however many Text() colours were
        // replaced. This was the actual source of it.
        //
        // Always offered, not only when signed out: accounts are created
        // automatically, so someone whose app storage was cleared silently
        // lands in a fresh empty account, and the sign-in form only ever
        // appeared when signed out — which the app never is.
        // Two doors that sound the same and are not, which is exactly how
        // someone with an existing Telegram account ended up being told
        // «не удалось привязать». Sign-in comes first because most people
        // arrive from the bot and already have an account; each says who
        // it is for, in those words.
        VpnkaMenuRow(
            "Войти по коду из бота",
            { showSignIn = true },
            subtitle = "Если аккаунт уже есть в Telegram — подписки и баланс",
        )
        VpnkaMenuRow(
            "Подключить Telegram",
            onLinkTelegram,
            subtitle = "Если аккаунт заведён здесь, в приложении",
        )
        // Buying and topping up both need an account to charge. Without one
        // they lead to the Telegram sign-in rather than to a shop that
        // cannot complete — the destination is the missing step, not a
        // refusal at the end of one.
        VpnkaMenuRow(
            "Купить подписку",
            if (signedIn) onRenew else ({ showSignIn = true }),
            subtitle = if (signedIn) null else "Сначала вход — покупка привязывается к аккаунту",
        )
        VpnkaMenuRow(
            "Пополнить баланс",
            if (signedIn) onTopUp else ({ showSignIn = true }),
            subtitle = if (signedIn) null else "Сначала вход — баланс хранится в аккаунте",
        )
        VpnkaMenuRow("Связаться с оператором", onSupport)
        VpnkaMenuRow("Настройки приложения", onOpenSettings)
        VpnkaMenuRow("Код восстановления", onShowRecovery)
        if (signedIn) {
            TextButton(onClick = onSignOut) { Text("Выйти из аккаунта") }
        } else {
            // Signed out means running on the shipped 24h trial: say what
            // they're actually on, so nothing above reads as a fault.
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Сейчас работает пробный доступ на сутки.",
                fontSize = 13.sp,
                color = VpnkaColors.TextMuted,
            )
        }

        }
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
        color = VpnkaColors.TextMuted,
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
            color = VpnkaColors.Warning,
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
                color = androidx.compose.ui.graphics.Color.White,
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
            fontFamily = VpnkaFonts.nunito800,
            fontWeight = VpnkaWeight.Extra,
            fontSize = 17.sp,
            color = VpnkaColors.TextStrong,
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
            color = VpnkaColors.TextMuted,
        )
    }
}

internal fun pluralDays(n: Int): String {
    val a = kotlin.math.abs(n)
    return when {
        a % 10 == 1 && a % 100 != 11 -> "день"
        a % 10 in 2..4 && a % 100 !in 12..14 -> "дня"
        else -> "дней"
    }
}

@Composable
private fun VpnkaInfoRow(label: String, value: String) {
    // Was 15sp in the theme's onSurfaceVariant — a pale grey that came from
    // Material's palette rather than this design's, and read as disabled
    // text on the warm background. Bigger and in the page's own darkest ink.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            fontFamily = VpnkaFonts.manrope600,
            fontWeight = VpnkaWeight.Semi,
            fontSize = 16.sp,
            color = VpnkaColors.TextMuted,
        )
        Text(
            text = value,
            fontFamily = VpnkaFonts.nunito800,
            fontWeight = VpnkaWeight.Extra,
            fontSize = 17.sp,
            color = VpnkaColors.TextStrong,
        )
    }
}

/**
 * «Купить подписку» — the shop, without leaving the app.
 *
 * Prices arrive already converted to roubles and already discounted for
 * this account, so nothing here recomputes money. The two buttons are
 * whatever the server says this client can actually use: balance when they
 * have enough, card when the amount clears RuKassa's floor. Offering a
 * button that the purchase call would then refuse is worse than not
 * offering it.
 */
@Composable
fun VpnkaShopScreen(
    loading: Boolean,
    buying: Boolean,
    tariffs: List<VpnkaAccount.Tariff>,
    error: String?,
    onBuy: (Int, String) -> Unit,
    onTopUp: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    VpnkaPage(title = "Купить подписку", onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {

        when {
            loading -> CircularProgressIndicator(modifier = Modifier.size(32.dp))

            tariffs.isEmpty() -> {
                Text(
                    text = "Не удалось загрузить тарифы — проверьте интернет",
                    fontSize = 15.sp,
                    color = VpnkaColors.TextMuted,
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onRetry) { Text("Повторить") }
            }

            else -> {
                if (error != null) {
                    Text(
                        text = error,
                        fontSize = 13.sp,
                        color = VpnkaColors.Warning,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                tariffs.forEach { tariff ->
                    VpnkaTariffCard(tariff, buying, onBuy, onTopUp)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        }
    }
}

@Composable
private fun VpnkaTariffCard(
    tariff: VpnkaAccount.Tariff,
    buying: Boolean,
    onBuy: (Int, String) -> Unit,
    onTopUp: () -> Unit,
) {
    // Compact by design: name and price share the top line, the terms sit
    // under them as one quiet line, and the actions are plain tappable text
    // rather than Material buttons — those brought their own height, ripple
    // and padding, which is what made these cards tower over everything else
    // on the screen.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(VpnkaColors.CardSpeed)
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = tariff.name,
                fontFamily = VpnkaFonts.nunito800,
                fontWeight = VpnkaWeight.Extra,
                fontSize = 15.sp,
                color = VpnkaColors.TextStrong,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${tariff.priceRub} ₽",
                fontFamily = VpnkaFonts.nunito800,
                fontWeight = VpnkaWeight.Extra,
                fontSize = 15.sp,
                color = VpnkaColors.TextStrong,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = buildString {
                append("${tariff.durationDays} дн · ${tariff.deviceLimit} устр.")
                // The full price only appears when a friend discount is live,
                // so the discount is visible rather than merely applied.
                tariff.priceRubFull?.let { append(" · вместо $it ₽") }
            },
            fontSize = 12.sp,
            color = VpnkaColors.TextMuted,
        )
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (tariff.canPayBalance) {
                VpnkaTariffAction("С баланса", !buying) {
                    onBuy(tariff.id, "balance")
                }
                Spacer(Modifier.width(18.dp))
            }
            if (tariff.canPayCard) {
                VpnkaTariffAction("Картой", !buying) { onBuy(tariff.id, "card") }
            }
            // Neither route is open: too little balance and too small an
            // amount for the processor. Say what would fix it instead of
            // showing a card with no way forward.
            if (!tariff.canPayBalance && !tariff.canPayCard) {
                VpnkaTariffAction("Пополнить баланс", true, onTopUp)
            }
        }
    }
}

@Composable
private fun VpnkaTariffAction(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        fontFamily = VpnkaFonts.nunito800,
        fontWeight = VpnkaWeight.Extra,
        fontSize = 13.sp,
        color = if (enabled) VpnkaColors.Accent else VpnkaColors.TextFaint,
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 2.dp),
    )
}

/**
 * «Поддержка» — the same ticket an operator answers, without Telegram.
 *
 * Messages are the ticket's own rows, not a copy: an agent sees one
 * conversation whether the client is typing here or in the bot, and nothing
 * has to be kept in sync between two stores.
 */
@Composable
fun VpnkaSupportScreen(
    loading: Boolean,
    sending: Boolean,
    messages: List<VpnkaAccount.SupportMessage>,
    onSend: (String) -> Unit,
    onBack: () -> Unit,
) {
    var draft by remember { mutableStateOf("") }

    VpnkaPage(title = "Поддержка", onBack = onBack) {
        Column(modifier = Modifier.fillMaxSize()) {

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading && messages.isEmpty() ->
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))

                messages.isEmpty() -> Text(
                    text = "Напишите, что случилось — оператор ответит здесь.",
                    fontSize = 14.sp,
                    color = VpnkaColors.TextMuted,
                )

                else -> LazyColumn {
                    items(messages) { message ->
                        VpnkaBubble(message)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it.take(4000) },
            label = { Text("Сообщение") },
            enabled = !sending,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                onSend(draft)
                draft = ""
            },
            enabled = draft.isNotBlank() && !sending,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (sending) "Отправляем…" else "Отправить") }

        }
    }
}

@Composable
private fun VpnkaBubble(message: VpnkaAccount.SupportMessage) {
    val mine = message.fromMe
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (mine) VpnkaColors.CardServer
                    else VpnkaColors.CardSpeed
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = message.body,
                fontSize = 14.sp,
                color = if (mine) VpnkaColors.TextStrong
                else VpnkaColors.TextStrong,
            )
        }
    }
}

/**
 * «Пополнить баланс» — a card payment that credits the balance.
 *
 * The balance is what lets a purchase be one tap and no browser. Amounts are
 * fixed rather than free-typed: RuKassa has a floor, and a field that lets
 * someone enter 50 ₽ only to be refused is a worse experience than three
 * buttons that all work.
 */
@Composable
fun VpnkaTopUpScreen(
    busy: Boolean,
    error: String?,
    onTopUp: (Int) -> Unit,
    onBack: () -> Unit,
) {
    VpnkaPage(title = "Пополнить баланс", onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
        Text(
            text = "Оплата картой или через СБП. С баланса подписка покупается " +
                "в одно нажатие.",
            fontSize = 14.sp,
            color = VpnkaColors.TextMuted,
        )
        Spacer(Modifier.height(20.dp))

        listOf(300, 500, 1000, 2000).forEach { amount ->
            Button(
                onClick = { onTopUp(amount) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("$amount ₽") }
            Spacer(Modifier.height(8.dp))
        }

        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error, fontSize = 13.sp, color = VpnkaColors.Warning)
        }

        }
    }
}

/**
 * The recovery code, and the one moment it is worth reading.
 *
 * Shown on demand rather than on first launch: a code presented to someone
 * who has nothing yet to lose is a code nobody writes down. The wording is
 * blunt because the consequence is — the server keeps only a hash, so if
 * this is lost with the phone, the account is unreachable.
 */
@Composable
fun VpnkaRecoveryScreen(code: String?, onBack: () -> Unit) {
    VpnkaPage(title = "Код восстановления", onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {

        if (code == null) {
            Text(
                text = "Код недоступен на этом устройстве. Он выдаётся один раз " +
                    "при создании аккаунта — если вы вошли по коду с другого " +
                    "телефона, используйте тот же код.",
                fontSize = 14.sp,
                color = VpnkaColors.TextMuted,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(VpnkaColors.CardSpeed)
                    .padding(20.dp),
            ) {
                Text(
                    text = code,
                    fontSize = 22.sp,
                    fontWeight = VpnkaWeight.Extra,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    color = VpnkaColors.TextStrong,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Запишите его. Это единственный способ вернуть подписку, " +
                    "если телефон потеряется или приложение будет переустановлено — " +
                    "у нас код не хранится, только его отпечаток.",
                fontSize = 14.sp,
                color = VpnkaColors.TextMuted,
            )
        }

        }
    }
}


/** A menu line in the app's own type, not Material's defaults. */
@Composable
private fun VpnkaMenuRow(
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = VpnkaFonts.nunito800,
                fontWeight = VpnkaWeight.Extra,
                fontSize = 17.sp,
                color = VpnkaColors.TextStrong,
            )
            // Only where the title alone leaves a real question open — two
            // rows that sound alike, or one whose effect isn't obvious. A
            // subtitle under every row is noise, and noise is what stops
            // the ones that matter from being read.
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = VpnkaColors.TextMuted,
                )
            }
        }
        Text(
            text = "›",
            fontSize = 18.sp,
            color = VpnkaColors.TextFaint,
        )
    }
}
