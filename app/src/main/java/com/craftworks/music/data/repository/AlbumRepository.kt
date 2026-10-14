package com.craftworks.music.data.repository

import androidx.media3.common.MediaItem
import com.craftworks.music.data.cache.LibraryCache
import com.craftworks.music.data.model.MediaQuery
import com.craftworks.music.managers.MediaProviderManager
import com.craftworks.music.managers.OfflineManager
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlbumRepository @Inject constructor(
    private val offlineManager: OfflineManager,
    private val libraryCache: LibraryCache
) {
    suspend fun getAlbums(
        query: MediaQuery.AlbumListQuery
    ): List<MediaItem> = coroutineScope {
        offlineManager.fetchOrCache(
            online = { it.getAlbumList(query) },
            onSuccess = { providerId, albums -> libraryCache.putAlbums(providerId, albums) },
            offline = { providerId ->
                val naturalOrder = MediaProviderManager.currentProvider.value?.supportAlbumSortOrder == false
                libraryCache.getAlbums(providerId, query, naturalOrder)
            }
        )?.map { it.toMediaItem() } ?: listOf()
    }

    suspend fun getAlbum(albumId: String): List<MediaItem>? = coroutineScope {
        val album = offlineManager.fetchOrCache(
            online = { it.getAlbumDetail(albumId) },
            onSuccess = { providerId, album -> album?.let { libraryCache.putAlbums(providerId, listOf(it)) } },
            offline = { providerId -> libraryCache.getAlbum(providerId, albumId) }
        )
        if (album != null) listOf(album.toMediaItem()) + (album.songs.map { it.toMediaItem() }) else null
    }
}
