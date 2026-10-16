package com.craftworks.music.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import com.craftworks.music.data.model.DownloadTarget
import com.craftworks.music.data.model.LibraryType
import com.craftworks.music.data.model.MediaModel
import com.craftworks.music.data.repository.AlbumRepository
import com.craftworks.music.data.repository.ArtistRepository
import com.craftworks.music.data.repository.SongRepository
import com.craftworks.music.data.repository.StarredRepository
import com.craftworks.music.managers.settings.AppearanceSettingsManager
import com.craftworks.music.managers.settings.MiscSettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

// Scoped to one ArtistDetails back stack entry, so stacked artist screens don't overwrite each other.
@HiltViewModel
class ArtistDetailsViewModel @Inject constructor(
    private val artistRepository: ArtistRepository,
    private val albumRepository: AlbumRepository,
    private val songRepository: SongRepository,
    private val starredRepository: StarredRepository,
    private val miscSettingsManager: MiscSettingsManager,
    appearanceSettingsManager: AppearanceSettingsManager
) : ViewModel() {
    private val _selectedArtist = MutableStateFlow<MediaModel.Artist?>(null)
    val selectedArtist: StateFlow<MediaModel.Artist?> = _selectedArtist.asStateFlow()

    private val _artistAlbums = MutableStateFlow<List<MediaItem>>(emptyList())
    val artistAlbums: StateFlow<List<MediaItem>> = _artistAlbums.asStateFlow()

    /** Albums of other artists holding at least one song of the artist. */
    private val _appearsOn = MutableStateFlow<List<MediaItem>>(emptyList())
    val appearsOn: StateFlow<List<MediaItem>> = _appearsOn.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val actionButtons = appearanceSettingsManager.artistDetailsButtons

    private var loadedArtistId: String? = null
    private var loadJob: Job? = null

    /**
     * Loads the artist details. Does nothing if they are already loaded for [artistId], unless [force] is set,
     * so coming back to the screen doesn't clear and reload it.
     * [initialArtist] is shown while the details load.
     */
    fun loadArtistDetails(artistId: String, initialArtist: MediaModel.Artist? = null, force: Boolean = false) {
        if (!force && loadedArtistId == artistId) return

        if (loadedArtistId != artistId) {
            _selectedArtist.value = initialArtist?.takeIf { it.id == artistId }
            _artistAlbums.value = emptyList()
            _appearsOn.value = emptyList()
        }
        loadedArtistId = artistId

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val loadingJob = launch {
                delay(1000)
                if (_artistAlbums.value.isEmpty()) {
                    _isLoading.value = true
                }
            }
            try {
                val infoDeferred = async { artistRepository.getArtistInfo(artistId) }

                val artistDetail = artistRepository.getArtistDetail(artistId)

                // Offline, the artist may not be cached: keep the one shown while loading.
                _selectedArtist.value = artistDetail?.artist ?: _selectedArtist.value
                if (artistDetail?.albums.isNullOrEmpty()) {
                    _artistAlbums.value = artistRepository.getArtistAlbums(artistId)
                } else {
                    _artistAlbums.value = artistDetail.albums.map { it.toMediaItem() }
                }

                _selectedArtist.value?.name?.let { artistName ->
                    launch { loadAppearances(artistId, artistName) }
                }

                val artistInfo = infoDeferred.await()
                _selectedArtist.value = _selectedArtist.value?.copy(
                    biography = artistInfo?.biography ?: _selectedArtist.value?.biography,
                    similarArtists = artistInfo?.similarArtists ?: _selectedArtist.value?.similarArtists ?: emptyList()
                )
            }
            finally {
                loadingJob.cancel()
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadAppearances(artistId: String, artistName: String) {
        val albums = try {
            artistRepository.getArtistAppearances(artistId, artistName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("ArtistDetailsViewModel", "Failed to get the albums $artistName appears on", e)
            return
        }
        val ownAlbumIds = _artistAlbums.value.mapTo(HashSet()) { it.mediaId }
        _appearsOn.value = albums
            .filter { album ->
                album.id !in ownAlbumIds && album.artists.none { it.id == artistId }
                    && album.participants["albumartist"]?.any { it.id == artistId } != true
            }
            .distinctBy { it.id }
            .sortedWith(compareByDescending<MediaModel.Album> { it.releaseYear ?: 0 }.thenBy { it.name.lowercase() })
            .map { it.toMediaItem() }
    }

    suspend fun getAlbum(id: String): List<MediaItem> {
        return albumRepository.getAlbum(id) ?: emptyList()
    }

    fun starArtist(id: String) {
        viewModelScope.launch {
            starredRepository.starItem(listOf(id), LibraryType.ARTIST)
        }
    }
    fun unstarArtist(id: String) {
        viewModelScope.launch {
            starredRepository.unStarItem(listOf(id), LibraryType.ARTIST)
        }
    }
    fun downloadArtist(songs: List<MediaItem>, target: DownloadTarget) {
        if (target == DownloadTarget.APP) return songRepository.downloadInApp(songs.map { it.mediaMetadata })
        viewModelScope.launch {
            val template = miscSettingsManager.downloadTemplateFlow.first()
            songs.forEach { song ->
                songRepository.downloadSong(song.mediaMetadata, template)
            }
        }
    }
}
