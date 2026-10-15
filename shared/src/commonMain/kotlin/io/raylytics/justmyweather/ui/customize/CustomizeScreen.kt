package io.raylytics.justmyweather.ui.customize

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.raylytics.justmyweather.resources.Res
import io.raylytics.justmyweather.resources.common_done
import io.raylytics.justmyweather.resources.customize_collapsible
import io.raylytics.justmyweather.resources.customize_collapsible_rests_as_cell
import io.raylytics.justmyweather.resources.customize_couldn_find_what
import io.raylytics.justmyweather.resources.customize_customize_view
import io.raylytics.justmyweather.resources.customize_density
import io.raylytics.justmyweather.resources.customize_height
import io.raylytics.justmyweather.resources.customize_hidden
import io.raylytics.justmyweather.resources.customize_look
import io.raylytics.justmyweather.resources.customize_modules
import io.raylytics.justmyweather.resources.customize_name
import io.raylytics.justmyweather.resources.customize_options
import io.raylytics.justmyweather.resources.customize_order
import io.raylytics.justmyweather.resources.customize_order_hint
import io.raylytics.justmyweather.resources.customize_order_hint_full_width
import io.raylytics.justmyweather.resources.customize_order_hint_hidden
import io.raylytics.justmyweather.resources.customize_preview
import io.raylytics.justmyweather.resources.customize_safety_alerts
import io.raylytics.justmyweather.resources.customize_show_fields_care_about
import io.raylytics.justmyweather.resources.customize_show_times_in
import io.raylytics.justmyweather.resources.customize_submit_idea_developer
import io.raylytics.justmyweather.resources.customize_tap_tile_details
import io.raylytics.justmyweather.resources.customize_tornado_severe_storm_hurricane
import io.raylytics.justmyweather.resources.customize_whole_observation_behind_reading
import io.raylytics.justmyweather.resources.customize_width
import io.raylytics.justmyweather.ui.dismissKeyboardOnTap
import io.raylytics.justmyweather.ui.home.DensityPreview
import io.raylytics.justmyweather.ui.theme.subtleBorder
import io.raylytics.justmyweather.ui.theme.textButtonColors
import io.raylytics.justmyweather.view.AlertBannerPosition
import io.raylytics.justmyweather.view.DailyStyle
import io.raylytics.justmyweather.view.Density
import io.raylytics.justmyweather.view.ForecastElement
import io.raylytics.justmyweather.view.ForecastMode
import io.raylytics.justmyweather.view.ForecastTileLayout
import io.raylytics.justmyweather.view.ModuleKey
import io.raylytics.justmyweather.view.ModuleSetting
import io.raylytics.justmyweather.view.ModuleSize
import io.raylytics.justmyweather.view.ThemeConfig
import io.raylytics.justmyweather.view.TimesIn
import io.raylytics.justmyweather.view.ViewConfig
import io.raylytics.justmyweather.view.canCollapse
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

private const val RELABEL_DEBOUNCE_MS = 400L

/**
 * The customization layer: pick which data points appear, set how wide each
 * sits on the glance grid, reorder and relabel them. Edits persist immediately
 * and the home view reflects them live. Deliberately a plain list — the power
 * is in editing down, not in a wall of controls — and each entry is folded to
 * one line until tapped (CollapsibleSection), with every setting a module has,
 * the forecast's and the sun's included, inside its own fold. Everything here
 * is also the grid's non-gesture path: the arrows and width chips do what
 * arrange mode's drag and tap do, for anyone who can't or won't long-press
 * (docs/modular-v2-evaluation.md, criterion 3).
 *
 * Two groups, in this order: the modules, then everything about the view as
 * a whole (tap-for-details, which clock, the look with its density, where a
 * safety banner sits). The modules lead because they are what people come
 * here to edit; the rest is set once and left.
 */
