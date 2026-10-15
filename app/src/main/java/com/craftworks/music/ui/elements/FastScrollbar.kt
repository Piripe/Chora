package com.craftworks.music.ui.elements

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A scrollbar for the list of [state], to be placed over its end edge (it fills the height it is given).
 * Its thumb shows while the list scrolls, and can be dragged to move through long lists quickly.
 */
@Composable
fun LazyListScrollbar(state: LazyListState, modifier: Modifier = Modifier) {
    FastScrollbar(
        state = state,
        metrics = {
            val info = state.layoutInfo
            ScrollbarMetrics(
                totalItems = info.totalItemsCount,
                visibleItems = info.visibleItemsInfo.size,
                firstIndex = state.firstVisibleItemIndex,
                firstOffset = state.firstVisibleItemScrollOffset,
                firstSize = info.visibleItemsInfo.firstOrNull()?.size ?: 0
            )
        },
        scrollToItem = { state.scrollToItem(it) },
        modifier = modifier
    )
}

/** A scrollbar for the grid of [state], see [LazyListScrollbar]. */
@Composable
fun LazyGridScrollbar(state: LazyGridState, modifier: Modifier = Modifier) {
    FastScrollbar(
        state = state,
        metrics = {
            val info = state.layoutInfo
            ScrollbarMetrics(
                totalItems = info.totalItemsCount,
                visibleItems = info.visibleItemsInfo.size,
                firstIndex = state.firstVisibleItemIndex,
                firstOffset = state.firstVisibleItemScrollOffset,
                firstSize = info.visibleItemsInfo.firstOrNull()?.size?.height ?: 0
            )
        },
        scrollToItem = { state.scrollToItem(it) },
        modifier = modifier
    )
}

/** What the scrollbar knows of a lazy list or grid: its item counts, and the size and offset of the first visible item. */
private class ScrollbarMetrics(
    val totalItems: Int,
    val visibleItems: Int,
    val firstIndex: Int,
    val firstOffset: Int,
    val firstSize: Int
)

@Composable
private fun FastScrollbar(
    state: ScrollableState,
    metrics: () -> ScrollbarMetrics,
    scrollToItem: suspend (index: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val minThumbHeightPx = with(density) { 48.dp.toPx() }

    var trackHeightPx by remember { mutableIntStateOf(0) }
    var isDragging by remember { mutableStateOf(false) }
    // Where the thumb was dragged to, as a fraction of the scroll range: it follows the finger, not the list.
    var dragFraction by remember { mutableFloatStateOf(0f) }

    // How far the list is scrolled (0 to 1), and how much of it is visible, estimated from the item counts.
    val scrollFraction by remember(state) {
        derivedStateOf {
            val list = metrics()
            val range = list.totalItems - list.visibleItems
            if (range <= 0) return@derivedStateOf 0f
            val scrolled = list.firstIndex + list.firstOffset.toFloat() / list.firstSize.coerceAtLeast(1)
            if (!state.canScrollForward) 1f else (scrolled / range).coerceIn(0f, 1f)
        }
    }
    val visibleFraction by remember(state) {
        derivedStateOf {
            val list = metrics()
            if (list.totalItems == 0) 1f
            else (list.visibleItems.toFloat() / list.totalItems).coerceIn(0f, 1f)
        }
    }
    val isScrollable by remember(state) { derivedStateOf { state.canScrollForward || state.canScrollBackward } }

    // Shown while scrolling or dragging, and a moment after.
    var isShown by remember { mutableStateOf(false) }
    LaunchedEffect(state.isScrollInProgress, isDragging) {
        if (state.isScrollInProgress || isDragging) isShown = true
        else {
            delay(1500)
            isShown = false
        }
    }

    val thumbHeightPx = maxOf(minThumbHeightPx, trackHeightPx * visibleFraction).coerceAtMost(trackHeightPx.toFloat())
    // Read by the drag gesture, which isn't restarted when the thumb size changes.
    val currentThumbHeightPx by rememberUpdatedState(thumbHeightPx)
    val fraction = if (isDragging) dragFraction else scrollFraction
    val thumbOffsetPx = ((trackHeightPx - thumbHeightPx) * fraction).roundToInt()
    val thumbWidth by animateDpAsState(if (isDragging) 8.dp else 6.dp, label = "Scrollbar thumb width")

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(32.dp)
            .onSizeChanged { trackHeightPx = it.height }
    ) {
        AnimatedVisibility(
            visible = isScrollable && (isShown || isDragging),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.offset { IntOffset(0, thumbOffsetPx) }
        ) {
            // The touch area is wider than the visible thumb, so it is easy to grab.
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(with(density) { thumbHeightPx.toDp() })
                    .pointerInput(state) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                dragFraction = scrollFraction
                                isDragging = true
                            },
                            onDragEnd = { isDragging = false },
                            onDragCancel = { isDragging = false },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                val range = trackHeightPx - currentThumbHeightPx
                                if (range <= 0f) return@detectVerticalDragGestures
                                dragFraction = (dragFraction + dragAmount / range).coerceIn(0f, 1f)
                                val list = metrics()
                                val target = dragFraction * (list.totalItems - list.visibleItems).coerceAtLeast(0)
                                coroutineScope.launch {
                                    if (dragFraction >= 1f) scrollToItem((list.totalItems - 1).coerceAtLeast(0))
                                    else scrollToItem(target.toInt())
                                }
                            }
                        )
                    }
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp)
                        .width(thumbWidth)
                        .fillMaxHeight()
                        .background(
                            if (isDragging) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            RoundedCornerShape(50)
                        )
                )
            }
        }
    }
}
