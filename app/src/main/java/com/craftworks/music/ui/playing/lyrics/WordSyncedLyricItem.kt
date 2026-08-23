package com.craftworks.music.ui.playing.lyrics

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.craftworks.music.data.model.Lyric
import com.craftworks.music.ui.playing.NowPlayingAlignment
import com.craftworks.music.ui.playing.calculateLyricBlur
import com.craftworks.music.ui.playing.dpToPx

@Composable
fun WordSyncedLyricItem(
    lyric: Lyric,
    index: Int,
    currentLyricIndex: Int,
    currentPosition: Int,
    useBlur: Boolean,
    visibleItemsInfo: List<LazyListItemInfo>,
    color: Color,
    lyricsAnimationSpeed: Int = 1200,
    lyricsAlignment: NowPlayingAlignment,
    onClick: () -> Unit = {},
) {
    val lyricBlur: Dp by animateDpAsState(
        targetValue = if (useBlur) calculateLyricBlur(
            index, currentLyricIndex, visibleItemsInfo
        ) else 0.dp,
        label = "Lyric Blur",
        animationSpec = tween(lyricsAnimationSpeed, 0, FastOutSlowInEasing)
    )

    val scale by animateFloatAsState(
        targetValue = if (currentLyricIndex == index) 1f else 0.9f,
        label = "Lyric Scale Animation",
        animationSpec = tween(lyricsAnimationSpeed, 0, FastOutSlowInEasing)
    )

    if (lyric.text[0].isEmpty()) {
        AnimatedContent(
            targetState = currentLyricIndex == index
        ) {
            if (it) {
                Box(
                    modifier = Modifier
                        .focusable(false)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        },
                    contentAlignment = when (lyricsAlignment) {
                        NowPlayingAlignment.LEFT -> Alignment.TopStart
                        NowPlayingAlignment.CENTER -> Alignment.TopCenter
                        NowPlayingAlignment.RIGHT -> Alignment.TopEnd
                    }
                ) {
                    InterludeIndicator(color)
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .padding(vertical = 12.dp)
                .heightIn(min = 48.dp)
                .focusable(false)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .blur(lyricBlur)
                .clickable {
                    onClick()
                },
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            FlowRow (
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = when (lyricsAlignment) {
                    NowPlayingAlignment.LEFT -> Arrangement.Start
                    NowPlayingAlignment.CENTER -> Arrangement.Center
                    NowPlayingAlignment.RIGHT -> Arrangement.End
                }
            ) {
                lyric.words?.forEachIndexed { i, word ->
                    val nextWordStart = lyric.words.getOrNull(i + 1)?.startMs ?: lyric.endMs!!
                    val duration = word.endMs?.let { it - word.startMs } ?: (nextWordStart - word.startMs)
                    val isThisWordActive = currentPosition >= word.startMs && currentPosition < lyric.endMs!!

                    AnimatedWord(
                        wordText = word.text,
                        isActive = isThisWordActive,
                        durationMillis = duration,
                        color = color
                    )
                }
            }
        }
    }
}


@Composable
fun AnimatedWord(
    wordText: String,
    isActive: Boolean,
    durationMillis: Int,
    color: Color
) {
    val inactiveColor = color.copy(alpha = 0.4f)
    val wipeProgress = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(1f) }

    val dipAmount = dpToPx(1).toFloat()

    LaunchedEffect(isActive) {
        if (isActive) {
            textAlpha.snapTo(1f)
            wipeProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = durationMillis,
                    easing = LinearEasing
                )
            )
        } else {
            wipeProgress.snapTo(0f)
            textAlpha.animateTo(
                targetValue = 0.4f,
                animationSpec = tween(durationMillis = 400, easing = LinearEasing)
            )
        }
    }

    val brush = if (isActive && wipeProgress.isRunning) {
        val currentOffset = wipeProgress.value * (1f + 0.3f)
        val activeEnd = (currentOffset - 0.3f).coerceIn(0f, 1f)
        val inactiveStart = currentOffset.coerceIn(0f, 1f)
        Brush.horizontalGradient(
            0f to color,
            activeEnd to color,
            inactiveStart to inactiveColor,
            1f to inactiveColor
        )
    } else {
        SolidColor(color)
    }

    val yOffset = remember { Animatable(0f) }
    LaunchedEffect(isActive) {
        if (isActive) {
            yOffset.animateTo(-dipAmount, tween(120, easing = FastOutSlowInEasing))
            yOffset.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
        } else {
            yOffset.animateTo(0f, tween(durationMillis, 0, FastOutSlowInEasing))
        }
    }

    Text(
        text = wordText,
        style = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.SemiBold,
            textMotion = TextMotion.Animated
        ),
        modifier = Modifier
            .graphicsLayer {
                translationY = yOffset.value
                alpha = textAlpha.value
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithCache {
                onDrawWithContent {
                    drawContent()
                    drawRect(
                        brush = brush,
                        blendMode = BlendMode.SrcIn
                    )
                }
            },
    )
}
