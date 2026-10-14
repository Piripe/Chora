package com.craftworks.music.player

import android.net.Uri
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.TransferListener
import com.craftworks.music.managers.SmartCacheManager

/** Plays `media://` tracks from the smart cache files when they are cached, and from [upstream] otherwise. */
@UnstableApi
class SmartCacheDataSource(
    private val upstream: DataSource,
    private val smartCache: SmartCacheManager,
    private val onCacheHit: () -> Unit,
) : DataSource {
    class Factory(
        private val upstreamFactory: DataSource.Factory,
        private val smartCache: SmartCacheManager,
        private val onCacheHit: () -> Unit = {},
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            SmartCacheDataSource(upstreamFactory.createDataSource(), smartCache, onCacheHit)
    }

    private val fileSource = FileDataSource()
    private var current: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
        fileSource.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val file = smartCache.getCachedFile(dataSpec.uri)
        if (file != null) {
            try {
                val length = fileSource.open(dataSpec.withUri(Uri.fromFile(file)))
                current = fileSource
                onCacheHit()
                return length
            } catch (e: FileDataSource.FileDataSourceException) {
                // Dropped from the cache in the meantime: stream it instead.
                Log.w("SmartCache", "Can't read cached file for ${dataSpec.uri}", e)
                fileSource.close()
            }
        }

        current = upstream
        return upstream.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        (current ?: upstream).read(buffer, offset, length)

    override fun getUri(): Uri? = current?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        current?.responseHeaders ?: emptyMap()

    override fun close() {
        try {
            current?.close()
        } finally {
            current = null
        }
    }
}
