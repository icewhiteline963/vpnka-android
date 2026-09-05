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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.v2ray.ang.handler.MmkvManager
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
            Text(if (isLoading) "Обновляем…" else "Обновить подписку и серверы")
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
    onNotificationSettings: () -> Unit,
    onCheckUpdate: () -> Unit,
    smartDeskEligible: Boolean = false,
    smartDeskHidden: Boolean = false,
    onSmartDeskHiddenChange: (Boolean) -> Unit = {},
    betaChannel: Boolean = false,
    onBetaChannelChange: (Boolean) -> Unit = {},
    onBack: () -> Unit,
) {
    VpnkaPage(title = "Настройки", onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
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
            title = "Напоминание о подписке",
            subtitle = "Где напоминать о конце подписки — в приложении и/или в Telegram — и email для связи",
            onClick = onNotificationSettings,
        )
        VpnkaSettingsRow(
            title = "Приложения через VPN",
            subtitle = "Выбрать, каким приложениям идти через VPN, а каким напрямую",
            onClick = onPerAppProxy,
        )

        VpnkaSettingsRow(
            title = "Проверить обновление",
            subtitle = "Скачать и установить свежую версию",
            onClick = onCheckUpdate,
        )
        // Тестовые сборки — по желанию и ТОЛЬКО поверх обычной.
        //
        // Проверять правки раньше можно было только на боевом приложении:
        // сборка уезжала всем клиентам, а на своём телефоне оставалась
        // навсегда — понизить версию Android не даёт. Теперь тестовые версии
        // приходят обычным обновлением тому, кто их попросил, и никому
        // больше.
        NotifyToggleRow(
            title = "Тестовые версии",
            subtitle = if (betaChannel)
                "Приходят раньше остальных. Могут быть недоделаны — если что-то сломалось, выключите и дождитесь обычной."
            else
                "Получать сборки до того, как они уйдут всем. По умолчанию выключено.",
            checked = betaChannel,
            enabled = true,
            onCheckedChange = onBetaChannelChange,
        )

        if (smartDeskEligible) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Безопасность",
                fontSize = 13.sp,
                fontFamily = VpnkaFonts.manrope600,
                fontWeight = VpnkaWeight.Semi,
                color = VpnkaColors.TextMuted,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
            NotifyToggleRow(
                title = "Скрыть VPNka облако",
                subtitle = "Раздел исчезнет из меню. Чтобы показать снова — потапайте 5 раз в правый нижний угол экрана.",
                checked = smartDeskHidden,
                enabled = true,
                onCheckedChange = onSmartDeskHiddenChange,
            )
        }

        Spacer(Modifier.height(24.dp))
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

/** Expiry-reminder channels + contact email. Backed by GET /app/profile and
 *  PATCH /app/settings; the Telegram toggle is disabled until a Telegram is
 *  linked, since there is nowhere to send otherwise. */
@Composable
fun VpnkaNotificationsScreen(
    inApp: Boolean,
    inTelegram: Boolean,
    telegramLinked: Boolean,
    email: String,
    saving: Boolean,
    onInApp: (Boolean) -> Unit,
    onInTelegram: (Boolean) -> Unit,
    onEmail: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    VpnkaPage(title = "Напоминание о подписке", onBack = onBack) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Напомним за 3 дня и за сутки до конца подписки. Выберите, куда:",
                fontFamily = VpnkaFonts.manrope600,
                fontWeight = VpnkaWeight.Semi,
                fontSize = 14.sp,
                color = VpnkaColors.TextMuted,
            )
            Spacer(Modifier.height(20.dp))
            NotifyToggleRow(
                title = "В приложении",
                subtitle = "Уведомление внутри приложения",
                checked = inApp,
                enabled = true,
                onCheckedChange = onInApp,
            )
            NotifyToggleRow(
                title = "В Telegram",
                subtitle = if (telegramLinked) "Сообщение от нашего бота"
                else "Сначала привяжите Telegram в профиле",
                checked = inTelegram && telegramLinked,
                enabled = telegramLinked,
                onCheckedChange = onInTelegram,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Email для связи (необязательно)",
                fontSize = 16.sp,
                color = VpnkaColors.TextStrong,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = email,
                onValueChange = onEmail,
                singleLine = true,
                placeholder = { Text("you@example.com") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onSave,
                enabled = !saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
            ) {
                Text(if (saving) "Сохранение…" else "Сохранить")
            }
        }
    }
}

