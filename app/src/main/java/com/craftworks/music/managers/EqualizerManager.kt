package com.craftworks.music.managers

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.Equalizer
import android.util.Log
import com.craftworks.music.managers.settings.PlaybackSettingsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** What the equalizer of the device can do. Levels are in millibels, frequencies in milliHertz. */
data class EqualizerInfo(
    val bandFrequencies: List<Int>,
    val minLevel: Int,
    val maxLevel: Int,
    val presets: List<String>,
)

/**
 * Owns the audio session every player of the app plays into, and the device [Equalizer] attached
 * to it. The equalizer follows the playback settings as long as the app runs.
 */
@Singleton
class EqualizerManager @Inject constructor(
    @ApplicationContext context: Context,
    private val playbackSettingsManager: PlaybackSettingsManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Shared by the main player and the crossfade player, so both go through the equalizer. */
    val audioSessionId: Int =
        (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).generateAudioSessionId()

    private val equalizer: Equalizer? = try {
        Equalizer(0, audioSessionId)
    } catch (e: Exception) {
        Log.w("Equalizer", "The device equalizer is not available", e)
        null
    }

    /** null when the device has no equalizer. */
    val info: EqualizerInfo? = equalizer?.runCatching {
        val (minLevel, maxLevel) = bandLevelRange.map { it.toInt() }
        EqualizerInfo(
            bandFrequencies = (0 until numberOfBands).map { getCenterFreq(it.toShort()) },
            minLevel = minLevel,
            maxLevel = maxLevel,
            presets = (0 until numberOfPresets).map { getPresetName(it.toShort()) },
        )
    }?.getOrNull()

    init {
        if (equalizer != null && info != null) {
            scope.launch {
                combine(
                    playbackSettingsManager.equalizerEnabledFlow,
                    playbackSettingsManager.equalizerBandLevelsFlow,
                ) { enabled, levels -> enabled to levels }
                    .collect { (enabled, levels) -> apply(equalizer, info, enabled, levels) }
            }
        }
    }

    private fun apply(equalizer: Equalizer, info: EqualizerInfo, enabled: Boolean, levels: List<Int>) {
        try {
            // Levels saved on another device don't match the bands of this one.
            val bandLevels = levels.takeIf { it.size == info.bandFrequencies.size }
                ?: List(info.bandFrequencies.size) { 0 }
            bandLevels.forEachIndexed { band, level ->
                equalizer.setBandLevel(band.toShort(), level.coerceIn(info.minLevel, info.maxLevel).toShort())
            }
            equalizer.enabled = enabled
        } catch (e: Exception) {
            Log.w("Equalizer", "Can't apply the equalizer settings", e)
        }
    }

    /** The band levels of a device preset, in millibels. */
    fun presetBandLevels(preset: Int): List<Int>? {
        val equalizer = equalizer ?: return null
        val info = info ?: return null
        return try {
            equalizer.usePreset(preset.toShort())
            info.bandFrequencies.indices.map { equalizer.getBandLevel(it.toShort()).toInt() }
        } catch (e: Exception) {
            Log.w("Equalizer", "Can't read the equalizer preset $preset", e)
            null
        }
    }

    companion object {
        /** "60 Hz", "14 kHz"... for a band center frequency in milliHertz. */
        fun frequencyLabel(milliHertz: Int): String {
            val hertz = milliHertz / 1000
            return if (hertz >= 1000) {
                val kiloHertz = hertz / 1000f
                if (kiloHertz % 1f == 0f) "${kiloHertz.toInt()} kHz" else "%.1f kHz".format(kiloHertz)
            } else "$hertz Hz"
        }
    }
}
