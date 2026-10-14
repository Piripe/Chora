package com.craftworks.music.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import coil.intercept.Interceptor
import coil.request.ImageResult
import java.nio.ByteBuffer

/**
 * Serves the covers of pre-fetched tracks from RAM instead of the provider's API,
 * so the now playing screen keeps its artwork (and colors) while offline.
 */
class PrefetchedArtworkInterceptor : Interceptor {
    @OptIn(UnstableApi::class)
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val url = when (val data = chain.request.data) {
            is Uri -> data.toString()
            is String -> data
            else -> null
        }
        val artwork = url?.let { ChoraMediaLibraryService.getInstance()?.getPrefetchedArtwork(it) }
            ?: return chain.proceed(chain.request)

        return chain.proceed(chain.request.newBuilder().data(ByteBuffer.wrap(artwork)).build())
    }
}
