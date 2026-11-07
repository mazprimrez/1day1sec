package com.onedayonesecond.ui.camera

import androidx.camera.core.Camera
import androidx.camera.core.FocusMeteringAction
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** What happened to a tap on the preview. */
enum class FocusOutcome { SEARCHING, LOCKED, MISSED }

/** The ring drawn where the user tapped. [id] tells one tap from the next. */
data class FocusRing(val id: Long, val x: Float, val y: Float, val outcome: FocusOutcome)

/**
 * Asks the camera to focus (and meter exposure and white balance) on a point of the
 * preview. CameraX only does this by itself when it is driven through a
 * CameraController; this app binds the camera by hand, so a tap has to be forwarded.
 *
 * After a few seconds the camera drops back to continuous autofocus on its own, so a
 * tap never leaves focus stuck on a spot the subject has since left.
 *
 * @param x,y the tap, in the preview view's own pixels.
 * @param onResult called on [executor] with the outcome, unless a later tap took over
 * (CameraX then cancels this request, and the newer tap reports instead).
 */
fun focusAt(
    camera: Camera,
    previewView: PreviewView,
    x: Float,
    y: Float,
    executor: Executor,
    onResult: (FocusOutcome) -> Unit,
) {
    // The factory turns view pixels into the sensor's coordinates, allowing for the
    // preview's crop and for the front camera being mirrored.
    val point = previewView.meteringPointFactory.createPoint(x, y)
    val action = FocusMeteringAction.Builder(point)
        .setAutoCancelDuration(AUTO_CANCEL_SECONDS, TimeUnit.SECONDS)
        .build()

    // Some front cameras are fixed-focus. Say so rather than showing a ring that
    // promises something the lens cannot do.
    if (!camera.cameraInfo.isFocusMeteringSupported(action)) {
        executor.execute { onResult(FocusOutcome.MISSED) }
        return
    }

    val pending = camera.cameraControl.startFocusAndMetering(action)
    pending.addListener(
        {
            val outcome = try {
                if (pending.get().isFocusSuccessful) FocusOutcome.LOCKED else FocusOutcome.MISSED
            } catch (e: Exception) {
                return@addListener // cancelled by a newer tap, or the camera was released
            }
            onResult(outcome)
        },
        executor,
    )
}

/**
 * Draws [ring] and removes it a moment after the camera has answered. If the camera
 * never answers (for example the lens was switched mid-focus) it is removed anyway.
 */
@Composable
fun FocusRingOverlay(ring: FocusRing?, onFinished: (id: Long) -> Unit) {
    if (ring == null) return

    val size = 72.dp
    val halfPx = with(LocalDensity.current) { (size / 2).toPx() }

    // A short "settling" pulse each time the ring appears.
    val scale = remember(ring.id) { Animatable(1.4f) }
    LaunchedEffect(ring.id) { scale.animateTo(1f, tween(durationMillis = 160)) }

    LaunchedEffect(ring.id) {
        delay(MAX_LIFETIME_MS)
        onFinished(ring.id)
    }
    LaunchedEffect(ring.id, ring.outcome) {
        if (ring.outcome != FocusOutcome.SEARCHING) {
            delay(LINGER_MS)
            onFinished(ring.id)
        }
    }

    Box(
        Modifier
            .offset { IntOffset((ring.x - halfPx).roundToInt(), (ring.y - halfPx).roundToInt()) }
            .size(size)
            .scale(scale.value)
            .border(
                width = 2.dp,
                color = if (ring.outcome == FocusOutcome.MISSED) MISSED_COLOR else Color.White,
                shape = CircleShape,
            ),
    )
}

private const val AUTO_CANCEL_SECONDS = 3L
private const val LINGER_MS = 700L
private const val MAX_LIFETIME_MS = 4_000L
private val MISSED_COLOR = Color(0xFFFF5252)
