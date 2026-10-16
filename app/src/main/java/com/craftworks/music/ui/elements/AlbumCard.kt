package com.craftworks.music.ui.elements

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.craftworks.music.R
import com.craftworks.music.data.model.id

@OptIn(ExperimentalFoundationApi::class)
@Stable
@Composable
fun AlbumCard(
    album: MediaItem,
    onClick: () -> Unit = { },
    onPlay: (album: MediaItem) -> Unit = { },
    modifier: Modifier = Modifier
) {
    if (album.mediaMetadata.mediaType != MediaMetadata.MEDIA_TYPE_ALBUM) return
    if (LocalExpressiveDesign.current) {
        ExpressiveAlbumCard(album, onClick, onPlay, modifier)
        return
    }
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var showMenu by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            //.padding(12.dp, 0.dp, 0.dp, 0.dp)
            .width(128.dp)
            //.height(172.dp)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    showMenu = true
                }
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(album.mediaMetadata.artworkUri)
                    .crossfade(true)
                    .diskCacheKey(album.mediaMetadata.id)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .placeholderMemoryCacheKey(album.mediaMetadata.id)
                    .listener(
                        onError = { request, result ->
                            Log.e("AlbumCard", "Failed for ${request.data}", result.throwable)
                        }
                    )
                    .build(),
                contentDescription = "Album Image",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )

            ActionTooltip(
                text = stringResource(R.string.action_play_album),
                modifier = Modifier.align(Alignment.BottomStart)
            ) {
                IconButton(
                    onClick = {
                        onPlay(album)
                    },
                    modifier = Modifier
                        .padding(6.dp)
                        .background(
                            MaterialTheme.colorScheme.background.copy(alpha = 0.75f),
                            shape = CircleShape
                        )
                        .height(36.dp)
                        .size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        tint = MaterialTheme.colorScheme.onBackground,
                        contentDescription = "Play Album",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = album.mediaMetadata.albumTitle.toString(),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )

        Text(
            text = album.mediaMetadata.albumArtist.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = LocalContentColor.current.copy(alpha = 0.75f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )

        AlbumCardMenu(
            album = album,
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        )
    }
}

/** The album card of the expressive design: a large rounded cover that shrinks when pressed, its title under it. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveAlbumCard(
    album: MediaItem,
    onClick: () -> Unit,
    onPlay: (album: MediaItem) -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    var showMenu by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .width(128.dp)
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
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(album.mediaMetadata.artworkUri)
                    .crossfade(true)
                    .diskCacheKey(album.mediaMetadata.id)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .placeholderMemoryCacheKey(album.mediaMetadata.id)
                    .build(),
                contentDescription = album.mediaMetadata.albumTitle?.toString(),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )

            ActionTooltip(
                text = stringResource(R.string.action_play_album),
                modifier = Modifier.align(Alignment.BottomStart)
            ) {
                FilledIconButton(
                    onClick = { onPlay(album) },
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier
                        .padding(6.dp)
                        .size(36.dp)
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.action_play_album))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = album.mediaMetadata.albumTitle.toString(),
            style = MaterialTheme.typography.titleSmallEmphasized,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = album.mediaMetadata.albumArtist.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )

        AlbumCardMenu(
            album = album,
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        )
    }
}
