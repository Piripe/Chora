package com.craftworks.music.ui.elements

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.craftworks.music.R
import com.craftworks.music.managers.settings.AppearanceSettingsManager
import com.craftworks.music.utils.fadingEdge

/**
 * Whether the screens use the Material 3 Expressive design, see [AppearanceSettingsManager.expressiveDesignFlow].
 * Provided by `MainActivity` for the phone layout; the TV screens don't read it.
 */
val LocalExpressiveDesign = staticCompositionLocalOf { true }

/** Shrinks the element a little while it is pressed. */
@Composable
fun Modifier.pressScale(interactionSource: MutableInteractionSource): Modifier {
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

/**
 * The shape of the item at [index] of a group of [count] items drawn as one block:
 * the outer corners of the group are round, the corners between items are small.
 */
fun segmentedItemShape(index: Int, count: Int, outer: Dp = 24.dp, inner: Dp = 6.dp): Shape {
    val top = if (index == 0) outer else inner
    val bottom = if (index == count - 1) outer else inner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/** Space between the items of a segmented group, see [segmentedItemShape]. */
val SegmentedItemGap = 2.dp

/** The shape of a group of settings. */
@Composable
fun settingsGroupShape(): Shape =
    RoundedCornerShape(if (LocalExpressiveDesign.current) 24.dp else 16.dp)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    ActionTooltip(stringResource(R.string.action_back), modifier = modifier) {
        FilledTonalIconButton(
            onClick = onClick,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(48.dp)
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
        }
    }
}

/** The settings icon, in a round tonal badge with the expressive design. */
@Composable
fun SettingsIcon(
    icon: ImageVector,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val alpha = if (enabled) 1f else 0.5f
    if (LocalExpressiveDesign.current) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = alpha))
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = alpha),
                modifier = Modifier.size(24.dp)
            )
        }
    } else {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint.copy(alpha = alpha),
            modifier = Modifier
                .padding(start = 20.dp, end = 16.dp)
                .size(32.dp)
        )
    }
}

/**
 * The scaffold of the settings screens: a top bar with the title and a back button.
 * With the expressive design, the title starts large and shrinks as the content scrolls.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    val colors = TopAppBarDefaults.topAppBarColors(
        containerColor = Color.Transparent,
        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
    )
    if (LocalExpressiveDesign.current) {
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                MediumFlexibleTopAppBar(
                    title = { Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        ExpressiveBackButton(onBack, Modifier.padding(horizontal = 4.dp))
                    },
                    colors = colors,
                    scrollBehavior = scrollBehavior
                )
            },
            content = content
        )
    } else {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            topBar = {
                TopAppBar(
                    title = { Text(text = title) },
                    navigationIcon = {
                        ActionTooltip(stringResource(R.string.action_back)) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.size(56.dp, 70.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    contentDescription = stringResource(R.string.action_back),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            content = content
        )
    }
}

/** The spinner shown while a details screen loads: a shape morphing indicator with the expressive design. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DetailsLoadingIndicator() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (LocalExpressiveDesign.current) {
            LoadingIndicator(Modifier.size(96.dp))
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(64.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 6.dp
            )
            Text(
                text = "Loading",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

/** How the artwork of an [ExpressiveDetailsHeader] comes in. */
enum class HeaderArtworkStyle {
    /** A rounded square that grows into place. */
    COVER,
    /** A cookie shape that spins into place, the picture staying upright inside it. */
    PORTRAIT
}

/**
 * The top of the album, artist and playlist screens with the expressive design: the artwork blurred
 * behind, the artwork itself in the middle, then the title, [subtitle] and [actions].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveDetailsHeader(
    imageData: Any?,
    imageCacheKey: String?,
    title: String,
    onBack: () -> Unit,
    artworkStyle: HeaderArtworkStyle = HeaderArtworkStyle.COVER,
    modifier: Modifier = Modifier,
    fallback: Painter? = null,
    subtitle: @Composable ColumnScope.() -> Unit = {},
    actions: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val request = remember(imageData, imageCacheKey) {
        ImageRequest.Builder(context)
            .data(imageData)
            .diskCacheKey(imageCacheKey)
            .diskCachePolicy(CachePolicy.READ_ONLY)
            .placeholderMemoryCacheKey(imageCacheKey)
            .crossfade(true)
            .build()
    }

    val entrance = remember { Animatable(0f) }
    val entranceSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(Unit) { entrance.animateTo(1f, entranceSpec) }

    Box(modifier.fillMaxWidth()) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .fadingEdge(Brush.verticalGradient(listOf(Color.Red.copy(alpha = 0.55f), Color.Transparent)))
                .blur(32.dp)
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding() + 8.dp)
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp)
        ) {
            Row(Modifier.fillMaxWidth()) {
                ExpressiveBackButton(onBack)
            }

            val artworkShape = when (artworkStyle) {
                HeaderArtworkStyle.COVER -> RoundedCornerShape(36.dp)
                HeaderArtworkStyle.PORTRAIT -> MaterialShapes.Cookie12Sided.toShape()
            }
            Box(
                modifier = Modifier
                    .size(216.dp)
                    .graphicsLayer {
                        val scale = 0.8f + 0.2f * entrance.value
                        scaleX = scale
                        scaleY = scale
                        if (artworkStyle == HeaderArtworkStyle.PORTRAIT)
                            rotationZ = (1f - entrance.value) * -120f
                    }
                    .clip(artworkShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
            ) {
                if (artworkStyle == HeaderArtworkStyle.PORTRAIT) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.rounded_artist_24),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(72.dp)
                    )
                }
                AsyncImage(
                    model = request,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    fallback = fallback,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            if (artworkStyle == HeaderArtworkStyle.PORTRAIT)
                                rotationZ = (1f - entrance.value) * 120f
                        }
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.headlineLargeEmphasized,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            subtitle()
            Spacer(Modifier.height(16.dp))
            actions()
        }
    }
}

/** A small title in the primary color, above a group of items. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveGroupTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMediumEmphasized,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

/** The title of a screen's top app bar: large and emphasized with the expressive design. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TopBarTitle(text: String) {
    if (LocalExpressiveDesign.current)
        Text(
            text = text,
            style = MaterialTheme.typography.headlineMediumEmphasized,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    else
        Text(text = text)
}

/** The height of a screen's top app bar, a bit taller for the larger [TopBarTitle] of the expressive design. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun topBarHeight(): Dp =
    if (LocalExpressiveDesign.current) 72.dp else TopAppBarDefaults.TopAppBarExpandedHeight
