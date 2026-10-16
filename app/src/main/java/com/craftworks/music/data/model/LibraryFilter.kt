package com.craftworks.music.data.model

import androidx.annotation.StringRes
import com.craftworks.music.R
import com.craftworks.music.data.providers.media.MediaProvider
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * A user defined filter for the Songs, Albums and Artists screens ("Advanced Filter" setting).
 * In [FilterMode.SIMPLE] it uses a few basic parameters ([simple]), in [FilterMode.ADVANCED] a list
 * of rules matched with AND ([FilterMatch.ALL]) or OR ([FilterMatch.ANY]). Both are kept so switching
 * mode does not lose anything. Simple parameters are turned into rules, so both modes are checked the
 * same way: on the client against the [MediaModel] returned by the provider, which works with every provider.
 */
@Serializable
data class LibraryFilter(
    val mode: FilterMode = FilterMode.SIMPLE,
    val simple: SimpleFilter = SimpleFilter(),
    val match: FilterMatch = FilterMatch.ALL,
    val rules: List<FilterRule> = emptyList()
) {
    private val effectiveMatch: FilterMatch get() = if (mode == FilterMode.SIMPLE) FilterMatch.ALL else match

    /** Rules of the current mode. Rules with an incomplete value are ignored. */
    val activeRules: List<FilterRule>
        get() = (if (mode == FilterMode.SIMPLE) simple.toRules() else rules).filter { it.isComplete() }
    val isActive: Boolean get() = activeRules.isNotEmpty()

    /** true or false when every result must have this favorite status, null otherwise. Used to filter on the server too. */
    val requiredFavorite: Boolean?
        get() {
            val rules = activeRules
            if (effectiveMatch == FilterMatch.ANY && rules.size > 1) return null
            return rules.firstOrNull { it.field == FilterField.FAVORITE }?.let { it.operator == FilterOperator.IS_TRUE }
        }

    /**
     * IDs to send to the server ([MediaQuery] artistIds/albumIds/genreIds) for the artists, albums and genres picked
     * in Simple mode, when [provider] supports it. Only the ones picked with this provider are returned, other providers use other IDs.
     * The server may return more than what matches (an artist in any role), the client check narrows it down.
     */
    fun serverArtistIds(provider: MediaProvider?): List<String>? {
        if (!canFilterOnServer(provider)) return null
        return simple.artists.ids(provider!!.id) ?: simple.albumArtists.ids(provider.id)
    }
    fun serverGenreIds(provider: MediaProvider?): List<String>? =
        if (canFilterOnServer(provider)) simple.genres.ids(provider!!.id) else null
    fun serverAlbumIds(provider: MediaProvider?): List<String>? =
        if (canFilterOnServer(provider)) simple.albums.ids(provider!!.id) else null

    private fun canFilterOnServer(provider: MediaProvider?) =
        mode == FilterMode.SIMPLE && provider?.featureFlags?.contains(ProviderFeature.FILTER_BY_ID) == true

    // Picked entities with an ID of another provider are only matched by name, so the server must not filter on them.
    private fun List<FilterEntity>.ids(providerId: String): List<String>? =
        takeIf { it.isNotEmpty() && it.all { entity -> entity.providerId == providerId } }?.map { it.id }

    fun matches(song: MediaModel.Song): Boolean = matches { LibraryFilterValues.song(song, it) }
    fun matches(album: MediaModel.Album): Boolean = matches { LibraryFilterValues.album(album, it) }
    fun matches(artist: MediaModel.Artist): Boolean = matches { LibraryFilterValues.artist(artist, it) }

    private inline fun matches(valueOf: (FilterField) -> FilterValue): Boolean {
        val rules = activeRules
        if (rules.isEmpty()) return true
        return if (effectiveMatch == FilterMatch.ALL) rules.all { it.matches(valueOf(it.field)) }
        else rules.any { it.matches(valueOf(it.field)) }
    }
}

