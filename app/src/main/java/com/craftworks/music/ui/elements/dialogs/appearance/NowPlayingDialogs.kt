package com.craftworks.music.ui.elements.dialogs.appearance

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.craftworks.music.R
import com.craftworks.music.ui.playing.NowPlayingAction
import com.craftworks.music.ui.playing.NowPlayingButton
import com.craftworks.music.ui.playing.NowPlayingButtonSlot
import com.craftworks.music.ui.playing.NowPlayingGesture
import com.craftworks.music.ui.playing.NowPlayingMenuItem
import com.craftworks.music.ui.playing.NowPlayingSeekBarStyle
import com.craftworks.music.ui.playing.SeekBar
import com.craftworks.music.ui.playing.defaultNowPlayingButtons
import com.craftworks.music.ui.playing.defaultNowPlayingCoverGestures
import com.craftworks.music.ui.playing.defaultNowPlayingMenuItems
import com.craftworks.music.ui.playing.nowPlayingActionIcon
import com.craftworks.music.ui.playing.nowPlayingActionLabel
import com.craftworks.music.ui.playing.nowPlayingButtonActions
import com.craftworks.music.ui.playing.nowPlayingButtonSlotLabel
import com.craftworks.music.ui.playing.nowPlayingGestureLabel
import com.craftworks.music.ui.playing.nowPlayingSeekBarStyleLabel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** A row of the buttons editor: a slot header, followed by the buttons of that slot. */
private sealed interface ButtonsEditorEntry {
    val key: String

    data class Header(val slot: NowPlayingButtonSlot) : ButtonsEditorEntry {
        override val key = "header_${slot.name}"
    }

    data class Button(val action: NowPlayingAction) : ButtonsEditorEntry {
        override val key = action.name
    }
}

private fun List<NowPlayingButton>.toEditorEntries(): List<ButtonsEditorEntry> =
    NowPlayingButtonSlot.entries.flatMap { slot ->
        listOf(ButtonsEditorEntry.Header(slot)) +
                filter { it.slot == slot }.distinctBy { it.action }.map { ButtonsEditorEntry.Button(it.action) }
    }

/** The buttons in their slots, or null when the entries aren't valid (a button before the first header, or a slot over its limit). */
private fun List<ButtonsEditorEntry>.toButtons(): List<NowPlayingButton>? {
    var slot: NowPlayingButtonSlot? = null
    val buttons = map { entry ->
        when (entry) {
            is ButtonsEditorEntry.Header -> { slot = entry.slot; null }
            is ButtonsEditorEntry.Button -> NowPlayingButton(entry.action, slot ?: return null)
        }
    }.filterNotNull()
    val full = NowPlayingButtonSlot.entries.any { s -> buttons.count { it.slot == s } > s.maxButtons }
    return if (full) null else buttons
}

