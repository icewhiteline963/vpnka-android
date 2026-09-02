package com.v2ray.ang.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.v2ray.ang.R

/**
 * The design tokens from the VPNka handoff, in one place.
 *
 * Kept apart from the screens that use them so a colour or a weight is
 * changed once rather than hunted through composables — and so the values
 * can be read against the handoff without reading any layout code.
 *
 * The palette is warm on purpose: this is an app people open when something
 * is blocked, and the whole point of the design is that it doesn't look like
 * a network tool.
 */
object VpnkaColors {
    /**
     * Which palette the screens read.
     *
     * Backed by Compose state rather than a plain flag so flipping it
     * recomposes everything that touched a colour — every screen updates on
     * the tap, with no restart and no colour plumbed through call sites.
     * The warm light palette stays the default: it is the design, and dark
     * is for people who want it, not a second design.
     */
    var dark by mutableStateOf(false)

    /**
     * Whether the tunnel is up, for screens other than the main one.
     *
     * Kept here rather than threaded through every `VpnkaPage` call: the
     * inner screens are plain pages that know nothing about the VPN, and
     * adding a parameter to each would put the same value in fifteen call
     * sites. Compose state, so flipping it repaints whatever is open.
     */
    var connected by mutableStateOf(false)

    /**
     * Палитра «Поток» — тёмная тёплая, из макетов супер-приложения.
     *
     * Теперь это палитра ВСЕГО приложения, а не только рабочего стола.
     * Раньше она включалась на время стола и снималась на выходе: главный
     * экран оставался светлым, и человек, переходя со стола на главный,
     * видел две разные программы. Стол и главный экран объединены — значит
     * и вид у них один.
     *
     * Значения ровно из макета: страница #100d09, полотно #15110c, панель
     * #1b160f, акцент #ff961e, второй акцент #ffc61f, текст #f8f1e6,
     * текст по акценту #1d1204.
     *
     * Выключается переключателем светлой темы: `dark` и светлая палитра
     * никуда не делись, «Поток» просто идёт первым. Так у человека,
     * который держит телефон в светлой теме, остаётся прежний вид.
     */
    var flow by mutableStateOf(true)

    private fun pick(light: Color, night: Color) = if (dark) night else light

    /** Трёхходовой выбор: «Поток» важнее и светлой, и ночной. */
    private fun pick(light: Color, night: Color, flowC: Color) =
        if (flow) flowC else if (dark) night else light

    // Disconnected — the resting state. The accent survives inversion: it
    // is the brand, and it reads on both washes.
    val Accent: Color get() = if (flow) Color(0xFFFF961E) else Color(0xFFE8850C)
    val AccentLight: Color get() = if (flow) Color(0xFFFFB655) else Color(0xFFF5A83C)

    /** Второй акцент макета — жёлтый; им отмечено «сейчас играет» и активное. */
    val Accent2: Color get() = if (flow) Color(0xFFFFC61F) else Color(0xFFF5A83C)

    // Connected.
    val Green: Color get() = pick(Color(0xFF2FAE4F), Color(0xFF5FD07E), Color(0xFF7DBF5E))

    // The trial countdown, and nothing else. Reserved so it keeps meaning
    // "this is about to stop working" rather than becoming another accent.
    val Warning: Color get() = pick(Color(0xFFD32F2F), Color(0xFFFF6B6B), Color(0xFFFF7A5C))

    // A step below Warning: "worth doing something about soon", not "now".
    // The expiry banner starts here at three days and turns to Warning
    // inside the last one, so the change of colour carries the urgency.
    val Amber: Color get() = pick(Color(0xFFB26B00), Color(0xFFE8A33C), Color(0xFFFFC61F))

    // Text, darkest first — and lightest first once inverted.
    val TextStrong: Color get() = pick(Color(0xFF5C3D10), Color(0xFFF6E7CE), Color(0xFFF8F1E6))
    val TextBrand: Color get() = pick(Color(0xFF7A4A12), Color(0xFFEBD3AC), Color(0xFFEFE5D6))
    val TextMuted: Color get() = pick(Color(0xFF8A6635), Color(0xFFC3AC85), Color(0xFFAEA394))
    val TextFaint: Color get() = pick(Color(0xFFB98C4E), Color(0xFF9A8362), Color(0xFF7E7469))
    val TextUnit: Color get() = pick(Color(0xFFA07A3E), Color(0xFFB09A72), Color(0xFF9A9084))
    val IconMuted: Color get() = pick(Color(0xFFA06A20), Color(0xFFD8A65A), Color(0xFFFFB655))

