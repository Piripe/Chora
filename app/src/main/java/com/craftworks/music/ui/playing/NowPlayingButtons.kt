@file:androidx.annotation.OptIn(UnstableApi::class)

package com.craftworks.music.ui.playing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.state.rememberNextButtonState
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberPreviousButtonState
import androidx.media3.ui.compose.state.rememberRepeatButtonState
import androidx.media3.ui.compose.state.rememberShuffleButtonState
import com.craftworks.music.R
import com.craftworks.music.ui.elements.ActionTooltip
import com.craftworks.music.ui.elements.bounceClick
import com.craftworks.music.ui.elements.moveClick

/** Where a button is drawn: it changes its colors. */
enum class NowPlayingButtonStyle {
    /** Next to the play/pause button. */
    CONTROLS,
    /** In the bottom row. */
    ROW,
    /** Next to the title. */
    TITLE,
}

internal data class ActionButtonState(
    val icon: ImageVector,
    val enabled: Boolean,
    val active: Boolean,
    /** What a click does, shown when long-pressing the button. Defaults to the action label. */
    val tooltip: String? = null,
)

/** Actions whose state is only shown by the button color (their icon doesn't change). */
internal val dimmedWhenInactive = setOf(
    NowPlayingAction.SHUFFLE,
    NowPlayingAction.REPEAT,
    NowPlayingAction.SLEEP_TIMER,
)

@Composable
internal fun rememberActionButtonState(
    action: NowPlayingAction,
    player: Player?,
    context: NowPlayingActionContext,
): ActionButtonState {
    val available = context.isAvailable(action)
    if (player == null) return when (action) {
        NowPlayingAction.PLAY_PAUSE, NowPlayingAction.PREVIOUS, NowPlayingAction.NEXT,
        NowPlayingAction.SHUFFLE, NowPlayingAction.REPEAT ->
            ActionButtonState(nowPlayingActionIcon(action), enabled = false, active = false)
        else -> staticActionButtonState(action, context, available)
    }

    return when (action) {
        NowPlayingAction.PLAY_PAUSE -> {
            val state = rememberPlayPauseButtonState(player)
            ActionButtonState(
                nowPlayingActionIcon(action, !state.showPlay), state.isEnabled, !state.showPlay,
                stringResource(if (state.showPlay) R.string.action_play else R.string.action_pause)
            )
        }
        NowPlayingAction.PREVIOUS -> {
            val state = rememberPreviousButtonState(player)
            ActionButtonState(nowPlayingActionIcon(action), state.isEnabled, false)
        }
        NowPlayingAction.NEXT -> {
            val state = rememberNextButtonState(player)
            ActionButtonState(nowPlayingActionIcon(action), state.isEnabled, false)
        }
        NowPlayingAction.SHUFFLE -> {
            val state = rememberShuffleButtonState(player)
            ActionButtonState(
                nowPlayingActionIcon(action), state.isEnabled, state.shuffleOn,
                stringResource(if (state.shuffleOn) R.string.action_shuffle_off else R.string.action_shuffle_on)
            )
        }
        NowPlayingAction.REPEAT -> {
            val state = rememberRepeatButtonState(player)
            ActionButtonState(
                icon = if (state.repeatModeState == Player.REPEAT_MODE_ONE)
                    ImageVector.vectorResource(R.drawable.rounded_repeat1_24)
                else nowPlayingActionIcon(action),
                enabled = state.isEnabled,
                active = state.repeatModeState != Player.REPEAT_MODE_OFF,
                // Same order as the repeat action: off, one, all.
                tooltip = stringResource(
                    when (state.repeatModeState) {
                        Player.REPEAT_MODE_OFF -> R.string.action_repeat_one
                        Player.REPEAT_MODE_ONE -> R.string.action_repeat_all
                        else -> R.string.action_repeat_off
                    }
                )
            )
        }
        else -> staticActionButtonState(action, context, available)
    }
}

