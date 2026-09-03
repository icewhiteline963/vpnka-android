package com.v2ray.ang.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.handler.VpnkaAccount
import com.v2ray.ang.handler.VpnkaExit
import com.v2ray.ang.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

/**
 * The VPNka home screen, built from the design handoff.
 *
 * Every number here — 230, 176, 62, 2.5sp of tracking — comes from that
 * document rather than from taste, so the screen can be checked against it
 * later without guessing which values were deliberate.
 *
 * The one place the implementation reasons for itself is the data: the
 * handoff shows a mock («Премиум · 214 дней», «24/31 мс», speeds climbing to
 * 120 Mbps). Those are wired to the real subscription, the real selected
 * server and real traffic counters, and where a real value is missing the
 * screen says so instead of showing a plausible number.
 */

private const val LOGO_SCALE = 1.06f  // handoff: background-size 106%

/** Turns the orange logo green, as the handoff's hue-rotate(75deg) does. */
private fun hueRotate(degrees: Float): ColorFilter {
    val rad = Math.toRadians(degrees.toDouble())
    val c = cos(rad).toFloat()
    val s = sin(rad).toFloat()
    // Standard luminance-preserving hue rotation matrix.
    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                0.213f + c * 0.787f - s * 0.213f,
                0.715f - c * 0.715f - s * 0.715f,
                0.072f - c * 0.072f + s * 0.928f, 0f, 0f,

                0.213f - c * 0.213f + s * 0.143f,
                0.715f + c * 0.285f + s * 0.140f,
                0.072f - c * 0.072f - s * 0.283f, 0f, 0f,

                0.213f - c * 0.213f - s * 0.787f,
                0.715f - c * 0.715f + s * 0.715f,
                0.072f + c * 0.928f + s * 0.072f, 0f, 0f,

                0f, 0f, 0f, 1f, 0f,
            )
        )
    )
}

