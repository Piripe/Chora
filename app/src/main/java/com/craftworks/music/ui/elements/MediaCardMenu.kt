package com.craftworks.music.ui.elements

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import com.craftworks.music.R
import com.craftworks.music.data.model.LibraryType
import com.craftworks.music.data.model.MediaModel
import com.craftworks.music.data.model.ProviderFeature
import com.craftworks.music.data.model.favorite
import com.craftworks.music.data.model.getProvider
import com.craftworks.music.data.model.id
import com.craftworks.music.data.providers.media.MediaProvider
import com.craftworks.music.player.MediaControllerManager
import com.craftworks.music.player.SongHelper
import com.craftworks.music.ui.elements.dialogs.AddToPlaylist
import com.craftworks.music.ui.elements.dialogs.DownloadTargetDialog
import com.craftworks.music.ui.elements.dialogs.ShareDialog
import com.craftworks.music.ui.viewmodels.MediaCardMenuViewModel

/** The long press menu of [AlbumCard]. */
@Composable
fun AlbumCardMenu(
    album: MediaItem,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    viewModel: MediaCardMenuViewModel = hiltViewModel()
) {
    val albumId = album.mediaMetadata.id ?: return
    MediaCardMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        provider = album.mediaMetadata.getProvider(),
        id = albumId,
        title = album.mediaMetadata.albumTitle?.toString() ?: album.mediaMetadata.title.toString(),
        libraryType = LibraryType.ALBUM,
        initiallyStarred = album.mediaMetadata.favorite ?: false,
        getSongs = { viewModel.getAlbumSongs(albumId) },
        getRadio = { viewModel.getAlbumRadio(album) },
        viewModel = viewModel
    )
}

/** The long press menu of [ArtistCard]. */
@Composable
fun ArtistCardMenu(
    artist: MediaModel.Artist,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    viewModel: MediaCardMenuViewModel = hiltViewModel()
) {
    MediaCardMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        provider = artist.getProvider(),
        id = artist.id,
        title = artist.name,
        libraryType = LibraryType.ARTIST,
        initiallyStarred = artist.userFavorite ?: false,
        getSongs = { viewModel.getArtistSongs(artist.id) },
        getRadio = { viewModel.getArtistRadio(artist) },
        viewModel = viewModel
    )
}

/** The media controller shared by the app, read when an action runs so the cards don't need it passed down. */
fun currentMediaController(context: Context): MediaController? =
    MediaControllerManager.getInstance(context).controller.value

@Composable
private fun MediaCardMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    provider: MediaProvider?,
    id: String,
    title: String,
    libraryType: LibraryType,
    initiallyStarred: Boolean,
    getSongs: suspend () -> List<MediaItem>,
    getRadio: suspend () -> List<MediaItem>,
    viewModel: MediaCardMenuViewModel
) {
    val context = LocalContext.current
    val features = provider?.featureFlags

    var isStarred by remember(id, initiallyStarred) { mutableStateOf(initiallyStarred) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var addToPlaylistSongs by remember { mutableStateOf<List<MediaItem>?>(null) }

    // Closes the menu, then runs the action with the songs once they are fetched.
    fun withSongs(action: suspend (songs: List<MediaItem>, controller: MediaController?) -> Unit) {
        onDismissRequest()
        viewModel.withSongs(getSongs) { songs -> action(songs, currentMediaController(context)) }
    }

    if (expanded) MenuSheet(
        title = title,
        onDismissRequest = onDismissRequest
    ) {
        MenuSheetItem(Icons.Rounded.PlayArrow, stringResource(R.string.action_play)) {
            withSongs { songs, controller -> SongHelper.play(songs, 0, controller) }
        }
        HorizontalDivider()
        MenuSheetItem(ImageVector.vectorResource(R.drawable.round_shuffle_28), stringResource(R.string.action_shuffle)) {
            withSongs { songs, controller -> SongHelper.play(songs.shuffled(), 0, controller) }
        }
        MenuSheetItem(ImageVector.vectorResource(R.drawable.play_next_24px), stringResource(R.string.action_play_next)) {
            withSongs { songs, controller -> SongHelper.playNext(songs, controller) }
        }
        MenuSheetItem(ImageVector.vectorResource(R.drawable.outline_queue_add_24), stringResource(R.string.action_add_to_queue)) {
            withSongs { songs, controller -> SongHelper.enqueue(songs, controller) }
        }
        if (features?.contains(ProviderFeature.RADIO) == true) {
            MenuSheetItem(ImageVector.vectorResource(R.drawable.rounded_sensors_24), stringResource(R.string.action_start_radio)) {
                onDismissRequest()
                viewModel.playMix(getRadio) { currentMediaController(context) }
            }
        }
        val canFavorite = features?.contains(ProviderFeature.FAVORITES) == true
        val canAddToPlaylist = features?.contains(ProviderFeature.PLAYLISTS) == true
        val canDownload = features?.contains(ProviderFeature.DOWNLOADS) == true
        val canShare = features?.contains(ProviderFeature.SHARING) == true
        if (canFavorite || canAddToPlaylist || canDownload || canShare) HorizontalDivider()
        if (canFavorite) {
            MenuSheetItem(
                ImageVector.vectorResource(if (isStarred) R.drawable.round_favorite_24 else R.drawable.round_favorite_border_24),
                stringResource(if (isStarred) R.string.action_remove_from_favorites else R.string.action_add_to_favorites)
            ) {
                isStarred = !isStarred
                viewModel.setStarred(id, libraryType, isStarred)
                onDismissRequest()
            }
        }
        if (canAddToPlaylist) {
            MenuSheetItem(Icons.Rounded.Add, stringResource(R.string.action_add_to_playlist)) {
                withSongs { songs, _ -> addToPlaylistSongs = songs }
            }
        }
        if (canDownload) {
            MenuSheetItem(ImageVector.vectorResource(R.drawable.rounded_download_24), stringResource(R.string.action_download)) {
                showDownloadDialog = true
                onDismissRequest()
            }
        }
        if (canShare) {
            MenuSheetItem(ImageVector.vectorResource(R.drawable.rounded_share_24), stringResource(R.string.action_share)) {
                showShareDialog = true
                onDismissRequest()
            }
        }
    }

    addToPlaylistSongs?.let { songs ->
        AddToPlaylist(
            onDismissRequest = { addToPlaylistSongs = null },
            mediaToAddToPlaylist = songs
        )
    }
    if (showDownloadDialog) {
        DownloadTargetDialog(
            onDismissRequest = { showDownloadDialog = false },
            onSelect = { target -> viewModel.download(target, getSongs) }
        )
    }
    if (showShareDialog) {
        ShareDialog(
            provider = provider,
            ids = listOf(id),
            title = title,
            onDismissRequest = { showShareDialog = false }
        )
    }
}
