package com.craftworks.music.ui.elements.dialogs.appearance

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.craftworks.music.R
import com.craftworks.music.managers.MediaProviderManager
import com.craftworks.music.ui.elements.ActionButton
import com.craftworks.music.ui.elements.ActionButtonType
import com.craftworks.music.ui.elements.getActionButtonIconText
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class, ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SongListActionButtonsDialog(
    title: String = "",
    actionButtons: List<ActionButton> = emptyList(),
    supportedButtonTypes: List<ActionButtonType> = emptyList(),
    onSet: (buttons: List<ActionButton>) -> Unit = { },
    onDismissRequest: () -> Unit = { },
) {
    val currentProvider by MediaProviderManager.currentProvider.collectAsStateWithLifecycle()
    val providerFeatures = currentProvider?.featureFlags ?: return

    val buttons = remember {
        mutableStateListOf<Pair<String, ActionButton>>().apply {
            addAll(actionButtons.filter{it.isCompatible(providerFeatures)}.map { Uuid.generateV4().toString() to it.copy() })
        }
    }

    var draggedButtonId by remember { mutableStateOf<String?>(null) }
    var pointerInRoot by remember { mutableStateOf(Offset.Zero) }
    var boxOriginInRoot by remember { mutableStateOf(Offset.Zero) }

    val chipBounds = remember { mutableStateMapOf<String, Rect>() }
    var rowZoneBounds by remember { mutableStateOf(Rect.Zero) }
    var menuZoneBounds by remember { mutableStateOf(Rect.Zero) }
    var trashZoneBounds by remember { mutableStateOf(Rect.Zero) }

    var floatingSize by remember { mutableStateOf(IntSize.Zero) }

    // Calculates live placement and updates model state
    fun updateLiveDragPosition() {
        val activeId = draggedButtonId ?: return

        // Pause list reordering while hovering over the trash zone
        if (trashZoneBounds.contains(pointerInRoot)) return

        val activeIndex = buttons.indexOfFirst { it.first == activeId }
        if (activeIndex == -1) return

        val activePair = buttons[activeIndex]

        // 1. Determine target zone from pointer position
        val targetInMenu = when {
            menuZoneBounds.contains(pointerInRoot) -> true
            rowZoneBounds.contains(pointerInRoot) -> false
            else -> activePair.second.inMenu
        }

        // 2. Filter target section items (excluding dragged item)
        val targetSectionButtons = buttons.filter { it.first != activeId && it.second.inMenu == targetInMenu }

        // 3. Calculate target relative index in section
        val targetRelativeIndex = if (targetSectionButtons.isEmpty()) {
            0
        } else if (targetInMenu) {
            // Vertical check (LazyColumn)
            var idx = targetSectionButtons.size
            for (i in targetSectionButtons.indices) {
                val rect = chipBounds[targetSectionButtons[i].first]
                if (rect != null && pointerInRoot.y < rect.center.y) {
                    idx = i
                    break
                }
            }
            idx
        } else {
            // Horizontal check (LazyRow)
            var idx = targetSectionButtons.size
            for (i in targetSectionButtons.indices) {
                val rect = chipBounds[targetSectionButtons[i].first]
                if (rect != null && pointerInRoot.x < rect.center.x) {
                    idx = i
                    break
                }
            }
            idx
        }

        // 4. Construct updated partitioned lists
        val remainingRow = buttons.filter { it.first != activeId && !it.second.inMenu }
        val remainingMenu = buttons.filter { it.first != activeId && it.second.inMenu }

        val updatedButton = activePair.second.copy(inMenu = targetInMenu)
        val updatedPair = activePair.first to updatedButton

        val newRowList = remainingRow.toMutableList()
        val newMenuList = remainingMenu.toMutableList()

        if (targetInMenu) {
            newMenuList.add(targetRelativeIndex.coerceIn(0, newMenuList.size), updatedPair)
        } else {
            newRowList.add(targetRelativeIndex.coerceIn(0, newRowList.size), updatedPair)
        }

        val newList = newRowList + newMenuList

        // 5. Update state only if order or properties changed
        if (buttons != newList) {
            buttons.clear()
            buttons.addAll(newList)
        }
    }

    var showAddButtonMenu by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onDismissRequest) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
                        }
                    },
                    actions = {
                        TextButton(onClick = {
                            onSet(buttons.map { it.second })
                            onDismissRequest()
                        }) {
                            Text(stringResource(R.string.action_save))
                        }

                        // Dynamically swap between Add Menu and Trash Target during drag
                        AnimatedContent(
                            targetState = draggedButtonId != null,
                            label = "ActionIconTransition"
                        ) { isDragging ->
                            if (isDragging) {
                                val isHoveredOverTrash = trashZoneBounds.contains(pointerInRoot)
                                val trashScale by animateFloatAsState(
                                    targetValue = if (isHoveredOverTrash) 1.25f else 1.0f,
                                    label = "trashScale"
                                )
                                val trashColor by animateColorAsState(
                                    targetValue = if (isHoveredOverTrash) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    label = "trashColor"
                                )

                                Box(
                                    modifier = Modifier
                                        .onGloballyPositioned { trashZoneBounds = it.boundsInRoot() }
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.action_remove),
                                        tint = trashColor,
                                        modifier = Modifier
                                            .size(28.dp)
                                            .scale(trashScale)
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier.onGloballyPositioned {
                                        trashZoneBounds = it.boundsInRoot()
                                    }
                                ) {
                                    IconButton(onClick = { showAddButtonMenu = true }) {
                                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add))
                                    }
                                    DropdownMenu(
                                        expanded = showAddButtonMenu,
                                        onDismissRequest = { showAddButtonMenu = false }
                                    ) {
                                        supportedButtonTypes.forEach { type ->
                                            val (icon, text) = getActionButtonIconText(type)
                                            DropdownMenuItem(
                                                leadingIcon = { Icon(icon, contentDescription = text) },
                                                text = { Text(text) },
                                                onClick = {
                                                    showAddButtonMenu = false
                                                    val newButton = ActionButton(type = type, inMenu = true)
                                                    buttons.add(Uuid.generateV4().toString() to newButton)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                )

                // Root Container handling touch pointer stream persistently
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .onGloballyPositioned { boxOriginInRoot = it.boundsInRoot().topLeft }
                        .padding(16.dp)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val pressInRoot = down.position + boxOriginInRoot

                                val hitId = chipBounds.entries.firstOrNull { (_, rect) ->
                                    rect.contains(pressInRoot)
                                }?.key

                                if (hitId != null) {
                                    draggedButtonId = hitId
                                    pointerInRoot = pressInRoot
                                    updateLiveDragPosition()
                                    down.consume()

                                    drag(down.id) { change ->
                                        change.consume()
                                        pointerInRoot = change.position + boxOriginInRoot
                                        updateLiveDragPosition()
                                    }

                                    // Remove button if released over trash target
                                    if (trashZoneBounds.contains(pointerInRoot)) {
                                        buttons.removeAll { it.first == hitId }
                                    }

                                    draggedButtonId = null
                                }
                            }
                        }
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text("Visible row", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp)
                                .onGloballyPositioned { rowZoneBounds = it.boundsInRoot() },
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                // More button
                                FilledTonalIconButton(
                                    onClick = {},
                                    modifier = Modifier.height(64.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.MoreVert,
                                        contentDescription = "More" // TODO : Translate
                                    )
                                }

                                // Action buttons
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    items(
                                        items = buttons.filter { !it.second.inMenu },
                                        key = { it.first }
                                    ) { (id, button) ->
                                        DraggableChip(
                                            modifier = Modifier.animateItem(),
                                            button = button,
                                            isBeingDragged = draggedButtonId == id,
                                            onBoundsChanged = { chipBounds[id] = it }
                                        )
                                    }
                                }

                                // Play button
                                var isCompact by remember { mutableStateOf(false) }
                                val density = LocalDensity.current
                                val compactThresholdPx = remember(density) { with(density) { 128.dp.roundToPx() } }

                                Button(
                                    onClick = {},
                                    modifier = Modifier
                                        .height(64.dp)
                                        .weight(0.5f)
                                        .onSizeChanged { size -> isCompact = size.width < compactThresholdPx },
                                    shape = RoundedCornerShape(32.dp),
                                    contentPadding = if (isCompact) PaddingValues(0.dp) else ButtonDefaults.ContentPadding
                                ) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.action_play))
                                    if(!isCompact) {
                                        Row (verticalAlignment = Alignment.CenterVertically) {
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(R.string.action_play))
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(24.dp))
                        Text("Overflow menu", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .heightIn(min = 72.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .onGloballyPositioned { menuZoneBounds = it.boundsInRoot() }
                                .padding(8.dp)
                        ) {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                items(
                                    items = buttons.filter { it.second.inMenu },
                                    key = { it.first }
                                ) { (id, button) ->
                                    DraggableChip(
                                        modifier = Modifier.animateItem(),
                                        button = button,
                                        isBeingDragged = draggedButtonId == id,
                                        onBoundsChanged = { chipBounds[id] = it }
                                    )
                                }
                            }
                        }
                    }

                    draggedButtonId?.let { activeId ->
                        val activePair = buttons.firstOrNull { it.first == activeId }
                        if (activePair != null) {
                            val localOffset = pointerInRoot - boxOriginInRoot
                            val cornerRadius by animateDpAsState(
                                targetValue = if (activePair.second.inMenu) 12.dp else 32.dp,
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                label = "cornerRadius"
                            )
                            Box(
                                modifier = Modifier
                                    .zIndex(10f)
                                    .onGloballyPositioned { floatingSize = it.size }
                                    .graphicsLayer {
                                        translationX = localOffset.x - (floatingSize.width / 2f)
                                        translationY = localOffset.y - (floatingSize.height / 2f)
                                        alpha = 0.95f
                                        shadowElevation = 12.dp.toPx()
                                        scaleX = 1.05f
                                        scaleY = 1.05f
                                        shape = RoundedCornerShape(cornerRadius)
                                    }
                            ) {
                                ActionButtonChip(
                                    button = activePair.second
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DraggableChip(
    button: ActionButton,
    isBeingDragged: Boolean,
    onBoundsChanged: (Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .onGloballyPositioned { onBoundsChanged(it.boundsInRoot()) }
            .alpha(if (isBeingDragged) 0f else 1f)
    ) {
        ActionButtonChip(button = button)
    }
}

@Composable
private fun ActionButtonChip(
    button: ActionButton
) {
    val (icon, text) = getActionButtonIconText(button.type)
    val inMenu = button.inMenu
    val tonalColors = IconButtonDefaults.filledTonalIconButtonColors()

    val expansionProgress by animateFloatAsState(
        targetValue = if (inMenu) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "expansionProgress"
    )

    val containerColor by animateColorAsState(
        targetValue = if (inMenu) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            tonalColors.containerColor
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "containerColor"
    )

    val contentColor by animateColorAsState(
        targetValue = if (inMenu) {
            MaterialTheme.colorScheme.onSurface
        } else {
            tonalColors.contentColor
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "contentColor"
    )

    val cornerRadius by animateDpAsState(
        targetValue = if (inMenu) 12.dp else 32.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "cornerRadius"
    )

    Surface(
        modifier = Modifier.layout { measurable, constraints ->
            val minSizePx = 64.dp.roundToPx()
            val targetHeightPx = 48.dp.roundToPx()
            val maxWidthPx = if (constraints.hasBoundedWidth) constraints.maxWidth else minSizePx

            // Synchronized size interpolation
            val currentWidth = (minSizePx + (maxWidthPx - minSizePx) * expansionProgress).toInt()
            val currentHeight = (minSizePx + (targetHeightPx - minSizePx) * expansionProgress).toInt()

            val placeable = measurable.measure(
                Constraints.fixed(currentWidth, currentHeight)
            )

            layout(currentWidth, currentHeight) {
                placeable.placeRelative(0, 0)
            }
        },
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = 20.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null
            )

            AnimatedVisibility(
                visible = inMenu,
                enter = fadeIn(animationSpec = tween(200, delayMillis = 100)),
                exit = fadeOut(animationSpec = tween(100))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}