@Composable
fun CustomizeScreen(
    config: ViewConfig,
    onToggle: (ModuleKey) -> Unit,
    onRelabel: (ModuleKey, String?) -> Unit,
    onResize: (ModuleKey, ModuleSize) -> Unit,
    onSetCollapsible: (ModuleKey, Boolean) -> Unit,
    onStepUp: (ModuleKey) -> Unit,
    onStepDown: (ModuleKey) -> Unit,
    onSetDensity: (Density) -> Unit,
    onSetTapForDetails: (Boolean) -> Unit,
    onSetTimesIn: (TimesIn) -> Unit,
    onSetDefaultForecastMode: (ForecastMode) -> Unit,
    onSetDailyStyle: (DailyStyle) -> Unit,
    onSetHourlyHours: (Int) -> Unit,
    onSetDailyDays: (Int) -> Unit,
    onSetSunDays: (Int) -> Unit,
    onToggleForecastElement: (ForecastElement) -> Unit,
    onSetForecastTileLayout: (ForecastTileLayout) -> Unit,
    onSetAlertBannerPosition: (AlertBannerPosition) -> Unit,
    theme: ThemeConfig,
    onThemeChange: (ThemeConfig) -> Unit,
    onSubmitIdea: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize().dismissKeyboardOnTap(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(Res.string.customize_customize_view), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onDone) { Text(stringResource(Res.string.common_done)) }
            }
            Text(
                text = stringResource(Res.string.customize_show_fields_care_about),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            // Everything below the header scrolls as one page — the option
            // sections have outgrown a fixed header on small screens.
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                GroupHeading(stringResource(Res.string.customize_modules), tag = "group_modules")
                config.items.forEach { setting ->
                    // Keyed by module so a field's open-or-folded state, and
                    // its half-typed name, move with it when it is reordered.
                    key(setting.module) {
                        FieldRow(
                            setting = setting,
                            // An arrow is live when a press would change
                            // something: the step's own rule, asked directly.
                            canMoveUp = config.stepUp(setting.module) != config,
                            canMoveDown = config.stepDown(setting.module) != config,
                            onToggle = { onToggle(setting.module) },
                            onRelabel = { onRelabel(setting.module, it) },
                            onResize = { onResize(setting.module, it) },
                            onSetCollapsible = { onSetCollapsible(setting.module, it) },
                            onMoveUp = { onStepUp(setting.module) },
                            onMoveDown = { onStepDown(setting.module) },
                        ) {
                            // A module's own options live in its fold, with
                            // its name and size: "everything about the
                            // forecast" is one tap, not a hunt between a
                            // picker up top and a row further down.
                            when (setting.module) {
                                ModuleKey.Forecast ->
                                    ForecastPicker(
                                        show = setting.visible,
                                        mode = config.defaultForecastMode,
                                        dailyStyle = config.dailyStyle,
                                        onSetMode = onSetDefaultForecastMode,
                                        onSetDailyStyle = onSetDailyStyle,
                                        hourlyHours = config.hourlyHours,
                                        onSetHourlyHours = onSetHourlyHours,
                                        dailyDays = config.dailyDays,
                                        onSetDailyDays = onSetDailyDays,
                                        elements = config.forecastElements,
                                        onToggleElement = onToggleForecastElement,
                                        tileLayout = config.forecastTileLayout,
                                        onSetTileLayout = onSetForecastTileLayout,
                                        showTitle = false,
                                    )
                                ModuleKey.Sun ->
                                    SunPicker(
                                        show = setting.visible,
                                        sunDays = config.sunDays,
                                        onSetSunDays = onSetSunDays,
                                        showTitle = false,
                                    )
                                else -> Unit
                            }
                        }
                    }
                    SectionDivider()
                }

                // Under the modules, with more air above it than between
                // folds: the rest is about the view as a whole, set once
                // and left, and a heading says where the module list ends.
                GroupHeading(stringResource(Res.string.customize_options), tag = "group_options")
                TapForDetailsPicker(enabled = config.tapForDetails, onSet = onSetTapForDetails)
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
                CollapsibleSection(
                    title = stringResource(Res.string.customize_show_times_in),
                    summary = config.timesIn.label,
                    tag = "section_times",
                ) {
                    TimesInPicker(timesIn = config.timesIn, onSet = onSetTimesIn, showTitle = false)
                }
                SectionDivider()
                // Density lives with the look: both are "how it is drawn",
                // and a reader setting one is likely to want the other.
                CollapsibleSection(
                    title = stringResource(Res.string.customize_look),
                    summary =
                        listOf(theme.mood.label, theme.accent.label, theme.type.label, config.density.label)
                            .joinToString(" · "),
                    tag = "section_look",
                ) {
                    ThemePicker(theme = theme, onChange = onThemeChange, showTitle = false)
                    Text(
                        text = stringResource(Res.string.customize_density),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    DensityPicker(selected = config.density, onSelect = onSetDensity, showTitle = false)
                    // The chips' names alone say little ("Comfortable" than
                    // what?), so the choice is shown, drawn the way the
                    // glance will draw it.
                    Text(
                        text = stringResource(Res.string.customize_preview),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    DensityPreview(density = config.density)
                }
                SectionDivider()
                AlertBannerPicker(
                    position = config.alertBannerPosition,
                    onSelect = onSetAlertBannerPosition,
                )
                SectionDivider()
                IdeaPrompt(onSubmitIdea = onSubmitIdea)
            }
        }
    }
}

