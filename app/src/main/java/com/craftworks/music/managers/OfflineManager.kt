package com.craftworks.music.managers

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.util.Log
import com.craftworks.music.data.model.ProviderFeature
import com.craftworks.music.data.providers.media.MediaProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks whether the current media provider can be reached.
 *
 * The app is considered offline when the current provider needs the network and either the device
 * has no network, or the last request to the provider failed. The provider is then checked
 * periodically, and the app switches back online as soon as it responds. While offline,
 * [fetchOrCache] answers from the [com.craftworks.music.data.cache.LibraryCache] instead.
 */
@Singleton
class OfflineManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    private val _networkAvailable = MutableStateFlow(hasNetwork())
    private val _serverUnreachable = MutableStateFlow(false)

    @Volatile
    private var lastFailureTime = 0L
    private var probeJob: Job? = null

    val isOffline: StateFlow<Boolean> = combine(
        _networkAvailable, _serverUnreachable, MediaProviderManager.currentProvider
    ) { networkAvailable, serverUnreachable, provider ->
        provider != null && provider.requiresNetwork() && (!networkAvailable || serverUnreachable)
    }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, false)

    init {
        connectivityManager?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _networkAvailable.value = true
                // The server may be reachable again with this network, check right away.
                if (_serverUnreachable.value) startProbing(immediate = true)
            }

            override fun onLost(network: Network) {
                _networkAvailable.value = hasNetwork()
            }
        })

        // Switching provider resets the reachability state.
        scope.launch {
            MediaProviderManager.currentProvider.collect {
                _serverUnreachable.value = false
                probeJob?.cancel()
            }
        }

        // Reload the screens with fresh data once back online.
        scope.launch {
            var wasOffline = isOffline.value
            isOffline.collect { offline ->
                if (wasOffline && !offline) DataRefreshManager.notifyDataSourcesChanged()
                wasOffline = offline
            }
        }
    }

    /**
     * Runs [online] against the current provider and stores its result with [onSuccess].
     * Falls back to [offline] when offline or when the request fails.
     * Providers that don't need the network (local folders) are always queried directly.
     */
    suspend fun <T> fetchOrCache(
        online: suspend (MediaProvider) -> T,
        onSuccess: (providerId: String, result: T) -> Unit = { _, _ -> },
        offline: (providerId: String) -> T,
    ): T? {
        val provider = MediaProviderManager.currentProvider.value ?: return null
        if (!provider.requiresNetwork()) return online(provider)

        if (shouldSkipNetwork()) return offline(provider.id)

        return try {
            online(provider).also {
                reportSuccess(provider)
                onSuccess(provider.id, it)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("OfflineManager", "Request to provider failed, using cache", e)
            reportFailure(provider)
            offline(provider.id)
        }
    }

    /**
     * Runs an action (rating, scrobble, playlist edit...) on [provider], returning [default] instead
     * of throwing when offline or when the provider fails.
     */
    suspend fun <T> runOrDefault(
        default: T,
        provider: MediaProvider? = MediaProviderManager.currentProvider.value,
        block: suspend (MediaProvider) -> T,
    ): T {
        if (provider == null) return default
        if (!provider.requiresNetwork()) return block(provider)

        if (provider.isCurrent() && shouldSkipNetwork()) return default

        return try {
            block(provider).also { reportSuccess(provider) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("OfflineManager", "Request to provider failed", e)
            reportFailure(provider)
            default
        }
    }

    /** Avoids waiting for timeouts on every request while we know the provider is unreachable. */
    private fun shouldSkipNetwork(): Boolean {
        if (!_networkAvailable.value) return true
        return _serverUnreachable.value &&
                SystemClock.elapsedRealtime() - lastFailureTime < RETRY_INTERVAL_MS
    }

    private fun MediaProvider.isCurrent() = MediaProviderManager.currentProvider.value?.id == id

    private fun reportSuccess(provider: MediaProvider) {
        if (!provider.isCurrent()) return
        _serverUnreachable.value = false
        probeJob?.cancel()
    }

    // Any error from the provider switches to offline mode, until it responds again.
    private fun reportFailure(provider: MediaProvider) {
        if (!provider.isCurrent()) return
        lastFailureTime = SystemClock.elapsedRealtime()
        _serverUnreachable.value = true
        startProbing(immediate = false)
    }

    private fun startProbing(immediate: Boolean) {
        if (probeJob?.isActive == true && !immediate) return
        probeJob?.cancel()
        probeJob = scope.launch {
            delay(if (immediate) 0 else FIRST_PROBE_DELAY_MS)
            while (isActive && _serverUnreachable.value) {
                val provider = MediaProviderManager.currentProvider.value ?: break
                if (_networkAvailable.value && provider.responds()) {
                    reportSuccess(provider)
                    break
                }
                delay(RETRY_INTERVAL_MS)
            }
        }
    }

    /**
     * Whether the provider answers a simple request. [MediaProvider.ping] isn't used as it returns
     * false for servers that aren't OpenSubsonic.
     */
    private suspend fun MediaProvider.responds(): Boolean = try {
        getMusicFolderList()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    private fun hasNetwork(): Boolean {
        val manager = connectivityManager ?: return true
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    companion object {
        private const val FIRST_PROBE_DELAY_MS = 5_000L
        private const val RETRY_INTERVAL_MS = 15_000L

        fun MediaProvider.requiresNetwork(): Boolean =
            ProviderFeature.OFFLINE_PLAYBACK !in featureFlags
    }
}
