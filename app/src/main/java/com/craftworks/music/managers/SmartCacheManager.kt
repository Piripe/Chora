package com.craftworks.music.managers

import android.content.Context
import android.content.res.Resources
import android.net.Uri
import android.text.format.DateUtils
import android.util.Log
import androidx.core.content.edit
import com.craftworks.music.R
import com.craftworks.music.data.cache.CachedArtwork
import com.craftworks.music.data.cache.CachedSong
import com.craftworks.music.data.cache.LibraryCache
import com.craftworks.music.data.cache.SmartCacheDatabase
import com.craftworks.music.data.model.AlbumArtistListSort
import com.craftworks.music.data.model.AlbumListSort
import com.craftworks.music.data.model.LibraryType
import com.craftworks.music.data.model.MediaModel
import com.craftworks.music.data.model.MediaQuery
import com.craftworks.music.data.model.SongListSort
import com.craftworks.music.data.model.SortOrder
import com.craftworks.music.data.providers.media.MediaProvider
import com.craftworks.music.managers.OfflineManager.Companion.requiresNetwork
import com.craftworks.music.managers.settings.PlaybackSettingsManager
import com.craftworks.music.player.SmartCacheService
import com.craftworks.music.player.StreamVariant
import com.craftworks.music.player.artworkKey
import com.craftworks.music.player.createStreamingHttpClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

enum class SmartCachePhase { IDLE, CHECKING, RANKING, CLEANING, DOWNLOADING }

sealed interface SmartCacheError {
    /** No provider, or one that doesn't need the network (local folders). */
    data object Unsupported : SmartCacheError
    data object Offline : SmartCacheError
    data object NoSpace : SmartCacheError
    data class Failed(val message: String?) : SmartCacheError
}

data class SmartCacheState(
    val phase: SmartCachePhase = SmartCachePhase.IDLE,
    val progress: Int = 0,
    val total: Int = 0,
    val usedBytes: Long = 0,
    val songCount: Int = 0,
    val lastRefresh: Long? = null,
    val lastError: SmartCacheError? = null,
) {
    val isRunning: Boolean get() = phase != SmartCachePhase.IDLE
}

/** What the smart cache is doing, or how its last refresh went. */
fun SmartCacheState.statusText(resources: Resources): String = when (phase) {
    SmartCachePhase.CHECKING -> resources.getString(R.string.smart_cache_status_checking, progress, total)
    SmartCachePhase.RANKING -> resources.getString(R.string.smart_cache_status_ranking, progress)
    SmartCachePhase.CLEANING -> resources.getString(R.string.smart_cache_status_cleaning)
    SmartCachePhase.DOWNLOADING -> resources.getString(R.string.smart_cache_status_downloading, progress, total)
    SmartCachePhase.IDLE -> when (val error = lastError) {
        SmartCacheError.Unsupported -> resources.getString(R.string.smart_cache_error_unsupported)
        SmartCacheError.Offline -> resources.getString(R.string.smart_cache_error_offline)
        SmartCacheError.NoSpace -> resources.getString(R.string.smart_cache_error_no_space)
        is SmartCacheError.Failed -> resources.getString(R.string.smart_cache_error_failed, error.message ?: "")
        null -> lastRefresh?.let {
            resources.getString(
                R.string.smart_cache_last_refresh,
                DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
            )
        } ?: resources.getString(R.string.smart_cache_refresh_description)
    }
}

/**
 * Keeps the most listened songs of the media provider on disk, so they play without the network.
 *
 * A refresh (started by the user) removes the cached songs that are no longer on the provider,
 * ranks the songs (most played on the provider and in the app, then the songs of the most played
 * albums, then of the most played artists) and downloads them in that order until the cache is full.
 * The audio files and covers are stored in the app's files, their metadata in [SmartCacheDatabase].
 * Cached songs are also added to the [LibraryCache], so they can be browsed offline after a restart.
 */
