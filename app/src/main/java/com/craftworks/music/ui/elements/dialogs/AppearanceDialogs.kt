package com.craftworks.music.ui.elements.dialogs

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
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
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.craftworks.music.R
import com.craftworks.music.data.BottomNavItem
import com.craftworks.music.data.model.ProviderFeature
import com.craftworks.music.data.model.Screen
import com.craftworks.music.managers.MediaProviderManager
import com.craftworks.music.managers.settings.AppTheme
import com.craftworks.music.managers.settings.AppearanceSettingsManager
import com.craftworks.music.ui.elements.ActionButton
import com.craftworks.music.ui.elements.ActionButtonType
import com.craftworks.music.ui.elements.bounceClick
import com.craftworks.music.ui.elements.getActionButtonIconText
import com.craftworks.music.ui.playing.NowPlayingAlignment
import com.craftworks.music.ui.playing.NowPlayingBackground
import com.craftworks.music.ui.screens.HomeItem
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

//region PREVIEWS
@Preview(showBackground = true)
@Composable
fun PreviewBackgroundDialog(){
    BackgroundDialog(setShowDialog = { })
}

@Preview(showBackground = true)
@Composable
fun PreviewNavbarItemsDialog(){
    NavbarItemsDialog(setShowDialog = { })
}
@Preview(showBackground = true)
@Composable
fun PreviewHomeItemsDialog(){
    HomeItemsDialog(setShowDialog = { })
}

