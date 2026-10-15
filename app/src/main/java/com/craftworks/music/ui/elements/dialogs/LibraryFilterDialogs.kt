package com.craftworks.music.ui.elements.dialogs

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.onFocusChanged
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.craftworks.music.data.model.FilterEntity
import com.craftworks.music.ui.elements.ActionTooltip
import com.craftworks.music.ui.viewmodels.FilterSearchViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.craftworks.music.R
import com.craftworks.music.data.model.FilterField
import com.craftworks.music.data.model.FilterMatch
import com.craftworks.music.data.model.FilterMode
import com.craftworks.music.data.model.FilterOperator
import com.craftworks.music.data.model.FilterRule
import com.craftworks.music.data.model.FilterValueType
import com.craftworks.music.data.model.LibraryFilter
import com.craftworks.music.data.model.SimpleFilter
import com.craftworks.music.data.model.SimpleFilterOption
import com.craftworks.music.data.model.SortOrder
import kotlin.math.roundToInt

/**
 * Sort and Filter buttons shown in the Songs, Albums and Artists top bars when the
 * "Advanced Filter" setting is enabled. They replace the sort order and sort by buttons.
 */
@Composable
fun <T> AdvancedFilterButtons(
    sorts: List<T>,
    sortLabels: Map<T, Int>,
    sort: T,
    sortOrder: SortOrder,
    supportSortOrder: Boolean,
    onSort: (T, SortOrder) -> Unit,
    filter: LibraryFilter,
    filterFields: List<FilterField>,
    simpleFilterOptions: List<SimpleFilterOption>,
    onFilter: (LibraryFilter) -> Unit
) {
    var showSortDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }

    if (sorts.size > 1 || supportSortOrder) {
        ActionTooltip(stringResource(R.string.button_sort_by)) {
            IconButton(onClick = { showSortDialog = true }) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.rounded_sort_24),
                    contentDescription = stringResource(R.string.button_sort_by),
                )
            }
        }
    }

    ActionTooltip(stringResource(R.string.button_filter)) {
        IconButton(onClick = { showFilterDialog = true }) {
            val ruleCount = filter.activeRules.size
            BadgedBox(
                badge = { if (ruleCount > 0) Badge { Text(ruleCount.toString()) } }
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.filter_list_24px),
                    contentDescription = stringResource(R.string.button_filter),
                )
            }
        }
    }

    if (showSortDialog) {
        SortDialog(
            title = R.string.button_sort_by,
            sorts = sorts,
            sortLabels = sortLabels,
            initialSort = sort,
            initialOrder = sortOrder,
            showOrder = supportSortOrder,
            onDismissRequest = { showSortDialog = false },
            onSort = { order, newSort -> onSort(newSort, order) }
        )
    }

    if (showFilterDialog) {
        LibraryFilterDialog(
            filter = filter,
            fields = filterFields,
            simpleOptions = simpleFilterOptions,
            onDismissRequest = { showFilterDialog = false },
            onApply = onFilter
        )
    }
}

