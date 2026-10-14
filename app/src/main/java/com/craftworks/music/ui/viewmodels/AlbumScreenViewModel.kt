package com.craftworks.music.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import com.craftworks.music.data.model.AlbumListSort
import com.craftworks.music.data.model.LibraryFilter
import com.craftworks.music.data.model.MediaQuery
import com.craftworks.music.data.model.SortOrder
import com.craftworks.music.data.repository.AlbumRepository
import com.craftworks.music.managers.DataRefreshManager
import com.craftworks.music.managers.MediaProviderManager
import com.craftworks.music.managers.settings.AppearanceSettingsManager
import com.craftworks.music.managers.settings.LocalDataSettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val FILTERED_PAGE_SIZE = 50

@HiltViewModel
class AlbumScreenViewModel @Inject constructor(
    private val albumRepository: AlbumRepository,
    private val localDataSettingsManager: LocalDataSettingsManager,
    appearanceSettingsManager: AppearanceSettingsManager
) : ViewModel() {

    private val _allAlbums = MutableStateFlow<List<MediaItem>>(emptyList())
    val allAlbums: StateFlow<List<MediaItem>> = _allAlbums.asStateFlow()

    private val _searchResults = MutableStateFlow<List<MediaItem>>(emptyList())
    val searchResults: StateFlow<List<MediaItem>> = _searchResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.ASC)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _sort = MutableStateFlow(AlbumListSort.NAME)
    val sort: StateFlow<AlbumListSort> = _sort.asStateFlow()

    private val _showFavoritesOnly = MutableStateFlow(false)
    val showFavoritesOnly: StateFlow<Boolean> = _showFavoritesOnly.asStateFlow()

    private val _useAdvancedFilter = MutableStateFlow(false)
    val useAdvancedFilter: StateFlow<Boolean> = _useAdvancedFilter.asStateFlow()

    private val _filter = MutableStateFlow(LibraryFilter())
    val filter: StateFlow<LibraryFilter> = _filter.asStateFlow()

    // Provider offset of the next page when a filter is active (filtered pages are smaller than provider pages).
    private var filteredNextIndex = 0
    private var filteredEndReached = false

    init {
        viewModelScope.launch {
            combine(
                localDataSettingsManager.sortAlbum,
                localDataSettingsManager.sortAlbumOrder,
                localDataSettingsManager.showFavoriteAlbum,
                appearanceSettingsManager.useAdvancedFilter,
                localDataSettingsManager.filterAlbum
            ) { sort, sortOrder, showFavorites, useAdvancedFilter, filter ->
                ListSettings(sort, sortOrder, showFavorites, useAdvancedFilter, filter)
            }
                .distinctUntilChanged()
                .collect { settings ->
                    _sort.value = settings.sort
                    _sortOrder.value = settings.sortOrder
                    _showFavoritesOnly.value = settings.showFavorites
                    _useAdvancedFilter.value = settings.useAdvancedFilter
                    _filter.value = settings.filter
                    getAlbums()
                }
        }

        viewModelScope.launch {
            DataRefreshManager.dataSourceChangedEvent.collect {
                getAlbums()
            }
        }
    }

    private data class ListSettings(
        val sort: AlbumListSort,
        val sortOrder: SortOrder,
        val showFavorites: Boolean,
        val useAdvancedFilter: Boolean,
        val filter: LibraryFilter
    )

    /** The filter to apply, or null when the advanced filter is disabled or has no rule. */
    private fun activeFilter(): LibraryFilter? =
        _filter.value.takeIf { _useAdvancedFilter.value && it.isActive }

    private fun albumQuery(startIndex: Int, limit: Int?, searchTerm: String? = null): MediaQuery.AlbumListQuery {
        val filter = activeFilter()
        val provider = MediaProviderManager.currentProvider.value
        return MediaQuery.AlbumListQuery(
            _sort.value,
            _sortOrder.value,
            artistIds = filter?.serverArtistIds(provider),
            genreIds = filter?.serverGenreIds(provider),
            limit = limit,
            searchTerm = searchTerm,
            startIndex = startIndex,
            favorite = if (_showFavoritesOnly.value || filter?.requiredFavorite == true) true else null
        )
    }

    private var getAlbumsJob: Job? = null
    private var getMoreAlbumsJob: Job? = null
    fun getAlbums() {
        getAlbumsJob?.cancel()
        getMoreAlbumsJob?.cancel()
        _allAlbums.value = emptyList()

        getAlbumsJob = viewModelScope.launch {
            try {
                _isLoading.value = true
                val filter = activeFilter()
                if (filter != null) {
                    val page = albumRepository.getFilteredAlbums(filter, 0, FILTERED_PAGE_SIZE) { start, limit -> albumQuery(start, limit) }
                    filteredNextIndex = page.nextStartIndex
                    filteredEndReached = page.endReached
                    _allAlbums.value = page.items
                    return@launch
                }
                _allAlbums.value = albumRepository.getAlbums(
                    MediaQuery.AlbumListQuery(
                        _sort.value,
                        _sortOrder.value,
                        limit = 50,
                        startIndex = 0,
                        favorite = if (_showFavoritesOnly.value) true else null
                    )
                )
            }
            finally {
                _isLoading.value = false
            }
        }
    }

    suspend fun getAlbum(id: String): List<MediaItem> {
        return albumRepository.getAlbum(id) ?: emptyList()
    }

    fun getMoreAlbums(size: Int){
        println("GETTING MORE ALBUMS")
        val filter = activeFilter()
        if (filter != null) {
            if (filteredEndReached || getAlbumsJob?.isActive == true || getMoreAlbumsJob?.isActive == true) return
            getMoreAlbumsJob = viewModelScope.launch {
                try {
                    _isLoading.value = true
                    val page = albumRepository.getFilteredAlbums(filter, filteredNextIndex, size) { start, limit -> albumQuery(start, limit) }
                    filteredNextIndex = page.nextStartIndex
                    filteredEndReached = page.endReached
                    val knownIds = _allAlbums.value.mapTo(HashSet()) { it.mediaId }
                    _allAlbums.value += page.items.filter { it.mediaId !in knownIds }
                }
                finally {
                    _isLoading.value = false
                }
            }
            return
        }
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val albumOffset = _allAlbums.value.size
                val newAlbums = albumRepository.getAlbums(
                    MediaQuery.AlbumListQuery(
                        _sort.value,
                        _sortOrder.value,
                        limit = size,
                        startIndex = albumOffset,
                        favorite = if (_showFavoritesOnly.value) true else null
                    )
                )
                _allAlbums.value += newAlbums
            }
            finally {
                _isLoading.value = false
            }
        }
    }

    fun search(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val filter = activeFilter()
                if (filter != null) {
                    _searchResults.value = albumRepository.getFilteredAlbums(filter, 0, FILTERED_PAGE_SIZE) { start, limit ->
                        albumQuery(start, limit, query)
                    }.items
                    return@launch
                }
                _searchResults.value = albumRepository.getAlbums(
                    MediaQuery.AlbumListQuery(
                        _sort.value,
                        _sortOrder.value,
                        searchTerm = query,
                        startIndex = 0,
                        favorite = if (_showFavoritesOnly.value) true else null
                    )
                )
            }
            finally {
                _isLoading.value = false
            }
        }
    }

    fun setSorting(newSort: AlbumListSort) {
        viewModelScope.launch {
            localDataSettingsManager.saveSortAlbum(newSort)
        }
    }
    fun setOrder(newSortOrder: SortOrder) {
        viewModelScope.launch {
            localDataSettingsManager.saveSortAlbumOrder(newSortOrder)
        }
    }
    fun setShowFavoritesOnly(showFavorites: Boolean) {
        viewModelScope.launch {
            localDataSettingsManager.saveShowFavoriteAlbum(showFavorites)
        }
    }
    fun setSortAndOrder(newSort: AlbumListSort, newSortOrder: SortOrder) {
        viewModelScope.launch {
            localDataSettingsManager.saveSortAlbumAndOrder(newSort, newSortOrder)
        }
    }
    fun setFilter(newFilter: LibraryFilter) {
        viewModelScope.launch {
            localDataSettingsManager.saveFilterAlbum(newFilter)
        }
    }
}