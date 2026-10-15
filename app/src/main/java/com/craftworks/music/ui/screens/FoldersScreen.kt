package com.craftworks.music.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.craftworks.music.R
import com.craftworks.music.data.model.FolderViewMode
import com.craftworks.music.data.model.MediaModel
import com.craftworks.music.managers.settings.AppearanceSettingsManager
import com.craftworks.music.player.SongHelper
import com.craftworks.music.ui.elements.ActionTooltip
import com.craftworks.music.ui.elements.FolderCardMenu
import com.craftworks.music.ui.elements.HorizontalSongCard
import com.craftworks.music.ui.elements.LazyGridScrollbar
import com.craftworks.music.ui.elements.LazyListScrollbar
import com.craftworks.music.ui.elements.RandomShapeRefreshIndicator
import com.craftworks.music.ui.elements.RippleEffect
import com.craftworks.music.ui.playing.dpToPx
import com.craftworks.music.ui.viewmodels.FoldersScreenViewModel
import com.craftworks.music.ui.viewmodels.MediaCardMenuViewModel
import com.craftworks.music.ui.viewmodels.SongsScreenViewModel
import com.craftworks.music.ui.viewmodels.TreeEntry
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersScreen(
    mediaController: MediaController? = null,
    viewModel: FoldersScreenViewModel = hiltViewModel(),
    songsViewModel: SongsScreenViewModel = hiltViewModel(),
    menuViewModel: MediaCardMenuViewModel = hiltViewModel()
) {
    val path by viewModel.path.collectAsStateWithLifecycle()
    val currentFolder by viewModel.currentFolder.collectAsStateWithLifecycle()
    val folders by viewModel.visibleFolders.collectAsStateWithLifecycle()
    val songs by viewModel.visibleSongs.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val canGoBack by viewModel.canGoBack.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isLoading.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val folderIconsOnly by remember { AppearanceSettingsManager(context).folderIconsOnly }.collectAsStateWithLifecycle(false)
    val viewMode by remember { AppearanceSettingsManager(context).folderViewMode }.collectAsStateWithLifecycle(FolderViewMode.LIST)
    val treeEntries by viewModel.treeEntries.collectAsStateWithLifecycle()

    val coroutineScope = rememberCoroutineScope()
    val state = rememberPullToRefreshState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    var isSearching by remember { mutableStateOf(false) }
    // A new list position for each folder, so an opened folder starts at the top.
    val listState = remember(currentFolder?.id, viewMode) { LazyListState() }
    val gridState = remember(currentFolder?.id) { LazyGridState() }
    var showMenu by remember { mutableStateOf(false) }
    var showViewModeMenu by remember { mutableStateOf(false) }

    var showRipple by remember { mutableIntStateOf(0) }
    val rippleXOffset = LocalWindowInfo.current.containerSize.width / 2
    val rippleYOffset = dpToPx(12)

    val rootName = stringResource(R.string.nav_folders)
    val currentName = currentFolder?.name?.takeIf { path.size > 1 && it.isNotBlank() } ?: rootName
    val hasContent = folders.isNotEmpty() || songs.isNotEmpty()

    // Opening another folder clears the search.
    LaunchedEffect(currentFolder?.id) { isSearching = false }

    // Back closes the search, then returns to the previously opened folder before leaving the screen.
    BackHandler(enabled = isSearching || canGoBack) {
        if (isSearching) {
            isSearching = false
            viewModel.setSearchQuery("")
        } else viewModel.goBack()
    }

    PullToRefreshBox(
        state = state,
        isRefreshing = isRefreshing,
        onRefresh = {
            viewModel.refresh()
            showRipple++
        },
        indicator = {
            RandomShapeRefreshIndicator(
                state = state,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    ) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        if (path.size > 1) {
                            ActionTooltip(stringResource(R.string.folder_up)) {
                                IconButton(onClick = { viewModel.goUp() }) {
                                    Icon(
                                        ImageVector.vectorResource(R.drawable.arrow_upward_24px),
                                        contentDescription = stringResource(R.string.folder_up)
                                    )
                                }
                            }
                        }
                    },
                    title = {
                        if (isSearching)
                            FolderSearchField(searchQuery, onQueryChange = { viewModel.setSearchQuery(it) })
                        else
                            FolderBreadcrumbs(path, rootName, onOpen = { viewModel.openAncestor(it) })
                    },
                    actions = {
                        if (isSearching) {
                            ActionTooltip(stringResource(R.string.action_close_search)) {
                                IconButton(onClick = {
                                    isSearching = false
                                    viewModel.setSearchQuery("")
                                }) {
                                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_close_search))
                                }
                            }
                        } else {
                            ActionTooltip(stringResource(R.string.action_search)) {
                                IconButton(onClick = { isSearching = true }) {
                                    Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.action_search))
                                }
                            }
                        }
                        Box {
                            ActionTooltip(stringResource(R.string.folder_view_mode)) {
                                IconButton(onClick = { showViewModeMenu = true }) {
                                    Icon(viewMode.icon(), contentDescription = stringResource(R.string.folder_view_mode))
                                }
                            }
                            DropdownMenu(
                                expanded = showViewModeMenu,
                                onDismissRequest = { showViewModeMenu = false }
                            ) {
                                FolderViewMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(mode.label()) },
                                        leadingIcon = { Icon(mode.icon(), contentDescription = null) },
                                        trailingIcon = {
                                            if (mode == viewMode) Icon(Icons.Rounded.Check, contentDescription = null)
                                        },
                                        onClick = {
                                            showViewModeMenu = false
                                            coroutineScope.launch { AppearanceSettingsManager(context).setFolderViewMode(mode) }
                                        }
                                    )
                                }
                            }
                        }
                        ActionTooltip(stringResource(R.string.action_play)) {
                            IconButton(
                                onClick = {
                                    menuViewModel.withSongs({ viewModel.getVisibleSongs() }) { songs ->
                                        SongHelper.play(songs, 0, mediaController)
                                    }
                                },
                                enabled = hasContent
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.action_play))
                            }
                        }
                        ActionTooltip(stringResource(R.string.action_more_options)) {
                            IconButton(
                                onClick = { showMenu = true },
                                enabled = currentFolder != null && hasContent
                            ) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more))
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
            ) {
                if (!isRefreshing && currentFolder != null && !hasContent) {
                    Text(
                        text = stringResource(R.string.folder_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(0.75f),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 32.dp)
                    )
                }

                val playSong: (List<MediaItem>, Int) -> Unit = { list, index ->
                    coroutineScope.launch { SongHelper.play(list, index, mediaController) }
                }

                when (viewMode) {
                    FolderViewMode.LIST, FolderViewMode.COMPACT -> {
                        val compact = viewMode == FolderViewMode.COMPACT
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(vertical = 12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = if (compact) 8.dp else 12.dp),
                            verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp),
                        ) {
                            items(folders, key = { "folder:" + it.id }) { folder ->
                                FolderRow(folder, folderIconsOnly, menuViewModel, compact = compact) { viewModel.openFolder(folder) }
                            }
                            itemsIndexed(songs, key = { index, song -> "song:$index:${song.mediaId}" }) { index, song ->
                                HorizontalSongCard(
                                    song = song,
                                    onClick = { playSong(songs, index) },
                                    viewModel = songsViewModel,
                                    mixViewModel = menuViewModel,
                                    mediaController = mediaController,
                                    compact = compact
                                )
                            }
                        }
                        LazyListScrollbar(listState, Modifier.align(Alignment.CenterEnd))
                    }

                    FolderViewMode.GRID -> {
                        LazyVerticalGrid(
                            state = gridState,
                            contentPadding = PaddingValues(vertical = 12.dp),
                            columns = GridCells.Adaptive(120.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            gridItems(folders, key = { "folder:" + it.id }) { folder ->
                                FolderTile(folder, folderIconsOnly, menuViewModel) { viewModel.openFolder(folder) }
                            }
                            // Songs are rows below the folders, so their details stay readable.
                            gridItemsIndexed(
                                songs,
                                key = { index, song -> "song:$index:${song.mediaId}" },
                                span = { _, _ -> GridItemSpan(maxLineSpan) }
                            ) { index, song ->
                                HorizontalSongCard(
                                    song = song,
                                    onClick = { playSong(songs, index) },
                                    viewModel = songsViewModel,
                                    mixViewModel = menuViewModel,
                                    mediaController = mediaController
                                )
                            }
                        }
                        LazyGridScrollbar(gridState, Modifier.align(Alignment.CenterEnd))
                    }

                    FolderViewMode.TREE -> {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(vertical = 12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            items(treeEntries, key = { it.key }) { entry ->
                                val indent = Modifier.padding(start = TREE_INDENT * entry.depth)
                                when (entry) {
                                    is TreeEntry.FolderEntry -> FolderRow(
                                        folder = entry.folder,
                                        iconOnly = folderIconsOnly,
                                        menuViewModel = menuViewModel,
                                        compact = true,
                                        modifier = indent,
                                        leading = { TreeChevron(entry.isExpanded, entry.isLoading) },
                                        onClick = { viewModel.toggleFolder(entry.folder) }
                                    )

                                    is TreeEntry.SongEntry -> HorizontalSongCard(
                                        song = entry.song,
                                        // Lined up with the folder names, past the chevron.
                                        modifier = Modifier.padding(start = TREE_INDENT * entry.depth + 32.dp),
                                        onClick = { playSong(entry.siblings, entry.index) },
                                        viewModel = songsViewModel,
                                        mixViewModel = menuViewModel,
                                        mediaController = mediaController,
                                        compact = true
                                    )
                                }
                            }
                        }
                        LazyListScrollbar(listState, Modifier.align(Alignment.CenterEnd))
                    }
                }
            }
        }
    }

    // The actions of the top bar apply to the listed content (the search results while searching).
    currentFolder?.let { folder ->
        FolderCardMenu(
            folder = folder,
            title = currentName,
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            viewModel = menuViewModel,
            getSongs = { viewModel.getVisibleSongs() }
        )
    }

    RippleEffect(
        center = Offset(rippleXOffset.toFloat(), rippleYOffset.toFloat()),
        color = MaterialTheme.colorScheme.surfaceVariant,
        key = showRipple
    )
}

