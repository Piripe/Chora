package com.craftworks.music.utils

/**
 * One page of a list filtered on the client.
 * [nextStartIndex] is the provider offset to continue from, it is not the number of returned items.
 */
data class FilteredPage<T>(
    val items: List<T>,
    val nextStartIndex: Int,
    val endReached: Boolean
) {
    fun <R> map(transform: (T) -> R) = FilteredPage(items.map(transform), nextStartIndex, endReached)
}

object FilteredPaging {
    private const val SCAN_PAGE_SIZE = 500
    // Stops scanning a huge library when almost nothing matches. The next scroll continues from there.
    private const val MAX_SCANNED_PAGES = 40

    /**
     * Reads pages from the provider, starting at [startIndex], until at least [count] items match
     * or the end of the list is reached.
     */
    suspend fun <T> load(
        startIndex: Int,
        count: Int,
        fetch: suspend (startIndex: Int, limit: Int) -> List<T>,
        id: (T) -> String,
        matches: (T) -> Boolean
    ): FilteredPage<T> {
        val result = mutableListOf<T>()
        val seen = HashSet<String>()
        var cursor = startIndex

        repeat(MAX_SCANNED_PAGES) {
            val page = fetch(cursor, SCAN_PAGE_SIZE)
            if (page.isEmpty()) return FilteredPage(result, cursor, true)
            cursor += page.size

            // Some providers ignore the offset and always return the whole list.
            val newItems = page.filter { seen.add(id(it)) }
            if (newItems.isEmpty()) return FilteredPage(result, cursor, true)

            result += newItems.filter(matches)
            if (page.size < SCAN_PAGE_SIZE) return FilteredPage(result, cursor, true)
            if (result.size >= count) return FilteredPage(result, cursor, false)
        }
        return FilteredPage(result, cursor, false)
    }
}
