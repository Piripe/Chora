package com.craftworks.music.data.model

import androidx.compose.runtime.Stable
import com.craftworks.music.utils.getTimeStamps
import com.craftworks.music.utils.mmssToMilliseconds
import com.craftworks.music.utils.separateBackgroundLyrics
import kotlinx.serialization.Serializable
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings

enum class LyricsRole {
    MAIN, BG
}

// Universal Lyric object
@Stable
data class Lyrics(
    val wordSynced: Boolean,
    val synced: Boolean,
    val lines: List<LyricsLine>
)

@Stable
data class LyricsLine(
    val startMs: Int,
    val endMs: Int? = null,
    val lines: List<Lyric>
)
@Stable
data class Lyric(
    val text: String,
    val words: List<SyncedWord>? = null,
    val startMs: Int? = null,
    val endMs: Int? = null,
    val role: LyricsRole = LyricsRole.MAIN
)
@Stable
data class SyncedWord(
    val text: String,
    val startMs: Int,
    val endMs: Int?
)

// LRCLIB Lyrics
@Serializable
data class LrcLibLyrics(
    val id: Int,
    val instrumental: Boolean,
    val plainLyrics: String? = "",
    val syncedLyrics: String? = "",
    val lyricsfile: String? = "",
)

// NetEase Lyrics
@Serializable
data class NeteaseLyricsResponse(
    val pureMusic: Boolean? = false,
    val lrc: NeteaseLrc? = null,
    val tlyric: NeteaseLrc? = null   // translation, may be absent or empty
)
@Serializable
data class NeteaseLrc(
    val lyric: String? = null
)

fun LrcLibLyrics.toLyrics(): List<LyricsLine> {
    if (instrumental) return listOf()

    if (lyricsfile.toString() != "null") {
        val settings = LoadSettings.builder().build()
        val raw = Load(settings).loadFromString(lyricsfile) as? Map<*, *>
            ?: throw IllegalArgumentException("Invalid YAML format")

        val linesList = raw["lines"] as? List<*> ?: emptyList<Any>()

        val lines = linesList.map { lineItem ->
            val lineMap = lineItem as? Map<*, *> ?: emptyMap<Any, Any>()

            val startMs = lineMap["start_ms"]?.toString()?.toIntOrNull() ?: 0
            val endMs = lineMap["end_ms"]?.toString()?.toIntOrNull() ?: 0

            val wordsList = lineMap["words"] as? List<*> ?: emptyList<Any>()
            val words = wordsList.map { wordItem ->
                val wordMap = wordItem as? Map<*, *> ?: emptyMap<Any, Any>()
                SyncedWord(
                    text = wordMap["text"]?.toString() ?: "",
                    startMs = wordMap["start_ms"] as? Int ?: 0,
                    endMs = wordMap["end_ms"] as? Int
                )
            }

            if (words.isEmpty()) {
                val rawText = lineMap["text"]?.toString()?.trim() ?: return emptyList()
                separateBackgroundLyrics(rawText, startMs, endMs)
            } else {
                separateBackgroundLyrics(words, startMs, endMs)
            }
        }
        return lines
    }
    else if (syncedLyrics != null) {
        val lines = mutableListOf<LyricsLine>()

        syncedLyrics.lines().forEach { lyric ->
            if (lyric.isBlank()) return@forEach
            val timeStampRaw = getTimeStamps(lyric).firstOrNull() ?: return@forEach
            val time = mmssToMilliseconds(timeStampRaw).toInt()
            val text = lyric.substringAfter("]").trim()

            separateBackgroundLyrics(text, time)
        }

        return lines
    }
    else if (plainLyrics != null) {
        return listOf(
            LyricsLine(startMs = -1, lines = listOf(Lyric(text = plainLyrics)))
        )
    }
    else
        return listOf()
}

fun NeteaseLyricsResponse.toLyrics(): List<LyricsLine> {
    if (pureMusic == true)
        return emptyList()

    val originalMap = mutableMapOf<Int, String>()
    val translationMap = mutableMapOf<Int, String>()

    lrc?.lyric?.lines()?.forEach { line ->
        val tags = getTimeStamps(line)
        if (tags.isEmpty()) return@forEach
        val text = line.substringAfter("]").trim()
        tags.forEach { tag ->
            val time = mmssToMilliseconds(tag).toInt()
            originalMap[time] = text
        }
    }
    if (!tlyric?.lyric.isNullOrEmpty()) {
        tlyric.lyric.lines().forEach { line ->
            val tags = getTimeStamps(line)
            if (tags.isEmpty()) return@forEach
            // Group lines sharing the same timestamp
            val text = line.substringAfter("]").trim()
            tags.forEach { tag ->
                val time = mmssToMilliseconds(tag).toInt()
                translationMap[time] = text
            }
        }
    }

    // Group lines sharing the same timestamp
    return originalMap
        .map { (timestamp, origLine) ->
            separateBackgroundLyrics(origLine, timestamp)
        }
}