@Composable
fun VpnkaConnectScreen(
    isRunning: Boolean,
    isLoading: Boolean,
    trialHoursLeft: Int?,
    subscriptionName: String?,
    canSwitchSubscription: Boolean,
    paidSubscription: Boolean,
    freeMonthEnabled: Boolean,
    /** Бесплатный месяц ещё идёт и кончается в ближайшие сутки. */
    freeMonthWaiting: Boolean,
    claimingFreeMonth: Boolean,
    onClaimFreeMonth: () -> Unit,
    serverName: String,
    serverDelay: String,
    sessionSeconds: Long,
    downBytes: Long,
    upBytes: Long,
    onToggle: () -> Unit,
    onOpenProfile: () -> Unit,
    onChangeSubscription: () -> Unit,
    onChangeServer: () -> Unit,
    updateVersion: String?,
    onCheckUpdate: () -> Unit,
    onPerAppProxy: () -> Unit,
    smartDeskEnabled: Boolean,
    smartDeskOnline: Boolean,
    onSmartDesk: () -> Unit,
    /** Открыть «Видео» сразу, минуя рабочий стол. */
    onYouTube: () -> Unit,
    /** Открыть приложение стола по его id — со значка на главном экране. */
    onOpenDeskApp: (String) -> Unit = {},
    expiryDaysLeft: Int?,
    /**
     * План, который кончается РАНЬШЕ остальных, когда за ним есть другой:
     * название и сколько дней. null — такого нет.
     */
    endingSoonPlan: Pair<String, Int>? = null,
    onRenew: () -> Unit,
    activeDaysLeft: Int?,
    activeDevicesUsed: Int?,
    activeDevicesLimit: Int?,
    telegramLinked: Boolean,
    /** «Войти через Телеграм» — открыть бота (с предложением поднять ВПН). */
    onLinkTelegram: () -> Unit,
    onLeaveReview: () -> Unit,
) {
    // Подключение перекрашивает ВЕСЬ набор, а не один цветок.
    //
    // В макете это `accentVars()`: при поднятом туннеле `--acc` уходит в
    // #4ec46a, `--onAcc` в почти чёрный зелёный, а полотно из тёплого
    // #100d09 в зелёное #0b100b. То есть зеленеют пилюля, кольцо, волны,
    // метки строк и шевроны — не только цветок.
    val bgEdge by animateColorAsState(
        if (isRunning) VpnkaColors.BgOnEdge else VpnkaColors.BgOffEdge,
        tween(800), label = "bg3",
    )

    // Свечение ПРИЖАТО К ВЕРХУ, как в макете.
    //
    // Было: круговой градиент из центра экрана — то есть самое светлое
    // место приходилось на середину списка, а шапка и кнопка сидели в
    // темноте. В макете это `radial-gradient(120% 62% at 50% 2%)`: пятно
    // цвета начинается у самой кромки экрана, над цветком, и сходит на
    // нет к середине. Полотно под ним — ровное, без второго градиента.
    // Кто мы снаружи — спрашиваем ЗДЕСЬ, а не внутри карточки сервера.
    //
    // Пока ответ жил в карточке, экран говорил две вещи разом: пилюля
    // наверху — «ЗАЩИЩЕНО» с горящей точкой, а строка под цветком —
    // «трафик идёт мимо VPN». Верить надо второму, и знать об этом должна
    // прежде всего пилюля.
    var exit by remember { mutableStateOf<VpnkaExit.Exit?>(null) }
    var leaking by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(isRunning, lifecycleOwner) {
        if (!isRunning) { exit = null; leaking = false; return@LaunchedEffect }
        // Обвинять в утечке — со ВТОРОГО подряд ответа, снимать обвинение —
        // после трёх подряд молчаний.
        //
        // Одиночный ответ «вижу твой настоящий адрес» бывает и на ровном
        // месте: проба уходит в момент переподключения ядра. А раньше
        // молчание («не дозвонились») обвинение не снимало вовсе — одна
        // неудачная проба, и пилюля до перезапуска туннеля писала «МИМО
        // VPN» при исправно работающем ВПН.
        var badInARow = 0
        var silentInARow = 0
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val e = VpnkaExit.current()
                when {
                    e == null -> {
                        silentInARow++
                        badInARow = 0
                        if (silentInARow >= 3) leaking = false
                    }
                    e.onVpn -> {
                        silentInARow = 0
                        badInARow = 0
                        exit = e
                        leaking = false
                    }
                    else -> {
                        silentInARow = 0
                        badInARow++
                        if (badInARow >= 2) {
                            leaking = true
                            // Страну забываем: мы идём НЕ через неё, а
                            // карточка продолжала показывать последнюю
                            // известную рядом с криком «мимо VPN».
                            exit = null
                        }
                    }
                }
                delay(10_000)
            }
        }
    }
    // Имя узла — только когда сервер его действительно назвал. Пустая
    // строка тоже «не назвал»: /whoami отвечает `on_vpn:true` без страны,
    // пока новый адрес узла не сопоставлен, — и карточка оставалась пустой.
    val liveName = exit?.let { e ->
        "${e.flag.orEmpty()} ${e.name ?: e.code ?: ""}".trim()
    }?.takeIf { it.isNotBlank() }

    val accent by animateColorAsState(
        when {
            leaking -> VpnkaColors.Warning
            isRunning -> VpnkaColors.AccentOn
            else -> VpnkaColors.Accent
        },
        tween(600), label = "accent",
    )
    // Чем писать ПО акценту — тоже меняется вместе с ним.
    val onAccent = if (isRunning) VpnkaColors.OnAccentOn else VpnkaColors.OnAccent
    val glow by animateColorAsState(
        if (isRunning) VpnkaColors.AccentOn.copy(alpha = 0.30f)
        else VpnkaColors.Accent.copy(alpha = 0.16f),
        tween(800), label = "glow",
    )
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(bgEdge)
            .drawBehind {
                // 120% ширины на 62% высоты — пятно ШИРЕ, чем высокое.
                // Круглый градиент того же радиуса уходил вниз до середины
                // списка; и цвет должен гаснуть к 64% луча, а не тянуться
                // до самого края.
                val cx = size.width / 2f
                val cy = size.height * 0.02f
                val rx = size.width * 1.2f
                val ry = size.height * 0.62f
                withTransform({ scale(1f, ry / rx, Offset(cx, cy)) }) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0f to glow,
                                0.64f to Color.Transparent,
                            ),
                            center = Offset(cx, cy),
                            radius = rx,
                        ),
                        topLeft = Offset(0f, cy - rx),
                        size = Size(size.width, rx * 2f),
                    )
                }
            }
    ) {
        val headerTop = if (maxHeight < 720.dp) 34.dp else 62.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            VpnkaHeader(
                onOpenProfile = onOpenProfile,
                updateAvailable = updateVersion != null,
                onCheckUpdate = onCheckUpdate,
                topPadding = headerTop,
                // Status sits on the icons' line — it is the one fact the
                // screen exists to state, and it belongs at the top of it
                // rather than floating above the button.
                status = when {
                    leaking -> "МИМО VPN"
                    isRunning -> "ЗАЩИЩЕНО"
                    else -> "НЕ ЗАЩИЩЕНО"
                },
                statusColor = accent,
                statusOn = isRunning,
                hint = when {
                    leaking -> "Сервер видит ваш настоящий адрес"
                    isLoading -> "Подключаемся…"
                    isRunning -> "Трафик зашифрован"
                    else -> "Нажмите на цветочек"
                },
            )

            // Блок подключения по макету: цветок СЛЕВА, сводка справа.
            //
            // Раньше кнопка стояла по центру, под ней крупный таймер, а
            // счётчики трафика лежали отдельной парой карточек ниже — из-за
            // чего на экран не помещалось ничего, кроме них. В макете круг
            // 146 точек занимает левую колонку, а справа тремя строками
            // идут время в сети, текущий сервер и оба счётчика.
            Row(
                // Боковые поля обязательны: блок — прямой ребёнок
                // прокручиваемой колонки без отступов, и без них цветок
                // упирался в левый край экрана, а счётчики в правый.
                modifier = Modifier.fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier.size(146.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    VpnkaConnectButton(
                        isRunning = isRunning,
                        isLoading = isLoading,
                        accent = accent,
                        onToggle = onToggle,
                        outerSize = 146.dp,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Column {
                        Text(
                            text = "В СЕТИ",
                            fontFamily = VpnkaFonts.manrope600,
                            fontWeight = VpnkaWeight.Semi,
                            fontSize = 9.sp,
                            letterSpacing = 0.14.em,
                            color = VpnkaColors.fg(0.5f),
                        )
                        Spacer(Modifier.height(5.dp))
                        // Моноширинный и 700, а не 900: на пропорциональном
                        // жирном строка дёргалась на каждой секунде.
                        Text(
                            text = formatSession(sessionSeconds),
                            fontFamily = VpnkaFonts.mono,
                            fontWeight = VpnkaWeight.Bold,
                            fontSize = 27.sp,
                            letterSpacing = (-0.5).sp,
                            color = VpnkaColors.TextStrong,
                            maxLines = 1,
                        )
                    }
                    // «Сейчас через» — сервер, на котором мы прямо сейчас.
                    VpnkaHomeServerCard(
                        name = serverName,
                        delay = serverDelay,
                        isRunning = isRunning,
                        liveName = liveName,
                        leaking = leaking,
                        accent = accent,
                        onClick = onChangeServer,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VpnkaStatCard("ЗАГРУЖЕНО", downBytes, Modifier.weight(1f))
                        VpnkaStatCard("ОТДАНО", upBytes, Modifier.weight(1f))
                    }
                }
            }

            // The nav bar's height is added to this block's own bottom
            // padding, not subtracted from the screen: the app draws
            // edge-to-edge, so on a phone with gesture navigation the last
            // row — «приложения через VPN» — sat underneath the system bar
            // and its taps went to the system. Measured after the padding,
            // so the button above still gets the space that is actually
            // left.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // The launch check already knew this; until now it only lit
                // a dot on a button in the corner, which is indistinguishable
                // from not checking at all. Say it in words, where the eye
                // already is.
                if (updateVersion != null) {
                    VpnkaUpdateBanner(version = updateVersion, onClick = onCheckUpdate)
                }
                if (expiryDaysLeft != null && expiryDaysLeft <= 3) {
                    VpnkaExpiryBanner(daysLeft = expiryDaysLeft, onRenew = onRenew)
                }
                // Отдельная строка про план, который кончается раньше других.
                //
                // Полоса выше говорит о том дне, когда доступ пропадёт совсем,
                // — по самому дальнему плану. Но у человека с двумя планами
                // может закончиться один, и это не потеря связи, а потеря
                // того, что давал именно он (устройств, страны). Смешивать
                // это в одну надпись значит пугать «подписка кончается» того,
                // у кого впереди ещё месяц.
                endingSoonPlan?.let { (name, days) ->
                    VpnkaPlanEndingRow(name = name, days = days, onRenew = onRenew)
                }
                // Free month, right where the bot puts it: offered to anyone
                // without an active paid plan. With a Telegram behind the
                // account, tapping claims it in-app. Without one, the same
                // card becomes the invitation to link — the month belongs to
                // an identified account, while a bare install gets the
                // 24-hour first-run trial and nothing more.
                if (!paidSubscription && freeMonthEnabled) {
                    VpnkaFreeMonthCard(
                        waiting = freeMonthWaiting,
                        claiming = claimingFreeMonth,
                        telegramLinked = telegramLinked,
                        bgColor = accent,
                        onClaim = onClaimFreeMonth,
                    )
                }
                // Card order depends on what the client holds. On a paid plan
                // the server is the thing worth switching, so it leads; on the
                // free month the subscription leads (the natural next step is
                // to keep/upgrade it). Both cards are always present — only the
                // order changes.
                // Подписка — ОБЫЧНАЯ строка списка.
                //
                // Была карточкой с радиусом 20, полями 16/12, тремя
                // строками текста и надписью «Сменить» — то есть вдвое выше
                // соседей и другой формы; в списке из четырёх одинаковых
                // строк она выпирала и ломала ритм. В макете это строка со
                // значением справа: «Подписка · 314 дней».
                val planRow: @Composable () -> Unit = {
                    VpnkaHomeRow(
                        icon = "★",
                        label = "Подписка",
                        right = when {
                            trialHoursLeft != null -> pluralHours(trialHoursLeft)
                            activeDaysLeft != null -> "$activeDaysLeft дн"
                            else -> subscriptionName.orEmpty()
                        },
                        accent = accent,
                        onAccent = onAccent,
                        onClick = if (canSwitchSubscription) onChangeSubscription
                            else onOpenProfile,
                    )
                }
                // Отдельной строки про загрузчик здесь больше нет.
                //
                // Он и так стоит значком в сетке выше — строка повторяла
                // тот же вход вторым способом, а под карточкой подписки
                // читалась как часть подписки.
                // Приложения — прямо на главном, как в макете «Поток».
                //
                // Рабочий стол был отдельным экраном: чтобы попасть в видео,
                // браузер или чаты, нужно было знать про стол и открыть его.
                // В макете сетка значков живёт под кнопкой подключения — то
                // есть стол и главный экран это ОДИН экран, а не два. Бейдж
                // на значке показывает то, что человеку и нужно знать
                // издалека: сколько качается и сколько непрочитанных.
                // Полоска загрузок — она в макете стоит ПЕРЕД сеткой
                // значков и говорит то, ради чего экран открывают на ходу:
                // что качается прямо сейчас и сколько осталось.
                if (smartDeskEnabled) {
                    VpnkaDownloadWidget(
                        accent = accent,
                        onAccent = onAccent,
                        onClick = onYouTube,
                    )
                    VpnkaAppGrid(isRunning = isRunning, onOpen = onOpenDeskApp)
                }
                // Сервер уже показан в блоке подключения — второй карточки
                // здесь нет: одни и те же имя и задержка стояли на экране
                // дважды.
                //
                // Ряды идут своей группой с зазором 7: в макете они прижаты
                // друг к другу списком, а не разбросаны по 12 точек, как
                // карточки выше.
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                // Первой строкой — вход через Телеграм, пока его нет.
                // Раньше об этом говорила выноска под шапкой, которую
                // закрывали и забывали; в макете это обычный ряд, только
                // залитый акцентом.
                // Карточка бесплатного месяца у непривязанных зовёт туда
                // же — и получалось два одинаковых призыва подряд, оба
                // несносимые. Строка уступает карточке: та объясняет, что
                // человек за это получит.
                val freeMonthAsksToLink =
                    !paidSubscription && freeMonthEnabled && !telegramLinked
                if (!telegramLinked && !freeMonthAsksToLink) {
                    VpnkaHomeRow(
                        icon = "✈",
                        label = "Войти через Телеграм",
                        primary = true,
                        accent = accent,
                        onAccent = onAccent,
                        // Строка делает ровно то, что написано: открывает
                        // бота. Раньше она вела в профиль — то есть на
                        // экран, где ту же кнопку надо было нажать второй
                        // раз, и по дороге терялось предложение поднять
                        // ВПН, без которого Телеграм у большинства не
                        // открывается.
                        onClick = onLinkTelegram,
                    )
                }
                planRow()
                // Дальше — строки, а не карточки.
                //
                // Каждый пункт был карточкой с заголовком и двумя строками
                // пояснения; такие занимали экран целиком, и до значков
                // приложений приходилось листать. В макете это компактные
                // строки со значением справа, а пояснение живёт внутри
                // самого раздела, где его читают по делу.
                VpnkaHomeRow(
                    icon = "⇄",
                    label = "Прокси для приложений",
                    accent = accent,
                    onAccent = onAccent,
                    onClick = onPerAppProxy,
                )
                // «VPNka облако» отложено до следующей выкатки (решение
                // владельца 03.09): раздел есть, но показывать его рано.
                VpnkaHomeRow(
                    icon = "✎",
                    label = "Оставить отзыв",
                    accent = accent,
                    onAccent = onAccent,
                    onClick = onLeaveReview,
                )
                }
            }
        }
    }
}

