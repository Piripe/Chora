package com.craftworks.music.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.craftworks.music.data.cache.CachedSong
import com.craftworks.music.data.model.LibraryType
import com.craftworks.music.data.model.MediaModel
import com.craftworks.music.managers.MediaProviderManager
import com.craftworks.music.managers.SmartCacheManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where the songs on the device come from. */
enum class DownloadSource { DOWNLOADS, SMART_CACHE }

/** How the songs on the device are grouped. */
enum class DownloadedType { ALBUMS, ARTISTS, PLAYLISTS, SONGS }

/** Songs on the device, grouped as an album, an artist, a playlist, or a single song. */
data class DownloadedItem(
    val key: String,
    val title: String,
    val subtitle: String?,
    val imageUrl: String?,
    val songs: List<CachedSong>,
    /** For a playlist: all its songs, on the device or not. */
    val totalSongs: Int? = null,
) {
    val sizeBytes: Long = songs.sumOf { it.sizeBytes }
}

/** The songs downloaded by the user and the ones kept by the smart cache, for the downloaded content screens. */
@HiltViewModel
class DownloadedContentViewModel @Inject constructor(
    private val smartCacheManager: SmartCacheManager,
) : ViewModel() {
    private val content = MutableStateFlow<SmartCacheManager.OfflineContent?>(null)

    private val _source = MutableStateFlow(DownloadSource.DOWNLOADS)
    val source: StateFlow<DownloadSource> = _source.asStateFlow()

    private val _type = MutableStateFlow(DownloadedType.ALBUMS)
    val type: StateFlow<DownloadedType> = _type.asStateFlow()

    /** The items of the selected source and type, sorted by title. Null while loading. */
    val items: StateFlow<List<DownloadedItem>?> = combine(content, _source, _type) { offline, source, type ->
        offline?.let { group(it, source, type) }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            // Reloaded whenever songs are added or removed.
            smartCacheManager.state
                .map { listOf(it.songCount, it.downloadedSongCount, it.usedBytes, it.downloadedBytes) }
                .distinctUntilChanged()
                .collect { content.value = smartCacheManager.getOfflineContent() }
        }
    }

    fun setSource(source: DownloadSource) {
        _source.value = source
    }

    fun setType(type: DownloadedType) {
        _type.value = type
    }

    fun remove(item: DownloadedItem) {
        viewModelScope.launch { smartCacheManager.removeSongs(item.songs) }
    }

    private fun group(
        content: SmartCacheManager.OfflineContent,
        source: DownloadSource,
        type: DownloadedType
    ): List<DownloadedItem> {
        val songs = content.songs.filter { it.manual == (source == DownloadSource.DOWNLOADS) }
        val items = when (type) {
            DownloadedType.SONGS -> songs.map { cached ->
                DownloadedItem(
                    key = "song/${cached.providerId}/${cached.songId}",
                    title = cached.song.name,
                    subtitle = cached.song.artistName,
                    imageUrl = songImage(cached),
                    songs = listOf(cached)
                )
            }

            DownloadedType.ALBUMS -> songs.groupBy { it.providerId to it.song.albumId }.map { (key, albumSongs) ->
                val sorted = albumSongs.sortedWith(compareBy({ it.song.discNumber }, { it.song.trackNumber }))
                val first = sorted.first().song
                DownloadedItem(
                    key = "album/${key.first}/${key.second}",
                    title = first.album ?: "",
                    subtitle = first.albumArtistName ?: first.artistName,
                    imageUrl = songImage(sorted.first()),
                    songs = sorted
                )
            }

            DownloadedType.ARTISTS -> {
                val artistSongs = LinkedHashMap<Pair<String, String>, MutableList<CachedSong>>()
                val songArtists = HashMap<Pair<String, String>, MediaModel.Artist>()
                songs.forEach { cached ->
                    (cached.song.albumArtists + cached.song.artists)
                        .filter { it.id.isNotBlank() }
                        .distinctBy { it.id }
                        .forEach { artist ->
                            val key = cached.providerId to artist.id
                            artistSongs.getOrPut(key) { mutableListOf() } += cached
                            songArtists.putIfAbsent(key, artist)
                        }
                }
                val cachedArtists = content.artists.associateBy { it.providerId to it.artistId }
                artistSongs.map { (key, artistSongList) ->
                    val artist = cachedArtists[key]?.artist ?: songArtists.getValue(key)
                    DownloadedItem(
                        key = "artist/${key.first}/${key.second}",
                        title = artist.name,
                        subtitle = null,
                        imageUrl = artistImage(key.first, artist),
                        songs = artistSongList
                    )
                }
            }

            DownloadedType.PLAYLISTS -> content.playlists.mapNotNull { cached ->
                val byId = songs.filter { it.providerId == cached.providerId }.associateBy { it.songId }
                val playlistSongs = cached.songIds.distinct().mapNotNull { byId[it] }
                if (playlistSongs.isEmpty()) return@mapNotNull null
                DownloadedItem(
                    key = "playlist/${cached.providerId}/${cached.playlistId}",
                    title = cached.playlist.name,
                    subtitle = cached.playlist.owner,
                    imageUrl = playlistImage(cached.providerId, cached.playlist),
                    songs = playlistSongs,
                    totalSongs = cached.songIds.distinct().size
                )
            }
        }
        return items.sortedBy { it.title.lowercase() }
    }

    // The same URLs as the ones the smart cache saves the pictures for, so they show offline.

    private fun songImage(cached: CachedSong): String? = cached.song.imageId?.let {
        MediaProviderManager.getProvider(cached.providerId)?.getImageUrl(it, LibraryType.SONG, 600)
    }

    private fun artistImage(providerId: String, artist: MediaModel.Artist): String? =
        artist.imageUrl ?: artist.imageId?.let {
            MediaProviderManager.getProvider(providerId)?.getImageUrl(it, LibraryType.ALBUM_ARTIST, 600)
        }

    private fun playlistImage(providerId: String, playlist: MediaModel.Playlist): String? =
        playlist.imageUrl ?: playlist.imageId?.let {
            MediaProviderManager.getProvider(providerId)?.getImageUrl(it, LibraryType.PLAYLIST)
        }
}
