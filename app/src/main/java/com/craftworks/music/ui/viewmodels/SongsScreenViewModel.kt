package com.craftworks.music.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.StarRating
import com.craftworks.music.data.model.LibraryFilter
import com.craftworks.music.data.model.LibraryType
import com.craftworks.music.data.model.MediaQuery
import com.craftworks.music.data.model.SongListSort
import com.craftworks.music.data.model.SortOrder
import com.craftworks.music.data.model.favorite
import com.craftworks.music.data.model.id
import com.craftworks.music.data.repository.SongRepository
import com.craftworks.music.data.repository.StarredRepository
import com.craftworks.music.managers.DataRefreshManager
import com.craftworks.music.managers.MediaProviderManager
import com.craftworks.music.managers.settings.AppearanceSettingsManager
import com.craftworks.music.managers.settings.LocalDataSettingsManager
import com.craftworks.music.managers.settings.MiscSettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val FILTERED_PAGE_SIZE = 100

@HiltViewModel
class SongsScreenViewModel @Inject constructor(
    private val songRepository: SongRepository,
    private val starredRepository: StarredRepository,
    private val localDataSettingsManager: LocalDataSettingsManager,
    private val miscSettingsManager: MiscSettingsManager,
    appearanceSettingsManager: AppearanceSettingsManager
) : ViewModel() {

    private val _allSongs = MutableStateFlow<List<MediaItem>>(emptyList())
    val allSongs: StateFlow<List<MediaItem>> = _allSongs.asStateFlow()

    private val _searchResults = MutableStateFlow<List<MediaItem>>(emptyList())
    val searchResults: StateFlow<List<MediaItem>> = _searchResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.ASC)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _sort = MutableStateFlow(SongListSort.NAME)
    val sort: StateFlow<SongListSort> = _sort.asStateFlow()

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
                localDataSettingsManager.sortSong,
                localDataSettingsManager.sortSongOrder,
                localDataSettingsManager.showFavoriteSong,
                appearanceSettingsManager.useAdvancedFilter,
                localDataSettingsManager.filterSong
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
                    getSongs()
                }
        }

        viewModelScope.launch {
            DataRefreshManager.dataSourceChangedEvent.collect {
                getSongs()
            }
        }
    }

    private data class ListSettings(
        val sort: SongListSort,
        val sortOrder: SortOrder,
        val showFavorites: Boolean,
        val useAdvancedFilter: Boolean,
        val filter: LibraryFilter
    )

    /** The filter to apply, or null when the advanced filter is disabled or has no rule. */
    private fun activeFilter(): LibraryFilter? =
        _filter.value.takeIf { _useAdvancedFilter.value && it.isActive }

    private fun songQuery(startIndex: Int, limit: Int?, searchTerm: String? = null): MediaQuery.SongListQuery {
        val filter = activeFilter()
        val provider = MediaProviderManager.currentProvider.value
        return MediaQuery.SongListQuery(
            sortBy = _sort.value,
            sortOrder = _sortOrder.value,
            albumIds = filter?.serverAlbumIds(provider),
            artistIds = filter?.serverArtistIds(provider),
            genreIds = filter?.serverGenreIds(provider),
            limit = limit,
            searchTerm = searchTerm,
            startIndex = startIndex,
            favorite = if (_showFavoritesOnly.value || filter?.requiredFavorite == true) true else null
        )
    }

    private var getSongsJob: Job? = null
    private var getMoreSongsJob: Job? = null
    fun getSongs() {
        getSongsJob?.cancel()
        getMoreSongsJob?.cancel()

        getSongsJob = viewModelScope.launch {
            try {
                _isLoading.value = true
                val filter = activeFilter()
                if (filter != null) {
                    val page = songRepository.getFilteredSongs(filter, 0, FILTERED_PAGE_SIZE) { start, limit -> songQuery(start, limit) }
                    filteredNextIndex = page.nextStartIndex
                    filteredEndReached = page.endReached
                    _allSongs.value = page.items
                    return@launch
                }
                _allSongs.value = songRepository.getSongs(
                    MediaQuery.SongListQuery(
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

    fun getMoreSongs(size: Int){
        val filter = activeFilter()
        if (filter != null) {
            if (filteredEndReached || getSongsJob?.isActive == true || getMoreSongsJob?.isActive == true) return
            getMoreSongsJob = viewModelScope.launch {
                try {
                    _isLoading.value = true
                    val page = songRepository.getFilteredSongs(filter, filteredNextIndex, size) { start, limit -> songQuery(start, limit) }
                    filteredNextIndex = page.nextStartIndex
                    filteredEndReached = page.endReached
                    val knownIds = _allSongs.value.mapTo(HashSet()) { it.mediaId }
                    _allSongs.value += page.items.filter { it.mediaId !in knownIds }
                }
                finally {
                    _isLoading.value = false
                }
            }
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            coroutineScope {
                val songOffset = _allSongs.value.size
                _allSongs.value += songRepository.getSongs(
                    MediaQuery.SongListQuery(
                        sortBy = _sort.value,
                        sortOrder = _sortOrder.value,
                        limit = size,
                        startIndex = songOffset,
                        favorite = if (_showFavoritesOnly.value) true else null
                    )
                )
            }
            _isLoading.value = false
        }
    }

    fun search(query: String){
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            val filter = activeFilter()
            if (filter != null) {
                try {
                    _searchResults.value = songRepository.getFilteredSongs(filter, 0, FILTERED_PAGE_SIZE) { start, limit ->
                        songQuery(start, limit, query)
                    }.items
                }
                finally {
                    _isLoading.value = false
                }
                return@launch
            }
            coroutineScope {
                _searchResults.value = songRepository.getSongs(
                    MediaQuery.SongListQuery(
                        sortBy = _sort.value,
                        sortOrder = _sortOrder.value,
                        searchTerm = query,
                        startIndex = 0,
                        favorite = if (_showFavoritesOnly.value) true else null
                    )
                )
            }
            _isLoading.value = false
        }
    }
    fun setSorting(newSort: SongListSort) {
        viewModelScope.launch {
            localDataSettingsManager.saveSortSong(newSort)
        }
    }
    fun setOrder(newSortOrder: SortOrder) {
        viewModelScope.launch {
            localDataSettingsManager.saveSortSongOrder(newSortOrder)
        }
    }
    fun setShowFavoritesOnly(showFavorites: Boolean) {
        viewModelScope.launch {
            localDataSettingsManager.saveShowFavoriteSong(showFavorites)
        }
    }
    fun setSortAndOrder(newSort: SongListSort, newSortOrder: SortOrder) {
        viewModelScope.launch {
            localDataSettingsManager.saveSortSongAndOrder(newSort, newSortOrder)
        }
    }
    fun setFilter(newFilter: LibraryFilter) {
        viewModelScope.launch {
            localDataSettingsManager.saveFilterSong(newFilter)
        }
    }
    fun downloadSong(song: MediaItem) {
        viewModelScope.launch {
            songRepository.downloadSong(song.mediaMetadata, miscSettingsManager.downloadTemplateFlow.first())
        }
    }

    fun setSongRating(
        songId: String,
        rating: Int
    ) {
        val song = _allSongs.value.firstOrNull {
            it.mediaMetadata.id == songId
        } ?: _searchResults.value.firstOrNull {
            it.mediaMetadata.id == songId
        }

        val maxStars = (song?.mediaMetadata?.userRating as? StarRating)?.maxStars ?: 5

        val updatedSong = song?.buildUpon()?.setMediaMetadata(
            song.mediaMetadata.buildUpon()
                .setUserRating(StarRating(maxStars, rating.toFloat()))
                .build()
        )?.build()

        if (updatedSong != null) {
            _allSongs.value = _allSongs.value.map { item ->
                if (item.mediaId == song.mediaId) updatedSong else item
            }
            _searchResults.value = _searchResults.value.map { item ->
                if (item.mediaId == song.mediaId) updatedSong else item
            }
        }

        viewModelScope.launch {
            songRepository.setSongRating(songId, rating)
        }
    }

    fun isSongStarred(metadata: MediaMetadata): Boolean =
        starredRepository.isStarred(metadata.id ?: "", LibraryType.SONG, metadata.favorite ?: false)

    fun songStarredFlow(metadata: MediaMetadata): Flow<Boolean> =
        starredRepository.isStarredFlow(metadata.id ?: "", LibraryType.SONG, metadata.favorite ?: false)

    fun toggleSongStar(metadata: MediaMetadata) {
        val id = metadata.id ?: return
        viewModelScope.launch {
            starredRepository.toggleStar(id, LibraryType.SONG, isSongStarred(metadata))
        }
    }
}