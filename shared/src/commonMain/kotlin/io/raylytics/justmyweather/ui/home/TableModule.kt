package io.raylytics.justmyweather.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

/**
 * A table module's rows, in columns: the first column (the names) aligned
 * to the start in the quieter colour, every other column aligned to the end
 * so digits stack under digits. Each column is as wide as its widest cell,
 * and every cell shares one style, so the rows line up across columns too.
 *
 * The text is sized to fit the tile both ways, up to the theme's title size:
 * by height, so a one-cell-tall tile fits its rows smaller; and by width, so
 * a wide value ("−12°", "↓10°") shrinks the table rather than being cut off.
 *
 * Screen readers get [text], the rows in reading order, rather than the
 * cells, which a Row of Columns would hand them column by column.
 */
@Composable
internal fun TableModuleContent(rows: List<List<String>>, text: String) {
    val columns = rows.maxOfOrNull { it.size } ?: return
    if (rows.isEmpty()) return
    val nameColor = MaterialTheme.colorScheme.onSurfaceVariant
    val valueColor = MaterialTheme.colorScheme.onBackground
    val ceiling = MaterialTheme.typography.titleLarge.copy(lineHeight = LINE_HEIGHT.em)
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        // What the table measures at the ceiling size: each column as wide as
        // its widest cell, plus the gaps. Text width scales with font size,
        // so one measurement says how far to shrink.
        val gaps = with(density) { (COLUMN_GAP * (columns - 1)).toPx() }
        val naturalWidth =
            (0 until columns).sumOf { column ->
                rows.maxOf { row -> measurer.measure(row.getOrElse(column) { "" }, ceiling).size.width }.toDouble()
            }.toFloat() + gaps
        var scale = 1f
        if (constraints.hasBoundedWidth && naturalWidth > constraints.maxWidth) {
            scale = (constraints.maxWidth - gaps) / (naturalWidth - gaps)
        }
        if (constraints.hasBoundedHeight) {
            // A row is one line at LINE_HEIGHT of the font size.
            val rowHeight = with(density) { ceiling.fontSize.toPx() } * LINE_HEIGHT
            scale = minOf(scale, constraints.maxHeight / (rows.size * rowHeight))
        }
        val fontSize = ceiling.fontSize * minOf(1f, scale)
        val style = TextStyle(fontSize = fontSize, lineHeight = LINE_HEIGHT.em, fontFamily = ceiling.fontFamily)
        Row(
            modifier = Modifier.clearAndSetSemantics { contentDescription = text },
            horizontalArrangement = Arrangement.spacedBy(COLUMN_GAP),
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

private val COLUMN_GAP = 12.dp
