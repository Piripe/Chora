package com.craftworks.music.data.cache

import com.craftworks.music.data.model.AlbumArtistDetailResponse
import com.craftworks.music.data.model.AlbumArtistInfo
import com.craftworks.music.data.model.AlbumArtistListSort
import com.craftworks.music.data.model.AlbumListSort
import com.craftworks.music.data.model.MediaModel
import com.craftworks.music.data.model.MediaQuery
import com.craftworks.music.data.model.SongListSort
import com.craftworks.music.data.model.SortOrder
import com.craftworks.music.data.model.parseDateMillis
import kotlinx.coroutines.CompletableDeferred
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory cache of the songs, albums, artists and playlists fetched from the media providers.
 *
 * Nothing is persisted: the cache only lives as long as the app process. It is filled by the
 * repositories on every successful request, and is used to answer the same queries when the
 * provider can't be reached, so already visited parts of the library can still be browsed offline.
 * At startup, it is also filled with the songs of the [com.craftworks.music.managers.SmartCacheManager],
 * and their artists, playlists and folders.
 */
@Singleton
class LibraryCache @Inject constructor() {
    private class ProviderCache {
        val songs = LinkedHashMap<String, MediaModel.Song>()
        val albums = LinkedHashMap<String, MediaModel.Album>()
        val artists = LinkedHashMap<String, MediaModel.Artist>()
        val artistAlbums = HashMap<String, LinkedHashSet<String>>()
        val artistInfo = HashMap<String, AlbumArtistInfo>()
        val playlists = LinkedHashMap<String, MediaModel.Playlist>()
        val playlistSongs = HashMap<String, List<String>>()
        val appPlays = HashMap<String, AppPlay>()
        /** The folders opened while online, with their content. */
        val folders = HashMap<String, MediaModel.Folder>()
        /** The folders holding the cached songs (song ID to its folders), see [CachedSongFolders]. */
        val songFolders = HashMap<String, List<CachedFolder>>()
    }

    /** When something was last played and added (epoch milliseconds), and how many times it was played. */
    private class PlayStats(val lastPlayedAt: Long?, val playCount: Double, val createdAt: Long?)

    private val caches = HashMap<String, ProviderCache>()

    /** Completed once the [com.craftworks.music.managers.SmartCacheManager] has restored its content at startup. */
    private val restored = CompletableDeferred<Unit>()

    fun markRestored() {
        restored.complete(Unit)
    }

    /** Waits for the startup content, so offline reads made right at launch aren't empty. */
    suspend fun awaitRestored() = restored.await()

    private inline fun <T> withCache(providerId: String, block: ProviderCache.() -> T): T =
        synchronized(caches) { caches.getOrPut(providerId) { ProviderCache() }.block() }

    //region Store

    fun putSongs(providerId: String, songs: List<MediaModel.Song>) = withCache(providerId) {
        songs.forEach { this.songs[it.id] = it }
    }

    fun putAlbums(providerId: String, albums: List<MediaModel.Album>) = withCache(providerId) {
        albums.forEach { album ->
            // Songs are stored on their own and joined back by albumId when reading.
            this.albums[album.id] = album.copy(songs = emptyList())
            album.songs.forEach { songs[it.id] = it }
        }
    }

    fun putArtists(providerId: String, artists: List<MediaModel.Artist>) = withCache(providerId) {
        artists.forEach { artist ->
            val previous = this.artists[artist.id]
            this.artists[artist.id] = artist.copy(
                biography = artist.biography ?: previous?.biography,
                imageUrl = artist.imageUrl ?: previous?.imageUrl
            )
        }
    }

    /** Stores [albums] and their songs, without replacing albums already known (they have more details). */
    fun addMissingAlbums(providerId: String, albums: List<MediaModel.Album>) = withCache(providerId) {
        albums.forEach { album ->
            this.albums.putIfAbsent(album.id, album.copy(songs = emptyList()))
            album.songs.forEach { songs.putIfAbsent(it.id, it) }
        }
    }

