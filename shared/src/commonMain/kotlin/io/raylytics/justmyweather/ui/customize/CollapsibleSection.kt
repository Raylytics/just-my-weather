package io.raylytics.justmyweather.ui.customize

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.raylytics.justmyweather.resources.Res
import io.raylytics.justmyweather.resources.customize_collapse
import io.raylytics.justmyweather.resources.customize_collapsed
import io.raylytics.justmyweather.resources.customize_expand
import io.raylytics.justmyweather.resources.customize_expanded
import org.jetbrains.compose.resources.stringResource

/**
 * One thing on the Customize screen, folded to a line: its name, what it is
 * set to now, and a chevron. A tap on the line opens every setting it has.
 *
 * The screen used to lay every control open at once — four sliders, a dozen
 * chip rows, a text box and two size rows per field — and the field list,
 * which is what people come to edit, was a long scroll away. Folded, the
 * whole screen fits in about a screenful, and the [summary] answers "what is
 * it now?" without opening anything.
 *
 * [trailing] sits on the line itself, outside the fold, for the one control
 * that must not need a tap first: a field's on/off switch. It takes its own
 * taps, so flipping it does not also open the section.
 *
 * Open or folded survives rotation and process death (rememberSaveable) but
 * not leaving the screen: Customize opens folded every time, which is the
 * point of it. Call inside `key(...)` when sections can move — the field list
 * reorders — so the state follows the section, not its position.
 */
@Composable
internal fun CollapsibleSection(
    title: String,
    summary: String?,
    /** testTag for the header line, so UI tests can open the section. */
    tag: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    // The chevron turns rather than swaps glyphs: one character, so it needs
    // no icon dependency, and the turn says which way the section went.
    val turn by animateFloatAsState(if (expanded) 90f else 0f)
    val state = stringResource(if (expanded) Res.string.customize_expanded else Res.string.customize_collapsed)
    val action = stringResource(if (expanded) Res.string.customize_collapse else Res.string.customize_expand)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClickLabel = action) { expanded = !expanded }
                    .semantics { stateDescription = state }
                    .padding(vertical = 12.dp)
                    .testTag(tag),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                summary?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            trailing?.invoke()
            Text(
                text = "›",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(turn).clearAndSetSemantics { },
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
    }
}
