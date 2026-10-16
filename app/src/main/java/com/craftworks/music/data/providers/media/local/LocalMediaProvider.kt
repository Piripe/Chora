package com.craftworks.music.data.providers.media.local

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import com.craftworks.music.R
import com.craftworks.music.data.model.AlbumArtistDetailResponse
import com.craftworks.music.data.model.AlbumArtistInfo
import com.craftworks.music.data.model.AlbumArtistListSort
import com.craftworks.music.data.model.AlbumInfo
import com.craftworks.music.data.model.AlbumListSort
import com.craftworks.music.data.model.ArtistListSort
import com.craftworks.music.data.model.AuthenticationResponse
import com.craftworks.music.data.model.GenreListSort
import com.craftworks.music.data.model.GetQueueResponse
import com.craftworks.music.data.model.ImageRequest
import com.craftworks.music.data.model.LibraryType
import com.craftworks.music.data.model.Lyrics
import com.craftworks.music.data.model.MediaModel
import com.craftworks.music.data.model.MediaQuery
import com.craftworks.music.data.model.MusicFolder
import com.craftworks.music.data.model.PlaylistListSort
import com.craftworks.music.data.model.PlaylistRules
import com.craftworks.music.data.model.ProviderFeature
import com.craftworks.music.data.model.ProviderInfo
import com.craftworks.music.data.model.ProviderType
import com.craftworks.music.data.model.ScrobbleEvent
import com.craftworks.music.data.model.ScrobbleMediaType
import com.craftworks.music.data.model.SearchResponse
import com.craftworks.music.data.model.SongListSort
import com.craftworks.music.data.model.TagListResponse
import com.craftworks.music.data.model.User
import com.craftworks.music.data.model.UserInfoResponse
import com.craftworks.music.data.providers.media.MediaProvider
import com.craftworks.music.utils.PagingUtils
import com.craftworks.music.utils.SimilarSongs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.io.File
import java.util.EnumSet

@Serializable
@SerialName("local")
class LocalMediaProvider(var providerData: LocalProviderData) : MediaProvider() {
    companion object {
        const val TAG = "LOCAL_PROVIDER"
        const val ALBUM_ART_PATH = "content://media/external/audio/albumart"
        private val ALBUM_SORT_BINDING = mapOf(
            AlbumListSort.ARTIST to "${MediaStore.Audio.Albums.ARTIST} %s",
            AlbumListSort.NAME to "${MediaStore.Audio.Albums.ALBUM} %s",
            AlbumListSort.RANDOM to "RANDOM()",
            AlbumListSort.SONG_COUNT to "${MediaStore.Audio.Albums.NUMBER_OF_SONGS} %s",
            AlbumListSort.YEAR to "${MediaStore.Audio.Albums.LAST_YEAR} %s",
        )
        private val SONG_SORT_BINDING = mapOf(
            SongListSort.ARTIST to "${MediaStore.Audio.Media.ARTIST} %s",
            SongListSort.DURATION to "${MediaStore.Audio.Media.DURATION} %s",
            SongListSort.NAME to "${MediaStore.Audio.Media.TITLE} %s",
            SongListSort.RANDOM to "RANDOM()",
            SongListSort.RECENTLY_ADDED to "${MediaStore.Audio.Media.DATE_ADDED} %s",
            SongListSort.YEAR to "${MediaStore.Audio.Media.YEAR} %s",
        )
    }

    override val providerIcon: Int
        get() = R.drawable.s_m_local_filled
    override val providerMonochromeIcon: Boolean
        get() = true
    override val providerName: Int
        get() = R.string.source_local_folder

    @Transient
    override val featureFlags: EnumSet<ProviderFeature> = EnumSet.of(
        ProviderFeature.OFFLINE_PLAYBACK,
        ProviderFeature.RADIO,
        ProviderFeature.FOLDERS
    )

    @Transient
    override val supportedAlbumSort: List<AlbumListSort> = listOf(
        AlbumListSort.ARTIST,
        AlbumListSort.NAME,
        AlbumListSort.RANDOM,
        AlbumListSort.SONG_COUNT,
        AlbumListSort.YEAR,
    )
    @Transient
    override val supportAlbumSortOrder: Boolean = true

    @Transient
    override val supportedAlbumArtistSort: List<AlbumArtistListSort> = listOf(
        AlbumArtistListSort.NAME,
    )
    @Transient
    override val supportAlbumArtistSortOrder: Boolean = false

    @Transient
    override val supportedArtistSort: List<ArtistListSort> = listOf(
        ArtistListSort.NAME,
    )
    @Transient
    override val supportArtistSortOrder: Boolean = false

