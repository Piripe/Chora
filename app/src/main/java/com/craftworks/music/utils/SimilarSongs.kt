package com.craftworks.music.utils

import com.craftworks.music.data.model.MediaModel
import kotlin.math.abs

/**
 * Finds similar songs without a server: used by the local provider and for the mixes made offline
 * from the library cache.
 */
object SimilarSongs {
    /**
     * The songs of [library] sharing the most with [seeds] (artists, genres, release years), at most
     * [count], in a random order and without the seeds themselves.
     */
    fun pick(library: List<MediaModel.Song>, seeds: List<MediaModel.Song>, count: Int): List<MediaModel.Song> {
        if (seeds.isEmpty()) return emptyList()
        val seedIds = seeds.map { it.id }.toSet()
        val artists = seeds.flatMap { it.artistNames() }.toSet()
        val genres = seeds.flatMap { song -> song.genres.map { it.name.lowercase() } }.filter { it.isNotBlank() }.toSet()
        val years = seeds.mapNotNull { song -> song.releaseYear?.takeIf { it > 0 } }.toSet()

        return library
            .filter { it.id !in seedIds }
            .map { song ->
                var score = 0
                if (song.artistNames().any { it in artists }) score += 3
                score += 2 * song.genres.count { it.name.lowercase() in genres }
                val year = song.releaseYear
                if (year != null && years.any { abs(it - year) <= 5 }) score += 1
                song to score
            }
            .filter { it.second > 0 }
            // Shuffled first so the songs with the same score are picked at random.
            .shuffled()
            .sortedByDescending { it.second }
            .take(count)
            .map { it.first }
            .shuffled()
    }

    private fun MediaModel.Song.artistNames(): List<String> =
        ((artists + albumArtists).map { it.name } + artistName)
            .map { it.lowercase() }
            .filter { it.isNotBlank() }
}
