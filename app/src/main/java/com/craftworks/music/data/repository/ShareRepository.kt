package com.craftworks.music.data.repository

import android.util.Log
import com.craftworks.music.data.model.ProviderFeature
import com.craftworks.music.data.model.ShareExpiration
import com.craftworks.music.data.providers.media.MediaProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShareRepository @Inject constructor() {

    /**
     * Creates a public link to the items with [ids] on [provider] (songs, or an album, playlist or artist).
     * Returns its URL, or null if the provider can't share or the server refused it.
     */
    suspend fun share(
        provider: MediaProvider,
        ids: List<String>,
        description: String?,
        expiration: ShareExpiration
    ): String? = withContext(Dispatchers.IO) {
        if (ids.isEmpty() || !provider.featureFlags.contains(ProviderFeature.SHARING)) return@withContext null
        try {
            provider.shareItem(ids, description?.takeIf { it.isNotBlank() }, expiration.expiresAt())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("ShareRepository", "Couldn't share $ids", e)
            null
        }
    }
}
