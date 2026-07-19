package com.v2ray.ang.ui

import androidx.compose.ui.graphics.Color
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
    // Disconnected — the resting state.
    val Accent = Color(0xFFE8850C)
    val AccentLight = Color(0xFFF5A83C)

    // Connected.
    val Green = Color(0xFF2FAE4F)

    // Text, darkest first.
    val TextStrong = Color(0xFF5C3D10)
    val TextBrand = Color(0xFF7A4A12)
    val TextMuted = Color(0xFF8A6635)
    val TextFaint = Color(0xFFB98C4E)
    val TextUnit = Color(0xFFA07A3E)
    val IconMuted = Color(0xFFA06A20)

    // Screen background — a radial wash, three stops each way.
    val BgOffCentre = Color(0xFFFFF8EA)
    val BgOffMid = Color(0xFFFFEFD2)
    val BgOffEdge = Color(0xFFFFE4B8)
    val BgOnCentre = Color(0xFFEEFBE9)
    val BgOnMid = Color(0xFFDCF3D2)
    val BgOnEdge = Color(0xFFCDEABF)

    // Cards sit on the wash rather than on a surface, so they are white with
    // alpha rather than a solid colour — the gradient shows through and the
    // card belongs to the page.
    val CardSpeed = Color(0xFFFFFFFF).copy(alpha = 0.75f)
    val CardServer = Color(0xFFFFFFFF).copy(alpha = 0.85f)
    val CardSettings = Color(0xFFFFFFFF).copy(alpha = 0.70f)

    // The warm shadow that ties the whole screen together.
    val Shadow = Color(0xFFB47814)

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