/** The folders from the root to the current one, tapping one opens it. */
@Composable
private fun FolderBreadcrumbs(path: List<MediaModel.Folder>, rootName: String, onOpen: (index: Int) -> Unit) {
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
            val isCurrent = index == path.lastIndex
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (index > 0)
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                Text(
                    text = folder.name.takeIf { index > 0 && it.isNotBlank() } ?: rootName,
                    fontWeight = if (isCurrent && path.count() > 1) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .then(if (isCurrent) Modifier else Modifier.clickable { onOpen(index) })
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun FolderSearchField(query: String, onQueryChange: (String) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.action_search)) },
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRow(
    folder: MediaModel.Folder,
    iconOnly: Boolean,
    menuViewModel: MediaCardMenuViewModel,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
    /** Shown before the folder picture (the chevron of the tree view). */
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var showMenu by remember { mutableStateOf(false) }

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
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(if (compact) 56.dp else 72.dp)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    showMenu = true
                }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading?.invoke()
        Box(
            modifier = Modifier
                .padding(start = 4.dp)
                .size(if (compact) 44.dp else 60.dp)
                .clip(RoundedCornerShape(if (compact) 8.dp else 12.dp))
        ) {
            if (iconOnly || folder.imageUrl == null)
                folderIcon()
            else
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(folder.imageUrl)
                        .crossfade(true)
                        .diskCacheKey(folder.imageId)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    loading = { folderIcon() },
                    error = { folderIcon() },
                    modifier = Modifier.fillMaxSize()
                )
        }

        Spacer(modifier = Modifier.width(if (compact) 8.dp else 12.dp))

        Text(
            text = folder.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        )
    }

    FolderCardMenu(
        folder = folder,
        title = folder.name,
        expanded = showMenu,
        onDismissRequest = { showMenu = false },
        viewModel = menuViewModel
    )
}