    /** Stores [artists], without replacing artists already known (they have more details). */
    fun addMissingArtists(providerId: String, artists: List<MediaModel.Artist>) = withCache(providerId) {
        artists.forEach { this.artists.putIfAbsent(it.id, it) }
    }

    fun putArtistAlbums(providerId: String, artistId: String, albums: List<MediaModel.Album>) {
        putAlbums(providerId, albums)
        withCache(providerId) {
            artistAlbums.getOrPut(artistId) { LinkedHashSet() }.addAll(albums.map { it.id })
        }
    }

    fun putArtistInfo(providerId: String, artistId: String, info: AlbumArtistInfo) = withCache(providerId) {
        artistInfo[artistId] = info
    }

    fun putPlaylists(providerId: String, playlists: List<MediaModel.Playlist>) = withCache(providerId) {
        playlists.forEach { this.playlists[it.id] = it }
    }

    fun putPlaylistSongs(providerId: String, playlistId: String, songs: List<MediaModel.Song>) = withCache(providerId) {
        songs.forEach { this.songs[it.id] = it }
        playlistSongs[playlistId] = songs.map { it.id }
    }

    fun putFolder(providerId: String, folder: MediaModel.Folder) = withCache(providerId) {
        folders[folder.id] = folder
        folder.children?.songs?.forEach { songs[it.id] = it }
    }

    fun putSongFolders(providerId: String, songFolders: Map<String, List<CachedFolder>>) = withCache(providerId) {
        this.songFolders.putAll(songFolders)
    }

    /** Plays made in the app, so the songs played offline (not known by the provider yet) count too. */
    fun putAppPlays(plays: List<AppPlay>) = plays.forEach { play ->
        withCache(play.providerId) { mergeAppPlay(play) }
    }

    fun recordAppPlay(providerId: String, songId: String, playedAt: Long) = withCache(providerId) {
        val previous = appPlays[songId]
        appPlays[songId] = AppPlay(
            providerId, songId, (previous?.playCount ?: 0) + 1, maxOf(playedAt, previous?.lastPlayedAt ?: 0L)
        )
    }

    private fun ProviderCache.mergeAppPlay(play: AppPlay) {
        val previous = appPlays[play.songId]
        appPlays[play.songId] = if (previous == null) play else play.copy(
            playCount = maxOf(play.playCount, previous.playCount),
            lastPlayedAt = maxOf(play.lastPlayedAt, previous.lastPlayedAt)
        )
    }

    //endregion

    //region Read

    fun getPlaylists(providerId: String, query: MediaQuery.PlaylistListQuery): List<MediaModel.Playlist> {
        val playlists = withCache(providerId) { playlists.values.toList() }
        val search = query.searchTerm?.takeIf { it.isNotBlank() }
        val filtered = playlists.filter { playlist ->
            (search == null || playlist.name.contains(search, true))
                && (!query.excludeSmartPlaylists || playlist.rules == null)
        }
        return paginate(sortList(filtered, query.sortOrder) { it.name.lowercase() }, query.startIndex, query.limit)
    }

    fun getPlaylistSongs(providerId: String, playlistId: String): List<MediaModel.Song> = withCache(providerId) {
        playlistSongs[playlistId].orEmpty().mapNotNull { songs[it] }
    }

