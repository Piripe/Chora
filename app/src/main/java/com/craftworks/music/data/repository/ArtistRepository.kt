package com.craftworks.music.data.repository

import androidx.media3.common.MediaItem
import com.craftworks.music.data.cache.LibraryCache
import com.craftworks.music.data.model.AlbumArtistDetailResponse
import com.craftworks.music.data.model.AlbumArtistInfo
import com.craftworks.music.data.model.AlbumListSort
import com.craftworks.music.data.model.LibraryFilter
import com.craftworks.music.data.model.MediaModel
import com.craftworks.music.data.model.MediaQuery
import com.craftworks.music.data.model.SortOrder
import com.craftworks.music.managers.MediaProviderManager
import com.craftworks.music.managers.OfflineManager
import com.craftworks.music.utils.FilteredPage
import com.craftworks.music.utils.FilteredPaging
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtistRepository @Inject constructor(
    private val offlineManager: OfflineManager,
    private val libraryCache: LibraryCache
) {

    suspend fun getArtists(
        query: MediaQuery.AlbumArtistListQuery
    ): List<MediaModel.Artist> = coroutineScope {
        offlineManager.fetchOrCache(
            online = { it.getAlbumArtistList(query) },
            onSuccess = { providerId, artists -> libraryCache.putArtists(providerId, artists) },
            offline = { providerId ->
                val naturalOrder = MediaProviderManager.currentProvider.value?.supportAlbumArtistSortOrder == false
                libraryCache.getArtists(providerId, query, naturalOrder)
            }
        ) ?: listOf()
    }

    /** Artists matching [filter], read from the provider page by page. See [FilteredPaging.load]. */
    suspend fun getFilteredArtists(
        filter: LibraryFilter,
        startIndex: Int,
        count: Int,
        query: (startIndex: Int, limit: Int) -> MediaQuery.AlbumArtistListQuery
    ): FilteredPage<MediaModel.Artist> =
        FilteredPaging.load(
            startIndex = startIndex,
            count = count,
            fetch = { start, limit -> getArtists(query(start, limit)) },
            id = { it.id },
            matches = { filter.matches(it) }
        )

    suspend fun getArtistAlbums(artistId: String): List<MediaItem> = coroutineScope {
        offlineManager.fetchOrCache(
            online = {
                it.getAlbumList(MediaQuery.AlbumListQuery(
                    AlbumListSort.NAME,
                    SortOrder.ASC,
                    startIndex = 0,
                    artistIds = listOf(artistId)
                ))
            },
            onSuccess = { providerId, albums -> libraryCache.putArtistAlbums(providerId, artistId, albums) },
            offline = { providerId -> libraryCache.getArtistAlbums(providerId, artistId) }
        )?.map { it.toMediaItem() } ?: listOf()
    }

    suspend fun getArtistDetail(artistId: String): AlbumArtistDetailResponse? = coroutineScope {
        offlineManager.fetchOrCache(
            online = { it.getAlbumArtistDetail(artistId) },
            onSuccess = { providerId, detail ->
                detail?.artist?.let { libraryCache.putArtists(providerId, listOf(it)) }
                detail?.albums?.let { libraryCache.putArtistAlbums(providerId, artistId, it) }
            },
            offline = { providerId -> libraryCache.getArtistDetail(providerId, artistId) }
        )
    }

    suspend fun getArtistInfo(artistId: String): AlbumArtistInfo? = coroutineScope {
        offlineManager.fetchOrCache(
            online = { it.getAlbumArtistInfo(artistId) },
            onSuccess = { providerId, info -> info?.let { libraryCache.putArtistInfo(providerId, artistId, it) } },
            offline = { providerId -> libraryCache.getArtistInfo(providerId, artistId) }
        )
    }
}
