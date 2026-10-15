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
import androidx.compose.runtime.collectAsState
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.craftworks.music.R
import com.craftworks.music.data.model.Screen
import com.craftworks.music.managers.MediaProviderManager
import com.craftworks.music.managers.settings.PlaybackSettingsManager
import com.craftworks.music.ui.elements.ActionTooltip
import com.craftworks.music.ui.elements.dialogs.TranscodingBitrateDialog
import com.craftworks.music.ui.elements.dialogs.TranscodingFormatDialog
import com.craftworks.music.ui.elements.dialogs.dialogFocusable
import com.craftworks.music.ui.elements.dialogs.equalizerPresetLabel
import com.craftworks.music.ui.viewmodels.EqualizerViewModel
import com.craftworks.music.ui.viewmodels.SmartCacheViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.math.roundToInt

@Preview(showSystemUi = false, showBackground = true)
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun S_PlaybackScreen(
    navHostController: NavHostController = rememberNavController(),
    smartCacheViewModel: SmartCacheViewModel = hiltViewModel(),
    equalizerViewModel: EqualizerViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    var showWifiTranscodingDialog by remember { mutableStateOf(false) }
    var showDataTranscodingDialog by remember { mutableStateOf(false) }

    var showTranscodingFormatDialog by remember { mutableStateOf(false) }

    val equalizer = equalizerViewModel.info
    val equalizerEnabled by equalizerViewModel.enabled.collectAsStateWithLifecycle()
    val equalizerPreset by equalizerViewModel.preset.collectAsStateWithLifecycle()
    val equalizerBandLevels by equalizerViewModel.bandLevels.collectAsStateWithLifecycle()

    val currentProvider by MediaProviderManager.currentProvider.collectAsStateWithLifecycle()

    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.settings_playback)) },
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
                                contentDescription = stringResource(R.string.settings_playback),
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
        Box (
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Transcoding
                Column(
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val transcodingBitrateWifi =
                        PlaybackSettingsManager(context).wifiTranscodingBitrateFlow.collectAsState("").value

                    SettingsDialogButton(
                        settingsName = stringResource(R.string.playback_max_bitrate_wifi),
                        settingsSubtitle = if (transcodingBitrateWifi != "No Transcoding") "$transcodingBitrateWifi Kbps" else stringResource(R.string.playback_no_transcoding),
                        settingsIcon = ImageVector.vectorResource(R.drawable.s_p_transcoding),
                        settingsDescription = stringResource(R.string.playback_max_bitrate_wifi_description),
                        toggleEvent = { showWifiTranscodingDialog = true },
                        enabled = currentProvider != null
                    )


                    val transcodingBitrateData =
                        PlaybackSettingsManager(context).mobileDataTranscodingBitrateFlow.collectAsState(
                            ""
                        ).value

                    SettingsDialogButton(
                        settingsName = stringResource(R.string.playback_max_bitrate_mobile_data),
                        settingsSubtitle = if (transcodingBitrateData != "No Transcoding") "$transcodingBitrateData Kbps" else stringResource(R.string.playback_no_transcoding),
                        settingsIcon = ImageVector.vectorResource(R.drawable.s_p_transcoding),
                        settingsDescription = stringResource(R.string.playback_max_bitrate_mobile_data_description),
                        toggleEvent = { showDataTranscodingDialog = true },
                        enabled = currentProvider != null
                    )

                    val transcodingFormat =
                        PlaybackSettingsManager(context).transcodingFormatFlow.collectAsState("opus").value

                    val transcodingFormatEnabled =
                        (transcodingBitrateData != "No Transcoding" || transcodingBitrateWifi != "No Transcoding") && currentProvider != null

                    SettingsDialogButton(
                        settingsName = stringResource(R.string.playback_transcoding_format),
                        settingsSubtitle = transcodingFormat,
                        settingsDescription = stringResource(R.string.playback_transcoding_format_description),
                        settingsIcon = ImageVector.vectorResource(R.drawable.s_p_transcoding),
                        enabled = transcodingFormatEnabled,
                        toggleEvent = { showTranscodingFormatDialog = true }
                    )
                }

                // Autoplay
                Column(
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val autoPlay by PlaybackSettingsManager(context).autoPlayFlow.collectAsStateWithLifecycle(false)
                    SettingsSwitch(
                        selected = autoPlay,
                        settingsName = stringResource(R.string.playback_autoplay),
                        settingsIcon = ImageVector.vectorResource(R.drawable.rounded_sensors_24),
                        settingsDescription = stringResource(R.string.playback_autoplay_description),
                        toggleEvent = {
                            coroutineScope.launch {
                                PlaybackSettingsManager(context).setAutoPlay(!autoPlay)
                            }
                        }
                    )
                }

                // Crossfade
                Column(
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val crossfadeSeconds by
                        PlaybackSettingsManager(context).crossfadeSecondsFlow.collectAsStateWithLifecycle(0)

                    SettingsSlider(
                        settingsName = if (crossfadeSeconds > 0)
                            stringResource(R.string.playback_crossfade_seconds, crossfadeSeconds)
                        else
                            stringResource(R.string.playback_crossfade_off),
                        settingsDescription = stringResource(R.string.playback_crossfade_description),
                        value = crossfadeSeconds.toFloat(),
                        steps = PlaybackSettingsManager.MAX_CROSSFADE_SECONDS - 1,
                        minValue = 0f, maxValue = PlaybackSettingsManager.MAX_CROSSFADE_SECONDS.toFloat(),
                        onValueChange = {
                            coroutineScope.launch {
                                PlaybackSettingsManager(context).setCrossfadeSeconds(it.roundToInt())
                            }
                        }
                    )
                }

                // Equalizer
                Column(
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    SettingsDialogButton(
                        settingsName = stringResource(R.string.playback_equalizer),
                        settingsSubtitle = when {
                            equalizer == null -> stringResource(R.string.playback_equalizer_unsupported)
                            equalizerEnabled -> equalizerPresetLabel(equalizer.presets, equalizerPreset, equalizerBandLevels)
                            else -> stringResource(R.string.playback_equalizer_off)
                        },
                        settingsIcon = ImageVector.vectorResource(R.drawable.rounded_equalizer_24),
                        settingsDescription = stringResource(R.string.playback_equalizer_description),
                        toggleEvent = {
                            navHostController.navigate(Screen.S_Equalizer) {
                                launchSingleTop = true
                            }
                        }
                    )
                }

                // Scrobble Percent
                Column(
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val sliderValue =
                        PlaybackSettingsManager(context).scrobblePercentFlow.collectAsState(7)

                    SettingsSlider(
                        settingsName = stringResource(R.string.playback_min_scrobble_percentage_value, sliderValue.value * 10),
                        settingsDescription = stringResource(R.string.playback_min_scrobble_percentage_description),
                        value = sliderValue.value.toFloat(),
                        steps = 8,
                        minValue = 1f, maxValue = 10f,
                        onValueChange = {
                            runBlocking {
                                PlaybackSettingsManager(context).setScrobblePercent(it.roundToInt())
                            }
                        }
                    )

                    val trackOfflineScrobbles by smartCacheViewModel.trackOfflineScrobbles.collectAsStateWithLifecycle()
                    val pendingScrobbles by smartCacheViewModel.pendingScrobbles.collectAsStateWithLifecycle()

                    SettingsSwitch(
                        selected = trackOfflineScrobbles,
                        settingsName = if (pendingScrobbles > 0)
                            stringResource(R.string.playback_track_offline_scrobbles_pending, pendingScrobbles)
                        else
                            stringResource(R.string.playback_track_offline_scrobbles),
                        settingsIcon = ImageVector.vectorResource(R.drawable.s_p_scrobble),
                        settingsDescription = stringResource(R.string.playback_track_offline_scrobbles_description),
                        toggleEvent = { smartCacheViewModel.setTrackOfflineScrobbles(!trackOfflineScrobbles) }
                    )
                }

                // Pre-fetching
                Column(
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val prefetchMinutes by
                        PlaybackSettingsManager(context).prefetchMinutesFlow.collectAsState(5)

                    SettingsSlider(
                        settingsName = if (prefetchMinutes > 0)
                            stringResource(R.string.playback_prefetch_minutes, prefetchMinutes)
                        else
                            stringResource(R.string.playback_prefetch_off),
                        settingsDescription = stringResource(R.string.playback_prefetch_description),
                        value = prefetchMinutes.toFloat(),
                        steps = PlaybackSettingsManager.MAX_PREFETCH_MINUTES / 5 - 1,
                        minValue = 0f, maxValue = PlaybackSettingsManager.MAX_PREFETCH_MINUTES.toFloat(),
                        onValueChange = {
                            runBlocking {
                                PlaybackSettingsManager(context).setPrefetchMinutes(it.roundToInt())
                            }
                        }
                    )
                }
            }
        }

        if (showWifiTranscodingDialog) TranscodingBitrateDialog(setShowDialog = {
            showWifiTranscodingDialog = it
        }, true)
        if (showDataTranscodingDialog) TranscodingBitrateDialog(setShowDialog = {
            showDataTranscodingDialog = it
        }, false)
        if (showTranscodingFormatDialog) TranscodingFormatDialog(setShowDialog = {
            showTranscodingFormatDialog = it
        })
    }
}