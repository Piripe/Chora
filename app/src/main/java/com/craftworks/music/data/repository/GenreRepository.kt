package com.craftworks.music.data.repository

import com.craftworks.music.data.cache.LibraryCache
import com.craftworks.music.data.model.GenreListSort
import com.craftworks.music.data.model.MediaModel
import com.craftworks.music.data.model.MediaQuery
import com.craftworks.music.data.model.SortOrder
import com.craftworks.music.managers.OfflineManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GenreRepository @Inject constructor(
    private val offlineManager: OfflineManager,
    private val libraryCache: LibraryCache
) {
    suspend fun getGenres(searchTerm: String? = null, limit: Int? = null): List<MediaModel.Genre> =
        offlineManager.fetchOrCache(
            online = {
                it.getGenreList(
                    MediaQuery.GenreListQuery(
                        GenreListSort.NAME,
                        SortOrder.ASC,
                        limit = limit,
                        searchTerm = searchTerm,
                        startIndex = 0
                    )
                )
            },
            offline = { providerId -> libraryCache.getGenres(providerId, searchTerm) }
        ) ?: emptyList()
}
