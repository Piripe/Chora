package com.craftworks.music.managers.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.craftworks.music.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackSettingsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val TRANSCODING_BITRATE_WIFI_KEY = stringPreferencesKey("transcoding_bitrate_wifi")
        private val TRANSCODING_BITRATE_DATA_KEY = stringPreferencesKey("transcoding_bitrate_data")
        private val TRANSCODING_FORMAT_KEY = stringPreferencesKey("transcoding_format")

        private val AUTOPLAY_SONGS = booleanPreferencesKey("autoplay")

        private val CROSSFADE_SECONDS_KEY = intPreferencesKey("crossfade_seconds")
        const val MAX_CROSSFADE_SECONDS = 12

        private val EQUALIZER_ENABLED_KEY = booleanPreferencesKey("equalizer_enabled")
        private val EQUALIZER_PRESET_KEY = intPreferencesKey("equalizer_preset")
        private val EQUALIZER_BAND_LEVELS_KEY = stringPreferencesKey("equalizer_band_levels")

        private val SCROBBLE_PERCENT_KEY = intPreferencesKey("scrobble_percent")

        private val PREFETCH_MINUTES_KEY = intPreferencesKey("prefetch_minutes")
        const val MAX_PREFETCH_MINUTES = 60

        private val TRACK_OFFLINE_SCROBBLES_KEY = booleanPreferencesKey("track_offline_scrobbles")

        private val SMART_CACHE_MAX_SIZE_GB_KEY = intPreferencesKey("smart_cache_max_size_gb")
        /** The smart cache sizes offered to the user, in GB. 0 disables the smart cache. */
        val SMART_CACHE_SIZES_GB = listOf(0, 1, 2, 5, 10, 20, 50, 100, 200, 500, 1000)
    }

    val wifiTranscodingBitrateFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[TRANSCODING_BITRATE_WIFI_KEY] ?: "No Transcoding"
    }

    suspend fun setWifiTranscodingBitrate(bitrate: String) {
        withContext(NonCancellable) {
            context.dataStore.edit { preferences ->
                preferences[TRANSCODING_BITRATE_WIFI_KEY] = bitrate
            }
        }
    }

    val mobileDataTranscodingBitrateFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[TRANSCODING_BITRATE_DATA_KEY] ?: "No Transcoding"
    }

    suspend fun setMobileDataTranscodingBitrate(bitrate: String) {
        withContext(NonCancellable) {
            context.dataStore.edit { preferences ->
                preferences[TRANSCODING_BITRATE_DATA_KEY] = bitrate
            }
        }
    }

    val transcodingFormatFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[TRANSCODING_FORMAT_KEY] ?: "opus"
    }

    suspend fun setTranscodingFormat(format: String) {
        withContext(NonCancellable) {
            context.dataStore.edit { preferences ->
                preferences[TRANSCODING_FORMAT_KEY] = format
            }
        }
    }

    val scrobblePercentFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[SCROBBLE_PERCENT_KEY] ?: 7
    }

    suspend fun setScrobblePercent(scrobblePercent: Int) {
        withContext(NonCancellable) {
            context.dataStore.edit { preferences ->
                preferences[SCROBBLE_PERCENT_KEY] = scrobblePercent
            }
        }
    }

    val autoPlayFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUTOPLAY_SONGS] ?: false
    }

    suspend fun setAutoPlay(autoPlay: Boolean) {
        withContext(NonCancellable) {
            context.dataStore.edit { preferences ->
                preferences[AUTOPLAY_SONGS] = autoPlay
            }
        }
    }

    val prefetchMinutesFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PREFETCH_MINUTES_KEY] ?: 5
    }

    suspend fun setPrefetchMinutes(minutes: Int) {
        withContext(NonCancellable) {
            context.dataStore.edit { preferences ->
                preferences[PREFETCH_MINUTES_KEY] = minutes.coerceIn(0, MAX_PREFETCH_MINUTES)
            }
        }
    }

    val trackOfflineScrobblesFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[TRACK_OFFLINE_SCROBBLES_KEY] ?: true
    }

    suspend fun setTrackOfflineScrobbles(enabled: Boolean) {
        withContext(NonCancellable) {
            context.dataStore.edit { preferences ->
                preferences[TRACK_OFFLINE_SCROBBLES_KEY] = enabled
            }
        }
    }

    /** The maximum size of the smart cache in GB, 0 when it is disabled. */
    val smartCacheMaxSizeGbFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[SMART_CACHE_MAX_SIZE_GB_KEY] ?: 0
    }

    suspend fun setSmartCacheMaxSizeGb(sizeGb: Int) {
        withContext(NonCancellable) {
            context.dataStore.edit { preferences ->
                preferences[SMART_CACHE_MAX_SIZE_GB_KEY] = sizeGb
            }
        }
    }

    /** How long two songs overlap when one ends, 0 when crossfade is disabled. */
    val crossfadeSecondsFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[CROSSFADE_SECONDS_KEY] ?: 0
    }

    suspend fun setCrossfadeSeconds(seconds: Int) {
        withContext(NonCancellable) {
            context.dataStore.edit { preferences ->
                preferences[CROSSFADE_SECONDS_KEY] = seconds.coerceIn(0, MAX_CROSSFADE_SECONDS)
            }
        }
    }

    val equalizerEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[EQUALIZER_ENABLED_KEY] ?: false
    }

    suspend fun setEqualizerEnabled(enabled: Boolean) {
        withContext(NonCancellable) {
            context.dataStore.edit { preferences ->
                preferences[EQUALIZER_ENABLED_KEY] = enabled
            }
        }
    }

    /** The equalizer preset of the device in use, -1 when the bands were set by hand. */
    val equalizerPresetFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[EQUALIZER_PRESET_KEY] ?: -1
    }

    /** The level of each equalizer band in millibels, empty when they were never set. */
    val equalizerBandLevelsFlow: Flow<List<Int>> = context.dataStore.data.map { preferences ->
        preferences[EQUALIZER_BAND_LEVELS_KEY]
            ?.split(",")
            ?.mapNotNull { it.toIntOrNull() }
            ?: emptyList()
    }

    suspend fun setEqualizerBands(preset: Int, bandLevels: List<Int>) {
        withContext(NonCancellable) {
            context.dataStore.edit { preferences ->
                preferences[EQUALIZER_PRESET_KEY] = preset
                preferences[EQUALIZER_BAND_LEVELS_KEY] = bandLevels.joinToString(",")
            }
        }
    }
}
