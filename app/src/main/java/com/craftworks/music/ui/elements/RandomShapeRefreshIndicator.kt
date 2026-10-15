package com.craftworks.music.ui.elements

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.graphics.shapes.RoundedPolygon

/** Shapes the pull to refresh indicator picks a random one from on each reload. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val refreshShapes = listOf(
    MaterialShapes.SoftBurst,
    MaterialShapes.Cookie4Sided,
    MaterialShapes.Cookie6Sided,
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Cookie12Sided,
    MaterialShapes.Clover4Leaf,
    MaterialShapes.Clover8Leaf,
    MaterialShapes.Sunny,
    MaterialShapes.VerySunny,
    MaterialShapes.Pentagon,
    MaterialShapes.Gem,
    MaterialShapes.Flower,
    MaterialShapes.Puffy,
    MaterialShapes.PuffyDiamond,
    MaterialShapes.Burst,
    MaterialShapes.Ghostish,
    MaterialShapes.Pill,
    MaterialShapes.Arch,
    MaterialShapes.Bun,
)

/**
 * Same as [PullToRefreshDefaults.LoadingIndicator], but the circle morphs into a random shape of
 * [refreshShapes] while pulling, and the loading animation starts from it. A new shape is picked
 * after each reload.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RandomShapeRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    var shapes: List<RoundedPolygon> by remember { mutableStateOf(refreshShapes.shuffled()) }
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) shapes = refreshShapes.shuffled()
    }
    val pullShapes = remember(shapes) { listOf(MaterialShapes.Circle, shapes.first()) }
    val containerColor = PullToRefreshDefaults.loadingIndicatorContainerColor
    val color = PullToRefreshDefaults.loadingIndicatorColor
    val indicatorModifier = Modifier.requiredSize(
        width = LoadingIndicatorDefaults.ContainerWidth,
        height = LoadingIndicatorDefaults.ContainerHeight
    )

    PullToRefreshDefaults.IndicatorBox(
        state = state,
        isRefreshing = isRefreshing,
        containerColor = containerColor,
        elevation = PullToRefreshDefaults.LoadingIndicatorElevation,
        modifier = modifier.size(
            width = LoadingIndicatorDefaults.ContainerWidth,
            height = LoadingIndicatorDefaults.ContainerHeight
        )
    ) {
        Crossfade(isRefreshing) { refreshing ->
            if (refreshing) {
                ContainedLoadingIndicator(
                    modifier = indicatorModifier,
                    containerColor = containerColor,
                    indicatorColor = color,
                    polygons = shapes
                )
            } else {
                ContainedLoadingIndicator(
                    progress = { state.distanceFraction },
                    modifier = indicatorModifier.drawWithContent {
                        val progress = state.distanceFraction
                        if (progress > 1f) {
                            rotate(-(progress - 1) * 180) { this@drawWithContent.drawContent() }
                        } else {
                            drawContent()
                        }
                    },
                    containerColor = containerColor,
                    indicatorColor = color,
                    polygons = pullShapes
                )
            }
        }
    }
}
