package com.craftworks.music.data.model

/** Where the songs the user downloads are stored. */
enum class DownloadTarget {
    /** In the app, to play them offline. See [com.craftworks.music.managers.SmartCacheManager.download]. */
    APP,
    /** As files in the device's music folder, named after the download templates. */
    DEVICE
}