@Composable
private fun staticActionButtonState(
    action: NowPlayingAction,
    context: NowPlayingActionContext,
    available: Boolean,
): ActionButtonState {
    val active = when (action) {
        NowPlayingAction.LYRICS -> context.lyricsOpen && context.hasLyrics
        NowPlayingAction.FAVORITE -> context.isStarred
        NowPlayingAction.RATING -> context.rating > 0
        NowPlayingAction.SLEEP_TIMER -> context.sleepTimerMinutes > 0
        else -> false
    }
    val tooltip = when (action) {
        NowPlayingAction.LYRICS -> stringResource(if (active) R.string.action_hide_lyrics else R.string.action_show_lyrics)
        NowPlayingAction.FAVORITE ->
            stringResource(if (active) R.string.action_remove_from_favorites else R.string.action_add_to_favorites)
        NowPlayingAction.QUEUE -> stringResource(R.string.action_show_queue)
        NowPlayingAction.SLEEP_TIMER -> stringResource(R.string.action_set_sleep_timer)
        NowPlayingAction.DOWNLOAD -> stringResource(R.string.action_download_song)
        NowPlayingAction.MENU -> stringResource(R.string.action_more_options)
        else -> null
    }
    return ActionButtonState(nowPlayingActionIcon(action, active), available, active, tooltip)
}

/** The sleep timer badge, e.g. "15m" or "2h". */
fun sleepTimerBadge(minutes: Int): String? = when {
    minutes <= 0 -> null
    minutes > 60 -> "${minutes / 60}h"
    else -> "${minutes}m"
}

