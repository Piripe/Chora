package com.craftworks.music.data.repository

import androidx.media3.common.MediaItem
import com.craftworks.music.data.cache.LibraryCache
import com.craftworks.music.data.model.MediaQuery
import com.craftworks.music.managers.OfflineManager
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepository @Inject constructor(
    private val offlineManager: OfflineManager,
    private val libraryCache: LibraryCache
) {

    suspend fun getPlaylists(query: MediaQuery.PlaylistListQuery): List<MediaItem> = coroutineScope {
        offlineManager.fetchOrCache(
            online = { it.getPlaylistList(query) },
            onSuccess = { providerId, playlists -> libraryCache.putPlaylists(providerId, playlists) },
            offline = { providerId -> libraryCache.getPlaylists(providerId, query) }
        )?.map { it.toMediaItem() } ?: listOf()
    }

    suspend fun getPlaylistSongs(playlistId: String): List<MediaItem> = coroutineScope {
        offlineManager.fetchOrCache(
            online = { it.getPlaylistSongList(playlistId) },
            onSuccess = { providerId, songs -> libraryCache.putPlaylistSongs(providerId, playlistId, songs) },
            offline = { providerId -> libraryCache.getPlaylistSongs(providerId, playlistId) }
        )?.map { it.toMediaItem() } ?: listOf()
    }

    suspend fun createPlaylist(name: String, comment: String = "", songsToAdd: List<String>, public: Boolean) {
        val playlistId = offlineManager.runOrDefault(null) { it.createPlaylist(name, comment, public = public) }
        if (playlistId != null) addSongsToPlaylist(playlistId, songsToAdd)
    }

    suspend fun addSongsToPlaylist(playlistId: String, songsToAdd: List<String>) {
        offlineManager.runOrDefault(false) { it.addToPlaylist(playlistId, songsToAdd) }
    }

    suspend fun removeSongsFromPlaylist(playlistId: String, songsToRemove: List<String>) {
        offlineManager.runOrDefault(false) { it.removeFromPlaylist(playlistId, songsToRemove) }
    }

    suspend fun deletePlaylist(playlistId: String) {
        offlineManager.runOrDefault(false) { it.deletePlaylist(playlistId) }
    }
}
