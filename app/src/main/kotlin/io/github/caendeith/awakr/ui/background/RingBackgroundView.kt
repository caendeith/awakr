package io.github.caendeith.awakr.ui.background

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import io.github.caendeith.awakr.AwakrApplication
import io.github.caendeith.awakr.core.alarm.Alarm
import io.github.caendeith.awakr.data.settings.AppSettings
import kotlinx.coroutines.flow.collect

/** What the ring screen should draw behind its content. [id] null keeps the plain dark screen. */
data class RingBackgroundStyle(
    val id: String?,
    val dimPercent: Int,
)

/**
 * Resolves the background of a ringing alarm: the alarm's own choice, then the app setting, then
 * nothing. Settings are read from the app container here so that the ring activity does not have to
 * thread them through.
 */
@Composable
fun rememberRingBackground(alarm: Alarm?): RingBackgroundStyle {
    val context = LocalContext.current
    val settings by produceState<AppSettings?>(initialValue = null) {
        val repository = runCatching {
            (context.applicationContext as? AwakrApplication)?.data?.settings
        }.getOrNull()
        if (repository == null) {
            value = AppSettings()
        } else {
            repository.settings.collect { value = it }
        }
    }
    val alarmBackgroundId = alarm?.backgroundId
    return remember(context, alarmBackgroundId, settings) {
        val current = settings
        RingBackgroundStyle(
            id = BackgroundStore.resolve(context, alarmBackgroundId, current?.ringBackgroundId),
            dimPercent = current?.ringBackgroundDim ?: DEFAULT_DIM_PERCENT,
        )
    }
}

/**
 * Draws the ring background behind the alarm content.
 *
 * The image fades in over [FADE_MILLIS] and drifts with a slow ken-burns zoom, and a scrim keeps
 * the time, the wake gate and the mission readable on top of a bright photo. A blank or broken id
 * draws nothing at all, so the shipped look is untouched when no background is configured.
 *
 * @param id `builtin:<key>`, `file:<path>` or null for none
 * @param dimPercent even darkening on top of the gradient scrim, 0..60
 */
@Composable
fun RingBackgroundView(id: String?, dimPercent: Int, modifier: Modifier = Modifier) {
    val ref = remember(id) { BackgroundIds.parse(id) } ?: return
    // A key that left the catalog must not paint a black hole over the ring screen.
    if (ref is BackgroundRef.Builtin && BackgroundCatalog.byKey(ref.key) == null) return

    val fade = remember(ref) { Animatable(0f) }
    if (fade.value < 1f) {
        androidx.compose.runtime.LaunchedEffect(ref) {
            fade.animateTo(1f, animationSpec = tween(FADE_MILLIS, easing = LinearEasing))
        }
    }

    val drift = rememberInfiniteTransition(label = "ring-background")
    val zoom by drift.animateFloat(
        initialValue = 1f,
        targetValue = 1f + KEN_BURNS_SCALE,
        animationSpec = infiniteRepeatable(
            animation = tween(KEN_BURNS_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ken-burns",
    )

    Box(modifier.fillMaxSize().clipToBounds()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = fade.value
                    scaleX = zoom
                    scaleY = zoom
                },
        ) {
            when (ref) {
                is BackgroundRef.Builtin -> ProceduralBackground(ref.key, Modifier.fillMaxSize())
                is BackgroundRef.Local -> PhotoLayer(ref.path, Modifier.fillMaxSize())
            }
        }
        // Scrim stays outside the zoom so the readability contract never moves.
        BackgroundScrim(dimPercent, Modifier.fillMaxSize())
    }
}

@Composable
private fun PhotoLayer(path: String, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path) {
        value = BackgroundStore.load(path)
    }
    val image = bitmap ?: return
    Image(
        bitmap = image,
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop,
    )
}

/**
 * Readability layer: a vertical gradient (10% at the top where the time sits, 55% at the bottom
 * where the buttons are, with a mid stop so the mission card never floats on a bright patch) plus
 * the user's even dimming.
 */
@Composable
private fun BackgroundScrim(dimPercent: Int, modifier: Modifier = Modifier) {
    val dim = dimPercent.coerceIn(MIN_DIM_PERCENT, MAX_DIM_PERCENT) / 100f
    Canvas(modifier) {
        if (dim > 0f) drawRect(Color.Black.copy(alpha = dim))
        drawRect(
            Brush_vertical(
                stops = listOf(
                    0f to Color.Black.copy(alpha = SCRIM_TOP),
                    0.45f to Color.Black.copy(alpha = SCRIM_MIDDLE),
                    1f to Color.Black.copy(alpha = SCRIM_BOTTOM),
                ),
            ),
        )
    }
}

private fun Brush_vertical(stops: List<Pair<Float, Color>>): androidx.compose.ui.graphics.Brush =
    androidx.compose.ui.graphics.Brush.verticalGradient(colorStops = *stops.toTypedArray())

private const val FADE_MILLIS = 600
private const val KEN_BURNS_MILLIS = 12_000
private const val KEN_BURNS_SCALE = 0.04f

private const val SCRIM_TOP = 0.10f
private const val SCRIM_MIDDLE = 0.28f
private const val SCRIM_BOTTOM = 0.55f

const val DEFAULT_DIM_PERCENT = 35
const val MIN_DIM_PERCENT = 0
const val MAX_DIM_PERCENT = 60
