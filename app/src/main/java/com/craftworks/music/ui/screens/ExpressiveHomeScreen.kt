package com.craftworks.music.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.ripple
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.craftworks.music.R
import com.craftworks.music.data.model.Screen
import com.craftworks.music.data.model.artists
import com.craftworks.music.data.model.id
import com.craftworks.music.data.providers.media.MediaProvider
import com.craftworks.music.data.providers.media.local.LocalMediaProvider
import com.craftworks.music.managers.MediaProviderManager
import com.craftworks.music.managers.settings.AppearanceSettingsManager
import com.craftworks.music.player.SongHelper
import com.craftworks.music.ui.elements.ActionTooltip
import com.craftworks.music.ui.elements.AlbumCardMenu
import com.craftworks.music.ui.elements.RandomShapeRefreshIndicator
import com.craftworks.music.ui.elements.RippleEffect
import com.craftworks.music.ui.playing.dpToPx
import com.craftworks.music.ui.viewmodels.HomeScreenViewModel
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.roundToInt

/** Shapes the covers of the "Explore" section cycle through. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val exploreShapes = listOf(
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Clover4Leaf,
    MaterialShapes.Arch,
    MaterialShapes.Cookie6Sided,
    MaterialShapes.Pentagon,
    MaterialShapes.SoftBurst,
    MaterialShapes.Gem,
    MaterialShapes.Square,
)

/**
 * The Material 3 Expressive version of the Home screen, see [AppearanceSettingsManager.expressiveHomeFlow].
 * Each section has its own layout: a carousel for recently played albums, ranked cards for the
 * most played ones and shaped covers to explore the library.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveHomeScreen(
    navHostController: NavHostController,
    mediaController: MediaController?,
    viewModel: HomeScreenViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val settingsManager = remember(context) { AppearanceSettingsManager(context) }
    val coroutineScope = rememberCoroutineScope()

    val recentlyPlayedAlbums by viewModel.recentlyPlayedAlbums.collectAsStateWithLifecycle()
    val recentAlbums by viewModel.recentAlbums.collectAsStateWithLifecycle()
    val mostPlayedAlbums by viewModel.mostPlayedAlbums.collectAsStateWithLifecycle()
    val shuffledAlbums by viewModel.shuffledAlbums.collectAsStateWithLifecycle()
    val songsPlayedThisWeek by viewModel.songsPlayedThisWeek.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    val currentProvider by MediaProviderManager.currentProvider.collectAsStateWithLifecycle()
    val username by settingsManager.usernameFlow.collectAsState("Username")
    val providerLogoEnabled by settingsManager.showNavidromeLogoFlow.collectAsStateWithLifecycle(true)
    val orderedHomeItems by settingsManager.homeItemsItemsFlow.collectAsState(initial = emptyList())

    val pullState = rememberPullToRefreshState()
    var showRipple by remember { mutableIntStateOf(0) }
    val rippleXOffset = LocalWindowInfo.current.containerSize.width / 2
    val rippleYOffset = dpToPx(WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value.roundToInt())

    val openAlbum: (MediaItem) -> Unit = { album ->
        navHostController.navigate(Screen.AlbumDetails(album.mediaMetadata.id ?: "", album.mediaMetadata.artworkUri.toString())) {
            launchSingleTop = true
        }
    }
    val playAlbum: (MediaItem) -> Unit = { album ->
        coroutineScope.launch {
            val mediaItems = viewModel.getAlbumSongs(album.mediaMetadata.id ?: "")
            if (mediaItems.isNotEmpty())
                SongHelper.play(mediaItems.drop(1), 0, mediaController)
        }
    }

    PullToRefreshBox(
        state = pullState,
        isRefreshing = isLoading,
        onRefresh = {
            viewModel.loadHomeScreenData()
            showRipple++
        },
        indicator = {
            RandomShapeRefreshIndicator(
                state = pullState,
                isRefreshing = isLoading,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            ExpressiveHomeHeader(
                username = username,
                provider = currentProvider?.takeIf { providerLogoEnabled },
                onOpenSettings = {
                    navHostController.navigate(Screen.Settings) {
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )

            LibraryChips(
                modifier = Modifier.padding(horizontal = 16.dp),
                onChanged = { showRipple++ }
            )

            QuickActions(
                onShuffle = {
                    val songs = viewModel.getShuffledSongs()
                    SongHelper.play(songs, 0, mediaController)
                },
                onSurprise = {
                    (shuffledAlbums.ifEmpty { recentAlbums }).randomOrNull()?.let(playAlbum)
                }
            )

            orderedHomeItems.filter { it.enabled }.forEach { item ->
                val seeAll = {
                    navHostController.navigate(Screen.HomeLists(item.key)) {
                        launchSingleTop = true
                        restoreState = true
                    }
                }
                when (item.key) {
                    "recently_played" -> {
                        ExpressiveSectionHeader(R.string.home_recently_played, item.key, viewModel, seeAll)
                        HeroAlbumCarousel(recentlyPlayedAlbums, openAlbum, playAlbum)
                    }
                    "recently_added" -> {
                        ExpressiveSectionHeader(R.string.home_recently_added, item.key, viewModel, seeAll)
                        AlbumCardRow(recentAlbums) { _, album ->
                            ExpressiveAlbumCard(
                                album = album,
                                shape = MaterialTheme.shapes.extraLarge,
                                onClick = { openAlbum(album) },
                                onPlay = { playAlbum(album) },
                                badge = album.mediaMetadata.recordingYear?.toString()
                            )
                        }
                    }
                    "most_played" -> {
                        ExpressiveSectionHeader(R.string.home_most_played, item.key, viewModel, seeAll)
                        AlbumCardRow(mostPlayedAlbums) { rank, album ->
                            RankedAlbumCard(rank + 1, album, { openAlbum(album) }, { playAlbum(album) })
                        }
                    }
                    "random_songs" -> {
                        ExpressiveSectionHeader(R.string.home_explore_library, item.key, viewModel, seeAll)
                        AlbumCardRow(shuffledAlbums) { i, album ->
                            ExpressiveAlbumCard(
                                album = album,
                                shape = exploreShapes[(i + album.mediaId.hashCode()).mod(exploreShapes.size)].toShape(),
                                onClick = { openAlbum(album) },
                                onPlay = { playAlbum(album) },
                                centered = true
                            )
                        }
                    }
                }
            }

            ListeningStatsCard(mostPlayedAlbums, songsPlayedThisWeek, navHostController, openAlbum)
        }
    }

    RippleEffect(
        center = Offset(rippleXOffset.toFloat(), rippleYOffset.toFloat()),
        color = MaterialTheme.colorScheme.surfaceVariant,
        key = showRipple
    )
}

//region Header

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveHomeHeader(
    username: String,
    provider: MediaProvider?,
    onOpenSettings: () -> Unit
) {
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> R.string.home_greeting_morning
            in 12..17 -> R.string.home_greeting_afternoon
            in 18..22 -> R.string.home_greeting_evening
            else -> R.string.home_greeting_night
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding() + 8.dp)
            .padding(start = 16.dp, end = 12.dp, bottom = 12.dp)
    ) {
        if (provider != null && provider !is LocalMediaProvider) {
            ProviderLogo(provider)
            Spacer(Modifier.width(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(greeting),
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = username,
                style = MaterialTheme.typography.displaySmallEmphasized,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        ActionTooltip(stringResource(R.string.action_open_settings)) {
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    ImageVector.vectorResource(R.drawable.rounded_settings_24),
                    contentDescription = "Settings",
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

//endregion

//region Quick actions

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QuickActions(
    onShuffle: suspend () -> Unit,
    onSurprise: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var isShuffling by remember { mutableStateOf(false) }
    val height = ButtonDefaults.MediumContainerHeight

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Button(
            onClick = {
                if (isShuffling) return@Button
                isShuffling = true
                coroutineScope.launch {
                    try { onShuffle() } finally { isShuffling = false }
                }
            },
            shapes = ButtonDefaults.shapesFor(height),
            contentPadding = ButtonDefaults.contentPaddingFor(height, hasStartIcon = true),
            modifier = Modifier
                .weight(1f)
                .height(height)
        ) {
            if (isShuffling) {
                LoadingIndicator(
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(ButtonDefaults.iconSizeFor(height))
                )
            } else {
                Icon(
                    ImageVector.vectorResource(R.drawable.round_shuffle_28),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.iconSizeFor(height))
                )
            }
            Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(height)))
            Text(
                stringResource(R.string.home_shuffle_library),
                style = ButtonDefaults.textStyleFor(height),
                maxLines = 1
            )
        }
        FilledTonalButton(
            onClick = onSurprise,
            shapes = ButtonDefaults.shapesFor(height),
            contentPadding = ButtonDefaults.contentPaddingFor(height, hasStartIcon = true),
            modifier = Modifier
                .weight(1f)
                .height(height)
        ) {
            Icon(
                ImageVector.vectorResource(R.drawable.rounded_casino_24),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.iconSizeFor(height))
            )
            Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(height)))
            Text(
                stringResource(R.string.home_random_album),
                style = ButtonDefaults.textStyleFor(height),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

//endregion

//region Listening stats

/**
 * A small summary of what the user listens to: the top artist and album (from the server play
 * counts of the most played albums) and how many songs were played in the app this week.
 * Hidden when there is nothing to show.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ListeningStatsCard(
    mostPlayedAlbums: List<MediaItem>,
    songsPlayedThisWeek: Int?,
    navHostController: NavHostController,
    openAlbum: (MediaItem) -> Unit
) {
    val playedAlbums = remember(mostPlayedAlbums) {
        mostPlayedAlbums.filter { it.playCount > 0 }
    }
    val topAlbum = playedAlbums.maxByOrNull { it.playCount }
    val topArtist = remember(playedAlbums) {
        playedAlbums
            .groupBy { it.mediaMetadata.albumArtist?.toString() }
            .filterKeys { !it.isNullOrBlank() }
            .maxByOrNull { (_, albums) -> albums.sumOf { it.playCount } }
            ?.let { (name, albums) -> TopArtist(name!!, albums.sumOf { it.playCount }.roundToInt(), albums.maxBy { it.playCount }) }
    }
    val weekCount = songsPlayedThisWeek?.takeIf { it > 0 }

    AnimatedVisibility(
        visible = topArtist != null || topAlbum != null || weekCount != null,
        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec())
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.home_your_listening),
                style = MaterialTheme.typography.titleLargeEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                if (topArtist != null) {
                    TopArtistTile(
                        topArtist = topArtist,
                        onClick = {
                            val artist = topArtist.album.mediaMetadata.artists?.firstOrNull()
                            if (artist != null)
                                navHostController.navigate(Screen.ArtistDetails(artist.id, artist.imageUrl)) {
                                    launchSingleTop = true
                                }
                            else openAlbum(topArtist.album)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (topAlbum != null) {
                        StatTile(
                            label = stringResource(R.string.home_on_repeat),
                            value = topAlbum.mediaMetadata.albumTitle?.toString() ?: "",
                            detail = pluralStringResource(R.plurals.home_plays, topAlbum.playCount.roundToInt(), topAlbum.playCount.roundToInt()),
                            container = MaterialTheme.colorScheme.primaryContainer,
                            content = MaterialTheme.colorScheme.onPrimaryContainer,
                            onClick = { openAlbum(topAlbum) }
                        )
                    }
                    if (weekCount != null) {
                        StatTile(
                            label = stringResource(R.string.home_this_week),
                            value = pluralStringResource(R.plurals.home_songs_played, weekCount, weekCount),
                            container = MaterialTheme.colorScheme.tertiaryContainer,
                            content = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }
    }
}

private data class TopArtist(val name: String, val plays: Int, val album: MediaItem)

private val MediaItem.playCount: Double
    get() = mediaMetadata.extras?.getDouble("playCount", 0.0) ?: 0.0

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TopArtistTile(topArtist: TopArtist, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .pressScale(interactionSource)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(interactionSource, ripple(), onClick = onClick)
            .padding(12.dp)
    ) {
        AlbumArtwork(
            album = topArtist.album,
            modifier = Modifier
                .size(88.dp)
                .clip(MaterialShapes.Cookie9Sided.toShape())
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.home_top_artist),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
        )
        Text(
            text = topArtist.name,
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = pluralStringResource(R.plurals.home_plays, topArtist.plays, topArtist.plays),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatTile(
    label: String,
    value: String,
    container: Color,
    content: Color,
    detail: String? = null,
    onClick: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interactionSource)
            .clip(RoundedCornerShape(24.dp))
            .background(container)
            .then(if (onClick != null) Modifier.clickable(interactionSource, ripple(), onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = content.copy(alpha = 0.8f))
        Text(
            value,
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = content,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (detail != null)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = 0.8f))
    }
}

//endregion

//region Sections

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveSectionHeader(
    title: Int,
    key: String,
    viewModel: HomeScreenViewModel,
    onSeeAll: () -> Unit
) {
    val refreshingSections by viewModel.refreshingSections.collectAsStateWithLifecycle()
    val isRefreshing = key in refreshingSections

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.headlineSmallEmphasized,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            ActionTooltip(stringResource(R.string.action_refresh_section)) {
                IconButton(
                    onClick = { viewModel.refreshSection(key) },
                    enabled = !isRefreshing,
                    modifier = Modifier.size(40.dp)
                ) {
                    if (isRefreshing) {
                        LoadingIndicator(Modifier.size(28.dp))
                    } else {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.rounded_refresh_24),
                            contentDescription = stringResource(R.string.action_refresh_section),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        ActionTooltip(stringResource(R.string.home_see_all)) {
            FilledTonalIconButton(
                onClick = onSeeAll,
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.home_see_all)
                )
            }
        }
    }
}

/** Recently played albums in a multi-browse carousel: one large cover and smaller ones next to it. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
private fun HeroAlbumCarousel(
    albums: List<MediaItem>,
    onClick: (MediaItem) -> Unit,
    onPlay: (MediaItem) -> Unit
) {
    if (albums.isEmpty()) {
        PlaceholderRow(height = 196.dp, width = 216.dp)
        return
    }
    val state = rememberCarouselState { albums.size }
    val haptics = LocalHapticFeedback.current

    HorizontalMultiBrowseCarousel(
        state = state,
        preferredItemWidth = 216.dp,
        itemSpacing = 8.dp,
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(196.dp)
    ) { index ->
        val album = albums.getOrNull(index) ?: return@HorizontalMultiBrowseCarousel
        var showMenu by remember { mutableStateOf(false) }
        // 0 when the item is at its smallest in the carousel, 1 when it is the large one.
        val expansion = {
            val info = carouselItemDrawInfo
            if (info.maxSize > info.minSize)
                ((info.size - info.minSize) / (info.maxSize - info.minSize)).coerceIn(0f, 1f)
            else 1f
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .maskClip(MaterialTheme.shapes.extraLarge)
                .combinedClickable(
                    onClick = { onClick(album) },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        showMenu = true
                    }
                )
        ) {
            AlbumArtwork(album, Modifier.fillMaxSize(), size = 600)
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = expansion() }
                    .background(
                        Brush.verticalGradient(
                            0.45f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.75f)
                        )
                    )
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .graphicsLayer { alpha = expansion() }
                    .padding(16.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = album.mediaMetadata.albumTitle?.toString() ?: "",
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = album.mediaMetadata.albumArtist?.toString() ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            ActionTooltip(
                text = stringResource(R.string.action_play_album),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .graphicsLayer { alpha = expansion() }
            ) {
                FilledIconButton(
                    onClick = { onPlay(album) },
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier
                        .padding(12.dp)
                        .size(48.dp)
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.action_play_album),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            AlbumCardMenu(album = album, expanded = showMenu, onDismissRequest = { showMenu = false })
        }
    }
}

@Composable
private fun AlbumCardRow(
    albums: List<MediaItem>,
    itemContent: @Composable (index: Int, album: MediaItem) -> Unit
) {
    if (albums.isEmpty()) {
        PlaceholderRow(height = 196.dp, width = 148.dp)
        return
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        itemsIndexed(albums, key = { _, album -> album.mediaId }) { index, album ->
            itemContent(index, album)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveAlbumCard(
    album: MediaItem,
    shape: Shape,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    badge: String? = null,
    centered: Boolean = false,
    width: Dp = 148.dp
) {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    var showMenu by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        modifier = Modifier
            .width(width)
            .pressScale(interactionSource)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    showMenu = true
                }
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            AlbumArtwork(
                album,
                Modifier
                    .fillMaxSize()
                    .clip(shape)
            )
            if (badge != null) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelMediumEmphasized,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            ActionTooltip(
                text = stringResource(R.string.action_play_album),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                FilledIconButton(
                    onClick = onPlay,
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier
                        .padding(6.dp)
                        .size(40.dp)
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.action_play_album))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        AlbumCardTexts(album, if (centered) TextAlign.Center else TextAlign.Start)
        AlbumCardMenu(album = album, expanded = showMenu, onDismissRequest = { showMenu = false })
    }
}

/** A most played album with its rank as a big number peeking out behind the cover. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RankedAlbumCard(
    rank: Int,
    album: MediaItem,
    onClick: () -> Unit,
    onPlay: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    var showMenu by remember { mutableStateOf(false) }
    val plays = album.playCount.roundToInt()

    Column(
        modifier = Modifier
            .width(176.dp)
            .pressScale(interactionSource)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    showMenu = true
                }
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
        ) {
            Text(
                text = rank.toString(),
                style = MaterialTheme.typography.displayLargeEmphasized,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(y = 10.dp)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(136.dp)
            ) {
                AlbumArtwork(
                    album,
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 48.dp, bottomEnd = 16.dp, bottomStart = 48.dp))
                )
                FilledIconButton(
                    onClick = onPlay,
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(40.dp)
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.action_play_album))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.padding(start = 40.dp)) {
            AlbumCardTexts(album, TextAlign.Start)
            if (plays > 0) {
                Text(
                    text = pluralStringResource(R.plurals.home_plays, plays, plays),
                    style = MaterialTheme.typography.labelMediumEmphasized,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        AlbumCardMenu(album = album, expanded = showMenu, onDismissRequest = { showMenu = false })
    }
}

@Composable
private fun AlbumCardTexts(album: MediaItem, textAlign: TextAlign) {
    Text(
        text = album.mediaMetadata.albumTitle?.toString() ?: "",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
        modifier = Modifier.fillMaxWidth()
    )
    Text(
        text = album.mediaMetadata.albumArtist?.toString() ?: "",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
        modifier = Modifier.fillMaxWidth()
    )
}

/** Empty cards shown while a section has no albums yet. */
@Composable
private fun PlaceholderRow(height: Dp, width: Dp) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(height)
    ) {
        repeat(3) {
            Box(
                Modifier
                    .width(width)
                    .fillMaxHeight()
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            )
        }
    }
}

//endregion

@Composable
private fun AlbumArtwork(album: MediaItem, modifier: Modifier = Modifier, size: Int? = null) {
    val context = LocalContext.current
    AsyncImage(
        model = remember(album.mediaId, size) {
            ImageRequest.Builder(context)
                .data(album.mediaMetadata.artworkUri)
                .crossfade(true)
                .diskCacheKey(album.mediaMetadata.id)
                .diskCachePolicy(CachePolicy.ENABLED)
                .placeholderMemoryCacheKey(album.mediaMetadata.id)
                .apply { if (size != null) size(size) }
                .build()
        },
        contentDescription = album.mediaMetadata.albumTitle?.toString(),
        contentScale = ContentScale.Crop,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)
    )
}

@Composable
private fun Modifier.pressScale(interactionSource: MutableInteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "Press scale"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
