package com.craftworks.music.ui.elements.dialogs.tv

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.tv.material3.Card
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.WideCardContainer
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.craftworks.music.R
import com.craftworks.music.data.model.DownloadTarget
import com.craftworks.music.data.model.LibraryType
import com.craftworks.music.data.model.MediaModel
import com.craftworks.music.data.model.ProviderFeature
import com.craftworks.music.data.model.favorite
import com.craftworks.music.data.model.getProvider
import com.craftworks.music.data.model.id
import com.craftworks.music.data.model.providerId
import com.craftworks.music.data.providers.media.MediaProvider
import com.craftworks.music.player.SongHelper
import com.craftworks.music.ui.elements.cardImageUrl
import com.craftworks.music.ui.elements.currentMediaController
import com.craftworks.music.ui.viewmodels.MediaCardMenuViewModel
import com.craftworks.music.ui.viewmodels.PlaylistScreenViewModel

private enum class CardDialogMenu { MAIN, ADD_TO_PLAYLIST, DOWNLOAD, SHARE }

/** The long press dialog of [com.craftworks.music.ui.elements.tv.TvAlbumCard]. */
@Composable
fun TvAlbumDialog(
    album: MediaItem,
    onDismissRequest: () -> Unit,
    viewModel: MediaCardMenuViewModel = hiltViewModel()
) {
    val albumId = album.mediaMetadata.id ?: return
    TvMediaCardDialog(
        provider = album.mediaMetadata.getProvider(),
        providerId = album.mediaMetadata.providerId,
        id = albumId,
        title = album.mediaMetadata.title?.toString() ?: "",
        subtitle = album.mediaMetadata.artist?.toString() ?: "",
        imageUrl = album.mediaMetadata.artworkUri,
        libraryType = LibraryType.ALBUM,
        initiallyStarred = album.mediaMetadata.favorite ?: false,
        getSongs = { viewModel.getAlbumSongs(albumId) },
        getRadio = { viewModel.getAlbumRadio(album) },
        onDismissRequest = onDismissRequest,
        viewModel = viewModel
    )
}

/** The long press dialog of [com.craftworks.music.ui.elements.tv.TvArtistCard]. */
@Composable
fun TvArtistDialog(
    artist: MediaModel.Artist,
    onDismissRequest: () -> Unit,
    viewModel: MediaCardMenuViewModel = hiltViewModel()
) {
    TvMediaCardDialog(
        provider = artist.getProvider(),
        providerId = artist.providerId,
        id = artist.id,
        title = artist.name,
        subtitle = "",
        imageUrl = artist.cardImageUrl()?.let { Uri.parse(it) },
        libraryType = LibraryType.ARTIST,
        initiallyStarred = artist.userFavorite ?: false,
        getSongs = { viewModel.getArtistSongs(artist.id) },
        getRadio = { viewModel.getArtistRadio(artist) },
        onDismissRequest = onDismissRequest,
        viewModel = viewModel
    )
}

/**
 * The long press dialog of a folder, also used for the actions of the folder screen on the listed content.
 * [getSongs] returns the songs the actions apply to, by default those of [folder] and its subfolders.
 */
@Composable
fun TvFolderDialog(
    folder: MediaModel.Folder,
    title: String,
    onDismissRequest: () -> Unit,
    viewModel: MediaCardMenuViewModel = hiltViewModel(),
    getSongs: suspend () -> List<MediaItem> = { viewModel.getFolderSongs(folder) }
) {
    // Servers can't favorite, share or make a radio from a folder.
    TvMediaCardDialog(
        provider = folder.getProvider(),
        providerId = folder.providerId,
        id = folder.id,
        title = title,
        subtitle = "",
        imageUrl = folder.imageUrl?.let { Uri.parse(it) },
        libraryType = null,
        initiallyStarred = false,
        getSongs = getSongs,
        getRadio = null,
        onDismissRequest = onDismissRequest,
        viewModel = viewModel
    )
}

