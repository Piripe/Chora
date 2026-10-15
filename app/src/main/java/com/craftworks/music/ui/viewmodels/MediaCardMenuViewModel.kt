package com.craftworks.music.ui.viewmodels

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import com.craftworks.music.R
import com.craftworks.music.data.model.DownloadTarget
import com.craftworks.music.data.model.LibraryType
import com.craftworks.music.data.model.MediaModel
import com.craftworks.music.data.repository.AlbumRepository
import com.craftworks.music.data.repository.ArtistRepository
import com.craftworks.music.data.repository.MixRepository
import com.craftworks.music.data.repository.SongRepository
import com.craftworks.music.data.repository.StarredRepository
import com.craftworks.music.managers.settings.MiscSettingsManager
import com.craftworks.music.player.SongHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the long press menus of the album and artist cards, see AlbumCardMenu and ArtistCardMenu,
 * and the radio / instant mix actions of the song menus.
 */
@HiltViewModel
class MediaCardMenuViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val albumRepository: AlbumRepository,
    private val artistRepository: ArtistRepository,
    private val mixRepository: MixRepository,
    private val songRepository: SongRepository,
    private val starredRepository: StarredRepository,
    private val miscSettingsManager: MiscSettingsManager,
) : ViewModel() {

    /** The songs of the album, without the album item [AlbumRepository.getAlbum] puts first. */
    suspend fun getAlbumSongs(albumId: String): List<MediaItem> =
        albumRepository.getAlbum(albumId)?.drop(1) ?: emptyList()

    /** The songs of every album of the artist. */
    suspend fun getArtistSongs(artistId: String): List<MediaItem> {
        val albums = artistRepository.getArtistDetail(artistId)?.albums?.takeIf { it.isNotEmpty() }?.map { it.toMediaItem() }
            ?: artistRepository.getArtistAlbums(artistId)
        return albums.flatMap { album ->
            album.mediaMetadata.extras?.getString("id")?.let { getAlbumSongs(it) } ?: emptyList()
        }
    }

    /** Songs similar to the album, see [MixRepository.getAlbumRadio]. */
    suspend fun getAlbumRadio(album: MediaItem): List<MediaItem> =
        mixRepository.getAlbumRadio(album.mediaMetadata)

    /** Songs similar to the artist, see [MixRepository.getArtistRadio]. */
    suspend fun getArtistRadio(artist: MediaModel.Artist): List<MediaItem> =
        mixRepository.getArtistRadio(artist)

    /** The song followed by similar songs, see [MixRepository.getInstantMix]. */
    suspend fun getInstantMix(song: MediaItem): List<MediaItem> =
        mixRepository.getInstantMix(song)

    /**
     * Replaces the queue with the mix [getMix] returns and plays it, outliving the menu that started it.
     * Tells the user when no songs were found, else calls [onPlay].
     */
    fun playMix(getMix: suspend () -> List<MediaItem>, onPlay: () -> Unit = {}, mediaController: () -> MediaController?) {
        viewModelScope.launch {
            val songs = getMix()
            if (songs.isEmpty()) {
                Toast.makeText(context, R.string.radio_no_songs_found, Toast.LENGTH_SHORT).show()
                return@launch
            }
            SongHelper.play(songs, 0, mediaController())
            onPlay()
        }
    }

    /** Runs [action] with the songs [getSongs] returns, outliving the menu that started it. */
    fun withSongs(getSongs: suspend () -> List<MediaItem>, action: suspend (songs: List<MediaItem>) -> Unit) {
        viewModelScope.launch {
            val songs = getSongs()
            if (songs.isNotEmpty()) action(songs)
        }
    }

    fun setStarred(id: String, type: LibraryType, starred: Boolean) {
        viewModelScope.launch {
            if (starred) starredRepository.starItem(listOf(id), type)
            else starredRepository.unStarItem(listOf(id), type)
        }
    }

    /** Downloads the songs [getSongs] returns, so the menu can close before they are fetched. */
    fun download(target: DownloadTarget, getSongs: suspend () -> List<MediaItem>) {
        withSongs(getSongs) { songs ->
            if (target == DownloadTarget.APP) return@withSongs songRepository.downloadInApp(songs.map { it.mediaMetadata })
            val template = miscSettingsManager.downloadTemplateFlow.first()
            songs.forEach { song ->
                songRepository.downloadSong(song.mediaMetadata, template)
            }
        }
    }
}
