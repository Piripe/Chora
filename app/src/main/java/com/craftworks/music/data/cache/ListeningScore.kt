package com.craftworks.music.data.cache

import java.util.concurrent.TimeUnit
import kotlin.math.pow

/**
 * How much a song, album or artist was listened to recently: each play counts for less as it gets
 * older, so something played a lot long ago ends up below something played a few times this month.
 */
object ListeningScore {
    /** A play counts half as much after this time. */
    val HALF_LIFE_MS: Long = TimeUnit.DAYS.toMillis(30)

    /** Below this, it wasn't really listened to lately (less than one play in the last half-life). */
    const val RECENT_MIN_SCORE = 0.5

    /** [score] as it was [elapsedMs] ago, worn down to now. */
    fun decay(score: Double, elapsedMs: Long): Double =
        score * 0.5.pow(elapsedMs.coerceAtLeast(0).toDouble() / HALF_LIFE_MS)

    /**
     * Estimates the score from what providers return, a play count and the date of the last play.
     * Every play is counted at the last play's date, as providers don't return the older ones.
     * An unknown date counts as one half-life ago.
     */
    fun of(playCount: Number?, lastPlayedAt: Long?, now: Long): Double {
        val count = playCount?.toDouble()?.takeIf { it > 0 } ?: return 0.0
        val elapsed = lastPlayedAt?.let { now - it } ?: HALF_LIFE_MS
        return decay(count, elapsed)
    }
}
