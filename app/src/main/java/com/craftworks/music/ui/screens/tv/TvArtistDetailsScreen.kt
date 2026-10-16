package com.craftworks.music.ui.screens.tv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.session.MediaController
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.craftworks.music.R
import com.craftworks.music.data.model.MediaModel
import com.craftworks.music.data.model.ProviderFeature
import com.craftworks.music.data.model.Screen
import com.craftworks.music.data.model.id
import com.craftworks.music.player.SongHelper
import com.craftworks.music.ui.elements.cardImageUrl
import com.craftworks.music.ui.elements.tv.TvAlbumCard
import com.craftworks.music.ui.elements.tv.TvArtistCard
import com.craftworks.music.ui.viewmodels.ArtistDetailsViewModel
import com.craftworks.music.ui.viewmodels.MediaCardMenuViewModel
import kotlinx.coroutines.launch

@Composable
@Preview
fun TvArtistDetailsScreen(
    selectedArtistId: String? = null,
    selectedArtistImage: String? = null,
    navHostController: NavHostController = rememberNavController(),
    mediaController: MediaController? = null,
    viewModel: ArtistDetailsViewModel = hiltViewModel(),
    initialArtist: MediaModel.Artist? = null,
    mixViewModel: MediaCardMenuViewModel = hiltViewModel()
) {
    LaunchedEffect(selectedArtistId) {
        if (selectedArtistId != null) viewModel.loadArtistDetails(selectedArtistId, initialArtist)
    }

    val showLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val artist = viewModel.selectedArtist.collectAsStateWithLifecycle().value
    val artistAlbums = viewModel.artistAlbums.collectAsStateWithLifecycle().value
    val appearsOn = viewModel.appearsOn.collectAsStateWithLifecycle().value

    AnimatedVisibility(
        visible = showLoading || artist == null,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                modifier = Modifier.size(64.dp),
                strokeWidth = 6.dp
            )
        }
    }

    AnimatedVisibility(
        visible = !showLoading,
        enter = fadeIn()
    ) {
        val coroutineScope = rememberCoroutineScope()
        val playRequester = remember { FocusRequester() }

        LaunchedEffect(Unit) {
            playRequester.requestFocus()
        }
        val groupedAlbums =
            artistAlbums.groupBy { it.mediaMetadata.recordingYear?.takeIf { year -> year != 0 } }
                .toSortedMap(compareByDescending { it })

        LazyVerticalGrid(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 24.dp),
            columns = GridCells.Fixed(5),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(span = { GridItemSpan(5) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusGroup(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(selectedArtistImage ?: "android.resource://com.craftworks.music/${R.drawable.placeholder}")
                            .diskCacheKey(selectedArtistId)
                            .crossfade(true)
                            .build(),
                        fallback = painterResource(R.drawable.rounded_artist_24),
                        contentScale = ContentScale.Crop,
                        contentDescription = null,
                        modifier = Modifier
                            .size(240.dp)
                            .clip(CircleShape)
                    )

                    // Artist Name
                    Column(
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = artist?.name ?: "",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )

                        // Biography
                        Text(
                            text = artist?.biography?.split("<a target")?.first() ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        val allArtistSongsList = artistAlbums.map {
                                            it.mediaMetadata.id.let { id ->
                                                val album = viewModel.getAlbum(id ?: "")
                                                if (album.isNotEmpty())
                                                    album.subList(1, album.size)
                                                else
                                                    emptyList()
                                            }
                                        }

                                        SongHelper.play(
                                            allArtistSongsList.flatten(),
                                            0,
                                            mediaController
                                        )
                                        navHostController.navigate(Screen.NowPlayingLandscape) {
                                            launchSingleTop = true
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(playRequester),
                                contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                            ) {
                                Icon(
                                    Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(ButtonDefaults.IconSize),
                                )
                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                Text(stringResource(R.string.action_play))
                            }

                            OutlinedButton(
                                onClick = {
                                    mediaController?.shuffleModeEnabled = true
                                    coroutineScope.launch {
                                        val allArtistSongsList = artistAlbums.map {
                                            it.mediaMetadata.id.let { id ->
                                                val album = viewModel.getAlbum(id ?: "")
                                                if (album.isNotEmpty())
                                                    album.subList(1, album.size)
                                                else
                                                    emptyList()
                                            }
                                        }

                                        mediaController?.shuffleModeEnabled = true
                                        val random = allArtistSongsList.indices.random()
                                        SongHelper.play(
                                            allArtistSongsList.flatten(),
                                            random,
                                            mediaController
                                        )
                                        navHostController.navigate(Screen.NowPlayingLandscape) {
                                            launchSingleTop = true
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f),
                                contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                            ) {
                                Icon(
                                    ImageVector.vectorResource(R.drawable.round_shuffle_28),
                                    contentDescription = null,
                                    modifier = Modifier.size(ButtonDefaults.IconSize),
                                )
                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                Text(stringResource(R.string.action_shuffle))
                            }

                            if (artist?.getProvider()?.featureFlags?.contains(ProviderFeature.RADIO) == true) {
                                OutlinedButton(
                                    onClick = {
                                        mixViewModel.playMix(
                                            getMix = { artist?.let { mixViewModel.getArtistRadio(it) }.orEmpty() },
                                            onPlay = {
                                                navHostController.navigate(Screen.NowPlayingLandscape) {
                                                    launchSingleTop = true
                                                }
                                            }
                                        ) { mediaController }
                                    },
                                    modifier = Modifier
                                        .weight(1f),
                                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                                ) {
                                    Icon(
                                        ImageVector.vectorResource(R.drawable.rounded_sensors_24),
                                        contentDescription = null,
                                        modifier = Modifier.size(ButtonDefaults.IconSize),
                                    )
                                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                    Text(stringResource(R.string.action_start_radio))
                                }
                            }
                        }
                    }
                }
            }

            groupedAlbums.forEach { (groupName, albumsInGroup) ->
                // Albums without a year are listed last, without a header
                if (groupName != null) item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = groupName.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                    )
                }
                items(albumsInGroup) { album ->
                    TvAlbumCard(
                        album = album,
                        onClick = {
                            navHostController.navigate(Screen.AlbumDetails(album.mediaMetadata.id?:"", album.mediaMetadata.artworkUri.toString())) {
                                launchSingleTop = true
                            }
                        },
                    )
                }
            }

            if (appearsOn.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    TvArtistSectionTitle(stringResource(R.string.artist_details_appears_on))
                }
                items(appearsOn) { album ->
                    TvAlbumCard(
                        album = album,
                        onClick = {
                            navHostController.navigate(Screen.AlbumDetails(album.mediaMetadata.id?:"", album.mediaMetadata.artworkUri.toString())) {
                                launchSingleTop = true
                            }
                        },
                    )
                }
            }

            val relatedArtists = artist?.similarArtists.orEmpty()
            if (relatedArtists.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    TvArtistSectionTitle(stringResource(R.string.artist_details_related_artists))
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LazyRow(
                        modifier = Modifier.focusGroup(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(relatedArtists) { relatedArtist ->
                            TvArtistCard(
                                artist = relatedArtist,
                                onClick = {
                                    // Not single top: it would replace this artist instead of opening on top of it.
                                    navHostController.navigate(Screen.ArtistDetails(relatedArtist.id, relatedArtist.cardImageUrl()))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Title of a section of the artist details (appears on, related artists). */
@Composable
private fun TvArtistSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
    )
}
