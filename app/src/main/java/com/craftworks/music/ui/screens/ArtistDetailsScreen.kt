package com.craftworks.music.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.craftworks.music.R
import com.craftworks.music.data.model.ProviderFeatures
import com.craftworks.music.data.model.Screen
import com.craftworks.music.data.model.id
import com.craftworks.music.fadingEdge
import com.craftworks.music.player.SongHelper
import com.craftworks.music.ui.elements.ActionButton
import com.craftworks.music.ui.elements.ActionButtonType
import com.craftworks.music.ui.elements.AlbumCard
import com.craftworks.music.ui.elements.SongListActionButtons
import com.craftworks.music.ui.elements.dialogs.AddToPlaylist
import com.craftworks.music.ui.elements.dialogs.dialogFocusable
import com.craftworks.music.ui.viewmodels.ArtistsScreenViewModel
import com.craftworks.music.utils.bleedHorizontal
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@ExperimentalFoundationApi
@Composable
@Preview
fun ArtistDetails(
    selectedArtistId: String? = null,
    selectedArtistImage: String? = null,
    navHostController: NavHostController = rememberNavController(),
    mediaController: MediaController? = null,
    viewModel: ArtistsScreenViewModel = hiltViewModel()
) {
    val showLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val artist = viewModel.selectedArtist.collectAsStateWithLifecycle().value
    val artistAlbums = viewModel.artistAlbums.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    val imageFadingEdge = Brush.verticalGradient(listOf(Color.Red.copy(0.75f), Color.Transparent))

    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var addToPlaylistSongs by remember { mutableStateOf<List<MediaItem>>(emptyList()) }


    val coroutineScope = rememberCoroutineScope()

    val getArtistSongs : suspend () -> List<MediaItem> = {
        artistAlbums.flatMap {
            it.mediaMetadata.id.let { id ->
                val album = viewModel.getAlbum(id ?: "")
                if (album.isNotEmpty())
                    album.subList(1, album.size)
                else
                    emptyList()
            }
        }
    }

    LaunchedEffect(selectedArtistId) {
        if (selectedArtistId != null) viewModel.loadArtistDetails(selectedArtistId)
    }

    // Loading spinner
    AnimatedVisibility(
        visible = showLoading,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(64.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 6.dp
            )
            Text(
                text = "Loading",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }

    // Main Content
    AnimatedVisibility(
        visible = artist?.name?.isNotBlank() == true,
        enter = fadeIn()
    ) {
        var isStarred by remember { mutableStateOf(artist?.userFavorite ?: false) }

        LazyVerticalGrid(
            modifier = Modifier
                .fillMaxSize()
                .dialogFocusable(),
            columns = GridCells.Adaptive(96.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(24.dp,0.dp),
        ) {
            // Group songs by their source (Local or Navidrome)
            val groupedAlbums =
                artistAlbums.groupBy { it.mediaMetadata.recordingYear }
                    .toSortedMap(compareByDescending { it })

            item(span = { GridItemSpan(maxLineSpan) }) {
                //Image and Name
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(
                            selectedArtistImage ?: artist?.imageUrl
                            ?: artist?.imageId?.let {
                                artist.getProvider()?.getImageUrl(it)
                            } ?: "")
                        .diskCacheKey(selectedArtistId)
                        .crossfade(true)
                        .build(),
                    placeholder = painterResource(R.drawable.s_a_username),
                    fallback = painterResource(R.drawable.s_a_username),
                    contentScale = ContentScale.FillWidth,
                    contentDescription = "Artist Image",
                    modifier = Modifier
                        .bleedHorizontal(24.dp)
                        .height(320.dp)
                        .fadingEdge(imageFadingEdge)
                        .blur(12.dp)
                )
                Column(
                    modifier = Modifier
                        .padding(
                            top = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding(),
                            bottom = 8.dp
                        ).height(266.dp)
                ) {
                    // Back button
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

                    Spacer(Modifier.weight(1f))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Album Name and Artist
                        Text(
                            text = artist?.name.toString(),
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.headlineMediumEmphasized,
                            textAlign = TextAlign.Left,
                            modifier = Modifier.fillMaxWidth()
                        )


                        // Description
                        artist?.biography?.let { description ->
                            var expanded by remember { mutableStateOf(false) }

                            val regex = Regex("""<a\s+(?:[^>]*?\s+)?href="([^"]*)"""")
                            val matchResult = regex.find(description)
                            val extractedUrl = matchResult?.groups?.get(1)?.value

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = if (description.isBlank()) 0.dp else 32.dp)
                                    .animateContentSize()
                                    .clickable {
                                        expanded = !expanded
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (description.isNotBlank()) {
                                    Text(
                                        text = description.split("<a target").first(),
                                        color = MaterialTheme.colorScheme.onBackground,
                                        fontWeight = FontWeight.Light,
                                        fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                                        textAlign = TextAlign.Start,
                                        maxLines = if (expanded) 100 else 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(6.dp, 6.dp, 6.dp, 0.dp)
                                    )

                                    // Show more on last.fm  button
                                    if (extractedUrl != null && expanded) {
                                        Button(
                                            onClick = {
                                                val intent =
                                                    Intent(Intent.ACTION_VIEW, extractedUrl.toUri())
                                                context.startActivity(intent)
                                            },
                                            modifier = Modifier.widthIn(128.dp)
                                                .padding(vertical = 6.dp),
                                        ) {
                                            Text(
                                                text = "Last.FM",
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(6.dp))

                        // Play, shuffle and more buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                        ) {
                            val favoriteAction: () -> Unit = {
                                coroutineScope.launch {
                                    artist?.id?.let {
                                        if (isStarred)
                                            viewModel.unstarArtist(it)
                                        else
                                            viewModel.starArtist(it)
                                    }
                                    if (selectedArtistId != null) viewModel.loadArtistDetails(selectedArtistId)
                                    isStarred = !isStarred
                                }
                            }

                            SongListActionButtons(
                                buttons = listOf(
                                    ActionButton(
                                        ActionButtonType.FAVORITE,
                                        false,
                                        onClick = favoriteAction
                                    ),
                                    ActionButton(
                                        ActionButtonType.SHUFFLE,
                                        false
                                    ) {
                                        coroutineScope.launch {
                                            val allArtistSongsList = getArtistSongs()

                                            mediaController?.shuffleModeEnabled = true
                                            val random = allArtistSongsList.indices.random()
                                            SongHelper.play(
                                                allArtistSongsList,
                                                random,
                                                mediaController
                                            )
                                        }
                                    },
                                    ActionButton(
                                        ActionButtonType.ADD_TO_QUEUE,
                                        true
                                    ) {
                                        coroutineScope.launch {
                                            mediaController?.addMediaItems(getArtistSongs())
                                        }
                                    },
                                    ActionButton(
                                        ActionButtonType.PLAY_NEXT,
                                        true
                                    ) {
                                        coroutineScope.launch {
                                            val allArtistSongsList = getArtistSongs()
                                            mediaController?.addMediaItems(
                                                mediaController.currentMediaItemIndex + 1,
                                                allArtistSongsList
                                            )
                                        }
                                    },
                                    ActionButton(
                                        ActionButtonType.SEPARATOR,
                                        true
                                    ),
                                    ActionButton(
                                        ActionButtonType.FAVORITE,
                                        true,
                                        onClick = favoriteAction
                                    ),
                                    ActionButton(
                                        ActionButtonType.ADD_TO_PLAYLIST,
                                        true
                                    ) {
                                        coroutineScope.launch {
                                            addToPlaylistSongs = getArtistSongs()
                                            showAddToPlaylistDialog = true
                                        }
                                    },
                                    ActionButton(
                                        ActionButtonType.DOWNLOAD,
                                        true
                                    ) {
                                        coroutineScope.launch {
                                            viewModel.downloadArtist(getArtistSongs())
                                        }
                                    },
                                ),
                                providerFeatures = artist?.getProvider()?.featureFlags ?: ProviderFeatures(0L),
                                playAction = {
                                    coroutineScope.launch {
                                        SongHelper.play(
                                            getArtistSongs(),
                                            0,
                                            mediaController
                                        )
                                    }
                                },
                                isStarred = isStarred
                            )
                        }
                    }
                }
            }

            /* Discography header */
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(R.string.artist_details_discography),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            groupedAlbums.forEach { (groupName, albumsInGroup) ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = groupName.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .padding(top = 12.dp)
                    )
                }
                itemsIndexed(albumsInGroup) { index, album ->
                    AlbumCard(
                        album = album,
                        onClick = {
                            navHostController.navigate(
                                Screen.AlbumDetails(
                                    album.mediaMetadata.id ?: "",
                                    album.mediaMetadata.artworkUri.toString()
                                )
                            ) {
                                launchSingleTop = true
                            }
                        },
                        onPlay = {
                            coroutineScope.launch {
                                val mediaItems = viewModel.getAlbum(album.mediaMetadata.id ?: "")
                                if (mediaItems.isNotEmpty())
                                    SongHelper.play(
                                        mediaItems = mediaItems.subList(1, mediaItems.size),
                                        index = 0,
                                        mediaController = mediaController
                                    )
                            }
                        }
                    )
                }
            }
        }

        if (showAddToPlaylistDialog) {
            AddToPlaylist(
                onDismissRequest = { showAddToPlaylistDialog = false },
                mediaToAddToPlaylist = addToPlaylistSongs
            )
        }
    }
}