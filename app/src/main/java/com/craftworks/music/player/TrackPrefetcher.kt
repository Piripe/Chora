package com.craftworks.music.player

import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

/** The stream settings a track was fetched with. A track fetched with other settings is not reused. */
data class StreamVariant(val bitrate: String, val format: String)

/** A queue item to pre-fetch: its `media://` [uri] and the cover shown for it. */
data class PrefetchItem(val uri: Uri, val artworkUrl: String?)

/**
 * Downloads the upcoming tracks of the queue and keeps them in RAM only (never on disk),
 * so [PrefetchDataSource] can serve them without hitting the network.
 * The covers of the queue tracks are kept too, so they can be displayed while offline.
 */
@UnstableApi
class TrackPrefetcher(
    private val httpClient: OkHttpClient,
    private val currentVariant: () -> StreamVariant,
    private val streamUrl: (uri: Uri, variant: StreamVariant) -> String,
    private val isOffline: () -> Boolean,
    /** Called once a track is fully downloaded, e.g. to keep it in the smart cache. */
    private val onPrefetched: (uri: Uri, variant: StreamVariant, entry: Entry) -> Unit = { _, _, _ -> },
) {
    /** A fully downloaded track, stored in fixed-size chunks to avoid huge contiguous allocations. */
    class Entry(private val chunks: List<ByteArray>, val length: Long) {
        fun read(position: Long, target: ByteArray, offset: Int, length: Int): Int {
            val chunk = chunks[(position / CHUNK_SIZE).toInt()]
            val inChunk = (position % CHUNK_SIZE).toInt()
            val count = min(min(length.toLong(), this.length - position), (CHUNK_SIZE - inChunk).toLong()).toInt()
            System.arraycopy(chunk, inChunk, target, offset, count)
            return count
        }

        fun writeTo(output: OutputStream) {
            var remaining = length
            for (chunk in chunks) {
                val count = min(chunk.size.toLong(), remaining).toInt()
                output.write(chunk, 0, count)
                remaining -= count
            }
        }
    }

    // Not a data class: every submit must re-plan, even for an identical queue (e.g. new transcoding settings).
    private class QueueRequest(
        val current: PrefetchItem?,
        val upcoming: List<PrefetchItem>,
        val previous: List<PrefetchItem>,
    )

    private data class Plan(
        val variant: StreamVariant? = null,
        val downloads: List<PrefetchItem> = emptyList(),
        val retain: Set<String> = emptySet(),
        // Covers of tracks that are not downloaded, like the current one which is streamed.
        val artworkDownloads: List<String> = emptyList(),
        val artworkRetain: Set<String> = emptySet(),
    )

    private val entries = ConcurrentHashMap<String, Entry>()
    private val artworks = ConcurrentHashMap<String, ByteArray>()
    private val cachedBytes = AtomicLong(0)
    private val maxBytes = Runtime.getRuntime().maxMemory() / 4

    private val requests = MutableStateFlow(QueueRequest(null, emptyList(), emptyList()))
    private val plan = MutableStateFlow(Plan())
    private val lock = Any()

    private var plannerJob: Job? = null
    private var workerJob: Job? = null

    fun start(scope: CoroutineScope) {
        plannerJob = scope.launch(Dispatchers.IO) {
            requests.collectLatest { request ->
                val variant = currentVariant()
                val items = listOfNotNull(request.current) + request.upcoming + request.previous
                val retain = items.map { key(it.uri, variant) }.toSet()
                val artworkRetain = items.mapNotNull { it.artworkUrl?.let(::artworkKey) }.toSet()
                val artworkDownloads = (listOfNotNull(request.current) + request.previous)
                    .mapNotNull { it.artworkUrl }
                    .distinctBy(::artworkKey)

                synchronized(lock) {
                    plan.value = Plan(variant, request.upcoming, retain, artworkDownloads, artworkRetain)
                    // Offline, the cached tracks may be the only playable ones: keep them all.
                    if (!isOffline()) {
                        entries.keys.filter { it !in retain }.forEach { remove(it) }
                        artworks.keys.filter { it !in artworkRetain }.forEach { removeArtwork(it) }
                    }
                }
            }
        }

        workerJob = scope.launch(Dispatchers.IO) {
            var lastPlan: Plan? = null
            val failed = mutableSetOf<String>()
            var stopped = false

            while (isActive) {
                val current = plan.value
                if (current !== lastPlan) {
                    lastPlan = current
                    failed.clear()
                    stopped = false
                }

                // Covers are small and the current one is needed first: fetch them before the tracks.
                val nextArtwork = current.artworkDownloads.firstOrNull { url ->
                    !artworks.containsKey(artworkKey(url)) && url !in failed
                }
                if (nextArtwork != null) {
                    if (!fetchArtwork(nextArtwork, null)) failed += nextArtwork
                    continue
                }

                val variant = current.variant
                val next = if (stopped || variant == null || cachedBytes.get() >= maxBytes) null
                    else current.downloads.firstOrNull { item ->
                        val key = key(item.uri, variant)
                        !entries.containsKey(key) && key !in failed
                    }

                if (variant == null || next == null) {
                    plan.first { it !== current }
                    continue
                }

                val key = key(next.uri, variant)
                when (download(next, key, variant)) {
                    DownloadResult.OK -> {}
                    DownloadResult.FAILED -> failed += key
                    DownloadResult.OVER_BUDGET -> stopped = true
                }
            }
        }
    }

    fun stop() {
        plannerJob?.cancel()
        workerJob?.cancel()
        clear()
    }

    /**
     * Sets the queue: the [upcoming] tracks are downloaded in order, the [current] and [previous]
     * ones are only kept if already cached. Everything else is dropped, unless offline.
     */
    fun submit(current: PrefetchItem?, upcoming: List<PrefetchItem>, previous: List<PrefetchItem>) {
        requests.value = QueueRequest(current, upcoming, previous)
    }

    /** Drops every cached track, e.g. when the system is low on memory. */
    fun clear() {
        synchronized(lock) {
            entries.keys.toList().forEach { remove(it) }
            artworks.keys.toList().forEach { removeArtwork(it) }
            // Re-plan so the worker does not immediately download everything again.
            plan.value = plan.value.copy(downloads = emptyList(), artworkDownloads = emptyList())
        }
    }

    fun get(uri: Uri): Entry? {
        if (entries.isEmpty()) return null
        entries[key(uri, currentVariant())]?.let { return it }

        // Offline, a copy fetched with other stream settings (e.g. on mobile data) beats nothing.
        if (!isOffline()) return null
        val prefix = "$uri|"
        return entries.entries.firstOrNull { it.key.startsWith(prefix) }?.value
    }

    /** The cover of a queue track, looked up by its artwork URL (at any size). */
    fun getArtwork(url: String): ByteArray? =
        if (artworks.isEmpty()) null else artworks[artworkKey(url)]

    private fun remove(key: String) {
        entries.remove(key)?.let { cachedBytes.addAndGet(-it.length) }
    }

    private fun removeArtwork(url: String) {
        artworks.remove(url)?.let { cachedBytes.addAndGet(-it.size.toLong()) }
    }

    /**
     * Downloads the cover at [url], falling back to the one embedded in [entry] if given.
     * Returns whether a cover is now cached.
     */
    private suspend fun fetchArtwork(url: String, entry: Entry?): Boolean {
        val key = artworkKey(url)
        if (artworks.containsKey(key)) return true
        val data = downloadArtwork(url) ?: entry?.embeddedArtwork() ?: return false

        synchronized(lock) {
            if (key !in plan.value.artworkRetain) return false
            if (artworks.put(key, data) == null) cachedBytes.addAndGet(data.size.toLong())
        }
        return true
    }

    private enum class DownloadResult { OK, FAILED, OVER_BUDGET }

    private suspend fun download(item: PrefetchItem, key: String, variant: StreamVariant): DownloadResult {
        val uri = item.uri
        val url = try {
            streamUrl(uri, variant)
        } catch (e: Exception) {
            Log.w(TAG, "Can't resolve stream url for $uri", e)
            return DownloadResult.FAILED
        }
        // Local files (content:// URIs) are already on the device and can't go through OkHttp.
        val httpUrl = url.toHttpUrlOrNull() ?: return DownloadResult.FAILED

        val call = httpClient.newCall(Request.Builder().url(httpUrl).build())
        return try {
            call.execute().use { response ->
                val body = response.body ?: return DownloadResult.FAILED
                val contentType = body.contentType()
                // Subsonic servers report errors as XML/JSON with a 200 status.
                if (!response.isSuccessful || contentType?.subtype?.let { "xml" in it || "json" in it } == true) {
                    Log.w(TAG, "Can't prefetch $uri: HTTP ${response.code} ($contentType)")
                    return DownloadResult.FAILED
                }

                val chunks = mutableListOf<ByteArray>()
                var length = 0L
                var chunk = ByteArray(CHUNK_SIZE)
                var inChunk = 0
                val source = body.byteStream()

                while (true) {
                    currentCoroutineContext().ensureActive()
                    if (key !in plan.value.retain) return DownloadResult.FAILED

                    val read = source.read(chunk, inChunk, CHUNK_SIZE - inChunk)
                    if (read == -1) break

                    inChunk += read
                    length += read
                    if (cachedBytes.get() + length > maxBytes) {
                        Log.d(TAG, "Prefetch memory budget reached ($maxBytes bytes), stopping")
                        return DownloadResult.OVER_BUDGET
                    }
                    if (inChunk == CHUNK_SIZE) {
                        chunks += chunk
                        chunk = ByteArray(CHUNK_SIZE)
                        inChunk = 0
                    }
                }
                if (inChunk > 0) chunks += chunk.copyOf(inChunk)
                if (length == 0L) return DownloadResult.FAILED

                val entry = Entry(chunks, length)
                synchronized(lock) {
                    if (key !in plan.value.retain) return DownloadResult.FAILED
                    entries[key] = entry
                    cachedBytes.addAndGet(length)
                }
                item.artworkUrl?.let { fetchArtwork(it, entry) }
                Log.d(TAG, "Prefetched $uri ($length bytes, ${cachedBytes.get()} bytes cached)")
                onPrefetched(uri, variant, entry)
                return DownloadResult.OK
            }
        } catch (e: Exception) {
            call.cancel()
            currentCoroutineContext().ensureActive()
            Log.w(TAG, "Can't prefetch $uri", e)
            return DownloadResult.FAILED
        }
    }

    private suspend fun downloadArtwork(url: String): ByteArray? {
        val httpUrl = url.toHttpUrlOrNull() ?: return null
        val call = httpClient.newCall(Request.Builder().url(httpUrl).build())
        return try {
            call.execute().use { response ->
                val body = response.body ?: return null
                if (!response.isSuccessful || body.contentType()?.type != "image") return null
                body.bytes()
            }
        } catch (e: Exception) {
            call.cancel()
            currentCoroutineContext().ensureActive()
            Log.w(TAG, "Can't prefetch artwork $url", e)
            null
        }
    }

    /** The cover embedded in the audio file itself, read straight from RAM. */
    private fun Entry.embeddedArtwork(): ByteArray? {
        val entry = this
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(object : MediaDataSource() {
                override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
                    if (position >= entry.length) return -1
                    var read = 0
                    while (read < size && position + read < entry.length)
                        read += entry.read(position + read, buffer, offset + read, size - read)
                    return read
                }

                override fun getSize() = entry.length

                override fun close() {}
            })
            retriever.embeddedPicture
        } catch (e: Exception) {
            Log.w(TAG, "Can't read embedded artwork", e)
            null
        } finally {
            retriever.release()
        }
    }

    companion object {
        private const val TAG = "PREFETCH"
        private const val CHUNK_SIZE = 256 * 1024

        private fun key(uri: Uri, variant: StreamVariant) = "$uri|${variant.bitrate}|${variant.format}"
    }
}

