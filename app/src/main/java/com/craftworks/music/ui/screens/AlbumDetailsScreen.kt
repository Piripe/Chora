package com.craftworks.music.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import coil.request.ImageRequest
import com.craftworks.music.R
import com.craftworks.music.data.model.ProviderFeatures
import com.craftworks.music.data.model.favorite
import com.craftworks.music.data.model.getProvider
import com.craftworks.music.data.model.id
import com.craftworks.music.fadingEdge
import com.craftworks.music.formatSeconds
import com.craftworks.music.managers.settings.AppearanceSettingsManager
import com.craftworks.music.player.SongHelper
import com.craftworks.music.ui.elements.HorizontalSongCard
import com.craftworks.music.ui.elements.dialogs.AddToPlaylist
import com.craftworks.music.ui.elements.dialogs.dialogFocusable
import com.craftworks.music.ui.viewmodels.AlbumDetailsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@ExperimentalFoundationApi
@Composable
fun AlbumDetails(
    selectedAlbumId: String = "",
    selectedAlbumImage: Uri = Uri.EMPTY,
    navHostController: NavHostController = rememberNavController(),
    mediaController: MediaController? = null,
    viewModel: AlbumDetailsViewModel = hiltViewModel()
) {
    val imageFadingEdge = Brush.verticalGradient(listOf(Color.Red.copy(0.75f), Color.Transparent))

    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var showBottomSheet by remember { mutableStateOf(false) }

    var showLoading by remember { mutableStateOf(false) }
    val currentAlbum = viewModel.songsInAlbum.collectAsStateWithLifecycle().value
    val showTrackNumbers by AppearanceSettingsManager(LocalContext.current).showTrackNumbersFlow.collectAsStateWithLifecycle(
        false
    )

    var showAddToPlaylistDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current

    LaunchedEffect(selectedAlbumId) {
        showLoading = false

        viewModel.loadAlbumDetails(selectedAlbumId)

        delay(500.milliseconds)
        showLoading = true
    }

    // Loading spinner
    AnimatedVisibility(
        visible = currentAlbum.isEmpty() && showLoading,
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
        visible = currentAlbum.isNotEmpty(),
        enter = fadeIn()
    ) {
        var isStarred by remember {
            mutableStateOf(
                currentAlbum[0].mediaMetadata.favorite ?: false
            )
        }
        val requester = remember { FocusRequester() }

        val coroutineScope = rememberCoroutineScope()
        LaunchedEffect(Unit) {
            requester.requestFocus()
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .dialogFocusable(),
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            // Header
            item {
                Box(
                    modifier = Modifier
                        .height(320.dp)
                        .fillMaxWidth()
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(selectedAlbumImage)
                            .diskCacheKey(selectedAlbumId)
                            .placeholderMemoryCacheKey(selectedAlbumId)
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
                                top = WindowInsets.statusBars
                                    .asPaddingValues()
                                    .calculateTopPadding(),
                                bottom = 12.dp
                            )
                            .padding(horizontal = 24.dp)
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

                        // Album Name and Artist
                        Column(
                            modifier = Modifier
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = currentAlbum[0].mediaMetadata.title.toString(),
                                color = MaterialTheme.colorScheme.onBackground,
                                style = MaterialTheme.typography.headlineMediumEmphasized,
                                textAlign = TextAlign.Left,
                            )

                            Text(
                                text = currentAlbum[0].mediaMetadata.artist.toString() +
                                        " • " +
                                        currentAlbum[0].mediaMetadata.recordingYear.toString() +
                                        " • " +
                                        formatSeconds(currentAlbum[0].mediaMetadata.durationMs?.div(1000)?.toInt() ?: 0),
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Left
                            )

                            // Genres
                            if (!currentAlbum[0].mediaMetadata.genre.isNullOrEmpty()) {
                                Text(
                                    text = currentAlbum[0].mediaMetadata.genre?.split(",")?.joinToString(" • ").toString(),
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                    style = MaterialTheme.typography.titleSmallEmphasized,
                                    textAlign = TextAlign.Left
                                )
                            }

                            Spacer(Modifier.height(6.dp))

                            // Play, shuffle and more buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                            ) {
                                val interactionSources =
                                    remember { List(4) { MutableInteractionSource() } }

                                ButtonGroup(
                                    overflowIndicator = { menuState ->
                                        FilledTonalIconButton(
                                            onClick = { showBottomSheet = true },
                                            interactionSource = interactionSources[3],
                                            modifier = Modifier
                                                .height(64.dp)
                                        ) {
                                            Icon(
                                                Icons.Outlined.MoreVert,
                                                contentDescription = "More"
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .height(64.dp)
                                        .widthIn(max = 640.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // More
                                    customItem(
                                        buttonGroupContent = {
                                            FilledTonalIconButton(
                                                onClick = { showBottomSheet = true },
                                                interactionSource = interactionSources[3],
                                                modifier = Modifier
                                                    .height(64.dp)
                                                    .animateWidth(interactionSources[3]),
                                            ) {
                                                Icon(
                                                    Icons.Outlined.MoreVert,
                                                    contentDescription = "More"
                                                )
                                            }
                                        },
                                        menuContent = { showBottomSheet = true },
                                    )

                                    // Heart
                                    if (currentAlbum[0].mediaMetadata.getProvider()?.featureFlags?.has(
                                            ProviderFeatures.FAVORITES
                                        ) ?: false
                                    ) {
                                        customItem(
                                            buttonGroupContent = {
                                                val cornerRadius by animateDpAsState(
                                                    targetValue = if (isStarred) 12.dp else 32.dp,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                                        stiffness = Spring.StiffnessLow
                                                    ),
                                                    label = "Star Button Shape Animation"
                                                )

                                                FilledTonalIconButton(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            if (isStarred)
                                                                currentAlbum[0].mediaMetadata.id?.let {
                                                                    viewModel.unstarAlbum(it)
                                                                }
                                                            else
                                                                currentAlbum[0].mediaMetadata.id?.let {
                                                                    viewModel.starAlbum(it)
                                                                }
                                                            viewModel.loadAlbumDetails(
                                                                selectedAlbumId
                                                            )
                                                            isStarred = !isStarred
                                                        }
                                                    },
                                                    interactionSource = interactionSources[1],
                                                    modifier = Modifier
                                                        .size(64.dp)
                                                        .animateWidth(interactionSources[1]),
                                                    shape = RoundedCornerShape(cornerRadius)
                                                ) {
                                                    Crossfade(
                                                        targetState = isStarred
                                                    ) {
                                                        if (it) Icon(
                                                            imageVector = ImageVector.vectorResource(R.drawable.round_favorite_24),
                                                            contentDescription = stringResource(R.string.action_remove_from_favorites),
                                                        )
                                                        else Icon(
                                                            imageVector = ImageVector.vectorResource(R.drawable.round_favorite_border_24),
                                                            contentDescription = stringResource(R.string.action_add_to_favorites)
                                                        )
                                                    }
                                                }
                                            },
                                            menuContent = { },
                                        )
                                    }

                                    // Shuffle
                                    customItem(
                                        buttonGroupContent = {
                                            FilledTonalIconButton(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        val random = currentAlbum.subList(
                                                            1,
                                                            currentAlbum.size
                                                        ).indices.random()
                                                        SongHelper.play(
                                                            currentAlbum.subList(
                                                                1,
                                                                currentAlbum.size
                                                            ),
                                                            random,
                                                            mediaController
                                                        )
                                                    }
                                                },
                                                interactionSource = interactionSources[2],
                                                modifier = Modifier
                                                    .size(64.dp)
                                                    .animateWidth(interactionSources[2]),
                                                shape = CircleShape
                                            ) {
                                                Icon(
                                                    ImageVector.vectorResource(R.drawable.round_shuffle_28),
                                                    contentDescription = "Shuffle"
                                                )
                                            }
                                        },
                                        menuContent = { },
                                    )

                                    // Play pill — filled, primary
                                    clickableItem(
                                        onClick = {
                                            coroutineScope.launch {
                                                SongHelper.play(
                                                    currentAlbum.subList(1, currentAlbum.size),
                                                    0,
                                                    mediaController
                                                )
                                            }
                                        },
                                        label = "Play",
                                        icon = {
                                            Icon(
                                                Icons.Filled.PlayArrow,
                                                contentDescription = "Play"
                                            )
                                        },
                                        weight = 0.5f
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Album Songs
            val groupedAlbums = currentAlbum.subList(1, currentAlbum.size).groupBy { song ->
                song.mediaMetadata.discNumber
            }

            if (groupedAlbums.size > 1) {
                groupedAlbums.forEach { (discNumber, albumsInGroup) ->
                    item() {
                        Column(
                            modifier = Modifier
                                .padding(
                                    horizontal = 12.dp
                                )
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.album_details_disc,
                                    discNumber.toString()
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            )
                            HorizontalDivider(
                                modifier = Modifier
                                    .height(1.dp)
                                    .fillMaxWidth(),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                            )
                        }
                    }
                    items(albumsInGroup) { song ->
                        HorizontalSongCard(
                            song = song,
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .animateItem(),
                            showTrackNumber = showTrackNumbers,
                            onClick = {
                                coroutineScope.launch {
                                    SongHelper.play(
                                        currentAlbum.subList(1, currentAlbum.size),
                                        currentAlbum.subList(1, currentAlbum.size).indexOf(song),
                                        mediaController
                                    )
                                }
                            },
                            mediaController = mediaController
                        )
                    }
                }
            } else {
                items(currentAlbum.subList(1, currentAlbum.size)) { song ->
                    HorizontalSongCard(
                        song = song,
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .animateItem(),
                        showTrackNumber = showTrackNumbers,
                        onClick = {
                            coroutineScope.launch {
                                SongHelper.play(
                                    currentAlbum.subList(1, currentAlbum.size),
                                    currentAlbum.subList(1, currentAlbum.size).indexOf(song),
                                    mediaController
                                )
                            }
                        },
                        mediaController = mediaController
                    )
                }
            }
        }

        if (showAddToPlaylistDialog) {
            AddToPlaylist(
                onDismissRequest = { showAddToPlaylistDialog = false },
                mediaToAddToPlaylist = currentAlbum
            )
        }

        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState
            ) {
                // Content inside the bottom sheet
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    DropdownMenuItem(
                        onClick = {
                            showBottomSheet = false
                            coroutineScope.launch {
                                mediaController?.addMediaItems(
                                    currentAlbum.subList(
                                        1,
                                        currentAlbum.size
                                    )
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        leadingIcon = {
                            Icon(
                                imageVector = ImageVector.vectorResource(R.drawable.outline_queue_add_24),
                                contentDescription = stringResource(R.string.action_add_to_queue)
                            )
                        },
                        text = { Text(stringResource(R.string.action_add_to_queue)) }
                    )
                    DropdownMenuItem(
                        onClick = {
                            showBottomSheet = false
                            coroutineScope.launch {
                                mediaController?.addMediaItems(
                                    mediaController.currentMediaItemIndex + 1,
                                    currentAlbum.subList(
                                        1,
                                        currentAlbum.size
                                    )

                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        leadingIcon = {
                            Icon(
                                imageVector = ImageVector.vectorResource(R.drawable.play_next_24px),
                                contentDescription = stringResource(R.string.action_play_next)
                            )
                        },
                        text = { Text(stringResource(R.string.action_play_next)) }
                    )
                    if (currentAlbum[0].mediaMetadata.getProvider()?.featureFlags?.any(
                            ProviderFeatures.FAVORITES +
                                    ProviderFeatures.DOWNLOADS +
                                    ProviderFeatures.PLAYLIST
                        ) ?: false
                    ) {
                        HorizontalDivider()
                    }
                    if (currentAlbum[0].mediaMetadata.getProvider()?.featureFlags?.has(
                            ProviderFeatures.FAVORITES
                        ) ?: false
                    ) {
                        DropdownMenuItem(
                            onClick = {
                                showBottomSheet = false
                                coroutineScope.launch {
                                    if (isStarred)
                                        currentAlbum[0].mediaMetadata.id?.let {
                                            viewModel.unstarAlbum(it)
                                        }
                                    else
                                        currentAlbum[0].mediaMetadata.id?.let {
                                            viewModel.starAlbum(it)
                                        }
                                    viewModel.loadAlbumDetails(
                                        selectedAlbumId
                                    )
                                    isStarred = !isStarred
                                }
                            },
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .fillMaxWidth(),
                            leadingIcon = {
                                Crossfade(
                                    targetState = isStarred
                                ) {
                                    if (it) Icon(
                                        imageVector = ImageVector.vectorResource(
                                            R.drawable.round_favorite_24
                                        ),
                                        contentDescription = stringResource(
                                            R.string.action_remove_from_favorites
                                        )
                                    )
                                    else
                                        Icon(
                                            imageVector = ImageVector.vectorResource(
                                                R.drawable.round_favorite_border_24
                                            ),
                                            contentDescription = stringResource(
                                                R.string.action_add_to_favorites
                                            )
                                        )
                                }
                            },
                            text = {
                                Text(
                                    if (isStarred) stringResource(R.string.action_remove_from_favorites) else stringResource(
                                        R.string.action_add_to_favorites
                                    )
                                )
                            }
                        )
                    }
                    if (currentAlbum[0].mediaMetadata.getProvider()?.featureFlags?.has(
                            ProviderFeatures.PLAYLIST
                        ) ?: false
                    ) {
                        DropdownMenuItem(
                            onClick = {
                                showBottomSheet = false
                                showAddToPlaylistDialog = true
                            },
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .fillMaxWidth(),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = stringResource(R.string.action_add_to_playlist)
                                )
                            },
                            text = { Text(stringResource(R.string.action_add_to_playlist)) }
                        )
                    }

                    // TODO : Add Download and Share buttons
                }
            }
        }
    }
}

@Preview
@Composable
fun PlayerActionButtonGroup() {
    val interactionSources = remember { List(4) { MutableInteractionSource() } }

    ButtonGroup(
        overflowIndicator = { menuState ->
            ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
        },
        modifier = Modifier
            .height(64.dp)
            .widthIn(max = 640.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // More
        customItem(
            buttonGroupContent = {
                FilledTonalIconButton(
                    onClick = { /* show sheet */ },
                    interactionSource = interactionSources[3],
                    modifier = Modifier
                        .height(32.dp)
                        .animateWidth(interactionSources[3]),
                ) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "More")
                }
            },
            menuContent = { DropdownMenuItem(text = { Text("More") }, onClick = {}) },
        )

        // Heart
        customItem(
            buttonGroupContent = {
                FilledTonalIconButton(
                    onClick = { /* like */ },
                    interactionSource = interactionSources[1],
                    modifier = Modifier
                        .size(64.dp)
                        .animateWidth(interactionSources[1]),
                    shape = CircleShape
                ) {
                    Icon(Icons.Outlined.FavoriteBorder, contentDescription = "Like")
                }
            },
            menuContent = { DropdownMenuItem(text = { Text("Like") }, onClick = {}) },
        )

        // Shuffle
        customItem(
            buttonGroupContent = {
                FilledTonalIconButton(
                    onClick = { /* shuffle */ },
                    interactionSource = interactionSources[2],
                    modifier = Modifier
                        .size(64.dp)
                        .animateWidth(interactionSources[2]),
                    shape = CircleShape
                ) {
                    Icon(
                        ImageVector.vectorResource(R.drawable.round_shuffle_28),
                        contentDescription = "Shuffle"
                    )
                }
            },
            menuContent = { DropdownMenuItem(text = { Text("Shuffle") }, onClick = {}) },
        )

        // Play pill — filled, primary
        clickableItem(
            onClick = {},
            label = "Play",
            icon = {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
            },
        )
//                                    customItem(
//                                        buttonGroupContent = {
//                                            Button(
//                                                onClick = { /* play */ },
//                                                interactionSource = interactionSources[0],
//                                                modifier = Modifier.animateWidth(interactionSources[0]),
//                                            ) {
//                                                Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
//                                            }
//                                        },
//                                        menuContent = { DropdownMenuItem(text = { Text("Play") }, onClick = {}) },
//                                    )
    }
}