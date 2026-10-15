package com.craftworks.music.ui.playing

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import com.craftworks.music.R
import com.craftworks.music.data.model.ProviderFeature
import kotlinx.serialization.Serializable
import java.util.EnumSet

/**
 * Everything that can be done from the now playing screen: as a button, in the details menu or with a cover gesture.
 * Stored by name in the settings, so the entries can be reordered but not renamed.
 */
@Serializable
enum class NowPlayingAction {
    PLAY_PAUSE, PREVIOUS, NEXT, SHUFFLE, REPEAT, SEEK_BACK, SEEK_FORWARD,
    LYRICS, QUEUE, SLEEP_TIMER, DOWNLOAD,
    FAVORITE, RATING, ADD_TO_PLAYLIST, GO_TO_ARTIST, GO_TO_ALBUM,
    MENU
}

/** Where a button is shown. The play/pause button is always in the middle of [CONTROLS_START] and [CONTROLS_END]. */
@Serializable
enum class NowPlayingButtonSlot(val maxButtons: Int) {
    /** Next to the details (more) button, beside the title. */
    TITLE(3),
    /** Left of the play/pause button. */
    CONTROLS_START(3),
    /** Right of the play/pause button. */
    CONTROLS_END(3),
    /** The row at the bottom of the screen. */
    BOTTOM(6),
}

@Serializable
data class NowPlayingButton(
    val action: NowPlayingAction,
    val slot: NowPlayingButtonSlot,
)

@Serializable
data class NowPlayingMenuItem(
    val action: NowPlayingAction,
    val enabled: Boolean = true,
)

@Serializable
enum class NowPlayingGesture {
    TAP, DOUBLE_TAP, LONG_PRESS, SWIPE_LEFT, SWIPE_RIGHT, SWIPE_UP, SWIPE_DOWN
}

@Serializable
enum class NowPlayingSeekBarStyle {
    STANDARD, WAVY, THICK, MINIMAL
}

/** How far the seek back / forward actions move, in ms. */
const val NOW_PLAYING_SEEK_INCREMENT_MS = 10_000L

/** Actions that can be put in the button rows. Play/pause is always shown, and the menu button is always next to the title. */
val nowPlayingButtonActions: List<NowPlayingAction> =
    NowPlayingAction.entries - NowPlayingAction.PLAY_PAUSE - NowPlayingAction.MENU

/** Actions that can be shown in the details menu (all of them, except the one opening it). */
val nowPlayingMenuActions: List<NowPlayingAction> =
    NowPlayingAction.entries - NowPlayingAction.MENU

val defaultNowPlayingButtons = listOf(
    NowPlayingButton(NowPlayingAction.SHUFFLE, NowPlayingButtonSlot.CONTROLS_START),
    NowPlayingButton(NowPlayingAction.PREVIOUS, NowPlayingButtonSlot.CONTROLS_START),
    NowPlayingButton(NowPlayingAction.NEXT, NowPlayingButtonSlot.CONTROLS_END),
    NowPlayingButton(NowPlayingAction.REPEAT, NowPlayingButtonSlot.CONTROLS_END),
    NowPlayingButton(NowPlayingAction.DOWNLOAD, NowPlayingButtonSlot.BOTTOM),
    NowPlayingButton(NowPlayingAction.SLEEP_TIMER, NowPlayingButtonSlot.BOTTOM),
    NowPlayingButton(NowPlayingAction.LYRICS, NowPlayingButtonSlot.BOTTOM),
    NowPlayingButton(NowPlayingAction.QUEUE, NowPlayingButtonSlot.BOTTOM),
)

/** Every action, but the ones already shown by default (as buttons, or play/pause) are hidden. */
val defaultNowPlayingMenuItems: List<NowPlayingMenuItem> = listOf(
    NowPlayingAction.FAVORITE,
    NowPlayingAction.RATING,
    NowPlayingAction.ADD_TO_PLAYLIST,
    NowPlayingAction.GO_TO_ARTIST,
    NowPlayingAction.GO_TO_ALBUM,
    NowPlayingAction.DOWNLOAD,
    NowPlayingAction.LYRICS,
    NowPlayingAction.QUEUE,
    NowPlayingAction.SLEEP_TIMER,
    NowPlayingAction.SHUFFLE,
    NowPlayingAction.REPEAT,
    NowPlayingAction.SEEK_BACK,
    NowPlayingAction.SEEK_FORWARD,
    NowPlayingAction.PREVIOUS,
    NowPlayingAction.PLAY_PAUSE,
    NowPlayingAction.NEXT,
).map { action ->
    NowPlayingMenuItem(
        action = action,
        enabled = action != NowPlayingAction.PLAY_PAUSE && defaultNowPlayingButtons.none { it.action == action }
    )
}

