package com.craftworks.music.data.cache

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.craftworks.music.data.model.MediaModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** A song stored on disk by the smart cache. */
data class CachedSong(
    val providerId: String,
    val song: MediaModel.Song,
    val fileName: String,
    val sizeBytes: Long,
    val bitrate: String,
    val format: String,
    /** Key of the cover in `cached_artworks`, see [com.craftworks.music.player.artworkKey]. */
    val artworkKey: String?,
    /** Position in the last refresh's ranking, lower is more listened. */
    val rank: Int,
    val cachedAt: Long,
) {
    val songId: String get() = song.id
}

data class CachedArtwork(val key: String, val fileName: String, val sizeBytes: Long)

/** A scrobble that couldn't be sent to the provider, to be sent once back online. */
data class PendingScrobble(val id: Long, val providerId: String, val songId: String, val playedAt: Long)

/**
 * Stores the smart cache metadata (cached songs and covers), how many times each song was played
 * in the app, and the scrobbles waiting to be sent to the provider.
 */
@Singleton
class SmartCacheDatabase @Inject constructor(
    @ApplicationContext context: Context
) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    private val json = Json { ignoreUnknownKeys = true }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE cached_songs (
                provider_id TEXT NOT NULL,
                song_id TEXT NOT NULL,
                album_id TEXT,
                title TEXT NOT NULL,
                artist TEXT,
                album TEXT,
                duration_ms INTEGER NOT NULL DEFAULT 0,
                file_name TEXT NOT NULL,
                size_bytes INTEGER NOT NULL,
                bitrate TEXT NOT NULL,
                format TEXT NOT NULL,
                artwork_key TEXT,
                rank INTEGER NOT NULL,
                cached_at INTEGER NOT NULL,
                metadata TEXT NOT NULL,
                PRIMARY KEY (provider_id, song_id)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX cached_songs_artwork ON cached_songs(artwork_key)")

        db.execSQL(
            """
            CREATE TABLE cached_artworks (
                artwork_key TEXT PRIMARY KEY NOT NULL,
                file_name TEXT NOT NULL,
                size_bytes INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE app_plays (
                provider_id TEXT NOT NULL,
                song_id TEXT NOT NULL,
                play_count INTEGER NOT NULL,
                last_played_at INTEGER NOT NULL,
                PRIMARY KEY (provider_id, song_id)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE pending_scrobbles (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                provider_id TEXT NOT NULL,
                song_id TEXT NOT NULL,
                played_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Append schema migrations here when DATABASE_VERSION is bumped.
    }

    //region Cached songs

    fun getCachedSongs(): List<CachedSong> =
        readableDatabase.query("cached_songs", null, null, null, null, null, "rank ASC").use { cursor ->
            buildList {
                while (cursor.moveToNext()) cursor.toCachedSong()?.let(::add)
            }
        }

    fun putCachedSong(cached: CachedSong) {
        writableDatabase.insertWithOnConflict(
            "cached_songs", null, cached.toContentValues(), SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    /** Updates the ranking and the metadata of already cached songs, keeping their files. */
    fun updateCachedSongs(songs: List<CachedSong>) = transaction {
        songs.forEach { cached ->
            update(
                "cached_songs",
                cached.toContentValues(),
                "provider_id = ? AND song_id = ?",
                arrayOf(cached.providerId, cached.songId)
            )
        }
    }

    fun deleteCachedSongs(songs: List<CachedSong>) = transaction {
        songs.forEach {
            delete("cached_songs", "provider_id = ? AND song_id = ?", arrayOf(it.providerId, it.songId))
        }
    }

    //endregion

    //region Artworks

    fun getCachedArtworks(): List<CachedArtwork> =
        readableDatabase.query("cached_artworks", null, null, null, null, null, null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        CachedArtwork(
                            key = cursor.getString(cursor.getColumnIndexOrThrow("artwork_key")),
                            fileName = cursor.getString(cursor.getColumnIndexOrThrow("file_name")),
                            sizeBytes = cursor.getLong(cursor.getColumnIndexOrThrow("size_bytes")),
                        )
                    )
                }
            }
        }

    fun putCachedArtwork(artwork: CachedArtwork) {
        writableDatabase.insertWithOnConflict(
            "cached_artworks",
            null,
            ContentValues().apply {
                put("artwork_key", artwork.key)
                put("file_name", artwork.fileName)
                put("size_bytes", artwork.sizeBytes)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun deleteCachedArtworks(keys: Collection<String>) = transaction {
        keys.forEach { delete("cached_artworks", "artwork_key = ?", arrayOf(it)) }
    }

    //endregion

    //region App plays

    // No UPSERT: it needs SQLite 3.24, which older Android versions don't ship.
    fun recordAppPlay(providerId: String, songId: String, playedAt: Long) = transaction {
        execSQL(
            "UPDATE app_plays SET play_count = play_count + 1, last_played_at = ? WHERE provider_id = ? AND song_id = ?",
            arrayOf<Any>(playedAt, providerId, songId)
        )
        insertWithOnConflict("app_plays", null, ContentValues().apply {
            put("provider_id", providerId)
            put("song_id", songId)
            put("play_count", 1)
            put("last_played_at", playedAt)
        }, SQLiteDatabase.CONFLICT_IGNORE)
    }

    /** The ids of the songs played the most in the app on [providerId], most played first. */
    fun getMostPlayedSongIds(providerId: String): List<String> =
        readableDatabase.query(
            "app_plays", arrayOf("song_id"), "provider_id = ?", arrayOf(providerId),
            null, null, "play_count DESC, last_played_at DESC"
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }

    /** How many times each song of [providerId] was played in the app. */
    fun getAppPlayCounts(providerId: String): Map<String, Int> =
        readableDatabase.query(
            "app_plays", arrayOf("song_id", "play_count"), "provider_id = ?", arrayOf(providerId),
            null, null, null
        ).use { cursor ->
            buildMap { while (cursor.moveToNext()) put(cursor.getString(0), cursor.getInt(1)) }
        }

    //endregion

    //region Pending scrobbles

    fun addPendingScrobble(providerId: String, songId: String, playedAt: Long) {
        writableDatabase.insert("pending_scrobbles", null, ContentValues().apply {
            put("provider_id", providerId)
            put("song_id", songId)
            put("played_at", playedAt)
        })
    }

    fun getPendingScrobbles(): List<PendingScrobble> =
        readableDatabase.query("pending_scrobbles", null, null, null, null, null, "played_at ASC, id ASC").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        PendingScrobble(
                            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                            providerId = cursor.getString(cursor.getColumnIndexOrThrow("provider_id")),
                            songId = cursor.getString(cursor.getColumnIndexOrThrow("song_id")),
                            playedAt = cursor.getLong(cursor.getColumnIndexOrThrow("played_at")),
                        )
                    )
                }
            }
        }

    fun deletePendingScrobble(id: Long) {
        writableDatabase.delete("pending_scrobbles", "id = ?", arrayOf(id.toString()))
    }

    fun countPendingScrobbles(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM pending_scrobbles", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

    //endregion

    private inline fun transaction(block: SQLiteDatabase.() -> Unit) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.block()
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun CachedSong.toContentValues() = ContentValues().apply {
        put("provider_id", providerId)
        put("song_id", song.id)
        put("album_id", song.albumId)
        put("title", song.name)
        put("artist", song.artistName)
        put("album", song.album)
        put("duration_ms", song.durationMs)
        put("file_name", fileName)
        put("size_bytes", sizeBytes)
        put("bitrate", bitrate)
        put("format", format)
        put("artwork_key", artworkKey)
        put("rank", rank)
        put("cached_at", cachedAt)
        put("metadata", json.encodeToString(MediaModel.Song.serializer(), song))
    }

    private fun Cursor.toCachedSong(): CachedSong? {
        val song = try {
            json.decodeFromString(MediaModel.Song.serializer(), getString(getColumnIndexOrThrow("metadata")))
        } catch (e: Exception) {
            Log.w(TAG, "Can't read the metadata of a cached song", e)
            return null
        }
        val artworkIndex = getColumnIndexOrThrow("artwork_key")
        return CachedSong(
            providerId = getString(getColumnIndexOrThrow("provider_id")),
            song = song,
            fileName = getString(getColumnIndexOrThrow("file_name")),
            sizeBytes = getLong(getColumnIndexOrThrow("size_bytes")),
            bitrate = getString(getColumnIndexOrThrow("bitrate")),
            format = getString(getColumnIndexOrThrow("format")),
            artworkKey = if (isNull(artworkIndex)) null else getString(artworkIndex),
            rank = getInt(getColumnIndexOrThrow("rank")),
            cachedAt = getLong(getColumnIndexOrThrow("cached_at")),
        )
    }

    companion object {
        private const val TAG = "SmartCacheDatabase"
        private const val DATABASE_NAME = "smart_cache.db"
        private const val DATABASE_VERSION = 1
    }
}
