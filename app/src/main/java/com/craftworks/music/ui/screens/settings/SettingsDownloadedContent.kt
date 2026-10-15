package com.craftworks.music.ui.screens.settings

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.text.htmlEncode
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.craftworks.music.R
import com.craftworks.music.ui.elements.ActionTooltip
import com.craftworks.music.ui.elements.dialogs.RemoveDownloadsDialog
import com.craftworks.music.ui.viewmodels.DownloadSource
import com.craftworks.music.ui.viewmodels.DownloadedContentViewModel
import com.craftworks.music.ui.viewmodels.DownloadedItem
import com.craftworks.music.ui.viewmodels.DownloadedType

/** The songs downloaded in the app and the ones kept by the smart cache, by album, artist, playlist or song. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun S_DownloadedContentScreen(
    navHostController: NavHostController = rememberNavController(),
    viewModel: DownloadedContentViewModel = hiltViewModel()
) {
    val source by viewModel.source.collectAsStateWithLifecycle()
    val type by viewModel.type.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()

    var itemToRemove by remember { mutableStateOf<DownloadedItem?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.downloads_browse)) },
                navigationIcon = {
                    ActionTooltip(stringResource(R.string.action_back)) {
                        IconButton(
                            onClick = {
                                navHostController.popBackStack()
                            },
                            modifier = Modifier.size(56.dp, 70.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                tint = MaterialTheme.colorScheme.onBackground,
                                contentDescription = stringResource(R.string.action_back),
                                modifier = Modifier
                                    .size(24.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .fillMaxSize()
        ) {
            PrimaryTabRow(
                selectedTabIndex = source.ordinal,
                containerColor = Color.Transparent
            ) {
                DownloadSource.entries.forEach { tabSource ->
                    Tab(
                        selected = source == tabSource,
                        onClick = { viewModel.setSource(tabSource) },
                        text = { Text(downloadSourceLabel(tabSource)) }
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DownloadedType.entries.forEach { chipType ->
                    FilterChip(
                        selected = type == chipType,
                        onClick = { viewModel.setType(chipType) },
                        label = { Text(downloadedTypeLabel(chipType)) }
                    )
                }
            }

            val currentItems = items
            when {
                currentItems == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                currentItems.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.downloads_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(currentItems, key = { it.key }) { item ->
                        DownloadedItemRow(
                            item = item,
                            imageShape = if (type == DownloadedType.ARTISTS) CircleShape else RoundedCornerShape(8.dp),
                            onRemove = { itemToRemove = item }
                        )
                    }
                }
            }
        }
    }

    itemToRemove?.let { item ->
        RemoveDownloadsDialog(
            title = stringResource(R.string.downloads_remove_title),
            text = removeDownloadsText(item, source),
            onDismissRequest = { itemToRemove = null },
            onConfirm = { viewModel.remove(item) }
        )
    }
}

@Composable
private fun DownloadedItemRow(item: DownloadedItem, imageShape: Shape, onRemove: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceBright)
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)
    ) {
        AsyncImage(
            model = item.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(imageShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = downloadedItemSummary(item),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        ActionTooltip(stringResource(R.string.action_remove)) {
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.action_remove),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun downloadSourceLabel(source: DownloadSource): String = when (source) {
    DownloadSource.DOWNLOADS -> stringResource(R.string.downloads_tab_downloads)
    DownloadSource.SMART_CACHE -> stringResource(R.string.downloads_tab_smart_cache)
}

@Composable
fun downloadedTypeLabel(type: DownloadedType): String = when (type) {
    DownloadedType.ALBUMS -> stringResource(R.string.nav_albums)
    DownloadedType.ARTISTS -> stringResource(R.string.nav_artists)
    DownloadedType.PLAYLISTS -> stringResource(R.string.nav_playlists)
    DownloadedType.SONGS -> stringResource(R.string.nav_songs)
}

/** The subtitle of [item], its song count and size. */
@Composable
fun downloadedItemSummary(item: DownloadedItem): String {
    val size = Formatter.formatShortFileSize(LocalContext.current, item.sizeBytes)
    val summary = if (item.totalSongs != null)
        stringResource(R.string.downloads_item_summary_partial, item.songs.size, item.totalSongs, size)
    else
        stringResource(R.string.downloads_item_summary, item.songs.size, size)
    return listOfNotNull(item.subtitle?.takeIf { it.isNotBlank() }, summary).joinToString(" · ")
}

/** Asks to confirm removing [item] from the device, as HTML. */
@Composable
fun removeDownloadsText(item: DownloadedItem, source: DownloadSource): String {
    val text = stringResource(
        R.string.downloads_remove_confirm,
        item.title.htmlEncode(),
        item.songs.size,
        Formatter.formatShortFileSize(LocalContext.current, item.sizeBytes)
    )
    return if (source == DownloadSource.SMART_CACHE) "$text ${stringResource(R.string.downloads_remove_smart_cache_note)}"
        else text
}
