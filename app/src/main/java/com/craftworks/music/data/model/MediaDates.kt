package com.craftworks.music.data.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/*
 * Every date of the media models is a UNIX date, in epoch milliseconds, whatever the provider:
 * - timestamps (createdAt, updatedAt, lastPlayedAt) are instants;
 * - release dates (releaseDate, originalDate) are calendar days, stored as midnight UTC of the day,
 *   so they must be read and formatted in UTC. They are only set when the day is known: the year
 *   alone is in releaseYear / originalYear.
 */

/** Parses the timestamps of providers: ISO dates (Navidrome, Subsonic) or epoch seconds (local MediaStore). */
fun parseDateMillis(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    value.toLongOrNull()?.let { return if (it < 100_000_000_000L) it * 1000 else it }
    return try { Instant.parse(value).toEpochMilli() } catch (_: Exception) { null }
        ?: try { OffsetDateTime.parse(value).toInstant().toEpochMilli() } catch (_: Exception) { null }
        ?: try { LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() } catch (_: Exception) { null }
        ?: try { LocalDate.parse(value.take(10)).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() } catch (_: Exception) { null }
}

/** A timestamp of a provider, see [parseDateMillis]. The dates before 1970 are placeholders for "never". */
fun parseTimestamp(value: String?): Long? = parseDateMillis(value)?.takeIf { it > 0 }

/** A release date given as "yyyy-MM-dd" (anything after is ignored), or null if the day isn't known. */
fun parseReleaseDate(value: String?): Long? {
    if (value == null || value.length < 10) return null
    return try { releaseDateMillis(LocalDate.parse(value.take(10))) } catch (_: Exception) { null }
}

/** A release date from its parts, or null if the day isn't known. */
fun releaseDateMillis(year: Int?, month: Int?, day: Int?): Long? {
    if (year == null || year <= 0 || month == null || day == null) return null
    return try { releaseDateMillis(LocalDate.of(year, month, day)) } catch (_: Exception) { null }
}

fun releaseDateMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** The day of a release date, see [releaseDateMillis]. */
fun releaseDay(epochMillis: Long): LocalDate = Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC).toLocalDate()

/**
 * Serializes a date as epoch milliseconds, and still reads the strings the models used to store
 * (smart cache database, playback resumption) with [parse].
 */
@OptIn(ExperimentalSerializationApi::class)
abstract class LenientDateSerializer(name: String, private val parse: (String) -> Long?) : KSerializer<Long?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(name, PrimitiveKind.LONG).nullable

    override fun serialize(encoder: Encoder, value: Long?) {
        if (value == null) encoder.encodeNull() else encoder.encodeLong(value)
    }

    override fun deserialize(decoder: Decoder): Long? {
        if (decoder is JsonDecoder) {
            val element = decoder.decodeJsonElement()
            if (element is JsonNull) return null
            val primitive = element as? JsonPrimitive ?: return null
            return if (primitive.isString) parse(primitive.content) else primitive.longOrNull
        }
        return if (decoder.decodeNotNullMark()) decoder.decodeLong() else decoder.decodeNull()
    }
}

object TimestampSerializer : LenientDateSerializer("com.craftworks.music.Timestamp", ::parseTimestamp)

object ReleaseDateSerializer : LenientDateSerializer("com.craftworks.music.ReleaseDate", ::parseReleaseDate)
