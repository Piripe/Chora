package com.craftworks.music.ui.playing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.craftworks.music.R

/** Menu actions opening another screen or dialog: the menu is closed when they are clicked. */
private val actionsClosingMenu = setOf(
    NowPlayingAction.LYRICS,
    NowPlayingAction.QUEUE,
    NowPlayingAction.SLEEP_TIMER,
    NowPlayingAction.DOWNLOAD,
    NowPlayingAction.SHARE,
    NowPlayingAction.RATING,
    NowPlayingAction.ADD_TO_PLAYLIST,
    NowPlayingAction.GO_TO_ARTIST,
    NowPlayingAction.GO_TO_ALBUM,
)

/** The details menu: the enabled [items] available for the current track, in their order. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun NowPlayingDetails(
    items: List<NowPlayingMenuItem>,
    player: Player?,
    context: NowPlayingActionContext,
    onAction: (NowPlayingAction) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        items
            .filter { it.enabled && it.action != NowPlayingAction.MENU && context.isAvailable(it.action) }
            .forEach { item ->
                key(item.action) {
                    NowPlayingDetailsItem(item.action, player, context) {
                        if (item.action in actionsClosingMenu) onDismiss()
                        onAction(item.action)
                    }
                }
            }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun NowPlayingDetailsItem(
    action: NowPlayingAction,
    player: Player?,
    context: NowPlayingActionContext,
    onClick: () -> Unit,
) {
    val state = rememberActionButtonState(action, player, context)
    val highlighted = state.active && action in dimmedWhenInactive

    ListItem(
        onClick = onClick,
        enabled = state.enabled,
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent
        ),
        leadingContent = {
            Icon(
                imageVector = state.icon,
                contentDescription = null,
                tint = if (highlighted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
        },
        trailingContent = {
            when (action) {
                NowPlayingAction.RATING -> Row(horizontalArrangement = Arrangement.End) {
                    repeat(context.rating) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                NowPlayingAction.SLEEP_TIMER ->
                    sleepTimerBadge(context.sleepTimerMinutes)?.let { Badge { Text(it) } }
                else -> {}
            }
        },
        content = {
            Text(
                when {
                    action == NowPlayingAction.FAVORITE && context.isStarred ->
                        stringResource(R.string.action_remove_from_favorites)
                    action == NowPlayingAction.FAVORITE ->
                        stringResource(R.string.action_add_to_favorites)
                    else -> nowPlayingActionLabel(action)
                }
            )
        }
    )
}
