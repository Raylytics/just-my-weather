package io.raylytics.justmyweather.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import io.raylytics.justmyweather.view.Density
import io.raylytics.justmyweather.view.ModuleSize
import io.raylytics.justmyweather.view.WeatherField

/** One tile of the preview: a label, a made-up value and a footprint. */
private data class PreviewTile(val label: String?, val value: String, val size: ModuleSize)

/*
 * The top of a default glance: the full-width temperature with two half-width
 * readings under it. Made-up values, since what is being shown is the spacing
 * and the type, not the weather; unit-free, so no region reads them wrong.
 */
private val PREVIEW_TILES =
    listOf(
        PreviewTile(label = null, value = "72°", size = ModuleSize(ModuleSize.COLUMNS, 2)),
        PreviewTile(label = WeatherField.FEELS_LIKE.defaultLabel, value = "70°", size = ModuleSize(2, 1)),
        PreviewTile(label = WeatherField.CONDITIONS.defaultLabel, value = "Clear", size = ModuleSize(2, 1)),
    )

/**
 * What [density] looks like, drawn by the glance's own parts — the lattice,
 * the tile shell and the fitted text — at the [DensitySpec] the glance would
 * use. Built from the real pieces rather than a picture of them, so the
 * preview cannot drift from what the user gets when they press Done.
 *
 * It is a picture, not a control: screen readers hear one description of it
 * rather than three readings that are not the weather.
 */
@Composable
fun DensityPreview(
    density: Density,
    modifier: Modifier = Modifier,
) {
    val spec = density.spec()
    CellGrid(
        items = PREVIEW_TILES,
        size = { it.size },
        gap = spec.moduleGap,
        cellAspect = spec.cellAspect,
        modifier =
            modifier
                .fillMaxWidth()
                .clearAndSetSemantics { contentDescription = "Preview of the ${density.label} density" },
    ) { tile, _, tileModifier ->
        TileShell(borderColor = MaterialTheme.colorScheme.surfaceVariant, modifier = tileModifier) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // The same label and value styles ModuleTile draws with.
                tile.label?.let { label ->
                    val labelStyle = MaterialTheme.typography.labelSmall
                    FittedText(
                        text = label,
                        style = labelStyle,
                        ceiling = labelStyle.fontSize,
                        floor = LABEL_FLOOR,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                FittedText(
                    text = tile.value,
                    style = spec.heroStyle,
                    ceiling = spec.heroStyle.fontSize,
                    floor = VALUE_FLOOR,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2 * tile.size.rows,
                )
            }
        }
    }
}