val defaultNowPlayingCoverGestures: Map<NowPlayingGesture, NowPlayingAction> = mapOf(
    NowPlayingGesture.DOUBLE_TAP to NowPlayingAction.FAVORITE,
    NowPlayingGesture.SWIPE_LEFT to NowPlayingAction.NEXT,
    NowPlayingGesture.SWIPE_RIGHT to NowPlayingAction.PREVIOUS,
)

/**
 * Keeps the stored menu items in their order and appends the actions they don't know yet
 * (added in a later version), so every action is always in the menu settings.
 */
fun List<NowPlayingMenuItem>.withAllMenuActions(): List<NowPlayingMenuItem> {
    val known = filter { it.action in nowPlayingMenuActions }.distinctBy { it.action }
    val missing = nowPlayingMenuActions
        .filter { action -> known.none { it.action == action } }
        .map { NowPlayingMenuItem(it) }
    return known + missing
}

/** What the current track supports, used to enable or hide the actions. */
data class NowPlayingActionContext(
    val isRadio: Boolean = false,
    val providerFeatures: EnumSet<ProviderFeature>? = null,
    val hasArtist: Boolean = false,
    val hasAlbum: Boolean = false,
    val hasLyrics: Boolean = false,
    val isStarred: Boolean = false,
    val rating: Int = 0,
    val lyricsOpen: Boolean = false,
    val sleepTimerMinutes: Int = 0,
) {
    fun isAvailable(action: NowPlayingAction): Boolean = when (action) {
        NowPlayingAction.SEEK_BACK, NowPlayingAction.SEEK_FORWARD -> !isRadio
        NowPlayingAction.LYRICS -> hasLyrics
        NowPlayingAction.DOWNLOAD -> !isRadio && providerFeatures?.contains(ProviderFeature.DOWNLOADS) == true
        NowPlayingAction.FAVORITE -> !isRadio && providerFeatures?.contains(ProviderFeature.FAVORITES) == true
        NowPlayingAction.RATING -> !isRadio && providerFeatures?.contains(ProviderFeature.RATINGS) == true
        NowPlayingAction.ADD_TO_PLAYLIST -> !isRadio && providerFeatures?.contains(ProviderFeature.PLAYLISTS) == true
        NowPlayingAction.GO_TO_ARTIST -> !isRadio && hasArtist
        NowPlayingAction.GO_TO_ALBUM -> !isRadio && hasAlbum
        NowPlayingAction.MENU -> !isRadio
        else -> true
    }
}

@Composable
fun nowPlayingActionLabel(action: NowPlayingAction): String = when (action) {
    NowPlayingAction.PLAY_PAUSE -> stringResource(R.string.action_play_pause)
    NowPlayingAction.PREVIOUS -> stringResource(R.string.action_previous)
    NowPlayingAction.NEXT -> stringResource(R.string.action_next)
    NowPlayingAction.SHUFFLE -> stringResource(R.string.action_shuffle)
    NowPlayingAction.REPEAT -> stringResource(R.string.action_repeat)
    NowPlayingAction.SEEK_BACK -> stringResource(R.string.action_seek_back, NOW_PLAYING_SEEK_INCREMENT_MS / 1000)
    NowPlayingAction.SEEK_FORWARD -> stringResource(R.string.action_seek_forward, NOW_PLAYING_SEEK_INCREMENT_MS / 1000)
    NowPlayingAction.LYRICS -> stringResource(R.string.action_lyrics)
    NowPlayingAction.QUEUE -> stringResource(R.string.action_queue)
    NowPlayingAction.SLEEP_TIMER -> stringResource(R.string.action_sleep_timer)
    NowPlayingAction.DOWNLOAD -> stringResource(R.string.action_download)
    NowPlayingAction.FAVORITE -> stringResource(R.string.action_favorite)
    NowPlayingAction.RATING -> stringResource(R.string.action_set_rating)
    NowPlayingAction.ADD_TO_PLAYLIST -> stringResource(R.string.action_add_to_playlist)
    NowPlayingAction.GO_TO_ARTIST -> stringResource(R.string.action_go_to_artist)
    NowPlayingAction.GO_TO_ALBUM -> stringResource(R.string.action_go_to_album)
    NowPlayingAction.MENU -> stringResource(R.string.action_more)
}

