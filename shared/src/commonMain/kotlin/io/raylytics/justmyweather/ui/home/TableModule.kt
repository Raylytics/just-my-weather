package io.raylytics.justmyweather.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

/**
 * A table module's rows, in columns: the first column (the names) aligned
 * to the start in the quieter colour, every other column aligned to the end
 * so digits stack under digits. Each column is as wide as its widest cell,
 * and every cell shares one style, so the rows line up across columns too.
 *
 * The text is sized to the height the tile leaves it, up to the theme's
 * title size: a one-cell-tall tile fits two rows only smaller, and a taller
 * one gets the full size rather than a small table in a big tile.
 */
@Composable
internal fun TableModuleContent(rows: List<List<String>>) {
    val columns = rows.maxOfOrNull { it.size } ?: return
    if (rows.isEmpty()) return
    val nameColor = MaterialTheme.colorScheme.onSurfaceVariant
    val valueColor = MaterialTheme.colorScheme.onBackground
    BoxWithConstraints(contentAlignment = Alignment.Center) {
        val ceiling = MaterialTheme.typography.titleLarge
        val style =
            if (constraints.hasBoundedHeight) {
                // A row is one line at LINE_HEIGHT of the font size.
                val perRow = with(LocalDensity.current) { (maxHeight / rows.size / LINE_HEIGHT).toSp() }
                val size = if (perRow < ceiling.fontSize) perRow else ceiling.fontSize
                TextStyle(fontSize = size, lineHeight = LINE_HEIGHT.em, fontFamily = ceiling.fontFamily)
            } else {
                ceiling.copy(lineHeight = LINE_HEIGHT.em)
            }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (column in 0 until columns) {
                val name = column == 0
                Column(horizontalAlignment = if (name) Alignment.Start else Alignment.End) {
                    rows.forEach { row ->
                        Text(
                            text = row.getOrElse(column) { "" },
                            style = style,
                            maxLines = 1,
                            textAlign = if (name) TextAlign.Start else TextAlign.End,
                            color = if (name) nameColor else valueColor,
                        )
                    }
                }
            }
        }
    }
}

/** Line height as a multiple of the font size: tight, since a row is one
 * short line and the tile is small. */
private const val LINE_HEIGHT = 1.2f
