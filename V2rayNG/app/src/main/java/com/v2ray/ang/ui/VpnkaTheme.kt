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

    private fun pick(light: Color, night: Color) = if (dark) night else light

    // Disconnected — the resting state. The accent survives inversion: it
    // is the brand, and it reads on both washes.
    val Accent = Color(0xFFE8850C)
    val AccentLight = Color(0xFFF5A83C)

    // Connected.
    val Green: Color get() = pick(Color(0xFF2FAE4F), Color(0xFF5FD07E))

    // The trial countdown, and nothing else. Reserved so it keeps meaning
    // "this is about to stop working" rather than becoming another accent.
    val Warning: Color get() = pick(Color(0xFFD32F2F), Color(0xFFFF6B6B))

    // A step below Warning: "worth doing something about soon", not "now".
    // The expiry banner starts here at three days and turns to Warning
    // inside the last one, so the change of colour carries the urgency.
    val Amber: Color get() = pick(Color(0xFFB26B00), Color(0xFFE8A33C))

    // Text, darkest first — and lightest first once inverted.
    val TextStrong: Color get() = pick(Color(0xFF5C3D10), Color(0xFFF6E7CE))
    val TextBrand: Color get() = pick(Color(0xFF7A4A12), Color(0xFFEBD3AC))
    val TextMuted: Color get() = pick(Color(0xFF8A6635), Color(0xFFC3AC85))
    val TextFaint: Color get() = pick(Color(0xFFB98C4E), Color(0xFF9A8362))
    val TextUnit: Color get() = pick(Color(0xFFA07A3E), Color(0xFFB09A72))
    val IconMuted: Color get() = pick(Color(0xFFA06A20), Color(0xFFD8A65A))

    // Screen background — a radial wash, three stops each way. Dark keeps
    // the same warmth rather than going neutral grey, so it still reads as
    // this app at night.
    val BgOffCentre: Color get() = pick(Color(0xFFFFF8EA), Color(0xFF2A2116))
    val BgOffMid: Color get() = pick(Color(0xFFFFEFD2), Color(0xFF1F1810))
    val BgOffEdge: Color get() = pick(Color(0xFFFFE4B8), Color(0xFF15100A))
    val BgOnCentre: Color get() = pick(Color(0xFFEEFBE9), Color(0xFF1B2A1C))
    val BgOnMid: Color get() = pick(Color(0xFFDCF3D2), Color(0xFF152015))
    val BgOnEdge: Color get() = pick(Color(0xFFCDEABF), Color(0xFF0E160F))

    // Cards sit on the wash rather than on a surface, so they are white with
    // alpha rather than a solid colour — the gradient shows through and the
    // card belongs to the page. Dark inverts the tint, not the idea.
    val CardSpeed: Color get() =
        pick(Color(0xFFFFFFFF).copy(alpha = 0.75f), Color(0xFFFFFFFF).copy(alpha = 0.07f))
    val CardServer: Color get() =
        pick(Color(0xFFFFFFFF).copy(alpha = 0.85f), Color(0xFFFFFFFF).copy(alpha = 0.10f))
    val CardSettings: Color get() =
        pick(Color(0xFFFFFFFF).copy(alpha = 0.70f), Color(0xFFFFFFFF).copy(alpha = 0.06f))

    // The warm shadow that ties the whole screen together.
    val Shadow: Color get() = pick(Color(0xFFB47814), Color(0xFF000000))

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
