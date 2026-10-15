package com.craftworks.music.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import com.craftworks.music.data.model.MediaModel
import com.craftworks.music.data.repository.FolderRepository
import com.craftworks.music.managers.DataRefreshManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class FoldersScreenViewModel @Inject constructor(
    private val folderRepository: FolderRepository
) : ViewModel() {

    /**
     * The visited locations, the current one last. Each location is the path of the folder:
     * the folders from the root to it. Back returns to the previous location, Up to the parent folder.
     */
    private val history = MutableStateFlow<List<List<MediaModel.Folder>>>(emptyList())

    /** The folders from the root to the current one. */
    val path: StateFlow<List<MediaModel.Folder>> = history
        .map { it.lastOrNull().orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Whether [goBack] has a folder to return to. */
    val canGoBack: StateFlow<Boolean> = history
        .map { it.size > 1 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val currentFolder: StateFlow<MediaModel.Folder?> = history
        .map { it.lastOrNull()?.lastOrNull() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Filters the content of the current folder, empty to show everything. */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** The subfolders of the current folder matching [searchQuery]. */
    val visibleFolders: StateFlow<List<MediaModel.Folder>> = combine(currentFolder, _searchQuery) { folder, query ->
        folder?.children?.folders.orEmpty().filter { query.isBlank() || it.name.contains(query.trim(), true) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The songs of the current folder matching [searchQuery]. */
    val visibleSongs: StateFlow<List<MediaItem>> = combine(currentFolder, _searchQuery) { folder, query ->
        folder?.children?.songs.orEmpty()
            .filter { song ->
                query.isBlank() || listOfNotNull(song.name, song.artistName, song.album, song.path?.substringAfterLast('/'))
                    .any { it.contains(query.trim(), true) }
            }
            .map { it.toMediaItem() }
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The content of a folder unfolded in the tree view. */
    private class TreeContent(val folders: List<MediaModel.Folder>, val songs: List<MediaItem>)

    /** The folders unfolded in the tree view, with their content (null while it loads). */
    private val expandedFolders = MutableStateFlow<Map<String, TreeContent?>>(emptyMap())

    /** The rows of the tree view: the listed content, with the content of the unfolded folders below them. */
    val treeEntries: StateFlow<List<TreeEntry>> = combine(visibleFolders, visibleSongs, expandedFolders) { folders, songs, expanded ->
        buildList { addTree(folders, songs, 0, "", expanded) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var loadJob: Job? = null

    init {
        openRoot()

        viewModelScope.launch {
            DataRefreshManager.dataSourceChangedEvent.collect {
                openRoot()
            }
        }
    }

    private fun openRoot() {
        expandedFolders.value = emptyMap()
        load(MediaModel.Folder.ROOT_ID) { root -> history.value = listOfNotNull(root?.let { listOf(it) }) }
    }

    /** Unfolds [folder] in the tree view, or folds it back. */
    fun toggleFolder(folder: MediaModel.Folder) {
        if (folder.id in expandedFolders.value) {
            expandedFolders.update { it - folder.id }
            return
        }
        expandedFolders.update { it + (folder.id to null) }
        loadTreeContent(folder.id)
    }

    private fun loadTreeContent(folderId: String) {
        viewModelScope.launch {
            val content = folderRepository.getFolder(folderId)?.children?.let { children ->
                withContext(Dispatchers.IO) { TreeContent(children.folders, children.songs.map { it.toMediaItem() }) }
            }
            expandedFolders.update {
                when {
                    folderId !in it -> it // Folded meanwhile.
                    content == null -> it - folderId
                    else -> it + (folderId to content)
                }
            }
        }
    }

    private fun MutableList<TreeEntry>.addTree(
        folders: List<MediaModel.Folder>,
        songs: List<MediaItem>,
        depth: Int,
        parentKey: String,
        expanded: Map<String, TreeContent?>
    ) {
        folders.forEach { folder ->
            val isExpanded = folder.id in expanded
            val content = expanded[folder.id]
            val key = "$parentKey/${folder.id}"
            add(TreeEntry.FolderEntry(folder, depth, isExpanded, isLoading = isExpanded && content == null, key = "folder:$key"))
            if (content != null && depth < MAX_TREE_DEPTH) addTree(content.folders, content.songs, depth + 1, key, expanded)
        }
        songs.forEachIndexed { index, song ->
            add(TreeEntry.SongEntry(song, depth, songs, index, key = "song:$parentKey:$index"))
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun openFolder(folder: MediaModel.Folder) {
        val path = path.value
        load(folder.id) { loaded ->
            // Providers may not know the name of the folder itself.
            if (loaded != null) navigate(path + loaded.copy(name = loaded.name.ifBlank { folder.name }))
        }
    }

    /** Opens the folder at [index] in [path] (an ancestor of the current folder). */
    fun openAncestor(index: Int) {
        val path = path.value
        if (index !in 0 until path.lastIndex) return
        navigate(path.take(index + 1))
    }

    /** Opens the parent folder, returns false when already at the root. */
    fun goUp(): Boolean {
        if (path.value.size <= 1) return false
        openAncestor(path.value.lastIndex - 1)
        return true
    }

    /** Returns to the previously opened folder, returns false when there is none. */
    fun goBack(): Boolean {
        if (history.value.size <= 1) return false
        cancelLoad()
        _searchQuery.value = ""
        history.value = history.value.dropLast(1)
        return true
    }

    fun refresh() {
        // The unfolded folders are loaded again too.
        expandedFolders.value.keys.forEach { loadTreeContent(it) }
        val path = path.value
        val current = path.lastOrNull() ?: return openRoot()
        load(current.id) { loaded ->
            if (loaded != null)
                history.value = history.value.dropLast(1) + listOf(path.dropLast(1) + loaded.copy(name = loaded.name.ifBlank { current.name }))
        }
    }

    /** The songs the actions of the screen apply to: those of the listed folders (and their subfolders), then the listed songs. */
    suspend fun getVisibleSongs(): List<MediaItem> =
        visibleFolders.value.flatMap { folderRepository.getFolderSongs(it) } + visibleSongs.value

    private fun navigate(newPath: List<MediaModel.Folder>) {
        cancelLoad()
        _searchQuery.value = ""
        history.value = history.value + listOf(newPath)
    }

    private fun cancelLoad() {
        loadJob?.cancel()
        _isLoading.value = false
    }

    private fun load(id: String, onLoaded: (MediaModel.Folder?) -> Unit) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                val folder = folderRepository.getFolder(id)
                loadJob = null
                onLoaded(folder)
            } finally {
                // A newer load may have started meanwhile: it keeps the indicator.
                if (loadJob == null || loadJob === coroutineContext.job) _isLoading.value = false
            }
        }
    }

    private companion object {
        /** Guards against folders being their own subfolder. */
        const val MAX_TREE_DEPTH = 32
    }
}

/** A row of the tree view of the folder screen, [depth] levels below the current folder. */
sealed interface TreeEntry {
    val depth: Int
    val key: String

    data class FolderEntry(
        val folder: MediaModel.Folder,
        override val depth: Int,
        val isExpanded: Boolean,
        val isLoading: Boolean,
        override val key: String
    ) : TreeEntry

    /** [song] is at [index] in [siblings], the songs of its folder. */
    data class SongEntry(
        val song: MediaItem,
        override val depth: Int,
        val siblings: List<MediaItem>,
        val index: Int,
        override val key: String
    ) : TreeEntry
}
