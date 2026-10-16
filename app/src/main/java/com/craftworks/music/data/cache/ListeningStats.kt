package com.craftworks.music.data.cache

import androidx.media3.common.MediaMetadata
import com.craftworks.music.data.model.artists
import com.craftworks.music.data.model.id
import java.util.Calendar

/** A song played in the app, kept in the play history for the listening stats of the Home screen. */
data class PlayHistoryEntry(
    val providerId: String,
    val songId: String,
    val albumId: String?,
    val album: String?,
    val albumArtist: String?,
    /** The first artist of the song. */
    val artistId: String?,
    val artist: String?,
    val artistImage: String?,
    val artworkUri: String?,
    val durationMs: Long,
    val playedAt: Long,
) {
    companion object {
        /** The entry of the song of [metadata] played at [playedAt], or null if it has no ID. */
        fun from(providerId: String, metadata: MediaMetadata, playedAt: Long): PlayHistoryEntry? {
            val songId = metadata.id?.takeIf { it.isNotEmpty() } ?: return null
            val artist = metadata.artists?.firstOrNull()
            return PlayHistoryEntry(
                providerId = providerId,
                songId = songId,
                albumId = metadata.extras?.getString("albumId")?.takeIf { it.isNotBlank() },
                album = metadata.albumTitle?.toString(),
                albumArtist = (metadata.albumArtist ?: metadata.artist)?.toString(),
                artistId = artist?.id?.takeIf { it.isNotBlank() },
                artist = (artist?.name ?: metadata.artist?.toString())?.takeIf { it.isNotBlank() },
                artistImage = artist?.imageUrl?.takeIf { it.isNotBlank() },
                artworkUri = metadata.artworkUri?.toString(),
                durationMs = metadata.durationMs ?: 0L,
                playedAt = playedAt,
            )
        }
    }
}

/** The days the listening stats are computed over, ending today. */
enum class ListeningPeriod(val days: Int) {
    WEEK(7),
    MONTH(30),
}

/**
 * The days of a [ListeningPeriod] ending on the day of [now], from the start of the first one
 * ([since]), and the start of the same number of days before them ([previousSince]).
 */
class ListeningWindow(val period: ListeningPeriod, now: Long) {
    private val dayStarts: LongArray
    val since: Long
    val previousSince: Long

    init {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -(period.days - 1))
        }
        dayStarts = LongArray(period.days) {
            calendar.timeInMillis.also { calendar.add(Calendar.DAY_OF_YEAR, 1) }
        }
        since = dayStarts.first()
        previousSince = calendar.apply {
            timeInMillis = since
            add(Calendar.DAY_OF_YEAR, -period.days)
        }.timeInMillis
    }

    /** The index of the day of [time] in the period, or -1 if it is before it. */
    fun dayOf(time: Long): Int = dayStarts.indexOfLast { it <= time }
}

/** The most listened artist or album of a [ListeningStats] period. */
data class TopListen(
    val id: String?,
    val name: String,
    /** The album artist, for an album. */
    val subtitle: String?,
    /** The cover of the album, or of the artist's most played album. */
    val artworkUri: String?,
    /** The picture of the artist, when the provider gave one. */
    val imageUrl: String?,
    val plays: Int,
)

/** What was listened to in the app on a provider during the last [period]. */
data class ListeningStats(
    val period: ListeningPeriod,
    val listens: Int,
    /** Listens of the period just before, to compare with. */
    val previousListens: Int,
    /** Sum of the duration of the songs listened to. */
    val listenedMs: Long,
    /** How many different songs were listened to. */
    val songs: Int,
    /** Listens of each day of the period, oldest first: the last one is today. */
    val daily: List<Int>,
    val topArtist: TopListen?,
    val topAlbum: TopListen?,
) {
    val isEmpty: Boolean get() = listens == 0

    companion object {
        /** The stats of [window] from [entries], which may hold plays from before [ListeningWindow.previousSince]. */
        fun from(entries: List<PlayHistoryEntry>, window: ListeningWindow): ListeningStats {
            val plays = entries.filter { it.playedAt >= window.since }
            val daily = IntArray(window.period.days)
            plays.forEach { entry -> window.dayOf(entry.playedAt).takeIf { it >= 0 }?.let { daily[it]++ } }
            return ListeningStats(
                period = window.period,
                listens = plays.size,
                previousListens = entries.count { it.playedAt in window.previousSince until window.since },
                listenedMs = plays.sumOf { it.durationMs },
                songs = plays.distinctBy { it.songId }.size,
                daily = daily.toList(),
                topArtist = topArtist(plays),
                topAlbum = topAlbum(plays),
            )
        }

        /** The group with the most plays, the one played last on a tie. */
        private fun <K> List<PlayHistoryEntry>.mostPlayed(key: (PlayHistoryEntry) -> K?): List<PlayHistoryEntry>? =
            filter { key(it) != null }
                .groupBy(key)
                .values
                .maxWithOrNull(compareBy<List<PlayHistoryEntry>> { it.size }.thenBy { group -> group.maxOf { it.playedAt } })

        private fun topArtist(plays: List<PlayHistoryEntry>): TopListen? {
            val group = plays.mostPlayed { it.artistId ?: it.artist } ?: return null
            return TopListen(
                id = group.firstNotNullOfOrNull { it.artistId },
                name = group.firstNotNullOfOrNull { it.artist } ?: return null,
                subtitle = null,
                // The cover of the album of this artist listened to the most.
                artworkUri = group.mostPlayed { it.albumId ?: it.album }?.firstNotNullOfOrNull { it.artworkUri }
                    ?: group.firstNotNullOfOrNull { it.artworkUri },
                imageUrl = group.firstNotNullOfOrNull { it.artistImage },
                plays = group.size,
            )
        }

        private fun topAlbum(plays: List<PlayHistoryEntry>): TopListen? {
            val group = plays.mostPlayed { it.albumId } ?: return null
            return TopListen(
                id = group.first().albumId,
                name = group.firstNotNullOfOrNull { it.album }.orEmpty(),
                subtitle = group.firstNotNullOfOrNull { it.albumArtist },
                artworkUri = group.firstNotNullOfOrNull { it.artworkUri },
                imageUrl = null,
                plays = group.size,
            )
        }
    }
}
