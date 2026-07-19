package com.v2ray.ang.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.min

/**
 * The connect control, drawn as a flower that carries the connection state
 * in its colour.
 *
 * Replaces the plain circle, and deliberately keeps everything about the
 * circle that made it work: same size, same position, same tap target, and
 * the same «Защищено» / «Не подключено» caption underneath. The picture is
 * what changed — not what the user has to understand. Someone who cannot
 * read the colour still has the words.
 *
 * Everything is drawn rather than shipped as an asset: it scales to any
 * screen without a folder of PNGs, and the colour can be animated between
 * states instead of cross-fading two bitmaps.
 *
 * Both themes are handled explicitly. Translucent petals are the detail that
 * quietly breaks here — an alpha tuned on a dark background washes out to
 * nearly invisible on a light one — so the palette carries separate alphas
 * instead of one set that is only right half the time.
 */
private const val PETALS = 8

private data class FlowerPalette(
    val core: Color,
    val petalAlpha: Float,
    val haloAlpha: Float,
)

@Composable
private fun paletteFor(isRunning: Boolean, isLoading: Boolean): FlowerPalette {
    val dark = isSystemInDarkTheme()

    // Amber while the tunnel is coming up, green once it is, grey when it
    // isn't. Three states the user actually distinguishes — "connecting" is
    // worth its own colour because that is when people tap twice.
    val target = when {
        isLoading -> if (dark) Color(0xFFFFC24D) else Color(0xFFEF9A00)
        isRunning -> if (dark) Color(0xFF5CD97A) else Color(0xFF2E9E4F)
        else -> if (dark) Color(0xFF8A8F98) else Color(0xFF9AA0A8)
    }
    val core by animateColorAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 600),
        label = "flower-colour",
    )
    return FlowerPalette(
        core = core,
        // On a light background the petals need more opacity to read at
        // all; on a dark one the same value would look like a solid blob.
        petalAlpha = if (dark) 0.34f else 0.46f,
        haloAlpha = if (dark) 0.16f else 0.10f,
    )
}

@Composable
fun VpnkaFlower(
    isRunning: Boolean,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = paletteFor(isRunning, isLoading)
    val transition = rememberInfiniteTransition(label = "flower-motion")

    // A slow breath while connected — enough to say "this is alive", far
    // too slow to nag. Static otherwise, because a flower pulsing at rest
    // would read as activity that isn't happening.
    val breath by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRunning) 1.045f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "flower-breath",
    )

    // Rotation only while connecting: it is the one moment where the user
    // is waiting and needs to see that something is happening.
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (isLoading) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "flower-spin",
    )

    Canvas(modifier = modifier) {
        val radius = min(size.width, size.height) / 2f
        val centre = Offset(size.width / 2f, size.height / 2f)

        drawHalo(centre, radius * breath, palette)
        rotate(degrees = spin, pivot = centre) {
            drawPetals(centre, radius * breath, palette)
        }
        drawCore(centre, radius * breath, palette)
    }
}

private fun DrawScope.drawHalo(
    centre: Offset,
    radius: Float,
    palette: FlowerPalette,
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                palette.core.copy(alpha = palette.haloAlpha),
                Color.Transparent,
            ),
            center = centre,
            radius = radius,
        ),
        radius = radius,
        center = centre,
    )
}

private fun DrawScope.drawPetals(
    centre: Offset,
    radius: Float,
    palette: FlowerPalette,
) {
    val petalLength = radius * 0.78f
    val petalWidth = radius * 0.42f

    for (index in 0 until PETALS) {
        rotate(degrees = 360f / PETALS * index, pivot = centre) {
            drawOval(
                brush = Brush.radialGradient(
                    // Brighter at the centre than at the tip, so the petals
                    // read as translucent rather than as flat shapes.
                    colors = listOf(
                        palette.core.copy(alpha = palette.petalAlpha),
                        palette.core.copy(alpha = palette.petalAlpha * 0.25f),
                    ),
                    center = centre,
                    radius = radius,
                ),
                topLeft = Offset(
                    x = centre.x - petalWidth / 2f,
                    y = centre.y - petalLength,
                ),
                size = Size(width = petalWidth, height = petalLength),
            )
        }
    }
}

private fun DrawScope.drawCore(
    centre: Offset,
    radius: Float,
    palette: FlowerPalette,
) {
    val coreRadius = radius * 0.3f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                palette.core.copy(alpha = 0.95f),
                palette.core.copy(alpha = 0.55f),
            ),
            center = centre,
            radius = coreRadius,
        ),
        radius = coreRadius,
        center = centre,
    )
}