    @Transient
    override val supportedGenreSort: List<GenreListSort> = listOf(GenreListSort.NAME)
    @Transient
    override val supportGenreSortOrder: Boolean = true

    @Transient
    override val supportedPlaylistSort: List<PlaylistListSort> = listOf(PlaylistListSort.NAME)
    @Transient
    override val supportPlaylistSortOrder: Boolean = true

    @Transient
    override val supportedSongSort: List<SongListSort> = listOf(
        SongListSort.ARTIST,
        SongListSort.DURATION,
        SongListSort.NAME,
        SongListSort.RANDOM,
        SongListSort.RECENTLY_ADDED,
        SongListSort.YEAR,
    )
    @Transient
    override val supportSongSortOrder: Boolean = true

    @Transient
    private lateinit var appContext: Context
    override fun init(context: Context) {
        appContext = context
    }

    override suspend fun addToPlaylist(
        playlistId: String,
        songIds: List<String>
    ): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun authenticate(
        username: String,
        password: String
    ): AuthenticationResponse {
        return AuthenticationResponse(providerType = ProviderType.LOCAL_FOLDER)
    }

    override suspend fun createFavorite(
        ids: List<String>,
        type: LibraryType
    ): Boolean {
        // TODO: Manage with this a db
        return false
    }

    override suspend fun createInternetRadioStation(
        name: String,
        streamUrl: String,
        homepageUrl: String?
    ): Boolean {
        // TODO: Manage with this a db
        return false
    }

    override suspend fun createPlaylist(
        name: String,
        comment: String,
        ownerId: String,
        public: Boolean,
        queryBuilderRules: PlaylistRules?,
        sync: Boolean
    ): String? {
        // TODO: Manage with this a db
        return null
    }

    override suspend fun deleteFavorite(
        ids: List<String>,
        type: LibraryType
    ): Boolean {
        // TODO: Manage with this a db
        return false
    }

    override suspend fun deleteInternetRadioStation(id: String): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun deletePlaylist(id: String): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun getAlbumArtistDetail(id: String): AlbumArtistDetailResponse {
        var albums = LocalUtils.getLocalAlbums(appContext, this.id, "${MediaStore.Audio.Albums.ALBUM} ASC", data.libraries.filter { it.second }.map { it.first.name })

        albums = albums.filter { it.artists.any { artist -> artist.id == id} }

        return AlbumArtistDetailResponse(albums.firstOrNull()?.artists?.firstOrNull(), albums)
    }

    override suspend fun getAlbumArtistInfo(
        id: String,
        limit: Int?
    ): AlbumArtistInfo? {
        return null // No artist info for local provider
    }

    override suspend fun getAlbumArtistList(query: MediaQuery.AlbumArtistListQuery): List<MediaModel.Artist> {
        if (query.startIndex > 0)
            return emptyList()

        return LocalUtils.getLocalAlbumArtists(appContext, id, query.musicFolderId?:data.libraries.filter { it.second }.map { it.first.name })
    }

