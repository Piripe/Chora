package com.craftworks.music.data.cache

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.craftworks.music.data.model.MediaModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A song stored on disk by the smart cache, or downloaded by the user ([manual]).
 * Downloaded songs are never dropped by the smart cache, only removed by the user.
 */
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
    val manual: Boolean = false,
) {
    val songId: String get() = song.id
}

data class CachedArtwork(val key: String, val fileName: String, val sizeBytes: Long)

/**
 * An artist of the cached songs, with its biography and similar artists, so it can be browsed and
 * searched offline. [artworkKeys] are its pictures in `cached_artworks`.
 */
data class CachedArtist(val providerId: String, val artist: MediaModel.Artist, val artworkKeys: List<String>) {
    val artistId: String get() = artist.id
}

/** A playlist with cached songs. [songIds] are all its songs, cached or not, in order. */
data class CachedPlaylist(
    val providerId: String,
    val playlist: MediaModel.Playlist,
    val songIds: List<String>,
    val artworkKeys: List<String>,
) {
    val playlistId: String get() = playlist.id
}

/** A folder of [CachedSongFolders], see [com.craftworks.music.data.model.MediaModel.Folder]. */
@Serializable
data class CachedFolder(val id: String, val name: String, val parentId: String? = null)

/**
 * The folders holding a cached song, from the one below the root folder to the one holding it,
 * so the folders of the cached songs can be browsed offline.
 */
data class CachedSongFolders(val providerId: String, val songId: String, val folders: List<CachedFolder>)

/** How many times a song was played in the app, and when last ([lastPlayedAt] in epoch milliseconds). */
data class AppPlay(val providerId: String, val songId: String, val playCount: Int, val lastPlayedAt: Long)

/** A scrobble that couldn't be sent to the provider, to be sent once back online. */
data class PendingScrobble(val id: Long, val providerId: String, val songId: String, val playedAt: Long)

