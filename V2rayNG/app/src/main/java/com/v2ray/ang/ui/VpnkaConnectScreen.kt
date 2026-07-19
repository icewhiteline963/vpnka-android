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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
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
    serverName: String,
    serverDelay: String,
    sessionSeconds: Long,
    downBytes: Long,
    upBytes: Long,
    onToggle: () -> Unit,
    onOpenProfile: () -> Unit,
    onChangeSubscription: () -> Unit,
    onChangeServer: () -> Unit,
) {
    val accent by animateColorAsState(
        targetValue = if (isRunning) VpnkaColors.Green else VpnkaColors.Accent,
        animationSpec = tween(600),
        label = "accent",
    )
    val bgCentre by animateColorAsState(
        if (isRunning) VpnkaColors.BgOnCentre else VpnkaColors.BgOffCentre,
        tween(800), label = "bg1",
    )
    val bgMid by animateColorAsState(
        if (isRunning) VpnkaColors.BgOnMid else VpnkaColors.BgOffMid,
        tween(800), label = "bg2",
    )
    val bgEdge by animateColorAsState(
        if (isRunning) VpnkaColors.BgOnEdge else VpnkaColors.BgOffEdge,
        tween(800), label = "bg3",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                // Handoff: radial-gradient(circle at 50% 30%, …).
                Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to bgCentre,
                        0.6f to bgMid,
                        1f to bgEdge,
                    ),
                    center = Offset.Unspecified,
                    radius = Float.POSITIVE_INFINITY,
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            VpnkaHeader(onOpenProfile = onOpenProfile)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = if (isRunning) "ЗАЩИЩЕНО" else "НЕ ЗАЩИЩЕНО",
                    fontFamily = VpnkaFonts.manrope700,
                    fontWeight = VpnkaWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 2.5.sp,
                    color = accent,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = when {
                        isLoading -> "Подключаемся…"
                        isRunning -> "Ваш трафик зашифрован 🌼"
                        else -> "Нажмите на цветочек"
                    },
                    fontFamily = VpnkaFonts.nunito800,
                    fontWeight = VpnkaWeight.Extra,
                    fontSize = 15.sp,
                    color = VpnkaColors.TextMuted,
                )
                Spacer(Modifier.height(8.dp))

                VpnkaConnectButton(
                    isRunning = isRunning,
                    isLoading = isLoading,
                    accent = accent,
                    onToggle = onToggle,
                )

                Spacer(Modifier.height(8.dp))
                Text(
                    text = formatSession(sessionSeconds),
                    fontFamily = VpnkaFonts.nunito900,
                    fontWeight = VpnkaWeight.Black,
                    fontSize = 34.sp,
                    color = VpnkaColors.TextStrong,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    VpnkaTrafficCard(
                        label = "ЗАГРУЖЕНО",
                        bytes = downBytes,
                        modifier = Modifier.weight(1f),
                    )
                    VpnkaTrafficCard(
                        label = "ОТДАНО",
                        bytes = upBytes,
                        modifier = Modifier.weight(1f),
                    )
                }
                // Above the server, because it is the wider choice: the plan
                // decides which servers exist at all. Only offered as a
                // switch when there is more than one — otherwise it is a
                // label saying which plan is carrying the traffic.
                VpnkaPlanRow(
                    name = subscriptionName,
                    trialHoursLeft = trialHoursLeft,
                    canSwitch = canSwitchSubscription,
                    onChange = onChangeSubscription,
                    onOpenProfile = onOpenProfile,
                )
                VpnkaServerCard(
                    name = serverName,
                    delay = serverDelay,
                    onChange = onChangeServer,
                )
            }
        }
    }
}

@Composable
private fun VpnkaHeader(onOpenProfile: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 62.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Only the account, nothing else. The plan moved down to its own row
        // above the server, where it sits next to the thing it governs; the
        // trial countdown went with it. A header that repeated either was
        // saying the same thing twice on one screen.
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color.White)
                .clickable(onClick = onOpenProfile),
            contentAlignment = Alignment.Center,
        ) {
            VpnkaPersonGlyph()
        }
    }
}

/** A simple head-and-shoulders mark, drawn rather than shipped. */
@Composable
private fun VpnkaPersonGlyph() {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(20.dp)) {
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
) {
    val transition = rememberInfiniteTransition(label = "button")

    // Handoff: pulse 2s ease-out infinite, scale 1→1.35, opacity .5→0.
    val pulseScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRunning) 1.35f else 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "pulse-scale",
    )
    val pulseAlpha by transition.animateFloat(
        initialValue = if (isRunning) 0.5f else 0.5f,
        targetValue = if (isRunning) 0f else 0.5f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "pulse-alpha",
    )
    // Handoff: dashed ring, 40s linear infinite.
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(40_000, easing = LinearEasing), RepeatMode.Restart,
        ),
        label = "dash-spin",
    )

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Box(
        modifier = Modifier.size(230.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Outer pulse ring.
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxSize()
                .scale(pulseScale)
        ) {
            drawCircle(
                color = accent.copy(alpha = pulseAlpha),
                radius = size.minDimension / 2f - 1.dp.toPx(),
                style = Stroke(width = 2.dp.toPx()),
            )
        }

        // Dashed ring, inset 14dp, slowly rotating.
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            rotate(spin) {
                drawCircle(
                    color = accent.copy(alpha = 0.35f),
                    radius = size.minDimension / 2f - 1.dp.toPx(),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(6.dp.toPx(), 8.dp.toPx()), 0f,
                        ),
                    ),
                )
            }
        }

        // The button itself: a white circle carrying the logo.
        Box(
            modifier = Modifier
                .size(176.dp)
                .scale(if (pressed) 0.95f else 1f)
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
                // The logo is drawn orange; connected, the handoff recolours
                // it rather than shipping a second image.
                colorFilter = if (isRunning) hueRotate(75f) else null,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(LOGO_SCALE),
            )
        }
    }
}

