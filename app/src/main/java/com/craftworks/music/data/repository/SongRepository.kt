package com.craftworks.music.data.repository

import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.craftworks.music.R
import com.craftworks.music.data.cache.LibraryCache
import com.craftworks.music.data.model.LibraryFilter
import com.craftworks.music.data.model.LibraryType
import com.craftworks.music.data.model.MediaModel
import com.craftworks.music.data.model.MediaQuery
import com.craftworks.music.data.model.ScrobbleEvent
import com.craftworks.music.data.model.getProvider
import com.craftworks.music.data.model.id
import com.craftworks.music.managers.MediaProviderManager
import com.craftworks.music.managers.OfflineManager
import com.craftworks.music.managers.OfflineManager.Companion.requiresNetwork
import com.craftworks.music.managers.OfflineScrobbleManager
import com.craftworks.music.managers.SmartCacheManager
import com.craftworks.music.utils.FilteredPage
import com.craftworks.music.utils.FilteredPaging
import com.craftworks.music.utils.StringUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SongRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val offlineManager: OfflineManager,
    private val libraryCache: LibraryCache,
    private val offlineScrobbleManager: OfflineScrobbleManager,
    private val smartCacheManager: SmartCacheManager
) {

    suspend fun getSongs(query: MediaQuery.SongListQuery): List<MediaItem> =
        getSongModels(query).map { it.toMediaItem() }

    /** Songs matching [filter], read from the provider page by page. See [FilteredPaging.load]. */
    suspend fun getFilteredSongs(
        filter: LibraryFilter,
        startIndex: Int,
        count: Int,
        query: (startIndex: Int, limit: Int) -> MediaQuery.SongListQuery
    ): FilteredPage<MediaItem> =
        FilteredPaging.load(
            startIndex = startIndex,
            count = count,
            fetch = { start, limit -> getSongModels(query(start, limit)) },
            id = { it.id },
            matches = { filter.matches(it) }
        ).map { it.toMediaItem() }

    private suspend fun getSongModels(query: MediaQuery.SongListQuery): List<MediaModel.Song> = coroutineScope {
        offlineManager.fetchOrCache(
            online = { it.getSongList(query) },
            onSuccess = { providerId, songs -> libraryCache.putSongs(providerId, songs) },
            offline = { providerId ->
                val naturalOrder = MediaProviderManager.currentProvider.value?.supportSongSortOrder == false
                libraryCache.getSongs(providerId, query, naturalOrder)
            }
        ) ?: listOf()
    }

    suspend fun getSong(songId: String): MediaItem? = coroutineScope {
        offlineManager.fetchOrCache(
            online = { it.getSongDetail(songId) },
            onSuccess = { providerId, song -> song?.let { libraryCache.putSongs(providerId, listOf(it)) } },
            offline = { providerId -> libraryCache.getSong(providerId, songId) }
        )?.toMediaItem()
    }
    
    suspend fun setSongRating(
        songId: String, rating: Int = 0
    ) {
        offlineManager.runOrDefault(false) { it.setRating(listOf(songId), rating, LibraryType.SONG) }
    }

    suspend fun getSimilarSongs(songId: String, count: Int) : List<MediaItem> = coroutineScope {
        offlineManager.fetchOrCache(
            online = { it.getSimilarSongs(songId, count) },
            onSuccess = { providerId, songs -> libraryCache.putSongs(providerId, songs) },
            offline = { emptyList() }
        )?.map { it.toMediaItem() } ?: listOf()
    }

    suspend fun scrobbleSong(songId: String, position: Int, playbackRate: Float, event: ScrobbleEvent?, submission: Boolean) {
        val provider = MediaProviderManager.currentProvider.value ?: return
        val tracked = submission && provider.requiresNetwork()
        // The smart cache downloads the songs played the most in the app.
        if (tracked) smartCacheManager.recordPlay(provider.id, songId)

        val sent = offlineManager.runOrDefault(false, provider) {
            it.scrobble(
                id = songId,
                position = position,
                playbackRate = playbackRate,
                event = event,
                submission = submission
            )
            true
        }
        if (!tracked) return

        if (sent) offlineScrobbleManager.flushAsync()
        else offlineScrobbleManager.onScrobbleFailed(provider.id, songId, playedAt = System.currentTimeMillis() - position)
    }

    fun downloadSong(song: MediaMetadata, template: String, playlistName: String = "{playlist}", playlistIndex: String = "{playlist_index}") {
        val values = mapOf(
            "title" to StringUtils.makeValidFilename(song.title.toString()),
            "album" to StringUtils.makeValidFilename(song.albumTitle.toString()),
            "artist" to StringUtils.makeValidFilename(song.artist.toString()),
            "album_artist" to StringUtils.makeValidFilename(song.albumArtist.toString()),
            "ext" to (song.extras?.getString("format") ?: "mp3"),
            "track" to song.trackNumber.toString(),
            "disc" to song.discNumber.toString(),
            "playlist" to StringUtils.makeValidFilename(playlistName),
            "playlist_index" to playlistIndex
        )
        val fileName = StringUtils.makeValidFilepath(Regex("\\{(\\w+)\\}").replace(template) { match ->
            val key = match.groupValues[1]
            values[key] ?: match.value
        })
        val request = DownloadManager.Request(song.getProvider()?.getStreamUrl(song.id?:"", false)?.toUri())
            .setTitle("${context.getString(R.string.notification_download_name)} ${song.title}")
            .setDescription(context.getString(R.string.notification_download_desc))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, fileName)

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
    }
}
