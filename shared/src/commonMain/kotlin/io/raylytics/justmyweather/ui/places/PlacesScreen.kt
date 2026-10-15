package io.raylytics.justmyweather.ui.places

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.raylytics.justmyweather.data.WeatherLocation
import io.raylytics.justmyweather.data.places.Place
import io.raylytics.justmyweather.data.places.SavedPlaces
import io.raylytics.justmyweather.resources.Res
import io.raylytics.justmyweather.resources.common_cancel
import io.raylytics.justmyweather.resources.common_done
import io.raylytics.justmyweather.resources.common_places
import io.raylytics.justmyweather.resources.common_remove
import io.raylytics.justmyweather.resources.places_coordinates
import io.raylytics.justmyweather.resources.places_enter_coordinates
import io.raylytics.justmyweather.resources.places_follows_device_way
import io.raylytics.justmyweather.resources.places_latitude
import io.raylytics.justmyweather.resources.places_loading_places
import io.raylytics.justmyweather.resources.places_longitude
import io.raylytics.justmyweather.resources.places_name_e_g_cabin
import io.raylytics.justmyweather.resources.places_no_match_clear_search
import io.raylytics.justmyweather.resources.places_save_place
import io.raylytics.justmyweather.resources.places_saved
import io.raylytics.justmyweather.resources.places_search_towns_cities
import io.raylytics.justmyweather.resources.places_showing
import io.raylytics.justmyweather.resources.places_united_states_forecast
import io.raylytics.justmyweather.resources.places_use_my_location
import io.raylytics.justmyweather.ui.dismissKeyboardOnTap
import io.raylytics.justmyweather.ui.scrollbar
import io.raylytics.justmyweather.ui.theme.textButtonColors
import io.raylytics.justmyweather.view.fixedDecimals
import org.jetbrains.compose.resources.stringResource

/**
 * Choose where the weather is for.
 *
 * Three ways in, in the order people need them: follow the device (the
 * default, and how the app has always worked), pick something already saved,
 * or find somewhere new. The search runs against a list bundled in the APK —
 * no geocoder, no key, no network — and the coordinate entry at the bottom is
 * the escape hatch for a spot no gazetteer lists, which on a weather app is a
 * real case rather than a theoretical one: trailheads, campsites, a cabin.
 */
@Composable
fun PlacesScreen(
    saved: SavedPlaces,
    results: List<Place>,
    query: String,
    loading: Boolean,
    onQueryChange: (String) -> Unit,
    onSave: (Place) -> Unit,
    onSaveCoordinates: (WeatherLocation) -> Unit,
    onSelect: (String?) -> Unit,
    onRemove: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().dismissKeyboardOnTap().padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(Res.string.common_places), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onDone) { Text(stringResource(Res.string.common_done)) }
            }

            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(Res.string.places_search_towns_cities)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("place-search"),
            )

            // The list below is one scroller, not three: the saved places, the
            // search results and the coordinate entry are all "ways to pick a
            // place", and stacking three independently-scrolling boxes on a
            // phone is how a screen becomes unusable in landscape.
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f).scrollbar(listState, gutter = 10.dp),
            ) {
                if (query.isBlank()) {
                    item {
                        ChoiceRow(
                            title = stringResource(Res.string.places_use_my_location),
                            subtitle = stringResource(Res.string.places_follows_device_way),
                            selected = saved.selected == null,
                            onClick = { onSelect(null) },
                            tag = "place-device",
                        )
                    }
                    if (saved.places.isNotEmpty()) {
                        item { SectionLabel(stringResource(Res.string.places_saved)) }
                        items(saved.places, key = { it.label }) { place ->
                            ChoiceRow(
                                title = place.label,
                                subtitle = coordinateLabel(place),
                                selected = saved.selected == place.label,
                                onClick = { onSelect(place.label) },
                                onRemove = { onRemove(place.label) },
                                tag = "place-saved",
                            )
                        }
                    }
                    item { CoordinateEntry(onSave = onSaveCoordinates) }
                } else {
                    when {
                        loading ->
                            item { Hint(stringResource(Res.string.places_loading_places)) }
                        results.isEmpty() ->
                            item {
                                // Names the escape hatch rather than just
                                // saying no: somewhere unlisted is exactly when
                                // coordinates are the answer, and that entry is
                                // one clear-the-box away.
                                Hint(stringResource(Res.string.places_no_match_clear_search))
                            }
                        else ->
                            items(results, key = { "${it.label}|${it.latitude},${it.longitude}" }) { place ->
                                ChoiceRow(
                                    title = place.label,
                                    subtitle = null,
                                    selected = false,
                                    onClick = {
                                        onSave(place)
                                        onQueryChange("")
                                        // Picked: the search is over, so is
                                        // the keyboard (it hid the saved list).
                                        focus.clearFocus()
                                    },
                                    tag = "place-result",
                                )
                            }
                    }
                }
            }
        }
    }
}