@Composable
private fun VpnkaTrafficCard(
    label: String,
    bytes: Long,
    modifier: Modifier = Modifier,
) {
    val (value, unit) = formatTraffic(bytes)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(VpnkaColors.CardSpeed)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = label,
            fontFamily = VpnkaFonts.manrope700,
            fontWeight = VpnkaWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
            color = VpnkaColors.TextFaint,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontFamily = VpnkaFonts.nunito800,
                fontWeight = VpnkaWeight.Extra,
                fontSize = 20.sp,
                color = VpnkaColors.TextStrong,
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = unit,
                fontFamily = VpnkaFonts.manrope600,
                fontWeight = VpnkaWeight.Semi,
                fontSize = 12.sp,
                color = VpnkaColors.TextUnit,
            )
        }
    }
}

@Composable
private fun VpnkaPlanRow(
    name: String?,
    trialHoursLeft: Int?,
    canSwitch: Boolean,
    onChange: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    val onTrial = trialHoursLeft != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(VpnkaColors.CardSpeed)
            .clickable(onClick = if (onTrial) onOpenProfile else onChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "ПОДПИСКА",
                fontFamily = VpnkaFonts.manrope700,
                fontWeight = VpnkaWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                color = VpnkaColors.TextFaint,
            )
            if (onTrial) {
                // The countdown lives here rather than in the header: it is
                // about the subscription, and this is the subscription row.
                // Loud on purpose — it counts down to the app going quiet,
                // and the muted colours around it would let it pass for
                // decoration.
                Text(
                    text = "До конца $trialHoursLeft ${pluralHours(trialHoursLeft)}, " +
                        "авторизуйтесь чтобы получить подписку!",
                    fontFamily = VpnkaFonts.nunito900,
                    fontWeight = VpnkaWeight.Black,
                    fontSize = 13.sp,
                    color = VpnkaColors.Warning,
                )
            } else {
                Text(
                    text = name ?: "Пробный доступ",
                    fontFamily = VpnkaFonts.nunito800,
                    fontWeight = VpnkaWeight.Extra,
                    fontSize = 15.sp,
                    color = VpnkaColors.TextStrong,
                )
            }
        }
        if (canSwitch && !onTrial) {
            Text(
                text = "Сменить ›",
                fontFamily = VpnkaFonts.nunito800,
                fontWeight = VpnkaWeight.Extra,
                fontSize = 13.sp,
                color = VpnkaColors.Accent,
            )
        }
    }
}

@Composable
private fun VpnkaServerCard(
    name: String,
    delay: String,
    onChange: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(VpnkaColors.CardServer)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            VpnkaColors.FlagCircleStart,
                            VpnkaColors.FlagCircleEnd,
                        )
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            // The flag comes out of the server's own name — our servers are
            // named «🇳🇱 Amsterdam» — rather than from a country table that
            // would have to be kept in step with the node list.
            Text(text = flagOf(name), fontSize = 18.sp)
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = nameWithoutFlag(name),
                fontFamily = VpnkaFonts.nunito800,
                fontWeight = VpnkaWeight.Extra,
                fontSize = 15.sp,
                color = VpnkaColors.TextStrong,
            )
            Text(
                text = delay,
                fontFamily = VpnkaFonts.manrope600,
                fontWeight = VpnkaWeight.Semi,
                fontSize = 12.sp,
                color = VpnkaColors.TextFaint,
            )
        }
        Text(
            text = "Сменить ›",
            fontFamily = VpnkaFonts.nunito800,
            fontWeight = VpnkaWeight.Extra,
            fontSize = 13.sp,
            color = VpnkaColors.Accent,
            textAlign = TextAlign.End,
            modifier = Modifier.clickable(onClick = onChange),
        )
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

internal fun nameWithoutFlag(name: String): String {
    val flag = flagOf(name)
    val stripped = if (flag != "🌍") name.trimStart().removePrefix(flag) else name
    return stripped.trim().ifBlank { "Сервер" }
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
                Brush.radialGradient(
                    colorStops = arrayOf(
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
            text = if (isLoading) "Обновляем…" else "Обновить список серверов",
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
    onOpenPlan: (VpnkaAccount.Plan) -> Unit,
    onBuy: () -> Unit,
    onBack: () -> Unit,
) {
    VpnkaPage(title = "Мои подписки", onBack = onBack) {
        if (plans.isEmpty()) {
            Text(
                text = "Пока нет ни одной подписки.",
                fontFamily = VpnkaFonts.manrope600,
                fontWeight = VpnkaWeight.Semi,
                fontSize = 15.sp,
                color = VpnkaColors.TextMuted,
            )
            Spacer(Modifier.height(16.dp))
        } else {
            plans.forEach { plan ->
                VpnkaChoiceRow(
                    title = plan.tariff ?: "Подписка",
                    subtitle = buildString {
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
                    }.ifBlank { null },
                    selected = false,
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
    onRevokeDevice: (Long) -> Unit,
    onBack: () -> Unit,
) {
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
                    Text(
                        text = device.label,
                        fontFamily = VpnkaFonts.nunito800,
                        fontWeight = VpnkaWeight.Extra,
                        fontSize = 15.sp,
                        color = VpnkaColors.TextStrong,
                        modifier = Modifier.weight(1f),
                    )
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
