package com.craftworks.music.ui.playing

import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest

/**
 * The full-size cover of the playing track.
 *
 * It isn't stored in the disk cache, so it needs the network (or a pre-fetched / smart cached cover).
 * When it can't be loaded, the cover shown by the mini player and the queue is used instead:
 * it comes from the disk cache, keyed by song id.
 */
@Composable
fun NowPlayingArtwork(
    artworkUri: Any?,
    songId: String?,
    contentScale: ContentScale,
    modifier: Modifier = Modifier,
    crossfade: Boolean = false,
    fallback: Painter? = null,
) {
    val context = LocalContext.current

    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data(artworkUri)
            .placeholderMemoryCacheKey(songId)
            .diskCachePolicy(CachePolicy.DISABLED)
            .crossfade(crossfade)
            .listener(onError = { _, result ->
                Log.w("NowPlayingArtwork", "Can't load the cover of $songId, using the cached one", result.throwable)
            })
            .build(),
        contentDescription = "Album Cover Art",
        contentScale = contentScale,
        alignment = Alignment.Center,
        modifier = modifier,
        error = {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(artworkUri)
                    .diskCacheKey(songId)
                    .diskCachePolicy(CachePolicy.READ_ONLY)
                    .build(),
                contentDescription = "Album Cover Art",
                contentScale = contentScale,
                alignment = Alignment.Center,
                fallback = fallback,
                error = fallback,
                modifier = Modifier.fillMaxSize()
            )
        }
    )
}
