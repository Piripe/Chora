package com.craftworks.music.managers.settings

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.craftworks.music.R
import com.craftworks.music.data.BottomNavItem
import com.craftworks.music.data.model.Screen
import com.craftworks.music.dataStore
import com.craftworks.music.ui.elements.ActionButton
import com.craftworks.music.ui.elements.ActionButtonType
import com.craftworks.music.ui.playing.NowPlayingAlignment
import com.craftworks.music.ui.playing.NowPlayingBackground
import com.craftworks.music.ui.playing.NowPlayingAction
import com.craftworks.music.ui.playing.NowPlayingButton
import com.craftworks.music.ui.playing.NowPlayingGesture
import com.craftworks.music.ui.playing.NowPlayingMenuItem
import com.craftworks.music.ui.playing.NowPlayingSeekBarStyle
import com.craftworks.music.ui.playing.defaultNowPlayingButtons
import com.craftworks.music.ui.playing.defaultNowPlayingCoverGestures
import com.craftworks.music.ui.playing.defaultNowPlayingMenuItems
import com.craftworks.music.ui.playing.withAllMenuActions
import com.craftworks.music.ui.screens.HomeItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

enum class OLEDProtectionMode {
    OFF, LYRICS_ONLY, MINIMAL,
}
enum class AppTheme {
    LIGHT, DARK, SYSTEM
}