/**
 * Editor of the now playing buttons: they are dragged between the slots (rows) and reordered.
 * The play/pause button can't be moved: it is always between the left and right buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingButtonsDialog(
    buttons: List<NowPlayingButton>,
    onSet: (List<NowPlayingButton>) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val entries = remember { mutableStateListOf<ButtonsEditorEntry>().apply { addAll(buttons.toEditorEntries()) } }
    val editedButtons = entries.toButtons() ?: emptyList()

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        // The list contains the preview and the hint before the entries.
        val fromIndex = entries.indexOfFirst { it.key == from.key }
        val toIndex = entries.indexOfFirst { it.key == to.key }
        if (fromIndex < 0 || toIndex < 0) return@rememberReorderableLazyListState

        val moved = entries.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        // Moving past a header moves the button to the next / previous slot, unless it is full.
        if (moved.toButtons() != null) {
            entries.clear()
            entries.addAll(moved)
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(stringResource(R.string.now_playing_buttons)) },
                    navigationIcon = {
                        IconButton(onClick = onDismissRequest) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
                        }
                    },
                    actions = {
                        TextButton(onClick = {
                            entries.clear()
                            entries.addAll(defaultNowPlayingButtons.toEditorEntries())
                        }) {
                            Text(stringResource(R.string.action_reset))
                        }
                        TextButton(onClick = {
                            entries.toButtons()?.let(onSet)
                            onDismissRequest()
                        }) {
                            Text(stringResource(R.string.action_save))
                        }
                    }
                )

                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item(key = "preview") {
                        NowPlayingButtonsPreview(editedButtons)
                    }
                    item(key = "hint") {
                        Text(
                            text = stringResource(R.string.now_playing_buttons_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }

                    items(entries, key = { it.key }) { entry ->
                        // Nothing can go above the first header: the buttons must be in a slot.
                        ReorderableItem(
                            reorderableState,
                            key = entry.key,
                            enabled = entry != entries.firstOrNull()
                        ) { isDragging ->
                            when (entry) {
                                is ButtonsEditorEntry.Header -> {
                                    val count = editedButtons.count { it.slot == entry.slot }
                                    ButtonsEditorHeader(
                                        slot = entry.slot,
                                        count = count,
                                        isEmpty = count == 0,
                                        addableActions = nowPlayingButtonActions.filter { action ->
                                            editedButtons.none { it.action == action }
                                        },
                                        onAdd = { action ->
                                            // Added at the end of the slot: before the next header.
                                            val headerIndex = entries.indexOf(entry)
                                            val nextHeader = entries.withIndex().firstOrNull { (index, it) ->
                                                index > headerIndex && it is ButtonsEditorEntry.Header
                                            }?.index ?: entries.size
                                            entries.add(nextHeader, ButtonsEditorEntry.Button(action))
                                        }
                                    )
                                }

                                is ButtonsEditorEntry.Button -> {
                                    val elevation by animateDpAsState(if (isDragging) 6.dp else 0.dp, label = "Dragged button elevation")
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        shadowElevation = elevation,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(56.dp)
                                                .padding(start = 16.dp)
                                        ) {
                                            Icon(nowPlayingActionIcon(entry.action), contentDescription = null)
                                            Spacer(Modifier.width(16.dp))
                                            Text(
                                                text = nowPlayingActionLabel(entry.action),
                                                style = MaterialTheme.typography.bodyLarge,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(onClick = { entries.remove(entry) }) {
                                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_remove))
                                            }
                                            IconButton(
                                                onClick = {},
                                                modifier = Modifier.draggableHandle()
                                            ) {
                                                Icon(
                                                    ImageVector.vectorResource(R.drawable.baseline_drag_handle_24),
                                                    contentDescription = null
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ButtonsEditorHeader(
    slot: NowPlayingButtonSlot,
    count: Int,
    isEmpty: Boolean,
    addableActions: List<NowPlayingAction>,
    onAdd: (NowPlayingAction) -> Unit,
) {
    var showAddMenu by remember { mutableStateOf(false) }

    Column(Modifier.padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = nowPlayingButtonSlotLabel(slot),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.now_playing_buttons_count, count, slot.maxButtons),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box {
                IconButton(
                    onClick = { showAddMenu = true },
                    enabled = count < slot.maxButtons && addableActions.isNotEmpty()
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add))
                }
                DropdownMenu(
                    expanded = showAddMenu,
                    onDismissRequest = { showAddMenu = false }
                ) {
                    addableActions.forEach { action ->
                        DropdownMenuItem(
                            leadingIcon = { Icon(nowPlayingActionIcon(action), contentDescription = null) },
                            text = { Text(nowPlayingActionLabel(action)) },
                            onClick = {
                                showAddMenu = false
                                onAdd(action)
                            }
                        )
                    }
                }
            }
        }
        if (isEmpty)
            Text(
                text = stringResource(R.string.now_playing_buttons_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )
    }
}

/** A sketch of the now playing screen with the edited buttons. */
@Composable
private fun NowPlayingButtonsPreview(buttons: List<NowPlayingButton>) {
    @Composable
    fun PreviewIcon(action: NowPlayingAction, size: Int = 24) {
        Icon(
            imageVector = nowPlayingActionIcon(action),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(size.dp)
        )
    }

    fun slot(slot: NowPlayingButtonSlot) = buttons.filter { it.slot == slot }.map { it.action }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title, title buttons and menu
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.7f)
                            .height(14.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(0.45f)
                            .height(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                    )
                }
                slot(NowPlayingButtonSlot.TITLE).forEach { PreviewIcon(it) }
                PreviewIcon(NowPlayingAction.MENU)
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            )

            // Controls, play/pause always in the middle
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                    slot(NowPlayingButtonSlot.CONTROLS_START).forEach { PreviewIcon(it, 28) }
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(nowPlayingActionIcon(NowPlayingAction.PLAY_PAUSE), contentDescription = null, modifier = Modifier.size(32.dp))
                    }
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                    slot(NowPlayingButtonSlot.CONTROLS_END).forEach { PreviewIcon(it, 28) }
                }
            }

            val bottom = slot(NowPlayingButtonSlot.BOTTOM)
            if (bottom.isNotEmpty())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    bottom.forEach { PreviewIcon(it) }
                }
        }
    }
}

