package com.craftworks.music.ui.screens.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.navigation.NavController
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.StandardCardContainer
import androidx.tv.material3.Text
import androidx.tv.material3.WideCardContainer
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.craftworks.music.R
import com.craftworks.music.data.model.FolderViewMode
import com.craftworks.music.data.model.MediaModel
import com.craftworks.music.data.model.Screen
import com.craftworks.music.data.model.id
import com.craftworks.music.managers.settings.AppearanceSettingsManager
import com.craftworks.music.player.SongHelper
import com.craftworks.music.ui.elements.dialogs.tv.GenericListDialog
import com.craftworks.music.ui.elements.dialogs.tv.SongDialog
import com.craftworks.music.ui.elements.dialogs.tv.TvFolderDialog
import com.craftworks.music.ui.elements.tv.TvHorizontalSongCard
import com.craftworks.music.ui.screens.icon
import com.craftworks.music.ui.screens.label
import com.craftworks.music.ui.viewmodels.FoldersScreenViewModel
import com.craftworks.music.ui.viewmodels.MediaCardMenuViewModel
import com.craftworks.music.ui.viewmodels.SongsScreenViewModel
import com.craftworks.music.ui.viewmodels.TreeEntry
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvFoldersScreen(
    mediaController: MediaController? = null,
    navHostController: NavController,
    viewModel: FoldersScreenViewModel = hiltViewModel(),
    songsViewModel: SongsScreenViewModel = hiltViewModel(),
    menuViewModel: MediaCardMenuViewModel = hiltViewModel(),
) {
    val path by viewModel.path.collectAsStateWithLifecycle()
    val currentFolder by viewModel.currentFolder.collectAsStateWithLifecycle()
    val folders by viewModel.visibleFolders.collectAsStateWithLifecycle()
    val songs by viewModel.visibleSongs.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val canGoBack by viewModel.canGoBack.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val folderIconsOnly by remember { AppearanceSettingsManager(context).folderIconsOnly }.collectAsStateWithLifecycle(false)
    val viewMode by remember { AppearanceSettingsManager(context).folderViewMode }.collectAsStateWithLifecycle(FolderViewMode.LIST)
    val treeEntries by viewModel.treeEntries.collectAsStateWithLifecycle()
    val hasContent = folders.isNotEmpty() || songs.isNotEmpty()
    val isDense = viewMode == FolderViewMode.COMPACT || viewMode == FolderViewMode.TREE

    val rootName = stringResource(R.string.nav_folders)
    val currentName = currentFolder?.name?.takeIf { path.size > 1 && it.isNotBlank() } ?: rootName

    var selectedSong by remember { mutableStateOf(MediaItem.EMPTY) }
    var showSongDialog by remember { mutableStateOf(false) }
    var dialogFolder by remember { mutableStateOf<MediaModel.Folder?>(null) }
    var showContentDialog by remember { mutableStateOf(false) }
    var showViewModeDialog by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val firstItemFocusRequester = remember { FocusRequester() }

    // Back returns to the previously opened folder before leaving the screen.
    BackHandler(enabled = canGoBack) { viewModel.goBack() }

    // The focused entry is gone when another folder opens: focus the first one.
    LaunchedEffect(currentFolder?.id) {
        if (currentFolder == null) return@LaunchedEffect
        gridState.scrollToItem(0)
        withFrameNanos { }
        try {
            firstItemFocusRequester.requestFocus()
        } catch (_: IllegalStateException) {
            // The folder is empty.
        }
    }

    val textFieldColors = TextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        cursorColor = MaterialTheme.colorScheme.inverseOnSurface,
        focusedTextColor = MaterialTheme.colorScheme.inverseOnSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedPlaceholderColor = MaterialTheme.colorScheme.inverseOnSurface,
        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedLeadingIconColor = MaterialTheme.colorScheme.inverseOnSurface,
        unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
    )

    LazyVerticalGrid(
        state = gridState,
        columns = if (viewMode == FolderViewMode.GRID) GridCells.Adaptive(160.dp) else GridCells.Fixed(1),
        modifier = Modifier
            .fillMaxSize()
            .focusGroup(),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(if (isDense) 8.dp else 24.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Up button and the path of the current folder, a parent folder opens when clicked.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.goUp() },
                        enabled = path.size > 1
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.arrow_upward_24px),
                            contentDescription = stringResource(R.string.folder_up)
                        )
                    }
                    TvFolderBreadcrumbs(path, rootName, onOpen = { viewModel.openAncestor(it) })
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { androidx.compose.material3.Text(stringResource(R.string.action_search)) },
                        leadingIcon = { androidx.compose.material3.Icon(Icons.Rounded.Search, contentDescription = null) },
                        singleLine = true,
                        colors = textFieldColors,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.width(320.dp)
                    )
                    Button(
                        onClick = {
                            menuViewModel.withSongs({ viewModel.getVisibleSongs() }) { songs ->
                                SongHelper.play(songs, 0, mediaController)
                                navHostController.navigate(Screen.NowPlayingLandscape) {
                                    launchSingleTop = true
                                }
                            }
                        },
                        enabled = hasContent
                    ) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.action_play))
                    }
                    OutlinedButton(
                        onClick = {
                            menuViewModel.withSongs({ viewModel.getVisibleSongs() }) { songs ->
                                SongHelper.play(songs.shuffled(), 0, mediaController)
                                navHostController.navigate(Screen.NowPlayingLandscape) {
                                    launchSingleTop = true
                                }
                            }
                        },
                        enabled = hasContent
                    ) {
                        Icon(
                            ImageVector.vectorResource(R.drawable.round_shuffle_28),
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.action_shuffle))
                    }
                    IconButton(
                        onClick = { showContentDialog = true },
                        enabled = currentFolder != null && hasContent
                    ) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more_options))
                    }
                    IconButton(onClick = { showViewModeDialog = true }) {
                        Icon(viewMode.icon(), contentDescription = stringResource(R.string.folder_view_mode))
                    }
                }
            }
        }

        if (!isLoading && currentFolder != null && !hasContent) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(R.string.folder_empty),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        val playSong: (List<MediaItem>, Int) -> Unit = { list, index ->
            coroutineScope.launch {
                SongHelper.play(list, index, mediaController)
                navHostController.navigate(Screen.NowPlayingLandscape) {
                    launchSingleTop = true
                }
            }
        }
        val onSongLongClick: (MediaItem) -> Unit = { song ->
            selectedSong = song
            showSongDialog = true
        }

        if (viewMode == FolderViewMode.TREE) {
            itemsIndexed(treeEntries, key = { _, entry -> entry.key }) { index, entry ->
                val focus = if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier
                when (entry) {
                    is TreeEntry.FolderEntry -> TvFolderCard(
                        folder = entry.folder,
                        iconOnly = folderIconsOnly,
                        compact = true,
                        modifier = focus.padding(start = TV_TREE_INDENT * entry.depth),
                        prefix = {
                            Icon(
                                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(start = 12.dp)
                                    .rotate(if (entry.isExpanded) 90f else 0f)
                            )
                        },
                        onClick = { viewModel.toggleFolder(entry.folder) },
                        onLongClick = { dialogFolder = entry.folder }
                    )

                    is TreeEntry.SongEntry -> TvHorizontalSongCard(
                        song = entry.song,
                        modifier = focus.padding(start = TV_TREE_INDENT * entry.depth),
                        onClick = { playSong(entry.siblings, entry.index) },
                        onLongClick = { onSongLongClick(entry.song) }
                    )
                }
            }
        } else {
            itemsIndexed(folders, key = { _, folder -> "folder:" + folder.id }) { index, folder ->
                val focus = if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier
                if (viewMode == FolderViewMode.GRID)
                    TvFolderTile(
                        folder = folder,
                        iconOnly = folderIconsOnly,
                        modifier = focus,
                        onClick = { viewModel.openFolder(folder) },
                        onLongClick = { dialogFolder = folder }
                    )
                else
                    TvFolderCard(
                        folder = folder,
                        iconOnly = folderIconsOnly,
                        compact = viewMode == FolderViewMode.COMPACT,
                        modifier = focus,
                        onClick = { viewModel.openFolder(folder) },
                        onLongClick = { dialogFolder = folder }
                    )
            }

            itemsIndexed(
                songs,
                key = { index, song -> "song:$index:${song.mediaId}" },
                span = { _, _ -> GridItemSpan(maxLineSpan) }
            ) { index, song ->
                TvHorizontalSongCard(
                    song = song,
                    modifier = if (folders.isEmpty() && index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier,
                    onClick = { playSong(songs, index) },
                    onLongClick = { onSongLongClick(song) }
                )
            }
        }
    }

    if (showViewModeDialog)
        GenericListDialog(
            titleRes = R.string.folder_view_mode,
            label = { it.label() },
            setShowDialog = { showViewModeDialog = it },
            options = FolderViewMode.entries,
            selectedOption = viewMode,
            onOptionSelected = { mode ->
                coroutineScope.launch { AppearanceSettingsManager(context).setFolderViewMode(mode) }
            }
        )

    dialogFolder?.let { folder ->
        TvFolderDialog(
            folder = folder,
            title = folder.name,
            onDismissRequest = { dialogFolder = null },
            viewModel = menuViewModel
        )
    }

    // The actions on the listed content (the search results while searching).
    if (showContentDialog) currentFolder?.let { folder ->
        TvFolderDialog(
            folder = folder,
            title = currentName,
            onDismissRequest = { showContentDialog = false },
            viewModel = menuViewModel,
            getSongs = { viewModel.getVisibleSongs() }
        )
    }

    if (showSongDialog)
        SongDialog(
            song = selectedSong,
            onSetRating = { rating ->
                songsViewModel.setSongRating(
                    songId = selectedSong.mediaMetadata.id ?: "",
                    rating = rating
                )
            },
            onDownload = { song, target ->
                songsViewModel.downloadSong(song, target)
            },
            setShowDialog = { showSongDialog = it }
        )
}