@Composable
private fun VpnkaAppGrid(isRunning: Boolean, onOpen: (String) -> Unit) {
    // Показываем то, что человек поставил себе на стол, тем же порядком.
    val installed = remember { com.v2ray.ang.ui.smartDeskInstalledApps() }
    if (installed.isEmpty()) return

    // Заголовка «ПРИЛОЖЕНИЯ» в макете нет: сетка значков в подписи не
    // нуждается — и так видно, что это значки, — а строка съедала место,
    // из-за которого нижние ряды уходили за край.
    //
    // Четыре в ряд, зазор 14 по вертикали и 10 по горизонтали. Сетку
    // кладём вручную рядами, а не LazyVerticalGrid: она внутри
    // прокручиваемого столбца, где меряется бесконечной высотой и роняет
    // разметку.
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        installed.chunked(4).forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEachIndexed { colIndex, app ->
                    VpnkaAppTile(
                        app = app,
                        // Порядковый номер нужен для гаммы: цвета плиток
                        // идут лентой по кругу, а не назначены каждому
                        // приложению по отдельности. Считаем его по месту
                        // в сетке: `indexOf` — это поиск по списку внутри
                        // цикла по тому же списку, да ещё и одинаковый
                        // номер для двух одинаковых значков.
                        index = rowIndex * 4 + colIndex,
                        isRunning = isRunning,
                        onOpen = onOpen,
                        modifier = Modifier.weight(1f),
                    )
                }
                // Неполный ряд добиваем пустыми местами, иначе три значка
                // расползаются на всю ширину и сетка перестаёт быть сеткой.
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * Цвета плитки по номеру — лента макета.
 *
 * Макет задаёт их в oklch: `linear-gradient(150deg, oklch(0.86 0.14 H+18),
 * oklch(0.7 0.185 H))`, где H берётся из двух наборов — тёплого при
 * выключенном ВПН и холодного при поднятом. Compose про oklch не знает,
 * поэтому переводим вручную; иначе пришлось бы вбивать 24 шестнадцатеричных
 * значения и потерять смысл: это ОДНО семейство, повёрнутое по кругу.
 */
private fun tileTint(index: Int, isRunning: Boolean): List<Color> {
    val warm = intArrayOf(55, 45, 70, 62, 80, 50)
    val cool = intArrayOf(145, 132, 158, 150, 168, 138)
    val hue = (if (isRunning) cool else warm)[((index % 6) + 6) % 6].toFloat()
    return listOf(oklch(0.86f, 0.14f, hue + 18f), oklch(0.7f, 0.185f, hue))
}

/** oklch → sRGB. Формулы Бьорна Оттоссона, без сокращений. */
private fun oklch(l: Float, c: Float, hueDeg: Float): Color {
    val h = Math.toRadians(hueDeg.toDouble())
    val a = c * cos(h).toFloat()
    val b = c * sin(h).toFloat()

    val lp = l + 0.3963377774f * a + 0.2158037573f * b
    val mp = l - 0.1055613458f * a - 0.0638541728f * b
    val sp = l - 0.0894841775f * a - 1.2914855480f * b
    val ll = lp * lp * lp
    val mm = mp * mp * mp
    val ss = sp * sp * sp

    fun gamma(x: Float): Float {
        val v = if (x <= 0.0031308f) 12.92f * x
        else 1.055f * Math.pow(x.toDouble(), 1.0 / 2.4).toFloat() - 0.055f
        return v.coerceIn(0f, 1f)
    }
    return Color(
        red = gamma(4.0767416621f * ll - 3.3077115913f * mm + 0.2309699292f * ss),
        green = gamma(-1.2684380046f * ll + 2.6097574011f * mm - 0.3413193965f * ss),
        blue = gamma(-0.0041960863f * ll - 0.7034186147f * mm + 1.7076147010f * ss),
    )
}

/**
 * Полоска загрузок, как в макете: квадрат со стрелкой, название файла,
 * ниточка прогресса под ним и объём справа.
 *
 * Пока ничего не качается, она не исчезает, а подводит итог — сколько
 * всего скачано и сколько файлов. Пустое место на её месте читалось бы
 * как «качалки нет».
 */
@Composable
private fun VpnkaDownloadWidget(
    accent: Color,
    onAccent: Color,
    onClick: () -> Unit,
) {
    // Журнал скачанного живёт на диске, а список — в памяти, и поднимал
    // его только экран «Видео». До первого захода туда полоска на главном
    // подводила итог «0 Б · 0» человеку с сорока файлами на диске.
    LaunchedEffect(Unit) {
        // В фоне и под страховкой: restore() расшифровывает MMKV и
        // разбирает до трёхсот записей, а исключение внутри LaunchedEffect
        // роняет процесс целиком. Экран «Видео» зовёт её ровно так же.
        withContext(Dispatchers.IO) { runCatching { YouTubeDownloads.restore() } }
    }
    val entries = YouTubeDownloads.entries
    // Сначала то, что РЕАЛЬНО качается: живые загрузки добавляются в
    // начало списка, и после «скачать всё» первым оказывался последний
    // поставленный в очередь — полоска замирала на нуле с его названием,
    // пока ниже шли две настоящие.
    val active = entries.firstOrNull { it.state == YouTubeDownloads.State.RUNNING }
        ?: entries.firstOrNull { it.state == YouTubeDownloads.State.QUEUED }
    val pct = active?.let {
        if (it.total > 0L) (it.done * 100 / it.total).toInt().coerceIn(0, 100) else 0
    } ?: 0
    val title = when {
        active == null -> "Все загрузки завершены"
        active.waitReason != null -> "${active.label} · ${active.waitReason}"
        else -> active.label
    }
    val right = if (active != null) {
        val (v, u) = formatTraffic(active.total.coerceAtLeast(active.done))
        "$pct% · $v $u"
    } else {
        val done = entries.count { it.state == YouTubeDownloads.State.DONE }
        val (v, u) = formatTraffic(
            entries.filter { it.state == YouTubeDownloads.State.DONE }.sumOf { it.total }
        )
        "$v $u · $done"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(VpnkaColors.CardSpeed)
            .border(1.dp, VpnkaColors.Hairline, RoundedCornerShape(15.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.size(28.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(accent),
            contentAlignment = Alignment.Center,
        ) {
            Text("↓", fontSize = 13.sp, color = onAccent)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = VpnkaFonts.manrope600,
                fontWeight = VpnkaWeight.Semi,
                fontSize = 11.5.sp,
                lineHeight = 14.sp,
                color = VpnkaColors.TextStrong,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(VpnkaColors.fg(0.14f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(pct / 100f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(accent),
                )
            }
        }
        Text(
            text = right,
            fontFamily = VpnkaFonts.mono,
            fontWeight = VpnkaWeight.Semi,
            fontSize = 10.5.sp,
            color = VpnkaColors.fg(0.68f),
            maxLines = 1,
        )
    }
}

/** Один значок: цветная плитка с гербом, счётчик и подпись. */
@Composable
private fun VpnkaAppTile(
    app: com.v2ray.ang.ui.DeskApp,
    index: Int,
    isRunning: Boolean,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Счётчик на значке — то, ради чего на него смотрят издалека: сколько
    // качается и сколько непрочитанных. Плитки до сих пор были одинаково
    // серыми и молчали об этом.
    // Непрочитанные считаем В ФОНЕ и раз в пять секунд.
    //
    // Прямой вызов из композиции стоил дорого: `contacts()` и `unread()`
    // ходят в ЗАШИФРОВАННЫЙ MMKV и разбирают Gson-ом всю переписку по
    // каждому собеседнику — а композиция главного экрана обновляется раз
    // в секунду, пока идёт трафик. Плюс MMKV не наблюдаем: пришедшее при
    // погашенном туннеле сообщение бейдж бы вообще не показал.
    val unread by produceState(0, app.id) {
        if (app.id != "messages") return@produceState
        while (true) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    com.v2ray.ang.handler.Messenger.contacts()
                        .sumOf { com.v2ray.ang.handler.ChatPrefs.unread(it.id) }
                }.getOrDefault(0)
            }
            delay(5_000)
        }
    }
    // Загрузки — наблюдаемый список в памяти, их можно читать прямо здесь.
    val badge = when (app.id) {
        "messages" -> unread
        "youtube" -> YouTubeDownloads.entries.count {
            it.state == YouTubeDownloads.State.RUNNING ||
                it.state == YouTubeDownloads.State.QUEUED
        }
        else -> 0
    }
    // Гамма плиток — ЛЕНТА одного семейства, которая переключается вместе
    // с туннелем: тёплая, пока ВПН выключен, холодная, когда поднят. Набор
    // из `appTint()` был другого рода — каждому приложению свой цвет из
    // разных семейств (тёмно-красный рядом с серо-синим), и он не менялся
    // вообще.
    val tint = tileTint(index, isRunning)
    // Нажимается вся колонка вместе с подписью, но БЕЗ клипа: счётчик
    // выходит за верхний край плитки, а `offset` размера не прибавляет —
    // клип на колонке срезал ему макушку.
    Column(
        modifier = modifier.clickable { onOpen(app.id) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier.size(52.dp)
                    .shadow(5.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(tint))
                    // Внутренний блик по верхней кромке — от него плитка
                    // выглядит выпуклой, как в макете.
                    .border(
                        1.dp,
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.42f), Color.Transparent)
                        ),
                        RoundedCornerShape(16.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) { Text(app.glyph, fontSize = 21.sp) }
            if (badge > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 8.dp, y = (-6).dp)
                        .defaultMinSize(minWidth = 17.dp, minHeight = 17.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(VpnkaColors.TextStrong)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (badge > 99) "99+" else badge.toString(),
                        fontFamily = VpnkaFonts.manrope700,
                        fontWeight = VpnkaWeight.Bold,
                        fontSize = 9.5.sp,
                        color = VpnkaColors.BgOffEdge,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            app.label,
            fontFamily = VpnkaFonts.manrope500,
            fontWeight = VpnkaWeight.Medium,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            color = VpnkaColors.fg(0.75f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun VpnkaHeader(
    onOpenProfile: () -> Unit,
    updateAvailable: Boolean,
    onCheckUpdate: () -> Unit,
    topPadding: Dp,
    status: String,
    statusColor: Color,
    /** Включён ли туннель — от этого зависит цвет точки в пилюле. */
    statusOn: Boolean,
    /** Строка под пилюлей: «Трафик зашифрован» / «Нажмите на цветочек». */
    hint: String,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = topPadding)) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Only the account, nothing else. The plan moved down to its own row
        // above the server, where it sits next to the thing it governs; the
        // trial countdown went with it. A header that repeated either was
        // saying the same thing twice on one screen.
        // Круги 32 точки и плёнка вместо белого — как в макете.
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(VpnkaColors.CardSpeed)
                .border(1.dp, VpnkaColors.Hairline, CircleShape)
                .clickable(onClick = onOpenProfile),
            contentAlignment = Alignment.Center,
        ) {
            VpnkaPersonGlyph()
        }

        // Статус — ПИЛЮЛЯ с точкой, а не просто надпись в разрядку.
        //
        // В макете это скруглённая плашка: точка слева (при включённом ВПН
        // акцентная и с ореолом) и текст акцентом. Надпись сама по себе
        // читалась как заголовок экрана, а не как состояние.
        // Пилюля и подсказка — ОДНОЙ колонкой по центру.
        //
        // Подсказка стояла отдельной строкой во всю ширину под шапкой и
        // жила своей жизнью: 15 точек жирным, тогда как в макете это
        // 11.5 обычной толщины прямо под состоянием, зазор 6.
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(VpnkaColors.CardSpeed)
                    .border(1.dp, VpnkaColors.Hairline, RoundedCornerShape(20.dp))
                    .padding(start = 9.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                // Ореол вокруг точки — он в макете есть и только он
                // отличает «горит» от «просто кружок»: box-shadow 0 0 0 4px
                // на акценте с прозрачностью .22.
                // Коробка всегда 16: ореол в макете — тень, места он не
                // занимает, а размер по состоянию раздвигал пилюлю на
                // восемь точек в тот момент, когда туннель вставал.
                Box(
                    modifier = Modifier.size(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (statusOn) {
                        Box(
                            modifier = Modifier.size(16.dp).clip(CircleShape)
                                .background(statusColor.copy(alpha = 0.22f)),
                        )
                    }
                    Box(
                        modifier = Modifier.size(8.dp).clip(CircleShape)
                            .background(
                                if (statusOn) statusColor
                                else VpnkaColors.TextMuted.copy(alpha = 0.55f)
                            ),
                    )
                }
                Text(
                    text = status,
                    fontFamily = VpnkaFonts.manrope700,
                    fontWeight = VpnkaWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 0.12.em,
                    color = statusColor,
                    maxLines = 1,
                )
            }
            Text(
                text = hint,
                fontFamily = VpnkaFonts.manrope600,
                fontWeight = VpnkaWeight.Semi,
                fontSize = 11.5.sp,
                color = VpnkaColors.fg(0.8f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // App update, top right. Always present — tapping it re-checks even
        // when we already believe we're current, because the check runs once
        // at launch and a release can land while the app sits open. The dot
        // is the only part that depends on what that check found.
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(VpnkaColors.CardSpeed)
                .border(1.dp, VpnkaColors.Hairline, CircleShape)
                .clickable(onClick = onCheckUpdate),
            contentAlignment = Alignment.Center,
        ) {
            VpnkaRefreshGlyph()
            if (updateAvailable) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(VpnkaColors.Accent)
                )
            }
        }
    }
    }
}

