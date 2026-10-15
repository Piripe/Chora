package com.craftworks.music.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.craftworks.music.R

@Composable
fun SettingsSwitch(
    selected: Boolean,
    settingsName: String,
    settingsIcon: ImageVector,
    toggleEvent: () -> Unit = {},
    enabled: Boolean = true,
    settingsDescription: String? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceBright)
            .selectable(
                selected = selected,
                onClick = toggleEvent,
                role = Role.Switch,
                enabled = enabled
            )
    ) {
        Icon(
            imageVector = settingsIcon,
            contentDescription = "Settings Icon",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.5f),
            modifier = Modifier
                .padding(start = 20.dp, end = 16.dp)
                .size(32.dp)
        )
        SettingsLabel(
            name = settingsName,
            description = settingsDescription,
            enabled = enabled,
            modifier = Modifier
                .padding(vertical = if (settingsDescription == null) 20.dp else 12.dp)
                .weight(1f)
        )

        Switch(
            checked = selected,
            onCheckedChange = { toggleEvent() },
            enabled = enabled,
            modifier = Modifier.padding(start = 12.dp, end = 20.dp)
        )
    }
}

/** Title of a setting, with an optional value line and a short description that wraps. */
@Composable
private fun SettingsLabel(
    name: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    description: String? = null,
    enabled: Boolean = true
) {
    val alpha = if (enabled) 1f else 0.5f
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = alpha),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start
        )
        if (!value.isNullOrEmpty())
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        if (description != null)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f * alpha),
            )
    }
}

@Composable
fun SettingsDialogButton(
    settingsName: String,
    settingsSubtitle: String,
    settingsIcon: ImageVector,
    enabled: Boolean? = true,
    settingsDescription: String? = null,
    toggleEvent: () -> Unit = {}
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (enabled == true)
                    MaterialTheme.colorScheme.surfaceBright
                else
                    MaterialTheme.colorScheme.surfaceContainerLow
            )
            .fillMaxWidth()
            .clickable(enabled = enabled == true) {
                toggleEvent()
            }
            .focusProperties { left = FocusRequester.Cancel }
    ) {
        Icon(
            imageVector = settingsIcon,
            contentDescription = "Settings Icon",
            tint = if (enabled == true)
                MaterialTheme.colorScheme.onSurfaceVariant
            else
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier
                .padding(start = 20.dp, end = 16.dp)
                .size(32.dp)
        )
        SettingsLabel(
            name = settingsName,
            value = settingsSubtitle,
            description = settingsDescription,
            enabled = enabled == true,
            modifier = Modifier
                .padding(top = 10.dp, bottom = 10.dp, end = 20.dp)
                .weight(1f)
        )
    }
}

/** A setting with a few exclusive values, picked directly from icon buttons instead of a dialog. */
@Composable
fun <T> SettingsIconChoice(
    settingsName: String,
    settingsIcon: ImageVector,
    options: List<T>,
    selected: T,
    optionIcon: @Composable (T) -> ImageVector,
    optionLabel: @Composable (T) -> String,
    settingsDescription: String? = null,
    onSelect: (T) -> Unit = {}
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceBright)
            .fillMaxWidth()
    ) {
        Icon(
            imageVector = settingsIcon,
            contentDescription = "Settings Icon",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = 20.dp, end = 16.dp)
                .size(32.dp)
        )
        SettingsLabel(
            name = settingsName,
            value = optionLabel(selected),
            description = settingsDescription,
            modifier = Modifier
                .padding(vertical = 10.dp)
                .weight(1f)
        )
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.padding(start = 12.dp, end = 20.dp)
        ) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    onClick = { onSelect(option) },
                    selected = selected == option,
                    icon = {},
                    label = {
                        Icon(
                            imageVector = optionIcon(option),
                            contentDescription = optionLabel(option),
                            modifier = Modifier.size(SegmentedButtonDefaults.IconSize)
                        )
                    }
                )
            }
        }
    }
}

@Composable
@Preview(showSystemUi = true, showBackground = true)
fun preview() {
    Column(
        Modifier.background(MaterialTheme.colorScheme.surfaceContainer).fillMaxSize()
    ) {
        SettingsDialogButton(
            settingsName = stringResource(R.string.playback_transcoding_format),
            settingsSubtitle = "transcodingFormat",
            settingsIcon = ImageVector.vectorResource(R.drawable.s_p_transcoding),
            enabled = true,
            toggleEvent = {  }
        )

        SettingsDialogButton(
            settingsName = stringResource(R.string.playback_transcoding_format),
            settingsSubtitle = "transcodingFormat",
            settingsIcon = ImageVector.vectorResource(R.drawable.s_p_transcoding),
            enabled = false,
            toggleEvent = {  }
        )
    }
}

@Composable
fun SettingsSlider(
    settingsName: String,
    steps: Int,
    value: Float,
    minValue: Float, maxValue: Float,
    settingsDescription: String? = null,
    onValueChange: (newValue: Float) -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val stepSize = (maxValue - minValue) / (steps + 1)

    Column (
        modifier = Modifier
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surfaceBright)
    ) {
        SettingsLabel(
            name = settingsName,
            description = settingsDescription,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .padding(top = 10.dp)
        )
        Slider(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 10.dp)
                .onKeyEvent { keyEvent ->
                    when (keyEvent.key) {
                        Key.DirectionRight if keyEvent.type == KeyEventType.KeyDown -> {
                            onValueChange((value + stepSize).coerceAtMost(maxValue))
                            true
                        }
                        Key.DirectionLeft if keyEvent.type == KeyEventType.KeyDown -> {
                            onValueChange((value - stepSize).coerceAtLeast(minValue))
                            true
                        }
                        else -> false
                    }
                },
            interactionSource = interactionSource,
            value = value,
            steps = steps,
            onValueChange = {
                onValueChange(it)
            },
            valueRange = minValue..maxValue
        )
    }
}
