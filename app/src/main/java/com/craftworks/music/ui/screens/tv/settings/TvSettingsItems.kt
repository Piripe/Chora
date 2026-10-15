package com.craftworks.music.ui.screens.tv.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text


@Composable
fun SettingsButtonItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    enabled: Boolean = true,
    description: String? = null,
    onClick: () -> Unit
) {
    ListItem(
        selected = false,
        enabled = enabled,
        headlineContent = { Text(title) },
        supportingContent = {
            Column {
                Text(subtitle)
                description?.let { SettingsDescription(it) }
            }
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
        },
        onClick = onClick,
    )
}

@Composable
fun SettingsSwitchItem(
    title: String,
    icon: ImageVector? = null,
    checked: Boolean,
    enabled: Boolean = true,
    description: String? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        selected = false,
        enabled = enabled,
        headlineContent = { Text(text = title) },
        supportingContent = description?.let { { SettingsDescription(it) } },
        leadingContent = icon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
            }
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = true,
                modifier = Modifier.focusable(false)
            )
        },
        onClick = { onCheckedChange(!checked) },
    )
}
/**
 * A setting with a few exclusive values shown as icons, without a dialog.
 * OK cycles through the values and D-pad left/right steps between them.
 */
@Composable
fun <T> SettingsIconChoiceItem(
    title: String,
    icon: ImageVector,
    options: List<T>,
    selected: T,
    optionIcon: @Composable (T) -> ImageVector,
    optionLabel: @Composable (T) -> String,
    description: String? = null,
    onSelect: (T) -> Unit
) {
    val index = options.indexOf(selected).coerceAtLeast(0)
    ListItem(
        selected = false,
        headlineContent = { Text(title) },
        supportingContent = {
            Column {
                Text(optionLabel(selected))
                description?.let { SettingsDescription(it) }
            }
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
        },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                options.forEach { option ->
                    val isSelected = option == selected
                    Icon(
                        imageVector = optionIcon(option),
                        contentDescription = optionLabel(option),
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else LocalContentColor.current.copy(alpha = 0.6f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .padding(4.dp)
                            .size(ButtonDefaults.IconSize)
                    )
                }
            }
        },
        onClick = { onSelect(options[(index + 1) % options.size]) },
        modifier = Modifier.onPreviewKeyEvent { keyEvent ->
            if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (keyEvent.key) {
                // At either end, let the key move the focus as usual.
                Key.DirectionLeft if index > 0 -> {
                    onSelect(options[index - 1]); true
                }
                Key.DirectionRight if index < options.lastIndex -> {
                    onSelect(options[index + 1]); true
                }
                else -> false
            }
        }
    )
}

/** Short explanation of a setting, shown under its title or value. Wraps over several lines. */
@Composable
fun SettingsDescription(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = LocalContentColor.current.copy(alpha = 0.8f),
        modifier = modifier
    )
}