/** Editor of the details menu: every action can be shown or hidden, and reordered. */
@Composable
fun NowPlayingMenuActionsDialog(
    items: List<NowPlayingMenuItem>,
    onSet: (List<NowPlayingMenuItem>) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val editedItems = remember { mutableStateListOf<NowPlayingMenuItem>().apply { addAll(items) } }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.now_playing_menu_actions)) },
        text = {
            val lazyListState = rememberLazyListState()
            val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
                editedItems.add(to.index, editedItems.removeAt(from.index))
            }

            Column {
                Text(
                    text = stringResource(R.string.now_playing_menu_actions_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                HorizontalDivider()
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                ) {
                    items(editedItems, key = { it.action.name }) { item ->
                        ReorderableItem(reorderableState, key = item.action.name) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            ) {
                                val index = editedItems.indexOf(item)
                                Checkbox(
                                    checked = item.enabled,
                                    onCheckedChange = { checked ->
                                        if (index >= 0) editedItems[index] = item.copy(enabled = checked)
                                    }
                                )
                                Icon(
                                    nowPlayingActionIcon(item.action),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = nowPlayingActionLabel(item.action),
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {},
                                    modifier = Modifier.draggableHandle()
                                ) {
                                    Icon(
                                        ImageVector.vectorResource(R.drawable.baseline_drag_handle_24),
                                        contentDescription = null
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSet(editedItems.toList())
                onDismissRequest()
            }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = {
                editedItems.clear()
                editedItems.addAll(defaultNowPlayingMenuItems)
            }) {
                Text(stringResource(R.string.action_reset))
            }
        }
    )
}

/** Picks the seek bar style, each option showing the bar it draws. */
@Composable
fun NowPlayingSeekBarStyleDialog(
    selection: NowPlayingSeekBarStyle,
    onSet: (NowPlayingSeekBarStyle) -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.now_playing_seek_bar_style)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                NowPlayingSeekBarStyle.entries.forEach { style ->
                    val select = {
                        onSet(style)
                        onDismissRequest()
                    }
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .selectable(
                                selected = style == selection,
                                onClick = select,
                                role = Role.RadioButton
                            )
                            .padding(end = 12.dp, bottom = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = style == selection, onClick = select)
                            Text(
                                text = nowPlayingSeekBarStyleLabel(style),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        // The preview isn't interactive: a click on it selects the style.
                        Box(Modifier.padding(start = 12.dp)) {
                            SeekBar(
                                value = 0.4f,
                                valueRange = 0f..1f,
                                style = style,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = select
                                    )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )
}

/** Sets the action of each cover gesture. Changes are saved right away. */
@Composable
fun NowPlayingCoverGesturesDialog(
    gestures: Map<NowPlayingGesture, NowPlayingAction>,
    onSet: (Map<NowPlayingGesture, NowPlayingAction>) -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.now_playing_cover_gestures)) },
        text = {
            Column {
                NowPlayingGesture.entries.forEach { gesture ->
                    var showMenu by remember { mutableStateOf(false) }
                    val action = gestures[gesture]

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showMenu = true }
                            .padding(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = nowPlayingGestureLabel(gesture),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Box {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (action != null) {
                                    Icon(
                                        nowPlayingActionIcon(action),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(
                                    text = action?.let { nowPlayingActionLabel(it) }
                                        ?: stringResource(R.string.cover_gesture_none),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (action != null) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.width(140.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.cover_gesture_none)) },
                                    onClick = {
                                        showMenu = false
                                        onSet(gestures - gesture)
                                    }
                                )
                                NowPlayingAction.entries.forEach { option ->
                                    DropdownMenuItem(
                                        leadingIcon = { Icon(nowPlayingActionIcon(option), contentDescription = null) },
                                        text = { Text(nowPlayingActionLabel(option)) },
                                        onClick = {
                                            showMenu = false
                                            onSet(gestures + (gesture to option))
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismissRequest) {
                Text(stringResource(R.string.action_done))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = { onSet(defaultNowPlayingCoverGestures) }) {
                Text(stringResource(R.string.action_reset))
            }
        }
    )
}
