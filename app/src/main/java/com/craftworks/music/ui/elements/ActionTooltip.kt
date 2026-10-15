package com.craftworks.music.ui.elements

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider

/**
 * Shows [text] in a tooltip next to [content] when it is long-pressed (or hovered with a mouse).
 * Wrap icon buttons with it so their action can be discovered.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionTooltip(
    text: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = rememberActionTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(text) } },
        state = rememberTooltipState(),
        modifier = modifier,
        content = content,
    )
}

/**
 * Places the tooltip far enough above the anchor to not be hidden by the finger pressing it.
 * When it would go under the status bar or the camera cutout, it is placed below the anchor
 * instead, further away since the finger covers more of what is below the touch point.
 */
@Composable
private fun rememberActionTooltipPositionProvider(): PopupPositionProvider {
    val density = LocalDensity.current
    val topInset = WindowInsets.safeDrawing.getTop(density)
    return remember(density, topInset) {
        val spacingAbove = with(density) { 24.dp.roundToPx() }
        val spacingBelow = with(density) { 48.dp.roundToPx() }
        val edgeMargin = with(density) { 8.dp.roundToPx() }

        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val maxX = windowSize.width - popupContentSize.width - edgeMargin
                val x = (anchorBounds.center.x - popupContentSize.width / 2)
                    .coerceAtMost(maxX)
                    .coerceAtLeast(edgeMargin)

                val above = anchorBounds.top - spacingAbove - popupContentSize.height
                val y = if (above >= topInset + edgeMargin) above
                    else anchorBounds.bottom + spacingBelow

                return IntOffset(x, y)
            }
        }
    }
}