@Singleton
class AppearanceSettingsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val USERNAME_KEY = stringPreferencesKey("username")
        private val NP_BACKGROUND_KEY = stringPreferencesKey("np_background_type")
        private val QUEUE_ADVANCED = booleanPreferencesKey("queue_advanced")
        private val FILTER_ADVANCED = booleanPreferencesKey("filter_advanced")
        private val NP_TITLE_ALIGNMENT = stringPreferencesKey("np_title_alignment")
        private val NP_LYRICS_ALIGNMENT = stringPreferencesKey("np_lyrics_alignment")
        private val SHOW_NAVIDROME_KEY = booleanPreferencesKey("show_navidrome_logo")
        private val SHOW_MORE_INFO_KEY = booleanPreferencesKey("show_more_info")
        private val NOW_PLAYING_LYRIC_BLUR_KEY = booleanPreferencesKey("now_playing_lyrics_blur")
        private val BOTTOM_NAV_ITEMS_KEY = stringPreferencesKey("bottom_nav_order")
        private val HOME_ITEMS_KEY = stringPreferencesKey("home_items_order")
        private val APP_THEME = stringPreferencesKey("theme")

        private val LYRICS_ANIMATION_SPEED = intPreferencesKey("lyrics_animation_speed")
        private val LYRICS_AUTOSCROLL = booleanPreferencesKey("lyrics_auto_scroll")
        private val LYRICS_RECENTER_AFTER_SCROLL = booleanPreferencesKey("lyrics_recenter_after_Scroll")
        private val LYRICS_WORD_BOUNCE = booleanPreferencesKey("lyrics_word_bounce")
        private val USE_REFRESH_ANIMATION = booleanPreferencesKey("use_refresh_animation")
        private val SHOW_TRACK_NUMBERS = booleanPreferencesKey("show_track_numbers")
        private val DETAILS_DYNAMIC_COLORS = booleanPreferencesKey("details_dynamic_colors")

        private val OLED_PROTECTION_MODE = stringPreferencesKey("oled_protection")
        private val DISABLE_SCREEN_STANDBY = booleanPreferencesKey("disable_screen_standby")
        private val ALBUM_DETAILS_BUTTONS = stringPreferencesKey("album_details_buttons")
        private val ARTIST_DETAILS_BUTTONS = stringPreferencesKey("artist_details_buttons")
        private val PLAYLIST_DETAILS_BUTTONS = stringPreferencesKey("playlist_details_buttons")

        private val NP_BUTTONS = stringPreferencesKey("np_buttons")
        private val NP_MENU_ACTIONS = stringPreferencesKey("np_menu_actions")
        private val NP_SEEK_BAR_STYLE = stringPreferencesKey("np_seek_bar_style")
        private val NP_COVER_GESTURES = stringPreferencesKey("np_cover_gestures")

        // Settings that can't be read (e.g. written by a newer version) fall back to the defaults.
        private val lenientJson = Json { ignoreUnknownKeys = true }
    }

    val usernameFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[USERNAME_KEY] ?: "Username"
    }

    suspend fun setUsername(username: String) {
        withContext(NonCancellable) {
            context.dataStore.edit { preferences ->
                preferences[USERNAME_KEY] = username
            }
        }
    }

    val npBackgroundFlow: Flow<NowPlayingBackground> = context.dataStore.data.map { preferences ->
        try {
            NowPlayingBackground.valueOf(
                preferences[NP_BACKGROUND_KEY]
                    ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                        NowPlayingBackground.ANIMATED_BLUR.name
                    else
                        NowPlayingBackground.STATIC_BLUR.name
            )
        } catch (e: Exception) {
            println(e.message)
            NowPlayingBackground.STATIC_BLUR
        }
    }

    suspend fun setBackgroundType(backgroundType: NowPlayingBackground) {
        withContext(NonCancellable) {
            context.dataStore.edit { preferences ->
                preferences[NP_BACKGROUND_KEY] = backgroundType.name
            }
        }
    }

    val showMoreInfoFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SHOW_MORE_INFO_KEY] ?: true
    }

    suspend fun setShowMoreInfo(showMoreInfo: Boolean) {
        withContext(NonCancellable) {
            context.dataStore.edit { preferences ->
                preferences[SHOW_MORE_INFO_KEY] = showMoreInfo
            }
        }
    }

    val nowPlayingLyricsBlurFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[NOW_PLAYING_LYRIC_BLUR_KEY]
            ?: true
    }

    suspend fun setNowPlayingLyricsBlur(blur: Boolean) {
        withContext(NonCancellable) {
            context.dataStore.edit { preferences ->
                preferences[NOW_PLAYING_LYRIC_BLUR_KEY] = blur
            }
        }
    }

    val showNavidromeLogoFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SHOW_NAVIDROME_KEY] ?: true
    }

    suspend fun setShowNavidromeLogo(showNavidromeLogo: Boolean) {
        withContext(NonCancellable) {
            context.dataStore.edit { preferences ->
                preferences[SHOW_NAVIDROME_KEY] = showNavidromeLogo
            }
        }
    }

    val homeItemsItemsFlow: Flow<List<HomeItem>> = context.dataStore.data.map { preferences ->
        val jsonString = preferences[HOME_ITEMS_KEY]
        val defaultValue = listOf(
            HomeItem("recently_played", true),
            HomeItem("recently_added", true),
            HomeItem("most_played", true),
            HomeItem("random_songs", true)
        )
        try {
            jsonString?.let { Json.decodeFromString<List<HomeItem>>(it) } ?: defaultValue
        } catch (e: Exception) {
            println(e.message)
            defaultValue
        }
    }

    suspend fun setHomeItems(items: List<HomeItem>) {
        withContext(NonCancellable) {
            context.dataStore.edit { preferences ->
                preferences[HOME_ITEMS_KEY] = Json.encodeToString(items)
            }
        }
    }

    val bottomNavItemsFlow: Flow<List<BottomNavItem>> = context.dataStore.data.map { preferences ->
        val jsonString = preferences[BOTTOM_NAV_ITEMS_KEY]
        val defaultValue = listOf(
            BottomNavItem(context.getString(R.string.nav_home), R.drawable.rounded_home_24, Screen.Home),
            BottomNavItem(context.getString(R.string.nav_albums), R.drawable.rounded_library_music_24, Screen.Albums),
            BottomNavItem(context.getString(R.string.nav_songs), R.drawable.round_music_note_24, Screen.Songs),
            BottomNavItem(context.getString(R.string.nav_artists), R.drawable.rounded_artist_24, Screen.Artists),
            BottomNavItem(context.getString(R.string.nav_radios), R.drawable.rounded_radio, Screen.Radios),
            BottomNavItem(context.getString(R.string.nav_playlists), R.drawable.placeholder, Screen.Playlists)
        )
        try {
            // Only the order and enabled state are meaningful in the stored items: resource ids
            // change between builds, so the icon and title always come from the defaults.
            val defaultsByScreen = defaultValue.associateBy { it.screenRoute }
            jsonString?.let { Json.decodeFromString<List<BottomNavItem>>(it) }
                ?.map { item ->
                    defaultsByScreen[item.screenRoute]?.let {
                        item.copy(title = it.title, icon = it.icon)
                    } ?: item
                }
                ?: defaultValue
        } catch (e: Exception) {
            println(e.message)
            defaultValue
        }
    }

    suspend fun setBottomNavItems(items: List<BottomNavItem>) {
        withContext(NonCancellable) {
            context.dataStore.edit { preferences ->
                preferences[BOTTOM_NAV_ITEMS_KEY] = Json.encodeToString(items)
            }
        }
    }

    val appTheme: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[APP_THEME] ?: "SYSTEM"
    }

    suspend fun setAppTheme(theme: AppTheme) {
        withContext(NonCancellable) {
            context.dataStore.edit { preferences ->
                preferences[APP_THEME] = theme.name
            }
        }
    }

    val lyricsAnimationSpeedFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[LYRICS_ANIMATION_SPEED] ?: 660
    }

    suspend fun setLyricsAnimationSpeed(speed: Int) {
        withContext(NonCancellable) {
            context.dataStore.edit { preferences ->
                preferences[LYRICS_ANIMATION_SPEED] = speed
            }
        }
    }

    val refreshAnimationFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[USE_REFRESH_ANIMATION]
            ?: (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
    }

    suspend fun setUseRefreshAnimation(useRefreshAnimation: Boolean) {
        withContext(NonCancellable) {
            context.dataStore.edit { preferences ->
                preferences[USE_REFRESH_ANIMATION] = useRefreshAnimation
            }
        }
    }

    val showTrackNumbersFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SHOW_TRACK_NUMBERS] ?: true
    }

    suspend fun setShowTrackNumbers(showTrackNumbers: Boolean) {
        withContext(NonCancellable) {
            context.dataStore.edit { preferences ->
                preferences[SHOW_TRACK_NUMBERS] = showTrackNumbers
            }
        }
    }

    val detailsDynamicColorsFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[DETAILS_DYNAMIC_COLORS] ?: false
    }

    suspend fun setDetailsDynamicColors(enabled: Boolean) {
        withContext(NonCancellable) {
            context.dataStore.edit { preferences ->
                preferences[DETAILS_DYNAMIC_COLORS] = enabled
            }
        }
    }

    val useAdvancedQueue: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[QUEUE_ADVANCED] ?: false
    }

    suspend fun setUseAdvancedQueue(useAdvancedQueue: Boolean) {
        withContext(NonCancellable) {
            context.dataStore.edit { preferences ->
                preferences[QUEUE_ADVANCED] = useAdvancedQueue
            }
        }
    }

    val useAdvancedFilter: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[FILTER_ADVANCED] ?: false
    }

    suspend fun setUseAdvancedFilter(useAdvancedFilter: Boolean) {
        withContext(NonCancellable) {
            context.dataStore.edit { preferences ->
                preferences[FILTER_ADVANCED] = useAdvancedFilter
            }
        }
    }

    val nowPlayingTitleAlignment: Flow<NowPlayingAlignment> =
        context.dataStore.data.map { preferences ->
            NowPlayingAlignment.valueOf(
                preferences[NP_TITLE_ALIGNMENT] ?: NowPlayingAlignment.LEFT.name
            )
        }

    suspend fun setNowPlayingTitleAlignment(nowPlayingTitleAlignment: NowPlayingAlignment) {
        withContext(NonCancellable) {
            context.dataStore.edit { preferences ->
                preferences[NP_TITLE_ALIGNMENT] = nowPlayingTitleAlignment.name
            }
        }
    }

    val nowPlayingLyricsAlignment: Flow<NowPlayingAlignment> =
        context.dataStore.data.map { preferences ->
            NowPlayingAlignment.valueOf(
                preferences[NP_LYRICS_ALIGNMENT] ?: NowPlayingAlignment.CENTER.name
            )
        }

    suspend fun setNowPlayingLyricsAlignment(nowPlayingLyricsAlignment: NowPlayingAlignment) {
        withContext(NonCancellable) {
            context.dataStore.edit { preferences ->
                preferences[NP_LYRICS_ALIGNMENT] = nowPlayingLyricsAlignment.name
            }
        }
    }

    val lyricsAutoScroll: Flow<Boolean> =
        context.dataStore.data.map { preferences ->
            preferences[LYRICS_AUTOSCROLL] ?: true
        }

    suspend fun setLyricsAutoScroll(autoScroll: Boolean) {
        withContext(NonCancellable) {
            context.dataStore.edit { preferences ->
                preferences[LYRICS_AUTOSCROLL] = autoScroll
            }
        }
    }


    val lyricsRecenterAfterScroll: Flow<Boolean> =
        context.dataStore.data.map { preferences ->
            preferences[LYRICS_RECENTER_AFTER_SCROLL] ?: true
        }

    suspend fun setLyricsRecenterAfterScroll(recenterAfterScroll: Boolean) {
        withContext(NonCancellable) {
            context.dataStore.edit { preferences ->
                preferences[LYRICS_RECENTER_AFTER_SCROLL] = recenterAfterScroll
            }
        }
    }

    val lyricsBounce: Flow<Boolean> =
        context.dataStore.data.map { preferences ->
            preferences[LYRICS_WORD_BOUNCE] ?: try {
                Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE) == 0f
            } catch (e: Exception) {
                true
            }
        }

    suspend fun setLyricsBounce(bounce: Boolean) {
        withContext(NonCancellable) {
            context.dataStore.edit { preferences ->
                preferences[LYRICS_WORD_BOUNCE] = bounce
            }
        }
    }

    val oledProtectionMode: Flow<OLEDProtectionMode> = context.dataStore.data.map { preferences ->
        try {
            OLEDProtectionMode.valueOf(preferences[OLED_PROTECTION_MODE] ?: "OFF")
        }
        catch (ex: Exception) {
            OLEDProtectionMode.OFF
        }
    }

    suspend fun setOledProtectionMode(mode: OLEDProtectionMode) {
        withContext(NonCancellable) {
            context.dataStore.edit { preferences ->
                preferences[OLED_PROTECTION_MODE] = mode.name
            }
        }
    }

    val disableScreenStandby: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[DISABLE_SCREEN_STANDBY] ?: false
    }

    suspend fun setDisableScreenStandby(enabled: Boolean) {
        withContext(NonCancellable) {
            context.dataStore.edit { preferences ->
                preferences[DISABLE_SCREEN_STANDBY] = enabled
            }
        }
    }

    val albumDetailsButtons: Flow<List<ActionButton>> = context.dataStore.data.map { preferences ->
        preferences[ALBUM_DETAILS_BUTTONS]?.let { Json.decodeFromString(it) } ?: listOf(
            ActionButton(ActionButtonType.FAVORITE,false),
            ActionButton(ActionButtonType.SHUFFLE,false),
            ActionButton(ActionButtonType.ADD_TO_QUEUE,true),
            ActionButton(ActionButtonType.PLAY_NEXT,true),
            ActionButton(ActionButtonType.SEPARATOR,true),
            ActionButton(ActionButtonType.FAVORITE,true),
            ActionButton(ActionButtonType.ADD_TO_PLAYLIST,true),
            ActionButton(ActionButtonType.DOWNLOAD,true),
            ActionButton(ActionButtonType.SHARE,true)
        )
    }

    suspend fun setAlbumDetailsButtons(buttons: List<ActionButton>) {
        withContext(NonCancellable) {
            context.dataStore.edit { preferences ->
                preferences[ALBUM_DETAILS_BUTTONS] = Json.encodeToString(buttons)
            }
        }
    }

    val artistDetailsButtons: Flow<List<ActionButton>> = context.dataStore.data.map { preferences ->
        preferences[ARTIST_DETAILS_BUTTONS]?.let { Json.decodeFromString(it) } ?: listOf(
            ActionButton(ActionButtonType.FAVORITE,false),
            ActionButton(ActionButtonType.SHUFFLE,false),
            ActionButton(ActionButtonType.ADD_TO_QUEUE,true),
            ActionButton(ActionButtonType.PLAY_NEXT,true),
            ActionButton(ActionButtonType.SEPARATOR,true),
            ActionButton(ActionButtonType.FAVORITE,true),
            ActionButton(ActionButtonType.ADD_TO_PLAYLIST,true),
            ActionButton(ActionButtonType.DOWNLOAD,true),
            ActionButton(ActionButtonType.SHARE,true)
        )
    }

    suspend fun setArtistDetailsButtons(buttons: List<ActionButton>) {
        withContext(NonCancellable) {
            context.dataStore.edit { preferences ->
                preferences[ARTIST_DETAILS_BUTTONS] = Json.encodeToString(buttons)
            }
        }
    }

    val playlistDetailsButtons: Flow<List<ActionButton>> = context.dataStore.data.map { preferences ->
        preferences[PLAYLIST_DETAILS_BUTTONS]?.let { Json.decodeFromString(it) } ?: listOf(
            ActionButton(ActionButtonType.ADD_TO_QUEUE,false),
            ActionButton(ActionButtonType.SHUFFLE,false),
            ActionButton(ActionButtonType.ADD_TO_QUEUE,true),
            ActionButton(ActionButtonType.PLAY_NEXT,true),
            ActionButton(ActionButtonType.SEPARATOR,true),
            ActionButton(ActionButtonType.DOWNLOAD,true),
            ActionButton(ActionButtonType.SHARE,true)
        )
    }

    suspend fun setPlaylistDetailsButtons(buttons: List<ActionButton>) {
        withContext(NonCancellable) {
            context.dataStore.edit { preferences ->
                preferences[PLAYLIST_DETAILS_BUTTONS] = Json.encodeToString(buttons)
            }
        }
    }

    val nowPlayingButtons: Flow<List<NowPlayingButton>> = context.dataStore.data.map { preferences ->
        preferences[NP_BUTTONS]?.let {
            try {
                lenientJson.decodeFromString<List<NowPlayingButton>>(it)
            } catch (e: Exception) {
                println(e.message)
                null
            }
        } ?: defaultNowPlayingButtons
    }

    suspend fun setNowPlayingButtons(buttons: List<NowPlayingButton>) {
        withContext(NonCancellable) {
            context.dataStore.edit { preferences ->
                preferences[NP_BUTTONS] = Json.encodeToString(buttons)
            }
        }
    }

    val nowPlayingMenuItems: Flow<List<NowPlayingMenuItem>> = context.dataStore.data.map { preferences ->
        (preferences[NP_MENU_ACTIONS]?.let {
            try {
                lenientJson.decodeFromString<List<NowPlayingMenuItem>>(it)
            } catch (e: Exception) {
                println(e.message)
                null
            }
        } ?: defaultNowPlayingMenuItems).withAllMenuActions()
    }

    suspend fun setNowPlayingMenuItems(items: List<NowPlayingMenuItem>) {
        withContext(NonCancellable) {
            context.dataStore.edit { preferences ->
                preferences[NP_MENU_ACTIONS] = Json.encodeToString(items)
            }
        }
    }

    val nowPlayingSeekBarStyle: Flow<NowPlayingSeekBarStyle> = context.dataStore.data.map { preferences ->
        try {
            NowPlayingSeekBarStyle.valueOf(preferences[NP_SEEK_BAR_STYLE] ?: NowPlayingSeekBarStyle.STANDARD.name)
        } catch (e: Exception) {
            NowPlayingSeekBarStyle.STANDARD
        }
    }

    suspend fun setNowPlayingSeekBarStyle(style: NowPlayingSeekBarStyle) {
        withContext(NonCancellable) {
            context.dataStore.edit { preferences ->
                preferences[NP_SEEK_BAR_STYLE] = style.name
            }
        }
    }

    /** The action of each cover gesture. A gesture without action isn't in the map. */
    val nowPlayingCoverGestures: Flow<Map<NowPlayingGesture, NowPlayingAction>> = context.dataStore.data.map { preferences ->
        preferences[NP_COVER_GESTURES]?.let {
            try {
                lenientJson.decodeFromString<Map<NowPlayingGesture, NowPlayingAction>>(it)
            } catch (e: Exception) {
                println(e.message)
                null
            }
        } ?: defaultNowPlayingCoverGestures
    }

    suspend fun setNowPlayingCoverGestures(gestures: Map<NowPlayingGesture, NowPlayingAction>) {
        withContext(NonCancellable) {
            context.dataStore.edit { preferences ->
                preferences[NP_COVER_GESTURES] = Json.encodeToString(gestures)
            }
        }
    }
}
