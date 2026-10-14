package com.craftworks.music.ui.playing

import androidx.annotation.StringRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.StarRating
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.craftworks.music.R
import com.craftworks.music.data.model.LibraryType
import com.craftworks.music.data.model.SongListSort
import com.craftworks.music.data.model.SortOrder
import com.craftworks.music.data.model.getProvider
import com.craftworks.music.data.model.id
import com.craftworks.music.player.ChoraMediaLibraryService
import com.craftworks.music.ui.elements.bounceClick
import com.craftworks.music.ui.elements.dialogs.AddToPlaylist
import com.craftworks.music.ui.elements.dialogs.SortDialog
import com.craftworks.music.utils.StringUtils
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.roundToInt

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedPlayQueueContent(
    mediaController: MediaController?,
    modifier: Modifier = Modifier,
    viewModel: NowPlayingViewModel = viewModel(),
    dismissNowPlaying: () -> Unit
) {
    if (mediaController == null)
        return
    val currentList = remember { mutableStateListOf<QueueItem>() }

    var dragStartIndex by remember { mutableIntStateOf(-1) }
    var dragCurrentIndex by remember { mutableIntStateOf(-1) }

    // Queue order (item ids) when the drag started, used to commit group drags to the player
    var dragOriginalOrder by remember { mutableStateOf<List<String>?>(null) }
    var draggedItemId by remember { mutableStateOf<String?>(null) }
    // Selected items merged into the dragged one while dragging a selection, in queue order
    var groupDragItems by remember { mutableStateOf<List<QueueItem>>(emptyList()) }

    var currentMediaIndex by remember { mutableIntStateOf(-1) }
    var currentMediaItem: QueueItem? by remember { mutableStateOf(null) }

    var selectedMediaItem: QueueItem? by remember { mutableStateOf(null) }
    var selectedMediaIndex by remember { mutableIntStateOf(-1) }

    var selectionMode by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf(setOf<String>()) }
    // Indices in the queue of the selected items, ordered
    val selectedIndices by remember {
        derivedStateOf { currentList.indices.filter { currentList[it].queueItemId in selection } }
    }

    var showClearDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showSelectionSortDialog by remember { mutableStateOf(false) }
    var showSelectByDialog by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var addToPlaylistSongsOverride by remember { mutableStateOf<List<MediaItem>?>(null) }

    DisposableEffect(mediaController) {
        fun syncList() {
            val incomingMediaItems =
                List(mediaController.mediaItemCount) { mediaController.getMediaItemAt(it) }

            val syncedList = incomingMediaItems.map { mediaItem ->
                val matchIndex = currentList.indexOfFirst { it.mediaItem == mediaItem }
                if (matchIndex != -1)
                    currentList.removeAt(matchIndex)
                else
                    QueueItem(mediaItem = mediaItem)
            }

            currentList.clear()
            currentList.addAll(syncedList)
        }

        val listener = object : Player.Listener {
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (mediaController.mediaItemCount == 0) return
                // The selection is merged into the dragged item, the list is synced on release
                if (groupDragItems.isNotEmpty()) return
                syncList()
                currentMediaItem = currentList[mediaController.currentMediaItemIndex]
                currentMediaIndex = mediaController.currentMediaItemIndex
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (groupDragItems.isNotEmpty()) return
                currentMediaItem = currentList[mediaController.currentMediaItemIndex]
                currentMediaIndex = mediaController.currentMediaItemIndex
            }
        }

        // Initial load
        if (mediaController.mediaItemCount == 0) return@DisposableEffect onDispose { }
        syncList()
        currentMediaItem = currentList[mediaController.currentMediaItemIndex]
        currentMediaIndex = mediaController.currentMediaItemIndex
        mediaController.addListener(listener)

        onDispose { mediaController.removeListener(listener) }
    }

    val lazyListState = rememberLazyListState()

    LaunchedEffect(Unit) {
        if (currentList.any { it.queueItemId == currentMediaItem?.queueItemId }) {
            lazyListState.scrollToItem(mediaController.currentMediaItemIndex)
        }
    }

    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        if (dragStartIndex == -1) dragStartIndex = from.index
        currentList.add(to.index, currentList.removeAt(from.index))
        dragCurrentIndex = to.index
    }

    val selectedCount = if (groupDragItems.isNotEmpty()) groupDragItems.size else selectedIndices.size

    fun exitSelection() {
        selectionMode = false
        selection = emptySet()
    }

    fun clearQueue() {
        mediaController.clearMediaItems()
        currentList.clear()
        currentMediaItem = null
        currentMediaIndex = -1
        exitSelection()
        dismissNowPlaying()
    }

    // Moves the selected items next to each other, at [target] among the non selected items
    fun moveSelectionTo(target: Int) {
        moveQueueItems(mediaController, selectedIndices, target)
    }

    fun removeSelection() {
        val indices = selectedIndices
        if (indices.isEmpty()) return
        if (indices.size == mediaController.mediaItemCount) {
            clearQueue()
            return
        }
        // Remove contiguous ranges, starting from the end so the other indices stay valid
        var start = indices.last()
        var end = start + 1
        for (i in indices.size - 2 downTo 0) {
            if (indices[i] == start - 1) {
                start--
            } else {
                mediaController.removeMediaItems(start, end)
                start = indices[i]
                end = start + 1
            }
        }
        mediaController.removeMediaItems(start, end)
        exitSelection()
    }

    // Merges the other selected items into the dragged one
    fun startGroupDrag(draggedId: String) {
        groupDragItems = currentList.filter { it.queueItemId in selection }
        currentList.removeAll { it.queueItemId in selection && it.queueItemId != draggedId }
    }

    // Splits the merged items back around the dragged one and commits the new order to the player
    fun commitGroupDrag(originalOrder: List<String>, draggedId: String) {
        val group = groupDragItems
        groupDragItems = emptyList()

        val draggedPosition = currentList.indexOfFirst { it.queueItemId == draggedId }
        val remaining = currentList.filter { it.queueItemId != draggedId }
        // Every item left in the list before the dragged one is a non selected item
        val target = draggedPosition.coerceAtLeast(0)

        val newList = remaining.take(target) + group + remaining.drop(target)

        // Show the final order right away, the player sync will follow
        currentList.clear()
        currentList.addAll(newList)
        if (newList.map { it.queueItemId } == originalOrder) return

        // The player still has the order from before the drag
        moveQueueItems(
            mediaController,
            group.map { originalOrder.indexOf(it.queueItemId) },
            target
        )
    }

    Column {
        if (selectionMode) {
            QueueSelectionTopBar(
                selectedCount = selectedCount,
                onExitSelection = { exitSelection() },
                onSelectAll = { selection = currentList.map { it.queueItemId }.toSet() },
                onInvertSelection = {
                    selection = currentList.map { it.queueItemId }.filter { it !in selection }.toSet()
                },
                onSelectRange = {
                    if (selectedIndices.isNotEmpty()) {
                        selection = selection + currentList
                            .subList(selectedIndices.first(), selectedIndices.last() + 1)
                            .map { it.queueItemId }
                    }
                },
                onSelectBy = { showSelectByDialog = true },
                onClearSelection = { selection = emptySet() },
                onRemove = { removeSelection() },
                onSort = { showSelectionSortDialog = true },
                onMoveNext = {
                    val currentIndex = mediaController.currentMediaItemIndex
                    moveSelectionTo(
                        currentList.indices.count { it <= currentIndex && currentList[it].queueItemId !in selection }
                    )
                },
                onMoveToStart = { moveSelectionTo(0) },
                onMoveToEnd = { moveSelectionTo(currentList.size - selectedIndices.size) },
                onAddToPlaylist = {
                    addToPlaylistSongsOverride = selectedIndices.map { currentList[it].mediaItem }
                    showAddToPlaylistDialog = true
                },
                isSelectionStarred = {
                    selectedIndices.isNotEmpty() && selectedIndices.all {
                        viewModel.isSongStarred(currentList[it].mediaItem.mediaMetadata)
                    }
                },
                onSetStarred = { starred ->
                    viewModel.setSongsStarred(
                        selectedIndices.map { currentList[it].mediaItem.mediaMetadata },
                        starred
                    )
                }
            )
        } else {
            Row(
                modifier = Modifier.padding(12.dp).height(QueueTopBarHeight),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            )  {
                ChoraMediaLibraryService.getInstance()?.player?.let { player ->
                    PlayPauseButton(player, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(40.dp))
                }

                IconButton(onClick = {
                    showSortDialog = true
                }, modifier = Modifier.size(40.dp).bounceClick() ) {
                    Icon(
                        ImageVector.vectorResource(R.drawable.rounded_sort_24),
                        contentDescription = stringResource(R.string.button_sort_by),
                        modifier = Modifier.size(30.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                append((currentMediaIndex + 1).toString())
                            }
                            append(" / ${mediaController.mediaItemCount}")
                        },
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Row (
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            ImageVector.vectorResource(R.drawable.rounded_timer_24),
                            contentDescription = "DURATION ICON",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = StringUtils.formatSeconds(currentList.sumOf { it.mediaItem.mediaMetadata.durationMs?:0 }.div(1000)),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }

                IconButton(onClick = {
                    showAddToPlaylistDialog = true
                }, modifier = Modifier.size(40.dp).bounceClick()) {
                    Icon(
                        ImageVector.vectorResource(R.drawable.save_24px),
                        contentDescription = stringResource(R.string.action_add_to_playlist),
                        modifier = Modifier.size(30.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = {
                    showClearDialog = true
                }, modifier = Modifier.padding(4.dp).size(40.dp).bounceClick()) {
                    Icon(
                        ImageVector.vectorResource(R.drawable.delete_sweep_24px),
                        contentDescription = stringResource(R.string.action_clear_queue),
                        modifier = Modifier.size(30.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        LazyColumn(
            state = lazyListState,
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            itemsIndexed(currentList, key = { _, item -> item.queueItemId }) { index, item ->
                ReorderableItem(
                    state = reorderableState,
                    key = item.queueItemId,
                    animateItemModifier = Modifier.animateItem(
                        placementSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow)
                    )
                )
                { draggingThis ->
                    val isSelected = item.queueItemId in selection
                    QueueItemContent(
                        index = index,
                        item = item,
                        draggingThis = draggingThis,
                        currentMediaItem = currentMediaItem,
                        mediaController = mediaController,
                        onMenuOpen = {
                            selectedMediaIndex = index
                            selectedMediaItem = item
                        },
                        onDragStarted = {
                            draggedItemId = item.queueItemId
                            dragOriginalOrder = currentList.map { it.queueItemId }
                            if (item.queueItemId in selection && selectedIndices.size > 1)
                                startGroupDrag(item.queueItemId)
                        },
                        onDragStopped = {
                            // The drag handle keeps the callback from when the drag started,
                            // so read the state here instead of using values from the composition
                            val originalOrder = dragOriginalOrder
                            val draggedId = draggedItemId
                            if (groupDragItems.isNotEmpty() && originalOrder != null && draggedId != null) {
                                commitGroupDrag(originalOrder, draggedId)
                            }
                            // Commit to player only if the item actually moved.
                            else if (dragStartIndex != -1 && dragCurrentIndex != -1 &&
                                dragStartIndex != dragCurrentIndex
                            ) {
                                mediaController.moveMediaItem(
                                    dragStartIndex,
                                    dragCurrentIndex
                                )
                            }
                            dragStartIndex = -1
                            dragCurrentIndex = -1
                            dragOriginalOrder = null
                            draggedItemId = null
                        },
                        selectionMode = selectionMode,
                        selected = isSelected,
                        onToggleSelection = {
                            selection = if (isSelected) selection - item.queueItemId
                                else selection + item.queueItemId
                        },
                        dragGroupCount = if (item.queueItemId == draggedItemId) groupDragItems.size else 0,
                        onThumbnailLongClick = {
                            selectionMode = true
                            selection = selection + item.queueItemId
                        }
                    )
                }
            }
        }
    }

    selectedMediaItem?.let {
        QueueItemMenu(
            onDialogDismiss = {selectedMediaItem = null},
            onAddToPlaylist = {
                addToPlaylistSongsOverride = listOf(it.mediaItem)
                showAddToPlaylistDialog = true
            },
            queueItem = it,
            queueIndex = selectedMediaIndex,
            mediaController = mediaController,
            viewModel = viewModel,
            advancedMenu = true
        )
    }
    if (showClearDialog) {
        AlertDialog(
            title = {
                Text(text = stringResource(R.string.action_clear_queue))
            },
            text = {
                Text(text = stringResource(R.string.clear_queue_confirm))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        clearQueue()
                    }
                ) {
                    Text(stringResource(R.string.action_clear_queue))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearDialog = false }
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            onDismissRequest = { showClearDialog = false }
        )
    }

    if (showSortDialog) {
        AdvancedQueueSortDialog(
            onDismissRequest = {
                showSortDialog = false
            },
            onSort = { order, sort -> sortQueue(mediaController, order, sort) }
        )
    }

    if (showSelectionSortDialog) {
        AdvancedQueueSortDialog(
            title = R.string.sort_selection_title,
            onDismissRequest = {
                showSelectionSortDialog = false
            },
            onSort = { order, sort -> sortQueue(mediaController, order, sort, selectedIndices) }
        )
    }

    if (showSelectByDialog) {
        QueueSelectByDialog(
            items = currentList,
            onDismissRequest = { showSelectByDialog = false },
            onSelect = { ids -> selection = selection + ids }
        )
    }

    if (showAddToPlaylistDialog) {
        AddToPlaylist(
            onDismissRequest = {
                showAddToPlaylistDialog = false
                addToPlaylistSongsOverride = null
                               },
            mediaToAddToPlaylist = addToPlaylistSongsOverride ?: currentList.map { it.mediaItem }
        )
    }
}


private fun syncMediaControllerToList(mediaController: MediaController, originList: MutableList<MediaItem>, destinationList: List<MediaItem>) {

    if (originList.size > 100) {
        val currentItem = mediaController.currentMediaItem ?: return
        val currentIndex = mediaController.currentMediaItemIndex

        val newIndex = destinationList.indexOf(currentItem)
            .coerceAtLeast(0)

        mediaController.replaceMediaItems(currentIndex+1, mediaController.mediaItemCount, destinationList.subList(newIndex + 1, destinationList.size))
        mediaController.replaceMediaItems(0, currentIndex, destinationList.subList(0, newIndex))
    } else {
         fun moveItem(item: MediaItem, to: Int) {
            val from = originList.indexOf(item)
            mediaController.moveMediaItem(from, to)
            originList.add(to, originList.removeAt(from))
        }
        destinationList.forEachIndexed { index, item -> moveItem(item, index) }
    }
}

/**
 * Moves the items at [indices] (in that order) next to each other, so the group starts at [target]
 * once the other items are taken into account (0 = start, the number of non moved items = end).
 *
 * Uses at most 2 moves per item, whatever the size of the queue.
 */
private fun moveQueueItems(mediaController: MediaController, indices: List<Int>, target: Int) {
    if (indices.isEmpty()) return
    val size = mediaController.mediaItemCount
    val order = MutableList(size) { it }

    val remaining = order.filter { it !in indices }
    val destination = remaining.take(target) + indices + remaining.drop(target)
    if (destination == order) return

    fun move(from: Int, to: Int) {
        if (from == to) return
        mediaController.moveMediaItem(from, to)
        order.add(to, order.removeAt(from))
    }

    // Gather the items at the end of the queue, keeping their order...
    indices.forEach { move(order.indexOf(it), size - 1) }
    // ...then move them to their destination.
    indices.indices.forEach { move(size - indices.size + it, target + it) }
}

/** Swaps the items at [a] and [b] without touching the items between them. */
private fun swapQueueItems(mediaController: MediaController, a: Int, b: Int) {
    if (a == b) return
    val first = minOf(a, b)
    val second = maxOf(a, b)
    mediaController.moveMediaItem(second, first)
    mediaController.moveMediaItem(first + 1, second)
}

/**
 * Reorders the items in [slots] (sorted queue positions) so that slots[i] holds the item that was
 * at newOrder[i]. Items outside [slots] don't move.
 */
private fun permuteQueueSlots(mediaController: MediaController, slots: List<Int>, newOrder: List<Int>) {
    val order = slots.toMutableList() // Original position of the item currently in each slot
    for (i in slots.indices) {
        val from = order.indexOf(newOrder[i])
        if (from == i) continue
        swapQueueItems(mediaController, slots[i], slots[from])
        order[from] = order[i]
        order[i] = newOrder[i]
    }
}

/**
 * Sorts the queue, or only the items at [indices] if specified. In that case, the sorted items
 * stay in the slots they were occupying.
 */
@Suppress("UNCHECKED_CAST")
private fun sortQueue(mediaController: MediaController, sortOrder: SortOrder, sort: SongListSort, indices: List<Int>? = null) {
    val currentMediaItems = MutableList(mediaController.mediaItemCount) { mediaController.getMediaItemAt(it) }
    val slots = indices?.sorted() ?: currentMediaItems.indices.toList()
    if (slots.size < 2) return
    val items = slots.map { IndexedValue(it, currentMediaItems[it]) }

    val sortBindings = mapOf<SongListSort, (IndexedValue<MediaItem>) -> Comparable<*>?>(
        SongListSort.ALBUM to { it.value.mediaMetadata.albumTitle.toString() },
        SongListSort.ALBUM_ARTIST to { it.value.mediaMetadata.albumArtist.toString() },
        SongListSort.ARTIST to { it.value.mediaMetadata.artist.toString() },
        SongListSort.BPM to { it.value.mediaMetadata.extras?.getInt("bpm") },
        SongListSort.DURATION to { it.value.mediaMetadata.durationMs },
        SongListSort.ID to { it.index },
        SongListSort.NAME to { it.value.mediaMetadata.title.toString() },
        SongListSort.PLAY_COUNT to { it.value.mediaMetadata.extras?.getInt("playCount") },
        SongListSort.RATING to { (it.value.mediaMetadata.userRating as? StarRating)?.starRating?.toInt() },
        SongListSort.TRACK_NUMBER to { it.value.mediaMetadata.trackNumber },
        SongListSort.YEAR to { it.value.mediaMetadata.let { metadata -> metadata.releaseYear ?: metadata.recordingYear } },
    )

    val sortList =
        if (sort == SongListSort.RANDOM) items.shuffled()
        else if ((sortOrder == SortOrder.DESC) or (sort == SongListSort.ID)) items.sortedByDescending(sortBindings[sort] as (IndexedValue<MediaItem>) -> Comparable<Any>?)
        else items.sortedBy(sortBindings[sort] as (IndexedValue<MediaItem>) -> Comparable<Any>?)

    if (indices == null)
        syncMediaControllerToList(mediaController, currentMediaItems, sortList.map { it.value })
    else
        permuteQueueSlots(mediaController, slots, sortList.map { it.index })
}

// Height of the top bars, so the queue doesn't jump when switching to the selection mode
private val QueueTopBarHeight = 48.dp

@Composable
private fun QueueTopBarButton(icon: Int, text: Int, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(40.dp).bounceClick()
    ) {
        Icon(
            ImageVector.vectorResource(icon),
            contentDescription = stringResource(text),
            modifier = Modifier.size(28.dp),
            tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        )
    }
}

@Composable
private fun QueueDropdownItem(icon: Int, text: Int, enabled: Boolean = true, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(text)) },
        onClick = onClick,
        enabled = enabled,
        leadingIcon = {
            Icon(ImageVector.vectorResource(icon), contentDescription = null)
        }
    )
}

@Composable
private fun QueueSelectionTopBar(
    selectedCount: Int,
    onExitSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onSelectRange: () -> Unit,
    onSelectBy: () -> Unit,
    onClearSelection: () -> Unit,
    onRemove: () -> Unit,
    onSort: () -> Unit,
    onMoveNext: () -> Unit,
    onMoveToStart: () -> Unit,
    onMoveToEnd: () -> Unit,
    onAddToPlaylist: () -> Unit,
    isSelectionStarred: () -> Boolean,
    onSetStarred: (Boolean) -> Unit,
) {
    val hasSelection = selectedCount > 0
    var selectionMenuExpanded by remember { mutableStateOf(false) }
    var actionsMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.padding(12.dp).height(QueueTopBarHeight),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        QueueTopBarButton(R.drawable.close_24px, R.string.action_clear_selection, onClick = onExitSelection)

        Text(
            text = stringResource(R.string.queue_selection_count, selectedCount),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 4.dp)
        )

        Box {
            QueueTopBarButton(R.drawable.select_all_24px, R.string.action_selection_options) {
                selectionMenuExpanded = true
            }
            DropdownMenu(
                expanded = selectionMenuExpanded,
                onDismissRequest = { selectionMenuExpanded = false }
            ) {
                QueueDropdownItem(R.drawable.select_all_24px, R.string.action_select_all) {
                    onSelectAll()
                    selectionMenuExpanded = false
                }
                QueueDropdownItem(R.drawable.swap_vert_24px, R.string.action_invert_selection) {
                    onInvertSelection()
                    selectionMenuExpanded = false
                }
                QueueDropdownItem(R.drawable.unfold_more_24px, R.string.action_select_range, enabled = selectedCount >= 2) {
                    onSelectRange()
                    selectionMenuExpanded = false
                }
                QueueDropdownItem(R.drawable.filter_list_24px, R.string.action_select_by) {
                    onSelectBy()
                    selectionMenuExpanded = false
                }
                QueueDropdownItem(R.drawable.check_box_outline_blank_24px, R.string.action_clear_selection, enabled = hasSelection) {
                    onClearSelection()
                    selectionMenuExpanded = false
                }
            }
        }

        Box {
            QueueTopBarButton(R.drawable.more_vert_24px, R.string.action_more, enabled = hasSelection) {
                actionsMenuExpanded = true
            }
            DropdownMenu(
                expanded = actionsMenuExpanded,
                onDismissRequest = { actionsMenuExpanded = false }
            ) {
                QueueDropdownItem(R.drawable.rounded_sort_24, R.string.sort_selection_title, enabled = selectedCount >= 2) {
                    onSort()
                    actionsMenuExpanded = false
                }
                HorizontalDivider()
                QueueDropdownItem(R.drawable.vertical_align_top_24px, R.string.action_move_to_start) {
                    onMoveToStart()
                    actionsMenuExpanded = false
                }
                QueueDropdownItem(R.drawable.play_next_24px, R.string.action_move_next) {
                    onMoveNext()
                    actionsMenuExpanded = false
                }
                QueueDropdownItem(R.drawable.vertical_align_bottom_24px, R.string.action_move_to_end) {
                    onMoveToEnd()
                    actionsMenuExpanded = false
                }
                HorizontalDivider()
                QueueDropdownItem(R.drawable.remove_circle_24px, R.string.action_remove_from_queue) {
                    onRemove()
                    actionsMenuExpanded = false
                }
                HorizontalDivider()
                QueueDropdownItem(R.drawable.rounded_add_24, R.string.action_add_to_playlist) {
                    onAddToPlaylist()
                    actionsMenuExpanded = false
                }
                val starred = isSelectionStarred()
                QueueDropdownItem(
                    if (starred) R.drawable.round_favorite_24 else R.drawable.round_favorite_border_24,
                    if (starred) R.string.action_remove_from_favorites else R.string.action_add_to_favorites
                ) {
                    onSetStarred(!starred)
                    actionsMenuExpanded = false
                }
            }
        }
    }
}