/** A new build is out, said plainly rather than hinted at with a dot. */
@Composable
private fun VpnkaUpdateBanner(version: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(VpnkaColors.Accent.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Доступна версия $version",
                fontFamily = VpnkaFonts.nunito800,
                fontWeight = VpnkaWeight.Extra,
                fontSize = 15.sp,
                color = VpnkaColors.Accent,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Нажмите, чтобы установить",
                fontFamily = VpnkaFonts.manrope600,
                fontWeight = VpnkaWeight.Semi,
                fontSize = 12.sp,
                color = VpnkaColors.TextMuted,
            )
        }
        Text(text = "›", fontSize = 18.sp, color = VpnkaColors.Accent)
    }
}

/** Один из планов кончается, но доступ остаётся — тон спокойный. */
@Composable
private fun VpnkaPlanEndingRow(name: String, days: Int, onRenew: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(VpnkaColors.CardSettings)
            .clickable(onClick = onRenew)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⏳", fontSize = 13.sp)
        Spacer(Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (days <= 0) "«$name» кончается сегодня"
                else "«$name» кончается через $days ${pluralDays(days)}",
                fontFamily = VpnkaFonts.nunito800,
                fontSize = 13.sp,
                color = VpnkaColors.TextStrong,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Другая подписка продолжает работать — связь не прервётся.",
                fontFamily = VpnkaFonts.manrope600,
                fontSize = 11.sp,
                color = VpnkaColors.TextMuted,
            )
        }
    }
}

/**
 * The subscription is nearly over, said where it will be seen.
 *
 * Amber at three days, red inside the last one: renewing is the user's
 * problem too, and the day the tunnel stops without warning is the day
 * they look for another provider.
 */
@Composable
private fun VpnkaExpiryBanner(daysLeft: Int, onRenew: () -> Unit) {
    val urgent = daysLeft <= 1
    val tint = if (urgent) VpnkaColors.Warning else VpnkaColors.Amber
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(tint.copy(alpha = 0.14f))
            .clickable(onClick = onRenew)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = if (urgent) {
                "Подписка кончается меньше чем через сутки"
            } else {
                "Подписка кончается через $daysLeft ${pluralDays(daysLeft)}"
            },
            fontFamily = VpnkaFonts.nunito800,
            fontWeight = VpnkaWeight.Extra,
            fontSize = 15.sp,
            color = tint,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "Продлите в боте, чтобы связь не прервалась. Нажмите здесь.",
            fontFamily = VpnkaFonts.manrope600,
            fontWeight = VpnkaWeight.Semi,
            fontSize = 12.sp,
            color = VpnkaColors.TextMuted,
        )
    }
}

/**
 * Строка «через какой узел мы сейчас идём» — по макету, но говорит правду.
 *
 * Имя ЖИВОГО узла приходит сверху: пока сервер не ответил, показываем
 * выбранное, ответил — тот, через который трафик идёт на самом деле. При
 * утечке карточка целиком становится предупреждением: до этого красной
 * была только надпись «ВНИМАНИЕ» кеглем 8.5, а сама новость про открытый
 * трафик — обычным белым на обычной подложке.
 */
