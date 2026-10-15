package com.craftworks.music.ui.screens.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.craftworks.music.R
import com.craftworks.music.managers.settings.AppearanceSettingsManager
import com.craftworks.music.ui.elements.ActionTooltip
import com.craftworks.music.ui.elements.dialogs.appearance.BackgroundDialog
import com.craftworks.music.ui.elements.dialogs.appearance.NowPlayingButtonsDialog
import com.craftworks.music.ui.elements.dialogs.appearance.NowPlayingCoverGesturesDialog
import com.craftworks.music.ui.elements.dialogs.appearance.NowPlayingMenuActionsDialog
import com.craftworks.music.ui.elements.dialogs.appearance.NowPlayingSeekBarStyleDialog
import com.craftworks.music.ui.elements.dialogs.dialogFocusable
import com.craftworks.music.ui.playing.NowPlayingAlignment
import com.craftworks.music.ui.playing.NowPlayingBackground
import com.craftworks.music.ui.playing.NowPlayingButtonSlot
import com.craftworks.music.ui.playing.NowPlayingGesture
import com.craftworks.music.ui.playing.NowPlayingSeekBarStyle
import com.craftworks.music.ui.playing.defaultNowPlayingButtons
import com.craftworks.music.ui.playing.defaultNowPlayingMenuItems
import com.craftworks.music.ui.playing.nowPlayingActionLabel
import com.craftworks.music.ui.playing.nowPlayingButtonSlotLabel
import com.craftworks.music.ui.playing.nowPlayingAlignmentIcon
import com.craftworks.music.ui.playing.nowPlayingAlignmentLabel
import com.craftworks.music.ui.playing.nowPlayingGestureLabel
import com.craftworks.music.ui.playing.nowPlayingSeekBarStyleLabel
import kotlinx.coroutines.launch