private enum class QueueSelectByField(@StringRes val label: Int, val value: (MediaMetadata) -> String?) {
    ALBUM(R.string.sort_by_album, { it.albumTitle?.toString() }),
    ARTIST(R.string.sort_by_artist, { it.artist?.toString() }),
    ALBUM_ARTIST(R.string.sort_by_album_artist, { it.albumArtist?.toString() }),
    YEAR(R.string.sort_by_year, { (it.releaseYear ?: it.recordingYear)?.toString() }),
}

/** Lists the distinct values of a field in the queue, picking one selects every matching item. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSelectByDialog(
    items: List<QueueItem>,
    onDismissRequest: () -> Unit,
    onSelect: (List<String>) -> Unit
) {
    var field by remember { mutableStateOf(QueueSelectByField.ALBUM) }

    val groups = items
        .groupBy { field.value(it.mediaItem.mediaMetadata)?.takeIf { value -> value.isNotBlank() } }
        .toList()
        .sortedWith(compareBy(nullsLast<String>()) { it.first?.lowercase() })

    AlertDialog(
        title = {
            Text(text = stringResource(R.string.select_by_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QueueSelectByField.entries.forEach { entry ->
                        FilterChip(
                            selected = entry == field,
                            onClick = { field = entry },
                            label = { Text(stringResource(entry.label)) }
                        )
                    }
                }

                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(groups) { (value, groupItems) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable {
                                    onSelect(groupItems.map { it.queueItemId })
                                    onDismissRequest()
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = value ?: stringResource(R.string.select_by_unknown),
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = groupItems.size.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        onDismissRequest = onDismissRequest
    )
}

@Composable
private fun AdvancedQueueSortDialog(
    title: Int = R.string.sort_queue_title,
    onDismissRequest: () -> Unit,
    onSort: (SortOrder, SongListSort) -> Unit
) {
    val sortTranslationBindings = mapOf(
        SongListSort.ALBUM to R.string.sort_by_album,
        SongListSort.ALBUM_ARTIST to R.string.sort_by_album_artist,
        SongListSort.ARTIST to R.string.sort_by_artist,
        SongListSort.BPM to R.string.sort_by_bpm,
        SongListSort.DURATION to R.string.sort_by_duration,
        SongListSort.ID to R.string.sort_queue_reverse, // Hack to reverse the queue
        SongListSort.NAME to R.string.sort_by_name,
        SongListSort.PLAY_COUNT to R.string.sort_by_play_count,
        SongListSort.RANDOM to R.string.sort_by_random,
        SongListSort.RATING to R.string.sort_by_rating,
        SongListSort.TRACK_NUMBER to R.string.sort_by_track_number,
        SongListSort.YEAR to R.string.sort_by_year,
    )

    SortDialog(
        title = title,
        sorts = listOf(
            SongListSort.ID,
            SongListSort.RANDOM,
            SongListSort.ALBUM,
            SongListSort.ALBUM_ARTIST,
            SongListSort.ARTIST,
            SongListSort.BPM,
            SongListSort.DURATION,
            SongListSort.NAME,
            SongListSort.PLAY_COUNT,
            SongListSort.TRACK_NUMBER,
            SongListSort.YEAR,
        ),
        sortLabels = sortTranslationBindings,
        initialSort = SongListSort.ID,
        confirmText = R.string.sort_queue_sort,
        onDismissRequest = onDismissRequest,
        onSort = onSort
    )
}