/** @param active whether the toggle-like actions are on (favorite, lyrics open, playing...). */
@Composable
fun nowPlayingActionIcon(action: NowPlayingAction, active: Boolean = false): ImageVector = when (action) {
    NowPlayingAction.PLAY_PAUSE ->
        if (active) ImageVector.vectorResource(R.drawable.media3_notification_pause) else Icons.Rounded.PlayArrow
    NowPlayingAction.PREVIOUS -> ImageVector.vectorResource(R.drawable.media3_notification_seek_to_previous)
    NowPlayingAction.NEXT -> ImageVector.vectorResource(R.drawable.media3_notification_seek_to_next)
    NowPlayingAction.SHUFFLE -> ImageVector.vectorResource(R.drawable.round_shuffle_28)
    NowPlayingAction.REPEAT -> ImageVector.vectorResource(R.drawable.rounded_repeat_24)
    NowPlayingAction.SEEK_BACK -> ImageVector.vectorResource(androidx.media3.session.R.drawable.media3_icon_skip_back_10)
    NowPlayingAction.SEEK_FORWARD -> ImageVector.vectorResource(androidx.media3.session.R.drawable.media3_icon_skip_forward_10)
    NowPlayingAction.LYRICS ->
        ImageVector.vectorResource(if (active) R.drawable.lyrics_active else R.drawable.lyrics_inactive)
    NowPlayingAction.QUEUE -> ImageVector.vectorResource(R.drawable.rounded_queue_music_24)
    NowPlayingAction.SLEEP_TIMER -> ImageVector.vectorResource(R.drawable.rounded_timer_24)
    NowPlayingAction.DOWNLOAD -> ImageVector.vectorResource(R.drawable.rounded_download_24)
    NowPlayingAction.FAVORITE ->
        ImageVector.vectorResource(if (active) R.drawable.round_favorite_24 else R.drawable.round_favorite_border_24)
    NowPlayingAction.RATING ->
        if (active) Icons.Rounded.Star else ImageVector.vectorResource(R.drawable.rounded_star_outline_24)
    NowPlayingAction.ADD_TO_PLAYLIST -> ImageVector.vectorResource(R.drawable.rounded_add_24)
    NowPlayingAction.GO_TO_ARTIST -> ImageVector.vectorResource(R.drawable.rounded_artist_24)
    NowPlayingAction.GO_TO_ALBUM -> ImageVector.vectorResource(androidx.media3.session.R.drawable.media3_icon_album)
    NowPlayingAction.MENU -> ImageVector.vectorResource(R.drawable.more_vert_24px)
}

@Composable
fun nowPlayingButtonSlotLabel(slot: NowPlayingButtonSlot): String = when (slot) {
    NowPlayingButtonSlot.TITLE -> stringResource(R.string.now_playing_buttons_title)
    NowPlayingButtonSlot.CONTROLS_START -> stringResource(R.string.now_playing_buttons_controls_start)
    NowPlayingButtonSlot.CONTROLS_END -> stringResource(R.string.now_playing_buttons_controls_end)
    NowPlayingButtonSlot.BOTTOM -> stringResource(R.string.now_playing_buttons_bottom)
}

@Composable
fun nowPlayingGestureLabel(gesture: NowPlayingGesture): String = when (gesture) {
    NowPlayingGesture.TAP -> stringResource(R.string.cover_gesture_tap)
    NowPlayingGesture.DOUBLE_TAP -> stringResource(R.string.cover_gesture_double_tap)
    NowPlayingGesture.LONG_PRESS -> stringResource(R.string.cover_gesture_long_press)
    NowPlayingGesture.SWIPE_LEFT -> stringResource(R.string.cover_gesture_swipe_left)
    NowPlayingGesture.SWIPE_RIGHT -> stringResource(R.string.cover_gesture_swipe_right)
    NowPlayingGesture.SWIPE_UP -> stringResource(R.string.cover_gesture_swipe_up)
    NowPlayingGesture.SWIPE_DOWN -> stringResource(R.string.cover_gesture_swipe_down)
}

@Composable
fun nowPlayingSeekBarStyleLabel(style: NowPlayingSeekBarStyle): String = when (style) {
    NowPlayingSeekBarStyle.STANDARD -> stringResource(R.string.seek_bar_style_standard)
    NowPlayingSeekBarStyle.WAVY -> stringResource(R.string.seek_bar_style_wavy)
    NowPlayingSeekBarStyle.THICK -> stringResource(R.string.seek_bar_style_thick)
    NowPlayingSeekBarStyle.MINIMAL -> stringResource(R.string.seek_bar_style_minimal)
}
