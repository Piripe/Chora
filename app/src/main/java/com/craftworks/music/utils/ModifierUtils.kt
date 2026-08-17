package com.craftworks.music.utils

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp

fun Modifier.bleedHorizontal(amount: Dp) = layout { measurable, constraints ->
    val extraPx = (amount * 2).roundToPx()
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.maxWidth + extraPx,
            maxWidth = constraints.maxWidth + extraPx
        )
    )
    layout(constraints.maxWidth, placeable.height) {
        placeable.place(-amount.roundToPx(), 0)
    }
}