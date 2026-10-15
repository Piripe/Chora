package com.craftworks.music.ui.screens.tv.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import com.craftworks.music.R
import com.craftworks.music.ui.elements.EqualizerBands
import com.craftworks.music.ui.elements.dialogs.equalizerPresetLabel
import com.craftworks.music.ui.elements.dialogs.tv.EqualizerPresetDialog
import com.craftworks.music.ui.viewmodels.EqualizerViewModel

/** The switch and preset on the left, the bands on the right, so left and right move between bands. */
@Composable
@Preview(device = "id:tv_1080p", showSystemUi = true, showBackground = true)
fun TvS_EqualizerScreen(viewModel: EqualizerViewModel = hiltViewModel()) {
    var showPresetDialog by remember { mutableStateOf(false) }

    val equalizer = viewModel.info
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val preset by viewModel.preset.collectAsStateWithLifecycle()
    val bandLevels by viewModel.bandLevels.collectAsStateWithLifecycle()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        Column(
            modifier = Modifier.width(360.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsSwitchItem(
                title = stringResource(R.string.playback_equalizer),
                icon = ImageVector.vectorResource(R.drawable.rounded_equalizer_24),
                description = stringResource(
                    if (equalizer != null) R.string.playback_equalizer_description
                    else R.string.playback_equalizer_unsupported
                ),
                checked = enabled && equalizer != null,
                enabled = equalizer != null,
                onCheckedChange = { viewModel.setEnabled(it) }
            )

            if (equalizer != null) {
                SettingsButtonItem(
                    title = stringResource(R.string.playback_equalizer_preset),
                    subtitle = equalizerPresetLabel(equalizer.presets, preset, bandLevels),
                    icon = ImageVector.vectorResource(R.drawable.rounded_equalizer_24),
                    enabled = enabled,
                    onClick = { showPresetDialog = true }
                )
            }
        }

        if (equalizer != null) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.weight(1f)
            ) {
                EqualizerBands(
                    info = equalizer,
                    bandLevels = bandLevels,
                    enabled = enabled,
                    onBandLevelChange = viewModel::setBandLevel,
                    sliderHeight = 280.dp,
                    textColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 24.dp, horizontal = 16.dp)
                )
            }
        }
    }

    if (showPresetDialog && equalizer != null) EqualizerPresetDialog(
        setShowDialog = { showPresetDialog = it },
        presets = equalizer.presets,
        selectedPreset = preset.takeIf { it >= 0 || bandLevels.all { level -> level == 0 } },
        onPresetSelected = viewModel::selectPreset
    )
}