/** Sort order and sort field picker. Also used to sort the queue. */
@Composable
fun <T> SortDialog(
    @StringRes title: Int,
    sorts: List<T>,
    sortLabels: Map<T, Int>,
    initialSort: T,
    initialOrder: SortOrder = SortOrder.ASC,
    showOrder: Boolean = true,
    @StringRes confirmText: Int = R.string.sort_queue_sort,
    onDismissRequest: () -> Unit,
    onSort: (SortOrder, T) -> Unit
) {
    var selectedOrder by remember { mutableStateOf(initialOrder) }
    var selectedSort by remember { mutableStateOf(initialSort) }

    AlertDialog(
        title = {
            Text(text = stringResource(title))
        },
        text = {
            Column {
                if (showOrder) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        listOf(
                            Triple(SortOrder.ASC, R.drawable.arrow_upward_24px, R.string.button_sort_order_ascending),
                            Triple(SortOrder.DESC, R.drawable.arrow_downward_24px, R.string.button_sort_order_descending)
                        ).forEachIndexed { index, (order, icon, label) ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                                onClick = { selectedOrder = order },
                                selected = selectedOrder == order,
                                icon = {
                                    SegmentedButtonDefaults.Icon(active = selectedOrder == order) {
                                        Icon(
                                            ImageVector.vectorResource(icon),
                                            contentDescription = null,
                                            modifier = Modifier.size(SegmentedButtonDefaults.IconSize)
                                        )
                                    }
                                },
                                label = { Text(stringResource(label)) }
                            )
                        }
                    }
                }

                LazyColumn {
                    items(items = sorts, key = { it.toString() }) { sort ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .selectable(
                                    selected = (sort == selectedSort),
                                    onClick = { selectedSort = sort },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (sort == selectedSort),
                                onClick = null
                            )
                            Text(
                                text = sortLabels[sort]?.let { stringResource(it) } ?: sort.toString(),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSort(selectedOrder, selectedSort)
                    onDismissRequest()
                }
            ) {
                Text(stringResource(confirmText))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        onDismissRequest = onDismissRequest
    )
}

/** Editor for a [LibraryFilter]: basic parameters in Simple mode, rules (inspired by Feishin) in Advanced mode. */
@Composable
fun LibraryFilterDialog(
    filter: LibraryFilter,
    fields: List<FilterField>,
    simpleOptions: List<SimpleFilterOption>,
    onDismissRequest: () -> Unit,
    onApply: (LibraryFilter) -> Unit,
    searchViewModel: FilterSearchViewModel = hiltViewModel()
) {
    var mode by remember { mutableStateOf(filter.mode) }
    var simple by remember { mutableStateOf(filter.simple) }
    var match by remember { mutableStateOf(filter.match) }
    val rules = remember { mutableStateListOf<FilterRule>().apply { addAll(filter.rules.filter { it.field in fields }) } }

    AlertDialog(
        title = { Text(stringResource(R.string.filter_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    FilterMode.entries.forEachIndexed { index, entry ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = FilterMode.entries.size),
                            onClick = { mode = entry },
                            selected = mode == entry,
                            label = { Text(stringResource(entry.label)) }
                        )
                    }
                }

                if (mode == FilterMode.SIMPLE) {
                    SimpleFilterEditor(
                        filter = simple,
                        options = simpleOptions,
                        searchArtists = searchViewModel::searchArtists,
                        searchAlbums = searchViewModel::searchAlbums,
                        searchGenres = searchViewModel::searchGenres,
                        onChange = { simple = it }
                    )
                } else {
                    Text(
                        text = stringResource(R.string.filter_match_label),
                        style = MaterialTheme.typography.labelLarge
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        FilterMatch.entries.forEachIndexed { index, entry ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = FilterMatch.entries.size),
                                onClick = { match = entry },
                                selected = match == entry,
                                label = { Text(stringResource(entry.label)) }
                            )
                        }
                    }

                    if (rules.isEmpty()) {
                        Text(
                            text = stringResource(R.string.filter_no_rules),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    rules.forEachIndexed { index, rule ->
                        FilterRuleEditor(
                            rule = rule,
                            fields = fields,
                            onChange = { rules[index] = it },
                            onRemove = { rules.removeAt(index) }
                        )
                    }

                    TextButton(onClick = { rules.add(FilterRule(fields.first())) }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text(
                            text = stringResource(R.string.filter_add_rule),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onApply(LibraryFilter(mode, simple, match, rules.toList()))
                    onDismissRequest()
                }
            ) {
                Text(stringResource(R.string.filter_apply))
            }
        },
        dismissButton = {
            Row {
                // Only clears the current mode, the other one is kept.
                TextButton(
                    onClick = {
                        onApply(
                            if (mode == FilterMode.SIMPLE) LibraryFilter(mode, SimpleFilter(), match, rules.toList())
                            else LibraryFilter(mode, simple, match)
                        )
                        onDismissRequest()
                    }
                ) {
                    Text(stringResource(R.string.filter_clear))
                }
                TextButton(onClick = onDismissRequest) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        },
        onDismissRequest = onDismissRequest
    )
}

@Composable
private fun SimpleFilterEditor(
    filter: SimpleFilter,
    options: List<SimpleFilterOption>,
    searchArtists: suspend (String) -> List<FilterEntity>,
    searchAlbums: suspend (String) -> List<FilterEntity>,
    searchGenres: suspend (String) -> List<FilterEntity>,
    onChange: (SimpleFilter) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        options.forEach { option ->
            when (option) {
                SimpleFilterOption.FAVORITE ->
                    TriStateFilter(option.label, filter.favorite) { onChange(filter.copy(favorite = it)) }
                SimpleFilterOption.RATING ->
                    RatingRangeFilter(option.label, filter.minRating, filter.maxRating) { min, max ->
                        onChange(filter.copy(minRating = min, maxRating = max))
                    }
                SimpleFilterOption.COMPILATION ->
                    TriStateFilter(option.label, filter.compilation) { onChange(filter.copy(compilation = it)) }
                SimpleFilterOption.EXPLICIT ->
                    TriStateFilter(option.label, filter.explicit) { onChange(filter.copy(explicit = it)) }
                SimpleFilterOption.ARTIST ->
                    EntityPicker(option.label, R.string.filter_search_artists, filter.artists, searchArtists) {
                        onChange(filter.copy(artists = it))
                    }
                SimpleFilterOption.ALBUM_ARTIST ->
                    EntityPicker(option.label, R.string.filter_search_artists, filter.albumArtists, searchArtists) {
                        onChange(filter.copy(albumArtists = it))
                    }
                SimpleFilterOption.ALBUM ->
                    EntityPicker(option.label, R.string.filter_search_albums, filter.albums, searchAlbums) {
                        onChange(filter.copy(albums = it))
                    }
                SimpleFilterOption.GENRE ->
                    EntityPicker(option.label, R.string.filter_search_genres, filter.genres, searchGenres) {
                        onChange(filter.copy(genres = it))
                    }
                SimpleFilterOption.YEAR -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // When only one bound is set, the other one shows the implicit value used by the filter.
                    YearFilter(
                        R.string.filter_simple_year_from, filter.minYear,
                        implicitValue = SimpleFilter.MIN_YEAR.takeIf { filter.maxYear.isNotEmpty() },
                        modifier = Modifier.weight(1f)
                    ) {
                        onChange(filter.copy(minYear = it))
                    }
                    YearFilter(
                        R.string.filter_simple_year_to, filter.maxYear,
                        implicitValue = SimpleFilter.MAX_YEAR.takeIf { filter.minYear.isNotEmpty() },
                        modifier = Modifier.weight(1f)
                    ) {
                        onChange(filter.copy(maxYear = it))
                    }
                }
            }
        }
    }
}