@Composable
private fun VpnkaHomeServerCard(
    name: String,
    delay: String,
    isRunning: Boolean,
    liveName: String?,
    leaking: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(11.dp)
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(shape)
            .background(
                if (leaking) VpnkaColors.Warning.copy(alpha = 0.18f)
                else VpnkaColors.CardSpeed
            )
            .border(
                1.dp,
                if (leaking) VpnkaColors.Warning.copy(alpha = 0.45f) else VpnkaColors.Hairline,
                shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        // Кружок слева — ФЛАГ страны, а не одинаковая оранжевая точка.
        //
        // Своих картинок флагов в приложении нет, зато флаг есть в имени
        // узла. `flagOf` для безымянных отдаёт глобус — его считаем «страны
        // нет» и оставляем градиент, как в макете у «Авто».
        val flag = flagOf(liveName ?: name).takeIf { it != "🌍" }.orEmpty()
        Box(
            modifier = Modifier.size(16.dp).clip(CircleShape)
                .then(
                    if (flag.isBlank()) Modifier.background(
                        Brush.verticalGradient(
                            listOf(VpnkaColors.FlagCircleStart, accent)
                        )
                    ) else Modifier
                )
                .border(1.dp, VpnkaColors.fg(0.2f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (flag.isNotBlank()) Text(flag, fontSize = 11.sp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                // Пока туннель погашен, «СЕЙЧАС ЧЕРЕЗ» — неправда: ни через
                // какой узел мы не идём. Тогда это просто выбранный сервер.
                text = when {
                    leaking -> "ВНИМАНИЕ"
                    isRunning -> "СЕЙЧАС ЧЕРЕЗ"
                    else -> "СЕРВЕР"
                },
                fontFamily = VpnkaFonts.manrope600,
                fontWeight = VpnkaWeight.Semi,
                fontSize = 8.5.sp,
                letterSpacing = 0.1.em,
                color = if (leaking) VpnkaColors.Warning else VpnkaColors.fg(0.55f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = when {
                    leaking -> "трафик идёт мимо VPN"
                    liveName != null -> liveName
                    delay.isNotBlank() -> "$name · $delay"
                    else -> name
                },
                fontFamily = VpnkaFonts.manrope700,
                fontWeight = VpnkaWeight.Bold,
                fontSize = 12.sp,
                color = if (leaking) VpnkaColors.Warning else VpnkaColors.TextStrong,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "›",
            fontSize = 12.sp,
            color = if (leaking) VpnkaColors.Warning else accent,
        )
    }
}

/**
 * Счётчик трафика в две строки — как в макете.
 *
 * Прежняя карточка занимала половину ширины экрана и стояла в своей
 * строке; здесь их две в узкой правой колонке рядом с цветком.
 */
@Composable
private fun VpnkaStatCard(label: String, bytes: Long, modifier: Modifier = Modifier) {
    val (value, unit) = formatTraffic(bytes)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(VpnkaColors.CardSpeed)
            .border(1.dp, VpnkaColors.Hairline, RoundedCornerShape(11.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Text(
            text = label,
            fontFamily = VpnkaFonts.manrope600,
            fontWeight = VpnkaWeight.Semi,
            fontSize = 10.sp,
            letterSpacing = 0.06.em,
            color = VpnkaColors.fg(0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(5.dp))
        // Значение и единица — по БАЗОВОЙ ЛИНИИ: в макете они в одном
        // потоке текста, а выравнивание по нижнему краю разводило 13 и 10
        // на пару точек.
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontFamily = VpnkaFonts.mono,
                fontWeight = VpnkaWeight.Bold,
                fontSize = 13.sp,
                color = VpnkaColors.TextStrong,
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = unit,
                fontFamily = VpnkaFonts.manrope600,
                fontSize = 10.sp,
                color = VpnkaColors.fg(0.8f),
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}

/**
 * Строка главного экрана — как в макете «Поток».
 *
 * Раньше каждый пункт был карточкой с заголовком и двумя строками
 * пояснения: пять таких занимали экран целиком, и до значков приложений
 * приходилось листать. В макете это компактные строки — значок слева,
 * название, значение справа, шеврон. Пояснение переезжает внутрь самого
 * раздела, где его читают по делу, а не поверх всего.
 *
 * `primary` — заливка акцентом, для единственного действия, которое сейчас
 * важнее прочих (вход через Телеграм у непривязанного).
 */
@Composable
private fun VpnkaHomeRow(
    icon: String,
    label: String,
    right: String = "",
    primary: Boolean = false,
    accent: Color = VpnkaColors.Accent,
    onAccent: Color = VpnkaColors.OnAccent,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (primary) accent else VpnkaColors.CardSpeed)
            .then(
                if (primary) Modifier
                else Modifier.border(1.dp, VpnkaColors.Hairline, RoundedCornerShape(14.dp))
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = if (primary) 12.dp else 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Значок сидит в квадратике 26 точек, а не висит голым символом:
        // без подложки строки читались как список текста, а в макете у
        // каждой слева цветная метка.
        Box(
            modifier = Modifier.size(26.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(
                    if (primary) Color.White.copy(alpha = 0.24f)
                    else accent.copy(alpha = 0.22f)
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = icon,
                fontSize = 12.sp,
                color = if (primary) onAccent else accent,
            )
        }
        Spacer(Modifier.width(11.dp))
        Text(
            text = label,
            fontFamily = VpnkaFonts.manrope600,
            fontWeight = VpnkaWeight.Semi,
            fontSize = 12.5.sp,
            color = if (primary) onAccent else VpnkaColors.TextStrong,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (right.isNotBlank()) {
            Text(
                text = right,
                fontFamily = VpnkaFonts.mono,
                fontWeight = VpnkaWeight.Semi,
                fontSize = 10.5.sp,
                color = if (primary) onAccent.copy(alpha = 0.72f)
                    else VpnkaColors.fg(0.45f),
                maxLines = 1,
            )
            Spacer(Modifier.width(11.dp))
        }
        Text(
            text = "›",
            fontSize = 12.sp,
            color = if (primary) onAccent.copy(alpha = 0.8f) else accent,
        )
    }
}

/** A circular arrow, drawn rather than pulled in as an icon dependency. */
@Composable
private fun VpnkaRefreshGlyph() {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(14.dp)) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.11f
        val inset = stroke + w * 0.10f
        drawArc(
            color = VpnkaColors.IconMuted,
            startAngle = 25f,
            sweepAngle = 300f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size(w - inset * 2, h - inset * 2),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = stroke,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            ),
        )
        // Arrowhead at the gap, pointing along the sweep.
        val r = (w - inset * 2) / 2f
        val tipX = w / 2f + r * 0.96f
        val tipY = h / 2f - r * 0.28f
        val a = w * 0.17f
        val head = androidx.compose.ui.graphics.Path().apply {
            moveTo(tipX + a * 0.2f, tipY - a * 0.9f)
            lineTo(tipX + a * 0.9f, tipY + a * 0.5f)
            lineTo(tipX - a * 0.8f, tipY + a * 0.2f)
            close()
        }
        drawPath(head, color = VpnkaColors.IconMuted)
    }
}

@Composable
private fun VpnkaFreeMonthCard(
    claiming: Boolean,
    telegramLinked: Boolean,
    /** Текущий бесплатный месяц ещё идёт: карточку показываем, но забрать
     *  следующий можно только после того, как он закончится. */
    waiting: Boolean,
    bgColor: androidx.compose.ui.graphics.Color,
    onClaim: () -> Unit,
) {
    val white = androidx.compose.ui.graphics.Color.White
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (waiting) bgColor.copy(alpha = 0.55f) else bgColor)
            .clickable(enabled = !claiming && !waiting, onClick = onClaim)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when {
                    claiming -> "🎁 Оформляем…"
                    waiting -> "🎁 Следующий месяц — завтра"
                    telegramLinked -> "🎁 Месяц бесплатно"
                    else -> "🔗 Подключить Telegram"
                },
                fontFamily = VpnkaFonts.nunito800,
                fontWeight = VpnkaWeight.Extra,
                fontSize = 16.sp,
                color = white,
                modifier = Modifier.weight(1f),
            )
            if (claiming) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = white,
                    strokeWidth = 2.dp,
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = if (waiting) {
                "Текущий месяц заканчивается. Как только он истечёт, здесь " +
                    "можно будет забрать следующий — и так сколько угодно раз."
            } else if (telegramLinked) {
                "30 дней бесплатно, без карты, продлевать можно сколько угодно раз. " +
                    "Нажмите, чтобы получить."
            } else {
                "Сейчас у вас пробные сутки. Привяжите Telegram — " +
                    "и месяц бесплатно, без карты."
            },
            fontFamily = VpnkaFonts.manrope600,
            fontWeight = VpnkaWeight.Semi,
            fontSize = 12.sp,
            color = white.copy(alpha = 0.92f),
        )
    }
}

/** A simple head-and-shoulders mark, drawn rather than shipped. */
@Composable
private fun VpnkaPersonGlyph() {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(14.dp)) {
        val w = size.width
        val h = size.height
        drawCircle(
            color = VpnkaColors.IconMuted,
            radius = w * 0.22f,
            center = Offset(w / 2f, h * 0.30f),
        )
        // Shoulders: an arc clipped by the canvas bottom reads as a bust
        // without needing a path.
        drawArc(
            color = VpnkaColors.IconMuted,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(w * 0.12f, h * 0.60f),
            size = androidx.compose.ui.geometry.Size(w * 0.76f, h * 0.70f),
        )
    }
}


