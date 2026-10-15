package com.craftworks.music.ui.elements.dialogs

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.craftworks.music.R
import com.craftworks.music.data.model.ShareExpiration
import com.craftworks.music.data.providers.media.MediaProvider
import com.craftworks.music.ui.viewmodels.ShareViewModel
import com.craftworks.music.utils.ShareUtils
import kotlinx.coroutines.launch

/**
 * Creates a public link to the items with [ids] on [provider] (songs, or an album, playlist or artist),
 * then lets the user copy or share it. [title] is the default description of the link.
 */
@Composable
fun ShareDialog(
    provider: MediaProvider?,
    ids: List<String>,
    title: String,
    onDismissRequest: () -> Unit,
    viewModel: ShareViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var description by remember { mutableStateOf(title) }
    var expiration by remember { mutableStateOf(ShareExpiration.NEVER) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf<String?>(null) }

    fun createLink() {
        if (provider == null || loading) return
        loading = true
        failed = false
        coroutineScope.launch {
            url = viewModel.share(provider, ids, description, expiration)
            failed = url == null
            loading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.rounded_share_24),
                contentDescription = null
            )
        },
        title = { Text(stringResource(R.string.share_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val link = url
                if (link != null) {
                    OutlinedTextField(
                        value = link,
                        onValueChange = { },
                        readOnly = true,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(stringResource(R.string.share_description)) },
                        enabled = !loading,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = stringResource(R.string.share_expires),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ShareExpiration.entries.forEach { entry ->
                            FilterChip(
                                selected = entry == expiration,
                                onClick = { expiration = entry },
                                enabled = !loading,
                                label = { Text(stringResource(entry.label)) }
                            )
                        }
                    }
                    if (failed) {
                        Text(
                            text = stringResource(R.string.share_error),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            val link = url
            if (link != null) {
                Button(onClick = {
                    ShareUtils.shareLink(context, link, description)
                    onDismissRequest()
                }) {
                    Text(stringResource(R.string.action_share))
                }
            } else {
                Button(onClick = { createLink() }, enabled = provider != null && !loading) {
                    if (loading) {
                        Box(contentAlignment = Alignment.Center) {
                            // Keeps the button size while loading.
                            Text(stringResource(R.string.share_create_link), color = MaterialTheme.colorScheme.primary.copy(alpha = 0f))
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    } else {
                        Text(stringResource(R.string.share_create_link))
                    }
                }
            }
        },
        dismissButton = {
            val link = url
            if (link != null) {
                TextButton(onClick = {
                    ShareUtils.copyLink(context, link, description)
                    onDismissRequest()
                }) {
                    Text(stringResource(R.string.share_copy_link))
                }
            } else {
                TextButton(onClick = onDismissRequest) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    )
}
