package com.craftworks.music.ui.screens.tv.settings

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.craftworks.music.R
import com.craftworks.music.data.model.Screen
import com.craftworks.music.managers.statusText
import com.craftworks.music.ui.elements.dialogs.smartCacheSizeLabel
import com.craftworks.music.ui.elements.dialogs.tv.RemoveDownloadsDialog
import com.craftworks.music.ui.elements.dialogs.tv.SmartCacheSizeDialog
import com.craftworks.music.ui.screens.settings.downloadSourceLabel
import com.craftworks.music.ui.screens.settings.downloadedItemSummary
import com.craftworks.music.ui.screens.settings.downloadedSongsLabel
import com.craftworks.music.ui.screens.settings.downloadedTypeLabel
import com.craftworks.music.ui.screens.settings.removeDownloadsText
import com.craftworks.music.ui.viewmodels.DownloadSource
import com.craftworks.music.ui.viewmodels.DownloadedContentViewModel
import com.craftworks.music.ui.viewmodels.DownloadedItem
import com.craftworks.music.ui.viewmodels.DownloadedType
import com.craftworks.music.ui.viewmodels.SmartCacheViewModel

@Composable
fun TvS_DownloadsScreen(
    navHostController: NavHostController = rememberNavController(),
    smartCacheViewModel: SmartCacheViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    var showSmartCacheSizeDialog by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    val state by smartCacheViewModel.state.collectAsStateWithLifecycle()
    val smartCacheMaxSizeGb by smartCacheViewModel.maxSizeGb.collectAsStateWithLifecycle()
    val smartCacheSupported by smartCacheViewModel.isSupported.collectAsStateWithLifecycle()
    val isOffline by smartCacheViewModel.isOffline.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 24.dp)
    ) {
        item {
            SettingsButtonItem(
                title = stringResource(R.string.downloads_storage),
                subtitle = downloadedSongsLabel(state),
                icon = ImageVector.vectorResource(R.drawable.rounded_download_24),
                description = stringResource(R.string.downloads_storage_description),
                onClick = { if (state.isDownloading) smartCacheViewModel.cancelDownloads() }
            )
        }

        item {
            SettingsButtonItem(
                title = stringResource(R.string.downloads_browse),
                subtitle = stringResource(R.string.downloads_browse_description),
                icon = ImageVector.vectorResource(R.drawable.rounded_library_music_24),
                onClick = {
                    navHostController.navigate(Screen.S_Downloaded_Content) {
                        launchSingleTop = true
                    }
                }
            )
        }

        item {
            SettingsButtonItem(
                title = stringResource(R.string.downloads_delete_all),
                subtitle = stringResource(R.string.downloads_delete_all_description),
                icon = ImageVector.vectorResource(R.drawable.delete_sweep_24px),
                enabled = state.downloadedSongCount > 0 || state.isDownloading,
                onClick = { showDeleteAllDialog = true }
            )
        }

        item {
            SettingsButtonItem(
                title = stringResource(R.string.smart_cache_size),
                description = stringResource(R.string.smart_cache_size_description),
                subtitle = if (smartCacheMaxSizeGb > 0)
                    stringResource(
                        R.string.smart_cache_usage,
                        Formatter.formatShortFileSize(context, state.usedBytes),
                        smartCacheSizeLabel(smartCacheMaxSizeGb),
                        state.songCount
                    )
                else
                    smartCacheSizeLabel(0),
                icon = ImageVector.vectorResource(R.drawable.rounded_storage_24),
                onClick = { showSmartCacheSizeDialog = true }
            )
        }

        item {
            SettingsButtonItem(
                title = stringResource(R.string.smart_cache_refresh),
                subtitle = when {
                    state.isRunning -> stringResource(
                        R.string.smart_cache_tap_to_cancel,
                        state.statusText(context.resources)
                    )
                    !smartCacheSupported -> stringResource(R.string.smart_cache_error_unsupported)
                    else -> state.statusText(context.resources)
                },
                icon = ImageVector.vectorResource(R.drawable.rounded_refresh_24),
                enabled = state.isRunning ||
                        (smartCacheMaxSizeGb > 0 && smartCacheSupported && !isOffline),
                onClick = { smartCacheViewModel.toggleRefresh() }
            )
        }
    }

    if (showSmartCacheSizeDialog) SmartCacheSizeDialog(
        setShowDialog = { showSmartCacheSizeDialog = it },
        selectedSizeGb = smartCacheMaxSizeGb,
        onSizeSelected = smartCacheViewModel::setMaxSizeGb
    )
    if (showDeleteAllDialog) RemoveDownloadsDialog(
        title = stringResource(R.string.downloads_delete_all),
        text = stringResource(
            R.string.downloads_delete_all_confirm,
            state.downloadedSongCount,
            Formatter.formatShortFileSize(context, state.downloadedBytes)
        ),
        onDismissRequest = { showDeleteAllDialog = false },
        onConfirm = smartCacheViewModel::clearDownloads
    )
}

/** The songs downloaded in the app and the ones kept by the smart cache. Selecting an item asks to remove it. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvS_DownloadedContentScreen(viewModel: DownloadedContentViewModel = hiltViewModel()) {
    val source by viewModel.source.collectAsStateWithLifecycle()
    val type by viewModel.type.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()

    var itemToRemove by remember { mutableStateOf<DownloadedItem?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 24.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DownloadSource.entries.forEach { chipSource ->
                    FilterChip(
                        selected = source == chipSource,
                        onClick = { viewModel.setSource(chipSource) }
                    ) {
                        Text(downloadSourceLabel(chipSource))
                    }
                }
            }
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                DownloadedType.entries.forEach { chipType ->
                    FilterChip(
                        selected = type == chipType,
                        onClick = { viewModel.setType(chipType) }
                    ) {
                        Text(downloadedTypeLabel(chipType))
                    }
                }
            }
        }

        val currentItems = items
        if (currentItems != null && currentItems.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.downloads_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        items(currentItems.orEmpty(), key = { it.key }) { item ->
            ListItem(
                selected = false,
                onClick = { itemToRemove = item },
                headlineContent = {
                    Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                supportingContent = {
                    Text(downloadedItemSummary(item), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                leadingContent = {
                    AsyncImage(
                        model = item.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(if (type == DownloadedType.ARTISTS) CircleShape else RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                },
                trailingContent = {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.downloads_remove)
                    )
                }
            )
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
