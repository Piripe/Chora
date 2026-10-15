package com.craftworks.music.ui.playing

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/** The feedback shown on the cover after a gesture. [id] restarts the animation when the same action is repeated. */
private data class CoverFeedback(
    val id: Long,
    val action: NowPlayingAction,
    val active: Boolean,
    val gesture: NowPlayingGesture,
)

/** Part of the cover size a swipe must travel to trigger its action (unless flung). */
private const val SWIPE_THRESHOLD = 0.2f
/** How much the cover follows the finger while swiping. */
private const val SWIPE_RESISTANCE = 0.35f
private val SwipeFlingVelocity = 800.dp

/**
 * Runs the [gestures] actions when the cover ([content]) is tapped, double tapped, long pressed or swiped.
 *
 * The cover shrinks while pressed, follows the finger while swiped, and the action icon pops over it once done.
 * Swipes are only intercepted on an axis that has an action, so the others still reach the parent
 * (e.g. swiping down to close the now playing sheet).
 *
 * @param isActiveAfter whether the action is "on" once run, to pick the feedback icon (e.g. a filled heart).
 */
@Composable
fun NowPlayingCoverGestures(
    gestures: Map<NowPlayingGesture, NowPlayingAction>,
    onAction: (NowPlayingAction) -> Unit,
    isActiveAfter: (NowPlayingAction) -> Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val currentGestures by rememberUpdatedState(gestures)
    val currentOnAction by rememberUpdatedState(onAction)
    val currentIsActiveAfter by rememberUpdatedState(isActiveAfter)

    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val pressScale = remember { Animatable(1f) }
    var feedback by remember { mutableStateOf<CoverFeedback?>(null) }

    fun trigger(gesture: NowPlayingGesture) {
        val action = currentGestures[gesture] ?: return
        // The state is read before running the action: the feedback shows the state it switches to.
        val active = currentIsActiveAfter(action)
        haptics.performHapticFeedback(
            if (gesture == NowPlayingGesture.LONG_PRESS) HapticFeedbackType.LongPress
            else HapticFeedbackType.Confirm
        )
        currentOnAction(action)
        feedback = CoverFeedback(System.nanoTime(), action, active, gesture)
    }

    val hasTap = NowPlayingGesture.TAP in gestures
    val hasDoubleTap = NowPlayingGesture.DOUBLE_TAP in gestures
    val hasLongPress = NowPlayingGesture.LONG_PRESS in gestures
    val hasHorizontalSwipe = NowPlayingGesture.SWIPE_LEFT in gestures || NowPlayingGesture.SWIPE_RIGHT in gestures
    val hasVerticalSwipe = NowPlayingGesture.SWIPE_UP in gestures || NowPlayingGesture.SWIPE_DOWN in gestures

    val onTap: ((Offset) -> Unit)? = if (hasTap) { { _ -> trigger(NowPlayingGesture.TAP) } } else null
    val onDoubleTap: ((Offset) -> Unit)? = if (hasDoubleTap) { { _ -> trigger(NowPlayingGesture.DOUBLE_TAP) } } else null
    val onLongPress: ((Offset) -> Unit)? = if (hasLongPress) { { _ -> trigger(NowPlayingGesture.LONG_PRESS) } } else null

    BoxWithConstraints(
        modifier = modifier
            .then(
                if (hasTap || hasDoubleTap || hasLongPress)
                    Modifier.pointerInput(hasTap, hasDoubleTap, hasLongPress) {
                        detectTapGestures(
                            onPress = {
                                scope.launch { pressScale.animateTo(0.96f, spring(stiffness = Spring.StiffnessMediumLow)) }
                                tryAwaitRelease()
                                scope.launch { pressScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)) }
                            },
                            // Without a double tap action, a tap doesn't have to wait for a possible second tap.
                            onTap = onTap,
                            onDoubleTap = onDoubleTap,
                            onLongPress = onLongPress,
                        )
                    }
                else Modifier
            )
            .then(
                if (hasHorizontalSwipe || hasVerticalSwipe)
                    Modifier.pointerInput(hasHorizontalSwipe, hasVerticalSwipe) {
                        val flingVelocity = with(density) { SwipeFlingVelocity.toPx() }
                        coroutineScope {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val velocityTracker = VelocityTracker()
                                velocityTracker.addPosition(down.uptimeMillis, down.position)

                                // Wait for the touch slop to know the swipe axis.
                                var total = Offset.Zero
                                var horizontal = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                                    if (!change.pressed || change.isConsumed) return@awaitEachGesture
                                    total += change.positionChange()
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    // Swipes towards a direction without action are left to the parent
                                    // (e.g. swiping down closes the now playing sheet).
                                    val slop = viewConfiguration.touchSlop
                                    val verticalGesture = if (total.y < 0) NowPlayingGesture.SWIPE_UP else NowPlayingGesture.SWIPE_DOWN
                                    val horizontalGesture = if (total.x < 0) NowPlayingGesture.SWIPE_LEFT else NowPlayingGesture.SWIPE_RIGHT

                                    // A diagonal swipe towards a vertical direction without action must be clearly
                                    // horizontal to be taken, so it doesn't get in the way of the parent.
                                    val ratio = if (verticalGesture in currentGestures) 1f else 2f
                                    if (abs(total.x) > slop && abs(total.x) > abs(total.y) * ratio) {
                                        if (horizontalGesture !in currentGestures) return@awaitEachGesture
                                        horizontal = true
                                        change.consume()
                                        break
                                    }
                                    if (abs(total.y) > slop) {
                                        if (verticalGesture !in currentGestures) return@awaitEachGesture
                                        if (abs(total.y) >= abs(total.x)) {
                                            horizontal = false
                                            change.consume()
                                            break
                                        }
                                    }
                                }

                                val extent = if (horizontal) size.width.toFloat() else size.height.toFloat()
                                var distance = if (horizontal) total.x else total.y

                                fun gestureFor(direction: Float) = when {
                                    horizontal && direction < 0 -> NowPlayingGesture.SWIPE_LEFT
                                    horizontal -> NowPlayingGesture.SWIPE_RIGHT
                                    direction < 0 -> NowPlayingGesture.SWIPE_UP
                                    else -> NowPlayingGesture.SWIPE_DOWN
                                }

                                fun follow() {
                                    // Barely move towards a direction without action.
                                    val resistance = if (gestureFor(distance) in currentGestures) SWIPE_RESISTANCE else 0.08f
                                    val moved = distance * resistance
                                    launch { offset.snapTo(if (horizontal) Offset(moved, 0f) else Offset(0f, moved)) }
                                }
                                follow()

                                drag(down.id) { change ->
                                    change.consume()
                                    val delta = change.positionChange()
                                    distance += if (horizontal) delta.x else delta.y
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    follow()
                                }

                                val velocity = velocityTracker.calculateVelocity().let { if (horizontal) it.x else it.y }
                                val flung = abs(velocity) > flingVelocity && sign(velocity) == sign(distance)
                                if (abs(distance) > extent * SWIPE_THRESHOLD || flung)
                                    trigger(gestureFor(distance))

                                launch { offset.animateTo(Offset.Zero, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)) }
                            }
                        }
                    }
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.fillMaxSize().graphicsLayer {
                translationX = offset.value.x
                translationY = offset.value.y
                scaleX = pressScale.value
                scaleY = pressScale.value
            }
        ) {
            content()
        }

        feedback?.let { CoverFeedbackIcon(it, maxWidth.value) }
    }
}