@Composable
private fun TvMediaCardDialog(
    provider: MediaProvider?,
    providerId: String?,
    id: String,
    title: String,
    subtitle: String,
    imageUrl: Uri?,
    /** The type to favorite and share the item as, null when it can't be. */
    libraryType: LibraryType?,
    initiallyStarred: Boolean,
    getSongs: suspend () -> List<MediaItem>,
    /** Returns the radio of the item, null when it has none. */
    getRadio: (suspend () -> List<MediaItem>)?,
    onDismissRequest: () -> Unit,
    viewModel: MediaCardMenuViewModel
) {
    val context = LocalContext.current
    val features = provider?.featureFlags
    val backgroundColor = MaterialTheme.colorScheme.surface

    var dialogMenu by remember { mutableStateOf(CardDialogMenu.MAIN) }
    var isStarred by remember(id, initiallyStarred) { mutableStateOf(initiallyStarred) }

    // Closes the dialog, then runs the action with the songs once they are fetched.
    fun withSongs(action: suspend (songs: List<MediaItem>, controller: MediaController?) -> Unit) {
        onDismissRequest()
        viewModel.withSongs(getSongs) { songs -> action(songs, currentMediaController(context)) }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind { drawRect(color = backgroundColor) }
                .padding(horizontal = 48.dp, vertical = 48.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.widthIn(max = 320.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WideCardContainer(
                    modifier = Modifier.fillMaxWidth(),
                    imageCard = {
                        Card(
                            onClick = { },
                            content = {
                                SubcomposeAsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(imageUrl)
                                        .crossfade(true)
                                        .diskCacheKey(id)
                                        .build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(52.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .focusable(false),
                                )
                            }
                        )
                    },
                    title = {
                        Text(
                            text = title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    },
                    subtitle = {
                        if (subtitle.isNotEmpty()) Text(
                            text = subtitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    },
                )

                when (dialogMenu) {
                    CardDialogMenu.MAIN -> {
                        ListItem(
                            selected = false,
                            headlineContent = { Text(stringResource(R.string.action_play)) },
                            onClick = { withSongs { songs, controller -> SongHelper.play(songs, 0, controller) } }
                        )
                        ListItem(
                            selected = false,
                            headlineContent = { Text(stringResource(R.string.action_shuffle)) },
                            onClick = { withSongs { songs, controller -> SongHelper.play(songs.shuffled(), 0, controller) } }
                        )
                        ListItem(
                            selected = false,
                            headlineContent = { Text(stringResource(R.string.action_play_next)) },
                            onClick = { withSongs { songs, controller -> SongHelper.playNext(songs, controller) } }
                        )
                        ListItem(
                            selected = false,
                            headlineContent = { Text(stringResource(R.string.action_add_to_queue)) },
                            onClick = { withSongs { songs, controller -> SongHelper.enqueue(songs, controller) } }
                        )
                        if (getRadio != null && features?.contains(ProviderFeature.RADIO) == true) {
                            ListItem(
                                selected = false,
                                headlineContent = { Text(stringResource(R.string.action_start_radio)) },
                                onClick = {
                                    onDismissRequest()
                                    viewModel.playMix(getRadio) { currentMediaController(context) }
                                }
                            )
                        }
                        if (libraryType != null && features?.contains(ProviderFeature.FAVORITES) == true) {
                            ListItem(
                                selected = false,
                                headlineContent = {
                                    Text(stringResource(if (isStarred) R.string.action_remove_from_favorites else R.string.action_add_to_favorites))
                                },
                                onClick = {
                                    isStarred = !isStarred
                                    viewModel.setStarred(id, libraryType!!, isStarred)
                                }
                            )
                        }
                        if (features?.contains(ProviderFeature.PLAYLISTS) == true) {
                            ListItem(
                                selected = false,
                                headlineContent = { Text(stringResource(R.string.action_add_to_playlist)) },
                                onClick = { dialogMenu = CardDialogMenu.ADD_TO_PLAYLIST }
                            )
                        }
                        if (features?.contains(ProviderFeature.DOWNLOADS) == true) {
                            ListItem(
                                selected = false,
                                headlineContent = { Text(stringResource(R.string.action_download)) },
                                onClick = { dialogMenu = CardDialogMenu.DOWNLOAD }
                            )
                        }
                        if (libraryType != null && features?.contains(ProviderFeature.SHARING) == true) {
                            ListItem(
                                selected = false,
                                headlineContent = { Text(stringResource(R.string.action_share)) },
                                onClick = { dialogMenu = CardDialogMenu.SHARE }
                            )
                        }
                    }

                    CardDialogMenu.ADD_TO_PLAYLIST -> {
                        AddSongsToPlaylist(
                            providerId = providerId,
                            onSelect = { playlistId, playlistViewModel ->
                                withSongs { songs, _ ->
                                    playlistViewModel.addSongsToPlaylist(playlistId, songs.mapNotNull { it.mediaMetadata.id })
                                }
                            }
                        )
                    }

                    CardDialogMenu.DOWNLOAD -> {
                        for ((target, targetTitle, description) in listOf(
                            Triple(DownloadTarget.APP, R.string.download_target_app, R.string.download_target_app_description),
                            Triple(DownloadTarget.DEVICE, R.string.download_target_device, R.string.download_target_device_description),
                        )) {
                            ListItem(
                                selected = false,
                                headlineContent = { Text(stringResource(targetTitle)) },
                                supportingContent = { Text(stringResource(description)) },
                                onClick = {
                                    viewModel.download(target, getSongs)
                                    onDismissRequest()
                                }
                            )
                        }
                    }

                    CardDialogMenu.SHARE -> {
                        TvShareMenu(
                            provider = provider,
                            ids = listOf(id),
                            title = title,
                            onDismiss = onDismissRequest
                        )
                    }
                }
            }
        }
    }
}

/** The playlists of the provider with [providerId], as songs can only be added to playlists of their provider. */
@Composable
private fun AddSongsToPlaylist(
    providerId: String?,
    onSelect: (playlistId: String, viewModel: PlaylistScreenViewModel) -> Unit,
    viewModel: PlaylistScreenViewModel = hiltViewModel()
) {
    val playlists by viewModel.allPlaylists.collectAsStateWithLifecycle()
    playlists.filter { it.mediaMetadata.providerId == providerId }.forEach { playlist ->
        ListItem(
            selected = false,
            headlineContent = {
                Text(
                    text = playlist.mediaMetadata.title.toString(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            onClick = { onSelect(playlist.mediaMetadata.id ?: return@ListItem, viewModel) }
        )
    }
}
