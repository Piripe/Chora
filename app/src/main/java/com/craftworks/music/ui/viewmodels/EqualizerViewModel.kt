package com.craftworks.music.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.craftworks.music.managers.EqualizerInfo
import com.craftworks.music.managers.EqualizerManager
import com.craftworks.music.managers.settings.PlaybackSettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Equalizer settings, for the playback settings screens. */
@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val equalizerManager: EqualizerManager,
    private val playbackSettingsManager: PlaybackSettingsManager,
) : ViewModel() {
    /** null when the device has no equalizer. */
    val info: EqualizerInfo? = equalizerManager.info

    val enabled: StateFlow<Boolean> = playbackSettingsManager.equalizerEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val preset: StateFlow<Int> = playbackSettingsManager.equalizerPresetFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), -1)

    /** One level per band in millibels, flat when they were never set. */
    val bandLevels: StateFlow<List<Int>> = playbackSettingsManager.equalizerBandLevelsFlow.map { levels ->
        val bandCount = info?.bandFrequencies?.size ?: 0
        levels.takeIf { it.size == bandCount } ?: List(bandCount) { 0 }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), List(info?.bandFrequencies?.size ?: 0) { 0 })

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { playbackSettingsManager.setEqualizerEnabled(enabled) }
    }

    /** Selects a device preset, or resets every band when [preset] is -1. */
    fun selectPreset(preset: Int) {
        val info = info ?: return
        viewModelScope.launch(Dispatchers.Default) {
            val levels = if (preset >= 0) equalizerManager.presetBandLevels(preset) ?: return@launch
                else List(info.bandFrequencies.size) { 0 }
            playbackSettingsManager.setEqualizerBands(preset, levels)
        }
    }

    fun setBandLevel(band: Int, level: Int) {
        val levels = bandLevels.value.toMutableList()
        if (band !in levels.indices) return
        levels[band] = level
        viewModelScope.launch { playbackSettingsManager.setEqualizerBands(-1, levels) }
    }
}
