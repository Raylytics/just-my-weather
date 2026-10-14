package io.raylytics.justmyweather.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.raylytics.justmyweather.resources.Res
import io.raylytics.justmyweather.resources.home_collapse
import io.raylytics.justmyweather.view.ModuleKey
import org.jetbrains.compose.resources.stringResource

/*
 * The two faces of a collapsible module (ModuleSetting.collapsible). Folded,
 * a module is one cell carrying only its name and a caret — the tile is the
 * button, and a tap on it opens the module to its configured size. Open, it
 * carries a small control to fold it back, drawn in its corner (or in the
 * forecast's header, beside the framing toggle). Both live here rather than
 * in ModuleGrid so the forecast, which draws its own header, can share the
 * control with every other tile instead of growing a lookalike.
 */

/**
 * The folded tile's content: the module's name, fitted to the cell, over
 * the caret the place chooser uses to say "this opens". The label is the
 * whole content, not a caption, so it reads in the value's colour rather
 * than the caption grey.
 */
@Composable
internal fun CollapsedModuleContent(label: String) {
    val style = MaterialTheme.typography.labelLarge
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FittedText(
            text = label,
            style = style,
            ceiling = style.fontSize,
            floor = LABEL_FLOOR,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
        )
        Text(
            text = "▾",
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/**
 * The control that folds an open module back to its cell: the caret turned
 * up, with its own tap so the grid's tap-for-details never sees it. Small
 * on purpose — it is the way back, not the point of the tile — and a screen
 * reader also has the tile's own Collapse action.
 */
@Composable
internal fun CollapseControl(
    module: ModuleKey,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(Res.string.home_collapse)
    Text(
        text = "▴",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            modifier
                .clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = label, onClick = onCollapse)
                .semantics { contentDescription = label }
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag("collapse_${module.key}"),
    )
}
