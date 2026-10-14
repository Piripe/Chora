package com.craftworks.music.ui.viewmodels

import androidx.lifecycle.ViewModel
import com.craftworks.music.data.model.AlbumArtistListSort
import com.craftworks.music.data.model.AlbumListSort
import com.craftworks.music.data.model.FilterEntity
import com.craftworks.music.data.model.MediaModel
import com.craftworks.music.data.model.MediaQuery
import com.craftworks.music.data.model.SortOrder
import com.craftworks.music.data.model.id
import com.craftworks.music.data.repository.AlbumRepository
import com.craftworks.music.data.repository.ArtistRepository
import com.craftworks.music.data.repository.GenreRepository
import com.craftworks.music.managers.MediaProviderManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Searches the artists, albums and genres picked in the Simple filter. */
@HiltViewModel
class FilterSearchViewModel @Inject constructor(
    private val albumRepository: AlbumRepository,
    private val artistRepository: ArtistRepository,
    private val genreRepository: GenreRepository
) : ViewModel() {

    // Genre lists are small, they are loaded once per provider and searched here.
    private var genres: Pair<String, List<MediaModel.Genre>>? = null

    suspend fun searchArtists(term: String): List<FilterEntity> {
        val providerId = MediaProviderManager.currentProvider.value?.id ?: return emptyList()
        if (term.isBlank()) return emptyList()
        return artistRepository.getArtists(
            MediaQuery.AlbumArtistListQuery(
                sortBy = AlbumArtistListSort.NAME,
                sortOrder = SortOrder.ASC,
                limit = RESULT_COUNT,
                searchTerm = term.trim(),
                startIndex = 0
            )
        )
            // Some providers ignore the search term.
            .filter { it.name.contains(term.trim(), true) }
            .take(RESULT_COUNT)
            .map { FilterEntity(it.id, it.name, providerId) }
    }

    suspend fun searchAlbums(term: String): List<FilterEntity> {
        val providerId = MediaProviderManager.currentProvider.value?.id ?: return emptyList()
        if (term.isBlank()) return emptyList()
        return albumRepository.getAlbums(
            MediaQuery.AlbumListQuery(
                AlbumListSort.NAME,
                SortOrder.ASC,
                limit = RESULT_COUNT,
                searchTerm = term.trim(),
                startIndex = 0
            )
        )
            .filter { it.mediaMetadata.title?.contains(term.trim(), true) == true }
            .take(RESULT_COUNT)
            .map {
                FilterEntity(
                    id = it.mediaMetadata.id ?: it.mediaId,
                    name = it.mediaMetadata.title.toString(),
                    providerId = providerId,
                    detail = it.mediaMetadata.albumArtist?.toString()
                )
            }
    }

    suspend fun searchGenres(term: String): List<FilterEntity> {
        val providerId = MediaProviderManager.currentProvider.value?.id ?: return emptyList()
        val all = genres?.takeIf { it.first == providerId }?.second
            ?: genreRepository.getGenres().also { if (it.isNotEmpty()) genres = providerId to it }
        return all
            .filter { term.isBlank() || it.name.contains(term.trim(), true) }
            .take(RESULT_COUNT)
            .map { FilterEntity(it.id ?: it.name, it.name, providerId) }
    }

    private companion object {
        const val RESULT_COUNT = 20
    }
}