@Composable
private fun VpnkaConnectButton(
    isRunning: Boolean,
    isLoading: Boolean,
    accent: Color,
    onToggle: () -> Unit,
    outerSize: Dp,
) {
    // Кольца по макету, а не «пульс и пунктир».
    //
    // Было: одно кольцо, раздувающееся до 1.35 размера всего блока, и
    // вращающийся пунктир. В макете пунктира нет вовсе, а волны — три,
    // они расходятся от 132 до 186 точек (то есть НЕ выходят за 146-й
    // блок настолько, чтобы налезть на таймер справа) и живут только
    // пока туннель поднят: на выключенном экране круг стоит тихо.
    // Заводим ход ТОЛЬКО когда есть что двигать: бесконечная анимация
    // запрашивает кадры, даже если её значение никто не читает, и экран
    // с погашенным туннелем не давал системе успокоиться.
    val wave = if (!isRunning) 0f else {
        val transition = rememberInfiniteTransition(label = "button")
        // Один общий ход 0…1 — как тик макета раз в 650 мс по модулю 10;
        // три волны берут его со сдвигом в треть.
        val w by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                // 6,5 с на полный ход: в макете тик идёт каждые 650 мс, а
                // фаза считается по модулю десяти тиков.
                tween(6_500, easing = LinearEasing), RepeatMode.Restart,
            ),
            label = "wave",
        )
        w
    }

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    // Выключено — кольцо не пропадает, а становится еле заметным:
    // rgba(fg,.14) макета, а не волосяная рамка карточек (.07).
    val ringColor = if (isRunning) accent.copy(alpha = 0.45f)
        else VpnkaColors.TextStrong.copy(alpha = 0.14f)
    Box(
        modifier = Modifier.size(outerSize),
        contentAlignment = Alignment.Center,
    ) {
        if (isRunning) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 1.5.dp.toPx()
                repeat(3) { i ->
                    val phase = (wave + i / 3f) % 1f
                    val diameter = (132f + phase * 54f).dp.toPx()
                    drawCircle(
                        color = accent.copy(alpha = 0.4f * (1f - phase)),
                        radius = diameter / 2f,
                        style = Stroke(width = stroke),
                    )
                }
            }
        }

        // Неподвижное кольцо 132 точки — оно есть всегда, меняется цвет.
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            // CSS рисует рамку СНАРУЖИ: внешний поперечник 132+2×1.5.
            // Compose ведёт обводку по центру, поэтому радиус чуть больше.
            drawCircle(
                color = ringColor,
                radius = (132.dp.toPx() + 1.5.dp.toPx()) / 2f,
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }

        // Сам цветок: белый круг 118 точек с логотипом.
        Box(
            modifier = Modifier
                .size(118.dp)
                .scale(if (pressed) 0.95f else 1f)
                // Цветок в макете висит над полотном: 0 10px 28px rgba(0,0,0,.28).
                .shadow(10.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = !isLoading,
                    onClick = onToggle,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.vpnka_logo),
                contentDescription = if (isRunning) "Отключить" else "Подключить",
                contentScale = ContentScale.Crop,
                // Подключено — логотип перекрашивается в зелень. Выключено —
                // остаётся ЯРКИМ.
                //
                // В макете на этом месте `grayscale(.5) brightness(.95)`, и
                // я перенёс это буквально — цветок стал блёклым. Но в
                // макете под фильтром лежит рисованная заглушка, а у нас
                // настоящий логотип, и половина цвета из него — это и есть
                // узнаваемость приложения. Оставляем как в рабочей версии.
                colorFilter = if (isRunning) hueRotate(78f) else null,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(LOGO_SCALE),
            )
        }
    }
}

// ---- small helpers ---------------------------------------------------------

internal fun formatSession(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}

/** Bytes as a number and a unit, so the two can be styled apart. */
internal fun formatTraffic(bytes: Long): Pair<String, String> {
    val b = bytes.coerceAtLeast(0)
    return when {
        b < 1024 -> b.toString() to "Б"
        b < 1024L * 1024 -> "%.0f".format(b / 1024.0) to "КБ"
        b < 1024L * 1024 * 1024 -> "%.1f".format(b / 1024.0 / 1024) to "МБ"
        else -> "%.2f".format(b / 1024.0 / 1024 / 1024) to "ГБ"
    }
}

/** The leading emoji of a server name, or a globe when it has none. */
internal fun flagOf(name: String): String {
    val trimmed = name.trimStart()
    // Regional-indicator pairs are two code points; take them together or
    // the flag renders as two stray letters.
    if (trimmed.length >= 4) {
        val first = trimmed.codePointAt(0)
        if (first in 0x1F1E6..0x1F1FF) {
            val second = trimmed.offsetByCodePoints(0, 1)
            if (trimmed.codePointAt(second) in 0x1F1E6..0x1F1FF) {
                return trimmed.substring(0, trimmed.offsetByCodePoints(0, 2))
            }
        }
    }
    return "🌍"
}

/** «час / часа / часов» — the warning is read, not parsed. */
internal fun pluralHours(n: Int): String {
    val a = kotlin.math.abs(n)
    return when {
        a % 10 == 1 && a % 100 != 11 -> "час"
        a % 10 in 2..4 && a % 100 !in 12..14 -> "часа"
        else -> "часов"
    }
}

/**
 * The warm page every inner screen sits on.
 *
 * The connect screen got the design; the profile, shop, support and the rest
 * kept Material's defaults and looked like a different app the moment you
 * stepped into them. This is the shared shell that closes that gap — the
 * same wash as the disconnected home screen, a title in the same face, and a
 * back affordance in the same place on all of them.
 */
@Composable
fun VpnkaPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                // Follows the tunnel, like the main screen: turning the VPN
                // on from inside the profile should be visible there and
                // not only after going back.
                Brush.radialGradient(
                    colorStops = if (VpnkaColors.connected) arrayOf(
                        0f to VpnkaColors.BgOnCentre,
                        0.6f to VpnkaColors.BgOnMid,
                        1f to VpnkaColors.BgOnEdge,
                    ) else arrayOf(
                        0f to VpnkaColors.BgOffCentre,
                        0.6f to VpnkaColors.BgOffMid,
                        1f to VpnkaColors.BgOffEdge,
                    ),
                    center = Offset.Unspecified,
                    radius = Float.POSITIVE_INFINITY,
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 20.dp, end = 20.dp, top = 62.dp, bottom = 24.dp)
                // Lift content above the on-screen keyboard — otherwise the IME
                // covers the bottom controls (the support screen's «Отправить»
                // button was hidden behind it).
                .imePadding()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(VpnkaColors.CardSettings)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("‹", fontSize = 22.sp, color = VpnkaColors.IconMuted)
                }
                Spacer(Modifier.size(12.dp))
                Text(
                    text = title,
                    fontFamily = VpnkaFonts.nunito900,
                    fontWeight = VpnkaWeight.Black,
                    fontSize = 22.sp,
                    color = VpnkaColors.TextBrand,
                )
            }
            Spacer(Modifier.height(20.dp))
            content()
        }
    }
}

/** A card in the same style as the ones on the home screen. */
@Composable
fun VpnkaCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(VpnkaColors.CardServer)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        content = content,
    )
}

/** The screens' primary action, in the accent colour. */
@Composable
fun VpnkaPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (enabled) VpnkaColors.Accent
                else VpnkaColors.Accent.copy(alpha = 0.4f)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontFamily = VpnkaFonts.nunito800,
            fontWeight = VpnkaWeight.Extra,
            fontSize = 16.sp,
            color = Color.White,
        )
    }
}

/** A quieter action — the same shape, without the fill. */
@Composable
fun VpnkaSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(VpnkaColors.CardSpeed)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontFamily = VpnkaFonts.nunito800,
            fontWeight = VpnkaWeight.Extra,
            fontSize = 15.sp,
            color = VpnkaColors.TextStrong,
        )
    }
}

/**
 * «Сервер» — everything about which exit carries the traffic.
 *
 * The design handoff shows a «Сменить ›» link and stops there. It used to
 * open v2rayNG's own server UI, which is dense, English in places, and looks
 * like a different application — and it was also the only route to
 * refreshing the list or testing latency, so the redesign quietly took both
 * away.
 *
 * Grouping them here rather than in settings is deliberate: refreshing the
 * list and measuring pings are things you do *because* you are choosing a
 * server, not app preferences you set once.
 */