/** How much each level of the tree view is shifted. */
private val TREE_INDENT = 20.dp

/** Shows whether a folder of the tree view is unfolded, or loading. */
@Composable
private fun TreeChevron(isExpanded: Boolean, isLoading: Boolean) {
    Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
        if (isLoading)
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        else
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(if (isExpanded) 90f else 0f)
            )
    }
}

/** A folder of the grid view: its picture, and its name below. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderTile(folder: MediaModel.Folder, iconOnly: Boolean, menuViewModel: MediaCardMenuViewModel, onClick: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var showMenu by remember { mutableStateOf(false) }

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
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
        }
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    showMenu = true
                }
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
        ) {
            if (iconOnly || folder.imageUrl == null)
                folderIcon()
            else
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(folder.imageUrl)
                        .crossfade(true)
                        .diskCacheKey(folder.imageId)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    loading = { folderIcon() },
                    error = { folderIcon() },
                    modifier = Modifier.fillMaxSize()
                )
        }
        Text(
            text = folder.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
        )
    }

    FolderCardMenu(
        folder = folder,
        title = folder.name,
        expanded = showMenu,
        onDismissRequest = { showMenu = false },
        viewModel = menuViewModel
    )
}

@Composable
internal fun FolderViewMode.icon(): ImageVector = ImageVector.vectorResource(
    when (this) {
        FolderViewMode.LIST -> R.drawable.rounded_view_list_24
        FolderViewMode.COMPACT -> R.drawable.rounded_view_compact_24
        FolderViewMode.GRID -> R.drawable.rounded_grid_view_24
        FolderViewMode.TREE -> R.drawable.rounded_account_tree_24
    }
)

@Composable
internal fun FolderViewMode.label(): String = stringResource(
    when (this) {
        FolderViewMode.LIST -> R.string.folder_view_list
        FolderViewMode.COMPACT -> R.string.folder_view_compact
        FolderViewMode.GRID -> R.string.folder_view_grid
        FolderViewMode.TREE -> R.string.folder_view_tree
    }
)