/**
 * Identifies a cover regardless of the requested size and of the auth salt/token,
 * so every screen showing it (now playing, queue...) can use the cached one.
 */
fun artworkKey(url: String): String =
    url.toHttpUrlOrNull()?.newBuilder()
        ?.removeAllQueryParameters("size")
        ?.removeAllQueryParameters("t")
        ?.removeAllQueryParameters("s")
        ?.build()?.toString()
        ?: url

/** Serves tracks from [TrackPrefetcher] when they are cached, and from [upstream] otherwise. */
@UnstableApi
class PrefetchDataSource(
    private val upstream: DataSource,
    private val prefetcher: TrackPrefetcher,
    private val onCacheHit: () -> Unit,
) : DataSource {
    class Factory(
        private val upstreamFactory: DataSource.Factory,
        private val prefetcher: TrackPrefetcher,
        private val onCacheHit: () -> Unit = {},
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            PrefetchDataSource(upstreamFactory.createDataSource(), prefetcher, onCacheHit)
    }

    private var entry: TrackPrefetcher.Entry? = null
    private var uri: Uri? = null
    private var position = 0L
    private var bytesRemaining = 0L

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val cached = if (dataSpec.uri.scheme == "media") prefetcher.get(dataSpec.uri) else null
        if (cached == null) return upstream.open(dataSpec)

        if (dataSpec.position > cached.length)
            throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)

        onCacheHit()
        entry = cached
        uri = dataSpec.uri
        position = dataSpec.position
        val available = cached.length - position
        bytesRemaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) available
            else min(dataSpec.length, available)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val cached = entry ?: return upstream.read(buffer, offset, length)
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val read = cached.read(position, buffer, offset, min(length.toLong(), bytesRemaining).toInt())
        position += read
        bytesRemaining -= read
        return read
    }

    override fun getUri(): Uri? = if (entry != null) uri else upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        if (entry != null) emptyMap() else upstream.responseHeaders

    override fun close() {
        if (entry != null) {
            entry = null
            uri = null
        } else {
            upstream.close()
        }
    }
}
