package com.craftworks.music.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.session.MediaController
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.craftworks.music.R
import com.craftworks.music.data.model.getProvider
import com.craftworks.music.data.model.id
import com.craftworks.music.player.SongHelper
import com.craftworks.music.player.rememberManagedMediaController
import com.craftworks.music.ui.elements.ActionButtonType
import com.craftworks.music.ui.elements.ActionTooltip
import com.craftworks.music.ui.elements.DetailsLoadingIndicator
import com.craftworks.music.ui.elements.ExpressiveDetailsHeader
import com.craftworks.music.ui.elements.HorizontalSongCard
import com.craftworks.music.ui.elements.LocalExpressiveDesign
import com.craftworks.music.ui.elements.MenuSheetItem
import com.craftworks.music.ui.elements.SegmentedItemGap
import com.craftworks.music.ui.elements.SongListActionButtons
import com.craftworks.music.ui.elements.dialogs.DownloadTargetDialog
import com.craftworks.music.ui.elements.dialogs.ShareDialog
import com.craftworks.music.ui.elements.dialogs.dialogFocusable
import com.craftworks.music.ui.elements.segmentedItemShape
import com.craftworks.music.ui.viewmodels.PlaylistScreenViewModel
import com.craftworks.music.utils.StringUtils
import com.craftworks.music.utils.fadingEdge
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalComposeUiApi::class)
@ExperimentalFoundationApi
@Preview(showBackground = true, showSystemUi = false)
@Composable
fun PlaylistDetails(
    selectedPlaylistId: String? = null,
    selectedPlaylistImage: String? = null,
    navHostController: NavHostController = rememberNavController(),
    mediaController: MediaController? = rememberManagedMediaController().value,
    viewModel: PlaylistScreenViewModel = hiltViewModel()
) {
    LaunchedEffect(selectedPlaylistId) {
        if (selectedPlaylistId != null) viewModel.loadPlaylistDetails(selectedPlaylistId)
    }

    val imageFadingEdge = Brush.verticalGradient(listOf(Color.Red, Color.Transparent))

    val requester = remember { FocusRequester() }

    val playlistMetadata =
        viewModel.selectedPlaylist.collectAsStateWithLifecycle().value?.mediaMetadata
    val playlistSongs = viewModel.selectedPlaylistSongs.collectAsStateWithLifecycle().value
    val actionButtons = viewModel.actionButtons.collectAsStateWithLifecycle(emptyList()).value
    val isLoading = viewModel.isLoading.collectAsStateWithLifecycle().value

    val playlistDuration =
        remember(playlistSongs) { playlistSongs.sumOf { it.mediaMetadata.durationMs ?: 0 } }

    val coroutineScope = rememberCoroutineScope()

    val context = LocalContext.current
    val expressive = LocalExpressiveDesign.current

    println("artwork uri: ${playlistMetadata?.artworkUri}; artwork data: ${playlistMetadata?.artworkData}")

    var showLoading by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isLoading) {
        if (isLoading) {
            delay(500.milliseconds)
            showLoading = true
        } else {
            showLoading = false
        }
    }

    // Loading spinner
    AnimatedVisibility(
        visible = showLoading,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        DetailsLoadingIndicator()
    }

    // Main Content
    AnimatedVisibility(
        visible = !isLoading && playlistSongs.isNotEmpty(),
        enter = fadeIn()
    ) {
        val actionRow = @Composable {
            // Play, shuffle and more buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
            ) {

                val actions: Map<ActionButtonType, ()->Unit> = mapOf(
                    ActionButtonType.SHUFFLE to {
                        mediaController?.shuffleModeEnabled = true
                        coroutineScope.launch {
                            val random = playlistSongs.indices.random()
                            SongHelper.play(playlistSongs, random, mediaController)
                        }
                    },
                    ActionButtonType.ADD_TO_QUEUE to {
                        coroutineScope.launch {
                            SongHelper.enqueue(playlistSongs, mediaController)
                        }
                    },
                    ActionButtonType.PLAY_NEXT to {
                        coroutineScope.launch {
                            SongHelper.playNext(playlistSongs, mediaController)
                        }
                    },
                    ActionButtonType.DOWNLOAD to {
                        showDownloadDialog = true
                    },
                    ActionButtonType.SHARE to {
                        showShareDialog = true
                    },
                )

                SongListActionButtons(
                    buttons = actionButtons.map { it.apply { this.onClick = actions[this.type]?:{}} },
                    providerFeatures = playlistMetadata?.getProvider()?.featureFlags,
                    playAction = {
                        coroutineScope.launch {
                            SongHelper.play(playlistSongs, 0, mediaController)
                        }
                    }
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .dialogFocusable(),
            contentPadding = PaddingValues(
                bottom = 16.dp,
            )
        ) {
            item {
                if (expressive) {
                    ExpressiveDetailsHeader(
                        imageData = selectedPlaylistImage,
                        imageCacheKey = selectedPlaylistId,
                        title = playlistMetadata?.title.toString(),
                        onBack = { navHostController.popBackStack() },
                        fallback = painterResource(R.drawable.placeholder),
                        subtitle = {
                            Text(
                                text = pluralStringResource(R.plurals.details_song_count, playlistSongs.size, playlistSongs.size) +
                                        " • " + StringUtils.formatSeconds(playlistDuration / 1000),
                                style = MaterialTheme.typography.titleMediumEmphasized,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        actions = actionRow
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .height(320.dp)
                            .fillMaxWidth()
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(selectedPlaylistImage)
                                .diskCacheKey(selectedPlaylistId)
                                .diskCachePolicy(CachePolicy.READ_ONLY)
                                .crossfade(true)
                                .build(),
                            fallback = painterResource(R.drawable.placeholder),
                            contentScale = ContentScale.FillWidth,
                            contentDescription = "Album Image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .fadingEdge(imageFadingEdge)
                                .blur(8.dp)
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    top = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding(),
                                    bottom = 12.dp
                                )
                                .padding(horizontal = 24.dp)
                        ) {
                            // Back button
                            ActionTooltip(stringResource(R.string.action_back)) {
                                FilledTonalIconButton(
                                    onClick = { navHostController.popBackStack() },
                                    modifier = Modifier
                                        .size(36.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = "back"
                                    )
                                }
                            }

                            Spacer(Modifier.weight(1f))

                            // Album Name and Artist
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = playlistMetadata?.title.toString(),
                                    color = MaterialTheme.colorScheme.onBackground,
                                    style = MaterialTheme.typography.headlineMediumEmphasized,
                                    textAlign = TextAlign.Left,
                                )

                                Text(
                                    text = StringUtils.formatSeconds(playlistDuration / 1000),
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Left
                                )

                                Spacer(Modifier.height(6.dp))

                                actionRow()
                            }
                        }
                    }
                }
            }

            itemsIndexed(playlistSongs) { index, song ->
                HorizontalSongCard(
                    song = song,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = if (expressive) SegmentedItemGap else 0.dp)
                        .animateItem(),
                    shape = if (expressive) segmentedItemShape(index, playlistSongs.size) else RoundedCornerShape(12.dp),
                    containerColor = if (expressive) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent,
                    onClick = {
                        coroutineScope.launch {
                            SongHelper.play(
                                playlistSongs,
                                playlistSongs.indexOf(song),
                                mediaController
                            )
                        }
                    },
                    extraMenuItems = { onDismiss ->
                        MenuSheetItem(
                            ImageVector.vectorResource(R.drawable.rounded_playlist_remove_24),
                            stringResource(R.string.action_remove_from_playlist)
                        ) {
                            viewModel.removeSongFromPlaylist(
                                playlistId = playlistMetadata?.id ?: "",
                                songId = song.mediaMetadata.id ?: ""
                            )
                            onDismiss()
                        }
                    },
                    mediaController = mediaController
                )
            }
        }
    }

    if (showShareDialog) {
        ShareDialog(
            provider = playlistMetadata?.getProvider(),
            ids = listOfNotNull(playlistMetadata?.id),
            title = playlistMetadata?.title?.toString() ?: "",
            onDismissRequest = { showShareDialog = false }
        )
    }

    if (showDownloadDialog) {
        DownloadTargetDialog(
            onDismissRequest = { showDownloadDialog = false },
            onSelect = { target ->
                viewModel.downloadPlaylist(
                    playlistSongs,
                    playlistMetadata?.id,
                    playlistMetadata?.title.toString(),
                    target
                )
            }
        )
    }
}