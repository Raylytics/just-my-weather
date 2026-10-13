package io.raylytics.justmyweather.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.raylytics.justmyweather.region.RegionSettings
import io.raylytics.justmyweather.region.Regions
import io.raylytics.justmyweather.region.ResolvedRegion
import io.raylytics.justmyweather.resources.Res
import io.raylytics.justmyweather.resources.common_done
import io.raylytics.justmyweather.resources.common_region
import io.raylytics.justmyweather.resources.common_region_units
import io.raylytics.justmyweather.resources.region_auto
import io.raylytics.justmyweather.resources.region_auto_named
import io.raylytics.justmyweather.resources.region_automatic
import io.raylytics.justmyweather.resources.region_automatic_named
import io.raylytics.justmyweather.resources.region_dates_times_set_by
import io.raylytics.justmyweather.resources.region_day_first
import io.raylytics.justmyweather.resources.region_find_country
import io.raylytics.justmyweather.resources.region_month_first
import io.raylytics.justmyweather.resources.region_precipitation
import io.raylytics.justmyweather.resources.region_pressure
import io.raylytics.justmyweather.resources.region_sets_units_how_dates
import io.raylytics.justmyweather.resources.region_temperature
import io.raylytics.justmyweather.resources.region_units
import io.raylytics.justmyweather.resources.region_wind
import io.raylytics.justmyweather.ui.customize.ChipRow
import io.raylytics.justmyweather.view.Conventions
import io.raylytics.justmyweather.view.DateOrder
import io.raylytics.justmyweather.view.PrecipitationUnit
import io.raylytics.justmyweather.view.PressureUnit
import io.raylytics.justmyweather.view.TemperatureUnit
import io.raylytics.justmyweather.view.WindUnit
import io.raylytics.justmyweather.view.format
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.jetbrains.compose.resources.stringResource

/**
 * Region & units. Automatic is the default and the recommendation: the app
 * follows the phone (docs/REGIONS.md), and the line under it says which
 * clue decided, so a wrong answer can be understood before it is
 * overridden. Each unit can be pinned on its own; "Auto" follows the region.
 * The date order and clock follow the region alone — choosing a region is
 * how to change them.
 */
@Composable
fun RegionScreen(
    settings: RegionSettings,
    resolved: ResolvedRegion?,
    automatic: ResolvedRegion?,
    conventions: Conventions,
    onUpdate: ((RegionSettings) -> RegionSettings) -> Unit,
    onDone: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    // Prepared regions first — the ones the app ships to — then every country.
    val countries =
        remember {
            val prepared = Regions.prepared.map { it.code }
            prepared + Regions.all.filter { it !in prepared }.sortedBy(Regions::displayName)
        }
    val shown =
        remember(query) {
            val q = query.trim().lowercase()
            if (q.isEmpty()) countries else countries.filter { Regions.displayName(it).lowercase().contains(q) }
        }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(Res.string.common_region_units), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onDone) { Text(stringResource(Res.string.common_done)) }
                }
            }
            item { UnitsSection(settings, resolved, conventions, onUpdate) }
            item {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
                Text(stringResource(Res.string.common_region), style = MaterialTheme.typography.labelMedium)
                Text(
                    text =
                        stringResource(Res.string.region_sets_units_how_dates),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                RegionRow(
                    title =
                        automatic?.let { stringResource(Res.string.region_automatic_named, it.region.name) }
                            ?: stringResource(Res.string.region_automatic),
                    detail = automatic?.source?.description?.replaceFirstChar(Char::uppercase),
                    selected = settings.manualRegion == null,
                    onClick = { onUpdate { it.copy(manualRegion = null) } },
                    tag = "region-automatic",
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(Res.string.region_find_country)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("region-search"),
                )
            }
            items(shown, key = { it }) { code ->
                RegionRow(
                    title = Regions.displayName(code),
                    detail = null,
                    selected = settings.manualRegion == code,
                    onClick = { onUpdate { it.copy(manualRegion = code) } },
                    tag = "region-$code",
                )
            }
        }
    }
}

@Composable
private fun UnitsSection(
    settings: RegionSettings,
    resolved: ResolvedRegion?,
    conventions: Conventions,
    onUpdate: ((RegionSettings) -> RegionSettings) -> Unit,
) {
    val base = resolved?.region?.conventions?.units
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(Res.string.region_units), style = MaterialTheme.typography.labelMedium)
        val temperature = stringResource(Res.string.region_temperature)
        val wind = stringResource(Res.string.region_wind)
        val pressure = stringResource(Res.string.region_pressure)
        val precipitationLabel = stringResource(Res.string.region_precipitation)
        UnitRow(temperature, TemperatureUnit.entries, settings.temperature, base?.temperature, { it.label }) { u ->
            onUpdate { it.copy(temperature = u) }
        }
        UnitRow(wind, WindUnit.entries, settings.wind, base?.wind, { it.label }) { u ->
            onUpdate { it.copy(wind = u) }
        }
        UnitRow(pressure, PressureUnit.entries, settings.pressure, base?.pressure, { it.label }) { u ->
            onUpdate { it.copy(pressure = u) }
        }
        val precipitation = settings.precipitation
        UnitRow(precipitationLabel, PrecipitationUnit.entries, precipitation, base?.precipitation, { it.label }) { u ->
            onUpdate { it.copy(precipitation = u) }
        }
        val sample = LocalDate(2026, 10, 6).format(conventions.shortDate)
        val clock = LocalTime(16, 5).format(conventions.clock)
        val order =
            if (conventions.dateOrder == DateOrder.DAY_FIRST) {
                stringResource(Res.string.region_day_first)
            } else {
                stringResource(Res.string.region_month_first)
            }
        Text(
            text = stringResource(Res.string.region_dates_times_set_by, sample, order, clock),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One dimension: "Auto (°C)" — following the region — or a unit pinned by
 * hand. Null is Auto, so a region change keeps moving an unpinned unit.
 */
@Composable
private fun <T> UnitRow(
    title: String,
    options: List<T>,
    chosen: T?,
    regionDefault: T?,
    label: (T) -> String,
    onChoose: (T?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        val auto =
            regionDefault?.let { stringResource(Res.string.region_auto_named, label(it)) }
                ?: stringResource(Res.string.region_auto)
        // A sealed choice of "auto" or a unit, as one list for the chip row.
        val choices: List<T?> = listOf<T?>(null) + options
        ChipRow(
            options = choices,
            selected = chosen,
            label = { it?.let(label) ?: auto },
            onSelect = onChoose,
            tag = { "unit-$title-${it?.let(label) ?: "auto"}" },
        )
    }
}

@Composable
private fun RegionRow(title: String, detail: String?, selected: Boolean, onClick: () -> Unit, tag: String) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            detail?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
