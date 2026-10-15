package com.craftworks.music.player

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.ui.util.fastFilter
import androidx.core.math.MathUtils.clamp
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Rating
import androidx.media3.common.StarRating
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.craftworks.music.MainActivity
import com.craftworks.music.R
import com.craftworks.music.data.model.AlbumArtistListSort
import com.craftworks.music.data.model.AlbumListSort
import com.craftworks.music.data.model.MediaQuery
import com.craftworks.music.data.model.PlaylistListSort
import com.craftworks.music.data.model.ProviderFeature
import com.craftworks.music.data.model.Screen
import com.craftworks.music.data.model.ScrobbleEvent
import com.craftworks.music.data.model.SongListSort
import com.craftworks.music.data.model.SortOrder
import com.craftworks.music.data.model.getProvider
import com.craftworks.music.data.model.id
import com.craftworks.music.data.repository.AlbumRepository
import com.craftworks.music.data.repository.ArtistRepository
import com.craftworks.music.data.repository.LyricsRepository
import com.craftworks.music.data.repository.MixRepository
import com.craftworks.music.data.repository.PlaylistRepository
import com.craftworks.music.data.repository.RadioRepository
import com.craftworks.music.data.repository.SongRepository
import com.craftworks.music.managers.EqualizerManager
import com.craftworks.music.managers.MediaProviderManager
import com.craftworks.music.managers.OfflineManager
import com.craftworks.music.managers.SmartCacheManager
import com.craftworks.music.managers.TranscodeManager
import com.craftworks.music.managers.settings.AppearanceSettingsManager
import com.craftworks.music.managers.settings.LocalDataSettingsManager
import com.craftworks.music.managers.settings.PlaybackSettingsManager
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import javax.inject.Inject
import kotlin.math.pow
import kotlin.time.Duration.Companion.seconds

/*
    Thanks to Yurowitz on StackOverflow for this! Used it as a template.
    https://stackoverflow.com/questions/76838126/can-i-define-a-medialibraryservice-without-an-app
*/

@UnstableApi
@AndroidEntryPoint
class ChoraMediaLibraryService : MediaLibraryService() {
    //region Vars
    lateinit var player: Player
    var session: MediaLibrarySession? = null

    private var scrobbleJob: Job? = null

    private lateinit var prefetcher: TrackPrefetcher
    private var prefetchMinutes = 0
    private var sleepTimerJob: Job? = null
    private var autoPlay = false
    private var autoPlayJob: Job? = null
    private lateinit var crossfader: Crossfader
    private var playerScrobbled = false
    // The ReplayGain volume of the current song.
    private var replayGain = 1f
    private var _sleepTimerRemainingTime = MutableStateFlow(0)
    val sleepTimerRemainingTime: StateFlow<Int> = _sleepTimerRemainingTime.asStateFlow()

    @Inject lateinit var appearanceSettingsManager: AppearanceSettingsManager
    @Inject lateinit var playbackSettingsManager: PlaybackSettingsManager
    @Inject lateinit var transcodeManager: TranscodeManager
    @Inject lateinit var offlineManager: OfflineManager
    @Inject lateinit var smartCacheManager: SmartCacheManager
    @Inject lateinit var equalizerManager: EqualizerManager

    @Inject lateinit var albumRepository: AlbumRepository
    @Inject lateinit var artistRepository: ArtistRepository
    @Inject lateinit var songRepository: SongRepository
    @Inject lateinit var radioRepository: RadioRepository
    @Inject lateinit var playlistRepository: PlaylistRepository
    @Inject lateinit var lyricsRepository: LyricsRepository
    @Inject lateinit var mixRepository: MixRepository

    companion object {
        private var instance: ChoraMediaLibraryService? = null

        // Used to estimate the queue length when a track has no duration.
        private const val DEFAULT_TRACK_DURATION_MS = 4 * 60_000L

        // How many songs autoplay adds when the queue ends.
        private const val AUTOPLAY_SONG_COUNT = 20

        fun getInstance(): ChoraMediaLibraryService? {
            return instance
        }
    }