    /**
     * The folder with [folderId] and its content: as it was opened online, or else rebuilt from the
     * folders of the cached songs (only showing them). Null when the folder is unknown.
     */
    fun getFolder(providerId: String, folderId: String): MediaModel.Folder? = withCache(providerId) {
        folders[folderId]?.let { return@withCache it }

        var found = folderId == MediaModel.Folder.ROOT_ID
        var self: CachedFolder? = null
        val subfolders = LinkedHashMap<String, CachedFolder>()
        // A folder shows the artwork of its first song.
        val subfolderImages = HashMap<String, String>()
        val folderSongs = mutableListOf<MediaModel.Song>()
        songFolders.forEach { (songId, path) ->
            val index = if (folderId == MediaModel.Folder.ROOT_ID) -1 else path.indexOfFirst { it.id == folderId }
            if (folderId != MediaModel.Folder.ROOT_ID && index < 0) return@forEach
            found = true
            if (index >= 0) self = path[index]
            val song = songs[songId]
            val child = path.getOrNull(index + 1)
            if (child == null) {
                if (song != null) folderSongs += song
            } else {
                subfolders.putIfAbsent(child.id, child)
                song?.imageId?.let { subfolderImages.putIfAbsent(child.id, it) }
            }
        }
        if (!found) return@withCache null

        val providerType = folderSongs.firstOrNull()?.providerType
            ?: songs.values.firstOrNull()?.providerType
            ?: return@withCache null
        MediaModel.Folder(
            id = folderId,
            providerId = providerId,
            providerType = providerType,
            name = self?.name ?: "",
            parentId = self?.parentId,
            children = MediaModel.Folder.Children(
                folders = subfolders.values.sortedBy { it.name.lowercase() }.map {
                    MediaModel.Folder(
                        id = it.id,
                        providerId = providerId,
                        providerType = providerType,
                        imageId = subfolderImages[it.id],
                        name = it.name,
                        parentId = it.parentId
                    )
                },
                songs = folderSongs.sortedWith(compareBy({ it.discNumber }, { it.trackNumber }, { it.name.lowercase() }))
            )
        )
    }

    /** The songs of the folder with [folderId] and of its subfolders, see [getFolder]. */
    fun getFolderSongs(providerId: String, folderId: String, limit: Int = 2000): List<MediaModel.Song> {
        val folderSongs = mutableListOf<MediaModel.Song>()
        fun collect(id: String) {
            val folder = getFolder(providerId, id) ?: return
            for (subfolder in folder.children?.folders.orEmpty()) {
                if (folderSongs.size >= limit) return
                collect(subfolder.id)
            }
            folderSongs += folder.children?.songs.orEmpty()
        }
        collect(folderId)
        return folderSongs.take(limit)
    }

    fun getSong(providerId: String, songId: String): MediaModel.Song? = withCache(providerId) {
        songs[songId]
    }

    fun getAlbum(providerId: String, albumId: String): MediaModel.Album? = withCache(providerId) {
        albums[albumId]?.copy(
            songs = songs.values
                .filter { it.albumId == albumId }
                .sortedWith(compareBy({ it.discNumber }, { it.trackNumber }))
        )
    }

    fun getArtistAlbums(providerId: String, artistId: String): List<MediaModel.Album> = withCache(providerId) {
        val knownIds = artistAlbums[artistId].orEmpty()
        // Albums only known through their songs (smart cache, played songs) may lack their artists.
        val songAlbumIds = songs.values
            .filter { song -> song.albumArtists.ifEmpty { song.artists }.any { it.id == artistId } }
            .mapTo(HashSet()) { it.albumId }
        albums.values
            .filter { album ->
                album.id in knownIds || album.id in songAlbumIds || album.artists.any { it.id == artistId }
                    || album.participants["albumartist"]?.any { it.id == artistId } == true
            }
            .sortedBy { it.name.lowercase() }
    }

    /** Cached albums holding a song of the artist with [artistId], or where the artist is a participant. */
    fun getArtistAppearances(providerId: String, artistId: String): List<MediaModel.Album> = withCache(providerId) {
        val songAlbumIds = songs.values
            .filter { song -> song.artists.any { it.id == artistId } }
            .mapTo(HashSet()) { it.albumId }
        albums.values
            .filter { album ->
                album.id in songAlbumIds || album.participants.values.any { artists -> artists.any { it.id == artistId } }
            }
            .sortedBy { it.name.lowercase() }
    }

    fun getArtistDetail(providerId: String, artistId: String): AlbumArtistDetailResponse? {
        val artist = withCache(providerId) { artists[artistId] }
        val albums = getArtistAlbums(providerId, artistId)
        if (artist == null && albums.isEmpty()) return null
        return AlbumArtistDetailResponse(artist, albums)
    }

    fun getArtistInfo(providerId: String, artistId: String): AlbumArtistInfo? = withCache(providerId) {
        artistInfo[artistId]
            ?: artists[artistId]?.let { AlbumArtistInfo(biography = it.biography, imageUrl = it.imageUrl) }
    }