@Composable
fun VpnkaServersScreen(
    servers: List<VpnkaServerOption>,
    selectedGuid: String?,
    isLoading: Boolean,
    isTesting: Boolean,
    onSelectServer: (String) -> Unit,
    onRefresh: () -> Unit,
    onSpeedTest: () -> Unit,
    onBack: () -> Unit,
) {
    VpnkaPage(title = "Сервер", onBack = onBack) {
        // Only when there is a real choice — a picker with one entry is noise.
        Text(
            text = "СЕРВЕРЫ",
            fontFamily = VpnkaFonts.manrope700,
            fontWeight = VpnkaWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
            color = VpnkaColors.TextFaint,
        )
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
        ) {
            items(servers) { server ->
                VpnkaChoiceRow(
                    title = server.name,
                    subtitle = server.delay.takeIf { it.isNotBlank() },
                    selected = server.guid == selectedGuid,
                    onClick = { onSelectServer(server.guid) },
                )
                Spacer(Modifier.height(6.dp))
            }
        }

        Spacer(Modifier.height(12.dp))
        VpnkaPrimaryButton(
            text = if (isLoading) "Обновляем…" else "Обновить подписку и серверы",
            enabled = !isLoading,
            onClick = onRefresh,
        )
        Spacer(Modifier.height(8.dp))
        VpnkaSecondaryButton(
            text = if (isTesting) "Проверяем…" else "Тест скорости",
            onClick = onSpeedTest,
        )
    }
}

@Composable
fun VpnkaChoiceRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) VpnkaColors.CardServer else VpnkaColors.CardSpeed
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = VpnkaFonts.nunito800,
                fontWeight = VpnkaWeight.Extra,
                fontSize = 15.sp,
                color = VpnkaColors.TextStrong,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontFamily = VpnkaFonts.manrope600,
                    fontWeight = VpnkaWeight.Semi,
                    fontSize = 12.sp,
                    color = VpnkaColors.TextFaint,
                )
            }
        }
        if (selected) {
            Text(
                text = "✓",
                fontSize = 18.sp,
                color = VpnkaColors.Accent,
            )
        }
    }
}

/** Choosing which plan carries the traffic. */
@Composable
fun VpnkaPlansScreen(
    subscriptions: List<VpnkaSubOption>,
    selectedGuid: String?,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
) {
    VpnkaPage(title = "Подписка", onBack = onBack) {
        subscriptions.forEach { sub ->
            VpnkaChoiceRow(
                title = sub.name,
                subtitle = null,
                selected = sub.guid == selectedGuid,
                onClick = { onSelect(sub.guid) },
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * «Мои подписки» — every plan the account holds, and the way to buy another.
 *
 * The profile listed them as flat text with nowhere to go. Each is a card
 * now: tapping opens what the bot shows for it — days, device slots, the
 * devices themselves, and the QR that adds it to another phone.
 */
@Composable
fun VpnkaPlansListScreen(
    plans: List<VpnkaAccount.Plan>,
    activeToken: String?,
    trialHoursLeft: Int?,
    onGetFreeMonth: () -> Unit,
    onSelectPlan: (VpnkaAccount.Plan) -> Unit,
    onOpenPlan: (VpnkaAccount.Plan) -> Unit,
    onBuy: () -> Unit,
    onBack: () -> Unit,
) {
    VpnkaPage(title = "Мои подписки", onBack = onBack) {
        if (plans.isEmpty()) {
            // No plan on the account means the traffic is running on the
            // shipped 24-hour trial — not on a free month, which is what
            // this row used to claim while the row above it, taken from the
            // real subscription, said «пробный доступ · 24 часа». Two names
            // for one thing, and the wrong one was here.
            //
            // The trial exists to buy time for exactly one action, so the
            // row says what that action is and performs it when tapped.
            VpnkaPlanRowActive(
                title = "Пробный доступ · 24 часа",
                subtitle = buildString {
                    if (trialHoursLeft != null) {
                        append("осталось $trialHoursLeft ${pluralHours(trialHoursLeft)} · ")
                    }
                    append("нажмите, чтобы получить месяц бесплатно")
                },
                onClick = onGetFreeMonth,
            )
            Spacer(Modifier.height(16.dp))
        } else {
            plans.forEach { plan ->
                val live = plan.groupToken != null && plan.groupToken == activeToken
                val subtitle = buildString {
                    val days = plan.daysLeft
                    if (plan.frozen) {
                        append("заморожена")
                    } else if (days != null) {
                        append("$days ${pluralDays(days)}")
                    }
                    if (plan.devicesLimit != null) {
                        if (isNotEmpty()) append(" · ")
                        append("${plan.devicesUsed ?: 0}/${plan.devicesLimit} устройств")
                    }
                    if (live) {
                        if (isNotEmpty()) append(" · ")
                        append("сейчас используется")
                    }
                }.ifBlank { null }

                // The whole card opens the plan — its days, devices, QR and
                // the copy button. Switching the active plan lives in the
                // picker on the connect screen, so this list is for reading
                // and managing a plan, and one tap means exactly that.
                VpnkaPlanRow2(
                    title = plan.tariff ?: "Подписка",
                    subtitle = subtitle,
                    live = live,
                    onActivate = { onSelectPlan(plan) },
                    onClick = { onOpenPlan(plan) },
                )
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(12.dp))
        }
        VpnkaPrimaryButton(text = "Купить подписку", onClick = onBuy)
    }
}

/**
 * The plan the traffic is actually going through.
 *
 * Green rather than the accent: the accent is what «tap me» looks like
 * everywhere else in the app, and this is a statement of fact, not an
 * invitation. It also matches the colour the connect button turns when
 * the tunnel is up, so the two read as the same signal.
 */
@Composable
private fun VpnkaPlanRow2(
    title: String,
    subtitle: String?,
    live: Boolean,
    onActivate: () -> Unit,
    onClick: () -> Unit,
) {
    // Two targets on one card: the radio on the left switches the active
    // subscription (the traffic runs through the selected one), the rest of
    // the card opens the plan's details. The radio's own click is consumed, so
    // tapping it activates without also opening details.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (live) VpnkaColors.Green.copy(alpha = 0.14f)
                else VpnkaColors.CardSpeed
            )
            .clickable(onClick = onClick)
            .padding(start = 4.dp, top = 8.dp, bottom = 8.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = live,
            onClick = onActivate,
            colors = RadioButtonDefaults.colors(
                selectedColor = VpnkaColors.Green,
                unselectedColor = VpnkaColors.TextFaint,
            ),
        )
        Spacer(Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = VpnkaFonts.nunito800,
                fontWeight = VpnkaWeight.Extra,
                fontSize = 15.sp,
                color = VpnkaColors.TextStrong,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontFamily = VpnkaFonts.manrope600,
                    fontWeight = VpnkaWeight.Semi,
                    fontSize = 12.sp,
                    color = if (live) VpnkaColors.Green else VpnkaColors.TextFaint,
                )
            }
        }
        // A chevron, so the whole card reads as «opens».
        Text(
            text = "›",
            fontFamily = VpnkaFonts.nunito800,
            fontWeight = VpnkaWeight.Extra,
            fontSize = 20.sp,
            color = if (live) VpnkaColors.Green else VpnkaColors.TextFaint,
        )
    }
}

@Composable
private fun VpnkaPlanRowActive(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(VpnkaColors.Green.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = VpnkaFonts.nunito800,
                fontWeight = VpnkaWeight.Extra,
                fontSize = 15.sp,
                color = VpnkaColors.TextStrong,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontFamily = VpnkaFonts.manrope600,
                    fontWeight = VpnkaWeight.Semi,
                    fontSize = 12.sp,
                    color = VpnkaColors.Green,
                )
            }
        }
        Text(text = "●", fontSize = 14.sp, color = VpnkaColors.Green)
    }
}

/**
 * One plan in full: what the bot's card shows, plus its devices and QR.
 *
 * The QR encodes the plan's own subscription URL, so scanning it on a second
 * phone adds that plan and nothing else — which is why the per-plan token
 * matters rather than the account-wide one.
 */
