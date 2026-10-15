package com.craftworks.music.managers

import android.content.Context
import android.content.res.Resources
import android.net.Uri
import android.text.format.DateUtils
import android.util.Log
import androidx.core.content.edit
import com.craftworks.music.R
import com.craftworks.music.data.cache.CachedArtist
import com.craftworks.music.data.cache.CachedArtwork
import com.craftworks.music.data.cache.CachedPlaylist
import com.craftworks.music.data.cache.CachedSong
import com.craftworks.music.data.cache.LibraryCache
import com.craftworks.music.data.cache.ListeningScore
import com.craftworks.music.data.cache.SmartCacheDatabase
import com.craftworks.music.data.model.AlbumArtistInfo
import com.craftworks.music.data.model.AlbumArtistListSort
import com.craftworks.music.data.model.AlbumListSort
import com.craftworks.music.data.model.LibraryType
import com.craftworks.music.data.model.MediaModel
import com.craftworks.music.data.model.MediaQuery
import com.craftworks.music.data.model.PlaylistListSort
import com.craftworks.music.data.model.ProviderFeature
import com.craftworks.music.data.model.SongListSort
import com.craftworks.music.data.model.SortOrder
import com.craftworks.music.data.model.parseDateMillis
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
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
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

enum class SmartCachePhase { IDLE, CHECKING, RANKING, CLEANING, DOWNLOADING, LIBRARY }

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
    SmartCachePhase.LIBRARY -> resources.getString(R.string.smart_cache_status_library, progress, total)
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
 * ranks the songs by [ListeningScore], where recent plays count more than old ones (the songs
 * listened to lately, then the songs of the albums and artists listened to lately, then the same
 * for older plays) and downloads them in that order until the cache is full. It then saves the
 * artists of the cached songs (with their picture, biography and similar artists) and the playlists
 * holding cached songs, so they can be browsed and searched offline too.
 * The audio files and covers are stored in the app's files, their metadata in [SmartCacheDatabase].
 * Cached songs, artists and playlists are also added to the [LibraryCache], so they can be browsed
 * offline after a restart.
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
    private val artists = ConcurrentHashMap<String, CachedArtist>()
    private val playlists = ConcurrentHashMap<String, CachedPlaylist>()
    private val artworkDownloads = ConcurrentHashMap<String, CompletableDeferred<String?>>()

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

    /** Counts a song played in the app, so the songs listened to the most lately are cached first. */
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
        cacheArtistsAndPlaylists(provider)
        trimToSize(maxBytes)
    }

    /** Removes the cached songs the provider no longer has, and refreshes the metadata of the others. */
    private suspend fun removeUnavailableSongs(provider: MediaProvider) {
        val cached = songs.values.filter { it.providerId == provider.id }
        if (cached.isEmpty()) return
        setPhase(SmartCachePhase.CHECKING, total = cached.size)

        val removed = ConcurrentLinkedQueue<CachedSong>()
        val updated = ConcurrentLinkedQueue<CachedSong>()

        // One request per album instead of one per song.
        forEachParallel(cached.groupBy { it.song.albumId }.entries, METADATA_PARALLELISM) { (albumId, albumSongs) ->
            val album = fetchOrNull(provider) { it.getAlbumDetail(albumId) }
            val available = album?.songs?.associateBy { it.id }.orEmpty()

            for (cachedSong in albumSongs) {
                // The song may have moved to another album.
                val fresh = available[cachedSong.songId]
                    ?: fetchOrNull(provider) { it.getSongDetail(cachedSong.songId) }
                if (fresh == null) removed += cachedSong else updated += cachedSong.copy(song = fresh)
                _state.update { it.copy(progress = it.progress + 1) }
            }
        }

        Log.d(TAG, "${removed.size} cached songs are no longer available")
        mutex.withLock {
            database.updateCachedSongs(updated.toList())
            updated.forEach { songs[key(it)] = it }
            deleteSongsLocked(removed)
        }
        libraryCache.putSongs(provider.id, updated.map { it.song })
        updateUsage()
    }

    /**
     * The songs to cache, by [ListeningScore] (plays on the provider and in the app, recent ones
     * counting more): first the songs listened to lately, then the songs of the albums and of the
     * artists listened to lately, then the same for the ones listened to longer ago.
     * Stops once the songs fill [budgetBytes].
     */
    private suspend fun rankSongs(
        provider: MediaProvider,
        budgetBytes: Long,
        variant: StreamVariant
    ): Map<String, MediaModel.Song> {
        setPhase(SmartCachePhase.RANKING)
        val now = System.currentTimeMillis()

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

        suspend fun addSongs(songIds: List<Scored<String>>, candidates: Map<String, MediaModel.Song>) {
            for ((songId) in songIds) {
                if (isFull()) break
                (candidates[songId] ?: resolveSong(provider, songId))?.let(::add)
            }
        }

        val processedAlbums = mutableSetOf<String>()
        suspend fun addAlbum(albumId: String) {
            if (isFull() || !processedAlbums.add(albumId)) return
            val album = fetchOrNull(provider) { it.getAlbumDetail(albumId) } ?: return
            libraryCache.putAlbums(provider.id, listOf(album))
            album.songs
                .sortedWith(compareBy({ it.discNumber }, { it.trackNumber }))
                .forEach(::add)
        }

        suspend fun addAlbums(albums: List<Scored<MediaModel.Album>>) {
            for ((album) in albums) {
                if (isFull()) break
                addAlbum(album.id)
            }
        }

        // The artists' albums listened to the most lately first.
        suspend fun addArtists(artists: List<Scored<MediaModel.Artist>>) {
            for ((artist) in artists) {
                if (isFull()) break
                val albums = fetchOrNull(provider) { it.getAlbumArtistDetail(artist.id).albums }.orEmpty()
                for (album in albums.sortedByDescending { ListeningScore.of(it.playCount, it.lastPlayedAt, now) }) {
                    if (isFull()) break
                    addAlbum(album.id)
                }
            }
        }

        val candidates = songCandidates(provider, budgetBytes, variant, now)
        val (recentSongs, olderSongs) = candidates.scores.partition { it.score >= ListeningScore.RECENT_MIN_SCORE }
        addSongs(recentSongs, candidates.songs)

        val albums = if (isFull()) emptyList() else albumCandidates(provider, now)
        val (recentAlbums, olderAlbums) = albums.partition { it.score >= ListeningScore.RECENT_MIN_SCORE }
        addAlbums(recentAlbums)

        val topArtists = if (isFull()) emptyList() else artistCandidates(provider, now)
        val (recentArtists, olderArtists) = topArtists.partition { it.score >= ListeningScore.RECENT_MIN_SCORE }
        addArtists(recentArtists)

        // Listened to a lot, but a long time ago.
        addSongs(olderSongs, candidates.songs)
        addAlbums(olderAlbums)
        addArtists(olderArtists)

        Log.d(TAG, "Ranked ${ranking.size} songs (~$rankedBytes bytes of $budgetBytes)")
        return ranking
    }

    private data class Scored<T>(val item: T, val score: Double)

    /** The songs to rank, by id, and their ids by [ListeningScore], highest first. */
    private class SongCandidates(val songs: Map<String, MediaModel.Song>, val scores: List<Scored<String>>)

    /**
     * The songs played on the provider or in the app. Only the songs played the most on the provider
     * that may still rank within [budgetBytes] are fetched, along with the ones played lately.
     * Songs only known from the app plays are fetched when ranked.
     */
    private suspend fun songCandidates(
        provider: MediaProvider,
        budgetBytes: Long,
        variant: StreamVariant,
        now: Long
    ): SongCandidates {
        val scores = HashMap(database.getAppPlayScores(provider.id, now))
        val candidates = HashMap<String, MediaModel.Song>()

        fun addCandidate(song: MediaModel.Song) {
            candidates[song.id] = song
            scores[song.id] = maxOf(scores[song.id] ?: 0.0, ListeningScore.of(song.playCount, song.lastPlayedAt, now))
        }

        // Most played: a song never scores more than its play count, so once the songs scoring
        // at least the next play count fill the budget, the next ones can't rank anymore.
        if (SongListSort.PLAY_COUNT in provider.supportedSongSort) {
            val topSongs = songPager(provider, SongListSort.PLAY_COUNT)
            var fetched = 0
            while (fetched < MAX_TOP_SONGS) {
                val song = topSongs.next(provider) ?: break
                val playCount = song.playCount ?: 0
                // Sorted by play count: the first song never played ends the list.
                if (playCount <= 0) break
                if (fetched > 0 && fetched % SONG_PAGE_SIZE == 0) {
                    val coveredBytes = candidates.values
                        .filter { (scores[it.id] ?: 0.0) >= playCount }
                        .sumOf { songs[key(provider.id, it.id)]?.sizeBytes ?: estimateSize(it, variant) }
                    if (coveredBytes >= budgetBytes) break
                }
                addCandidate(song)
                fetched++
            }
        }

        // Played lately, even if not often.
        if (SongListSort.RECENTLY_PLAYED in provider.supportedSongSort) {
            val recentSongs = songPager(provider, SongListSort.RECENTLY_PLAYED)
            var fetched = 0
            while (fetched++ < MAX_RECENT_SONGS) {
                val song = recentSongs.next(provider) ?: break
                // Sorted by last play: the first song played too long ago ends the list.
                if (!song.isRecent(now)) break
                addCandidate(song)
            }
        }

        val sorted = scores.entries
            .filter { it.value > 0 }
            .map { Scored(it.key, it.value) }
            .sortedByDescending { it.score }
        return SongCandidates(candidates, sorted)
    }

    /** The albums played the most and the ones played lately, by [ListeningScore], highest first. */
    private suspend fun albumCandidates(provider: MediaProvider, now: Long): List<Scored<MediaModel.Album>> {
        val albums = LinkedHashMap<String, MediaModel.Album>()

        suspend fun collect(sort: AlbumListSort, keep: (MediaModel.Album) -> Boolean) {
            if (sort !in provider.supportedAlbumSort) return
            val pager = Pager<MediaModel.Album>(ALBUM_PAGE_SIZE, { it.id }) { start ->
                provider.getAlbumList(
                    MediaQuery.AlbumListQuery(sortBy = sort, sortOrder = SortOrder.DESC, limit = ALBUM_PAGE_SIZE, startIndex = start)
                )
            }
            var fetched = 0
            while (fetched++ < MAX_ALBUM_CANDIDATES) {
                val album = pager.next(provider)?.takeIf(keep) ?: break
                albums.putIfAbsent(album.id, album)
            }
        }
        collect(AlbumListSort.PLAY_COUNT) { (it.playCount ?: 0.0) > 0.0 }
        collect(AlbumListSort.RECENTLY_PLAYED) { (it.playCount ?: 0.0) > 0.0 && it.lastPlayedAt.isRecent(now) }

        return albums.values
            .map { Scored(it, ListeningScore.of(it.playCount, it.lastPlayedAt, now)) }
            .sortedByDescending { it.score }
    }

    /** The artists played the most, by [ListeningScore], highest first. */
    private suspend fun artistCandidates(provider: MediaProvider, now: Long): List<Scored<MediaModel.Artist>> {
        if (AlbumArtistListSort.PLAY_COUNT !in provider.supportedAlbumArtistSort) return emptyList()
        val pager = Pager<MediaModel.Artist>(ARTIST_PAGE_SIZE, { it.id }) { start ->
            provider.getAlbumArtistList(
                MediaQuery.AlbumArtistListQuery(
                    sortBy = AlbumArtistListSort.PLAY_COUNT,
                    sortOrder = SortOrder.DESC,
                    limit = ARTIST_PAGE_SIZE,
                    startIndex = start
                )
            )
        }
        val topArtists = mutableListOf<MediaModel.Artist>()
        while (topArtists.size < MAX_ARTIST_CANDIDATES) {
            val artist = pager.next(provider) ?: break
            if ((artist.playCount ?: 0.0) <= 0.0) break
            topArtists += artist
        }
        return topArtists
            .map { Scored(it, ListeningScore.of(it.playCount, it.lastPlayedAt, now)) }
            .sortedByDescending { it.score }
    }

    private fun songPager(provider: MediaProvider, sort: SongListSort) =
        Pager<MediaModel.Song>(SONG_PAGE_SIZE, { it.id }) { start ->
            provider.getSongList(
                MediaQuery.SongListQuery(sortBy = sort, sortOrder = SortOrder.DESC, limit = SONG_PAGE_SIZE, startIndex = start)
            )
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

        // Space taken by the downloads in progress, not counted by usedBytes() yet.
        val budgetLock = Any()
        var reservedBytes = 0L

        // Taken in ranking order, so the most listened songs are still downloaded first.
        forEachParallel(missing, SONG_DOWNLOAD_PARALLELISM) { (rank, song) ->
            val estimate = estimateSize(song, variant)
            val inProgressBytes = synchronized(budgetLock) {
                if (usedBytes() + reservedBytes + estimate > maxBytes) null
                else reservedBytes.also { reservedBytes += estimate }
            }
            if (inProgressBytes != null) {
                try {
                    if (songsDir.usableSpace < inProgressBytes + estimate + MIN_FREE_SPACE_BYTES) {
                        throw RefreshException(SmartCacheError.NoSpace)
                    }
                    downloadSong(provider, song, rank, variant)
                } finally {
                    synchronized(budgetLock) { reservedBytes -= estimate }
                }
            }
            _state.update { it.copy(progress = it.progress + 1) }
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
    private suspend fun cacheArtwork(provider: MediaProvider, imageId: String): String? =
        // Same URL as the one shown by the player, see MediaModel.Song.toMediaItem.
        cacheArtwork(provider.getImageUrl(imageId, LibraryType.SONG, 600))

    /** Downloads the image at [url] if not cached yet, and returns its key. */
    private suspend fun cacheArtwork(url: String): String? {
        if (!url.startsWith("http")) return null
        val key = artworkKey(url)
        if (artworks.containsKey(key)) return key

        // The songs of an album share their cover: download it once, the others wait for it.
        val pending = CompletableDeferred<String?>()
        artworkDownloads.putIfAbsent(key, pending)?.let { return it.await() }
        var result: String? = null
        try {
            // Another download may have finished just before.
            if (artworks.containsKey(key)) return key.also { result = it }

            val fileName = UUID.randomUUID().toString()
            val size = download(url, File(artworksDir, fileName), image = true) ?: return null
            val artwork = CachedArtwork(key, fileName, size)
            mutex.withLock {
                database.putCachedArtwork(artwork)
                artworks[key] = artwork
            }
            result = key
            return key
        } finally {
            pending.complete(result)
            artworkDownloads.remove(key, pending)
        }
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

    //region Artists and playlists

    /**
     * Saves the artists of the cached songs and the playlists holding cached songs, with their
     * pictures, and drops the ones no cached song needs anymore.
     */
    private suspend fun cacheArtistsAndPlaylists(provider: MediaProvider) {
        val cachedSongs = songs.values.filter { it.providerId == provider.id }
        val songArtists = cachedSongs
            .flatMap { it.song.artistsToCache() }
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
        val playlistList = if (ProviderFeature.PLAYLISTS in provider.featureFlags) fetchPlaylists(provider) else emptyList()
        setPhase(SmartCachePhase.LIBRARY, total = songArtists.size + playlistList.size)

        val cachedSongIds = cachedSongs.mapTo(HashSet()) { it.songId }
        val tasks = songArtists.map { artist -> suspend { cacheArtist(provider, artist) } } +
            playlistList.map { playlist -> suspend { cachePlaylist(provider, playlist, cachedSongIds) } }
        forEachParallel(tasks, METADATA_PARALLELISM) { task ->
            task()
            _state.update { it.copy(progress = it.progress + 1) }
        }

        // Playlists deleted on the provider, or without cached songs anymore.
        val listedIds = playlistList.mapTo(HashSet()) { it.id }
        mutex.withLock {
            val removed = playlists.values.filter { it.providerId == provider.id && it.playlistId !in listedIds }
            database.deleteCachedPlaylists(removed)
            removed.forEach { playlists.remove(key(it.providerId, it.playlistId)) }
            deleteUnusedLibraryLocked()
            deleteUnusedArtworksLocked()
        }
        updateUsage()
    }

    /** Saves [songArtist] with its details, biography, similar artists and pictures. */
    private suspend fun cacheArtist(provider: MediaProvider, songArtist: MediaModel.Artist) {
        val previous = artists[key(provider.id, songArtist.id)]?.artist
        val (detail, info) = coroutineScope {
            val detail = async { fetchOrNull(provider) { it.getAlbumArtistDetail(songArtist.id).artist } }
            val info = async { fetchOrNull(provider) { it.getAlbumArtistInfo(songArtist.id) } }
            detail.await() to info.await()
        }

        val base = detail ?: previous ?: songArtist
        val artist = base.copy(
            biography = info?.biography?.takeIf { it.isNotBlank() } ?: base.biography ?: previous?.biography,
            imageUrl = base.imageUrl ?: info?.imageUrl ?: previous?.imageUrl,
            similarArtists = info?.similarArtists ?: base.similarArtists.ifEmpty { previous?.similarArtists.orEmpty() },
        )

        // Every URL the screens may show it with, see ArtistCard and ArtistDetailsScreen.
        val urls = listOfNotNull(
            artist.imageUrl,
            info?.imageUrl,
            artist.imageId?.let { provider.getImageUrl(it, LibraryType.ALBUM_ARTIST, 600) },
        ).distinctBy { artworkKey(it) }
        val artworkKeys = coroutineScope { urls.map { async { cacheArtwork(it) } }.awaitAll() }.filterNotNull()

        val cached = CachedArtist(provider.id, artist, artworkKeys)
        mutex.withLock {
            database.putCachedArtists(listOf(cached))
            artists[key(provider.id, artist.id)] = cached
        }
        publishLibrary(listOf(cached), emptyList())
    }

    /** Saves [playlist] if it holds cached songs, or drops it if not anymore. */
    private suspend fun cachePlaylist(provider: MediaProvider, playlist: MediaModel.Playlist, cachedSongIds: Set<String>) {
        val playlistKey = key(provider.id, playlist.id)
        val playlistSongs = fetchOrNull(provider) { it.getPlaylistSongList(playlist.id) } ?: return
        if (playlistSongs.none { it.id in cachedSongIds }) {
            mutex.withLock {
                playlists.remove(playlistKey)?.let { database.deleteCachedPlaylists(listOf(it)) }
            }
            return
        }

        val urls = listOfNotNull(
            playlist.imageUrl,
            playlist.imageId?.let { provider.getImageUrl(it, LibraryType.PLAYLIST) },
        ).distinctBy { artworkKey(it) }
        val artworkKeys = coroutineScope { urls.map { async { cacheArtwork(it) } }.awaitAll() }.filterNotNull()

        val cached = CachedPlaylist(provider.id, playlist, playlistSongs.map { it.id }, artworkKeys)
        mutex.withLock {
            database.putCachedPlaylists(listOf(cached))
            playlists[playlistKey] = cached
        }
        publishLibrary(emptyList(), listOf(cached))
    }

    private suspend fun fetchPlaylists(provider: MediaProvider): List<MediaModel.Playlist> {
        val pager = Pager<MediaModel.Playlist>(PLAYLIST_PAGE_SIZE, { it.id }) { start ->
            provider.getPlaylistList(
                MediaQuery.PlaylistListQuery(
                    sortBy = PlaylistListSort.NAME,
                    sortOrder = SortOrder.ASC,
                    limit = PLAYLIST_PAGE_SIZE,
                    startIndex = start
                )
            )
        }
        return buildList { while (true) add(pager.next(provider) ?: break) }
    }

    /** Drops the artists and playlists without cached songs anymore. */
    private fun deleteUnusedLibraryLocked() {
        val songIds = songs.keys
        val artistIds = songs.values.flatMapTo(HashSet()) { cached ->
            cached.song.artistsToCache().map { key(cached.providerId, it.id) }
        }

        val unusedArtists = artists.filterKeys { it !in artistIds }.values.toList()
        database.deleteCachedArtists(unusedArtists)
        unusedArtists.forEach { artists.remove(key(it.providerId, it.artistId)) }

        val unusedPlaylists = playlists.values.filter { cached ->
            cached.songIds.none { key(cached.providerId, it) in songIds }
        }
        database.deleteCachedPlaylists(unusedPlaylists)
        unusedPlaylists.forEach { playlists.remove(key(it.providerId, it.playlistId)) }
    }

    //endregion

    //region Pre-fetched tracks

    /**
     * Offers a track the player pre-fetched (fully downloaded, in RAM) to the smart cache.
     * It is kept if the cache has room for it, or if its [ListeningScore] is higher than the one of
     * the cached songs listened to the least lately, which are then dropped to make room. [write] copies the track to a stream.
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

        val now = System.currentTimeMillis()
        val appScores = database.getAppPlayScores(providerId, now)
        val score = listeningScore(song, appScores, now)
        // Cheap check first, to avoid writing the file for nothing. Done again below, under the lock.
        if (evictionsFor(providerId, score, sizeBytes, maxBytes, appScores, now) == null) return
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
                else evictionsFor(providerId, score, sizeBytes, maxBytes, appScores, now)
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
                rank = rankFor(providerId, score, appScores, now),
                cachedAt = System.currentTimeMillis(),
            )
            database.putCachedSong(cached)
            songs[key(cached)] = cached
            Log.d(TAG, "Kept pre-fetched track $songId (score $score), dropped ${evicted.size} songs")
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
     * The cached songs to drop so a song with a [ListeningScore] of [score] fits, lowest score first:
     * none if there is room, null if it can't fit without dropping songs scoring as much or more.
     */
    private fun evictionsFor(
        providerId: String,
        score: Double,
        sizeBytes: Long,
        maxBytes: Long,
        appScores: Map<String, Double>,
        now: Long
    ): List<CachedSong>? {
        var freeBytes = maxBytes - usedBytes()
        if (freeBytes >= sizeBytes) return emptyList()

        val evicted = mutableListOf<CachedSong>()
        val lowerScored = songs.values
            .filter { it.providerId == providerId }
            .map { it to listeningScore(it.song, appScores, now) }
            .filter { (_, cachedScore) -> cachedScore < score }
            .sortedWith(compareBy<Pair<CachedSong, Double>> { it.second }.thenByDescending { it.first.rank })
        for ((cached, _) in lowerScored) {
            evicted += cached
            freeBytes += cached.sizeBytes
            if (freeBytes >= sizeBytes) return evicted
        }
        return null
    }

    /** Places a song with a [ListeningScore] of [score] just before the cached songs scoring less. */
    private fun rankFor(providerId: String, score: Double, appScores: Map<String, Double>, now: Long): Int {
        val providerSongs = songs.values.filter { it.providerId == providerId }
        return providerSongs.filter { listeningScore(it.song, appScores, now) < score }.minOfOrNull { it.rank }
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
            if (songs.isEmpty() && artworks.isEmpty() && artists.isEmpty() && playlists.isEmpty()) return@withLock
            deleteSongsLocked(songs.values.toList())
            deleteUnusedLibraryLocked()
            deleteUnusedArtworksLocked()
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

        deleteUnusedLibraryLocked()
        deleteUnusedArtworksLocked()
    }

    /** Covers are shared by the songs of an album, only drop the ones no song, artist or playlist uses anymore. */
    private fun deleteUnusedArtworksLocked() {
        val usedArtworks = songs.values.mapNotNullTo(HashSet()) { it.artworkKey }
        artists.values.flatMapTo(usedArtworks) { it.artworkKeys }
        playlists.values.flatMapTo(usedArtworks) { it.artworkKeys }
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
        database.getCachedArtists().forEach { artists[key(it.providerId, it.artistId)] = it }
        database.getCachedPlaylists().forEach { playlists[key(it.providerId, it.playlistId)] = it }
        deleteUnusedLibraryLocked()
        deleteUnusedArtworksLocked()

        publishToLibraryCache(cachedSongs)
        publishLibrary(artists.values, playlists.values)

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

    /** Makes the cached artists (with their biography) and playlists (with their cached songs) browsable offline. */
    private fun publishLibrary(cachedArtists: Collection<CachedArtist>, cachedPlaylists: Collection<CachedPlaylist>) {
        cachedArtists.groupBy { it.providerId }.forEach { (providerId, providerArtists) ->
            libraryCache.putArtists(providerId, providerArtists.map { it.artist })
            providerArtists.forEach { cached ->
                val artist = cached.artist
                libraryCache.putArtistInfo(
                    providerId,
                    artist.id,
                    AlbumArtistInfo(artist.biography, artist.imageUrl, artist.similarArtists.ifEmpty { null })
                )
            }
        }
        cachedPlaylists.groupBy { it.providerId }.forEach { (providerId, providerPlaylists) ->
            libraryCache.putPlaylists(providerId, providerPlaylists.map { it.playlist })
            providerPlaylists.forEach { cached ->
                // Only the cached songs can be played offline.
                val playlistSongs = cached.songIds.mapNotNull { songs[key(providerId, it)]?.song }
                libraryCache.putPlaylistSongs(providerId, cached.playlistId, playlistSongs)
            }
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

    /**
     * Runs [block] on [items], [parallelism] at a time, taking them in order.
     * The first failure cancels the others and is rethrown.
     */
    private suspend fun <T : Any> forEachParallel(items: Collection<T>, parallelism: Int, block: suspend (T) -> Unit) {
        val queue = ConcurrentLinkedQueue(items)
        coroutineScope {
            repeat(minOf(parallelism, items.size)) {
                launch {
                    while (true) {
                        ensureActive()
                        block(queue.poll() ?: break)
                    }
                }
            }
        }
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
        private const val PLAYLIST_PAGE_SIZE = 100
        private const val MAX_CONSECUTIVE_MISSES = 50

        /** Songs downloaded at once: each one may be transcoded by the server. */
        private const val SONG_DOWNLOAD_PARALLELISM = 4
        /** Albums, artists and playlists fetched at once, small requests. */
        private const val METADATA_PARALLELISM = 6
        private const val MAX_TOP_SONGS = 5000
        private const val MAX_RECENT_SONGS = 2000
        private const val MAX_ALBUM_CANDIDATES = 500
        private const val MAX_ARTIST_CANDIDATES = 200

        /** A single play older than this scores below [ListeningScore.RECENT_MIN_SCORE]. */
        private val RECENT_WINDOW_MS = ListeningScore.HALF_LIFE_MS

        private fun key(providerId: String, songId: String) = "$providerId/$songId"
        private fun key(cached: CachedSong) = key(cached.providerId, cached.songId)

        /** The artists shown for a song, kept along with it. */
        private fun MediaModel.Song.artistsToCache(): List<MediaModel.Artist> = albumArtists + artists

        /** A song's [ListeningScore]: from its plays on the provider, or in the app if more (e.g. not scrobbled yet). */
        private fun listeningScore(song: MediaModel.Song, appScores: Map<String, Double>, now: Long): Double =
            maxOf(ListeningScore.of(song.playCount, song.lastPlayedAt, now), appScores[song.id] ?: 0.0)

        /** Played recently enough for a play to still count, see [ListeningScore.RECENT_MIN_SCORE]. */
        private fun String?.isRecent(now: Long): Boolean =
            parseDateMillis(this)?.let { now - it <= RECENT_WINDOW_MS } == true

        private fun MediaModel.Song.isRecent(now: Long) = (playCount ?: 0) > 0 && lastPlayedAt.isRecent(now)

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
