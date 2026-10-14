package com.craftworks.music.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import coil.intercept.Interceptor
import coil.request.ImageResult
import java.io.File
import java.nio.ByteBuffer

/**
 * Serves the covers of pre-fetched tracks from RAM, and the ones of smart cached tracks from disk,
 * instead of the provider's API, so the now playing screen keeps its artwork (and colors) while offline.
 */
class PrefetchedArtworkInterceptor(
    private val smartCacheArtwork: (url: String) -> File?,
) : Interceptor {
    @OptIn(UnstableApi::class)
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val url = when (val data = chain.request.data) {
            is Uri -> data.toString()
            is String -> data
            else -> null
        } ?: return chain.proceed(chain.request)

        ChoraMediaLibraryService.getInstance()?.getPrefetchedArtwork(url)?.let { artwork ->
            return chain.proceed(chain.request.newBuilder().data(ByteBuffer.wrap(artwork)).build())
        }
        smartCacheArtwork(url)?.let { file ->
            return chain.proceed(chain.request.newBuilder().data(file).build())
        }
        return chain.proceed(chain.request)
    }
}