@Serializable
enum class FilterMode(@StringRes val label: Int) {
    SIMPLE(R.string.filter_mode_simple),
    ADVANCED(R.string.filter_mode_advanced)
}

/** An artist, album or genre picked in the Simple filter. */
@Serializable
data class FilterEntity(
    val id: String,
    val name: String,
    val providerId: String,
    /** Shown next to the name in search results, like the artist of an album. */
    val detail: String? = null
)

/** Basic parameters of [FilterMode.SIMPLE]. null and empty values are unset. */
@Serializable
data class SimpleFilter(
    val favorite: Boolean? = null,
    /** Star range, 0 is unrated. 0 to [MAX_RATING] is unset. */
    val minRating: Int = 0,
    val maxRating: Int = MAX_RATING,
    val compilation: Boolean? = null,
    val explicit: Boolean? = null,
    /** Picked artists, albums and genres. Several picks of the same kind match any of them. */
    val artists: List<FilterEntity> = emptyList(),
    val albumArtists: List<FilterEntity> = emptyList(),
    val albums: List<FilterEntity> = emptyList(),
    val genres: List<FilterEntity> = emptyList(),
    val minYear: String = "",
    val maxYear: String = ""
) {
    fun toRules(): List<FilterRule> = buildList {
        fun bool(field: FilterField, value: Boolean?) {
            if (value != null) add(FilterRule(field, if (value) FilterOperator.IS_TRUE else FilterOperator.IS_FALSE))
        }
        fun entities(field: FilterField, value: List<FilterEntity>) {
            if (value.isNotEmpty()) add(FilterRule(field, FilterOperator.IS_ANY_OF, entities = value))
        }
        bool(FilterField.FAVORITE, favorite)
        if (minRating > 0 || maxRating < MAX_RATING)
            add(FilterRule(FilterField.RATING, FilterOperator.BETWEEN, minRating.toString(), maxRating.toString()))
        bool(FilterField.COMPILATION, compilation)
        bool(FilterField.EXPLICIT, explicit)
        entities(FilterField.ARTIST, artists)
        entities(FilterField.ALBUM_ARTIST, albumArtists)
        entities(FilterField.ALBUM, albums)
        entities(FilterField.GENRE, genres)
        // A single bound is a range: "from" goes up to MAX_YEAR, "to" starts at MIN_YEAR.
        val min = minYear.trim().toIntOrNull()
        val max = maxYear.trim().toIntOrNull()
        if (min != null || max != null)
            add(FilterRule(FilterField.YEAR, FilterOperator.BETWEEN, (min ?: MIN_YEAR).toString(), (max ?: MAX_YEAR).toString()))
    }

    companion object {
        const val MAX_RATING = 5
        const val MIN_YEAR = 0
        const val MAX_YEAR = 9999
    }
}

/** The parameters shown in [FilterMode.SIMPLE], per screen. */
enum class SimpleFilterOption(@StringRes val label: Int) {
    FAVORITE(R.string.sort_by_favorite),
    RATING(R.string.sort_by_rating),
    COMPILATION(R.string.filter_field_compilation),
    EXPLICIT(R.string.sort_by_explicit_status),
    ARTIST(R.string.sort_by_artist),
    ALBUM_ARTIST(R.string.sort_by_album_artist),
    ALBUM(R.string.sort_by_album),
    GENRE(R.string.sort_by_genre),
    YEAR(R.string.sort_by_year);

    companion object {
        val songOptions = listOf(FAVORITE, RATING, COMPILATION, EXPLICIT, ARTIST, ALBUM_ARTIST, ALBUM, GENRE, YEAR)
        val albumOptions = listOf(FAVORITE, RATING, COMPILATION, EXPLICIT, ARTIST, ALBUM_ARTIST, GENRE, YEAR)
        val artistOptions = listOf(FAVORITE, RATING, GENRE)
    }
}

