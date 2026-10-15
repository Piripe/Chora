package com.craftworks.music.data.model

import androidx.annotation.StringRes
import com.craftworks.music.R
import java.util.concurrent.TimeUnit

/** When a shared link stops working. */
enum class ShareExpiration(@StringRes val label: Int, private val days: Long?) {
    NEVER(R.string.share_expires_never, null),
    DAY(R.string.share_expires_day, 1),
    WEEK(R.string.share_expires_week, 7),
    MONTH(R.string.share_expires_month, 30),
    YEAR(R.string.share_expires_year, 365);

    /** The expiration date in ms since the epoch, null for never. */
    fun expiresAt(now: Long = System.currentTimeMillis()): Long? =
        days?.let { now + TimeUnit.DAYS.toMillis(it) }
}
