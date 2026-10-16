package com.craftworks.music.ui.screens.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.craftworks.music.R
import com.craftworks.music.ui.elements.EqualizerBands
import com.craftworks.music.ui.elements.SettingsScaffold
import com.craftworks.music.ui.elements.dialogs.dialogFocusable
import com.craftworks.music.ui.elements.settingsGroupShape
import com.craftworks.music.ui.viewmodels.EqualizerViewModel

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun S_EqualizerScreen(
    navHostController: NavHostController = rememberNavController(),
    viewModel: EqualizerViewModel = hiltViewModel()
) {
    val equalizer = viewModel.info
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val preset by viewModel.preset.collectAsStateWithLifecycle()
    val bandLevels by viewModel.bandLevels.collectAsStateWithLifecycle()

    SettingsScaffold(
        title = stringResource(R.string.playback_equalizer),
        onBack = { navHostController.popBackStack() }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .dialogFocusable()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.clip(settingsGroupShape()),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                SettingsSwitch(
                    selected = enabled && equalizer != null,
                    settingsName = stringResource(R.string.playback_equalizer),
                    settingsIcon = ImageVector.vectorResource(R.drawable.rounded_equalizer_24),
                    settingsDescription = stringResource(
                        if (equalizer != null) R.string.playback_equalizer_description
                        else R.string.playback_equalizer_unsupported
                    ),
                    enabled = equalizer != null,
                    toggleEvent = { viewModel.setEnabled(!enabled) }
                )
            }

            if (equalizer == null) return@Column

            // Presets
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = preset < 0 && bandLevels.all { it == 0 },
                    onClick = { viewModel.selectPreset(-1) },
                    label = { Text(stringResource(R.string.playback_equalizer_flat)) },
                    enabled = enabled
                )
                equalizer.presets.forEachIndexed { index, name ->
                    FilterChip(
                        selected = preset == index,
                        onClick = { viewModel.selectPreset(index) },
                        label = { Text(name) },
                        enabled = enabled
                    )
                }
            }

            // Bands
            EqualizerBands(
                info = equalizer,
                bandLevels = bandLevels,
                enabled = enabled,
                onBandLevelChange = viewModel::setBandLevel,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(settingsGroupShape())
                    .background(MaterialTheme.colorScheme.surfaceBright)
                    .padding(vertical = 20.dp, horizontal = 8.dp)
            )
        }
    }
}
