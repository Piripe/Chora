package com.craftworks.music.ui.screens.settings

import android.text.format.Formatter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.craftworks.music.R
import com.craftworks.music.data.model.Screen
import com.craftworks.music.managers.MediaProviderManager
import com.craftworks.music.managers.SmartCacheState
import com.craftworks.music.managers.downloadStatusText
import com.craftworks.music.managers.settings.MiscSettingsManager
import com.craftworks.music.managers.statusText
import com.craftworks.music.ui.elements.ActionTooltip
import com.craftworks.music.ui.elements.dialogs.DownloadTemplateDialog
import com.craftworks.music.ui.elements.dialogs.RemoveDownloadsDialog
import com.craftworks.music.ui.elements.dialogs.SmartCacheSizeDialog
import com.craftworks.music.ui.elements.dialogs.dialogFocusable
import com.craftworks.music.ui.elements.dialogs.smartCacheSizeLabel
import com.craftworks.music.ui.viewmodels.SmartCacheViewModel
import kotlinx.coroutines.launch

@Preview(showSystemUi = false, showBackground = true)
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun S_DownloadsScreen(
    navHostController: NavHostController = rememberNavController(),
    smartCacheViewModel: SmartCacheViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showSmartCacheSizeDialog by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var showDownloadTemplateDialog by remember { mutableStateOf(false) }
    var showPlaylistDownloadTemplateDialog by remember { mutableStateOf(false) }

    val state by smartCacheViewModel.state.collectAsStateWithLifecycle()
    val smartCacheMaxSizeGb by smartCacheViewModel.maxSizeGb.collectAsStateWithLifecycle()
    val smartCacheSupported by smartCacheViewModel.isSupported.collectAsStateWithLifecycle()
    val isOffline by smartCacheViewModel.isOffline.collectAsStateWithLifecycle()
    val currentProvider by MediaProviderManager.currentProvider.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.settings_downloads)) },
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
        Box(
            modifier = Modifier
                .padding(
                    top = innerPadding.calculateTopPadding()
                )
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .dialogFocusable()
        ) {
            Column(
                Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Downloaded in the app
                Column(
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    SettingsDialogButton(
                        settingsName = stringResource(R.string.downloads_storage),
                        settingsSubtitle = downloadedSongsLabel(state),
                        settingsIcon = ImageVector.vectorResource(R.drawable.rounded_download_24),
                        settingsDescription = stringResource(R.string.downloads_storage_description),
                        toggleEvent = { if (state.isDownloading) smartCacheViewModel.cancelDownloads() }
                    )

                    SettingsDialogButton(
                        settingsName = stringResource(R.string.downloads_browse),
                        settingsSubtitle = "",
                        settingsIcon = ImageVector.vectorResource(R.drawable.rounded_library_music_24),
                        settingsDescription = stringResource(R.string.downloads_browse_description),
                        toggleEvent = {
                            navHostController.navigate(Screen.S_Downloaded_Content) {
                                launchSingleTop = true
                            }
                        }
                    )

                    SettingsDialogButton(
                        settingsName = stringResource(R.string.downloads_delete_all),
                        settingsSubtitle = "",
                        settingsIcon = ImageVector.vectorResource(R.drawable.delete_sweep_24px),
                        settingsDescription = stringResource(R.string.downloads_delete_all_description),
                        enabled = state.downloadedSongCount > 0 || state.isDownloading,
                        toggleEvent = { showDeleteAllDialog = true }
                    )
                }

                // Smart cache
                Column(
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    SettingsDialogButton(
                        settingsName = stringResource(R.string.smart_cache_size),
                        settingsSubtitle = if (smartCacheMaxSizeGb > 0)
                            stringResource(
                                R.string.smart_cache_usage,
                                Formatter.formatShortFileSize(context, state.usedBytes),
                                smartCacheSizeLabel(smartCacheMaxSizeGb),
                                state.songCount
                            )
                        else
                            smartCacheSizeLabel(0),
                        settingsIcon = ImageVector.vectorResource(R.drawable.rounded_storage_24),
                        settingsDescription = stringResource(R.string.smart_cache_size_description),
                        toggleEvent = { showSmartCacheSizeDialog = true }
                    )

                    SettingsDialogButton(
                        settingsName = stringResource(R.string.smart_cache_refresh),
                        settingsSubtitle = when {
                            state.isRunning -> stringResource(
                                R.string.smart_cache_tap_to_cancel,
                                state.statusText(context.resources)
                            )
                            !smartCacheSupported -> stringResource(R.string.smart_cache_error_unsupported)
                            else -> state.statusText(context.resources)
                        },
                        settingsIcon = ImageVector.vectorResource(R.drawable.rounded_refresh_24),
                        enabled = state.isRunning ||
                                (smartCacheMaxSizeGb > 0 && smartCacheSupported && !isOffline),
                        toggleEvent = { smartCacheViewModel.toggleRefresh() }
                    )
                }

                // Saved to the device
                Column(
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val downloadTemplate by MiscSettingsManager(context).downloadTemplateFlow.collectAsState("{album}/{title} - {artist}.{ext}")

                    SettingsDialogButton(
                        settingsName = stringResource(R.string.misc_download_template),
                        settingsDescription = stringResource(R.string.misc_download_template_summary),
                        settingsSubtitle = downloadTemplate,
                        settingsIcon = ImageVector.vectorResource(R.drawable.rounded_download_24),
                        toggleEvent = { showDownloadTemplateDialog = true },
                        enabled = currentProvider != null
                    )

                    val playlistDownloadTemplate by MiscSettingsManager(context).playlistDownloadTemplateFlow.collectAsState("{playlist}/{playlist_index}. {title} - {artist}.{ext}")

                    SettingsDialogButton(
                        settingsName = stringResource(R.string.misc_playlist_download_template),
                        settingsDescription = stringResource(R.string.misc_playlist_download_template_summary),
                        settingsSubtitle = playlistDownloadTemplate,
                        settingsIcon = ImageVector.vectorResource(R.drawable.rounded_download_24),
                        toggleEvent = { showPlaylistDownloadTemplateDialog = true },
                        enabled = currentProvider != null
                    )
                }
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
        if (showDownloadTemplateDialog)
            DownloadTemplateDialog(
                onDismissRequest = {
                    showDownloadTemplateDialog = false
                },
                onConfirm = {
                    coroutineScope.launch {
                        MiscSettingsManager(context).setDownloadTemplate(it)
                    }
                },
            )
        if (showPlaylistDownloadTemplateDialog)
            DownloadTemplateDialog(
                onDismissRequest = {
                    showPlaylistDownloadTemplateDialog = false
                },
                onConfirm = {
                    coroutineScope.launch {
                        MiscSettingsManager(context).setPlaylistDownloadTemplate(it)
                    }
                },
                template = MiscSettingsManager(context).playlistDownloadTemplateFlow,
                R.string.misc_playlist_download_template,
                R.string.misc_playlist_download_template_description
            )
    }
}

/** The space and songs downloaded in the app, with the progress of the downloads. */
@Composable
fun downloadedSongsLabel(state: SmartCacheState): String {
    val context = LocalContext.current
    val storage = stringResource(
        R.string.downloads_storage_value,
        Formatter.formatShortFileSize(context, state.downloadedBytes),
        state.downloadedSongCount
    )
    val status = state.downloadStatusText(context.resources) ?: return storage
    return if (state.isDownloading) stringResource(R.string.smart_cache_tap_to_cancel, "$storage · $status")
        else "$storage · $status"
}
