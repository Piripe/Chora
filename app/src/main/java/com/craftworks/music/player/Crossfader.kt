package com.craftworks.music.player

import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Where a transcoded stream must start, since such streams can't be seeked into. */
class StreamOffset {
    @Volatile var positionMs: Long? = null
}

/**
 * Overlaps the end of a song with the start of the next one.
 *
 * Shortly before a song ends, its end is handed to a second player that fades it out, while the
 * main player moves on to the next song and fades it in. The main player stays the one of the
 * session, so the queue, the controllers and the notification are not affected. Main thread only.
 */
@UnstableApi
class Crossfader(
    private val player: ExoPlayer,
    private val scope: CoroutineScope,
    private val createFadeOutPlayer: () -> ExoPlayer,
    /** The stream offset used by the data sources of the players made by [createFadeOutPlayer]. */
    private val fadeOutStreamOffset: StreamOffset,
    /** Whether the player can seek into this item, false for streams transcoded on the fly. */
    private val canSeek: (MediaItem) -> Boolean,
    /** The ReplayGain volume of the current song of [player]. */
    private val replayGain: () -> Float,
    /** Called when the main player leaves [MediaItem] for the next song, at positionMs. */
    private val onSongLeft: (MediaItem, Long) -> Unit,
) {
    private enum class Phase { PREPARING, FADING }

    private var phase: Phase? = null
    private var job: Job? = null
    private var monitorJob: Job? = null
    private var fadeOutPlayer: ExoPlayer? = null

    // Set while the crossfade moves the main player itself, so its own events don't cancel it.
    private var switching = false

    // Only one try per song, so a failed crossfade is not started again at once.
    private var attempted = false

    /** How long the songs overlap, 0 to disable crossfade. */
    var crossfadeMs = 0L
        set(value) {
            field = value
            monitorJob?.cancel()
            monitorJob = if (value > 0) scope.launch {
                while (isActive) {
                    maybeStart()
                    delay(MONITOR_INTERVAL_MS)
                }
            } else null
        }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (switching) return
            attempted = false
            cancel()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            if (switching || reason != Player.DISCONTINUITY_REASON_SEEK) return
            attempted = false
            cancel()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (phase == Phase.FADING) fadeOutPlayer?.playWhenReady = playWhenReady
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) cancel()
        }
    }

    private val fadeOutListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) fadeOutPlayer?.stop()
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.w("Crossfade", "Fade-out player failed", error)
            if (phase == Phase.PREPARING) cancel() else fadeOutPlayer?.stop()
        }
    }

    init {
        player.addListener(playerListener)
    }

    private fun MediaItem.isRadio() = mediaMetadata.mediaType == MediaMetadata.MEDIA_TYPE_RADIO_STATION

    private fun maybeStart() {
        if (phase != null || attempted || !player.isPlaying) return
        if (player.repeatMode == Player.REPEAT_MODE_ONE || !player.hasNextMediaItem()) return

        val current = player.currentMediaItem ?: return
        if (current.isRadio() || player.getMediaItemAt(player.nextMediaItemIndex).isRadio()) return

        val durationMs = player.duration.takeIf { it > 0 }
            ?: current.mediaMetadata.durationMs?.takeIf { it > 0 }
            ?: return
        // A crossfade would eat most of very short songs.
        if (durationMs < crossfadeMs * 3) return

        val remainingMs = durationMs - player.currentPosition
        if (remainingMs > crossfadeMs + PREPARE_LEAD_MS) return

        attempted = true
        // Seeked to the very end: too late to fade.
        if (remainingMs < MIN_FADE_MS + PREPARE_LEAD_MS) return

        prepare(current, durationMs)
    }

    /**
     * Loads the end of [current] in the fade-out player a little ahead of the main player, then
     * starts it when the main player reaches the same position, and switches the main player to the
     * next song.
     */
    private fun prepare(current: MediaItem, durationMs: Long) {
        val seekable = canSeek(current)
        val fadeOut = fadeOutPlayer ?: createFadeOutPlayer().also {
            it.addListener(fadeOutListener)
            fadeOutPlayer = it
        }

        // Transcoded streams start at whole seconds.
        fun startPosition() = (player.currentPosition + PREPARE_LEAD_MS + 999) / 1000 * 1000
        var startMs = startPosition()

        if (seekable) {
            fadeOutStreamOffset.positionMs = null
            fadeOut.setMediaItem(current, startMs)
        } else {
            // The stream itself starts at startMs.
            fadeOutStreamOffset.positionMs = startMs
            fadeOut.setMediaItem(current)
        }
        val outGain = replayGain()
        fadeOut.volume = outGain
        fadeOut.playWhenReady = false
        fadeOut.prepare()
        phase = Phase.PREPARING

        job = scope.launch {
            while (true) {
                delay(PREPARE_INTERVAL_MS)
                val positionMs = player.currentPosition
                if (durationMs - positionMs < MIN_FADE_MS) return@launch cancel()

                val ready = fadeOut.playbackState == Player.STATE_READY
                if (ready && positionMs < startMs) continue

                // Not loaded in time, or the main player is already past the start.
                if (!ready && positionMs <= startMs + MAX_DRIFT_MS) continue
                if (positionMs > startMs + MAX_DRIFT_MS) {
                    if (!seekable) return@launch cancel()
                    startMs = startPosition()
                    fadeOut.seekTo(startMs)
                    continue
                }
                break
            }

            if (!player.hasNextMediaItem()) return@launch cancel()

            val leftAtMs = player.currentPosition
            val fadeMs = (durationMs - leftAtMs).coerceIn(MIN_FADE_MS, crossfadeMs.coerceAtLeast(MIN_FADE_MS))
            fadeOut.play()
            onSongLeft(current, leftAtMs)

            switching = true
            try {
                player.seekTo(player.nextMediaItemIndex, 0)
            } finally {
                switching = false
            }
            attempted = false
            player.volume = 0f
            phase = Phase.FADING

            // Equal power curves, so the loudness stays even during the fade.
            while (true) {
                val progress = (player.currentPosition.toFloat() / fadeMs).coerceIn(0f, 1f)
                player.volume = replayGain() * sin(progress * PI / 2).toFloat()
                fadeOut.volume = outGain * cos(progress * PI / 2).toFloat()
                if (progress >= 1f) break
                delay(FADE_INTERVAL_MS)
            }
            finish()
        }
    }

    /** Stops the fade-out player and puts the main player back to full volume. */
    private fun finish() {
        job?.cancel()
        job = null
        fadeOutPlayer?.run {
            stop()
            clearMediaItems()
        }
        fadeOutStreamOffset.positionMs = null
        if (phase == Phase.FADING) player.volume = replayGain()
        phase = null
    }

    fun cancel() {
        if (phase != null) finish()
    }

    fun release() {
        monitorJob?.cancel()
        finish()
        player.removeListener(playerListener)
        fadeOutPlayer?.release()
        fadeOutPlayer = null
    }

    companion object {
        private const val MONITOR_INTERVAL_MS = 100L
        private const val PREPARE_INTERVAL_MS = 10L
        private const val FADE_INTERVAL_MS = 50L

        // How long ahead of the main player the fade-out player is loaded.
        private const val PREPARE_LEAD_MS = 1500L
        // How far apart the two players may start.
        private const val MAX_DRIFT_MS = 60L
        private const val MIN_FADE_MS = 1000L
    }
}
