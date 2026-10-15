package com.craftworks.music.data.repository

import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.craftworks.music.data.cache.LibraryCache
import com.craftworks.music.data.model.MediaModel
import com.craftworks.music.data.model.MediaQuery
import com.craftworks.music.data.model.SongListSort
import com.craftworks.music.data.model.SortOrder
import com.craftworks.music.data.model.artists
import com.craftworks.music.data.model.getProvider
import com.craftworks.music.data.model.id
import com.craftworks.music.data.providers.media.MediaProvider
import com.craftworks.music.managers.OfflineManager
import com.craftworks.music.utils.SimilarSongs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** How many songs a mix has. */
const val MIX_SIZE = 50

/**
 * Mixes of similar songs: the instant mix of a song, and the radio of an album or an artist.
 *
 * The provider is asked first (Subsonic servers use Last.fm or similar services for it). Servers
 * without such a service return few or no songs, so the mix is then completed with the top songs
 * of the artist and random songs of the same genres. Offline, it is made from the library cache.
 */
@Singleton
class MixRepository @Inject constructor(
    private val offlineManager: OfflineManager,
    private val libraryCache: LibraryCache,
) {
    /** [song] followed by songs similar to it, or nothing when none were found. */
    suspend fun getInstantMix(song: MediaItem, count: Int = MIX_SIZE): List<MediaItem> = withContext(Dispatchers.IO) {
        val metadata = song.mediaMetadata
        val provider = metadata.getProvider() ?: return@withContext emptyList()
        val songId = metadata.id ?: return@withContext emptyList()
        val artists = metadata.artists.orEmpty().map { it.id to it.name }
            .ifEmpty { listOfNotNull(metadata.artist?.let { "" to it.toString() }) }
        val similar = mix(
            provider = provider,
            seedIds = setOf(songId),
            count = count - 1,
            similar = { it.getSimilarSongs(songId, count - 1) },
            artists = artists,
            genres = metadata.genreNames(),
            offlineSeeds = { songs -> songs.filter { it.id == songId } }
        )
        // Empty when nothing was found, so it isn't played as a mix of one song.
        if (similar.isEmpty()) emptyList() else listOf(song) + similar
    }

    /** Songs similar to the album [album]. */
    suspend fun getAlbumRadio(album: MediaMetadata, count: Int = MIX_SIZE): List<MediaItem> = withContext(Dispatchers.IO) {
        val provider = album.getProvider() ?: return@withContext emptyList()
        val albumId = album.id ?: return@withContext emptyList()
        val albumSongs = fetch(provider, emptyList()) { it.getAlbumDetail(albumId).songs.orEmpty() }
            .ifEmpty { libraryCache.getAlbum(provider.id, albumId)?.songs.orEmpty() }
        mix(
            provider = provider,
            // The album's own songs are left out: the radio is about discovering other songs.
            seedIds = albumSongs.map { it.id }.toSet(),
            count = count,
            similar = { it.getAlbumRadio(albumId, count) },
            artists = albumSongs.flatMap { it.albumArtists + it.artists }
                .ifEmpty { album.artists.orEmpty() }
                .map { it.id to it.name }.distinct(),
            genres = albumSongs.flatMap { song -> song.genres.map { it.name } }.distinct()
                .ifEmpty { album.genreNames() },
            offlineSeeds = { songs -> songs.filter { it.albumId == albumId } }
        )
    }

    /** Songs similar to the artist [artist]. */
    suspend fun getArtistRadio(artist: MediaModel.Artist, count: Int = MIX_SIZE): List<MediaItem> = withContext(Dispatchers.IO) {
        val provider = artist.getProvider() ?: return@withContext emptyList()
        mix(
            provider = provider,
            seedIds = emptySet(),
            count = count,
            similar = { it.getArtistRadio(artist.id, count) },
            artists = listOf(artist.id to artist.name),
            genres = artist.genres.map { it.name },
            offlineSeeds = { songs ->
                songs.filter { song -> (song.artists + song.albumArtists).any { it.id == artist.id } }
            }
        )
    }

    /**
     * Up to [count] songs: the ones [similar] returns, then the top songs of [artists] (ID to name) and random
     * songs of [genres] when the provider found too few. Songs in [seedIds] are left out.
     */
    private suspend fun mix(
        provider: MediaProvider,
        seedIds: Set<String>,
        count: Int,
        similar: suspend (MediaProvider) -> List<MediaModel.Song>,
        artists: List<Pair<String, String>>,
        genres: List<String>,
        offlineSeeds: (List<MediaModel.Song>) -> List<MediaModel.Song>,
    ): List<MediaItem> {
        if (count <= 0) return emptyList()
        val songs = LinkedHashMap<String, MediaModel.Song>()
        fun add(list: List<MediaModel.Song>) {
            for (song in list) {
                if (songs.size >= count) return
                if (song.id !in seedIds) songs.putIfAbsent(song.id, song)
            }
        }

        val fromProvider = fetch(provider, null, similar)
        // null when offline or when the provider failed: the cache is used instead.
        if (fromProvider == null) {
            val cached = libraryCache.getSongs(
                provider.id,
                MediaQuery.SongListQuery(SongListSort.NAME, SortOrder.ASC, startIndex = 0),
                naturalOrder = false
            )
            add(SimilarSongs.pick(cached, offlineSeeds(cached), count))
            return songs.values.map { it.toMediaItem(provider) }
        }
        add(fromProvider)

        // Completed with the top songs of the artists, then random songs of the same genres.
        val fallback = mutableListOf<MediaModel.Song>()
        if (songs.size < count / 2) {
            for ((artistId, artistName) in artists.take(3)) {
                fallback += fetch(provider, emptyList()) { it.getTopSongs(artistName, artistId, count) }.orEmpty()
            }
            for (genre in genres.filter { it.isNotBlank() }.take(3)) {
                fallback += fetch(provider, emptyList()) {
                    it.getRandomSongList(MediaQuery.RandomSongListQuery(genre = genre, limit = count, played = MediaQuery.Played.All))
                }.orEmpty()
            }
            add(fallback.shuffled())
        }
        // Still nothing related: random songs, so the radio plays something.
        if (songs.isEmpty()) {
            add(fetch(provider, emptyList()) {
                it.getRandomSongList(MediaQuery.RandomSongListQuery(limit = count, played = MediaQuery.Played.All))
            }.orEmpty())
        }

        val result = songs.values.toList()
        libraryCache.putSongs(provider.id, result)
        return result.map { it.toMediaItem(provider) }
    }

    /**
     * Runs [block] on [provider], returning [default] when offline or when it fails. Some providers
     * don't implement every request yet, these are treated as failures too.
     */
    private suspend fun <T> fetch(provider: MediaProvider, default: T, block: suspend (MediaProvider) -> T): T =
        try {
            offlineManager.runOrDefault(default, provider, block)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Local providers are queried directly, their errors aren't caught by runOrDefault.
            Log.w("MixRepository", "Request to ${provider.id} failed", e)
            default
        } catch (e: NotImplementedError) {
            Log.w("MixRepository", "Request not supported by ${provider.id}", e)
            default
        }

    private fun MediaMetadata.genreNames(): List<String> =
        genre?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
}
