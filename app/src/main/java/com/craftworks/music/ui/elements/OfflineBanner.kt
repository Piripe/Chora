package com.craftworks.music.ui.elements

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.craftworks.music.R

private val BannerHeight = 28.dp

/**
 * Shows an "Offline mode" banner at the top of the app above [content].
 *
 * Screens already pad themselves for the status bar, so instead of stacking the banner and the
 * content (which would double that padding), the content is only pushed down by the banner's own
 * height and the banner is drawn over the content's status bar area.
 */
@Composable
fun OfflineBannerHost(
    isOffline: Boolean,
    isTv: Boolean,
    content: @Composable () -> Unit
) {
    val contentOffset by animateDpAsState(if (isOffline) BannerHeight else 0.dp, label = "offlineBannerOffset")

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().padding(top = contentOffset)) {
            content()
        }

        AnimatedVisibility(
            visible = isOffline,
            enter = expandVertically(),
            exit = shrinkVertically(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            OfflineBanner(isTv)
        }
    }
}

@Composable
private fun OfflineBanner(isTv: Boolean) {
    val containerColor: Color
    val contentColor: Color
    if (isTv) {
        containerColor = androidx.tv.material3.MaterialTheme.colorScheme.errorContainer
        contentColor = androidx.tv.material3.MaterialTheme.colorScheme.onErrorContainer
    } else {
        containerColor = MaterialTheme.colorScheme.errorContainer
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    }

    Box(
        Modifier
            .fillMaxWidth()
            .background(containerColor)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(BannerHeight),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.cloud_off_24px),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.padding(end = 8.dp).size(16.dp)
            )
            Text(
                text = stringResource(R.string.label_offline_mode),
                color = contentColor,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
