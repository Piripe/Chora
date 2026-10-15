package com.craftworks.music.data.repository

import androidx.media3.common.MediaItem
import com.craftworks.music.data.cache.LibraryCache
import com.craftworks.music.data.model.LibraryType
import com.craftworks.music.data.model.MediaModel
import com.craftworks.music.data.model.MediaQuery
import com.craftworks.music.data.model.ProviderFeature
import com.craftworks.music.data.model.SongListSort
import com.craftworks.music.data.model.SortOrder
import com.craftworks.music.managers.MediaProviderManager
import com.craftworks.music.managers.OfflineManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderRepository @Inject constructor(
    private val offlineManager: OfflineManager,
    private val libraryCache: LibraryCache
) {
    /**
     * The folder with [id] and its content ([MediaModel.Folder.ROOT_ID] for the top-level folder),
     * null when it can't be loaded (the provider doesn't support folders, or offline and not cached).
     * The image URL of the subfolders is resolved.
     */
    suspend fun getFolder(id: String): MediaModel.Folder? = withContext(Dispatchers.IO) {
        val folder = offlineManager.fetchOrCache<MediaModel.Folder?>(
            online = { provider ->
                if (ProviderFeature.FOLDERS !in provider.featureFlags) null
                else provider.getFolder(MediaQuery.FolderQuery(SongListSort.NAME, SortOrder.ASC, id = id))
            },
            onSuccess = { providerId, folder -> folder?.let { libraryCache.putFolder(providerId, it) } },
            offline = { providerId -> libraryCache.getFolder(providerId, id) }
        ) ?: return@withContext null

        val provider = MediaProviderManager.getProvider(folder.providerId)
        folder.copy(
            children = folder.children?.let { children ->
                children.copy(folders = children.folders.map { subfolder ->
                    subfolder.copy(
                        imageUrl = subfolder.imageUrl ?: subfolder.imageId?.let {
                            provider?.getImageUrl(it, LibraryType.ALBUM, 300)
                        }
                    )
                })
            }
        )
    }

    /** The songs of [folder] and of its subfolders, see [com.craftworks.music.data.providers.media.MediaProvider.getFolderSongs]. */
    suspend fun getFolderSongs(folder: MediaModel.Folder): List<MediaItem> = withContext(Dispatchers.IO) {
        offlineManager.fetchOrCache(
            online = { provider ->
                if (ProviderFeature.FOLDERS !in provider.featureFlags) emptyList()
                else provider.getFolderSongs(folder.id)
            },
            offline = { providerId -> libraryCache.getFolderSongs(providerId, folder.id) }
        ).orEmpty().map { it.toMediaItem() }
    }
}
