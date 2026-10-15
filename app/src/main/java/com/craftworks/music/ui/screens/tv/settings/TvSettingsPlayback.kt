package com.craftworks.music.ui.screens.tv.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.craftworks.music.R
import com.craftworks.music.managers.settings.PlaybackSettingsManager
import com.craftworks.music.ui.elements.dialogs.tv.TranscodingBitrateDialog
import com.craftworks.music.ui.elements.dialogs.tv.TranscodingFormatDialog
import com.craftworks.music.ui.viewmodels.SmartCacheViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
@Preview(device = "id:tv_1080p", showSystemUi = true, showBackground = true)
fun TvS_PlaybackScreen(smartCacheViewModel: SmartCacheViewModel = hiltViewModel()) {
    var showWifiTranscodingDialog by remember { mutableStateOf(false) }
    var showDataTranscodingDialog by remember { mutableStateOf(false) }
    var showTranscodingFormatDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val transcodingBitrateWifi by PlaybackSettingsManager(context).wifiTranscodingBitrateFlow.collectAsState("")
    val transcodingBitrateData by PlaybackSettingsManager(context).mobileDataTranscodingBitrateFlow.collectAsState("")
    val transcodingFormat by PlaybackSettingsManager(context).transcodingFormatFlow.collectAsState("opus")
    val transcodingFormatEnabled by remember {
        derivedStateOf {
            transcodingBitrateData != "No Transcoding" || transcodingBitrateWifi != "No Transcoding"
        }
    }

    val trackOfflineScrobbles by smartCacheViewModel.trackOfflineScrobbles.collectAsStateWithLifecycle()
    val pendingScrobbles by smartCacheViewModel.pendingScrobbles.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 24.dp)
    ) {
        item {
            SettingsButtonItem(
                title = stringResource(R.string.playback_max_bitrate_wifi),
                subtitle = if (transcodingBitrateWifi != "No Transcoding") "$transcodingBitrateWifi Kbps" else stringResource(R.string.playback_no_transcoding),
                icon = ImageVector.vectorResource(R.drawable.s_p_transcoding),
                description = stringResource(R.string.playback_max_bitrate_wifi_description),
                onClick = { showWifiTranscodingDialog = true }
            )
        }

        item {
            SettingsButtonItem(
                title = stringResource(R.string.playback_max_bitrate_mobile_data),
                subtitle = if (transcodingBitrateData != "No Transcoding") "$transcodingBitrateData Kbps" else stringResource(R.string.playback_no_transcoding),
                icon = ImageVector.vectorResource(R.drawable.s_p_transcoding),
                description = stringResource(R.string.playback_max_bitrate_mobile_data_description),
                onClick = { showDataTranscodingDialog = true }
            )
        }

        item {
            SettingsButtonItem(
                title = stringResource(R.string.playback_transcoding_format),
                description = stringResource(R.string.playback_transcoding_format_description),
                subtitle = transcodingFormat,
                icon = ImageVector.vectorResource(R.drawable.s_p_transcoding),
                enabled = transcodingFormatEnabled,
                onClick = { showTranscodingFormatDialog = true }
            )
        }

        item {
            val sliderValue by PlaybackSettingsManager(context).scrobblePercentFlow.collectAsState(7)

            Surface(
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .padding(vertical = 8.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.playback_min_scrobble_percentage_value, sliderValue * 10),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    SettingsDescription(
                        stringResource(R.string.playback_min_scrobble_percentage_description),
                        Modifier.padding(bottom = 8.dp)
                    )
                    Slider(
                        value = sliderValue.toFloat(),
                        onValueChange = {
                            coroutineScope.launch {
                                PlaybackSettingsManager(context).setScrobblePercent(it.roundToInt())
                            }
                        },
                        valueRange = 1f..10f,
                        steps = 8,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onKeyEvent { keyEvent ->
                                when (keyEvent.key) {
                                    Key.DirectionRight -> {
                                        coroutineScope.launch {
                                            PlaybackSettingsManager(context).setScrobblePercent((sliderValue + 1).coerceAtMost(10))
                                        }
                                        true
                                    }

                                    Key.DirectionLeft -> {
                                        coroutineScope.launch {
                                            PlaybackSettingsManager(context).setScrobblePercent((sliderValue - 1).coerceAtLeast(1))
                                        }
                                        true
                                    }

                                    else -> false
                                }
                            }
                    )
                }
            }
        }

        item {
            SettingsSwitchItem(
                title = if (pendingScrobbles > 0)
                    stringResource(R.string.playback_track_offline_scrobbles_pending, pendingScrobbles)
                else
                    stringResource(R.string.playback_track_offline_scrobbles),
                icon = ImageVector.vectorResource(R.drawable.s_p_scrobble),
                description = stringResource(R.string.playback_track_offline_scrobbles_description),
                checked = trackOfflineScrobbles,
                onCheckedChange = { smartCacheViewModel.setTrackOfflineScrobbles(it) }
            )
        }

        item {
            val prefetchMinutes by PlaybackSettingsManager(context).prefetchMinutesFlow.collectAsState(5)

            Surface(
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .padding(vertical = 8.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = if (prefetchMinutes > 0)
                            stringResource(R.string.playback_prefetch_minutes, prefetchMinutes)
                        else
                            stringResource(R.string.playback_prefetch_off),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    SettingsDescription(
                        stringResource(R.string.playback_prefetch_description),
                        Modifier.padding(bottom = 8.dp)
                    )
                    Slider(
                        value = prefetchMinutes.toFloat(),
                        onValueChange = {
                            coroutineScope.launch {
                                PlaybackSettingsManager(context).setPrefetchMinutes(it.roundToInt())
                            }
                        },
                        valueRange = 0f..PlaybackSettingsManager.MAX_PREFETCH_MINUTES.toFloat(),
                        steps = PlaybackSettingsManager.MAX_PREFETCH_MINUTES / 5 - 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onKeyEvent { keyEvent ->
                                when (keyEvent.key) {
                                    Key.DirectionRight -> {
                                        coroutineScope.launch {
                                            PlaybackSettingsManager(context).setPrefetchMinutes((prefetchMinutes + 5).coerceAtMost(PlaybackSettingsManager.MAX_PREFETCH_MINUTES))
                                        }
                                        true
                                    }

                                    Key.DirectionLeft -> {
                                        coroutineScope.launch {
                                            PlaybackSettingsManager(context).setPrefetchMinutes((prefetchMinutes - 5).coerceAtLeast(0))
                                        }
                                        true
                                    }

                                    else -> false
                                }
                            }
                    )
                }
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