/**
 * The last thing on the page, where someone who scrolled the whole list
 * looking for an option lands when it isn't there. Opens the idea form — not
 * the bug report: a missing feature is a wish, and it travels without the
 * diagnostics a bug needs.
 */
@Composable
private fun IdeaPrompt(onSubmitIdea: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.customize_couldn_find_what),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onSubmitIdea) { Text(stringResource(Res.string.customize_submit_idea_developer)) }
    }
}

/**
 * Where a safety-alert banner sits. Worth a control even though most users see
 * it rarely: when a tornado warning does appear, whether it lands above or
 * below the temperature is exactly the kind of thing people have a firm
 * opinion about.
 */
@Composable
private fun AlertBannerPicker(
    position: AlertBannerPosition,
    onSelect: (AlertBannerPosition) -> Unit,
) {
    CollapsibleSection(
        title = stringResource(Res.string.customize_safety_alerts),
        summary = position.label,
        tag = "section_banner",
    ) {
        ChipRow(
            options = AlertBannerPosition.entries,
            selected = position,
            label = { it.label },
            onSelect = onSelect,
            tag = { "banner_${it.key}" },
        )
        Text(
            text = stringResource(Res.string.customize_tornado_severe_storm_hurricane),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One labelled row of cell counts: "Width  1 2 3 4". */
@Composable
private fun SizeChips(
    title: String,
    options: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    tag: (Int) -> String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ChipRow(
            options = options,
            selected = selected,
            label = { it.toString() },
            onSelect = onSelect,
            tag = tag,
        )
    }
}

/** One switch: whether a tap on a tile opens the sheet with everything behind
 * it. On by default, because the sheet is where the fields a tile has no
 * room for live; off for a screen that should stay inert. */
@Composable
private fun TapForDetailsPicker(
    enabled: Boolean,
    onSet: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(Res.string.customize_tap_tile_details), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(Res.string.customize_whole_observation_behind_reading),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onSet,
            modifier = Modifier.testTag("tap-for-details-toggle"),
        )
    }
}

/** The faint rule between folded sections. */
@Composable
private fun SectionDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}

/**
 * The heading over one of the page's two groups. A line of text rather than
 * a card or a box: the folds below it already draw their own rules, and a
 * heading's job here is to say where one list ends and the other begins.
 */
@Composable
private fun GroupHeading(
    text: String,
    tag: String,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 20.dp, bottom = 2.dp).testTag(tag),
    )
}

/**
 * One switch, in the fold of a module that cannot shrink to a cell: let it
 * rest as one anyway, as a name to tap. Only for those modules — a number
 * is made small by resizing it (see ModuleKey.canCollapse) — and only while
 * the module shows, like its size chips.
 */
@Composable
private fun CollapsiblePicker(
    setting: ModuleSetting,
    onSet: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(Res.string.customize_collapsible), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(Res.string.customize_collapsible_rests_as_cell),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = setting.collapsible,
            onCheckedChange = onSet,
            modifier = Modifier.testTag("collapsible_${setting.module.key}"),
        )
    }
}

/**
 * One module, folded to its name, its footprint and its on/off switch. Open,
 * it holds everything else about the module: the name it shows under, its
 * place in the order, its size, whether it rests folded (for the modules
 * that can), and [moduleSettings] — the forecast's framing and reach, the
 * sun's days — for the modules that have any.
 */
