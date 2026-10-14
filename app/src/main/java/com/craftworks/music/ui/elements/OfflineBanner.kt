package com.craftworks.music.ui.elements

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.craftworks.music.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private val BannerHeight = 28.dp
private const val BackOnlineDurationMs = 2000L

private enum class BannerType(@StringRes val label: Int, @DrawableRes val icon: Int) {
    Offline(R.string.label_offline_mode, R.drawable.cloud_off_24px),
    BackOnline(R.string.label_back_online, R.drawable.cloud_done_24px)
}

/**
 * Shows an "Offline mode" banner at the top of the app above [content], and a short "Back online"
 * banner once the connection comes back. Both can be dismissed by swiping them sideways.
 *
 * Screens already pad themselves for the status bar, so instead of stacking the banner and the
 * content (which would double that padding), the content is only pushed down by the banner's own
 * height and the banner is drawn over the content's status bar area.
 */
@Composable
fun OfflineBannerHost(
    isOffline: Boolean,
    isTv: Boolean,
    content: @Composable () -> Unit
) {
    // The offline banner stays dismissed until the app goes offline again.
    var offlineDismissed by remember { mutableStateOf(false) }
    var showBackOnline by remember { mutableStateOf(false) }
    var wasOffline by remember { mutableStateOf(isOffline) }

    LaunchedEffect(isOffline) {
        if (isOffline) {
            offlineDismissed = false
            showBackOnline = false
            wasOffline = true
        } else if (wasOffline) {
            wasOffline = false
            showBackOnline = true
            delay(BackOnlineDurationMs)
            showBackOnline = false
        }
    }

    val banner = when {
        isOffline && !offlineDismissed -> BannerType.Offline
        !isOffline && showBackOnline -> BannerType.BackOnline
        else -> null
    }
    // Keep the last banner around so it can still be drawn while it animates out.
    val lastBanner = remember { mutableStateOf(BannerType.Offline) }
    if (banner != null) lastBanner.value = banner

    val contentOffset by animateDpAsState(if (banner != null) BannerHeight else 0.dp, label = "offlineBannerOffset")

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().padding(top = contentOffset)) {
            content()
        }

        AnimatedVisibility(
            visible = banner != null,
            enter = expandVertically(),
            exit = shrinkVertically(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            SwipeToDismissBanner(
                key = lastBanner.value,
                onDismiss = {
                    when (lastBanner.value) {
                        BannerType.Offline -> offlineDismissed = true
                        BannerType.BackOnline -> showBackOnline = false
                    }
                }
            ) {
                AnimatedContent(
                    targetState = lastBanner.value,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "connectivityBanner"
                ) { type ->
                    ConnectivityBanner(type, isTv)
                }
            }
        }
    }
}

/**
 * Lets [content] be dragged sideways. Releasing it far or fast enough flings it away and calls
 * [onDismiss]; otherwise it springs back. The position resets whenever [key] changes.
 */
@Composable
private fun SwipeToDismissBanner(
    key: Any,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }

    LaunchedEffect(key) { offsetX.snapTo(0f) }

    Box(
        Modifier
            .graphicsLayer {
                translationX = offsetX.value
                if (size.width > 0f) {
                    alpha = 1f - (abs(offsetX.value) / size.width).coerceIn(0f, 1f)
                }
            }
            .pointerInput(key) {
                val velocityTracker = VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = { velocityTracker.resetTracking() },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val target = offsetX.value + dragAmount
                        // The pointer position is relative to the moving banner, so track the
                        // banner's offset instead to get the real swipe velocity.
                        velocityTracker.addPosition(change.uptimeMillis, Offset(target, 0f))
                        scope.launch { offsetX.snapTo(target) }
                    },
                    onDragCancel = { scope.launch { offsetX.animateTo(0f) } },
                    onDragEnd = {
                        val velocity = velocityTracker.calculateVelocity().x
                        val width = size.width.toFloat()
                        val dismiss = abs(offsetX.value) > width / 3 || abs(velocity) > 1000f
                        scope.launch {
                            if (dismiss) {
                                val direction = if (offsetX.value + velocity / 10 < 0) -1f else 1f
                                offsetX.animateTo(direction * width)
                                onDismiss()
                            } else {
                                offsetX.animateTo(0f)
                            }
                        }
                    }
                )
            }
    ) {
        content()
    }
}

@Composable
private fun ConnectivityBanner(type: BannerType, isTv: Boolean) {
    val containerColor: Color
    val contentColor: Color
    if (isTv) {
        val colors = androidx.tv.material3.MaterialTheme.colorScheme
        containerColor = if (type == BannerType.Offline) colors.errorContainer else colors.primaryContainer
        contentColor = if (type == BannerType.Offline) colors.onErrorContainer else colors.onPrimaryContainer
    } else {
        val colors = MaterialTheme.colorScheme
        containerColor = if (type == BannerType.Offline) colors.errorContainer else colors.primaryContainer
        contentColor = if (type == BannerType.Offline) colors.onErrorContainer else colors.onPrimaryContainer
    }

    Box(
        Modifier
            .fillMaxWidth()
            .background(containerColor)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(BannerHeight),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(type.icon),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.padding(end = 8.dp).size(16.dp)
            )
            Text(
                text = stringResource(type.label),
                color = contentColor,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