    private val rootItem = MediaItem.Builder()
        .setMediaId("nodeROOT")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setIsBrowsable(false)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .build()
        )
        .build()

    private val homeItem = MediaItem.Builder()
        .setMediaId("nodeHOME")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS)
                .setTitle("Home")
                .setExtras(Bundle().apply {
                    putInt(
                        MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                        MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
                    )
                })
                .build()
        )
        .build()

    private val albumsItem = MediaItem.Builder()
        .setMediaId("nodeALBUMS")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)
                .setTitle("Albums")
                .build()
        )
        .build()

    private val artistsItem = MediaItem.Builder()
        .setMediaId("nodeARTISTS")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS)
                .setTitle("Artists")
                .build()
        )
        .build()

    private val radiosItem = MediaItem.Builder()
        .setMediaId("nodeRADIOS")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                .setTitle("Radios")
                .build()
        )
        .build()

    private val playlistsItem = MediaItem.Builder()
        .setMediaId("nodePLAYLISTS")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)
                .setTitle("Playlists")
                .build()
        )
        .build()

    private var rootHierarchy = mutableListOf<MediaItem>()

    private val serviceMainScope = CoroutineScope(Dispatchers.Main)
    private val serviceIOScope = CoroutineScope(Dispatchers.IO)

    var aHomeScreenItems = mutableListOf<MediaItem>()
    var aAlbumScreenItems = mutableListOf<MediaItem>()
    var aArtistsScreenItems = mutableListOf<MediaItem>()
    var aRadioScreenItems = mutableListOf<MediaItem>()
    var aPlaylistScreenItems = mutableListOf<MediaItem>()

    var aFolderSongs = mutableListOf<MediaItem>()

    //endregion

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        instance = this

        Log.d("AA", "onCreate Android Auto")

        if (session == null)
            initializePlayer()
        else
            Log.d("AA", "MediaSession already initialized, not recreating")
    }

    @OptIn(ExperimentalApi::class)
    fun initializePlayer() {
        serviceIOScope.launch {
            appearanceSettingsManager.bottomNavItemsFlow.collect { items ->
                val routeToItem = mapOf(
                    Screen.Home to homeItem,
                    Screen.Albums to albumsItem,
                    Screen.Artists to artistsItem,
                    Screen.Radios to radiosItem,
                    Screen.Playlists to playlistsItem
                )

                rootHierarchy = items
                    .filter { it.enabled }
                    .mapNotNull { routeToItem[it.screenRoute] }
                    .toMutableList()

                session?.notifyChildrenChanged("nodeROOT", 0, null)
            }
        }

        val okHttpClient = createStreamingHttpClient()

        prefetcher = TrackPrefetcher(
            okHttpClient,
            currentVariant = ::currentStreamVariant,
            streamUrl = { uri, variant -> getStreamUrl(uri, variant) },
            isOffline = { offlineManager.isOffline.value },
            onPrefetched = { uri, variant, entry ->
                smartCacheManager.offerPrefetchedTrack(uri, variant, entry.length, entry::writeTo)
            }
        ).also { it.start(serviceIOScope) }

        // Set when the player seeks, for streams transcoded on the fly.
        val streamOffset = StreamOffset()

        val exoPlayer = ExoPlayer.Builder(this)
            .setSeekParameters(SeekParameters.EXACT)
            .setMediaSourceFactory(createMediaSourceFactory(okHttpClient, streamOffset))
            .setWakeMode(
                if (MediaProviderManager.currentProvider.value?.featureFlags?.contains(
                        ProviderFeature.OFFLINE_PLAYBACK
                    ) ?: false)
                    C.WAKE_MODE_NETWORK
                else
                    C.WAKE_MODE_LOCAL
            )
            .setHandleAudioBecomingNoisy(true)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .experimentalSetDynamicSchedulingEnabled(false) // Temporary fix for lyrics. refer to https://github.com/androidx/media/issues/3286
            .build()
        // The equalizer is attached to this session.
        exoPlayer.audioSessionId = equalizerManager.audioSessionId
        player = exoPlayer

        player.repeatMode = Player.REPEAT_MODE_OFF
        player.shuffleModeEnabled = false

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Apply ReplayGain
                replayGain = clamp(
                    (10f.pow(((mediaItem?.mediaMetadata?.extras?.getFloat("replayGain") ?: 0f) / 20f))),
                    0f,
                    1f
                )
                player.volume = replayGain
                Log.d("REPLAY GAIN", "Setting ReplayGain to ${player.volume}")

                playerScrobbled = false

                super.onMediaItemTransition(mediaItem, reason)

                serviceIOScope.launch {
                    lyricsRepository.getLyrics(mediaItem?.mediaMetadata)
                    val mediaId = mediaItem?.mediaMetadata?.id ?: return@launch
                    songRepository.scrobbleSong(mediaId, 0, 1f, ScrobbleEvent.START, false)
                }

                updatePrefetchQueue()
                extendQueueIfEnding()
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                updatePrefetchQueue()
                extendQueueIfEnding()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                updatePrefetchQueue()
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                updatePrefetchQueue()
                extendQueueIfEnding()
            }

            override fun onPlayerError(error: PlaybackException) {
                error.printStackTrace()
                Log.e("PLAYER", error.stackTraceToString())

                Toast.makeText(
                    this@ChoraMediaLibraryService,
                    PlaybackException.getErrorCodeName(error.errorCode),
                    Toast.LENGTH_SHORT
                ).show()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    streamOffset.positionMs = newPosition.positionMs
                }
                super.onPositionDiscontinuity(oldPosition, newPosition, reason)
            }
        })

        crossfader = Crossfader(
            exoPlayer,
            serviceMainScope,
            createFadeOutPlayer = {
                ExoPlayer.Builder(this)
                    .setSeekParameters(SeekParameters.EXACT)
                    .setMediaSourceFactory(createMediaSourceFactory(okHttpClient, crossfadeStreamOffset))
                    // The main player holds the audio focus.
                    .setAudioAttributes(AudioAttributes.DEFAULT, false)
                    .build()
                    .also { it.audioSessionId = equalizerManager.audioSessionId }
            },
            fadeOutStreamOffset = crossfadeStreamOffset,
            canSeek = ::canSeek,
            replayGain = { replayGain },
            onSongLeft = { mediaItem, positionMs ->
                // The song is cut before its end, it may not have reached the scrobble percentage yet.
                if (!playerScrobbled) {
                    playerScrobbled = true
                    scrobbleSubmission(mediaItem, positionMs)
                }
            }
        )

        val mainActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainActivityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        session = MediaLibrarySession.Builder(this, player, LibrarySessionCallback())
            .setId("AutoSession")
            .setSessionActivity(sessionActivityPendingIntent)
            .build()

        scrobbleJob = serviceMainScope.launch {
            while (isActive) {
                val duration = player.duration
                val mediaItem = player.currentMediaItem

                if (duration > 0 && !playerScrobbled) {
                    val currentPosition = player.currentPosition
                    val progress = (currentPosition * 100 / duration).toInt()
                    val scrobblePercentage = playbackSettingsManager.scrobblePercentFlow.first() * 10

                    if (progress >= scrobblePercentage && mediaItem != null) {
                        playerScrobbled = true
                        scrobbleSubmission(mediaItem, currentPosition)
                    }
                }
                delay(1000)
            }
        }

        serviceMainScope.launch {
            transcodeManager.transcodingConfigChangesFlow
                .distinctUntilChanged()
                .collect {
                    if (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING) {
                        if (player.currentTimeline.isEmpty.not()) {
                            updateTranscodingDuringPlayback()
                        }
                    }
                }
        }

        serviceMainScope.launch {
            playbackSettingsManager.prefetchMinutesFlow
                .distinctUntilChanged()
                .collect {
                    prefetchMinutes = it
                    updatePrefetchQueue()
                }
        }

        // Tracks kept while offline are only dropped once back online.
        serviceMainScope.launch {
            offlineManager.isOffline.collect { updatePrefetchQueue() }
        }

        serviceMainScope.launch {
            playbackSettingsManager.autoPlayFlow
                .distinctUntilChanged()
                .collect {
                    autoPlay = it
                    extendQueueIfEnding()
                }
        }

        serviceMainScope.launch {
            combine(
                playbackSettingsManager.crossfadeSecondsFlow,
                playbackSettingsManager.equalizerEnabledFlow
            ) { crossfadeSeconds, equalizerEnabled -> crossfadeSeconds to equalizerEnabled }
                .distinctUntilChanged()
                .collect { (crossfadeSeconds, equalizerEnabled) ->
                    crossfader.crossfadeMs = crossfadeSeconds * 1000L

                    // Offloaded playback skips audio effects and can't be faded smoothly.
                    val offloadMode = if (crossfadeSeconds > 0 || equalizerEnabled)
                        TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
                    else
                        TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setAudioOffloadPreferences(
                            TrackSelectionParameters.AudioOffloadPreferences.Builder()
                                .setAudioOffloadMode(offloadMode)
                                .build()
                        )
                        .build()
                }
        }

        Log.d("AA", "Initialized MediaLibraryService.")
    }

    // Set when the crossfade starts the end of a song transcoded on the fly.
    private val crossfadeStreamOffset = StreamOffset()

    /**
     * Plays `media://` tracks from the smart cache first, then from the pre-fetched tracks, then
     * from the provider. [streamOffset] is where a transcoded stream must start.
     */
    private fun createMediaSourceFactory(okHttpClient: OkHttpClient, streamOffset: StreamOffset): DefaultMediaSourceFactory {
        val resolvingDataSourceFactory = ResolvingDataSource.Factory(
            DefaultDataSource.Factory(this, OkHttpDataSource.Factory(okHttpClient)),
            object : ResolvingDataSource.Resolver {
                override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
                    val uri = dataSpec.uri
                    val scheme = dataSpec.uri.scheme

                    if (scheme != "media")
                        return dataSpec

                    val variant = currentStreamVariant()
                    val offset = if (variant.bitrate != "No Transcoding") streamOffset.positionMs?.div(1000)?.toInt() else null
                    if (offset != null) streamOffset.positionMs = null

                    return dataSpec.withUri(getStreamUrl(uri, variant, offset).toUri())
                }
            }
        )

        // Upcoming tracks pre-fetched in RAM are served before going to the network.
        val prefetchDataSourceFactory = PrefetchDataSource.Factory(resolvingDataSourceFactory, prefetcher) {
            // The whole file is available, so the transcoding offset hack must not apply to the next stream.
            streamOffset.positionMs = null
        }

        // Tracks stored on disk by the smart cache are played before anything else.
        val smartCacheDataSourceFactory = SmartCacheDataSource.Factory(prefetchDataSourceFactory, smartCacheManager) {
            streamOffset.positionMs = null
        }
        return DefaultMediaSourceFactory(smartCacheDataSourceFactory)
    }

    /** Whether the player can seek into [mediaItem]; streams transcoded on the fly can't be. */
    private fun canSeek(mediaItem: MediaItem): Boolean {
        val uri = mediaItem.localConfiguration?.uri ?: return true
        if (uri.scheme != "media" || currentStreamVariant().bitrate == "No Transcoding") return true
        return smartCacheManager.isCached(uri) || prefetcher.get(uri) != null
    }

    private fun scrobbleSubmission(mediaItem: MediaItem, positionMs: Long) {
        if (mediaItem.mediaMetadata.mediaType == MediaMetadata.MEDIA_TYPE_RADIO_STATION) return
        serviceIOScope.launch {
            songRepository.scrobbleSong(mediaItem.mediaMetadata.id ?: "", positionMs.toInt(), 1f,
                ScrobbleEvent.START , true)
        }
    }

    /**
     * With autoplay on, adds songs similar to the last song of the queue once it starts, so the
     * music keeps playing like a radio.
     */
    private fun extendQueueIfEnding() {
        if (!autoPlay || autoPlayJob?.isActive == true) return
        if (player.repeatMode != Player.REPEAT_MODE_OFF || player.hasNextMediaItem()) return
        val last = player.currentMediaItem ?: return
        if (last.mediaMetadata.mediaType == MediaMetadata.MEDIA_TYPE_RADIO_STATION) return
        if (last.mediaMetadata.getProvider()?.featureFlags?.contains(ProviderFeature.RADIO) != true) return

        autoPlayJob = serviceMainScope.launch {
            val songs = mixRepository.getInstantMix(last, AUTOPLAY_SONG_COUNT + 1).drop(1)
            // The queue may have changed while the songs were fetched.
            if (player.currentMediaItem?.mediaId != last.mediaId || player.hasNextMediaItem()) return@launch
            val queued = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }.toSet()
            player.addMediaItems(songs.filter { it.mediaId !in queued })
        }
    }

    private fun currentStreamVariant(): StreamVariant = runBlocking {
        StreamVariant(transcodeManager.currentBitrateFlow.first(), transcodeManager.currentFormatFlow.first())
    }

    private fun getStreamUrl(uri: Uri, variant: StreamVariant, offsetSeconds: Int? = null): String {
        val providerId = uri.authority ?: throw Exception("No provider for MediaItem")
        val songId = uri.lastPathSegment ?: throw Exception("No ID for MediaItem")

        val provider = MediaProviderManager.getProvider(providerId)
            ?: throw Exception("Can't get streamUrl for mediaitem $songId")

        return when {
            variant.bitrate == "No Transcoding" -> provider.getStreamUrl(songId, false)
            offsetSeconds != null -> provider.getStreamUrl(songId, true, variant.bitrate.toInt(), variant.format, offset = offsetSeconds)
            else -> provider.getStreamUrl(songId, true, variant.bitrate.toInt(), variant.format)
        }
    }

    /**
     * Sends the queue to the prefetcher: the next [prefetchMinutes] minutes of tracks are downloaded,
     * the current track and the previous [prefetchMinutes] / 2 minutes are kept. Main thread only.
     */
    private fun updatePrefetchQueue() {
        if (!::prefetcher.isInitialized) return

        val currentIndex = player.currentMediaItemIndex
        val timeline = player.currentTimeline
        if (prefetchMinutes <= 0 || timeline.isEmpty || currentIndex == C.INDEX_UNSET) {
            prefetcher.submit(null, emptyList(), emptyList())
            return
        }

        // Tracks in the smart cache are already on disk.
        fun MediaItem.toPrefetchItem() = localConfiguration?.uri?.takeIf { it.scheme == "media" && !smartCacheManager.isCached(it) }?.let { uri ->
            PrefetchItem(uri, mediaMetadata.artworkUri?.toString()?.takeIf { it.startsWith("http") })
        }

        // Repeat one would only ever return the current track.
        val repeatMode = if (player.repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF else player.repeatMode
        val shuffle = player.shuffleModeEnabled

        // Walks the queue in play order from the current track until budgetMs of tracks are collected.
        fun collect(budgetMs: Long, step: (Int) -> Int): List<PrefetchItem> {
            val visited = mutableSetOf(currentIndex)
            val items = mutableListOf<PrefetchItem>()
            var totalMs = 0L
            var index = currentIndex

            while (totalMs < budgetMs) {
                index = step(index)
                if (index == C.INDEX_UNSET || !visited.add(index)) break

                val item = player.getMediaItemAt(index)
                val prefetchItem = item.toPrefetchItem() ?: continue
                if (items.any { it.uri == prefetchItem.uri }) continue

                items += prefetchItem
                totalMs += item.mediaMetadata.durationMs?.takeIf { it > 0 } ?: DEFAULT_TRACK_DURATION_MS
            }
            return items
        }

        val budgetMs = prefetchMinutes * 60_000L
        val upcoming = collect(budgetMs) { timeline.getNextWindowIndex(it, repeatMode, shuffle) }
        val previous = collect(budgetMs / 2) { timeline.getPreviousWindowIndex(it, repeatMode, shuffle) }

        prefetcher.submit(player.currentMediaItem?.toPrefetchItem(), upcoming, previous)
    }

    /** The cover of a pre-fetched track, so it can be shown while offline. */
    fun getPrefetchedArtwork(url: String): ByteArray? =
        if (::prefetcher.isInitialized) prefetcher.getArtwork(url) else null

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (::prefetcher.isInitialized && (level == TRIM_MEMORY_RUNNING_CRITICAL || level == TRIM_MEMORY_COMPLETE)) {
            Log.d("PREFETCH", "Low memory, dropping pre-fetched tracks")
            prefetcher.clear()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return session
    }

    private inner class LibrarySessionCallback : MediaLibrarySession.Callback {
        override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
            serviceIOScope.launch {
                println("ONPOSTCONNTECT MUSIC SERVICE!")

                if (session.isAutoCompanionController(controller))
                    getHomeScreenItems()

                this@ChoraMediaLibraryService.session?.notifyChildrenChanged(
                    "nodeHOME",
                    aHomeScreenItems.size,
                    null
                )
            }
            super.onPostConnect(session, controller)
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>
        ): ListenableFuture<List<MediaItem>> {
            // Android Auto uses the legacy MediaController, so we need to do some very weird hacks to make it work nicely.

            // If only one item is requested, check if it belongs to a folder we've already loaded
            if (mediaItems.size == 1) {
                val requestedId = mediaItems[0].mediaId
                // Try to find the full item in the last browsed folder
                val fullItem = aFolderSongs.find { it.mediaId == requestedId }
                if (fullItem != null) {
                    val startIndex = aFolderSongs.indexOf(fullItem)
                    val folderQueue = aFolderSongs.subList(startIndex, aFolderSongs.size)
                    return Futures.immediateFuture(folderQueue)
                }

                // Not found in the current folder
                val cachedItem = aPlaylistScreenItems.find { it.mediaId == requestedId }
                    ?: aRadioScreenItems.find { it.mediaId == requestedId }
                    ?: aAlbumScreenItems.find { it.mediaId == requestedId }
                    ?: aArtistsScreenItems.find { it.mediaId == requestedId }

                if (cachedItem != null) {
                    return Futures.immediateFuture(listOf(cachedItem))
                }
            }

            return Futures.immediateFuture(mediaItems)
        }

        override fun onSetRating(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            rating: Rating
        ): ListenableFuture<SessionResult> {
            val currentItem = player.currentMediaItem
                ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_INVALID_STATE))

            val id = currentItem.mediaMetadata.id ?: ""
            val newRating = (rating as StarRating).starRating.toInt()

            runBlocking {
                songRepository.setSongRating(id, newRating)
            }

            val updatedItem = currentItem.buildUpon()
                .setMediaMetadata(
                    currentItem.mediaMetadata.buildUpon()
                        .setUserRating(rating)
                        .build()
                )
                .build()

            val index = player.currentMediaItemIndex
            if (player.currentMediaItem?.mediaMetadata?.id == id) {
                player.replaceMediaItem(index, updatedItem)
            }
            return super.onSetRating(session, controller, rating)
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }

        @OptIn(UnstableApi::class)
        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return Futures.immediateFuture(
                try {
                    LibraryResult.ofItemList(
                        when (parentId) {
                            "nodeROOT" -> rootHierarchy
                            "nodeHOME" -> getHomeScreenItems()
                            "nodeALBUMS" -> getAlbumScreenItems()
                            "nodeARTISTS" -> getArtistScreenItems()
                            "nodeRADIOS" -> getRadioItems()
                            "nodePLAYLISTS" -> getPlaylistItems()
                            else -> {
                                val mediaItem =
                                    aHomeScreenItems.find { it.mediaId == parentId }
                                        ?: aPlaylistScreenItems.find { it.mediaId == parentId }
                                        ?: aAlbumScreenItems.find { it.mediaId == parentId }
                                        ?: aArtistsScreenItems.find { it.mediaId == parentId }
                                getFolderItems(
                                    parentId,
                                    mediaItem?.mediaMetadata?.mediaType
                                        ?: MediaMetadata.MEDIA_TYPE_ALBUM
                                )
                            }
                        }, params)
                } catch (_: Exception) {
                    LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
                }
            )
        }


        @OptIn(UnstableApi::class)
        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val mediaItem = aFolderSongs.find { it.mediaId == mediaId }
                ?: aPlaylistScreenItems.find { it.mediaId == mediaId }
                ?: aRadioScreenItems.find { it.mediaId == mediaId }
                ?: return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))

            return Futures.immediateFuture(
                LibraryResult.ofItem(
                    mediaItem,
                    LibraryParams.Builder().build()
                )
            )
        }

        override fun onSubscribe(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            session.notifyChildrenChanged(
                parentId,
                when (parentId) {
                    "nodeROOT" -> 2
                    "nodeHOME" -> aHomeScreenItems.size
                    "nodeALBUMS" -> aAlbumScreenItems.size
                    "nodeARTISTS" -> aArtistsScreenItems.size
                    "nodeRADIOS" -> aRadioScreenItems.size
                    "nodePLAYLISTS" -> aPlaylistScreenItems.size
                    else -> 0
                },
                params
            )

            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isToStream: Boolean
        ): ListenableFuture<MediaItemsWithStartPosition> {
            val settable = SettableFuture.create<MediaItemsWithStartPosition>()
            serviceMainScope.launch {
                try {
                    val playbackResumptionList = withTimeout(2.seconds) {
                        LocalDataSettingsManager(applicationContext)
                            .playbackResumptionPlaylistWithStartPosition
                            .first()
                    }
                    withContext(Dispatchers.Main) {
                        player.setMediaItems(playbackResumptionList.mediaItems)
                        player.prepare()
                        player.playWhenReady = true
                        player.seekTo(playbackResumptionList.startIndex, playbackResumptionList.startPositionMs)
                    }
                    settable.set(playbackResumptionList)
                } catch (e: Exception) {
                    Log.e("RESUMPTION", "Failed/timed out getting resumption state", e)
                    settable.setException(e)
                }
            }
            return settable
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return Futures.immediateFuture(
                LibraryResult.ofItemList(
                    runBlocking {
                        songRepository.getSongs(MediaQuery.SongListQuery(sortBy = SongListSort.NAME, sortOrder = SortOrder.ASC, searchTerm = query, startIndex = 0)).toMutableList()
                    },
                    LibraryParams.Builder().build()
                )
            )
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            println("onSearch!!!")

            session.notifySearchResultChanged(
                browser,
                query,
                runBlocking {
                    songRepository.getSongs(MediaQuery.SongListQuery(sortBy = SongListSort.NAME, sortOrder = SortOrder.ASC, searchTerm = query, startIndex = 0)).size +
                            albumRepository.getAlbums(MediaQuery.AlbumListQuery(sortBy = AlbumListSort.NAME, sortOrder = SortOrder.ASC, searchTerm = query, startIndex = 0)).size +
                            radioRepository.getRadios().fastFilter {
                                it.name.contains(query)
                            }.map { it.toMediaItem() }.size +
                            playlistRepository.getPlaylists(MediaQuery.PlaylistListQuery(sortBy = PlaylistListSort.NAME, sortOrder = SortOrder.ASC, searchTerm = query, startIndex = 0)).fastFilter {
                                it.mediaMetadata.title?.contains(
                                    query
                                ) == true
                            }.size
                },
                LibraryParams.Builder().build()
            )

            return Futures.immediateFuture(LibraryResult.ofVoid())
        }
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel() // Cancel any previously running timer

        if (minutes <= 0) {
            Log.d("SLEEPTIMER", "Sleep timer cancelled.")
            _sleepTimerRemainingTime.value = 0
            return
        }

        Log.d("SLEEPTIMER", "Sleep timer set for $minutes minutes.")

        sleepTimerJob = serviceMainScope.launch {
            var timeRemaining = minutes
            _sleepTimerRemainingTime.value = timeRemaining

            while (timeRemaining > 0) {
                delay(60 * 1000L)
                timeRemaining--
                _sleepTimerRemainingTime.value = timeRemaining
            }

            if (::player.isInitialized && player.isPlaying) {
                player.stop()
                Log.d("SLEEPTIMER", "Timer finished. Playback stopped.")
            }
        }
    }

    private fun updateTranscodingDuringPlayback() {
        val currentWindowIndex = player.currentMediaItemIndex
        val currentPlaybackPosition = player.currentPosition
        val wasPlaying = player.isPlaying

        val currentQueue = mutableListOf<MediaItem>()
        for (i in 0 until player.mediaItemCount) {
            currentQueue.add(player.getMediaItemAt(i))
        }

        player.setMediaItems(currentQueue, currentWindowIndex, currentPlaybackPosition)

        player.prepare()

        if (wasPlaying) {
            player.play()
        }
    }

    override fun onDestroy() {
        saveState()
        session?.release()
        scrobbleJob?.cancel()
        sleepTimerJob?.cancel()
        if (::prefetcher.isInitialized) prefetcher.stop()
        if (::crossfader.isInitialized) crossfader.release()
        instance = null
        super.onDestroy()
    }

    fun saveState() {
        runBlocking {
            Log.d(
                "AA",
                "Saving state! Playlist: ${List(player.mediaItemCount) { i -> player.getMediaItemAt(i) }.map { it.mediaMetadata.title }}, current index: ${player.currentMediaItemIndex}, current position: ${player.currentPosition}"
            )

            LocalDataSettingsManager(applicationContext).setPlaybackResumption(
                List(player.mediaItemCount) { i ->
                    player.getMediaItemAt(i)
                },
                player.currentMediaItemIndex,
                player.currentPosition
            )
        }
    }

    //region getChildren
    private fun getHomeScreenItems(): MutableList<MediaItem> {
        println("GETTING ANDROID AUTO SCREEN ITEMS")
        runBlocking {
            if (aHomeScreenItems.isEmpty()) {
                val recentlyPlayedAlbums = async { albumRepository.getAlbums(MediaQuery.AlbumListQuery(sortBy = AlbumListSort.RECENTLY_PLAYED, sortOrder = SortOrder.DESC, limit = 6, startIndex = 0)) }.await()
                val mostPlayedAlbums = async { albumRepository.getAlbums(MediaQuery.AlbumListQuery(sortBy = AlbumListSort.PLAY_COUNT, sortOrder = SortOrder.DESC, limit = 6, startIndex = 0)) }.await()

                recentlyPlayedAlbums.forEach { album ->
                    aHomeScreenItems.add(
                        album.apply {
                            this.mediaMetadata.extras?.putString(
                                MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE,
                                this@ChoraMediaLibraryService.getString(R.string.home_recently_played)
                            )
                        }
                    )
                }

                mostPlayedAlbums.forEach { album ->
                    aHomeScreenItems.add(
                        album.apply {
                            this.mediaMetadata.extras?.putString(
                                MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE,
                                this@ChoraMediaLibraryService.getString(R.string.home_most_played)
                            )
                        }
                    )
                }
            }
        }
        return aHomeScreenItems
    }

    private fun getAlbumScreenItems() : MutableList<MediaItem> {
        println("GETTING ANDROID AUTO ALBUM SCREEN ITEMS")
        runBlocking {
            if (aAlbumScreenItems.isEmpty()) {
                while (true) {
                    val albums = async { albumRepository.getAlbums(MediaQuery.AlbumListQuery(sortBy = AlbumListSort.NAME, sortOrder = SortOrder.ASC, limit = 250, startIndex = aAlbumScreenItems.size)) }.await()
                    aAlbumScreenItems.addAll(albums)
                    if (albums.isEmpty()) {
                        break
                    }
                }
            }
        }
        return aAlbumScreenItems
    }

    private fun getArtistScreenItems() : MutableList<MediaItem> {
        println("GETTING ANDROID AUTO ARTIST SCREEN ITEMS")
        runBlocking {
            if (aArtistsScreenItems.isEmpty()) {
                val albums = async { artistRepository.getArtists(MediaQuery.AlbumArtistListQuery(sortBy = AlbumArtistListSort.NAME, sortOrder = SortOrder.ASC, startIndex = aArtistsScreenItems.size)) }.await()

                albums.forEach {
                    aArtistsScreenItems.add(it.toMediaItem())
                }
            }
        }
        return aArtistsScreenItems
    }

    private fun getRadioItems(): MutableList<MediaItem> {
        runBlocking {
            if (aRadioScreenItems.isEmpty()) {
                aRadioScreenItems.addAll(
                    radioRepository.getRadios().map { radio ->
                        Log.d("MediaItemTransition", radio.toString())
                        radio.toMediaItem()
                    }
                )
                Log.d("MediaItemTransition", "aRadioScreenItems: ${aRadioScreenItems.map { it.mediaMetadata }}")
            }
        }
        return aRadioScreenItems
    }

    private fun getPlaylistItems(): MutableList<MediaItem> {
        runBlocking {
            if (aPlaylistScreenItems.isEmpty()) {
                aPlaylistScreenItems.addAll(playlistRepository.getPlaylists(MediaQuery.PlaylistListQuery(sortBy = PlaylistListSort.NAME, sortOrder = SortOrder.ASC, startIndex = aPlaylistScreenItems.size)))
            }
        }
        return aPlaylistScreenItems
    }

    private fun getFolderItems(parentId: String, type: Int): MutableList<MediaItem> {
        runBlocking {
            aFolderSongs.clear()
            when (type) {
                MediaMetadata.MEDIA_TYPE_ALBUM -> {
                    val albumSongs = async { albumRepository.getAlbum(parentId) }.await()
                    aFolderSongs.addAll(
                        albumSongs?.subList(1, albumSongs.size) ?: emptyList()
                    )
                }

                MediaMetadata.MEDIA_TYPE_PLAYLIST -> {
                    aFolderSongs.addAll(
                        playlistRepository.getPlaylistSongs(parentId)
                    )
                }

                MediaMetadata.MEDIA_TYPE_ARTIST -> {
                    aFolderSongs.addAll(
                        artistRepository.getArtistAlbums(parentId)
                    )
                }

                else -> aFolderSongs.clear()
            }
        }

        return aFolderSongs
    }
    //endregion
}