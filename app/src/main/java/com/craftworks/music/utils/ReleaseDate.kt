package com.craftworks.music.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.os.ConfigurationCompat
import androidx.media3.common.MediaMetadata
import com.craftworks.music.data.model.releaseDay
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Formats the release date of albums and songs (epoch milliseconds, see MediaDates.kt). */
object ReleaseDate {
    /**
     * The release [date] formatted for [locale], or [year] when the day isn't known.
     * Returns null when neither is known.
     */
    fun format(date: Long?, year: Int?, locale: Locale = Locale.getDefault()): String? {
        if (date != null) {
            return releaseDay(date).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
        }
        return year?.takeIf { it > 0 }?.toString()
    }

    /** The release date of an album or song media item, see [format]. */
    fun format(metadata: MediaMetadata?, locale: Locale = Locale.getDefault()): String? {
        if (metadata == null) return null
        return format(metadata.releaseDateMillis(), metadata.recordingYear ?: metadata.releaseYear, locale)
    }
}

/** The release date the media item of an album or song carries in its extras. */
fun MediaMetadata.releaseDateMillis(): Long? =
    extras?.takeIf { it.containsKey("releaseDate") }?.getLong("releaseDate")

/** The release date of an album or song media item, formatted with the locale of the app. */
@Composable
@ReadOnlyComposable
fun releaseDateText(metadata: MediaMetadata?): String? =
    ReleaseDate.format(metadata, ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault())
