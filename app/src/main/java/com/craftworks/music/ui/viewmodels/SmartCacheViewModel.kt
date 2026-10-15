package com.craftworks.music.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.craftworks.music.managers.MediaProviderManager
import com.craftworks.music.managers.OfflineManager
import com.craftworks.music.managers.OfflineManager.Companion.requiresNetwork
import com.craftworks.music.managers.OfflineScrobbleManager
import com.craftworks.music.managers.SmartCacheManager
import com.craftworks.music.managers.SmartCacheState
import com.craftworks.music.managers.settings.PlaybackSettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Downloads, smart cache and offline scrobbles settings, for the playback and downloads settings screens. */
@HiltViewModel
class SmartCacheViewModel @Inject constructor(
    private val smartCacheManager: SmartCacheManager,
    private val playbackSettingsManager: PlaybackSettingsManager,
    offlineScrobbleManager: OfflineScrobbleManager,
    offlineManager: OfflineManager,
) : ViewModel() {
    val state: StateFlow<SmartCacheState> = smartCacheManager.state
    val pendingScrobbles: StateFlow<Int> = offlineScrobbleManager.pendingCount
    val isOffline: StateFlow<Boolean> = offlineManager.isOffline

    /** Whether the current provider can use the smart cache (local folders don't need it). */
    val isSupported: StateFlow<Boolean> = MediaProviderManager.currentProvider
        .map { it?.requiresNetwork() == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val maxSizeGb: StateFlow<Int> = playbackSettingsManager.smartCacheMaxSizeGbFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val trackOfflineScrobbles: StateFlow<Boolean> = playbackSettingsManager.trackOfflineScrobblesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setMaxSizeGb(sizeGb: Int) {
        viewModelScope.launch { playbackSettingsManager.setSmartCacheMaxSizeGb(sizeGb) }
    }

    fun setTrackOfflineScrobbles(enabled: Boolean) {
        viewModelScope.launch { playbackSettingsManager.setTrackOfflineScrobbles(enabled) }
    }

    /** Starts a refresh, or cancels the running one. */
    fun toggleRefresh() {
        if (state.value.isRunning) smartCacheManager.cancelRefresh() else smartCacheManager.refresh()
    }

    fun cancelDownloads() {
        smartCacheManager.cancelDownloads()
    }

    /** Deletes every song downloaded by the user, the smart cache is kept. */
    fun clearDownloads() {
        viewModelScope.launch { smartCacheManager.clearDownloads() }
    }
}
