package com.craftworks.music.ui.elements

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.craftworks.music.R
import com.craftworks.music.data.model.ProviderFeatures
import kotlinx.coroutines.launch
import kotlin.experimental.and
import kotlin.experimental.or


@OptIn(ExperimentalMaterial3Api::class)
@Stable
@Composable
fun SongListActionButtons(
    buttons: List<ActionButton>,
    providerFeatures: ProviderFeatures,
    playAction: () -> Unit,
    isStarred: Boolean = false
) {
    val rowButtons = buttons.filter { !it.inMenu && it.isCompatible(providerFeatures) }
    val menuButtons = buttons.filter { it.inMenu && it.isCompatible(providerFeatures) }

    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    val interactionSources =
        remember { List(rowButtons.size + 2) { MutableInteractionSource() } }

    ButtonGroup(
        overflowIndicator = { menuState ->
            FilledTonalIconButton(
                onClick = { menuState.show() },
                modifier = Modifier.height(64.dp)
            ) {
                Icon(
                    Icons.Outlined.MoreVert,
                    contentDescription = "More" // TODO : Translate
                )
            }
        },
        modifier = Modifier
            .height(64.dp)
            .widthIn(max = 640.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // More
        customItem(
            buttonGroupContent = {
                FilledTonalIconButton(
                    onClick = { showBottomSheet = true },
                    interactionSource = interactionSources[rowButtons.size + 1],
                    modifier = Modifier
                        .height(64.dp)
                        .animateWidth(interactionSources[rowButtons.size + 1]),
                ) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        contentDescription = "More" // TODO : Translate
                    )
                }
            },
            menuContent = { showBottomSheet = true },
        )

        fun rowButton(button: ActionButton, interactionSource: MutableInteractionSource) : Unit {
            when(button.type) {
                ActionButtonType.SEPARATOR -> {}
                ActionButtonType.FAVORITE -> {
                    customItem(
                        buttonGroupContent = {
                            val cornerRadius by animateDpAsState(
                                targetValue = if (isStarred) 12.dp else 32.dp,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessLow
                                ),
                                label = "Star Button Shape Animation"
                            )

                            FilledTonalIconButton(
                                onClick = button.onClick,
                                interactionSource = interactionSource,
                                modifier = Modifier
                                    .size(64.dp)
                                    .animateWidth(interactionSource),
                                shape = RoundedCornerShape(cornerRadius)
                            ) {
                                Crossfade(
                                    targetState = isStarred
                                ) {
                                    if (it) Icon(
                                        imageVector = ImageVector.vectorResource(R.drawable.round_favorite_24),
                                        contentDescription = stringResource(R.string.action_remove_from_favorites)
                                    )
                                    else
                                        Icon(
                                            imageVector = ImageVector.vectorResource(R.drawable.round_favorite_border_24),
                                            contentDescription = stringResource(R.string.action_add_to_favorites)
                                        )
                                }
                            }
                        },
                        menuContent = {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (isStarred) stringResource(R.string.action_remove_from_favorites)
                                        else stringResource(R.string.action_add_to_favorites)
                                    )
                                },
                                leadingIcon = {
                                    Crossfade(
                                        targetState = isStarred
                                    ) {
                                        if (it) Icon(
                                            imageVector = ImageVector.vectorResource(R.drawable.round_favorite_24),
                                            contentDescription = stringResource(R.string.action_remove_from_favorites)
                                        )
                                        else
                                            Icon(
                                                imageVector = ImageVector.vectorResource(R.drawable.round_favorite_border_24),
                                                contentDescription = stringResource(R.string.action_add_to_favorites)
                                            )
                                    }
                                },
                                onClick = button.onClick
                            )
                        },
                    )
                }
                else -> {
                    customItem(
                        buttonGroupContent = {
                            val (icon, text) = getActionButtonIconText(button.type)
                            FilledTonalIconButton(
                                onClick = button.onClick,
                                interactionSource = interactionSource,
                                modifier = Modifier
                                    .size(64.dp)
                                    .animateWidth(interactionSource),
                                shape = CircleShape
                            ) {
                                Icon(
                                    icon,
                                    contentDescription = text
                                )
                            }
                        },
                        menuContent = {
                            val (icon, text) = getActionButtonIconText(button.type)
                            DropdownMenuItem(
                                text = { Text(text) },
                                leadingIcon = { Icon(icon, contentDescription = text) },
                                onClick = button.onClick
                            )
                        },
                    )
                }
            }
        }

        for ((index, button) in rowButtons.withIndex()) {
            rowButton(button, interactionSources[index + 1])
        }



        // Play pill — filled, primary
        customItem(
            buttonGroupContent = {
                var isCompact by remember { mutableStateOf(false) }
                val density = LocalDensity.current
                val compactThresholdPx = remember(density) { with(density) { 128.dp.roundToPx() } }

                Button(
                    onClick = playAction, // event callback — launch happens here, not during composition
                    interactionSource = interactionSources[0],
                    modifier = Modifier
                        .height(64.dp)
                        .weight(0.5f)
                        .animateWidth(interactionSources[0])
                        .onSizeChanged { size -> isCompact = size.width < compactThresholdPx },
                    shape = RoundedCornerShape(32.dp),
                    contentPadding = if (isCompact) PaddingValues(0.dp) else ButtonDefaults.ContentPadding
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.action_play))
                    if(!isCompact) {
                        Row (verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.action_play))
                        }
                    }
                }
            },
            menuContent = {
                DropdownMenuItem(
                    text = { Text("Play") }, // TODO : Translate
                    leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                    onClick = playAction
                )
            },
        )
    }

    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState
        ) {
            // Content inside the bottom sheet
            Column(
                modifier = Modifier
                    .fillMaxWidth()
            ) {

                @Composable
                fun menuButton(button: ActionButton) {
                    when(button.type) {
                        ActionButtonType.SEPARATOR -> HorizontalDivider()
                        ActionButtonType.FAVORITE -> {
                            DropdownMenuItem(
                                onClick = {
                                    scope.launch { sheetState.hide() }
                                    button.onClick()
                                },
                                modifier = Modifier
                                    .padding(horizontal = 12.dp)
                                    .fillMaxWidth(),
                                leadingIcon = {
                                    Crossfade(
                                        targetState = isStarred
                                    ) {
                                        if (it) Icon(
                                            imageVector = ImageVector.vectorResource(
                                                R.drawable.round_favorite_24
                                            ),
                                            contentDescription = stringResource(
                                                R.string.action_remove_from_favorites
                                            )
                                        )
                                        else
                                            Icon(
                                                imageVector = ImageVector.vectorResource(
                                                    R.drawable.round_favorite_border_24
                                                ),
                                                contentDescription = stringResource(
                                                    R.string.action_add_to_favorites
                                                )
                                            )
                                    }
                                },
                                text = {
                                    Text(
                                        if (isStarred) stringResource(R.string.action_remove_from_favorites) else stringResource(
                                            R.string.action_add_to_favorites
                                        )
                                    )
                                }
                            )
                        }
                        else -> {
                            val (icon, text) = getActionButtonIconText(button.type)
                            DropdownMenuItem(
                                onClick = {
                                    scope.launch { sheetState.hide() }
                                    button.onClick()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp),
                                leadingIcon = {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = text
                                    )
                                },
                                text = { Text(text) }
                            )
                        }
                    }
                }

                for (button in menuButtons) {
                    menuButton(button)
                }
            }
        }
    }
}

