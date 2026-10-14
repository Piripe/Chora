package com.craftworks.music.data.repository

import com.craftworks.music.data.model.LibraryType
import com.craftworks.music.managers.MediaProviderManager
import com.craftworks.music.managers.OfflineManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for favorite (starred) state changes made during the session.
 *
 * Media items carry the favorite state they had when they were fetched, so stale copies of the
 * same item (song cards, queue items, now playing...) would otherwise disagree with each other.
 * Every star/unstar goes through this repository which keeps an override per item, and the UI
 * reads [isStarred] with the item's own value as fallback.
 */
@Singleton
class StarredRepository @Inject constructor(
    private val offlineManager: OfflineManager
) {
    data class StarKey(val providerId: String?, val id: String, val type: LibraryType)

    private fun key(id: String, type: LibraryType) =
        StarKey(MediaProviderManager.currentProvider.value?.id, id, type)

    private val _overrides = MutableStateFlow<Map<StarKey, Boolean>>(emptyMap())

    fun isStarred(id: String, type: LibraryType, fallback: Boolean): Boolean =
        _overrides.value[key(id, type)] ?: fallback

    fun isStarredFlow(id: String, type: LibraryType, fallback: Boolean): Flow<Boolean> =
        combine(_overrides, MediaProviderManager.currentProvider) { overrides, provider ->
            overrides[StarKey(provider?.id, id, type)] ?: fallback
        }.distinctUntilChanged()

    suspend fun starItem(ids: List<String>, type: LibraryType) =
        setStarred(ids, type, true)

    suspend fun unStarItem(ids: List<String>, type: LibraryType) =
        setStarred(ids, type, false)

    suspend fun toggleStar(id: String, type: LibraryType, currentlyStarred: Boolean) =
        setStarred(listOf(id), type, !currentlyStarred)

    private suspend fun setStarred(ids: List<String>, type: LibraryType, starred: Boolean): Boolean {
        if (ids.isEmpty()) return true
        val keys = ids.map { key(it, type) }
        val previous = _overrides.value

        // Optimistic update so every screen reflects the change immediately
        _overrides.update { current -> current + keys.associateWith { starred } }

        val success = offlineManager.runOrDefault(false) { provider ->
            if (starred) provider.createFavorite(ids, type)
            else provider.deleteFavorite(ids, type)
        }

        if (!success) {
            // Revert only the keys we touched
            _overrides.update { current ->
                current - keys.toSet() + keys.mapNotNull { key -> previous[key]?.let { key to it } }
            }
        }
        return success
    }
}