    /** Genres of the cached songs and albums, used when the provider is unreachable. */
    fun getGenres(providerId: String, searchTerm: String?): List<MediaModel.Genre> = withCache(providerId) {
        (songs.values.flatMap { it.genres } + albums.values.flatMap { it.genres })
            .filter { it.name.isNotBlank() && (searchTerm.isNullOrBlank() || it.name.contains(searchTerm, true)) }
            .distinctBy { it.id ?: it.name.lowercase() }
            .sortedBy { it.name.lowercase() }
    }

    fun getAlbums(
        providerId: String,
        query: MediaQuery.AlbumListQuery,
        naturalOrder: Boolean
    ): List<MediaModel.Album> {
        val (albums, artistAlbums) = withCache(providerId) { albums.values.toList() to artistAlbums.toMap() }
        val search = query.searchTerm?.takeIf { it.isNotBlank() }
        val stats = if (query.sortBy in ALBUM_PLAY_SORTS) withCache(providerId) { albumStats() } else emptyMap()

        val filtered = albums.filter { album ->
            (query.artistIds == null || query.artistIds.any { artistId ->
                artistAlbums[artistId]?.contains(album.id) == true || album.artists.any { it.id == artistId }
                    || album.participants.values.any { artists -> artists.any { it.id == artistId } }
            })
                && (query.compilation == null || (album.isCompilation == true) == query.compilation)
                && (query.favorite == null || (album.userFavorite == true) == query.favorite)
                && (query.genreIds == null || album.genres.any { genre -> query.genreIds.any { it == genre.id || it.equals(genre.name, true) } })
                && (query.hasRating == null || ((album.userRating ?: 0) > 0) == query.hasRating)
                && (query.minYear == null || (album.releaseYear ?: 0) >= query.minYear)
                && (query.maxYear == null || (album.releaseYear ?: Int.MAX_VALUE) <= query.maxYear)
                && (search == null || album.name.contains(search, true) || album.albumArtistName?.contains(search, true) == true)
                // Like the providers, only list the albums that were played.
                && (query.sortBy != AlbumListSort.RECENTLY_PLAYED || stats[album.id]?.lastPlayedAt != null)
                && (query.sortBy != AlbumListSort.PLAY_COUNT || (stats[album.id]?.playCount ?: 0.0) > 0.0)
        }

        val descendingByDefault = query.sortBy in setOf(
            AlbumListSort.RECENTLY_ADDED, AlbumListSort.RECENTLY_PLAYED, AlbumListSort.PLAY_COUNT,
            AlbumListSort.RATING, AlbumListSort.FAVORITE
        )
        val sortOrder = if (naturalOrder) { if (descendingByDefault) SortOrder.DESC else SortOrder.ASC } else query.sortOrder

        val sorted = if (query.sortBy == AlbumListSort.RANDOM) filtered.shuffled()
        else sortList(filtered, sortOrder) { album ->
            when (query.sortBy) {
                AlbumListSort.ALBUM_ARTIST, AlbumListSort.ARTIST -> album.albumArtistName?.lowercase()
                AlbumListSort.DURATION -> album.durationMs
                AlbumListSort.EXPLICIT_STATUS -> album.explicit
                AlbumListSort.FAVORITE -> album.userFavorite
                AlbumListSort.ID -> album.id
                AlbumListSort.PLAY_COUNT -> stats[album.id]?.playCount
                AlbumListSort.RATING, AlbumListSort.COMMUNITY_RATING, AlbumListSort.CRITIC_RATING -> album.userRating
                AlbumListSort.RECENTLY_ADDED -> stats[album.id]?.createdAt
                AlbumListSort.RECENTLY_PLAYED -> stats[album.id]?.lastPlayedAt
                AlbumListSort.RELEASE_DATE -> album.releaseDate ?: album.originalDate
                AlbumListSort.SONG_COUNT -> album.songCount
                AlbumListSort.SORT_NAME -> (album.sortName ?: album.name).lowercase()
                AlbumListSort.YEAR -> album.releaseYear
                else -> album.name.lowercase()
            }
        }

        return paginate(sorted, query.startIndex, query.limit)
    }

