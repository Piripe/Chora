package com.craftworks.music.utils

import com.craftworks.music.data.model.Lyric
import com.craftworks.music.data.model.Lyrics
import com.craftworks.music.data.model.LyricsLine
import com.craftworks.music.data.model.LyricsRole
import com.craftworks.music.data.model.SyncType
import com.craftworks.music.data.model.SyncedWord
import com.gitlab.mvysny.konsumexml.Konsumer
import com.gitlab.mvysny.konsumexml.Whitespace
import com.gitlab.mvysny.konsumexml.konsumeXml

private const val iTunesNs = "http://music.apple.com/lyric-ttml-internal"
private const val ttmNs = "http://www.w3.org/ns/ttml#metadata"

private class Group(val role: LyricsRole) {
    val words = mutableListOf<SyncedWord>()
    val text = StringBuilder()
}

fun parseTtml(ttml: String): Lyrics {
    return ttml.konsumeXml().use { k ->
        var syncType = SyncType.NONE
        val lines = mutableListOf<LyricsLine>()

        k.child("tt") {
            syncType = when (attributes.getValueOrNull("timing", iTunesNs)) {
                "Word" -> SyncType.WORD
                "Line" -> SyncType.LINE
                else -> SyncType.NONE
            }
            child("head") { skipContents() }
            child("body") {
                children("div") {
                    children("p") { lines.add(parseLines(this)) }
                }
            }
        }

        Lyrics(syncType = syncType, lines = lines)
    }
}

private fun parseLines(k: Konsumer): LyricsLine {
    val lineStart = mmssToMilliseconds(k.attributes.getValueOrNull("begin"))
    val lineEnd = mmssToMilliseconds(k.attributes.getValueOrNull("end"))

    val groups = mutableListOf<Group>()
    val plainText = StringBuilder()
    parseWords(k, LyricsRole.MAIN, groups, plainText)

    val lyrics = if (groups.isEmpty()) {
        listOf(Lyric(text = plainText.toString().trim(), startMs = lineStart, endMs = lineEnd))
    } else {
        groups.map { g ->
            Lyric(
                text = g.text.toString(),
                words = g.words,
                startMs = g.words.first().startMs,
                endMs = g.words.last().endMs,
                role = g.role
            )
        }
    }

    return LyricsLine(startMs = lineStart!!, endMs = lineEnd, lines = lyrics)
}

private fun parseWords(
    k: Konsumer,
    inheritedRole: LyricsRole,
    groups: MutableList<Group>,
    plainTextOut: StringBuilder? = null
) {
    while (!k.isFinished) {
        val gap = k.text(Whitespace.preserve, failOnElement = false)
        plainTextOut?.append(gap)
        val hasSeparator = gap.isNotEmpty()

        if (k.isFinished) break

        k.child("span") {
            val role = if (attributes.getValueOrNull("role", ttmNs) == "x-bg") LyricsRole.BG else inheritedRole

            val start = mmssToMilliseconds(attributes.getValueOrNull("begin"))
            val end = mmssToMilliseconds(attributes.getValueOrNull("end"))

            val leadingText = text(Whitespace.preserve, failOnElement = false)

            if (isFinished) {
                if (groups.isEmpty() || groups.last().role != role) {
                    groups.add(Group(role))
                } else if (hasSeparator) {
                    val g = groups.last()
                    val prev = g.words.removeAt(g.words.lastIndex)
                    g.words.add(prev.copy(text = prev.text + " "))
                }

                val cleanedText = if (role == LyricsRole.BG) {
                    leadingText.replace(Regex("[()]"), "")
                } else {
                    leadingText
                }

                groups.last().words.add(SyncedWord(cleanedText, start!!, end))
                groups.last().text.append(cleanedText)
            } else {
                // bg lines wrapper
                parseWords(this, role, groups)
            }
        }
    }
}