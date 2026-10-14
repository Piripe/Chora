package com.craftworks.music.managers

import android.util.Log
import com.craftworks.music.data.cache.SmartCacheDatabase
import com.craftworks.music.managers.settings.PlaybackSettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the scrobbles that couldn't be sent (offline, provider unreachable) when the
 * "track offline scrobbles" setting is on, and sends them with their original play time
 * once the provider is reachable again.
 */
@Singleton
class OfflineScrobbleManager @Inject constructor(
    private val database: SmartCacheDatabase,
    private val offlineManager: OfflineManager,
    private val playbackSettingsManager: PlaybackSettingsManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val flushMutex = Mutex()
    private var initialized = false

    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    fun init() {
        if (initialized) return
        initialized = true

        scope.launch {
            _pendingCount.value = database.countPendingScrobbles()
            offlineManager.isOffline.collect { offline -> if (!offline) flush() }
        }
    }

    /** Stores a scrobble that failed, if offline scrobbles are tracked. */
    suspend fun onScrobbleFailed(providerId: String, songId: String, playedAt: Long) {
        if (songId.isEmpty() || !playbackSettingsManager.trackOfflineScrobblesFlow.first()) return
        database.addPendingScrobble(providerId, songId, playedAt)
        _pendingCount.value = database.countPendingScrobbles()
        Log.d(TAG, "Scrobble of $songId kept for later (${_pendingCount.value} pending)")
    }

    /** Sends the pending scrobbles, e.g. after a scrobble succeeded, as the provider is reachable. */
    fun flushAsync() {
        if (_pendingCount.value > 0) scope.launch { flush() }
    }

    private suspend fun flush(): Unit = flushMutex.withLock {
        for (scrobble in database.getPendingScrobbles()) {
            if (offlineManager.isOffline.value) break

            val provider = MediaProviderManager.getProvider(scrobble.providerId)
            if (provider != null) {
                val sent = offlineManager.runOrDefault(false, provider) {
                    it.scrobble(
                        id = scrobble.songId,
                        playbackRate = 1f,
                        submission = true,
                        event = null,
                        time = scrobble.playedAt
                    )
                    true
                }
                // Keep the order: retry this one and the next ones later.
                if (!sent) break
            }
            // Scrobbles of a removed provider can never be sent.
            database.deletePendingScrobble(scrobble.id)
        }

        _pendingCount.value = database.countPendingScrobbles()
    }

    companion object {
        private const val TAG = "OfflineScrobbles"
    }
}
