package com.craftworks.music.ui.elements.dialogs.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.craftworks.music.R
import com.craftworks.music.data.model.ShareExpiration
import com.craftworks.music.data.providers.media.MediaProvider
import com.craftworks.music.ui.viewmodels.ShareViewModel
import com.craftworks.music.utils.ShareUtils
import kotlinx.coroutines.launch

/**
 * Creates a public link to the items with [ids] on [provider], once an expiration is picked, and shows it.
 * [title] is the description of the link.
 */
@Composable
fun TvShareMenu(
    provider: MediaProvider?,
    ids: List<String>,
    title: String,
    onDismiss: () -> Unit,
    viewModel: ShareViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.widthIn(max = 320.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val link = url
        when {
            link != null -> {
                // TVs often can't share or paste, so the link is shown to be typed or read elsewhere.
                Text(
                    text = link,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                ListItem(
                    selected = false,
                    headlineContent = { Text(stringResource(R.string.action_share)) },
                    onClick = { ShareUtils.shareLink(context, link, title) }
                )
                ListItem(
                    selected = false,
                    headlineContent = { Text(stringResource(R.string.action_close)) },
                    onClick = onDismiss
                )
            }
            loading -> CircularProgressIndicator(modifier = Modifier.size(32.dp))
            else -> {
                if (failed) {
                    Text(
                        text = stringResource(R.string.share_error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                ShareExpiration.entries.forEach { expiration ->
                    ListItem(
                        selected = false,
                        enabled = provider != null,
                        overlineContent = { Text(stringResource(R.string.share_expires)) },
                        headlineContent = { Text(stringResource(expiration.label)) },
                        onClick = {
                            if (provider != null) {
                                loading = true
                                coroutineScope.launch {
                                    url = viewModel.share(provider, ids, title, expiration)
                                    failed = url == null
                                    loading = false
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