@Serializable
enum class FilterMatch(@StringRes val label: Int) {
    ALL(R.string.filter_match_all),
    ANY(R.string.filter_match_any)
}

enum class FilterValueType(val operators: List<FilterOperator>) {
    TEXT(listOf(
        FilterOperator.CONTAINS, FilterOperator.NOT_CONTAINS, FilterOperator.IS, FilterOperator.IS_NOT,
        FilterOperator.STARTS_WITH, FilterOperator.ENDS_WITH
    )),
    NUMBER(listOf(
        FilterOperator.IS, FilterOperator.IS_NOT, FilterOperator.GREATER_THAN, FilterOperator.LESS_THAN,
        FilterOperator.BETWEEN
    )),
    BOOLEAN(listOf(FilterOperator.IS_TRUE, FilterOperator.IS_FALSE)),
    DATE(listOf(
        FilterOperator.IN_THE_LAST, FilterOperator.NOT_IN_THE_LAST, FilterOperator.BEFORE, FilterOperator.AFTER
    ))
}

@Serializable
enum class FilterOperator(@StringRes val label: Int, val valueCount: Int = 1) {
    CONTAINS(R.string.filter_op_contains),
    NOT_CONTAINS(R.string.filter_op_not_contains),
    IS(R.string.filter_op_is),
    IS_NOT(R.string.filter_op_is_not),
    STARTS_WITH(R.string.filter_op_starts_with),
    ENDS_WITH(R.string.filter_op_ends_with),
    GREATER_THAN(R.string.filter_op_greater_than),
    LESS_THAN(R.string.filter_op_less_than),
    BETWEEN(R.string.filter_op_between, 2),
    IS_TRUE(R.string.filter_op_is_true, 0),
    IS_FALSE(R.string.filter_op_is_false, 0),
    IN_THE_LAST(R.string.filter_op_in_the_last),
    NOT_IN_THE_LAST(R.string.filter_op_not_in_the_last),
    BEFORE(R.string.filter_op_before),
    AFTER(R.string.filter_op_after),
    /** Matches picked [FilterRule.entities] by ID, or by name for providers without IDs. Only used by the Simple filter. */
    IS_ANY_OF(R.string.filter_op_is);

    /** Operators that match when no value of the item matches the rule. */
    val isNegative: Boolean get() = this == NOT_CONTAINS || this == IS_NOT || this == NOT_IN_THE_LAST
}

@Serializable
enum class FilterField(@StringRes val label: Int, val type: FilterValueType) {
    TITLE(R.string.filter_field_title, FilterValueType.TEXT),
    NAME(R.string.filter_field_name, FilterValueType.TEXT),
    ALBUM(R.string.sort_by_album, FilterValueType.TEXT),
    ARTIST(R.string.sort_by_artist, FilterValueType.TEXT),
    ALBUM_ARTIST(R.string.sort_by_album_artist, FilterValueType.TEXT),
    GENRE(R.string.sort_by_genre, FilterValueType.TEXT),
    COMMENT(R.string.sort_by_comment, FilterValueType.TEXT),
    FORMAT(R.string.filter_field_format, FilterValueType.TEXT),
    PATH(R.string.filter_field_path, FilterValueType.TEXT),
    RECORD_LABEL(R.string.filter_field_record_label, FilterValueType.TEXT),
    RELEASE_TYPE(R.string.filter_field_release_type, FilterValueType.TEXT),
    YEAR(R.string.sort_by_year, FilterValueType.NUMBER),
    DURATION(R.string.filter_field_duration_seconds, FilterValueType.NUMBER),
    TRACK_NUMBER(R.string.sort_by_track_number, FilterValueType.NUMBER),
    DISC_NUMBER(R.string.filter_field_disc_number, FilterValueType.NUMBER),
    SONG_COUNT(R.string.sort_by_song_count, FilterValueType.NUMBER),
    ALBUM_COUNT(R.string.sort_by_album_count, FilterValueType.NUMBER),
    PLAY_COUNT(R.string.sort_by_play_count, FilterValueType.NUMBER),
    RATING(R.string.sort_by_rating, FilterValueType.NUMBER),
    BPM(R.string.sort_by_bpm, FilterValueType.NUMBER),
    BITRATE(R.string.filter_field_bitrate, FilterValueType.NUMBER),
    SAMPLE_RATE(R.string.filter_field_sample_rate, FilterValueType.NUMBER),
    BIT_DEPTH(R.string.filter_field_bit_depth, FilterValueType.NUMBER),
    CHANNELS(R.string.sort_by_channels, FilterValueType.NUMBER),
    FAVORITE(R.string.sort_by_favorite, FilterValueType.BOOLEAN),
    COMPILATION(R.string.filter_field_compilation, FilterValueType.BOOLEAN),
    EXPLICIT(R.string.sort_by_explicit_status, FilterValueType.BOOLEAN),
    DATE_ADDED(R.string.filter_field_date_added, FilterValueType.DATE),
    LAST_PLAYED(R.string.filter_field_last_played, FilterValueType.DATE);