@Composable
fun NowPlayingActionButton(
    action: NowPlayingAction,
    player: Player?,
    context: NowPlayingActionContext,
    color: Color,
    iconSize: Dp,
    style: NowPlayingButtonStyle,
    onAction: (NowPlayingAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberActionButtonState(action, player, context)
    val dimmed = action in dimmedWhenInactive && !state.active

    val alpha = when (style) {
        NowPlayingButtonStyle.CONTROLS -> if (!state.enabled || dimmed) 0.5f else 1f
        NowPlayingButtonStyle.ROW -> when {
            !state.enabled -> 0.25f
            state.active && action in dimmedWhenInactive -> 0.9f
            else -> 0.5f
        }
        NowPlayingButtonStyle.TITLE -> when {
            !state.enabled -> 0.3f
            dimmed -> 0.5f
            else -> 0.8f
        }
    }
    val tint = color.copy(alpha = alpha)

    val moveModifier = if (style == NowPlayingButtonStyle.CONTROLS && action in setOf(NowPlayingAction.PREVIOUS, NowPlayingAction.NEXT))
        Modifier.moveClick(action == NowPlayingAction.NEXT, state.enabled)
    else Modifier

    ActionTooltip(state.tooltip ?: nowPlayingActionLabel(action), modifier = modifier) {
        BadgedBox(
            badge = {
                if (action == NowPlayingAction.SLEEP_TIMER)
                    sleepTimerBadge(context.sleepTimerMinutes)?.let { Badge { Text(it) } }
            }
        ) {
            IconButton(
                onClick = { onAction(action) },
                enabled = state.enabled,
                modifier = Modifier
                    .size(iconSize + 16.dp)
                    .bounceClick(state.enabled)
                    .then(moveModifier),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = tint,
                    disabledContentColor = tint
                )
            ) {
                Icon(
                    imageVector = state.icon,
                    contentDescription = nowPlayingActionLabel(action),
                    tint = tint,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}

/** Icon size of a button next to the play/pause button. */
private fun controlsIconSize(action: NowPlayingAction): Dp = when (action) {
    NowPlayingAction.PREVIOUS, NowPlayingAction.NEXT -> 40.dp
    NowPlayingAction.SEEK_BACK, NowPlayingAction.SEEK_FORWARD -> 32.dp
    else -> 24.dp
}

/**
 * The play/pause button, always centered, with the [NowPlayingButtonSlot.CONTROLS_START] and
 * [NowPlayingButtonSlot.CONTROLS_END] buttons around it. The buttons shrink when they don't fit.
 */
@Composable
fun NowPlayingControlsRow(
    buttons: List<NowPlayingButton>,
    player: Player?,
    context: NowPlayingActionContext,
    color: Color,
    playPauseSize: Dp,
    onAction: (NowPlayingAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val start = buttons.filter { it.slot == NowPlayingButtonSlot.CONTROLS_START }.map { it.action }
    val end = buttons.filter { it.slot == NowPlayingButtonSlot.CONTROLS_END }.map { it.action }

    BoxWithConstraints(modifier) {
        val availableWidth = maxWidth
        val sideWidth = ((availableWidth - playPauseSize) / 2).coerceAtLeast(0.dp)

        @Composable
        fun Side(actions: List<NowPlayingAction>) {
            // Shrink every button by the same factor when the side is too narrow.
            val wanted = actions.fold(0.dp) { total, action -> total + controlsIconSize(action) + 16.dp }
            val scale = if (wanted > sideWidth && wanted > 0.dp) sideWidth / wanted else 1f
            Row(
                modifier = Modifier.width(sideWidth),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                actions.forEach { action ->
                    key(action) {
                        NowPlayingActionButton(
                            action = action,
                            player = player,
                            context = context,
                            color = color,
                            iconSize = ((controlsIconSize(action) + 16.dp) * scale - 16.dp).coerceAtLeast(16.dp),
                            style = NowPlayingButtonStyle.CONTROLS,
                            onAction = onAction
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Side(start)
            if (player != null)
                PlayPauseButton(player, color, Modifier.size(min(playPauseSize, availableWidth)))
            else
                Icon(
                    nowPlayingActionIcon(NowPlayingAction.PLAY_PAUSE),
                    contentDescription = null,
                    tint = color.copy(0.5f),
                    modifier = Modifier.size(playPauseSize)
                )
            Side(end)
        }
    }
}

/** The [NowPlayingButtonSlot.BOTTOM] buttons. Nothing is drawn without buttons. */
@Composable
fun NowPlayingBottomRow(
    buttons: List<NowPlayingButton>,
    player: Player?,
    context: NowPlayingActionContext,
    color: Color,
    onAction: (NowPlayingAction) -> Unit,
    modifier: Modifier = Modifier,
    excluded: Set<NowPlayingAction> = emptySet(),
) {
    val actions = buttons
        .filter { it.slot == NowPlayingButtonSlot.BOTTOM && it.action !in excluded }
        .map { it.action }
    if (actions.isEmpty()) return

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        actions.forEach { action ->
            key(action) {
                NowPlayingActionButton(
                    action = action,
                    player = player,
                    context = context,
                    color = color,
                    iconSize = 28.dp,
                    style = NowPlayingButtonStyle.ROW,
                    onAction = onAction
                )
            }
        }
    }
}

/** The [NowPlayingButtonSlot.TITLE] buttons, followed by the details menu button. */
@Composable
fun NowPlayingTitleButtons(
    buttons: List<NowPlayingButton>,
    player: Player?,
    context: NowPlayingActionContext,
    color: Color,
    onAction: (NowPlayingAction) -> Unit,
    excluded: Set<NowPlayingAction> = emptySet(),
) {
    buttons
        .filter { it.slot == NowPlayingButtonSlot.TITLE && it.action !in excluded }
        .forEach { button ->
            key(button.action) {
                NowPlayingActionButton(
                    action = button.action,
                    player = player,
                    context = context,
                    color = color,
                    iconSize = 24.dp,
                    style = NowPlayingButtonStyle.TITLE,
                    onAction = onAction
                )
            }
        }

    if (context.isAvailable(NowPlayingAction.MENU))
        NowPlayingActionButton(
            action = NowPlayingAction.MENU,
            player = player,
            context = context,
            color = color,
            iconSize = 24.dp,
            style = NowPlayingButtonStyle.TITLE,
            onAction = onAction
        )
}
