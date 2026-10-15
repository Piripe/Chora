package com.craftworks.music.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import com.craftworks.music.data.model.AlbumListSort
import com.craftworks.music.data.model.MediaQuery
import com.craftworks.music.data.model.SortOrder
import com.craftworks.music.data.repository.AlbumRepository
import com.craftworks.music.managers.DataRefreshManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeScreenViewModel @Inject constructor(
    private val albumRepository: AlbumRepository
) : ViewModel() {
    private val _recentlyPlayedAlbums = MutableStateFlow<List<MediaItem>>(emptyList())
    val recentlyPlayedAlbums: StateFlow<List<MediaItem>> = _recentlyPlayedAlbums.asStateFlow()

    private val _recentAlbums = MutableStateFlow<List<MediaItem>>(emptyList())
    val recentAlbums: StateFlow<List<MediaItem>> = _recentAlbums.asStateFlow()

    private val _mostPlayedAlbums = MutableStateFlow<List<MediaItem>>(emptyList())
    val mostPlayedAlbums: StateFlow<List<MediaItem>> = _mostPlayedAlbums.asStateFlow()

    private val _shuffledAlbums = MutableStateFlow<List<MediaItem>>(emptyList())
    val shuffledAlbums: StateFlow<List<MediaItem>> = _shuffledAlbums.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _refreshingSections = MutableStateFlow<Set<String>>(emptySet())
    /** Keys of the home sections currently being reloaded by [refreshSection]. */
    val refreshingSections: StateFlow<Set<String>> = _refreshingSections.asStateFlow()

    init {
        loadHomeScreenData()

        viewModelScope.launch {
            DataRefreshManager.dataSourceChangedEvent.collect {
                loadHomeScreenData()
            }
        }
    }

    fun loadHomeScreenData() {
        viewModelScope.launch {
            _isLoading.value = true
            coroutineScope {
                HOME_SECTIONS.map { key -> async { loadSection(key) } }.forEach { it.await() }
            }
            _isLoading.value = false
        }
    }

    /** Reloads only the home section with this [key] (see [HOME_SECTIONS]). */
    fun refreshSection(key: String) {
        if (key !in HOME_SECTIONS || key in _refreshingSections.value) return
        viewModelScope.launch {
            _refreshingSections.update { it + key }
            try {
                loadSection(key)
            } finally {
                _refreshingSections.update { it - key }
            }
        }
    }

    private suspend fun loadSection(key: String) {
        val (sort, order) = when (key) {
            "recently_played" -> AlbumListSort.RECENTLY_PLAYED to SortOrder.DESC
            "recently_added" -> AlbumListSort.RECENTLY_ADDED to SortOrder.DESC
            "most_played" -> AlbumListSort.PLAY_COUNT to SortOrder.DESC
            "random_songs" -> AlbumListSort.RANDOM to SortOrder.ASC
            else -> return
        }
        val albums = albumRepository.getAlbums(
            MediaQuery.AlbumListQuery(sort, order, limit = 20, startIndex = 0)
        )
        when (key) {
            "recently_played" -> _recentlyPlayedAlbums.value = albums
            "recently_added" -> _recentAlbums.value = albums
            "most_played" -> _mostPlayedAlbums.value = albums
            "random_songs" -> _shuffledAlbums.value = albums
        }
    }

    suspend fun getAlbumSongs(albumId: String): List<MediaItem> {
        return albumRepository.getAlbum(albumId) ?: emptyList()
    }

    companion object {
        val HOME_SECTIONS = listOf("recently_played", "recently_added", "most_played", "random_songs")
    }
}
