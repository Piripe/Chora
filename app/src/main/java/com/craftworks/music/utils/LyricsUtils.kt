package com.craftworks.music.utils

import com.craftworks.music.data.model.Lyric
import com.craftworks.music.data.model.LyricsLine
import com.craftworks.music.data.model.LyricsRole
import com.craftworks.music.data.model.SyncedWord

fun separateBackgroundLyrics(
    lyricsText: String,
    startMs: Int,
    endMs: Int? = null
): LyricsLine {
    val bgRegex = Regex("\\(([^()]+)\\)")
    val bgMatches = bgRegex.findAll(lyricsText).toList()

    val lines = mutableListOf<Lyric>()
    var lastIndex = 0

    bgMatches.forEach { match ->
        val mainSegment = lyricsText.substring(lastIndex, match.range.first).trim()
        if (mainSegment.isNotBlank()) {
            lines.add(Lyric(text = mainSegment, role = LyricsRole.MAIN))
        }

        val bgSegment = match.groupValues[1].trim()
        if (bgSegment.isNotBlank()) {
            lines.add(Lyric(text = bgSegment, role = LyricsRole.BG))
        }

        lastIndex = match.range.last + 1

        // Reattach punctuation right after the closing parenthesis to the previous main line
        val punctuation = Regex("^[,;:]").find(lyricsText.substring(lastIndex))?.value
        if (punctuation != null) {
            val lastMainIdx = lines.indexOfLast { it.role == LyricsRole.MAIN }
            if (lastMainIdx != -1) {
                lines[lastMainIdx] = lines[lastMainIdx].copy(
                    text = lines[lastMainIdx].text + punctuation
                )
            }
            lastIndex += punctuation.length
        }
    }

    val tailMain = lyricsText.substring(lastIndex).trim()
    if (tailMain.isNotBlank()) {
        lines.add(Lyric(text = tailMain, role = LyricsRole.MAIN))
    }

    return LyricsLine(
        startMs = startMs,
        endMs = endMs,
        lines = lines.ifEmpty { listOf(Lyric(text = lyricsText, role = LyricsRole.MAIN)) }
    )
}

fun separateBackgroundLyrics(
    wordsList: List<SyncedWord>,
    startMs: Int,
    endMs: Int?
): LyricsLine {
    val words = wordsList.mapIndexed { index, word ->
        val nextWordStart = wordsList.getOrNull(index + 1)?.startMs ?: endMs
        word.copy(endMs = word.endMs ?: nextWordStart)
    }

    val lines = mutableListOf<Lyric>()
    var currentRole: LyricsRole? = null
    var currentWords = mutableListOf<SyncedWord>()
    var inBg = false

    for (word in words) {
        val trimmedText = word.text.trim()
        val startsWithOpenParen = trimmedText.startsWith("(")

        if (startsWithOpenParen && inBg) {
            val lastWordIndex = currentWords.lastIndex
            if (lastWordIndex >= 0)
                currentWords[lastWordIndex] = currentWords[lastWordIndex].copy(
                    text = currentWords[lastWordIndex].text.trimEnd()
                )

            lines.add(
                Lyric(
                    text = currentWords.joinToString("") { it.text },
                    words = currentWords.toList(),
                    role = currentRole ?: LyricsRole.BG
                )
            )
        }

        if (startsWithOpenParen) inBg = true

        val role = if (inBg) LyricsRole.BG else LyricsRole.MAIN

        // Strip parenthesis and drop any punctuation immediately following closing parenthesis
        val clean = word.copy(
            text = word.text
                .replace(Regex("\\s+\\("), "")
                .replace(Regex("\\)[,;:]?"), "")
                .replace("(", "")
        )

        if (Regex("\\)[,;:]?$").containsMatchIn(trimmedText)) inBg = false

        if (clean.text.isBlank()) continue

        if (role != currentRole) {
            if (currentWords.isNotEmpty() && currentRole != null) {
                val lastWordIndex = currentWords.lastIndex
                if (lastWordIndex >= 0)
                    currentWords[lastWordIndex] = currentWords[lastWordIndex].copy(
                        text = currentWords[lastWordIndex].text.trimEnd()
                    )

                lines.add(
                    Lyric(
                        text = currentWords.joinToString("") { it.text },
                        words = currentWords.toList(),
                        role = currentRole
                    )
                )
            }
            currentWords = mutableListOf()
            currentRole = role
        }
        currentWords.add(clean)
    }
    if (currentWords.isNotEmpty() && currentRole != null) {
        val lastWordIndex = currentWords.lastIndex
        if (lastWordIndex >= 0)
            currentWords[lastWordIndex] = currentWords[lastWordIndex].copy(
                text = currentWords[lastWordIndex].text.trimEnd()
            )

        lines.add(
            Lyric(
                text = currentWords.joinToString("") { it.text },
                words = currentWords.toList(),
                role = currentRole
            )
        )
    }

    return LyricsLine(
        startMs = startMs,
        endMs = endMs,
        lines = lines.ifEmpty {
            listOf(Lyric(text = wordsList.joinToString("") { it.text }, words = wordsList, role = LyricsRole.MAIN))
        }
    )
}

fun splitMultipleBg(input: List<SyncedWord>): List<List<SyncedWord>> {
    val result = mutableListOf<MutableList<SyncedWord>>()

    for (raw in input) {
        var token = raw

        // A new "(" starts a new group
        if (token.text.startsWith("(")) {
            result.add(mutableListOf())
            token = token.copy(text = token.text.removePrefix("("))
        }

        // Strip closing ")"
        if (token.text.trim().endsWith(")")) {
            token = token.copy(text = token.text.trim().removeSuffix(")"))
            if (result.isEmpty()) result.add(mutableListOf())
            if (token.text.isNotEmpty()) result.last().add(token)
        } else {
            if (result.isEmpty()) result.add(mutableListOf())
            if (token.text.isNotEmpty()) result.last().add(token)
        }
    }

    return result
}

fun mmssToMilliseconds(mmss: String): Long {
    val parts = mmss.split(":", ".")
    if (parts.size == 3) {
        try {
            val minutes = parts[0].toLong()
            val seconds = parts[1].toLong()
            val ms = parts[2].substring(0,2).toLong()
            return (minutes * 60 + seconds) * 1000 + ms * 10
        } catch (e: NumberFormatException) {
            e.printStackTrace()
        }
    }
    return 0L
}

fun getTimeStamps(input: String): List<String> {
    val regex = Regex("\\[(.*?)]")
    val matches = regex.findAll(input)

    val result = mutableListOf<String>()
    for (match in matches) {
        result.add(match.groupValues[1])
    }

    return result
}