package com.craftworks.music.ui.elements

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media.utils.MediaConstants.METADATA_KEY_IS_EXPLICIT
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.StarRating
import androidx.media3.session.MediaController
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.craftworks.music.R
import com.craftworks.music.data.model.LibraryType
import com.craftworks.music.data.model.ProviderFeature
import com.craftworks.music.data.model.getProvider
import com.craftworks.music.data.model.id
import com.craftworks.music.player.SongHelper
import com.craftworks.music.ui.elements.dialogs.AddToPlaylist
import com.craftworks.music.ui.elements.dialogs.DownloadTargetDialog
import com.craftworks.music.ui.elements.dialogs.RatingDialog
import com.craftworks.music.ui.elements.dialogs.ShareDialog
import com.craftworks.music.ui.viewmodels.MediaCardMenuViewModel
import com.craftworks.music.ui.viewmodels.SongsScreenViewModel
import com.craftworks.music.utils.StringUtils

@Composable
fun HorizontalSongCard(
    song: MediaItem,
    modifier: Modifier = Modifier,
    showTrackNumber: Boolean = false,
    onClick: () -> Unit,
    extraMenuItems: (@Composable (onDismiss: () -> Unit) -> Unit)? = null,
    viewModel: SongsScreenViewModel = hiltViewModel(),
    mixViewModel: MediaCardMenuViewModel = hiltViewModel(),
    mediaController: MediaController?,
    /** A smaller row and cover, for dense lists. */
    compact: Boolean = false
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    var expanded by remember { mutableStateOf(false) }
    var showAddSongToPlaylistDialog by remember { mutableStateOf(false) }
    var showSongRatingDialog by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }

    val isStarred by remember(song.mediaMetadata) { viewModel.songStarredFlow(song.mediaMetadata) }
        .collectAsStateWithLifecycle(viewModel.isSongStarred(song.mediaMetadata))

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            disabledContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
            disabledContentColor = MaterialTheme.colorScheme.onTertiaryContainer
        ),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    expanded = true
                }
            )
    ) {
        Row(
            modifier = Modifier
                .height(if (compact) 56.dp else 72.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            if (showTrackNumber) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .padding(8.dp, 0.dp, 0.dp, 0.dp),
                    //.clip(CircleShape)
                    //.background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = song.mediaMetadata.trackNumber.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                }
            }
            else {
                // Servers don't all serve the cover art for every kind of ID: try the cover art ID of the song,
                // then its album, then the song itself, until one loads.
                val provider = song.mediaMetadata.getProvider()
                val artworkIds = remember(song.mediaMetadata) {
                    val extras = song.mediaMetadata.extras
                    listOfNotNull(
                        extras?.getString("imageId")?.let { it to LibraryType.SONG },
                        extras?.getString("albumId")?.let { it to LibraryType.ALBUM },
                        song.mediaMetadata.id?.let { it to LibraryType.SONG },
                    ).filter { it.first.isNotEmpty() }.distinctBy { it.first }
                }
                var artworkIndex by remember(artworkIds) { mutableIntStateOf(0) }
                val (imageId, imageType) = artworkIds.getOrNull(artworkIndex) ?: ("" to LibraryType.SONG)
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(provider?.getImageUrl(
                            id = imageId,
                            itemType = imageType,
                            size = 128
                        ))
                        .crossfade(true)
                        .diskCacheKey("song-art-$imageId")
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .placeholderMemoryCacheKey("song-art-$imageId")
                        .build(),
                    onError = { if (artworkIndex < artworkIds.lastIndex) artworkIndex++ },
                    contentDescription = "Album Image",
                    contentScale = ContentScale.FillHeight,
                    modifier = Modifier
                        .size(if (compact) 48.dp else 64.dp)
                        .padding(4.dp, 0.dp, 0.dp, 0.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }

            Spacer(modifier = Modifier.width(if (compact) 8.dp else 12.dp))

            Column(
                Modifier
                    .padding(end = 12.dp)
                    .weight(1f),
                verticalArrangement = Arrangement.SpaceAround
            ) {
                Row (
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = song.mediaMetadata.title.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start
                    )
                    if (song.mediaMetadata.extras?.getBoolean(METADATA_KEY_IS_EXPLICIT) == true)
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.rounded_explicit_24),
                            contentDescription = "Explicit"
                        )
                }

                Text(
                    text = song.mediaMetadata.artist.toString() + if ((song.mediaMetadata.recordingYear ?: 0) != 0) " • " + song.mediaMetadata.recordingYear else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(0.75f),
                    modifier = Modifier,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start
                )

                if (song.mediaMetadata.userRating != null) {
                    Row {
                        repeat((song.mediaMetadata.userRating as StarRating).starRating.toInt()) {
                            Icon(
                                imageVector = Icons.Rounded.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
            val formattedDuration by remember(song.mediaMetadata.durationMs) {
                derivedStateOf {
                    StringUtils.formatSeconds((song.mediaMetadata.durationMs?.div(1000)) ?: 0)
                }
            }
            Text(
                text = formattedDuration,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(end = 12.dp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End
            )

            ActionTooltip(stringResource(if (isStarred) R.string.action_remove_from_favorites else R.string.action_add_to_favorites)) {
                IconButton(
                    modifier = Modifier,
                    onClick = { viewModel.toggleSongStar(song.mediaMetadata) },
                ) {
                    Crossfade(
                        targetState = isStarred
                    ) {
                        if (it) Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.round_favorite_24),
                            contentDescription = stringResource(R.string.action_remove_from_favorites)
                        )
                        else
                            Icon(
                                imageVector = ImageVector.vectorResource(R.drawable.round_favorite_border_24),
                                contentDescription = stringResource(R.string.action_add_to_favorites)
                            )
                    }
                }
            }

            Box(
                modifier = Modifier.width(48.dp)
            ) {
                ActionTooltip(stringResource(R.string.action_more_options)) {
                    IconButton(
                        modifier = Modifier,
                        onClick = { expanded = true },
//                        colors = IconButtonDefaults.iconButtonColors(
//                            containerColor = MaterialTheme.colorScheme.background,
//                            contentColor = MaterialTheme.colorScheme.primary,
//                            disabledContainerColor = MaterialTheme.colorScheme.background,
//                            disabledContentColor = MaterialTheme.colorScheme.onBackground
//                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            tint = MaterialTheme.colorScheme.onBackground,
                            contentDescription = "More menu"
                        )
                    }
                }

                if (expanded) MenuSheet(
                    title = song.mediaMetadata.title.toString(),
                    onDismissRequest = { expanded = false }
                ) {
                    val features = song.mediaMetadata.getProvider()?.featureFlags
                    MenuSheetItem(ImageVector.vectorResource(R.drawable.play_next_24px), stringResource(R.string.action_play_next)) {
                        SongHelper.playNext(listOf(song), mediaController)
                        expanded = false
                    }
                    MenuSheetItem(ImageVector.vectorResource(R.drawable.outline_queue_add_24), stringResource(R.string.action_add_to_queue)) {
                        SongHelper.enqueue(listOf(song), mediaController)
                        expanded = false
                    }
                    if (features?.contains(ProviderFeature.RADIO) == true) {
                        MenuSheetItem(ImageVector.vectorResource(R.drawable.rounded_sensors_24), stringResource(R.string.action_instant_mix)) {
                            mixViewModel.playMix({ mixViewModel.getInstantMix(song) }) { mediaController }
                            expanded = false
                        }
                    }
                    HorizontalDivider()
                    MenuSheetItem(
                        ImageVector.vectorResource(if (isStarred) R.drawable.round_favorite_24 else R.drawable.round_favorite_border_24),
                        stringResource(if (isStarred) R.string.action_remove_from_favorites else R.string.action_add_to_favorites)
                    ) {
                        viewModel.toggleSongStar(song.mediaMetadata)
                        expanded = false
                    }
                    MenuSheetItem(ImageVector.vectorResource(R.drawable.rounded_star_outline_24), stringResource(R.string.action_set_rating)) {
                        showSongRatingDialog = true
                        expanded = false
                    }
                    if (features?.contains(ProviderFeature.PLAYLISTS) == true) {
                        MenuSheetItem(Icons.Rounded.Add, stringResource(R.string.action_add_to_playlist)) {
                            showAddSongToPlaylistDialog = true
                            expanded = false
                        }
                    }
                    val canDownload = features?.contains(ProviderFeature.DOWNLOADS) == true
                    val canShare = features?.contains(ProviderFeature.SHARING) == true
                    if (canDownload || canShare) HorizontalDivider()
                    if (canDownload) {
                        MenuSheetItem(ImageVector.vectorResource(R.drawable.rounded_download_24), stringResource(R.string.action_download)) {
                            showDownloadDialog = true
                            expanded = false
                        }
                    }
                    if (canShare) {
                        MenuSheetItem(ImageVector.vectorResource(R.drawable.rounded_share_24), stringResource(R.string.action_share)) {
                            showShareDialog = true
                            expanded = false
                        }
                    }

                    extraMenuItems?.let {
                        HorizontalDivider()
                        it { expanded = false }
                    }
                }
            }
        }
    }

    if (showAddSongToPlaylistDialog) {
        AddToPlaylist(
            onDismissRequest = {showAddSongToPlaylistDialog = false},
            mediaToAddToPlaylist = listOf(song)
        )
    }
    if (showDownloadDialog) {
        DownloadTargetDialog(
            onDismissRequest = { showDownloadDialog = false },
            onSelect = { viewModel.downloadSong(song, it) }
        )
    }
    if (showShareDialog) {
        ShareDialog(
            provider = song.mediaMetadata.getProvider(),
            ids = listOfNotNull(song.mediaMetadata.id),
            title = song.mediaMetadata.title.toString(),
            onDismissRequest = { showShareDialog = false }
        )
    }
    if (showSongRatingDialog) {
        RatingDialog(
            currentRating = (song.mediaMetadata.userRating as? StarRating)?.starRating?.toInt() ?: 0,
            onDismiss = { showSongRatingDialog = false },
            onSetRating = { rating ->
                viewModel.setSongRating(song.mediaMetadata.id ?: "", rating)
            }
        )
    }
}

@Preview(showSystemUi = false, showBackground = true)
@Composable
fun PreviewHorizontalSongCard() {
    HorizontalSongCard(
        song = MediaItem.Builder()
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Lololol")
                    .build()
            ).build(),
        onClick = {},
        mediaController = null
    )
}