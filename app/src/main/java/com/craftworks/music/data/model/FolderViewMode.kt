package com.craftworks.music.data.model

/** How the folder view shows the content of a folder. */
enum class FolderViewMode {
    /** One row per folder or song. */
    LIST,
    /** Like [LIST], with smaller rows and covers. */
    COMPACT,
    /** Folders as tiles, then the songs as rows. */
    GRID,
    /** Folders unfold in place, showing their content below them. */
    TREE
}
