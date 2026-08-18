@file:OptIn(UnstableApi::class) package com.craftworks.music.player

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import com.craftworks.music.data.model.makeUnique
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SongHelper {
    companion object{
        suspend fun play(mediaItems: List<MediaItem>, index: Int, mediaController: MediaController?) {
            if (mediaItems.isEmpty())
                return

            withContext(Dispatchers.Main) {
                mediaController?.setMediaItems(mediaItems.map { it.makeUnique() }, index, 0)
                mediaController?.prepare()
                mediaController?.play()
            }
        }
        fun enqueue(mediaItems: List<MediaItem>, mediaController: MediaController?) {
            mediaController?.addMediaItems(mediaItems.map { it.makeUnique() })
        }
        fun playNext(mediaItems: List<MediaItem>, mediaController: MediaController?) {
            mediaController?.addMediaItems(
                mediaController.currentMediaItemIndex + 1,
                mediaItems.map { it.makeUnique() }
            )
        }
    }
}