/** The now playing screen settings, a sub-screen of the appearance settings. */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
@Preview(showBackground = true)
fun S_NowPlayingScreen(navHostController: NavHostController = rememberNavController()) {
    var showBackgroundDialog by remember { mutableStateOf(false) }
    var showSeekBarStyleDialog by remember { mutableStateOf(false) }
    var showButtonsDialog by remember { mutableStateOf(false) }
    var showMenuActionsDialog by remember { mutableStateOf(false) }
    var showCoverGesturesDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val appearanceSettingsManager = remember { AppearanceSettingsManager(context) }

    val backgroundType by appearanceSettingsManager.npBackgroundFlow.collectAsStateWithLifecycle(NowPlayingBackground.STATIC_BLUR)
    val titleAlignment by appearanceSettingsManager.nowPlayingTitleAlignment.collectAsStateWithLifecycle(NowPlayingAlignment.LEFT)
    val showMoreInfo by appearanceSettingsManager.showMoreInfoFlow.collectAsStateWithLifecycle(true)
    val seekBarStyle by appearanceSettingsManager.nowPlayingSeekBarStyle.collectAsStateWithLifecycle(NowPlayingSeekBarStyle.STANDARD)
    val buttons by appearanceSettingsManager.nowPlayingButtons.collectAsStateWithLifecycle(defaultNowPlayingButtons)
    val menuItems by appearanceSettingsManager.nowPlayingMenuItems.collectAsStateWithLifecycle(defaultNowPlayingMenuItems)
    val coverGestures by appearanceSettingsManager.nowPlayingCoverGestures.collectAsStateWithLifecycle(emptyMap())
    val disableScreenStandby by appearanceSettingsManager.disableScreenStandby.collectAsStateWithLifecycle(false)

    val backgroundTypeLabels = mapOf(
        NowPlayingBackground.PLAIN to R.string.background_style_plain,
        NowPlayingBackground.STATIC_BLUR to R.string.background_style_blur,
        NowPlayingBackground.ANIMATED_BLUR to R.string.background_style_anim,
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.appearance_now_playing)) },
                navigationIcon = {
                    ActionTooltip(stringResource(R.string.action_back)) {
                        IconButton(
                            onClick = {
                                navHostController.popBackStack()
                            },
                            modifier = Modifier.size(56.dp, 70.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                tint = MaterialTheme.colorScheme.onBackground,
                                contentDescription = "Back To Appearance",
                                modifier = Modifier
                                    .size(24.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(
                    top = innerPadding.calculateTopPadding()
                )
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .dialogFocusable()
        ) {
            Column(
                Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Look
                Column(
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    SettingsDialogButton(
                        stringResource(R.string.now_playing_background_style),
                        stringResource(
                            backgroundTypeLabels[backgroundType]
                                ?: androidx.media3.session.R.string.error_message_invalid_state
                        ),
                        ImageVector.vectorResource(R.drawable.s_a_background),
                        settingsDescription = stringResource(R.string.now_playing_background_style_description),
                        toggleEvent = { showBackgroundDialog = true }
                    )

                    SettingsIconChoice(
                        settingsName = stringResource(R.string.now_playing_title_alignment),
                        settingsIcon = ImageVector.vectorResource(R.drawable.rounded_sort_24),
                        options = NowPlayingAlignment.entries,
                        selected = titleAlignment,
                        optionIcon = { nowPlayingAlignmentIcon(it) },
                        optionLabel = { nowPlayingAlignmentLabel(it) },
                        onSelect = {
                            coroutineScope.launch { appearanceSettingsManager.setNowPlayingTitleAlignment(it) }
                        }
                    )

                    SettingsSwitch(
                        showMoreInfo,
                        stringResource(R.string.now_playing_more_info),
                        ImageVector.vectorResource(R.drawable.s_a_moreinfo),
                        settingsDescription = stringResource(R.string.now_playing_more_info_description),
                        toggleEvent = {
                            coroutineScope.launch {
                                appearanceSettingsManager.setShowMoreInfo(!showMoreInfo)
                            }
                        }
                    )

                    SettingsDialogButton(
                        stringResource(R.string.now_playing_seek_bar_style),
                        nowPlayingSeekBarStyleLabel(seekBarStyle),
                        ImageVector.vectorResource(R.drawable.horizontal_rule_24px),
                        toggleEvent = { showSeekBarStyleDialog = true }
                    )
                }

                // Controls
                Column(
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // e.g. "Left Of Play/Pause: Shuffle, Previous · Right Of Play/Pause: Next, Repeat"
                    val buttonsSummary = NowPlayingButtonSlot.entries.mapNotNull { slot ->
                        val actions = buttons.filter { it.slot == slot }
                        if (actions.isEmpty()) null
                        else nowPlayingButtonSlotLabel(slot) + ": " +
                                actions.map { nowPlayingActionLabel(it.action) }.joinToString(", ")
                    }.joinToString(" · ")
                    SettingsDialogButton(
                        stringResource(R.string.now_playing_buttons),
                        buttonsSummary,
                        ImageVector.vectorResource(R.drawable.action_key_24px),
                        settingsDescription = stringResource(R.string.now_playing_buttons_description),
                        toggleEvent = { showButtonsDialog = true }
                    )

                    val menuSummary = menuItems.filter { it.enabled }
                        .map { nowPlayingActionLabel(it.action) }
                        .joinToString(", ")
                    SettingsDialogButton(
                        stringResource(R.string.now_playing_menu_actions),
                        menuSummary,
                        ImageVector.vectorResource(R.drawable.more_vert_24px),
                        settingsDescription = stringResource(R.string.now_playing_menu_actions_description),
                        toggleEvent = { showMenuActionsDialog = true }
                    )

                    val gesturesSummary = NowPlayingGesture.entries.mapNotNull { gesture ->
                        coverGestures[gesture]?.let { nowPlayingGestureLabel(gesture) + ": " + nowPlayingActionLabel(it) }
                    }.joinToString(", ").ifEmpty { stringResource(R.string.cover_gesture_none) }
                    SettingsDialogButton(
                        stringResource(R.string.now_playing_cover_gestures),
                        gesturesSummary,
                        ImageVector.vectorResource(R.drawable.swap_vert_24px),
                        settingsDescription = stringResource(R.string.now_playing_cover_gestures_description),
                        toggleEvent = { showCoverGesturesDialog = true }
                    )
                }

                Column(
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    SettingsSwitch(
                        disableScreenStandby,
                        stringResource(R.string.now_playing_screen_standby),
                        ImageVector.vectorResource(R.drawable.rounded_tv_24),
                        settingsDescription = stringResource(R.string.now_playing_screen_standby_description),
                        toggleEvent = {
                            coroutineScope.launch {
                                appearanceSettingsManager.setDisableScreenStandby(!disableScreenStandby)
                            }
                        }
                    )
                }
            }
        }

        if (showBackgroundDialog)
            BackgroundDialog(setShowDialog = { showBackgroundDialog = it })

        if (showSeekBarStyleDialog)
            NowPlayingSeekBarStyleDialog(
                selection = seekBarStyle,
                onSet = { coroutineScope.launch { appearanceSettingsManager.setNowPlayingSeekBarStyle(it) } },
                onDismissRequest = { showSeekBarStyleDialog = false }
            )

        if (showButtonsDialog)
            NowPlayingButtonsDialog(
                buttons = buttons,
                onSet = { coroutineScope.launch { appearanceSettingsManager.setNowPlayingButtons(it) } },
                onDismissRequest = { showButtonsDialog = false }
            )

        if (showMenuActionsDialog)
            NowPlayingMenuActionsDialog(
                items = menuItems,
                onSet = { coroutineScope.launch { appearanceSettingsManager.setNowPlayingMenuItems(it) } },
                onDismissRequest = { showMenuActionsDialog = false }
            )

        if (showCoverGesturesDialog)
            NowPlayingCoverGesturesDialog(
                gestures = coverGestures,
                onSet = { coroutineScope.launch { appearanceSettingsManager.setNowPlayingCoverGestures(it) } },
                onDismissRequest = { showCoverGesturesDialog = false }
            )
    }
}
