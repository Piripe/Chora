@file:androidx.annotation.OptIn(UnstableApi::class)

package com.craftworks.music.ui.playing

import android.util.Log
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import com.craftworks.music.R
import com.craftworks.music.ui.elements.ActionTooltip
import com.craftworks.music.ui.elements.LocalExpressiveDesign
import com.craftworks.music.ui.elements.bounceClick
import com.craftworks.music.utils.StringUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun PlaybackProgressSlider(
    color: Color = MaterialTheme.colorScheme.onBackground,
    mediaController: MediaController? = null,
    metadata: MediaMetadata? = null,
    style: NowPlayingSeekBarStyle = NowPlayingSeekBarStyle.STANDARD,
) {
    var currentValue by remember { mutableLongStateOf(0L) }
    val currentDuration by remember(mediaController?.duration) {
        derivedStateOf {
            mediaController?.duration?.coerceAtLeast(0L)
        }
    }

    val animatedValue by animateFloatAsState(
        targetValue = currentValue.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "Smooth Slider Update"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val focused = remember { mutableStateOf(false) }
    var isInteracting by remember { mutableStateOf(false) }

    var isPlaying by remember { mutableStateOf(false) }

    LaunchedEffect(mediaController, isPlaying) {
        if (mediaController != null && isPlaying) {
            while (isActive && !isInteracting) {
                currentValue = mediaController.currentPosition
                delay(1000L)
            }
        } else {
            if (mediaController != null) {
                currentValue = mediaController.currentPosition
            }
        }
    }

    DisposableEffect(mediaController) {
        if (mediaController == null) {
            onDispose { }
        }

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                Log.d("TAG", "MediaController isPlaying changed: $playing")
                isPlaying = playing
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                super.onPositionDiscontinuity(oldPosition, newPosition, reason)
                if (reason != Player.DISCONTINUITY_REASON_SEEK)
                    currentValue = newPosition.positionMs
            }
        }

        mediaController?.addListener(listener)

        // Initial check in case state changed before listener was attached or for initial setup
        isPlaying = mediaController?.isPlaying ?: false
        currentValue = mediaController?.currentPosition ?: 0L

        onDispose {
            mediaController?.removeListener(listener)
        }
    }

    Column(
        Modifier.focusable(false)
    ) {
        SeekBar(
            value = animatedValue,
            valueRange = 0f..(currentDuration?.toFloat() ?: 0f),
            style = style,
            color = color,
            isPlaying = isPlaying,
            enabled = metadata?.mediaType != MediaMetadata.MEDIA_TYPE_RADIO_STATION,
            interactionSource = interactionSource,
            onValueChange = {
                isInteracting = true
                currentValue = it.toLong()
            },
            onValueChangeFinished = {
                isInteracting = false
                mediaController?.seekTo(currentValue)
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged {
                    focused.value = it.isFocused
                }
                .onKeyEvent { keyEvent ->
                    when {
                        keyEvent.key == Key.DirectionRight && keyEvent.type == KeyEventType.KeyDown -> {
                            currentValue = (currentValue + 5000)
                            mediaController?.seekTo(currentValue)
                            true
                        }

                        keyEvent.key == Key.DirectionLeft && keyEvent.type == KeyEventType.KeyDown -> {
                            currentValue = (currentValue - 5000)
                            mediaController?.seekTo(currentValue)
                            true
                        }

                        else -> false
                    }
                },
        )

        // Time thingies
        Box(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Text(
                text = remember(currentValue) { StringUtils.formatSeconds(currentValue / 1000) },
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Start,
                color = color.copy(alpha = 0.5f),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(64.dp),
                maxLines = 1
            )
            Text(
                text = remember(currentDuration) { StringUtils.formatSeconds(currentDuration?.div(1000) ?: (currentValue/1000)) },
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.End,
                color = color.copy(alpha = 0.5f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(64.dp),
                maxLines = 1
            )
        }
    }
}

/**
 * The progress slider, drawn with one of the Material 3 Expressive [NowPlayingSeekBarStyle]s.
 * The wavy track only waves while playing.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SeekBar(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    style: NowPlayingSeekBarStyle,
    color: Color,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onValueChange: (Float) -> Unit = {},
    onValueChangeFinished: () -> Unit = {},
) {
    val colors = SliderDefaults.colors(
        activeTrackColor = color,
        inactiveTrackColor = color.copy(alpha = 0.25f),
        thumbColor = color
    )
    val state = remember(valueRange) { SliderState(value = value, trackRange = valueRange) }
    state.value = value

    val isDragged by interactionSource.collectIsDraggedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isInteracting = isDragged || isPressed

    Slider(
        state = state,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
        thumb = {
            when (style) {
                NowPlayingSeekBarStyle.STANDARD -> SliderDefaults.Thumb(
                    interactionSource = interactionSource,
                    colors = colors,
                    enabled = enabled,
                )
                NowPlayingSeekBarStyle.WAVY -> SliderDefaults.Thumb(
                    interactionSource = interactionSource,
                    colors = colors,
                    enabled = enabled,
                    thumbSize = DpSize(4.dp, 24.dp),
                )
                NowPlayingSeekBarStyle.THICK -> SliderDefaults.Thumb(
                    interactionSource = interactionSource,
                    colors = colors,
                    enabled = enabled,
                    thumbSize = DpSize(4.dp, 48.dp),
                )
                NowPlayingSeekBarStyle.MINIMAL -> {
                    val thumbSize by animateDpAsState(
                        targetValue = if (isInteracting) 18.dp else 12.dp,
                        label = "Seek bar thumb size"
                    )
                    // The slider measures the thumb with the track height as minimum height, which would
                    // stretch the small thumb and push it off-center: it is centered in a fixed box instead.
                    Box(
                        modifier = Modifier.size(18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        SliderDefaults.Thumb(
                            interactionSource = interactionSource,
                            colors = colors,
                            enabled = enabled,
                            thumbSize = DpSize(thumbSize, thumbSize),
                        )
                    }
                }
            }
        },
        track = { sliderState ->
            when (style) {
                NowPlayingSeekBarStyle.STANDARD -> SliderDefaults.Track(
                    sliderState = sliderState,
                    colors = colors,
                    enabled = enabled,
                )
                NowPlayingSeekBarStyle.WAVY -> {
                    // The wave flattens when paused or while seeking. The indicator animates the amplitude
                    // changes itself, and ignores new targets while doing so: it must only get the final
                    // value, not an animated one.
                    val waving = isPlaying && !isInteracting
                    val amplitude = remember(waving) { { _: Float -> if (waving) 1f else 0f } }
                    val range = sliderState.trackRange
                    val length = range.endInclusive - range.start
                    LinearWavyProgressIndicator(
                        progress = {
                            if (length <= 0f) 0f
                            else ((sliderState.value - range.start) / length).coerceIn(0f, 1f)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        color = color,
                        trackColor = color.copy(alpha = 0.25f),
                        amplitude = amplitude,
                        waveSpeed = if (waving) WavyProgressIndicatorDefaults.LinearDeterminateWavelength else 0.dp,
                    )
                }
                NowPlayingSeekBarStyle.THICK -> SliderDefaults.Track(
                    sliderState = sliderState,
                    trackCornerSize = 12.dp,
                    modifier = Modifier.height(32.dp),
                    colors = colors,
                    enabled = enabled,
                )
                NowPlayingSeekBarStyle.MINIMAL -> SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.height(4.dp),
                    colors = colors,
                    enabled = enabled,
                    drawStopIndicator = null,
                    thumbTrackGapSize = 0.dp,
                    trackInsideCornerSize = 2.dp,
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PlayPauseButton(player: Player, color: Color, modifier: Modifier = Modifier) {
    val state = rememberPlayPauseButtonState(player)
    val icon = if (state.showPlay) Icons.Rounded.PlayArrow else ImageVector.vectorResource(R.drawable.media3_notification_pause)
    val contentDescription =
        if (state.showPlay) "play"
        else "pause"

    if (LocalExpressiveDesign.current) {
        // A tonal button, round while paused, morphing into a rounded square while playing.
        val cornerPercent by animateIntAsState(
            targetValue = if (state.showPlay) 50 else 30,
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            label = "Play pause shape"
        )
        ActionTooltip(stringResource(if (state.showPlay) R.string.action_play else R.string.action_pause)) {
            IconButton(
                onClick = state::onClick,
                enabled = state.isEnabled,
                modifier = modifier
                    .bounceClick(state.isEnabled)
                    .clip(RoundedCornerShape(percent = cornerPercent))
                    .background(color.copy(alpha = if (state.isEnabled) 0.16f else 0.08f))
            ) {
                Icon(
                    icon,
                    contentDescription = contentDescription,
                    tint = if (state.isEnabled) color else color.copy(0.5f),
                    modifier = Modifier.fillMaxSize(0.6f)
                )
            }
        }
        return
    }
    ActionTooltip(stringResource(if (state.showPlay) R.string.action_play else R.string.action_pause)) {
        IconButton(onClick = state::onClick, modifier = modifier.bounceClick(state.isEnabled), enabled = state.isEnabled) {
            Icon(icon, contentDescription = contentDescription, modifier = modifier, tint = if (state.isEnabled) color else color.copy(0.5f))
        }
    }
//    ToggleButton(
//        checked = state.showPlay,
//        onCheckedChange = { state.onClick() },
//        modifier = modifier,
//        enabled = state.isEnabled
//    ) {
//        Icon(icon,
//            contentDescription = contentDescription,
//            modifier = modifier
//        )
//    }
}
