package com.craftworks.music.ui.playing

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.StarRating
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.session.MediaController
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.craftworks.music.R
import com.craftworks.music.data.model.MediaModel
import com.craftworks.music.data.model.Screen
import com.craftworks.music.data.model.artists
import com.craftworks.music.data.model.getProvider
import com.craftworks.music.data.repository.LyricsState
import com.craftworks.music.managers.settings.OLEDProtectionMode
import com.craftworks.music.player.ChoraMediaLibraryService
import com.craftworks.music.ui.elements.dialogs.AddToPlaylist
import com.craftworks.music.ui.elements.dialogs.RatingDialog
import com.craftworks.music.ui.playing.tv.TvNowPlaying
import kotlinx.coroutines.flow.flowOf

enum class NowPlayingAlignment {
    LEFT, CENTER, RIGHT
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
fun NowPlayingContent(
    mediaController: MediaController? = null,
    metadata: MediaMetadata? = null,
    navHostController: NavHostController = rememberNavController(),
    viewModel: NowPlayingViewModel = viewModel(),
    onHide: () -> Unit = {}
) {
    val backgroundStyle by viewModel.backgroundStyle.collectAsStateWithLifecycle(NowPlayingBackground.STATIC_BLUR)
    val useAdvancedQueue by viewModel.useAdvancedQueue.collectAsStateWithLifecycle(false)
    val backgroundDarkMode by viewModel.isBackgroundDark.collectAsStateWithLifecycle()
    val oledProtectionMode by viewModel.oledProtectionMode.collectAsStateWithLifecycle(OLEDProtectionMode.OFF)
    val buttons by viewModel.buttons.collectAsStateWithLifecycle(defaultNowPlayingButtons)
    val menuItems by viewModel.menuItems.collectAsStateWithLifecycle(defaultNowPlayingMenuItems)
    val seekBarStyle by viewModel.seekBarStyle.collectAsStateWithLifecycle(NowPlayingSeekBarStyle.STANDARD)
    val coverGestures by viewModel.coverGestures.collectAsStateWithLifecycle(emptyMap())
    val titleAlignment by viewModel.titleAlignment.collectAsStateWithLifecycle(NowPlayingAlignment.LEFT)
    val showMoreInfo by viewModel.showMoreInfo.collectAsStateWithLifecycle(true)
    val lyricsOpen by viewModel.lyricsOpen.collectAsStateWithLifecycle()
    val playQueueOpen by viewModel.playQueueOpen.collectAsStateWithLifecycle()
    val detailsOpen by viewModel.detailsOpen.collectAsStateWithLifecycle()
    var showRatingDialog by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    val sleepTimerOpen by viewModel.sleepTimerDialogOpen.collectAsStateWithLifecycle()
    val sleepTimerMinutes by ChoraMediaLibraryService.getInstance()?.sleepTimerRemainingTime
        ?.collectAsStateWithLifecycle(initialValue = 0)
        ?: remember { mutableIntStateOf(0) }
    val colors by viewModel.paletteColors.collectAsStateWithLifecycle()
    val iconTextColor by viewModel.iconTextColor.collectAsStateWithLifecycle()
    val lyrics by LyricsState.lyrics.collectAsStateWithLifecycle()
    val loadingLyrics by LyricsState.loading.collectAsStateWithLifecycle()

    val isStarred by remember(metadata) { metadata?.let { viewModel.songStarredFlow(it) } ?: flowOf(false) }
        .collectAsStateWithLifecycle(metadata?.let { viewModel.isSongStarred(it) } ?: false)

    val isSystemDark = if (oledProtectionMode != OLEDProtectionMode.OFF) true
        else isSystemInDarkTheme()

    LaunchedEffect(metadata?.artworkUri, backgroundStyle) {
        viewModel.updatePaletteFromUri(metadata?.artworkUri, backgroundStyle, isSystemDark)
    }

    val targetOverlayColor = when {
        oledProtectionMode != OLEDProtectionMode.OFF -> Color.Black.copy(0.7f)
        backgroundStyle == NowPlayingBackground.ANIMATED_BLUR -> {
            if (backgroundDarkMode) Color.Black.copy(0.2f) else Color.White.copy(0.2f)
        }
        else -> Color.Transparent
    }

    val isTv = LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
    // The lyrics are always shown next to the cover in landscape: they can't be toggled.
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // The buttons use the service player directly to show their state, the actions do the same.
    val player: Player? = ChoraMediaLibraryService.getInstance()?.player ?: mediaController
    val rating = (metadata?.userRating as? StarRating)?.starRating?.toInt() ?: 0
    val albumId = metadata?.extras?.getString("albumId")
    val actionContext = NowPlayingActionContext(
        isRadio = metadata?.mediaType == MediaMetadata.MEDIA_TYPE_RADIO_STATION,
        providerFeatures = metadata?.getProvider()?.featureFlags,
        hasArtist = !metadata?.artists.isNullOrEmpty(),
        hasAlbum = !albumId.isNullOrEmpty(),
        hasLyrics = (lyrics != null || loadingLyrics) && !isLandscape,
        isStarred = isStarred,
        rating = rating,
        lyricsOpen = lyricsOpen,
        sleepTimerMinutes = sleepTimerMinutes,
    )

    val onArtistNav: (MediaModel.Artist) -> Unit = { artist ->
        onHide()
        navHostController.navigate(Screen.ArtistDetails(artist.id, artist.imageUrl ?: artist.imageId?.let {artist.getProvider()?.getImageUrl(it)}))
    }

    val onAction: (NowPlayingAction) -> Unit = action@{ action ->
        if (!actionContext.isAvailable(action)) return@action
        when (action) {
            NowPlayingAction.PLAY_PAUSE -> player?.let { Util.handlePlayPauseButtonAction(it) }
            NowPlayingAction.PREVIOUS -> player?.seekToPrevious()
            NowPlayingAction.NEXT -> player?.seekToNext()
            NowPlayingAction.SHUFFLE -> player?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
            // Same order as the repeat button.
            NowPlayingAction.REPEAT -> player?.let {
                it.repeatMode = when (it.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                    Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                    else -> Player.REPEAT_MODE_OFF
                }
            }
            NowPlayingAction.SEEK_BACK -> player?.let {
                it.seekTo((it.currentPosition - NOW_PLAYING_SEEK_INCREMENT_MS).coerceAtLeast(0L))
            }
            NowPlayingAction.SEEK_FORWARD -> player?.let {
                val target = it.currentPosition + NOW_PLAYING_SEEK_INCREMENT_MS
                it.seekTo(if (it.duration > 0) target.coerceAtMost(it.duration) else target)
            }
            NowPlayingAction.LYRICS -> viewModel.setLyricsOpen(!lyricsOpen)
            NowPlayingAction.QUEUE -> viewModel.setPlayQueueOpen(!playQueueOpen)
            NowPlayingAction.SLEEP_TIMER -> viewModel.setSleepTimerDialogOpen(true)
            NowPlayingAction.DOWNLOAD -> metadata?.let { viewModel.downloadSong(it) }
            NowPlayingAction.FAVORITE -> metadata?.let { viewModel.toggleSongStar(it) }
            NowPlayingAction.RATING -> showRatingDialog = true
            NowPlayingAction.ADD_TO_PLAYLIST -> showAddToPlaylistDialog = true
            NowPlayingAction.GO_TO_ARTIST -> metadata?.artists?.firstOrNull()?.let(onArtistNav)
            NowPlayingAction.GO_TO_ALBUM -> albumId?.let {
                onHide()
                navHostController.navigate(Screen.AlbumDetails(it, metadata?.artworkUri.toString()))
            }
            NowPlayingAction.MENU -> viewModel.setDetailsOpen(!detailsOpen)
        }
    }

    // For the cover gestures feedback: the state each action switches to.
    val isActiveAfter: (NowPlayingAction) -> Boolean = { action ->
        when (action) {
            NowPlayingAction.PLAY_PAUSE -> player?.isPlaying == true
            NowPlayingAction.FAVORITE -> !isStarred
            NowPlayingAction.LYRICS -> !lyricsOpen
            NowPlayingAction.RATING -> rating > 0
            else -> false
        }
    }
    val availableCoverGestures = coverGestures.filterValues { actionContext.isAvailable(it) }

    NowPlaying_Background(colors, backgroundStyle, targetOverlayColor)

    if (isTv) {
        TvNowPlaying(
            mediaController,
            iconTextColor,
            metadata
        )
    } else if (isLandscape) {
        NowPlayingLandscape(
            mediaController = mediaController,
            metadata = metadata,
            iconColor = iconTextColor,
            player = player,
            actionContext = actionContext,
            buttons = buttons,
            seekBarStyle = seekBarStyle,
            coverGestures = availableCoverGestures,
            titleAlignment = titleAlignment,
            showMoreInfo = showMoreInfo,
            onAction = onAction,
            isActiveAfter = isActiveAfter,
            onRefreshLyrics = { viewModel.refreshLyrics(metadata) },
            onArtistNav = onArtistNav,
        )
    } else {
        NowPlayingPortrait(
            mediaController = mediaController,
            metadata = metadata,
            iconColor = iconTextColor,
            lyricsOpen = lyricsOpen,
            player = player,
            actionContext = actionContext,
            buttons = buttons,
            seekBarStyle = seekBarStyle,
            coverGestures = availableCoverGestures,
            titleAlignment = titleAlignment,
            showMoreInfo = showMoreInfo,
            onAction = onAction,
            isActiveAfter = isActiveAfter,
            onRefreshLyrics = { viewModel.refreshLyrics(metadata) },
            onArtistNav = onArtistNav,
        )
    }


    val playQueueSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val detailsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val timePickerState = rememberTimePickerState(initialHour = 0, initialMinute = 0, is24Hour = true)

    if (playQueueOpen) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.setPlayQueueOpen(false) },
            sheetState = playQueueSheetState,
        ) {
            if (useAdvancedQueue) {
                AdvancedPlayQueueContent(
                    mediaController = mediaController,
                    viewModel = viewModel,
                    dismissNowPlaying = {
                        onHide()
                        viewModel.setPlayQueueOpen(false)
                    }
                )
            }   else {
                PlayQueueContent(
                    mediaController = mediaController,
                    viewModel = viewModel,
                    dismissNowPlaying = {
                        onHide()
                        viewModel.setPlayQueueOpen(false)
                    }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (detailsOpen) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.setDetailsOpen(false) },
            sheetState = detailsSheetState,
        ) {
            NowPlayingDetails(
                items = menuItems,
                player = player,
                context = actionContext,
                onAction = onAction,
                onDismiss = { viewModel.setDetailsOpen(false) }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (sleepTimerOpen) {
        AlertDialog(
            onDismissRequest = { viewModel.setSleepTimerDialogOpen(false) },
            title = { Text(stringResource(R.string.now_playing_set_sleep_timer)) },
            text = {
                TimePicker(
                    state = timePickerState,
                )
            },
            confirmButton = {
                Button (
                    onClick = {
                        ChoraMediaLibraryService.getInstance()?.setSleepTimer(timePickerState.hour * 60 + timePickerState.minute)
                        viewModel.setSleepTimerDialogOpen(false)
                    }
                ) {
                    Text(stringResource(R.string.action_done))
                }
            }
        )
    }

    if (showRatingDialog)
        RatingDialog(
            currentRating = rating,
            onDismiss = { showRatingDialog = false },
            onSetRating = { rating ->
                mediaController?.setRating(StarRating(5, rating.toFloat()))
            }
        )

    if (showAddToPlaylistDialog)
        mediaController?.currentMediaItem?.let {
            AddToPlaylist(
                onDismissRequest = { showAddToPlaylistDialog = false },
                mediaToAddToPlaylist = listOf(it)
            )
        }
}

@Composable
fun dpToPx(dp: Int): Int {
    return with(LocalDensity.current) { dp.dp.toPx() }.toInt()
}