@Preview(showBackground = true)
@Composable
fun PreviewThemeDialog(){
    ThemeDialog(setShowDialog = { })
}
//endregion

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Preview
@Composable
fun NameDialog(setShowDialog: (Boolean) -> Unit = {} ) {
    val context = LocalContext.current
    val username by AppearanceSettingsManager(context).usernameFlow.collectAsState("Username")
    var usernameTextField by remember(username) { mutableStateOf(username) }

    AlertDialog(
        onDismissRequest = { setShowDialog(false) },
        title = { Text(stringResource(R.string.appearance_username)) },
        text = {
            OutlinedTextField(
                value = usernameTextField,
                onValueChange = {
                    usernameTextField = it
                },
                label = { stringResource(R.string.appearance_username) },
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = {
                runBlocking {
                    AppearanceSettingsManager(context).setUsername(usernameTextField)
                    setShowDialog(false)
                }
            }) {
                Text(stringResource(R.string.action_done))
            }
        }
    )
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun BackgroundDialog(setShowDialog: (Boolean) -> Unit) {
    val context = LocalContext.current

    val backgroundType by AppearanceSettingsManager(context).npBackgroundFlow.collectAsState(NowPlayingBackground.ANIMATED_BLUR)

    val backgroundTypeLabels = mapOf(
        NowPlayingBackground.PLAIN to R.string.background_style_plain,
        NowPlayingBackground.STATIC_BLUR to R.string.background_style_blur,
        NowPlayingBackground.ANIMATED_BLUR to R.string.background_style_anim
    )

    AlertDialog(
        onDismissRequest = { setShowDialog(false) },
        title = { Text(stringResource(R.string.appearance_background_style)) },
        text = {
            Column{
                NowPlayingBackground.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .selectable(
                                selected = (option == backgroundType),
                                onClick = {
                                    runBlocking {
                                        AppearanceSettingsManager(context).setBackgroundType(option)
                                    }
                                    setShowDialog(false)
                                },
                                role = Role.RadioButton,
                                enabled = !(option == NowPlayingBackground.ANIMATED_BLUR && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option == backgroundType,
                            onClick = {
                                runBlocking {
                                    AppearanceSettingsManager(context).setBackgroundType(option)
                                }
                                setShowDialog(false)
                            },
                            modifier = Modifier.bounceClick(),
                            enabled = !(option == NowPlayingBackground.ANIMATED_BLUR && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
                        )
                        Text(
                            text = stringResource(id = backgroundTypeLabels[option] ?: androidx.media3.session.R.string.error_message_invalid_state) +
                                    if (option == NowPlayingBackground.ANIMATED_BLUR && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
                                        " (Android 13+)"
                                    else "",
                            fontWeight = FontWeight.Normal,
                            fontSize = MaterialTheme.typography.titleMedium.fontSize,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = { }
    )
}


@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun ThemeDialog(setShowDialog: (Boolean) -> Unit) {
    val context = LocalContext.current

    val selectedTheme by AppearanceSettingsManager(context).appTheme.collectAsState(
        AppTheme.SYSTEM.name)

    val themes = listOf(
       AppTheme.DARK,
       AppTheme.LIGHT,
       AppTheme.SYSTEM
    )

    val themeStrings = listOf(
        R.string.theme_dark, R.string.theme_light, R.string.theme_system
    )

    AlertDialog(
        onDismissRequest = { setShowDialog(false) },
        title = { Text(stringResource(R.string.appearance_theme)) },
        text = {
            Column{
                for ((index, option) in themes.withIndex()) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .selectable(
                                selected = (option.name == selectedTheme),
                                onClick = {
                                    runBlocking {
                                        AppearanceSettingsManager(context).setAppTheme(option)
                                        val uiModeManager =
                                            context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager

                                        when (option) {
                                            AppTheme.DARK -> {
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                                                    uiModeManager.setApplicationNightMode(
                                                        UiModeManager.MODE_NIGHT_YES
                                                    )
                                                else
                                                    AppCompatDelegate.setDefaultNightMode(
                                                        AppCompatDelegate.MODE_NIGHT_YES
                                                    )
                                            }

                                            AppTheme.LIGHT -> {
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                                                    uiModeManager.setApplicationNightMode(
                                                        UiModeManager.MODE_NIGHT_NO
                                                    )
                                                else
                                                    AppCompatDelegate.setDefaultNightMode(
                                                        AppCompatDelegate.MODE_NIGHT_NO
                                                    )
                                            }

                                            AppTheme.SYSTEM -> {
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                                                    uiModeManager.setApplicationNightMode(
                                                        UiModeManager.MODE_NIGHT_AUTO
                                                    )
                                                else
                                                    AppCompatDelegate.setDefaultNightMode(
                                                        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                                                    )
                                            }
                                        }
                                    }
                                    setShowDialog(false)
                                },
                                role = Role.RadioButton
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option.name == selectedTheme,
                            onClick = {
                                runBlocking {
                                    AppearanceSettingsManager(context).setAppTheme(option)
                                    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager

                                    when (option) {
                                       AppTheme.DARK -> {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                                                uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_YES)
                                            else
                                                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                                        }
                                       AppTheme.LIGHT -> {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                                                uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_NO)
                                            else
                                                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                                        }
                                       AppTheme.SYSTEM -> {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                                                uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_AUTO)
                                            else
                                                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                                        }
                                    }
                                }
                                setShowDialog(false)
                            },
                            modifier = Modifier.bounceClick()
                        )
                        Text(
                            text = stringResource(id = themeStrings[index]),
                            fontWeight = FontWeight.Normal,
                            fontSize = MaterialTheme.typography.titleMedium.fontSize,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = { }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NavbarItemsDialog(setShowDialog: (Boolean) -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val bottomNavigationItems =
        (AppearanceSettingsManager(context).bottomNavItemsFlow.collectAsState(null).value ?: emptyList()).toMutableList()

    AlertDialog(
        onDismissRequest = { setShowDialog(false) },
        title = { Text(stringResource(R.string.appearance_navbar_items)) },
        text = {
            val lazyListState = rememberLazyListState()
            val reorderableLazyColumnState =
                rememberReorderableLazyListState(lazyListState) { from, to ->
                    AppearanceSettingsManager(context).setBottomNavItems(bottomNavigationItems.toMutableList()
                        .apply {
                            add(to.index, removeAt(from.index))
                        })
                }

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                state = lazyListState
            ) {
                items(bottomNavigationItems, key = { it.title }) { navItem ->
                    ReorderableItem(reorderableLazyColumnState, navItem.title) {
                        val interactionSource = remember { MutableInteractionSource() }
                        val index = bottomNavigationItems.indexOf(navItem)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .height(48.dp)
                        ) {
                            Checkbox(
                                enabled = navItem.title != "Home",
                                checked = bottomNavigationItems[index].enabled,
                                onCheckedChange = {
                                    coroutineScope.launch {
                                        bottomNavigationItems[index] = bottomNavigationItems[index].copy(enabled = it)
                                        AppearanceSettingsManager(context).setBottomNavItems(bottomNavigationItems)
                                    }
                                },
                                modifier = Modifier
                                    .semantics { contentDescription = navItem.title }
                                    .bounceClick()
                            )
                            Text(
                                text = navItem.title,
                                fontWeight = FontWeight.Normal,
                                fontSize = MaterialTheme.typography.titleMedium.fontSize,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                modifier = Modifier.draggableHandle(
                                    onDragStarted = {
                                    },
                                    onDragStopped = {
                                    },
                                    interactionSource = interactionSource,
                                ),
                                onClick = {},
                            ) {
                                Icon(
                                    ImageVector.vectorResource(R.drawable.baseline_drag_handle_24),
                                    contentDescription = "Reorder"
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                setShowDialog(false)
            }) {
                Text(stringResource(R.string.action_done))
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    coroutineScope.launch {
                        AppearanceSettingsManager(context).setBottomNavItems(
                            //region Default Values
                            mutableStateListOf(
                                BottomNavItem(
                                    "Home", R.drawable.rounded_home_24, Screen.Home
                                ), BottomNavItem(
                                    "Albums",
                                    R.drawable.rounded_library_music_24,
                                    Screen.Albums
                                ), BottomNavItem(
                                    "Songs",
                                    R.drawable.round_music_note_24,
                                    Screen.Songs
                                ), BottomNavItem(
                                    "Artists",
                                    R.drawable.rounded_artist_24,
                                    Screen.Artists
                                ), BottomNavItem(
                                    "Radios", R.drawable.rounded_radio, Screen.Radios
                                ), BottomNavItem(
                                    "Playlists",
                                    R.drawable.placeholder,
                                    Screen.Playlists
                                )
                            ) //endregion
                        )
                        setShowDialog(false)
                    }
                }
            ) {
                Text(stringResource(R.string.action_reset))
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeItemsDialog(setShowDialog: (Boolean) -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val homeItems =
        (AppearanceSettingsManager(context).homeItemsItemsFlow.collectAsState(null).value ?: emptyList()).toMutableList()

    AlertDialog(
        onDismissRequest = { setShowDialog(false) },
        title = { Text(stringResource(R.string.appearance_home_items)) },
        text = {
            val lazyListState = rememberLazyListState()
            val reorderableLazyColumnState =
                rememberReorderableLazyListState(lazyListState) { from, to ->
                    AppearanceSettingsManager(context).setHomeItems(homeItems.toMutableList()
                        .apply {
                            add(to.index, removeAt(from.index))
                        })
                }

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                state = lazyListState
            ) {
                items(homeItems, key = { it.key }) { item ->
                    ReorderableItem(reorderableLazyColumnState, item.key) {
                        val interactionSource = remember { MutableInteractionSource() }
                        val index = homeItems.indexOf(item)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .height(48.dp)
                        ) {
                            Checkbox(
                                checked = homeItems[index].enabled,
                                onCheckedChange = {
                                    coroutineScope.launch {
                                        homeItems[index] = homeItems[index].copy(enabled = it)
                                        AppearanceSettingsManager(context).setHomeItems(homeItems)
                                    }
                                },
                                modifier = Modifier
                                    .bounceClick()
                            )
                            val titleMap = remember {
                                mapOf(
                                    "recently_played" to R.string.home_recently_played,
                                    "recently_added" to R.string.home_recently_added,
                                    "most_played" to R.string.home_most_played,
                                    "random_songs" to R.string.home_explore_library
                                )
                            }
                            Text(
                                text = stringResource(titleMap[item.key] ?: androidx.media3.session.R.string.error_message_fallback),
                                fontWeight = FontWeight.Normal,
                                fontSize = MaterialTheme.typography.titleMedium.fontSize,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                modifier = Modifier.draggableHandle(
                                    onDragStarted = {
                                    },
                                    onDragStopped = {
                                    },
                                    interactionSource = interactionSource,
                                ),
                                onClick = {},
                            ) {
                                Icon(
                                    ImageVector.vectorResource(R.drawable.baseline_drag_handle_24),
                                    contentDescription = "Reorder"
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                setShowDialog(false)
            }) {
                Text(stringResource(R.string.action_done))
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    coroutineScope.launch {
                        AppearanceSettingsManager(context).setHomeItems(
                            //region Default Values
                            mutableStateListOf(
                                HomeItem(
                                    "recently_played",
                                    true
                                ),
                                HomeItem(
                                    "recently_added",
                                    true
                                ),
                                HomeItem(
                                    "most_played",
                                    true
                                ),
                                HomeItem(
                                    "random_songs",
                                    true
                                )
                            ) //endregion
                        )
                        setShowDialog(false)
                    }
                }
            ) {
                Text(stringResource(R.string.action_reset))
            }
        }
    )
}

@Composable
@Preview
fun NowPlayingTitleAlignmentDialog(
    setShowDialog: (Boolean) -> Unit = { },
    title: String = "",
    selection: NowPlayingAlignment = NowPlayingAlignment.LEFT,
    onSet: (NowPlayingAlignment) -> Unit = { }
) {
    val nowPlayingTitleAlignment by remember { mutableStateOf(selection) }

    AlertDialog(
        onDismissRequest = { setShowDialog(false) },
        title = { Text(title) },
        text = {
            Column {
                NowPlayingAlignment.entries.forEach { alignment ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .selectable(
                                selected = (alignment == nowPlayingTitleAlignment),
                                onClick = {
                                    onSet(alignment)
                                    setShowDialog(false)
                                },
                                role = Role.RadioButton
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = alignment == nowPlayingTitleAlignment,
                            onClick = {
                                onSet(alignment)
                            },
                            modifier = Modifier.bounceClick()
                        )
                        val alignmentStringRes = when (alignment) {
                            NowPlayingAlignment.LEFT -> R.string.alignment_setting_left
                            NowPlayingAlignment.CENTER -> R.string.alignment_setting_center
                            NowPlayingAlignment.RIGHT -> R.string.alignment_setting_right
                        }

                        Text(
                            text = stringResource(id = alignmentStringRes),
                            fontWeight = FontWeight.Normal,
                            fontSize = MaterialTheme.typography.titleMedium.fontSize,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = { }
    )
}
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