@Composable
fun VpnkaPlanDetailScreen(
    plan: VpnkaAccount.Plan,
    devices: List<VpnkaAccount.Device>,
    devicesLoading: Boolean,
    qr: androidx.compose.ui.graphics.ImageBitmap?,
    onCopySubscription: () -> Unit,
    onShareSubscription: () -> Unit,
    onRevokeDevice: (Long) -> Unit,
    onRenameDevice: (Long, String) -> Unit,
    onBack: () -> Unit,
) {
    // The device the user is renaming, if any — drives the dialog below.
    var renaming by remember { mutableStateOf<VpnkaAccount.Device?>(null) }
    VpnkaPage(title = plan.tariff ?: "Подписка", onBack = onBack) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                VpnkaCard {
                    val days = plan.daysLeft
                    VpnkaDetailRow(
                        "Состояние",
                        if (plan.frozen) "Заморожена" else "Активна",
                    )
                    if (days != null) {
                        VpnkaDetailRow("Осталось", "$days ${pluralDays(days)}")
                    }
                    if (plan.devicesLimit != null) {
                        VpnkaDetailRow(
                            "Устройства",
                            "${plan.devicesUsed ?: 0} из ${plan.devicesLimit}",
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                if (plan.groupToken != null) {
                    VpnkaSecondaryButton(
                        text = "Копировать подписку",
                        onClick = onCopySubscription,
                    )
                    Spacer(Modifier.height(8.dp))
                    VpnkaSecondaryButton(
                        text = "Поделиться подпиской",
                        onClick = onShareSubscription,
                    )
                    Spacer(Modifier.height(16.dp))
                }

                if (qr != null) {
                    Text(
                        text = "ДОБАВИТЬ НА ДРУГОЕ УСТРОЙСТВО",
                        fontFamily = VpnkaFonts.manrope700,
                        fontWeight = VpnkaWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        color = VpnkaColors.TextFaint,
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White)
                            .padding(20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            bitmap = qr,
                            contentDescription = "QR-код подписки",
                            modifier = Modifier.size(200.dp),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }

                Text(
                    text = "УСТРОЙСТВА",
                    fontFamily = VpnkaFonts.manrope700,
                    fontWeight = VpnkaWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    color = VpnkaColors.TextFaint,
                )
                Spacer(Modifier.height(8.dp))
                if (devicesLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else if (devices.isEmpty()) {
                    Text(
                        text = "Ни одно устройство ещё не подключалось.",
                        fontFamily = VpnkaFonts.manrope600,
                        fontWeight = VpnkaWeight.Semi,
                        fontSize = 14.sp,
                        color = VpnkaColors.TextMuted,
                    )
                }
            }

            items(devices) { device ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(VpnkaColors.CardSpeed)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Tap the name (or the pencil) to rename the device —
                    // «iPhone», «OnePlus» — instead of the app string it
                    // reports for itself (Happ, v2rayNG).
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { renaming = device },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = device.label,
                            fontFamily = VpnkaFonts.nunito800,
                            fontWeight = VpnkaWeight.Extra,
                            fontSize = 15.sp,
                            color = VpnkaColors.TextStrong,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(text = "✎", fontSize = 14.sp, color = VpnkaColors.Accent)
                    }
                    Text(
                        text = "Отключить",
                        fontFamily = VpnkaFonts.nunito800,
                        fontWeight = VpnkaWeight.Extra,
                        fontSize = 13.sp,
                        color = VpnkaColors.Warning,
                        modifier = Modifier.clickable { onRevokeDevice(device.id) },
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
        }

        renaming?.let { dev ->
            var name by remember(dev.id) { mutableStateOf(dev.label) }
            AlertDialog(
                onDismissRequest = { renaming = null },
                title = { Text("Название устройства") },
                text = {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(32) },
                        singleLine = true,
                        placeholder = { Text("iPhone, OnePlus…") },
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        onRenameDevice(dev.id, name.trim())
                        renaming = null
                    }) { Text("Сохранить") }
                },
                dismissButton = {
                    TextButton(onClick = { renaming = null }) { Text("Отмена") }
                },
            )
        }
    }
}

/**
 * Buying a subscription without leaving the app.
 *
 * Tariffs and prices come from the same source the bot uses. Paying from
 * balance settles instantly; paying by card opens the processor's page and
 * returns to /paid/app. The card invoice is built server-side by the exact
 * call the bot makes — the app only ever opens the URL it is handed, so there
 * is nothing different to generate here.
 */
@Composable
fun VpnkaTopUpScreen(
    balanceRub: Int?,
    submitting: Boolean,
    onPay: (Int) -> Unit,
    onBack: () -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    VpnkaPage(title = "Пополнить баланс", onBack = onBack) {
        Spacer(Modifier.height(20.dp))
        if (balanceRub != null) {
            Text(
                text = "Текущий баланс: $balanceRub ₽",
                fontFamily = VpnkaFonts.manrope600,
                fontWeight = VpnkaWeight.Semi,
                fontSize = 14.sp,
                color = VpnkaColors.TextMuted,
            )
            Spacer(Modifier.height(18.dp))
        }
        Text(
            text = "Сумма пополнения, ₽",
            fontSize = 16.sp,
            color = VpnkaColors.TextStrong,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it.filter { c -> c.isDigit() }.take(6) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            placeholder = { Text("например, 300") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Минимум 100 ₽",
            fontFamily = VpnkaFonts.manrope600,
            fontWeight = VpnkaWeight.Semi,
            fontSize = 12.sp,
            color = VpnkaColors.TextFaint,
        )
        Spacer(Modifier.weight(1f))
        val amt = amount.toIntOrNull() ?: 0
        VpnkaPrimaryButton(
            text = if (submitting) "Оплата…" else "Оплатить СБП",
            onClick = { if (!submitting && amt >= 100) onPay(amt) },
            enabled = amt >= 100 && !submitting,
        )
    }
}

@Composable
fun VpnkaShopScreen(
    tariffs: List<VpnkaAccount.Tariff>,
    loading: Boolean,
    buyingId: Long?,
    onBuyBalance: (Long) -> Unit,
    onBuyCard: (Long) -> Unit,
    onBack: () -> Unit,
) {
    VpnkaPage(title = "Купить подписку", onBack = onBack) {
        when {
            loading -> {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(modifier = Modifier.size(28.dp)) }
            }
            tariffs.isEmpty() -> {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Сейчас нет доступных тарифов.",
                    fontFamily = VpnkaFonts.manrope600,
                    fontWeight = VpnkaWeight.Semi,
                    fontSize = 14.sp,
                    color = VpnkaColors.TextMuted,
                )
            }
            else -> LazyColumn(modifier = Modifier.weight(1f)) {
                item { Spacer(Modifier.height(8.dp)) }
                items(tariffs) { t ->
                    val busy = buyingId == t.id
                    VpnkaCard {
                        Text(
                            text = t.name,
                            fontFamily = VpnkaFonts.nunito800,
                            fontWeight = VpnkaWeight.Extra,
                            fontSize = 17.sp,
                            color = VpnkaColors.TextStrong,
                        )
                        if (!t.description.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = t.description,
                                fontFamily = VpnkaFonts.manrope600,
                                fontWeight = VpnkaWeight.Semi,
                                fontSize = 13.sp,
                                color = VpnkaColors.TextMuted,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = buildString {
                                append("${t.durationDays} ${pluralDays(t.durationDays)}")
                                t.deviceLimit?.let { append(" · $it устройств") }
                                t.trafficLimitGb?.let { append(" · $it ГБ") }
                            },
                            fontFamily = VpnkaFonts.manrope600,
                            fontWeight = VpnkaWeight.Semi,
                            fontSize = 13.sp,
                            color = VpnkaColors.TextFaint,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${t.priceRub} ₽",
                                fontFamily = VpnkaFonts.nunito800,
                                fontWeight = VpnkaWeight.Extra,
                                fontSize = 20.sp,
                                color = VpnkaColors.TextStrong,
                            )
                            t.priceRubFull?.let {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "$it ₽",
                                    fontFamily = VpnkaFonts.manrope600,
                                    fontWeight = VpnkaWeight.Semi,
                                    fontSize = 14.sp,
                                    color = VpnkaColors.TextFaint,
                                    textDecoration = TextDecoration.LineThrough,
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        // СБП first and orange (primary path); paying from
                        // balance is the smaller secondary button below it.
                        if (t.canPayCard) {
                            VpnkaPrimaryButton(
                                text = if (busy) "Оплата…" else "Оплатить СБП",
                                onClick = { if (!busy) onBuyCard(t.id) },
                                enabled = !busy,
                            )
                            if (t.canPayBalance) Spacer(Modifier.height(8.dp))
                        }
                        if (t.canPayBalance) {
                            VpnkaSecondaryButton(
                                text = if (busy) "Оплата…" else "Оплатить с баланса",
                                onClick = { if (!busy) onBuyBalance(t.id) },
                            )
                        }
                        if (!t.canPayBalance && !t.canPayCard) {
                            Text(
                                text = "Оплата этого тарифа сейчас недоступна",
                                fontFamily = VpnkaFonts.manrope600,
                                fontWeight = VpnkaWeight.Semi,
                                fontSize = 12.sp,
                                color = VpnkaColors.TextFaint,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun VpnkaDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
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