    // Screen background — a radial wash, three stops each way. Dark keeps
    // the same warmth rather than going neutral grey, so it still reads as
    // this app at night.
    val BgOffCentre: Color get() = pick(Color(0xFFFFF8EA), Color(0xFF2A2116), Color(0xFF1B160F))
    val BgOffMid: Color get() = pick(Color(0xFFFFEFD2), Color(0xFF1F1810), Color(0xFF15110C))
    val BgOffEdge: Color get() = pick(Color(0xFFFFE4B8), Color(0xFF15100A), Color(0xFF100D09))
    val BgOnCentre: Color get() = pick(Color(0xFFEEFBE9), Color(0xFF1B2A1C), Color(0xFF1B160F))
    val BgOnMid: Color get() = pick(Color(0xFFDCF3D2), Color(0xFF152015), Color(0xFF15110C))
    val BgOnEdge: Color get() = pick(Color(0xFFCDEABF), Color(0xFF0E160F), Color(0xFF100D09))

    // Cards sit on the wash rather than on a surface, so they are white with
    // alpha rather than a solid colour — the gradient shows through and the
    // card belongs to the page. Dark inverts the tint, not the idea.
    // В «Потоке» карточка — это тонкая плёнка цвета текста поверх тёмного
    // полотна плюс волосяная рамка, ровно как в макете (rgba(fg,.05….08) +
    // 1px rgba(fg,.09)). Сплошные #211b14/#2a231a — НЕ карточки: это тона
    // штриховки-заглушки под картинку, и заливать ими блоки было ошибкой.
    private val FlowFilm = Color(0xFFF8F1E6)
    val CardSpeed: Color get() =
        pick(Color(0xFFFFFFFF).copy(alpha = 0.75f), Color(0xFFFFFFFF).copy(alpha = 0.07f), FlowFilm.copy(alpha = 0.045f))
    val CardServer: Color get() =
        pick(Color(0xFFFFFFFF).copy(alpha = 0.85f), Color(0xFFFFFFFF).copy(alpha = 0.10f), FlowFilm.copy(alpha = 0.07f))
    val CardSettings: Color get() =
        pick(Color(0xFFFFFFFF).copy(alpha = 0.70f), Color(0xFFFFFFFF).copy(alpha = 0.06f), FlowFilm.copy(alpha = 0.05f))

    /** Волосяная рамка карточек. В светлой теме её нет — там держит тень. */
    val Hairline: Color get() =
        if (flow) FlowFilm.copy(alpha = 0.09f) else Color(0x00000000)

    /**
     * Чем писать ПО акценту. В макете это почти чёрный #1d1204, а не белый:
     * оранжевый там светлый, и белый текст на нём не читается.
     */
    val OnAccent: Color get() = if (flow) Color(0xFF1D1204) else Color(0xFFFFFFFF)

    // The warm shadow that ties the whole screen together.
    val Shadow: Color get() = pick(Color(0xFFB47814), Color(0xFF000000), Color(0xFF000000))

    val FlagCircleStart = Color(0xFFFFD75E)
    val FlagCircleEnd = Color(0xFFFF9D2E)
}

/**
 * Nunito for headings and numbers, Manrope for labels and body.
 *
 * Both are bundled as variable fonts: one file each covers every weight the
 * design asks for, which is smaller than shipping four static cuts and means
 * a weight the design adds later needs no new asset.
 */
@OptIn(ExperimentalTextApi::class)
private fun variable(resId: Int, weight: Int) = Font(
    resId,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

object VpnkaFonts {
    val nunito800 = FontFamily(variable(R.font.nunito_var, 800))
    val nunito900 = FontFamily(variable(R.font.nunito_var, 900))
    val manrope600 = FontFamily(variable(R.font.manrope_var, 600))
    val manrope700 = FontFamily(variable(R.font.manrope_var, 700))
}

/** Weights the design names, so call sites read like the handoff. */
object VpnkaWeight {
    val Semi = FontWeight(600)
    val Bold = FontWeight(700)
    val Extra = FontWeight(800)
    val Black = FontWeight(900)
}