/** The folders from the root to the current one, clicking one opens it. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvFolderBreadcrumbs(path: List<MediaModel.Folder>, rootName: String, onOpen: (index: Int) -> Unit) {
    val listState = rememberLazyListState()
    // Keep the current folder visible.
    LaunchedEffect(path.size, path.lastOrNull()?.id) {
        if (path.isNotEmpty()) listState.scrollToItem(path.lastIndex)
    }

    LazyRow(
        state = listState,
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(path, key = { index, folder -> "$index:${folder.id}" }) { index, folder ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (index > 0)
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                val name = folder.name.takeIf { index > 0 && it.isNotBlank() } ?: rootName
                if (index == path.lastIndex)
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                else
                    OutlinedButton(onClick = { onOpen(index) }) {
                        Text(text = name, maxLines = 1)
                    }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvFolderCard(
    folder: MediaModel.Folder,
    iconOnly: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    /** Shown before the name (the chevron of the tree view). */
    prefix: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val folderIcon = @Composable {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.rounded_folder_24),
                contentDescription = null
            )
        }
    }

    WideCardContainer(
        modifier = modifier,
        imageCard = {
            Card(
                onClick = onClick,
                onLongClick = onLongClick,
                interactionSource = it,
                content = {
                    Box(modifier = Modifier.size(if (compact) 40.dp else 52.dp)) {
                        if (iconOnly || folder.imageUrl == null)
                            folderIcon()
                        else
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(folder.imageUrl)
                                    .crossfade(true)
                                    .size(64)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                loading = { folderIcon() },
                                error = { folderIcon() },
                                modifier = Modifier.fillMaxSize()
                            )
                    }
                }
            )
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                prefix?.invoke()
                Text(
                    text = folder.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = if (prefix != null) 4.dp else 16.dp)
                )
            }
        }
    )
}

/** A folder of the grid view: its picture, and its name below. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvFolderTile(
    folder: MediaModel.Folder,
    iconOnly: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val folderIcon = @Composable {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.rounded_folder_24),
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
        }
    }

    StandardCardContainer(
        modifier = modifier,
        imageCard = {
            Card(
                onClick = onClick,
                onLongClick = onLongClick,
                interactionSource = it,
                modifier = Modifier.aspectRatio(1f),
                content = {
                    if (iconOnly || folder.imageUrl == null)
                        folderIcon()
                    else
                        SubcomposeAsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(folder.imageUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            loading = { folderIcon() },
                            error = { folderIcon() },
                            modifier = Modifier.fillMaxSize()
                        )
                }
            )
        },
        title = {
            Text(
                text = folder.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    )
}

/** How much each level of the tree view is shifted. */
private val TV_TREE_INDENT = 32.dp