@Composable
private fun NotifyToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 16.sp, color = VpnkaColors.TextStrong)
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontFamily = VpnkaFonts.manrope600,
                fontWeight = VpnkaWeight.Semi,
                fontSize = 14.sp,
                color = VpnkaColors.TextMuted,
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = androidx.compose.ui.graphics.Color(0xFFE8850C),
            ),
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
    telegramLinked: Boolean,
    signingIn: Boolean,
    signInError: String?,
    info: VpnkaAccount.Info?,
    onSignIn: (String) -> Unit,
    onSignOut: () -> Unit,
    onGetCode: () -> Unit,
    onRenew: () -> Unit,
    onBuyInApp: () -> Unit,
    onSupport: () -> Unit,
    onTopUp: () -> Unit,
    onShowRecovery: () -> Unit,
    onOpenSettings: () -> Unit,
    onLinkTelegram: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    var showSignIn by remember { mutableStateOf(false) }
    // A bot code logs into a Telegram account, so a successful sign-in flips
    // telegramLinked. Close the form on that: signedIn is already true for
    // everyone (accounts auto-create), so nothing else would dismiss it and
    // the user stared at the code field after logging in.
    LaunchedEffect(telegramLinked) {
        if (telegramLinked) showSignIn = false
    }

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
        //
        // One door, not two. «Войти в аккаунт» and «Подключить Telegram» read
        // as the same thing and differed only by which side already held the
        // account — a distinction a user can't make about themselves. Now a
        // single «Подключить Telegram» opens the bot and the backend decides:
        // it attaches when the Telegram side is empty, or hands back a login
        // code when the account already lives there (link_telegram →
        // existing_account). The code entry stays as a quiet secondary link —
        // for that returned code, and for phones that can't open Telegram.
        //
        // Hidden once a Telegram is attached: the whole question is answered.
        // The test is telegramLinked, not signedIn — the app makes an account
        // on first launch, so signedIn is true for everyone.
        if (!telegramLinked) {
            VpnkaMenuRow(
                "Подключить Telegram",
                onLinkTelegram,
                subtitle = "Общий аккаунт с ботом: подписки, баланс и устройства вместе",
            )
            Text(
                text = "Уже есть код из бота? Ввести",
                fontFamily = VpnkaFonts.manrope600,
                fontWeight = VpnkaWeight.Semi,
                fontSize = 13.sp,
                color = VpnkaColors.Accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showSignIn = true }
                    .padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
        // Buying in the app credits this account directly, so it's offered to
        // everyone — including an app-only account with no Telegram.
        VpnkaMenuRow("Купить подписку в приложении", onBuyInApp)
        // Buying in the bot, on the other hand, is credited to the Telegram
        // account: an unlinked user would pay there and find this app still on
        // an unpaid account. So the Telegram buy and the bot top-up are shown
        // only once Telegram is attached.
        if (telegramLinked) {
            VpnkaMenuRow("Купить подписку в Telegram", onRenew)
            VpnkaMenuRow("Пополнить баланс", onTopUp)
        }
        VpnkaMenuRow("Связаться с оператором", onSupport)
        VpnkaMenuRow("Настройки приложения", onOpenSettings)
        // Only for an app-only account: the recovery code is its single way
        // back if the app is lost. Once Telegram is attached, recovery goes
        // through Telegram instead, so the code is noise — hide it.
        if (!telegramLinked) {
            VpnkaMenuRow("Код восстановления", onShowRecovery)
        }
        VpnkaMenuRow(
            if (VpnkaColors.dark) "Светлая тема" else "Тёмная тема",
            {
                VpnkaColors.dark = !VpnkaColors.dark
                // «Тёмная» — это и есть палитра макета «Поток». Светлая
                // остаётся прежней, тёплой: переключатель гасит «Поток», а
                // не подменяет его вторым тёмным набором.
                VpnkaColors.flow = VpnkaColors.dark
                MmkvManager.setDarkTheme(VpnkaColors.dark)
            },
            subtitle = if (VpnkaColors.dark) "Вернуть тёплое оформление"
            else "Тёмное оформление для вечера",
        )
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
        // Поле принимает ОБА кода: короткий вход из бота (6 знаков) и код
        // восстановления (16 знаков четвёрками).
        //
        // Раньше здесь стояло `.take(6)`, а кнопка включалась ровно на шести
        // знаках — то есть код восстановления невозможно было даже набрать,
        // хотя экран прямо называет его «единственным способом вернуть
        // подписку, если телефон потеряется». И сама функция восстановления
        // не вызывалась ниоткуда.
        onValueChange = { code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(16) },
        label = { Text("Код из бота или код восстановления", color = VpnkaColors.TextMuted) },
        singleLine = true,
        enabled = !signingIn,
        // Without explicit colours the field takes Material's palette, not
        // this design's, and the typed code came out near-invisible on the
        // warm background — the same fix the message and top-up fields got.
        textStyle = LocalTextStyle.current.copy(
            color = VpnkaColors.TextStrong,
            fontSize = 15.sp,
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = VpnkaColors.TextStrong,
            unfocusedTextColor = VpnkaColors.TextStrong,
            disabledTextColor = VpnkaColors.TextMuted,
            cursorColor = VpnkaColors.Accent,
            focusedBorderColor = VpnkaColors.Accent,
            unfocusedBorderColor = VpnkaColors.TextFaint,
            focusedLabelColor = VpnkaColors.Accent,
            unfocusedLabelColor = VpnkaColors.TextMuted,
        ),
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
        enabled = (code.length == 6 || code.length == 16) && !signingIn,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = VpnkaColors.Accent,
            contentColor = androidx.compose.ui.graphics.Color.White,
            disabledContainerColor = VpnkaColors.Accent.copy(alpha = 0.4f),
            disabledContentColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
        ),
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
    TextButton(
        onClick = onGetCode,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.textButtonColors(contentColor = VpnkaColors.Accent),
    ) {
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
    onSendImage: (ByteArray, String) -> Unit,
    onHistory: () -> Unit,
    onBack: () -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    val context = LocalContext.current

    fun bytesOf(uri: android.net.Uri): ByteArray? = try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (e: Exception) {
        null
    }

    // Pick a screenshot from the gallery.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            bytesOf(uri)?.let { onSendImage(it, mime) }
        }
    }

    // Paste a screenshot straight from the clipboard (works on Android too:
    // a copied image rides the clipboard as a content:// uri).
    fun pasteFromClipboard() {
        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
            as? android.content.ClipboardManager
        val uri = cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
        val mime = uri?.let { context.contentResolver.getType(it) } ?: ""
        if (uri != null && mime.startsWith("image/")) {
            bytesOf(uri)?.let { onSendImage(it, mime); return }
        }
        android.widget.Toast.makeText(
            context, "В буфере нет картинки", android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    VpnkaPage(title = "Поддержка", onBack = onBack) {
        Column(modifier = Modifier.fillMaxSize()) {

        // Past conversations are reachable but not in the way: what someone
        // opening support wants is to type, and history is for the rarer
        // case of going back to an answer they were given.
        VpnkaMenuRow(
            "История обращений",
            onHistory,
            subtitle = "Прошлые вопросы и ответы оператора",
        )
        Spacer(Modifier.height(8.dp))

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
                    itemsIndexed(messages) { i, message ->
                        val ts = parseSupportTs(message.createdAt)
                        val prevTs = messages.getOrNull(i - 1)
                            ?.let { parseSupportTs(it.createdAt) }
                        // A date header at the very top of the conversation and
                        // whenever the day changes between two messages.
                        if (ts != null &&
                            (prevTs == null || prevTs.toLocalDate() != ts.toLocalDate())
                        ) {
                            SupportDateHeader(ts)
                            Spacer(Modifier.height(8.dp))
                        }
                        VpnkaBubble(message, ts)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        // Explicit colours, not the theme's. Material defaults are drawn
        // for a grey surface; on this warm wash the typed text and the label
        // came out barely darker than the background — the same way the
        // profile menu did, and for the same reason.
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it.take(4000) },
            label = { Text("Сообщение", color = VpnkaColors.TextMuted) },
            enabled = !sending,
            textStyle = LocalTextStyle.current.copy(
                color = VpnkaColors.TextStrong,
                fontSize = 15.sp,
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = VpnkaColors.TextStrong,
                unfocusedTextColor = VpnkaColors.TextStrong,
                disabledTextColor = VpnkaColors.TextMuted,
                cursorColor = VpnkaColors.Accent,
                focusedBorderColor = VpnkaColors.Accent,
                unfocusedBorderColor = VpnkaColors.TextFaint,
                focusedLabelColor = VpnkaColors.Accent,
                unfocusedLabelColor = VpnkaColors.TextMuted,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            VpnkaSecondaryButton(
                text = "📎 Из галереи",
                onClick = { picker.launch("image/*") },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            VpnkaSecondaryButton(
                text = "📋 Вставить",
                onClick = { pasteFromClipboard() },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        VpnkaPrimaryButton(
            text = if (sending) "Отправляем…" else "Отправить",
            onClick = {
                onSend(draft)
                draft = ""
            },
            enabled = draft.isNotBlank() && !sending,
        )

        }
    }
}

private val supportTimeFmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
private val supportDateFmt = java.time.format.DateTimeFormatter
    .ofPattern("d MMMM yyyy", java.util.Locale("ru"))

/** Parse the backend's ISO-8601 UTC timestamp into local wall-clock time. */
private fun parseSupportTs(iso: String): java.time.LocalDateTime? {
    if (iso.isBlank()) return null
    return try {
        java.time.OffsetDateTime.parse(iso)
            .atZoneSameInstant(java.time.ZoneId.systemDefault())
            .toLocalDateTime()
    } catch (e: Exception) {
        try {
            java.time.Instant.parse(iso)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDateTime()
        } catch (e2: Exception) {
            null
        }
    }
}

@Composable
private fun SupportDateHeader(ts: java.time.LocalDateTime) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = ts.format(supportDateFmt),
            fontFamily = VpnkaFonts.manrope600,
            fontWeight = VpnkaWeight.Semi,
            fontSize = 11.sp,
            color = VpnkaColors.TextFaint,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(VpnkaColors.CardSpeed)
                .padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun SupportAttachment(ref: String) {
    val bmp by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, ref) {
        val bytes = VpnkaAccount.fetchSupportImage(ref)
        value = bytes?.let {
            runCatching {
                android.graphics.BitmapFactory
                    .decodeByteArray(it, 0, it.size)
                    ?.asImageBitmap()
            }.getOrNull()
        }
    }
    val image = bmp
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = "Скриншот",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .sizeIn(maxWidth = 220.dp, maxHeight = 280.dp)
                .clip(RoundedCornerShape(10.dp)),
        )
    } else {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(VpnkaColors.CardSettings),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun VpnkaBubble(
    message: VpnkaAccount.SupportMessage,
    ts: java.time.LocalDateTime?,
) {
    val mine = message.fromMe
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
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
                Column {
                    message.attachment?.takeIf { it.isNotBlank() }?.let { ref ->
                        SupportAttachment(ref)
                    }
                    // Hide the "📷 Скриншот" placeholder body when it's just the
                    // stand-in for an image with no caption.
                    val showText = message.body.isNotBlank() &&
                        !(message.attachment != null && message.body == "📷 Скриншот")
                    if (showText) {
                        if (message.attachment != null) Spacer(Modifier.height(6.dp))
                        Text(
                            text = message.body,
                            fontSize = 14.sp,
                            color = VpnkaColors.TextStrong,
                        )
                    }
                }
            }
            if (ts != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = ts.format(supportTimeFmt),
                    fontSize = 10.sp,
                    color = VpnkaColors.TextFaint,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
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


/**
 * «История обращений» — every ticket this client opened.
 *
 * The support screen shows one live conversation; a question asked and
 * answered last month used to be unreachable, which made the answer
 * worthless the moment the ticket closed.
 */
@Composable
fun VpnkaTicketsScreen(
    loading: Boolean,
    tickets: List<VpnkaAccount.SupportTicket>,
    onOpen: (VpnkaAccount.SupportTicket) -> Unit,
    onBack: () -> Unit,
) {
    VpnkaPage(title = "История обращений", onBack = onBack) {
        when {
            loading && tickets.isEmpty() ->
                CircularProgressIndicator(modifier = Modifier.size(28.dp))

            tickets.isEmpty() -> Text(
                text = "Вы ещё не обращались в поддержку.",
                fontSize = 14.sp,
                color = VpnkaColors.TextMuted,
            )

            else -> LazyColumn {
                items(tickets) { ticket ->
                    VpnkaChoiceRow(
                        title = ticket.subject.ifBlank { "Обращение #${ticket.id}" },
                        subtitle = buildString {
                            append(ticketStatusLabel(ticket.status))
                            val day = ticket.createdAt.take(10)
                            if (day.isNotBlank()) append(" · $day")
                        },
                        selected = false,
                        onClick = { onOpen(ticket) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

/** One past thread, read-only: it may be closed, and reopening is a new ask. */
@Composable
fun VpnkaTicketThreadScreen(
    subject: String,
    loading: Boolean,
    messages: List<VpnkaAccount.SupportMessage>,
    onBack: () -> Unit,
) {
    VpnkaPage(title = subject.ifBlank { "Обращение" }, onBack = onBack) {
        when {
            loading && messages.isEmpty() ->
                CircularProgressIndicator(modifier = Modifier.size(28.dp))

            messages.isEmpty() -> Text(
                text = "В этом обращении нет сообщений.",
                fontSize = 14.sp,
                color = VpnkaColors.TextMuted,
            )

            else -> LazyColumn {
                itemsIndexed(messages) { i, message ->
                    val ts = parseSupportTs(message.createdAt)
                    val prevTs = messages.getOrNull(i - 1)
                        ?.let { parseSupportTs(it.createdAt) }
                    if (ts != null &&
                        (prevTs == null || prevTs.toLocalDate() != ts.toLocalDate())
                    ) {
                        SupportDateHeader(ts)
                        Spacer(Modifier.height(8.dp))
                    }
                    VpnkaBubble(message, ts)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

private fun ticketStatusLabel(status: String): String = when (status) {
    "new" -> "новое"
    "open" -> "в работе"
    "resolved" -> "решено"
    "closed" -> "закрыто"
    else -> status
}
