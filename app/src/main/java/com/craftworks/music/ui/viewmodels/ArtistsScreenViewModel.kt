package com.craftworks.music.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.craftworks.music.data.model.AlbumArtistListSort
import com.craftworks.music.data.model.MediaModel
import com.craftworks.music.data.model.MediaQuery
import com.craftworks.music.data.model.SortOrder
import com.craftworks.music.data.repository.ArtistRepository
import com.craftworks.music.managers.DataRefreshManager
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

@HiltViewModel
class ArtistsScreenViewModel @Inject constructor(
    private val artistRepository: ArtistRepository,
    private val localDataSettingsManager: LocalDataSettingsManager
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

    init {
        viewModelScope.launch {
            combine(
                localDataSettingsManager.sortArtist,
                localDataSettingsManager.sortArtistOrder,
                localDataSettingsManager.showFavoriteArtist
            ) { sort, sortOrder, showFavorites -> Triple(sort, sortOrder, showFavorites) }
                .distinctUntilChanged()
                .collect { (sort, sortOrder, showFavorites) ->
                    _sort.value = sort
                    _sortOrder.value = sortOrder
                    _showFavoritesOnly.value = showFavorites
                    getArtists()
                }
        }

        viewModelScope.launch {
            DataRefreshManager.dataSourceChangedEvent.collect {
                getArtists()
            }
        }
    }

    private var getArtistsJob: Job? = null
    fun getArtists() {
        getArtistsJob?.cancel()

        getArtistsJob = viewModelScope.launch {
            try {
                _isLoading.value = true
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
}