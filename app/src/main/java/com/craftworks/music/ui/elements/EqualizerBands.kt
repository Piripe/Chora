package com.craftworks.music.ui.elements

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalSlider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.craftworks.music.managers.EqualizerInfo
import com.craftworks.music.managers.EqualizerManager
import kotlin.math.roundToInt

/** One vertical slider per equalizer band, in 1 dB steps, with its level above and its frequency below. */
@Composable
fun EqualizerBands(
    info: EqualizerInfo,
    bandLevels: List<Int>,
    enabled: Boolean,
    onBandLevelChange: (band: Int, level: Int) -> Unit,
    modifier: Modifier = Modifier,
    sliderHeight: Dp = 260.dp,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val steps = ((info.maxLevel - info.minLevel) / 100 - 1).coerceAtLeast(0)
    val range = info.minLevel.toFloat()..info.maxLevel.toFloat()

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        info.bandFrequencies.forEachIndexed { band, frequency ->
            val level = bandLevels.getOrElse(band) { 0 }
            val state = remember(info) { SliderState(level.toFloat(), steps, range) }
            val interactionSource = remember { MutableInteractionSource() }
            val dragged by interactionSource.collectIsDraggedAsState()

            // Follows the saved level (e.g. a preset), but not while the thumb is held.
            LaunchedEffect(level, dragged) {
                if (!dragged) state.value = level.toFloat()
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "%+d".format((state.value / 100).roundToInt()),
                    style = MaterialTheme.typography.labelLarge,
                    color = textColor.copy(alpha = if (enabled) 1f else 0.5f),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                VerticalSlider(
                    state = state,
                    onValueChange = {
                        state.value = it
                        val snapped = ((it / 100).roundToInt() * 100).coerceIn(info.minLevel, info.maxLevel)
                        if (snapped != level) onBandLevelChange(band, snapped)
                    },
                    enabled = enabled,
                    topToBottom = false,
                    interactionSource = interactionSource,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .height(sliderHeight)
                )
                Text(
                    text = EqualizerManager.frequencyLabel(frequency),
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = if (enabled) 1f else 0.5f),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}