/** One tappable place. The current one is marked with a word, not a colour
 * alone — "selected" has to survive a colourblind reader and a screenshot. */
@Composable
private fun ChoiceRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String,
    onRemove: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (selected) {
            Text(
                text = stringResource(Res.string.places_showing),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        onRemove?.let {
            TextButton(onClick = it, modifier = Modifier.testTag("place-remove")) {
                Text(stringResource(Res.string.common_remove))
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 16.dp),
    )
}

/**
 * The escape hatch: a coordinate and a name for it.
 *
 * Validated before it can be saved, because a bad coordinate here is not a
 * cosmetic problem — it reaches the NWS point lookup, and a NaN throws on the
 * way. Out-of-range values are refused rather than clamped: silently moving
 * somebody's cabin to the nearest legal latitude is worse than saying no.
 */
@Composable
private fun CoordinateEntry(onSave: (WeatherLocation) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf("") }
    var lon by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        if (!open) {
            TextButton(onClick = { open = true }, modifier = Modifier.testTag("place-coords-open")) {
                Text(stringResource(Res.string.places_enter_coordinates))
            }
            return@Column
        }
        Text(stringResource(Res.string.places_coordinates), style = MaterialTheme.typography.labelMedium)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = { Text(stringResource(Res.string.places_name_e_g_cabin)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("coords-name"),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = lat,
                onValueChange = { lat = it },
                placeholder = { Text(stringResource(Res.string.places_latitude)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f).testTag("coords-lat"),
            )
            OutlinedTextField(
                value = lon,
                onValueChange = { lon = it },
                placeholder = { Text(stringResource(Res.string.places_longitude)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f).testTag("coords-lon"),
            )
        }
        val parsed = parseCoordinates(name, lat, lon)
        Text(
            text =
                stringResource(Res.string.places_united_states_forecast),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = {
                    parsed?.let(onSave)
                    name = ""
                    lat = ""
                    lon = ""
                    open = false
                },
                enabled = parsed != null,
                colors = textButtonColors(),
                modifier = Modifier.testTag("coords-save"),
            ) { Text(stringResource(Res.string.places_save_place)) }
            TextButton(onClick = { open = false }) { Text(stringResource(Res.string.common_cancel)) }
        }
    }
}

/**
 * A name and two numbers, or null if they are not yet a place. Pure and
 * `internal` so the rules are asserted directly rather than inferred from a
 * greyed-out button.
 */
internal fun parseCoordinates(name: String, latitude: String, longitude: String): WeatherLocation? {
    val lat = latitude.trim().toDoubleOrNull() ?: return null
    val lon = longitude.trim().toDoubleOrNull() ?: return null
    if (!lat.isFinite() || !lon.isFinite()) return null
    if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
    return WeatherLocation(latitude = lat, longitude = lon, label = name.trim().ifBlank { coordinateLabel(lat, lon) })
}

private fun coordinateLabel(location: WeatherLocation): String =
    coordinateLabel(location.latitude, location.longitude)

/**
 * "38.22, -85.74" — always with a dot, whatever the device's locale.
 *
 * [Locale.US] is not decoration here, twice over. A device set to German
 * formats 38.22 as "38,22", so the pair would read "38,22, -85,74" — a comma
 * serving as both the decimal point and the separator between the two numbers,
 * which is unreadable. And because [SavedPlaces] uses the label as a place's
 * identity, a locale-dependent label would make the same coordinates a
 * different place after a language change. The same trap `PointCacheKey.round`
 * documents.
 */
internal fun coordinateLabel(latitude: Double, longitude: Double): String =
    "${fixedDecimals(latitude, 2)}, ${fixedDecimals(longitude, 2)}"