    override suspend fun getAlbumDetail(id: String): MediaModel.Album {
        Log.d(TAG, "Getting album data for id $id")

        val albumWithSongs: MediaModel.Album
        val contentResolver = appContext.contentResolver

        val albumUri = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI
        val albumProjection = arrayOf(
            MediaStore.Audio.Albums._ID,
            MediaStore.Audio.Albums.ALBUM,
            MediaStore.Audio.Albums.ARTIST,
            MediaStore.Audio.Albums.FIRST_YEAR
        )

        contentResolver.query(
            albumUri,
            albumProjection,
            "${MediaStore.Audio.Albums._ID} = ?",
            arrayOf(id),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID)
                val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM)
                val artistIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ARTIST)
                val yearIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.FIRST_YEAR)

                val albumName = cursor.getString(nameIdx) ?: "Unknown"
                val artistName = cursor.getString(artistIdx) ?: "Unknown"
                val year = cursor.getInt(yearIdx)

                val songs = LocalUtils.getLocalAlbumSongs(appContext, id, this.id)
                val totalDuration = songs.sumOf { it.durationMs }
                val genres = songs.firstOrNull()?.genres

                return MediaModel.Album(
                    id = id,
                    providerId = this@LocalMediaProvider.id,
                    providerType = ProviderType.LOCAL_FOLDER,
                    albumArtistName = artistName,
                    durationMs = totalDuration,
                    genres = genres?:emptyList(),
                    name = albumName,
                    releaseYear = year,
                    songs = songs,
                    songCount = songs.size
                )
            }
        }

        throw Exception("Failed to get local album $id")
    }

    override suspend fun getAlbumInfo(id: String): AlbumInfo {
        TODO("Not yet implemented")
    }

    override suspend fun getAlbumList(query: MediaQuery.AlbumListQuery): List<MediaModel.Album> {
        Log.d(TAG, "Getting All Albums")

        val sortOrder = ALBUM_SORT_BINDING[query.sortBy]?.format(query.sortOrder.name)
            ?: ("${MediaStore.Audio.Albums.ALBUM} ASC")

        val folders = data.libraries.filter { it.second }.map { it.first.name }

        var albums = LocalUtils.getLocalAlbums(appContext, id, sortOrder, folders)

        if (query.artistIds?.isNotEmpty()?:false) {
            albums = albums.filter { it.artists.any { artist -> query.artistIds.contains(artist.id) } }
        }

        return PagingUtils.paginate(albums, query.limit, query.startIndex)
    }

    override suspend fun getAlbumRadio(
        albumId: String,
        count: Int?
    ): List<MediaModel.Song> {
        val songs = getAllLocalSongs()
        return SimilarSongs.pick(songs, songs.filter { it.albumId == albumId }, count ?: 50)
    }

    override suspend fun getArtistList(query: MediaQuery.ArtistListQuery): List<MediaModel.Artist> {
        TODO("Not yet implemented")
    }

    override suspend fun getArtistRadio(
        artistId: String,
        count: Int?
    ): List<MediaModel.Song> {
        val songs = getAllLocalSongs()
        val albumIds = getAlbumArtistDetail(artistId).albums.orEmpty().map { it.id }.toSet()
        val seeds = songs.filter { song -> song.albumId in albumIds || (song.artists + song.albumArtists).any { it.id == artistId } }
        return SimilarSongs.pick(songs, seeds, count ?: 50)
    }

    override suspend fun getDownloadUrl(id: String): String {
        TODO("Not yet implemented")
    }

    /** The directories of the enabled libraries, found in the paths of [songs]. */
    private fun libraryRoots(songs: List<MediaModel.Song>): List<String> =
        // Libraries may be stored as a part of a path ("Music"), the matching directories are the library roots.
        data.libraries.filter { it.second }
            .map { it.first.name.trimEnd('/') }
            .filter { it.isNotEmpty() }
            .flatMap { library ->
                songs.mapNotNull { song ->
                    val path = song.path!!
                    var index = path.indexOf(library)
                    while (index >= 0 && path.getOrNull(index + library.length) != '/')
                        index = path.indexOf(library, index + 1)
                    if (index >= 0) path.substring(0, index + library.length) else null
                }
            }
            .distinct()
            .sortedBy { File(it).name.lowercase() }

    override suspend fun getFolderSongs(id: String, limit: Int): List<MediaModel.Song> {
        val songs = getAllLocalSongs().filter { !it.path.isNullOrEmpty() }
        val directories = if (id == MediaModel.Folder.ROOT_ID) libraryRoots(songs) else listOf(id)
        // Same order as the folder screen: in each folder, its subfolders then its files, by name.
        val byFolderOrder = Comparator<MediaModel.Song> { a, b ->
            val aParts = a.path!!.split('/')
            val bParts = b.path!!.split('/')
            val common = aParts.zip(bParts).indexOfFirst { (x, y) -> x != y }.takeIf { it >= 0 }
                ?: return@Comparator aParts.size - bParts.size
            val aIsFolder = common < aParts.size - 1
            val bIsFolder = common < bParts.size - 1
            when {
                aIsFolder != bIsFolder -> if (aIsFolder) -1 else 1
                else -> aParts[common].lowercase().compareTo(bParts[common].lowercase())
            }
        }
        return directories.flatMap { directory -> songs.filter { it.path!!.startsWith("$directory/") }.sortedWith(byFolderOrder) }
            .distinctBy { it.id }
            .take(limit)
    }

    override suspend fun getFolder(query: MediaQuery.FolderQuery): MediaModel.Folder {
        // MediaStore has no folders: they are rebuilt from the paths of the songs. Folder IDs are their absolute paths.
        val songs = getAllLocalSongs().filter { !it.path.isNullOrEmpty() }

        val roots = libraryRoots(songs)

        // A folder shows the artwork of its first song.
        fun folder(path: String, parentId: String?, content: List<MediaModel.Song>, children: MediaModel.Folder.Children? = null) = MediaModel.Folder(
            id = path,
            providerId = id,
            providerType = ProviderType.LOCAL_FOLDER,
            children = children,
            imageId = content.firstOrNull()?.imageId,
            name = File(path).name,
            parentId = parentId
        )

        // With several libraries, the root lists them. With only one, the root shows its content directly.
        if (query.id == MediaModel.Folder.ROOT_ID && roots.size != 1) {
            return MediaModel.Folder(
                id = MediaModel.Folder.ROOT_ID,
                providerId = id,
                providerType = ProviderType.LOCAL_FOLDER,
                name = "",
                children = MediaModel.Folder.Children(
                    folders = roots.map { root ->
                        folder(root, MediaModel.Folder.ROOT_ID, songs.filter { it.path!!.startsWith("$root/") })
                    },
                    songs = emptyList()
                )
            )
        }

        val directory = if (query.id == MediaModel.Folder.ROOT_ID) roots.first() else query.id
        val parentId = when {
            query.id == MediaModel.Folder.ROOT_ID -> null
            directory in roots -> MediaModel.Folder.ROOT_ID
            else -> File(directory).parent
        }

        val content = songs.filter { it.path!!.startsWith("$directory/") }
        val (nested, files) = content.partition { it.path!!.removePrefix("$directory/").contains('/') }
        val subfolders = nested
            .groupBy { "$directory/" + it.path!!.removePrefix("$directory/").substringBefore('/') }
            .toList()
            .sortedBy { File(it.first).name.lowercase() }

        return folder(
            directory,
            parentId,
            content,
            MediaModel.Folder.Children(
                folders = subfolders.map { (path, songs) -> folder(path, directory, songs) },
                songs = files.sortedBy { File(it.path!!).name.lowercase() }
            )
        ).copy(id = query.id)
    }

    override suspend fun getGenreList(query: MediaQuery.GenreListQuery): List<MediaModel.Genre> {
        val folders = data.libraries.filter { it.second }.map { it.first.name }
        // MediaStore has no genre IDs here, the name is used instead.
        val genres = LocalUtils.getLocalSongs(appContext, id, "${MediaStore.Audio.Media.TITLE} ASC", folders)
            .flatMap { it.genres }
            .map { it.name }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .filter { query.searchTerm.isNullOrBlank() || it.contains(query.searchTerm, true) }
            .sortedBy { it.lowercase() }
            .map { MediaModel.Genre(name = it, id = it) }
        return PagingUtils.paginate(genres, query.limit, query.startIndex)
    }

    override suspend fun getImageRequest(
        id: String,
        itemType: LibraryType,
        size: Int?,
        baseUrl: String?
    ): ImageRequest? {
        TODO("Not yet implemented")
    }

    override fun getImageUrl(
        id: String,
        itemType: LibraryType?,
        size: Int?,
    ): String {
        when (itemType) {
            LibraryType.ALBUM, LibraryType.SONG  -> {
                val artworkUri = "$ALBUM_ART_PATH/$id".toUri().let { uri ->
                    try {
                        appContext.contentResolver.openInputStream(uri)?.close()
                        uri
                    } catch (e: Exception) {
                        "android.resource://com.craftworks.music/${R.drawable.albumplaceholder}".toUri()
                    }
                }

                return artworkUri.toString()
            }
            else -> return "android.resource://com.craftworks.music/${R.drawable.placeholder}"
        }
    }

    override suspend fun getInternetRadioStations(): List<MediaModel.InternetRadioStation> {
        // TODO: Manage with this a db
        return emptyList()
    }

    override suspend fun getLyrics(songId: String): List<Lyrics> {
        //TODO("Not yet implemented")
        return emptyList()
    }

    override suspend fun getMusicFolderList(): List<MusicFolder> = data.libraries.map { it.first }

    override suspend fun getPlaylistDetail(id: String): MediaModel.Playlist {
        TODO("Not yet implemented")
    }

    override suspend fun getPlaylistList(query: MediaQuery.PlaylistListQuery): List<MediaModel.Playlist> {
        // TODO: Manage with this a db
        return emptyList()
    }

    override suspend fun getPlaylistSongList(id: String): List<MediaModel.Song> {
        // TODO: Manage with this a db
        return emptyList()
    }

    override suspend fun getPlayQueue(): GetQueueResponse {
        TODO("Not yet implemented")
    }

    override suspend fun getRandomSongList(query: MediaQuery.RandomSongListQuery): List<MediaModel.Song> {
        TODO("Not yet implemented")
    }

    override suspend fun getRoles(): List<String> {
        TODO("Not yet implemented")
    }

    override suspend fun getProviderInfo(): ProviderInfo {
        TODO("Not yet implemented")
    }

    override suspend fun getSimilarSongs(
        songId: String,
        count: Int?,
        musicFolderId: List<String>?
    ): List<MediaModel.Song> {
        val songs = getAllLocalSongs()
        return SimilarSongs.pick(songs, songs.filter { it.id == songId }, count ?: 50)
    }

    override suspend fun getArtistAppearances(artistId: String, artistName: String): List<MediaModel.Album> {
        val albumIds = getAllLocalSongs()
            .filter { song -> song.artists.any { it.id == artistId } }
            .mapTo(HashSet()) { it.albumId }
        return LocalUtils.getLocalAlbums(appContext, id, "${MediaStore.Audio.Albums.ALBUM} ASC", data.libraries.filter { it.second }.map { it.first.name })
            .filter { it.id in albumIds }
    }

    private fun getAllLocalSongs(): List<MediaModel.Song> =
        LocalUtils.getLocalSongs(
            appContext, id, "${MediaStore.Audio.Media.TITLE} ASC",
            data.libraries.filter { it.second }.map { it.first.name }
        )


    override suspend fun getSongDetail(id: String): MediaModel.Song {
        TODO("Not yet implemented")
    }

    override suspend fun getSongList(query: MediaQuery.SongListQuery): List<MediaModel.Song> {
        Log.d(TAG, "Getting All Songs")

        val sortOrder = SONG_SORT_BINDING[query.sortBy]?.format(query.sortOrder.name)
            ?: ("${MediaStore.Audio.Media.TITLE} ASC")

        val folders = data.libraries.filter { it.second }.map { it.first.name }

        val songs = LocalUtils.getLocalSongs(appContext, id, sortOrder, folders)

        return PagingUtils.paginate(songs, query.limit, query.startIndex)
    }

    override fun getStreamUrl(
        id: String,
        transcode: Boolean,
        bitrate: Int?,
        format: String?,
        mediaType: String?,
        offset: Int?,
        skipAutoTranscode: Boolean?
    ): String {
        return ContentUris.withAppendedId(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            id.toLong()
        ).toString()
    }

    override suspend fun getTagList(
        type: LibraryType,
        folder: String?,
        tagName: String?
    ): TagListResponse {
        TODO("Not yet implemented")
    }

    override suspend fun getTopSongs(
        artist: String,
        artistId: String,
        limit: Int?,
        type: String?
    ): List<MediaModel.Song> {
        // No play counts here: the songs of the artist, in a random order.
        return getAllLocalSongs()
            .filter { song -> (song.artists + song.albumArtists).any { it.id == artistId } || song.artistName.equals(artist, true) }
            .shuffled()
            .take(limit ?: 50)
    }

    override suspend fun getUserInfo(
        id: String,
        username: String
    ): UserInfoResponse {
        TODO("Not yet implemented")
    }

    override suspend fun getUserList(query: MediaQuery.UserListQuery): List<User> {
        TODO("Not yet implemented")
    }

    override suspend fun movePlaylistItem(
        playlistId: String,
        trackId: String,
        startingIndex: Int,
        endingIndex: Int
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun ping(): Boolean {
        return true
    }

    override suspend fun removeFromPlaylist(
        id: String,
        songIds: List<String>
    ): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun replacePlaylist(
        id: String,
        songIds: List<String>
    ): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun savePlayQueue(
        songs: List<String>,
        currentIndex: Int?,
        positionMs: Int?
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun scrobble(
        id: String,
        mediaType: ScrobbleMediaType,
        playbackRate: Float,
        submission: Boolean,
        albumId: String?,
        event: ScrobbleEvent?,
        position: Int?,
        time: Long?
    ) {
        Log.d(TAG, "Not scrobbling on local media $id")
    }

    override suspend fun search(query: MediaQuery.SearchQuery): SearchResponse {
        TODO("Not yet implemented")
    }

    override suspend fun setPlaylistSongs(
        id: String,
        songIds: List<String>
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun setRating(
        ids: List<String>,
        rating: Int,
        type: LibraryType
    ): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun shareItem(
        ids: List<String>,
        description: String?,
        expires: Long?
    ): String? = null

    override suspend fun updateInternetRadioStation(
        id: String,
        name: String,
        streamUrl: String,
        homepageUrl: String?
    ): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun updatePlaylist(
        id: String,
        name: String,
        comment: String?,
        ownerId: String?,
        public: Boolean?,
        queryBuilderRules: PlaylistRules?,
        sync: Boolean?
    ): Boolean {
        TODO("Not yet implemented")
    }

}