/** The action icon popping over the cover. Swipes make it come from the side the cover was swiped to. */
@Composable
private fun CoverFeedbackIcon(feedback: CoverFeedback, coverSizeDp: Float) {
    val alpha = remember(feedback.id) { Animatable(0f) }
    val scale = remember(feedback.id) { Animatable(0.6f) }
    val slide = remember(feedback.id) { Animatable(1f) }

    LaunchedEffect(feedback.id) {
        launch { slide.animateTo(0f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)) }
        launch { scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) }
        alpha.animateTo(1f, tween(120))
        alpha.animateTo(0f, tween(durationMillis = 350, delayMillis = 450))
    }

    val slideDistance = (coverSizeDp / 6).dp
    val (slideX, slideY) = when (feedback.gesture) {
        NowPlayingGesture.SWIPE_LEFT -> -1f to 0f
        NowPlayingGesture.SWIPE_RIGHT -> 1f to 0f
        NowPlayingGesture.SWIPE_UP -> 0f to -1f
        NowPlayingGesture.SWIPE_DOWN -> 0f to 1f
        else -> 0f to 0f
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(88.dp)
            .graphicsLayer {
                this.alpha = alpha.value
                scaleX = scale.value
                scaleY = scale.value
                translationX = slideX * slide.value * slideDistance.toPx()
                translationY = slideY * slide.value * slideDistance.toPx()
            }
            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
    ) {
        Icon(
            imageVector = nowPlayingActionIcon(feedback.action, feedback.active),
            contentDescription = nowPlayingActionLabel(feedback.action),
            tint = Color.White,
            modifier = Modifier.size(44.dp)
        )
    }
}
