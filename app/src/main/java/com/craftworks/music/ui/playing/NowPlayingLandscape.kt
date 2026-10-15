@file:OptIn(UnstableApi::class)

package com.craftworks.music.ui.playing

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import com.craftworks.music.R
import com.craftworks.music.data.model.MediaModel
import com.craftworks.music.data.model.artists
import com.craftworks.music.data.model.id
import com.craftworks.music.data.model.providerId
import com.craftworks.music.data.repository.LyricsState
import com.craftworks.music.managers.MediaProviderManager
import com.gigamole.composefadingedges.marqueeHorizontalFadingEdges

@Preview(device = "id:tv_1080p", showBackground = true, showSystemUi = true)
@Preview(
    device = "spec:parent=pixel_6,orientation=landscape",
    showBackground = true,
    showSystemUi = true
)
@Composable
fun NowPlayingLandscape(
    mediaController: MediaController? = null,
    metadata: MediaMetadata? = null,
    iconColor: Color = Color.White,
    player: Player? = null,
    actionContext: NowPlayingActionContext = NowPlayingActionContext(),
    buttons: List<NowPlayingButton> = defaultNowPlayingButtons,
    seekBarStyle: NowPlayingSeekBarStyle = NowPlayingSeekBarStyle.STANDARD,
    coverGestures: Map<NowPlayingGesture, NowPlayingAction> = emptyMap(),
    titleAlignment: NowPlayingAlignment = NowPlayingAlignment.LEFT,
    showMoreInfo: Boolean = true,
    onAction: (NowPlayingAction) -> Unit = {},
    isActiveAfter: (NowPlayingAction) -> Boolean = { false },
    onRefreshLyrics: () -> Unit = {},
    onArtistNav: (MediaModel.Artist) -> Unit = {},
) {
    val iconTextColor by animateColorAsState(
        targetValue = iconColor,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessVeryLow
        ),
        label = "Animated text color"
    )

    // The lyrics are always shown next to the cover.
    val excludedButtons = setOf(NowPlayingAction.LYRICS)

    val isRadio = metadata?.mediaType == MediaMetadata.MEDIA_TYPE_RADIO_STATION

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            NowPlayingCoverGestures(
                gestures = coverGestures,
                onAction = onAction,
                isActiveAfter = isActiveAfter,
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
            ) {
                Crossfade(
                    targetState = metadata?.artworkUri.toString(),
                    animationSpec = tween(
                        durationMillis = 400,
                        easing = FastOutSlowInEasing
                    ),
                    label = "Crossfade between albums"
                ) { artworkUri ->
                    NowPlayingArtwork(
                        artworkUri = artworkUri,
                        songId = metadata?.id,
                        fallback = painterResource(R.drawable.placeholder),
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxSize()
                            .shadow(4.dp, RoundedCornerShape(24.dp), clip = true)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CompositionLocalProvider(
                    LocalLayoutDirection provides
                            if (titleAlignment == NowPlayingAlignment.RIGHT)
                                LayoutDirection.Rtl
                            else LayoutDirection.Ltr
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Crossfade(
                                targetState = metadata?.title.toString(),
                                animationSpec = tween(
                                    durationMillis = 400,
                                    easing = FastOutSlowInEasing
                                ),
                                label = "Animated Song Title",
                                modifier = Modifier.weight(1f)
                            ) { title ->
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.headlineMediumEmphasized,
                                    fontWeight = FontWeight.Bold,
                                    color = iconTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Visible,
                                    softWrap = false,
                                    textAlign = when (titleAlignment) {
                                        NowPlayingAlignment.LEFT -> TextAlign.Start
                                        NowPlayingAlignment.CENTER -> TextAlign.Center
                                        NowPlayingAlignment.RIGHT -> TextAlign.End
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .marqueeHorizontalFadingEdges(
                                            marqueeProvider = { Modifier.basicMarquee() })
                                )
                            }

                            NowPlayingTitleButtons(
                                buttons = buttons,
                                player = player,
                                context = actionContext,
                                color = iconTextColor,
                                onAction = onAction,
                                excluded = excludedButtons,
                            )
                        }
                    }
                }

                Crossfade(
                    targetState = metadata?.artists,
                    animationSpec = tween(
                        durationMillis = 400,
                        easing = FastOutSlowInEasing
                    ),
                    label = "Animated Artist"
                ) { artistInfo ->
                    Text(
                        text = buildAnnotatedString {
                            artistInfo?.forEachIndexed { index, artist ->
                                withLink(
                                    LinkAnnotation.Clickable(
                                        tag = "ARTIST",
                                        linkInteractionListener = {
                                            onArtistNav(artist)
                                        }
                                    )
                                ) {
                                    append(artist.name)
                                }
                                if (index != artistInfo.size - 1) append(" • ")
                            }
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Normal,
                        color = iconTextColor.copy(alpha = 0.7f),
                        maxLines = 1,
                        softWrap = false,
                        textAlign = when (titleAlignment) {
                            NowPlayingAlignment.LEFT -> TextAlign.Start
                            NowPlayingAlignment.CENTER -> TextAlign.Center
                            NowPlayingAlignment.RIGHT -> TextAlign.End
                        },
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .marqueeHorizontalFadingEdges(marqueeProvider = { Modifier.basicMarquee() })
                    )
                }

                if (showMoreInfo && !isRadio) {
                    Text(
                        text = buildString {
                            append(
                                metadata?.extras?.getString("format")?.uppercase()
                                    ?: ""
                            )
                            append(" · ")
                            append(metadata?.extras?.getLong("bitrate") ?: "")
                            append(" · ")
                            append(MediaProviderManager.getProvider(metadata?.providerId?:"")?.providerName?.let { stringResource(it) } ?:"")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = iconTextColor.copy(alpha = 0.45f),
                        maxLines = 1,
                        textAlign = when (titleAlignment) {
                            NowPlayingAlignment.LEFT -> TextAlign.Start
                            NowPlayingAlignment.CENTER -> TextAlign.Center
                            NowPlayingAlignment.RIGHT -> TextAlign.End
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (metadata?.mediaType != MediaMetadata.MEDIA_TYPE_RADIO_STATION)
                    PlaybackProgressSlider(iconTextColor, mediaController, style = seekBarStyle)

            }
            NowPlayingControlsRow(
                buttons = buttons.filter { it.action !in excludedButtons },
                player = player,
                context = actionContext,
                color = iconTextColor,
                playPauseSize = 92.dp,
                onAction = onAction,
                modifier = Modifier
                    .wrapContentHeight()
                    .fillMaxWidth()
            )

            NowPlayingBottomRow(
                buttons = buttons,
                player = player,
                context = actionContext,
                color = iconTextColor,
                onAction = onAction,
                excluded = excludedButtons,
                modifier = Modifier.requiredHeightIn(min = 48.dp)
            )
        }

        val lyrics by LyricsState.lyrics.collectAsStateWithLifecycle()

        AnimatedVisibility(
            metadata?.mediaType != MediaMetadata.MEDIA_TYPE_RADIO_STATION && lyrics != null
        ) {
            Box(Modifier
                .widthIn(max = 480.dp, min = 256.dp)
                .fillMaxHeight()) {
                LyricsView(
                    iconTextColor,
                    true,
                    mediaController,
                    PaddingValues(horizontal = 32.dp, vertical = 16.dp),
                    onRefreshLyrics
                )
            }
        }
    }
}