    fun getArtists(
        providerId: String,
        query: MediaQuery.AlbumArtistListQuery,
        naturalOrder: Boolean
    ): List<MediaModel.Artist> {
        val artists = withCache(providerId) { artists.values.toList() }
        val search = query.searchTerm?.takeIf { it.isNotBlank() }

        val filtered = artists.filter { artist ->
            (query.favorite == null || (artist.userFavorite == true) == query.favorite)
                && (query.genreIds == null || artist.genres.any { genre -> query.genreIds.any { it == genre.id || it.equals(genre.name, true) } })
                && (search == null || artist.name.contains(search, true))
        }

        val descendingByDefault = query.sortBy in setOf(
            AlbumArtistListSort.PLAY_COUNT, AlbumArtistListSort.RATING, AlbumArtistListSort.FAVORITE,
            AlbumArtistListSort.RECENTLY_ADDED
        )
        val sortOrder = if (naturalOrder) { if (descendingByDefault) SortOrder.DESC else SortOrder.ASC } else query.sortOrder

        val sorted = if (query.sortBy == AlbumArtistListSort.RANDOM) filtered.shuffled()
        else sortList(filtered, sortOrder) { artist ->
            when (query.sortBy) {
                AlbumArtistListSort.ALBUM_COUNT -> artist.albumCount
                AlbumArtistListSort.DURATION -> artist.durationMs
                AlbumArtistListSort.FAVORITE -> artist.userFavorite
                AlbumArtistListSort.PLAY_COUNT -> artist.playCount
                AlbumArtistListSort.RATING -> artist.userRating
                AlbumArtistListSort.SONG_COUNT -> artist.songCount
                else -> artist.name.lowercase()
            }
        }

        return paginate(sorted, query.startIndex, query.limit)
    }

    fun getSongs(
        providerId: String,
        query: MediaQuery.SongListQuery,
        naturalOrder: Boolean
    ): List<MediaModel.Song> {
        val songs = withCache(providerId) { songs.values.toList() }
        val search = query.searchTerm?.takeIf { it.isNotBlank() }
        val stats = if (query.sortBy in SONG_PLAY_SORTS) {
            withCache(providerId) { songs.associate { it.id to songStats(it) } }
        } else emptyMap()

        val filtered = songs.filter { song ->
            (query.albumIds == null || song.albumId in query.albumIds)
                && (query.artistIds == null || song.artists.any { it.id in query.artistIds } || song.albumArtists.any { it.id in query.artistIds }
                    || song.participants.orEmpty().values.any { artists -> artists.any { it.id in query.artistIds } })
                && (query.albumArtistIds == null || song.albumArtists.any { it.id in query.albumArtistIds })
                && (query.favorite == null || (song.userFavorite == true) == query.favorite)
                && (query.genreIds == null || song.genres.any { genre -> query.genreIds.any { it == genre.id || it.equals(genre.name, true) } })
                && (query.hasRating == null || ((song.userRating ?: 0) > 0) == query.hasRating)
                && (query.minYear == null || (song.releaseYear ?: 0) >= query.minYear)
                && (query.maxYear == null || (song.releaseYear ?: Int.MAX_VALUE) <= query.maxYear)
                && (search == null || song.name.contains(search, true) || song.artistName.contains(search, true) || song.album?.contains(search, true) == true)
                && (query.sortBy != SongListSort.RECENTLY_PLAYED || stats[song.id]?.lastPlayedAt != null)
                && (query.sortBy != SongListSort.PLAY_COUNT || (stats[song.id]?.playCount ?: 0.0) > 0.0)
        }

        val descendingByDefault = query.sortBy in setOf(
            SongListSort.RECENTLY_ADDED, SongListSort.RECENTLY_PLAYED, SongListSort.PLAY_COUNT,
            SongListSort.RATING, SongListSort.FAVORITE
        )
        val sortOrder = if (naturalOrder) { if (descendingByDefault) SortOrder.DESC else SortOrder.ASC } else query.sortOrder

        val sorted = if (query.sortBy == SongListSort.RANDOM) filtered.shuffled()
        else sortList(filtered, sortOrder) { song ->
            when (query.sortBy) {
                SongListSort.ALBUM -> song.album?.lowercase()
                SongListSort.ALBUM_ARTIST -> song.albumArtistName?.lowercase()
                SongListSort.ARTIST -> song.artistName.lowercase()
                SongListSort.BPM -> song.bpm
                SongListSort.CHANNELS -> song.channels
                SongListSort.COMMENT -> song.comment
                SongListSort.DURATION -> song.durationMs
                SongListSort.EXPLICIT_STATUS -> song.explicit
                SongListSort.FAVORITE -> song.userFavorite
                SongListSort.GENRE -> song.genres.firstOrNull()?.name?.lowercase()
                SongListSort.ID -> song.id
                SongListSort.PLAY_COUNT -> stats[song.id]?.playCount
                SongListSort.RATING -> song.userRating
                SongListSort.RECENTLY_ADDED -> stats[song.id]?.createdAt
                SongListSort.RECENTLY_PLAYED -> stats[song.id]?.lastPlayedAt
                SongListSort.RELEASE_DATE -> song.releaseDate
                SongListSort.SORT_NAME -> (song.sortName ?: song.name).lowercase()
                SongListSort.TRACK_NUMBER -> song.discNumber * 100_000L + song.trackNumber
                SongListSort.YEAR -> song.releaseYear
                else -> song.name.lowercase()
            }
        }

        return paginate(sorted, query.startIndex, query.limit)
    }

