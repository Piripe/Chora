package com.craftworks.music.data.cache

import com.craftworks.music.data.model.AlbumArtistDetailResponse
import com.craftworks.music.data.model.AlbumArtistInfo
import com.craftworks.music.data.model.AlbumArtistListSort
import com.craftworks.music.data.model.AlbumListSort
import com.craftworks.music.data.model.MediaModel
import com.craftworks.music.data.model.MediaQuery
import com.craftworks.music.data.model.SongListSort
import com.craftworks.music.data.model.SortOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory cache of the songs, albums, artists and playlists fetched from the media providers.
 *
 * Nothing is persisted: the cache only lives as long as the app process. It is filled by the
 * repositories on every successful request, and is used to answer the same queries when the
 * provider can't be reached, so already visited parts of the library can still be browsed offline.
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
    }

    private val caches = HashMap<String, ProviderCache>()

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
        albums.values
            .filter { album -> album.id in knownIds || album.artists.any { it.id == artistId } }
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

    fun getAlbums(
        providerId: String,
        query: MediaQuery.AlbumListQuery,
        naturalOrder: Boolean
    ): List<MediaModel.Album> {
        val (albums, artistAlbums) = withCache(providerId) { albums.values.toList() to artistAlbums.toMap() }
        val search = query.searchTerm?.takeIf { it.isNotBlank() }

        val filtered = albums.filter { album ->
            (query.artistIds == null || query.artistIds.any { artistId ->
                artistAlbums[artistId]?.contains(album.id) == true || album.artists.any { it.id == artistId }
            })
                && (query.compilation == null || (album.isCompilation == true) == query.compilation)
                && (query.favorite == null || (album.userFavorite == true) == query.favorite)
                && (query.genreIds == null || album.genres.any { genre -> query.genreIds.any { it.equals(genre.name, true) } })
                && (query.hasRating == null || ((album.userRating ?: 0) > 0) == query.hasRating)
                && (query.minYear == null || (album.releaseYear ?: 0) >= query.minYear)
                && (query.maxYear == null || (album.releaseYear ?: Int.MAX_VALUE) <= query.maxYear)
                && (search == null || album.name.contains(search, true) || album.albumArtistName?.contains(search, true) == true)
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
                AlbumListSort.PLAY_COUNT -> album.playCount
                AlbumListSort.RATING, AlbumListSort.COMMUNITY_RATING, AlbumListSort.CRITIC_RATING -> album.userRating
                AlbumListSort.RECENTLY_ADDED -> album.createdAt
                AlbumListSort.RECENTLY_PLAYED -> album.lastPlayedAt
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

        val filtered = songs.filter { song ->
            (query.albumIds == null || song.albumId in query.albumIds)
                && (query.artistIds == null || song.artists.any { it.id in query.artistIds } || song.albumArtists.any { it.id in query.artistIds })
                && (query.albumArtistIds == null || song.albumArtists.any { it.id in query.albumArtistIds })
                && (query.favorite == null || (song.userFavorite == true) == query.favorite)
                && (query.genreIds == null || song.genres.any { genre -> query.genreIds.any { it.equals(genre.name, true) } })
                && (query.hasRating == null || ((song.userRating ?: 0) > 0) == query.hasRating)
                && (query.minYear == null || (song.releaseYear ?: 0) >= query.minYear)
                && (query.maxYear == null || (song.releaseYear ?: Int.MAX_VALUE) <= query.maxYear)
                && (search == null || song.name.contains(search, true) || song.artistName.contains(search, true) || song.album?.contains(search, true) == true)
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
                SongListSort.PLAY_COUNT -> song.playCount
                SongListSort.RATING -> song.userRating
                SongListSort.RECENTLY_ADDED -> song.createdAt
                SongListSort.RECENTLY_PLAYED -> song.lastPlayedAt
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

    @Suppress("UNCHECKED_CAST")
    private fun <T> sortList(items: List<T>, sortOrder: SortOrder, key: (T) -> Comparable<*>?): List<T> {
        val order: Comparator<Comparable<Any>> =
            if (sortOrder == SortOrder.ASC) naturalOrder() else reverseOrder()
        return items.sortedWith(compareBy(nullsLast(order)) { key(it) as Comparable<Any>? })
    }

    private fun <T> paginate(items: List<T>, startIndex: Int, limit: Int?): List<T> =
        items.drop(startIndex).let { if (limit != null) it.take(limit) else it }
}