    companion object {
        val songFields = listOf(
            TITLE, ALBUM, ARTIST, ALBUM_ARTIST, GENRE, YEAR, DURATION, TRACK_NUMBER, DISC_NUMBER,
            PLAY_COUNT, RATING, FAVORITE, BPM, BITRATE, SAMPLE_RATE, BIT_DEPTH, CHANNELS, FORMAT,
            COMPILATION, EXPLICIT, COMMENT, PATH, DATE_ADDED, LAST_PLAYED
        )
        val albumFields = listOf(
            NAME, ALBUM_ARTIST, ARTIST, GENRE, YEAR, DURATION, SONG_COUNT, PLAY_COUNT, RATING,
            FAVORITE, COMPILATION, EXPLICIT, RECORD_LABEL, RELEASE_TYPE, COMMENT, DATE_ADDED, LAST_PLAYED
        )
        val artistFields = listOf(
            NAME, GENRE, ALBUM_COUNT, SONG_COUNT, DURATION, PLAY_COUNT, RATING, FAVORITE, LAST_PLAYED
        )
    }
}

@Serializable
data class FilterRule(
    val field: FilterField,
    val operator: FilterOperator = field.type.operators.first(),
    val value: String = "",
    val value2: String = "",
    val entities: List<FilterEntity> = emptyList()
) {
    fun isComplete(): Boolean {
        if (operator == FilterOperator.IS_ANY_OF) return field.type == FilterValueType.TEXT && entities.isNotEmpty()
        if (operator !in field.type.operators) return false
        return when (field.type) {
            FilterValueType.TEXT -> value.isNotEmpty()
            FilterValueType.NUMBER -> value.toDoubleOrNull() != null &&
                    (operator.valueCount < 2 || value2.toDoubleOrNull() != null)
            FilterValueType.BOOLEAN -> true
            FilterValueType.DATE -> when (operator) {
                FilterOperator.BEFORE, FilterOperator.AFTER -> parseDay(value) != null
                else -> value.toLongOrNull() != null
            }
        }
    }

    fun matches(value: FilterValue): Boolean = when (value) {
        // Fields like artists or genres have several values: a negative rule must hold for all of them.
        is FilterValue.Text -> when {
            operator == FilterOperator.IS_ANY_OF -> entities.any { entity ->
                entity.id in value.ids || value.values.any { it.equals(entity.name, true) }
            }
            operator.isNegative -> value.values.all { matchesText(it) }
            else -> value.values.any { matchesText(it) }
        }
        is FilterValue.Number -> matchesNumber(value.value)
        is FilterValue.Bool -> if (operator == FilterOperator.IS_TRUE) value.value == true else value.value != true
        is FilterValue.Date -> matchesDate(value.epochMillis)
    }

    private fun matchesText(text: String): Boolean = when (operator) {
        FilterOperator.CONTAINS -> text.contains(value, true)
        FilterOperator.NOT_CONTAINS -> !text.contains(value, true)
        FilterOperator.IS -> text.equals(value, true)
        FilterOperator.IS_NOT -> !text.equals(value, true)
        FilterOperator.STARTS_WITH -> text.startsWith(value, true)
        FilterOperator.ENDS_WITH -> text.endsWith(value, true)
        else -> false
    }

    private fun matchesNumber(number: Double?): Boolean {
        val first = value.toDoubleOrNull() ?: return true
        if (number == null) return operator == FilterOperator.IS_NOT
        return when (operator) {
            FilterOperator.IS -> number == first
            FilterOperator.IS_NOT -> number != first
            FilterOperator.GREATER_THAN -> number > first
            FilterOperator.LESS_THAN -> number < first
            FilterOperator.BETWEEN -> {
                val second = value2.toDoubleOrNull() ?: return true
                number in minOf(first, second)..maxOf(first, second)
            }
            else -> false
        }
    }

    private fun matchesDate(epochMillis: Long?): Boolean {
        when (operator) {
            FilterOperator.IN_THE_LAST, FilterOperator.NOT_IN_THE_LAST -> {
                val days = value.toLongOrNull() ?: return true
                val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days)
                val inRange = epochMillis != null && epochMillis >= since
                return if (operator == FilterOperator.IN_THE_LAST) inRange else !inRange
            }
            FilterOperator.BEFORE -> {
                val day = parseDay(value) ?: return true
                return epochMillis != null && epochMillis < day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
            FilterOperator.AFTER -> {
                val day = parseDay(value) ?: return true
                return epochMillis != null && epochMillis >= day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
            else -> return false
        }
    }

    companion object {
        /** Parses a "yyyy-mm-dd" date typed by the user. */
        fun parseDay(text: String): LocalDate? = try { LocalDate.parse(text.trim()) } catch (_: Exception) { null }
    }
}

sealed class FilterValue {
    /** [ids] are the provider IDs of the values, when they have one (artists, genres). */
    data class Text(val values: List<String>, val ids: List<String> = emptyList()) : FilterValue()
    data class Number(val value: Double?) : FilterValue()
    data class Bool(val value: Boolean?) : FilterValue()
    data class Date(val epochMillis: Long?) : FilterValue()
}

/** Reads the value of a [FilterField] from each media model. */
private object LibraryFilterValues {
    private fun text(vararg values: String?) = FilterValue.Text(values.filterNotNull().filter { it.isNotEmpty() })
    private fun texts(values: Iterable<String?>) = FilterValue.Text(values.filterNotNull().filter { it.isNotEmpty() })
    private fun artists(names: List<String?>, artists: List<MediaModel.Artist>) =
        FilterValue.Text((names + artists.map { it.name }).filterNotNull().filter { it.isNotEmpty() }, artists.map { it.id })
    private fun genres(genres: List<MediaModel.Genre>) =
        FilterValue.Text(genres.map { it.name }.filter { it.isNotEmpty() }, genres.mapNotNull { it.id })
    private fun number(value: kotlin.Number?) = FilterValue.Number(value?.toDouble())
    private fun date(epochMillis: Long?) = FilterValue.Date(epochMillis)
    private val none = FilterValue.Text(emptyList())

    fun song(song: MediaModel.Song, field: FilterField): FilterValue = when (field) {
        FilterField.TITLE, FilterField.NAME -> text(song.name)
        FilterField.ALBUM -> FilterValue.Text(listOfNotNull(song.album?.takeIf { it.isNotEmpty() }), listOf(song.albumId))
        // Participants hold every role (artist, album artist, composer…) when the provider sends them.
        FilterField.ARTIST -> artists(
            listOf(song.artistName),
            song.artists + song.albumArtists + song.participants.orEmpty().values.flatten()
        )
        FilterField.ALBUM_ARTIST -> artists(
            listOf(song.albumArtistName),
            song.albumArtists + song.participants?.get("albumartist").orEmpty()
        )
        FilterField.GENRE -> genres(song.genres)
        FilterField.COMMENT -> text(song.comment)
        FilterField.FORMAT -> text(song.format, song.container)
        FilterField.PATH -> text(song.path)
        FilterField.YEAR -> number(song.releaseYear)
        FilterField.DURATION -> number(song.durationMs / 1000)
        FilterField.TRACK_NUMBER -> number(song.trackNumber)
        FilterField.DISC_NUMBER -> number(song.discNumber)
        FilterField.PLAY_COUNT -> number(song.playCount ?: 0)
        FilterField.RATING -> number(song.userRating ?: 0)
        FilterField.BPM -> number(song.bpm)
        FilterField.BITRATE -> number(song.bitRate)
        FilterField.SAMPLE_RATE -> number(song.sampleRate)
        FilterField.BIT_DEPTH -> number(song.bitDepth)
        FilterField.CHANNELS -> number(song.channels)
        FilterField.FAVORITE -> FilterValue.Bool(song.userFavorite)
        FilterField.COMPILATION -> FilterValue.Bool(song.compilation)
        FilterField.EXPLICIT -> FilterValue.Bool(song.explicit)
        FilterField.DATE_ADDED -> date(song.createdAt)
        FilterField.LAST_PLAYED -> date(song.lastPlayedAt)
        else -> none
    }

    fun album(album: MediaModel.Album, field: FilterField): FilterValue = when (field) {
        FilterField.TITLE, FilterField.NAME -> text(album.name)
        FilterField.ALBUM -> FilterValue.Text(listOf(album.name), listOf(album.id))
        FilterField.ALBUM_ARTIST -> artists(listOf(album.albumArtistName), album.artists + album.participants["albumartist"].orEmpty())
        FilterField.ARTIST -> artists(listOf(album.albumArtistName), album.artists + album.participants.values.flatten())
        FilterField.GENRE -> genres(album.genres)
        FilterField.COMMENT -> text(album.comment)
        FilterField.RECORD_LABEL -> texts(album.recordLabels)
        FilterField.RELEASE_TYPE -> texts(album.releaseTypes + album.releaseType)
        FilterField.YEAR -> number(album.releaseYear ?: album.originalYear)
        FilterField.DURATION -> number(album.durationMs / 1000)
        FilterField.SONG_COUNT -> number(album.songCount)
        FilterField.PLAY_COUNT -> number(album.playCount ?: 0)
        FilterField.RATING -> number(album.userRating ?: 0)
        FilterField.FAVORITE -> FilterValue.Bool(album.userFavorite)
        FilterField.COMPILATION -> FilterValue.Bool(album.isCompilation)
        FilterField.EXPLICIT -> FilterValue.Bool(album.explicit)
        FilterField.DATE_ADDED -> date(album.createdAt)
        FilterField.LAST_PLAYED -> date(album.lastPlayedAt)
        else -> none
    }

    fun artist(artist: MediaModel.Artist, field: FilterField): FilterValue = when (field) {
        FilterField.TITLE, FilterField.NAME, FilterField.ARTIST, FilterField.ALBUM_ARTIST -> text(artist.name)
        FilterField.GENRE -> genres(artist.genres)
        FilterField.ALBUM_COUNT -> number(artist.albumCount)
        FilterField.SONG_COUNT -> number(artist.songCount)
        FilterField.DURATION -> number(artist.durationMs?.div(1000))
        FilterField.PLAY_COUNT -> number(artist.playCount ?: 0)
        FilterField.RATING -> number(artist.userRating ?: 0)
        FilterField.FAVORITE -> FilterValue.Bool(artist.userFavorite)
        FilterField.LAST_PLAYED -> date(artist.lastPlayedAt)
        else -> none
    }
}
