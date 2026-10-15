package com.craftworks.music.ui.elements.dialogs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.craftworks.music.R
import com.craftworks.music.data.model.DownloadTarget

/** Asks whether songs are downloaded in the app (to play offline) or saved as files on the device. */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Preview
@Composable
fun DownloadTargetDialog(
    onDismissRequest: () -> Unit = {},
    onSelect: (DownloadTarget) -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.rounded_download_24),
                contentDescription = null
            )
        },
        title = { Text(stringResource(R.string.download_target_title)) },
        text = {
            Column(
                modifier = Modifier.dialogFocusable(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DownloadTargetOption(
                    icon = ImageVector.vectorResource(R.drawable.rounded_library_music_24),
                    title = stringResource(R.string.download_target_app),
                    description = stringResource(R.string.download_target_app_description),
                    onClick = {
                        onSelect(DownloadTarget.APP)
                        onDismissRequest()
                    }
                )
                DownloadTargetOption(
                    icon = ImageVector.vectorResource(R.drawable.rounded_storage_24),
                    title = stringResource(R.string.download_target_device),
                    description = stringResource(R.string.download_target_device_description),
                    onClick = {
                        onSelect(DownloadTarget.DEVICE)
                        onDismissRequest()
                    }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun DownloadTargetOption(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(end = 16.dp)
                .size(24.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Asks to confirm removing downloaded songs from the device. [text] may hold HTML. */
@Composable
fun RemoveDownloadsDialog(
    title: String,
    text: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = { Icon(imageVector = Icons.Rounded.Delete, contentDescription = null) },
        title = { Text(title) },
        text = { Text(AnnotatedString.fromHtml(text)) },
        confirmButton = {
            Button(onClick = {
                onConfirm()
                onDismissRequest()
            }) {
                Text(stringResource(R.string.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