    //endregion

    /** The song's play stats, completed with the plays made in the app. */
    private fun ProviderCache.songStats(song: MediaModel.Song): PlayStats {
        val appPlay = appPlays[song.id]
        return PlayStats(
            lastPlayedAt = listOfNotNull(parseDateMillis(song.lastPlayedAt), appPlay?.lastPlayedAt).maxOrNull(),
            playCount = maxOf(song.playCount ?: 0, appPlay?.playCount ?: 0).toDouble(),
            createdAt = parseDateMillis(song.createdAt)
        )
    }

    /**
     * The play stats of every album, completed with the ones of their cached songs: the albums only
     * known through their songs (smart cache) have none, and the songs played offline aren't counted
     * by the provider yet.
     */
    private fun ProviderCache.albumStats(): Map<String, PlayStats> {
        val songStatsByAlbum = songs.values.groupBy({ it.albumId }, { songStats(it) })
        return albums.values.associate { album ->
            val songStats = songStatsByAlbum[album.id].orEmpty()
            album.id to PlayStats(
                lastPlayedAt = (listOfNotNull(parseDateMillis(album.lastPlayedAt)) + songStats.mapNotNull { it.lastPlayedAt }).maxOrNull(),
                playCount = maxOf(album.playCount ?: 0.0, songStats.sumOf { it.playCount }),
                createdAt = parseDateMillis(album.createdAt) ?: songStats.mapNotNull { it.createdAt }.minOrNull()
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> sortList(items: List<T>, sortOrder: SortOrder, key: (T) -> Comparable<*>?): List<T> {
        val order: Comparator<Comparable<Any>> =
            if (sortOrder == SortOrder.ASC) naturalOrder() else reverseOrder()
        return items.sortedWith(compareBy(nullsLast(order)) { key(it) as Comparable<Any>? })
    }

    private fun <T> paginate(items: List<T>, startIndex: Int, limit: Int?): List<T> =
        items.drop(startIndex).let { if (limit != null) it.take(limit) else it }

    private companion object {
        val ALBUM_PLAY_SORTS = setOf(AlbumListSort.RECENTLY_ADDED, AlbumListSort.RECENTLY_PLAYED, AlbumListSort.PLAY_COUNT)
        val SONG_PLAY_SORTS = setOf(SongListSort.RECENTLY_ADDED, SongListSort.RECENTLY_PLAYED, SongListSort.PLAY_COUNT)
    }
}