/**
 * Stores the smart cache and downloads metadata (cached songs, their artists, playlists and folders, and covers), how many times each song was played
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
                manual INTEGER NOT NULL DEFAULT 0,
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

        createLibraryTables(db)
        createSongFoldersTable(db)

        db.execSQL(
            """
            CREATE TABLE app_plays (
                provider_id TEXT NOT NULL,
                song_id TEXT NOT NULL,
                play_count INTEGER NOT NULL,
                last_played_at INTEGER NOT NULL,
                score REAL NOT NULL DEFAULT 0,
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
        if (oldVersion < 2) {
            // The older plays aren't dated, they are all counted at the last one.
            db.execSQL("ALTER TABLE app_plays ADD COLUMN score REAL NOT NULL DEFAULT 0")
            db.execSQL("UPDATE app_plays SET score = play_count")
        }
        if (oldVersion < 3) createLibraryTables(db)
        if (oldVersion < 4) db.execSQL("ALTER TABLE cached_songs ADD COLUMN manual INTEGER NOT NULL DEFAULT 0")
        if (oldVersion < 5) createSongFoldersTable(db)
    }

    private fun createLibraryTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE cached_artists (
                provider_id TEXT NOT NULL,
                artist_id TEXT NOT NULL,
                metadata TEXT NOT NULL,
                artwork_keys TEXT NOT NULL,
                PRIMARY KEY (provider_id, artist_id)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE cached_playlists (
                provider_id TEXT NOT NULL,
                playlist_id TEXT NOT NULL,
                metadata TEXT NOT NULL,
                song_ids TEXT NOT NULL,
                artwork_keys TEXT NOT NULL,
                PRIMARY KEY (provider_id, playlist_id)
            )
            """.trimIndent()
        )
    }

    private fun createSongFoldersTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE cached_song_folders (
                provider_id TEXT NOT NULL,
                song_id TEXT NOT NULL,
                folders TEXT NOT NULL,
                PRIMARY KEY (provider_id, song_id)
            )
            """.trimIndent()
        )
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

    //region Artists and playlists

    fun getCachedArtists(): List<CachedArtist> =
        readableDatabase.query("cached_artists", null, null, null, null, null, null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val artist = cursor.decode(MediaModel.Artist.serializer(), "metadata") ?: continue
                    add(
                        CachedArtist(
                            providerId = cursor.getString(cursor.getColumnIndexOrThrow("provider_id")),
                            artist = artist,
                            artworkKeys = cursor.decode(stringList, "artwork_keys").orEmpty(),
                        )
                    )
                }
            }
        }

    fun getCachedPlaylists(): List<CachedPlaylist> =
        readableDatabase.query("cached_playlists", null, null, null, null, null, null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val playlist = cursor.decode(MediaModel.Playlist.serializer(), "metadata") ?: continue
                    add(
                        CachedPlaylist(
                            providerId = cursor.getString(cursor.getColumnIndexOrThrow("provider_id")),
                            playlist = playlist,
                            songIds = cursor.decode(stringList, "song_ids").orEmpty(),
                            artworkKeys = cursor.decode(stringList, "artwork_keys").orEmpty(),
                        )
                    )
                }
            }
        }

    fun putCachedArtists(artists: List<CachedArtist>) = transaction {
        artists.forEach { cached ->
            insertWithOnConflict("cached_artists", null, ContentValues().apply {
                put("provider_id", cached.providerId)
                put("artist_id", cached.artistId)
                put("metadata", json.encodeToString(MediaModel.Artist.serializer(), cached.artist))
                put("artwork_keys", json.encodeToString(stringList, cached.artworkKeys))
            }, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun putCachedPlaylists(playlists: List<CachedPlaylist>) = transaction {
        playlists.forEach { cached ->
            insertWithOnConflict("cached_playlists", null, ContentValues().apply {
                put("provider_id", cached.providerId)
                put("playlist_id", cached.playlistId)
                put("metadata", json.encodeToString(MediaModel.Playlist.serializer(), cached.playlist))
                put("song_ids", json.encodeToString(stringList, cached.songIds))
                put("artwork_keys", json.encodeToString(stringList, cached.artworkKeys))
            }, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun deleteCachedArtists(artists: List<CachedArtist>) = transaction {
        artists.forEach {
            delete("cached_artists", "provider_id = ? AND artist_id = ?", arrayOf(it.providerId, it.artistId))
        }
    }

    fun deleteCachedPlaylists(playlists: List<CachedPlaylist>) = transaction {
        playlists.forEach {
            delete("cached_playlists", "provider_id = ? AND playlist_id = ?", arrayOf(it.providerId, it.playlistId))
        }
    }

    //endregion

    //region Song folders

    fun getCachedSongFolders(): List<CachedSongFolders> =
        readableDatabase.query("cached_song_folders", null, null, null, null, null, null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val folders = cursor.decode(folderList, "folders") ?: continue
                    add(
                        CachedSongFolders(
                            providerId = cursor.getString(cursor.getColumnIndexOrThrow("provider_id")),
                            songId = cursor.getString(cursor.getColumnIndexOrThrow("song_id")),
                            folders = folders,
                        )
                    )
                }
            }
        }

    fun putCachedSongFolders(songFolders: List<CachedSongFolders>) = transaction {
        songFolders.forEach { cached ->
            insertWithOnConflict("cached_song_folders", null, ContentValues().apply {
                put("provider_id", cached.providerId)
                put("song_id", cached.songId)
                put("folders", json.encodeToString(folderList, cached.folders))
            }, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun deleteCachedSongFolders(songFolders: List<CachedSongFolders>) = transaction {
        songFolders.forEach {
            delete("cached_song_folders", "provider_id = ? AND song_id = ?", arrayOf(it.providerId, it.songId))
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

    /** Counts a play, and adds it to the song's [ListeningScore] after wearing down the older plays. */
    fun recordAppPlay(providerId: String, songId: String, playedAt: Long) = transaction {
        val args = arrayOf(providerId, songId)
        val previous = rawQuery(
            "SELECT score, last_played_at FROM app_plays WHERE provider_id = ? AND song_id = ?", args
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getDouble(0) to cursor.getLong(1) else null }

        if (previous == null) {
            insert("app_plays", null, ContentValues().apply {
                put("provider_id", providerId)
                put("song_id", songId)
                put("play_count", 1)
                put("last_played_at", playedAt)
                put("score", 1.0)
            })
        } else {
            val (score, lastPlayedAt) = previous
            execSQL(
                "UPDATE app_plays SET play_count = play_count + 1, last_played_at = ?, score = ? WHERE provider_id = ? AND song_id = ?",
                arrayOf<Any>(maxOf(playedAt, lastPlayedAt), ListeningScore.decay(score, playedAt - lastPlayedAt) + 1, providerId, songId)
            )
        }
    }

    /** The [ListeningScore] at [now] of each song played in the app on [providerId]. */
    fun getAppPlayScores(providerId: String, now: Long): Map<String, Double> =
        readableDatabase.query(
            "app_plays", arrayOf("song_id", "score", "last_played_at"), "provider_id = ?", arrayOf(providerId),
            null, null, null
        ).use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    put(cursor.getString(0), ListeningScore.decay(cursor.getDouble(1), now - cursor.getLong(2)))
                }
            }
        }

    /** Every song played in the app, on every provider. */
    fun getAppPlays(): List<AppPlay> =
        readableDatabase.query(
            "app_plays", arrayOf("provider_id", "song_id", "play_count", "last_played_at"), null, null,
            null, null, null
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(AppPlay(cursor.getString(0), cursor.getString(1), cursor.getInt(2), cursor.getLong(3)))
                }
            }
        }

    /** How many different songs were last played in the app on [providerId] at or after [since]. */
    fun countSongsPlayedSince(providerId: String, since: Long): Int =
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM app_plays WHERE provider_id = ? AND last_played_at >= ?", arrayOf(providerId, since.toString())
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }

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

    private fun <T> Cursor.decode(serializer: KSerializer<T>, column: String): T? = try {
        json.decodeFromString(serializer, getString(getColumnIndexOrThrow(column)))
    } catch (e: Exception) {
        Log.w(TAG, "Can't read $column", e)
        null
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
        put("manual", if (manual) 1 else 0)
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
            manual = getInt(getColumnIndexOrThrow("manual")) != 0,
        )
    }

    companion object {
        private const val TAG = "SmartCacheDatabase"
        private const val DATABASE_NAME = "smart_cache.db"
        private const val DATABASE_VERSION = 5

        private val stringList = ListSerializer(String.serializer())
        private val folderList = ListSerializer(CachedFolder.serializer())
    }
}