/** Unset / Yes / No picker. */
@Composable
private fun TriStateFilter(@StringRes label: Int, value: Boolean?, onChange: (Boolean?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = stringResource(label), style = MaterialTheme.typography.labelLarge)
        val states = listOf(
            null to R.string.filter_simple_unset,
            true to R.string.filter_op_is_true,
            false to R.string.filter_op_is_false
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            states.forEachIndexed { index, (state, stateLabel) ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = states.size),
                    onClick = { onChange(state) },
                    selected = value == state,
                    label = { Text(stringResource(stateLabel)) }
                )
            }
        }
    }
}

/** Star range slider, 0 meaning unrated, with a few shortcuts for the common choices. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RatingRangeFilter(@StringRes label: Int, min: Int, max: Int, onChange: (Int, Int) -> Unit) {
    val maxRating = SimpleFilter.MAX_RATING
    val summary = when {
        min == 0 && max == maxRating -> stringResource(R.string.filter_rating_any)
        min == 0 && max == 0 -> stringResource(R.string.filter_rating_unrated)
        min == 1 && max == maxRating -> stringResource(R.string.filter_rating_rated)
        min == max -> stringResource(R.string.filter_rating_exactly, min)
        max == maxRating -> stringResource(R.string.filter_rating_at_least, min)
        min == 0 -> stringResource(R.string.filter_rating_up_to, max)
        else -> stringResource(R.string.filter_rating_range, min, max)
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        RangeSlider(
            value = min.toFloat()..max.toFloat(),
            onValueChange = { range ->
                onChange(range.start.roundToInt(), range.endInclusive.roundToInt())
            },
            valueRange = 0f..maxRating.toFloat(),
            steps = maxRating - 1
        )

        // Labels under each step of the slider.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "–",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            (1..maxRating).forEach { stars ->
                Text(
                    text = "$stars★",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple(0, maxRating, R.string.filter_rating_any),
                Triple(0, 0, R.string.filter_rating_unrated),
                Triple(1, maxRating, R.string.filter_rating_rated)
            ).forEach { (chipMin, chipMax, chipLabel) ->
                FilterChip(
                    selected = min == chipMin && max == chipMax,
                    onClick = { onChange(chipMin, chipMax) },
                    label = { Text(stringResource(chipLabel)) }
                )
            }
        }
    }
}

/** Search field that picks artists, albums or genres by ID. Picked values are shown as removable chips. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EntityPicker(
    @StringRes label: Int,
    @StringRes searchLabel: Int,
    selected: List<FilterEntity>,
    search: suspend (String) -> List<FilterEntity>,
    onChange: (List<FilterEntity>) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<FilterEntity>?>(null) }

    LaunchedEffect(query, focused) {
        if (!focused) return@LaunchedEffect
        delay(300)
        results = try { search(query) } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = stringResource(label), style = MaterialTheme.typography.labelLarge)

        if (selected.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                selected.forEach { entity ->
                    InputChip(
                        selected = true,
                        onClick = { onChange(selected - entity) },
                        label = { Text(entity.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingIcon = {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.filter_remove_value, entity.name),
                                modifier = Modifier.size(InputChipDefaults.IconSize)
                            )
                        }
                    )
                }
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(searchLabel)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
        )

        val visibleResults = results?.filter { result -> selected.none { it.id == result.id && it.providerId == result.providerId } }
        if (focused && visibleResults != null && (query.isNotBlank() || visibleResults.isNotEmpty())) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (visibleResults.isEmpty()) {
                        Text(
                            text = stringResource(R.string.filter_search_no_results),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    visibleResults.take(8).forEach { result ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onChange(selected + result)
                                    query = ""
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = result.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!result.detail.isNullOrBlank()) {
                                Text(
                                    text = result.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YearFilter(
    @StringRes label: Int,
    value: String,
    implicitValue: Int?,
    modifier: Modifier,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onChange(text.filter { it.isDigit() }.take(4)) },
        label = { Text(stringResource(label)) },
        placeholder = if (implicitValue != null) { { Text(implicitValue.toString()) } } else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

@Composable
private fun FilterRuleEditor(
    rule: FilterRule,
    fields: List<FilterField>,
    onChange: (FilterRule) -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterDropdown(
                    label = stringResource(rule.field.label),
                    options = fields,
                    optionLabel = { stringResource(it.label) },
                    onSelect = { field ->
                        if (field == rule.field) return@FilterDropdown
                        onChange(
                            if (field.type == rule.field.type) rule.copy(field = field)
                            else FilterRule(field)
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
                ActionTooltip(stringResource(R.string.filter_remove_rule)) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.filter_remove_rule))
                    }
                }
            }

            FilterDropdown(
                label = stringResource(rule.operator.label),
                options = rule.field.type.operators,
                optionLabel = { stringResource(it.label) },
                onSelect = { operator ->
                    // Days and dates use different values, so they are cleared when switching between them.
                    val dateKindChanged = rule.field.type == FilterValueType.DATE &&
                            operator.isDayCount() != rule.operator.isDayCount()
                    onChange(if (dateKindChanged) rule.copy(operator = operator, value = "", value2 = "") else rule.copy(operator = operator))
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (rule.operator.valueCount > 0) {
                val isNumber = rule.field.type == FilterValueType.NUMBER ||
                        (rule.field.type == FilterValueType.DATE && rule.operator.isDayCount())
                val keyboardOptions = KeyboardOptions(
                    keyboardType = if (isNumber) KeyboardType.Decimal else KeyboardType.Text
                )
                val valueLabel = when {
                    rule.field.type != FilterValueType.DATE -> R.string.filter_value
                    rule.operator.isDayCount() -> R.string.filter_value_days
                    else -> R.string.filter_value_date
                }

                OutlinedTextField(
                    value = rule.value,
                    onValueChange = { onChange(rule.copy(value = it)) },
                    label = { Text(stringResource(valueLabel)) },
                    singleLine = true,
                    isError = !isValidValue(rule, rule.value),
                    keyboardOptions = keyboardOptions,
                    modifier = Modifier.fillMaxWidth()
                )
                if (rule.operator.valueCount > 1) {
                    OutlinedTextField(
                        value = rule.value2,
                        onValueChange = { onChange(rule.copy(value2 = it)) },
                        label = { Text(stringResource(R.string.filter_value_and)) },
                        singleLine = true,
                        isError = !isValidValue(rule, rule.value2),
                        keyboardOptions = keyboardOptions,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/** Empty values are not errors, the rule is just ignored until it is filled. */
private fun isValidValue(rule: FilterRule, value: String): Boolean = value.isEmpty() || when (rule.field.type) {
    FilterValueType.NUMBER -> value.toDoubleOrNull() != null
    FilterValueType.DATE -> if (rule.operator.isDayCount()) value.toLongOrNull() != null else FilterRule.parseDay(value) != null
    else -> true
}

private fun FilterOperator.isDayCount() =
    this == FilterOperator.IN_THE_LAST || this == FilterOperator.NOT_IN_THE_LAST

@Composable
private fun <T> FilterDropdown(
    label: String,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 360.dp)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