@Singleton
class SmartCacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: SmartCacheDatabase,
    private val libraryCache: LibraryCache,
    private val offlineManager: OfflineManager,
    private val playbackSettingsManager: PlaybackSettingsManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient by lazy { createStreamingHttpClient() }
    private val prefs by lazy { context.getSharedPreferences("SmartCache", Context.MODE_PRIVATE) }

    private val songsDir = File(context.filesDir, "smart_cache/songs")
    private val artworksDir = File(context.filesDir, "smart_cache/artworks")

    // Read from the playback and image loading threads, so kept in memory.
    private val songs = ConcurrentHashMap<String, CachedSong>()
    private val artworks = ConcurrentHashMap<String, CachedArtwork>()

    /** Guards every change to the cache content (database, files and the maps above). */
    private val mutex = Mutex()
    private val loaded = CompletableDeferred<Unit>()
    private var initialized = false

    private val _state = MutableStateFlow(SmartCacheState())
    val state: StateFlow<SmartCacheState> = _state.asStateFlow()

    @Volatile
    private var refreshJob: Job? = null

    private class RefreshException(val error: SmartCacheError, cause: Throwable? = null) : Exception(cause)

    fun init() {
        if (initialized) return
        initialized = true

        scope.launch {
            mutex.withLock { load() }
            loaded.complete(Unit)

            // Applies a new maximum size right away, without waiting for the next refresh.
            playbackSettingsManager.smartCacheMaxSizeGbFlow.distinctUntilChanged().collect { sizeGb ->
                if (sizeGb <= 0) {
                    refreshJob?.cancelAndJoin()
                    clear()
                } else {
                    trimToSize(sizeGb * BYTES_PER_GB)
                }
            }
        }
    }

    //region Lookups

    /** The file of a cached `media://` track, or null if it isn't cached. */
    fun getCachedFile(uri: Uri): File? {
        if (uri.scheme != "media" || songs.isEmpty()) return null
        val providerId = uri.authority ?: return null
        val songId = uri.lastPathSegment ?: return null
        return songs[key(providerId, songId)]?.let { File(songsDir, it.fileName) }
    }

    fun isCached(uri: Uri): Boolean = getCachedFile(uri) != null

    /** The cached cover for an artwork URL (at any size), or null. */
    fun getArtworkFile(url: String): File? {
        if (artworks.isEmpty() || !url.startsWith("http")) return null
        return artworks[artworkKey(url)]?.let { File(artworksDir, it.fileName) }?.takeIf { it.exists() }
    }

    //endregion

    /** Counts a song played in the app, so the most played ones are cached first. */
    fun recordPlay(providerId: String, songId: String) {
        if (songId.isEmpty()) return
        scope.launch { database.recordAppPlay(providerId, songId, System.currentTimeMillis()) }
    }

    //region Refresh

    fun refresh() {
        if (refreshJob?.isActive == true) return
        // Set before the job starts, so the service doesn't see an idle cache and stop right away.
        _state.update { it.copy(phase = SmartCachePhase.CHECKING, progress = 0, total = 0, lastError = null) }
        refreshJob = scope.launch { runRefresh() }
        SmartCacheService.start(context)
    }

    fun cancelRefresh() {
        refreshJob?.cancel()
    }

    private suspend fun runRefresh() {
        var error: SmartCacheError? = null
        try {
            loaded.await()
            refreshCurrentProvider()
            prefs.edit { putLong(LAST_REFRESH_KEY, System.currentTimeMillis()) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: RefreshException) {
            Log.w(TAG, "Refresh stopped: ${e.error}", e.cause)
            error = e.error
        } catch (e: Exception) {
            Log.e(TAG, "Refresh failed", e)
            error = SmartCacheError.Failed(e.message)
        } finally {
            _state.update {
                it.copy(
                    phase = SmartCachePhase.IDLE,
                    progress = 0,
                    total = 0,
                    lastError = error,
                    lastRefresh = prefs.getLong(LAST_REFRESH_KEY, 0).takeIf { time -> time > 0 }
                )
            }
        }
    }

    private suspend fun refreshCurrentProvider() {
        val provider = MediaProviderManager.currentProvider.value
        if (provider == null || !provider.requiresNetwork()) throw RefreshException(SmartCacheError.Unsupported)
        if (offlineManager.isOffline.value) throw RefreshException(SmartCacheError.Offline)

        val maxBytes = playbackSettingsManager.smartCacheMaxSizeGbFlow.first() * BYTES_PER_GB
        if (maxBytes <= 0) return

        // Cached songs are always played as is, so they are downloaded at the best quality the user streams at.
        val variant = cacheVariant()

        removeUnavailableSongs(provider)

        // Songs of other providers keep their space, they are only dropped when the cache is over its size.
        val otherProvidersBytes = songs.values.filter { it.providerId != provider.id }.sumOf { it.sizeBytes }
        val ranking = rankSongs(provider, (maxBytes - otherProvidersBytes).coerceAtLeast(0), variant)

        applyRanking(provider, ranking)
        downloadSongs(provider, ranking, maxBytes, variant)
        trimToSize(maxBytes)
    }

    /** Removes the cached songs the provider no longer has, and refreshes the metadata of the others. */
    private suspend fun removeUnavailableSongs(provider: MediaProvider) {
        val cached = songs.values.filter { it.providerId == provider.id }
        if (cached.isEmpty()) return
        setPhase(SmartCachePhase.CHECKING, total = cached.size)

        val removed = mutableListOf<CachedSong>()
        val updated = mutableListOf<CachedSong>()
        var checked = 0

        // One request per album instead of one per song.
        for ((albumId, albumSongs) in cached.groupBy { it.song.albumId }) {
            val album = fetchOrNull(provider) { it.getAlbumDetail(albumId) }
            val available = album?.songs?.associateBy { it.id }.orEmpty()

            for (cachedSong in albumSongs) {
                // The song may have moved to another album.
                val fresh = available[cachedSong.songId]
                    ?: fetchOrNull(provider) { it.getSongDetail(cachedSong.songId) }
                if (fresh == null) removed += cachedSong else updated += cachedSong.copy(song = fresh)
                checked++
                _state.update { it.copy(progress = checked) }
            }
        }

        Log.d(TAG, "${removed.size} cached songs are no longer available")
        mutex.withLock {
            database.updateCachedSongs(updated)
            updated.forEach { songs[key(it)] = it }
            deleteSongsLocked(removed)
        }
        libraryCache.putSongs(provider.id, updated.map { it.song })
        updateUsage()
    }

    /**
     * The songs to cache, in order: the most played songs on the provider and in the app
     * (alternately), then the songs of the most played albums, then of the most played artists.
     * Stops once the songs fill [budgetBytes].
     */
    private suspend fun rankSongs(
        provider: MediaProvider,
        budgetBytes: Long,
        variant: StreamVariant
    ): Map<String, MediaModel.Song> {
        setPhase(SmartCachePhase.RANKING)

        val ranking = LinkedHashMap<String, MediaModel.Song>()
        var rankedBytes = 0L
        var consecutiveMisses = 0

        fun isFull() = rankedBytes >= budgetBytes || consecutiveMisses >= MAX_CONSECUTIVE_MISSES

        fun add(song: MediaModel.Song) {
            if (isFull() || song.id in ranking) return
            val size = songs[key(provider.id, song.id)]?.sizeBytes ?: estimateSize(song, variant)
            // A song too big for the remaining space is skipped, smaller ones may still fit.
            if (rankedBytes + size > budgetBytes) {
                consecutiveMisses++
                return
            }
            consecutiveMisses = 0
            ranking[song.id] = song
            rankedBytes += size
            _state.update { it.copy(progress = ranking.size) }
        }

        // 1. Most played songs, on the provider and in the app.
        val providerTopSongs = if (SongListSort.PLAY_COUNT in provider.supportedSongSort) {
            Pager<MediaModel.Song>(SONG_PAGE_SIZE, { it.id }) { start ->
                provider.getSongList(
                    MediaQuery.SongListQuery(
                        sortBy = SongListSort.PLAY_COUNT,
                        sortOrder = SortOrder.DESC,
                        limit = SONG_PAGE_SIZE,
                        startIndex = start
                    )
                )
            }
        } else null
        val appTopSongIds = database.getMostPlayedSongIds(provider.id)

        var providerSongsDone = providerTopSongs == null
        var appIndex = 0
        while (!isFull() && (!providerSongsDone || appIndex < appTopSongIds.size)) {
            if (!providerSongsDone) {
                val song = providerTopSongs?.next(provider)
                // Sorted by play count: the first song never played ends the list.
                if (song == null || (song.playCount ?: 0) <= 0) providerSongsDone = true
                else add(song)
            }
            if (appIndex < appTopSongIds.size) {
                resolveSong(provider, appTopSongIds[appIndex++])?.let(::add)
            }
        }

        // 2. Songs of the most played albums.
        val processedAlbums = mutableSetOf<String>()
        suspend fun addAlbum(albumId: String) {
            if (isFull() || !processedAlbums.add(albumId)) return
            val album = fetchOrNull(provider) { it.getAlbumDetail(albumId) } ?: return
            libraryCache.putAlbums(provider.id, listOf(album))
            album.songs
                .sortedWith(compareBy({ it.discNumber }, { it.trackNumber }))
                .forEach(::add)
        }

        if (AlbumListSort.PLAY_COUNT in provider.supportedAlbumSort) {
            val topAlbums = Pager<MediaModel.Album>(ALBUM_PAGE_SIZE, { it.id }) { start ->
                provider.getAlbumList(
                    MediaQuery.AlbumListQuery(
                        sortBy = AlbumListSort.PLAY_COUNT,
                        sortOrder = SortOrder.DESC,
                        limit = ALBUM_PAGE_SIZE,
                        startIndex = start
                    )
                )
            }
            while (!isFull()) {
                val album = topAlbums.next(provider) ?: break
                if ((album.playCount ?: 0.0) <= 0.0) break
                addAlbum(album.id)
            }
        }

        // 3. Albums of the most played artists, their most played albums first.
        if (AlbumArtistListSort.PLAY_COUNT in provider.supportedAlbumArtistSort) {
            val topArtists = Pager<MediaModel.Artist>(ARTIST_PAGE_SIZE, { it.id }) { start ->
                provider.getAlbumArtistList(
                    MediaQuery.AlbumArtistListQuery(
                        sortBy = AlbumArtistListSort.PLAY_COUNT,
                        sortOrder = SortOrder.DESC,
                        limit = ARTIST_PAGE_SIZE,
                        startIndex = start
                    )
                )
            }
            while (!isFull()) {
                val artist = topArtists.next(provider) ?: break
                if ((artist.playCount ?: 0.0) <= 0.0) break
                val albums = fetchOrNull(provider) { it.getAlbumArtistDetail(artist.id).albums }.orEmpty()
                for (album in albums.sortedByDescending { it.playCount ?: 0.0 }) {
                    if (isFull()) break
                    addAlbum(album.id)
                }
            }
        }

        Log.d(TAG, "Ranked ${ranking.size} songs (~$rankedBytes bytes of $budgetBytes)")
        return ranking
    }

    /** Drops the cached songs that are no longer ranked, and stores the new ranks. */
    private suspend fun applyRanking(provider: MediaProvider, ranking: Map<String, MediaModel.Song>) {
        setPhase(SmartCachePhase.CLEANING)
        val ranks = ranking.keys.withIndex().associate { (index, id) -> id to index }

        mutex.withLock {
            val (kept, dropped) = songs.values
                .filter { it.providerId == provider.id }
                .partition { it.songId in ranks }
            val updated = kept.map { it.copy(rank = ranks.getValue(it.songId), song = ranking.getValue(it.songId)) }

            database.updateCachedSongs(updated)
            updated.forEach { songs[key(it)] = it }
            deleteSongsLocked(dropped)
            Log.d(TAG, "Dropped ${dropped.size} songs that are no longer ranked")
        }
        updateUsage()
    }

    private suspend fun downloadSongs(
        provider: MediaProvider,
        ranking: Map<String, MediaModel.Song>,
        maxBytes: Long,
        variant: StreamVariant
    ) {
        val missing = ranking.values.withIndex().filter { (_, song) -> !songs.containsKey(key(provider.id, song.id)) }
        setPhase(SmartCachePhase.DOWNLOADING, total = missing.size)

        missing.forEachIndexed { done, (rank, song) ->
            currentCoroutineContext().ensureActive()
            val estimate = estimateSize(song, variant)
            if (usedBytes() + estimate <= maxBytes) {
                if (songsDir.usableSpace < estimate + MIN_FREE_SPACE_BYTES) throw RefreshException(SmartCacheError.NoSpace)
                downloadSong(provider, song, rank, variant)
            }
            _state.update { it.copy(progress = done + 1) }
        }
    }

    private suspend fun downloadSong(provider: MediaProvider, song: MediaModel.Song, rank: Int, variant: StreamVariant) {
        val url = try {
            if (variant.bitrate == NO_TRANSCODING) provider.getStreamUrl(song.id, false)
            else provider.getStreamUrl(song.id, true, variant.bitrate.toIntOrNull(), variant.format)
        } catch (e: Exception) {
            Log.w(TAG, "Can't get the stream url of ${song.id}", e)
            return
        }

        val fileName = UUID.randomUUID().toString()
        val partFile = File(songsDir, "$fileName.part")
        val size = download(url, partFile, image = false)
        if (size == null) {
            if (!provider.isReachable()) throw RefreshException(SmartCacheError.Offline)
            return
        }
        if (!partFile.renameTo(File(songsDir, fileName))) {
            partFile.delete()
            return
        }

        val artworkKey = song.imageId?.let { cacheArtwork(provider, it) }
        val cached = CachedSong(
            providerId = provider.id,
            song = song,
            fileName = fileName,
            sizeBytes = size,
            bitrate = variant.bitrate,
            format = variant.format,
            artworkKey = artworkKey,
            rank = rank,
            cachedAt = System.currentTimeMillis(),
        )
        mutex.withLock {
            database.putCachedSong(cached)
            songs[key(cached)] = cached
        }
        publishToLibraryCache(listOf(cached))
        updateUsage()
    }

    /** Downloads the cover of a song if not cached yet, and returns its key. */
    private suspend fun cacheArtwork(provider: MediaProvider, imageId: String): String? {
        // Same URL as the one shown by the player, see MediaModel.Song.toMediaItem.
        val url = provider.getImageUrl(imageId, LibraryType.SONG, 600)
        val key = artworkKey(url)
        if (artworks.containsKey(key)) return key

        val fileName = UUID.randomUUID().toString()
        val file = File(artworksDir, fileName)
        val size = download(url, file, image = true) ?: return null

        val artwork = CachedArtwork(key, fileName, size)
        mutex.withLock {
            database.putCachedArtwork(artwork)
            artworks[key] = artwork
        }
        return key
    }

    /** Downloads [url] into [target], returning its size, or null (and no file) if it failed. */
    private suspend fun download(url: String, target: File, image: Boolean): Long? {
        val call = httpClient.newCall(Request.Builder().url(url).build())
        val size = try {
            call.execute().use { response ->
                val body = response.body ?: return@use null
                val contentType = body.contentType()
                // Subsonic servers report errors as XML/JSON with a 200 status.
                val valid = response.isSuccessful && if (image) contentType?.type == "image"
                    else contentType?.subtype?.let { "xml" in it || "json" in it } != true
                if (!valid) {
                    Log.w(TAG, "Can't download $url: HTTP ${response.code} ($contentType)")
                    return@use null
                }

                var total = 0L
                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            total += read
                        }
                    }
                }
                total.takeIf { it > 0 }
            }
        } catch (e: CancellationException) {
            call.cancel()
            target.delete()
            throw e
        } catch (e: Exception) {
            call.cancel()
            Log.w(TAG, "Can't download $url", e)
            null
        }

        if (size == null) target.delete()
        return size
    }

    //endregion

    //region Pre-fetched tracks

    /**
     * Offers a track the player pre-fetched (fully downloaded, in RAM) to the smart cache.
     * It is kept if the cache has room for it, or if it was played more than the least played
     * cached songs, which are then dropped to make room. [write] copies the track to a stream.
     */
    fun offerPrefetchedTrack(uri: Uri, variant: StreamVariant, sizeBytes: Long, write: (OutputStream) -> Unit) {
        // A refresh decides what to keep on its own.
        if (!loaded.isCompleted || refreshJob?.isActive == true || uri.scheme != "media") return
        val providerId = uri.authority ?: return
        val songId = uri.lastPathSegment ?: return
        if (songs.containsKey(key(providerId, songId))) return

        scope.launch {
            try {
                addPrefetchedTrack(providerId, songId, variant, sizeBytes, write)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Can't add pre-fetched track $songId", e)
            }
        }
    }

    private suspend fun addPrefetchedTrack(
        providerId: String,
        songId: String,
        variant: StreamVariant,
        sizeBytes: Long,
        write: (OutputStream) -> Unit
    ) {
        val provider = MediaProviderManager.getProvider(providerId)?.takeIf { it.requiresNetwork() } ?: return
        val maxBytes = playbackSettingsManager.smartCacheMaxSizeGbFlow.first() * BYTES_PER_GB
        if (maxBytes <= 0 || sizeBytes > maxBytes) return
        // Cached files are always played as is: only keep tracks at the quality a refresh would download.
        if (!variant.sameQualityAs(cacheVariant())) return

        val song = libraryCache.getSong(providerId, songId)
            ?: try {
                provider.getSongDetail(songId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Can't get the metadata of pre-fetched track $songId", e)
                return
            }

        val playCounts = database.getAppPlayCounts(providerId)
        val plays = playScore(song, playCounts)
        // Cheap check first, to avoid writing the file for nothing. Done again below, under the lock.
        if (evictionsFor(providerId, plays, sizeBytes, maxBytes, playCounts) == null) return
        if (songsDir.usableSpace < sizeBytes + MIN_FREE_SPACE_BYTES) return

        val fileName = UUID.randomUUID().toString()
        val partFile = File(songsDir, "$fileName.part")
        val file = File(songsDir, fileName)
        try {
            partFile.outputStream().use(write)
        } catch (e: IOException) {
            partFile.delete()
            throw e
        }
        if (!partFile.renameTo(file)) {
            partFile.delete()
            return
        }

        val artworkKey = song.imageId?.let { cacheArtwork(provider, it) }

        val added: CachedSong? = mutex.withLock {
            val evicted = if (songs.containsKey(key(providerId, songId)) || refreshJob?.isActive == true) null
                else evictionsFor(providerId, plays, sizeBytes, maxBytes, playCounts)
            if (evicted == null) {
                deleteUnusedArtworksLocked()
                return@withLock null
            }
            deleteSongsLocked(evicted)

            val cached = CachedSong(
                providerId = providerId,
                song = song,
                fileName = fileName,
                sizeBytes = sizeBytes,
                bitrate = variant.bitrate,
                format = variant.format,
                artworkKey = artworkKey,
                rank = rankFor(providerId, plays, playCounts),
                cachedAt = System.currentTimeMillis(),
            )
            database.putCachedSong(cached)
            songs[key(cached)] = cached
            Log.d(TAG, "Kept pre-fetched track $songId ($plays plays), dropped ${evicted.size} songs")
            cached
        }

        if (added != null) {
            publishToLibraryCache(listOf(added))
            updateUsage()
        } else {
            file.delete()
        }
    }

    /**
     * The cached songs to drop so a song played [plays] times fits, least played first:
     * none if there is room, null if it can't fit without dropping songs played as much or more.
     */
    private fun evictionsFor(
        providerId: String,
        plays: Int,
        sizeBytes: Long,
        maxBytes: Long,
        playCounts: Map<String, Int>
    ): List<CachedSong>? {
        var freeBytes = maxBytes - usedBytes()
        if (freeBytes >= sizeBytes) return emptyList()

        val evicted = mutableListOf<CachedSong>()
        val lessPlayed = songs.values
            .filter { it.providerId == providerId }
            .map { it to playScore(it.song, playCounts) }
            .filter { (_, cachedPlays) -> cachedPlays < plays }
            .sortedWith(compareBy<Pair<CachedSong, Int>> { it.second }.thenByDescending { it.first.rank })
        for ((cached, _) in lessPlayed) {
            evicted += cached
            freeBytes += cached.sizeBytes
            if (freeBytes >= sizeBytes) return evicted
        }
        return null
    }

    /** Places a song played [plays] times just before the cached songs played less. */
    private fun rankFor(providerId: String, plays: Int, playCounts: Map<String, Int>): Int {
        val providerSongs = songs.values.filter { it.providerId == providerId }
        return providerSongs.filter { playScore(it.song, playCounts) < plays }.minOfOrNull { it.rank }
            ?: providerSongs.maxOfOrNull { it.rank + 1 }
            ?: 0
    }

    private suspend fun cacheVariant() = StreamVariant(
        playbackSettingsManager.wifiTranscodingBitrateFlow.first(),
        playbackSettingsManager.transcodingFormatFlow.first()
    )

    //endregion

    //region Size management

    /** Drops songs until the cache fits in [maxBytes]: other providers' songs first, then the least listened. */
    private suspend fun trimToSize(maxBytes: Long) {
        loaded.await()
        mutex.withLock {
            var used = usedBytes()
            if (used <= maxBytes) return@withLock

            val currentProviderId = MediaProviderManager.currentProvider.value?.id
            val dropped = mutableListOf<CachedSong>()
            for (cached in songs.values.sortedWith(
                compareBy<CachedSong> { it.providerId == currentProviderId }.thenByDescending { it.rank }
            )) {
                if (used <= maxBytes) break
                dropped += cached
                used -= cached.sizeBytes
            }
            Log.d(TAG, "Over the maximum size, dropping ${dropped.size} songs")
            deleteSongsLocked(dropped)
        }
        updateUsage()
    }

    /** Deletes every cached song and cover. */
    suspend fun clear() {
        loaded.await()
        mutex.withLock {
            if (songs.isEmpty() && artworks.isEmpty()) return@withLock
            deleteSongsLocked(songs.values.toList())
        }
        updateUsage()
    }

    private fun deleteSongsLocked(dropped: Collection<CachedSong>) {
        if (dropped.isEmpty()) return
        database.deleteCachedSongs(dropped.toList())
        dropped.forEach {
            songs.remove(key(it))
            File(songsDir, it.fileName).delete()
        }

        deleteUnusedArtworksLocked()
    }

    /** Covers are shared by the songs of an album, only drop the ones no song uses anymore. */
    private fun deleteUnusedArtworksLocked() {
        val usedArtworks = songs.values.mapNotNullTo(HashSet()) { it.artworkKey }
        val unusedArtworks = artworks.keys.filter { it !in usedArtworks }
        database.deleteCachedArtworks(unusedArtworks)
        unusedArtworks.forEach { key ->
            artworks.remove(key)?.let { File(artworksDir, it.fileName).delete() }
        }
    }

    private fun usedBytes(): Long =
        songs.values.sumOf { it.sizeBytes } + artworks.values.sumOf { it.sizeBytes }

    private fun updateUsage() {
        _state.update { it.copy(usedBytes = usedBytes(), songCount = songs.size) }
    }

    //endregion

    /** Loads the cache index, dropping entries without a file and files without an entry. */
    private fun load() {
        songsDir.mkdirs()
        artworksDir.mkdirs()

        val (cachedSongs, missingSongs) = database.getCachedSongs().partition { File(songsDir, it.fileName).exists() }
        database.deleteCachedSongs(missingSongs)
        val (cachedArtworks, missingArtworks) = database.getCachedArtworks().partition { File(artworksDir, it.fileName).exists() }
        database.deleteCachedArtworks(missingArtworks.map { it.key })

        // Interrupted downloads (.part) and files of deleted entries.
        val songFiles = cachedSongs.mapTo(HashSet()) { it.fileName }
        songsDir.listFiles()?.filter { it.name !in songFiles }?.forEach { it.delete() }
        val artworkFiles = cachedArtworks.mapTo(HashSet()) { it.fileName }
        artworksDir.listFiles()?.filter { it.name !in artworkFiles }?.forEach { it.delete() }

        cachedSongs.forEach { songs[key(it)] = it }
        cachedArtworks.forEach { artworks[it.key] = it }
        publishToLibraryCache(cachedSongs)

        _state.update {
            it.copy(lastRefresh = prefs.getLong(LAST_REFRESH_KEY, 0).takeIf { time -> time > 0 })
        }
        updateUsage()
        Log.d(TAG, "Loaded ${songs.size} cached songs (${usedBytes()} bytes)")
    }

    /** Makes the cached songs, and the albums and artists they belong to, browsable offline. */
    private fun publishToLibraryCache(cached: Collection<CachedSong>) {
        cached.groupBy { it.providerId }.forEach { (providerId, providerSongs) ->
            val models = providerSongs.map { it.song }
            libraryCache.putSongs(providerId, models)
            libraryCache.addMissingAlbums(providerId, models.groupBy { it.albumId }.map { (albumId, albumSongs) ->
                val first = albumSongs.first()
                MediaModel.Album(
                    id = albumId,
                    providerId = first.providerId,
                    providerType = first.providerType,
                    albumArtistName = first.albumArtistName ?: first.artistName,
                    artists = first.albumArtists,
                    durationMs = albumSongs.sumOf { it.durationMs },
                    genres = first.genres,
                    imageId = first.imageId,
                    name = first.album ?: "",
                    releaseYear = first.releaseYear,
                    songs = albumSongs,
                )
            })
            libraryCache.addMissingArtists(providerId, models.flatMap { it.albumArtists.ifEmpty { it.artists } }.distinctBy { it.id })
        }
    }

    private suspend fun resolveSong(provider: MediaProvider, songId: String): MediaModel.Song? =
        songs[key(provider.id, songId)]?.song
            ?: libraryCache.getSong(provider.id, songId)
            ?: fetchOrNull(provider) { it.getSongDetail(songId) }

    /**
     * Runs [block], returning null if the item is not on the provider.
     * Throws if the provider can't be reached, so a network failure never deletes cached songs.
     */
    private suspend fun <T> fetchOrNull(provider: MediaProvider, block: suspend (MediaProvider) -> T?): T? = try {
        block(provider)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (!provider.isReachable()) throw RefreshException(SmartCacheError.Offline, e)
        Log.d(TAG, "Item not available on the provider", e)
        null
    }

    private suspend fun MediaProvider.isReachable(): Boolean = try {
        getMusicFolderList()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    private fun setPhase(phase: SmartCachePhase, total: Int = 0) {
        _state.update { it.copy(phase = phase, progress = 0, total = total) }
    }

    /** Iterates over a paginated provider list. */
    private inner class Pager<T>(
        private val pageSize: Int,
        private val id: (T) -> String,
        private val fetch: suspend (startIndex: Int) -> List<T>,
    ) {
        private val buffer = ArrayDeque<T>()
        private val seen = HashSet<String>()
        private var nextIndex = 0
        private var done = false

        suspend fun next(provider: MediaProvider): T? {
            currentCoroutineContext().ensureActive()
            if (buffer.isEmpty() && !done) {
                val page = try {
                    fetch(nextIndex)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (!provider.isReachable()) throw RefreshException(SmartCacheError.Offline, e)
                    throw e
                }
                nextIndex += pageSize
                // Some providers ignore the start index: stop once a page brings nothing new.
                val fresh = page.filter { seen.add(id(it)) }
                if (fresh.isEmpty()) done = true
                buffer.addAll(fresh)
            }
            return buffer.removeFirstOrNull()
        }
    }

    companion object {
        private const val TAG = "SmartCache"
        private const val LAST_REFRESH_KEY = "lastRefresh"
        private const val NO_TRANSCODING = "No Transcoding"

        const val BYTES_PER_GB = 1_000_000_000L
        private const val MIN_FREE_SPACE_BYTES = 500_000_000L
        private const val BUFFER_SIZE = 64 * 1024

        private const val SONG_PAGE_SIZE = 200
        private const val ALBUM_PAGE_SIZE = 100
        private const val ARTIST_PAGE_SIZE = 100
        private const val MAX_CONSECUTIVE_MISSES = 50

        private fun key(providerId: String, songId: String) = "$providerId/$songId"
        private fun key(cached: CachedSong) = key(cached.providerId, cached.songId)

        /** How many times a song was played: on the provider, or in the app if more (e.g. not scrobbled yet). */
        private fun playScore(song: MediaModel.Song, appPlayCounts: Map<String, Int>): Int =
            maxOf(song.playCount ?: 0, appPlayCounts[song.id] ?: 0)

        /** The format doesn't matter when the original file is streamed. */
        private fun StreamVariant.sameQualityAs(other: StreamVariant): Boolean =
            bitrate == other.bitrate && (bitrate == NO_TRANSCODING || format == other.format)

        /** The expected file size of [song] downloaded with [variant]. */
        private fun estimateSize(song: MediaModel.Song, variant: StreamVariant): Long {
            val seconds = song.durationMs / 1000.0
            val sourceKbps = song.bitRate?.takeIf { it > 0 }
            return if (variant.bitrate == NO_TRANSCODING) {
                song.size?.toLong()?.takeIf { it > 0 } ?: ((sourceKbps ?: 320) * 125 * seconds).toLong()
            } else {
                val kbps = variant.bitrate.toIntOrNull() ?: 320
                (minOf(kbps, sourceKbps ?: kbps) * 125 * seconds).toLong()
            }
        }
    }
}
