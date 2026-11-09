package io.github.caendeith.awakr.ui.background

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.random.Random

/**
 * Paints the built-in backgrounds.
 *
 * Everything is drawn with gradients and paths, so the app ships no bitmap assets: the same id
 * renders as a 60 dp tile in the picker and as a full screen ring background.
 */
@Composable
fun ProceduralBackground(key: String, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        when (key) {
            "ink" -> fillVertical(listOf(InkTop, InkRaised, InkDeep))
            "amber" -> fillDiagonal(listOf(Color(0xFF161006), Color(0xFF6B4000), Amber))
            "ember" -> fillVertical(listOf(Color(0xFF100605), Color(0xFF5A1A0E), Ember))
            "aurora" -> fillDiagonal(listOf(Color(0xFF04121A), Color(0xFF0E3B3A), Teal, Color(0xFF123A5C)))
            "dawn" -> fillVertical(listOf(Color(0xFF190D15), Color(0xFF4A1F30), Color(0xFFC97B4A)))
            "void" -> {
                fillVertical(listOf(Color(0xFF05060A), Color(0xFF10131C), Color(0xFF07080B)))
                sparkles(seed = 11, count = 26, alpha = 0.5f)
            }

            "moss" -> fillVertical(listOf(Color(0xFF06120C), Color(0xFF12301F), Color(0xFF2A5D3A)))
            "rose" -> fillDiagonal(
                listOf(Color(0xFF180A10), Color(0xFF451A28), Color(0xFFB25A6A)),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height * 0.9f),
            )

            "starry" -> starry()
            "mountains" -> mountains()
            "dunes" -> dunes()
            "fog" -> fog()
            else -> fillVertical(listOf(InkTop, InkDeep))
        }
    }
}

/** base gradient + deterministic stars; the seed keeps the sky identical between recompositions. */
private fun DrawScope.starry() {
    fillVertical(listOf(Color(0xFF07080C), Color(0xFF111827), Color(0xFF07080B)))
    glow(Amber, center = Offset(size.width * 0.5f, size.height * 0.12f), radius = size.width * 0.9f, alpha = 0.10f)

    val random = Random(20260214)
    val count = ((size.width * size.height) / 3600f).toInt().coerceIn(60, 220)
    repeat(count) {
        val x = random.nextFloat() * size.width
        val y = random.nextFloat() * size.height * 0.86f
        val radius = 0.8f + random.nextFloat() * 2.1f
        val alpha = 0.2f + random.nextFloat() * 0.8f
        val tint = if (random.nextFloat() > 0.88f) Amber else Color.White
        drawCircle(tint.copy(alpha = alpha), radius, Offset(x, y))
        if (radius > 2.4f) drawCircle(tint.copy(alpha = alpha * 0.18f), radius * 3.4f, Offset(x, y))
    }
}

/** Three ridge lines over a sky that glows amber just above the horizon. */
private fun DrawScope.mountains() {
    fillVertical(listOf(Color(0xFF0A0D14), Color(0xFF1B2634), Color(0xFF0B0C0E)))
    glow(Amber, center = Offset(size.width * 0.68f, size.height * 0.6f), radius = size.width * 0.7f, alpha = 0.13f)

    val ridges = listOf(
        Triple(0.58f, 0.16f, Color(0xFF16202C)),
        Triple(0.68f, 0.11f, Color(0xFF101820)),
        Triple(0.79f, 0.07f, Color(0xFF0A0E13)),
    )
    ridges.forEachIndexed { index, (baseline, peak, color) ->
        val path = Path()
        path.moveTo(0f, size.height)
        path.lineTo(0f, size.height * baseline)
        val peaks = listOf(0.22f, 0.46f, 0.72f, 0.92f)
        peaks.forEachIndexed { peakIndex, x ->
            val height = size.height * (baseline - peak * (0.7f + 0.3f * ((peakIndex + index) % 2)))
            path.lineTo(size.width * x, height)
            path.lineTo(size.width * (x + 0.11f), size.height * baseline)
        }
        path.lineTo(size.width, size.height)
        path.close()
        drawPath(path, color)
    }
}

/** Warm sand bands that slide sideways, one over the other. */
private fun DrawScope.dunes() {
    fillVertical(listOf(Color(0xFF140F0A), Color(0xFF2A1B10), Color(0xFF0B0C0E)))
    glow(Amber, center = Offset(size.width * 0.24f, size.height * 0.16f), radius = size.width, alpha = 0.12f)

    val bands = listOf(
        0.52f to Color(0xFF2C1D12),
        0.63f to Color(0xFF21160E),
        0.74f to Color(0xFF17100B),
        0.86f to Color(0xFF0E0A08),
    )
    bands.forEachIndexed { index, (top, color) ->
        val wave = 0.05f - index * 0.008f
        val path = Path()
        path.moveTo(0f, size.height * (top + wave))
        path.cubicTo(
            size.width * 0.3f, size.height * (top - wave),
            size.width * 0.7f, size.height * (top + wave * 1.6f),
            size.width, size.height * (top - wave * 0.4f),
        )
        path.lineTo(size.width, size.height)
        path.lineTo(0f, size.height)
        path.close()
        drawPath(path, color)
    }
}

/** Soft overlapping clouds of light; no hard edges anywhere. */
private fun DrawScope.fog() {
    fillVertical(listOf(Color(0xFF090C0F), Color(0xFF141C22), Color(0xFF08090B)))
    val blobs = listOf(
        Triple(0.22f, 0.32f, Color(0xFF2E7D6B)),
        Triple(0.74f, 0.24f, Amber),
        Triple(0.54f, 0.58f, Color(0xFF42586B)),
        Triple(0.18f, 0.78f, Color(0xFF2E7D6B)),
        Triple(0.86f, 0.72f, Color(0xFF6B4A2E)),
    )
    blobs.forEachIndexed { index, (x, y, color) ->
        glow(
            color,
            center = Offset(size.width * x, size.height * y),
            radius = size.width * (0.55f + index * 0.05f),
            alpha = 0.09f,
        )
    }
}

/** Small deterministic dust for the darkest gradients; keeps a flat fill from looking dead. */
private fun DrawScope.sparkles(seed: Int, count: Int, alpha: Float) {
    val random = Random(seed)
    repeat(count) {
        val x = random.nextFloat() * size.width
        val y = random.nextFloat() * size.height
        drawCircle(
            Amber.copy(alpha = alpha * (0.15f + random.nextFloat() * 0.45f)),
            0.7f + random.nextFloat() * 1.3f,
            Offset(x, y),
        )
    }
}

private fun DrawScope.fillVertical(colors: List<Color>) {
    drawRect(
        Brush.linearGradient(
            colors = colors,
            start = Offset(0f, 0f),
            end = Offset(size.width * 0.35f, size.height),
        ),
    )
}

private fun DrawScope.fillDiagonal(
    colors: List<Color>,
    start: Offset = Offset.Zero,
    end: Offset = Offset.Infinite,
) {
    drawRect(Brush.linearGradient(colors = colors, start = start, end = end))
}

private fun DrawScope.glow(color: Color, center: Offset, radius: Float, alpha: Float) {
    if (radius <= 0f) return
    drawRect(
        Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), Color.Transparent),
            center = center,
            radius = radius,
        ),
    )
}

private val InkTop = Color(0xFF0B0C0E)
private val InkRaised = Color(0xFF171B22)
private val InkDeep = Color(0xFF080A0D)
private val Amber = Color(0xFFFFB020)
private val Ember = Color(0xFFE0632A)
private val Teal = Color(0xFF2E7D6B)
