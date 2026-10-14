package com.craftworks.music.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.craftworks.music.data.model.AlbumArtistListSort
import com.craftworks.music.data.model.LibraryFilter
import com.craftworks.music.data.model.MediaModel
import com.craftworks.music.data.model.MediaQuery
import com.craftworks.music.data.model.SortOrder
import com.craftworks.music.data.repository.ArtistRepository
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
class ArtistsScreenViewModel @Inject constructor(
    private val artistRepository: ArtistRepository,
    private val localDataSettingsManager: LocalDataSettingsManager,
    appearanceSettingsManager: AppearanceSettingsManager
) : ViewModel() {
    private val _allArtists = MutableStateFlow<List<MediaModel.Artist>>(emptyList())
    val allArtists: StateFlow<List<MediaModel.Artist>> = _allArtists.asStateFlow()

    private val _searchResults = MutableStateFlow<List<MediaModel.Artist>>(emptyList())
    val searchResults: StateFlow<List<MediaModel.Artist>> = _searchResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.ASC)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _sort = MutableStateFlow(AlbumArtistListSort.NAME)
    val sort: StateFlow<AlbumArtistListSort> = _sort.asStateFlow()

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
                localDataSettingsManager.sortArtist,
                localDataSettingsManager.sortArtistOrder,
                localDataSettingsManager.showFavoriteArtist,
                appearanceSettingsManager.useAdvancedFilter,
                localDataSettingsManager.filterArtist
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
                    getArtists()
                }
        }

        viewModelScope.launch {
            DataRefreshManager.dataSourceChangedEvent.collect {
                getArtists()
            }
        }
    }

    private data class ListSettings(
        val sort: AlbumArtistListSort,
        val sortOrder: SortOrder,
        val showFavorites: Boolean,
        val useAdvancedFilter: Boolean,
        val filter: LibraryFilter
    )

    /** The filter to apply, or null when the advanced filter is disabled or has no rule. */
    private fun activeFilter(): LibraryFilter? =
        _filter.value.takeIf { _useAdvancedFilter.value && it.isActive }

    private fun artistQuery(startIndex: Int, limit: Int?, searchTerm: String? = null) =
        MediaQuery.AlbumArtistListQuery(
            sortBy = _sort.value,
            sortOrder = _sortOrder.value,
            favorite = if (_showFavoritesOnly.value || activeFilter()?.requiredFavorite == true) true else null,
            genreIds = activeFilter()?.serverGenreIds(MediaProviderManager.currentProvider.value),
            limit = limit,
            searchTerm = searchTerm,
            startIndex = startIndex
        )

    private var getArtistsJob: Job? = null
    fun getArtists() {
        getArtistsJob?.cancel()

        getArtistsJob = viewModelScope.launch {
            try {
                _isLoading.value = true
                val filter = activeFilter()
                if (filter != null) {
                    val page = artistRepository.getFilteredArtists(filter, 0, FILTERED_PAGE_SIZE) { start, limit -> artistQuery(start, limit) }
                    filteredNextIndex = page.nextStartIndex
                    filteredEndReached = page.endReached
                    _allArtists.value = page.items
                    return@launch
                }
                _allArtists.value = artistRepository.getArtists(
                    MediaQuery.AlbumArtistListQuery(
                        sortBy = _sort.value,
                        sortOrder = _sortOrder.value,
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

    fun getMoreArtists() {
        if (_isLoading.value || getArtistsJob?.isActive == true) return

        val filter = activeFilter()
        if (filter != null) {
            if (filteredEndReached) return
            getArtistsJob = viewModelScope.launch {
                try {
                    _isLoading.value = true
                    val page = artistRepository.getFilteredArtists(filter, filteredNextIndex, FILTERED_PAGE_SIZE) { start, limit -> artistQuery(start, limit) }
                    filteredNextIndex = page.nextStartIndex
                    filteredEndReached = page.endReached
                    val knownIds = _allArtists.value.mapTo(HashSet()) { it.id }
                    _allArtists.value += page.items.filter { it.id !in knownIds }
                }
                finally {
                    _isLoading.value = false
                }
            }
            return
        }

        getArtistsJob = viewModelScope.launch {
            try {
                _isLoading.value = true
                _allArtists.value += artistRepository.getArtists(
                    MediaQuery.AlbumArtistListQuery(
                        sortBy = _sort.value,
                        sortOrder = _sortOrder.value,
                        startIndex = _allArtists.value.size,
                        limit = 50,
                        favorite = if (_showFavoritesOnly.value) true else null
                    )
                )
            }
            finally {
                _isLoading.value = false
            }
        }
    }


    private var searchJob: Job? = null
    fun search(query: String) {
        if (query.isBlank())
            return

        searchJob?.cancel()

        searchJob = viewModelScope.launch {
            try {
                _isLoading.value = true
                val filter = activeFilter()
                if (filter != null) {
                    _searchResults.value = artistRepository.getFilteredArtists(filter, 0, FILTERED_PAGE_SIZE) { start, limit ->
                        artistQuery(start, limit, query)
                    }.items
                    return@launch
                }
                _searchResults.value = artistRepository.getArtists(
                    MediaQuery.AlbumArtistListQuery(
                        sortBy = _sort.value,
                        sortOrder = _sortOrder.value,
                        favorite = if (_showFavoritesOnly.value) true else null,
                        startIndex = 0,
                        searchTerm = query
                    )
                )
            }
            finally {
                _isLoading.value = false
            }
        }
    }

    fun setSorting(newSort: AlbumArtistListSort) {
        viewModelScope.launch {
            localDataSettingsManager.saveSortArtist(newSort)
        }
    }
    fun setOrder(newSortOrder: SortOrder) {
        viewModelScope.launch {
            localDataSettingsManager.saveSortArtistOrder(newSortOrder)
        }
    }
    fun setShowFavoritesOnly(showFavorites: Boolean) {
        viewModelScope.launch {
            localDataSettingsManager.saveShowFavoriteArtist(showFavorites)
        }
    }
    fun setSortAndOrder(newSort: AlbumArtistListSort, newSortOrder: SortOrder) {
        viewModelScope.launch {
            localDataSettingsManager.saveSortArtistAndOrder(newSort, newSortOrder)
        }
    }
    fun setFilter(newFilter: LibraryFilter) {
        viewModelScope.launch {
            localDataSettingsManager.saveFilterArtist(newFilter)
        }
    }
}