@Composable
private fun FieldRow(
    setting: ModuleSetting,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: () -> Unit,
    onRelabel: (String?) -> Unit,
    onResize: (ModuleSize) -> Unit,
    onSetCollapsible: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    moduleSettings: @Composable ColumnScope.() -> Unit,
) {
    // testTags (per field key) give UI tests a stable handle on controls
    // that otherwise carry only a glyph or no text.
    val key = setting.module.key
    CollapsibleSection(
        title = setting.customLabel ?: setting.module.defaultLabel,
        summary =
            when {
                !setting.visible -> stringResource(Res.string.customize_hidden)
                setting.collapsible -> "${setting.size.label} · ${stringResource(Res.string.customize_collapsible)}"
                else -> setting.size.label
            },
        tag = "field_$key",
        // On the line, not in the fold: showing or hiding a module is the
        // thing most visits come to do, and it should not take two taps.
        trailing = {
            Switch(
                checked = setting.visible,
                onCheckedChange = { onToggle() },
                modifier = Modifier.testTag("toggle_$key"),
            )
        },
    ) {
        NameField(setting = setting, onRelabel = onRelabel)
        // Reorder controls. Arrows as text keep us off an icon dependency;
        // the outline says they are buttons, which a bare arrow did not.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(Res.string.customize_order),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = onMoveUp,
                enabled = canMoveUp,
                colors = textButtonColors(),
                border = subtleBorder(enabled = canMoveUp),
                modifier = Modifier.testTag("moveUp_$key"),
            ) { Text("↑") }
            TextButton(
                onClick = onMoveDown,
                enabled = canMoveDown,
                colors = textButtonColors(),
                border = subtleBorder(enabled = canMoveDown),
                modifier = Modifier.testTag("moveDown_$key"),
            ) { Text("↓") }
        }
        // What a press does is not obvious from two arrows: say it, for the
        // case this field is in. The rule itself is ViewConfig.step.
        Text(
            text =
                stringResource(
                    when {
                        !setting.visible -> Res.string.customize_order_hint_hidden
                        !setting.collapsible && setting.size.columns >= ModuleSize.COLUMNS ->
                            Res.string.customize_order_hint_full_width
                        else -> Res.string.customize_order_hint
                    },
                ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("orderHint_$key"),
        )
        // Footprint on the glance grid — the non-gesture twin of dragging the
        // tile's corner in arrange mode. Only for shown fields: a hidden field
        // has no tile for the size to describe. Each row offers only the
        // counts the module can fill: a phrase's width chips start at 2.
        if (setting.visible) {
            val min = setting.module.minSize
            SizeChips(
                title = stringResource(Res.string.customize_width),
                options = (min.columns..ModuleSize.COLUMNS).toList(),
                selected = setting.size.columns,
                onSelect = { onResize(setting.size.copy(columns = it)) },
                tag = { "width_${key}_$it" },
            )
            SizeChips(
                title = stringResource(Res.string.customize_height),
                options = (min.rows..ModuleSize.MAX_ROWS).toList(),
                selected = setting.size.rows,
                onSelect = { onResize(setting.size.copy(rows = it)) },
                tag = { "height_${key}_$it" },
            )
            if (setting.module.canCollapse) {
                CollapsiblePicker(setting = setting, onSet = onSetCollapsible)
            }
        }
        moduleSettings()
    }
}

/** The name a module shows under on the glance; empty means its default. */
@Composable
private fun NameField(
    setting: ModuleSetting,
    onRelabel: (String?) -> Unit,
) {
    // Local edit state keyed by field so the cursor stays put while the
    // persisted config streams back in; the field's default name shows as
    // the placeholder, so an empty box clearly means "use the default".
    var label by remember(setting.module) { mutableStateOf(setting.customLabel ?: "") }
    // Debounce persistence: typing only writes to DataStore once the user
    // pauses, instead of a disk write per keystroke. The delay is cancelled
    // and restarted on each change; the guard skips the no-op initial write.
    LaunchedEffect(label) {
        delay(RELABEL_DEBOUNCE_MS)
        val normalized = label.ifBlank { null }
        if (normalized != setting.customLabel) onRelabel(normalized)
    }
    // Flush a still-pending edit if the box leaves composition before the
    // debounce fires — tapping Done, pressing back, or folding the section.
    // Without this, the last keystroke is silently dropped. rememberUpdatedState
    // keeps onDispose reading the latest typed value and the latest persisted
    // label, so the guard compares against the current value and skips an
    // already-saved one.
    val latestLabel by rememberUpdatedState(label)
    val latestSaved by rememberUpdatedState(setting.customLabel)
    DisposableEffect(setting.module) {
        onDispose {
            val normalized = latestLabel.ifBlank { null }
            if (normalized != latestSaved) onRelabel(normalized)
        }
    }
    OutlinedTextField(
        value = label,
        onValueChange = { label = it },
        label = { Text(stringResource(Res.string.customize_name)) },
        placeholder = { Text(setting.module.defaultLabel) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
}
