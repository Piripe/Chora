package com.craftworks.music.data.model

enum class ProviderFeature {
    REPORT_PLAYBACK, FAVORITES, DOWNLOADS, SELECT_MULTIPLE_MUSIC_FOLDERS, OFFLINE_PLAYBACK, INTERNET_RADIO, PLAYLISTS, RATINGS,
    /** The list queries can be filtered by artist and genre IDs (artistIds, genreIds) on the server. */
    FILTER_BY_ID,
    /** Public links to songs, albums, playlists and artists can be created, see [com.craftworks.music.data.providers.media.MediaProvider.shareItem]. */
    SHARING
}