@Composable
fun getActionButtonIconText(type: ActionButtonType): Pair<ImageVector, String> =
    when (type) {
        ActionButtonType.SEPARATOR -> ImageVector.vectorResource(R.drawable.horizontal_rule_24px) to "Separator" // TODO : Translate
        ActionButtonType.SHUFFLE -> ImageVector.vectorResource(R.drawable.round_shuffle_28) to stringResource(R.string.action_shuffle)
        ActionButtonType.FAVORITE -> ImageVector.vectorResource(R.drawable.round_favorite_24) to stringResource(R.string.action_add_to_favorites)
        ActionButtonType.ADD_TO_QUEUE -> ImageVector.vectorResource(R.drawable.outline_queue_add_24) to stringResource(R.string.action_add_to_queue)
        ActionButtonType.PLAY_NEXT -> ImageVector.vectorResource(R.drawable.play_next_24px) to stringResource(R.string.action_play_next)
        ActionButtonType.ADD_TO_PLAYLIST -> ImageVector.vectorResource(R.drawable.rounded_add_24) to stringResource(R.string.action_add_to_playlist)
        ActionButtonType.DOWNLOAD -> ImageVector.vectorResource(R.drawable.rounded_download_24) to stringResource(R.string.action_download)
        else -> ImageVector.vectorResource(R.drawable.placeholder) to ""
    }

@JvmInline
value class ActionButtonType(val id: Byte) {
    companion object {
        val SEPARATOR = ActionButtonType(0)
        val SHUFFLE = ActionButtonType(1)
        val FAVORITE = ActionButtonType(2)
        val ADD_TO_QUEUE = ActionButtonType(3)
        val PLAY_NEXT = ActionButtonType(4)
        val ADD_TO_PLAYLIST = ActionButtonType(5)
        val DOWNLOAD = ActionButtonType(6)
    }
}

data class ActionButton(
    var type: ActionButtonType,
    var inMenu: Boolean,
    var onClick: () -> Unit = {}
) {
    fun isCompatible(flags: ProviderFeatures) : Boolean =
        when (type) {
            ActionButtonType.FAVORITE -> flags.has(ProviderFeatures.FAVORITES)
            ActionButtonType.ADD_TO_PLAYLIST -> flags.has(ProviderFeatures.PLAYLIST)
            ActionButtonType.DOWNLOAD -> flags.has(ProviderFeatures.DOWNLOADS)
            else -> true
        }

    fun toByte() : Byte = type.id or (if (inMenu) -0x80 else 0)

    companion object {
        fun fromByte(value: Byte) = ActionButton(ActionButtonType(value and 0x7f), value < 0)
    }
}