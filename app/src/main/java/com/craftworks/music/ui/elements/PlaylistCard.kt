package com.craftworks.music.ui.elements

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.craftworks.music.data.model.ProviderType
import com.craftworks.music.data.model.id
import com.craftworks.music.data.model.providerType
import com.craftworks.music.ui.elements.dialogs.playlistToDelete
import com.craftworks.music.ui.elements.dialogs.showDeletePlaylistDialog

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaylistCard(playlist: MediaItem, onClick: () -> Unit) {
    val metadata = playlist.mediaMetadata
    val context = LocalContext.current
    val expressive = LocalExpressiveDesign.current
    val interactionSource = remember { MutableInteractionSource() }
    val coverShape = if (expressive) MaterialTheme.shapes.extraLarge else RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            //.padding(12.dp)
            .then(
                if (expressive) Modifier.pressScale(interactionSource)
                else Modifier.clip(RoundedCornerShape(12.dp))
            )
            .combinedClickable(
                interactionSource = interactionSource,
                // The expressive card shrinks when pressed instead.
                indication = if (expressive) null else ripple(),
                onClick = { onClick() },
                onLongClick = {
                    playlistToDelete.value =
                        playlist.mediaMetadata.id ?: ""
                    showDeletePlaylistDialog.value = true
                },
                onLongClickLabel = "Delete Playlist"
            )
            .widthIn(min = 128.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(
                    if (metadata.providerType == ProviderType.LOCAL_FOLDER)
                        metadata.artworkData else
                        metadata.artworkUri
                )
                .crossfade(true)
                .diskCacheKey(metadata.id)
                .diskCachePolicy(CachePolicy.ENABLED)
                .placeholderMemoryCacheKey(metadata.id)
                .build(),
            contentScale = ContentScale.FillWidth,
            contentDescription = "Album Image",
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant, coverShape)
                .clip(coverShape)
        )

        Text(
            text = metadata.title.toString(),
            style = if (expressive) MaterialTheme.typography.titleSmallEmphasized else MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 4.dp)
                .wrapContentHeight(align = Alignment.CenterVertically),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}