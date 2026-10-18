package io.raylytics.justmyweather.ui.whatsnew

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.raylytics.justmyweather.resources.Res
import io.raylytics.justmyweather.resources.whats_new_dismiss
import io.raylytics.justmyweather.resources.whats_new_open_customize
import io.raylytics.justmyweather.resources.whats_new_title
import io.raylytics.justmyweather.resources.whats_new_version
import io.raylytics.justmyweather.whatsnew.HighlightAction
import io.raylytics.justmyweather.whatsnew.ReleaseNote
import org.jetbrains.compose.resources.stringResource

/**
 * The once-per-update sheet over the glance: what changed, in a few lines,
 * and a button to try the headline change. Any way out of it — the button,
 * "Got it", a swipe down or back — counts as seen ([onDismiss]); the action
 * also calls [onAction] so the host can open the screen it names.
 *
 * Built like the detail sheet (ui/home/DetailSheet): fully open, sized to
 * its content, clear of the navigation bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewSheet(
    notes: List<ReleaseNote>,
    onAction: (HighlightAction) -> Unit,
    onDismiss: () -> Unit,
) {
    if (notes.isEmpty()) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // The first action among the notes becomes the sheet's one button: one
    // invitation reads as a suggestion, three read as homework.
    val action = notes.flatMap { it.highlights }.firstNotNullOfOrNull { it.action }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("whatsNewSheet"),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
                    .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(Res.string.whats_new_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            notes.forEach { note ->
                Text(
                    text = stringResource(Res.string.whats_new_version, note.version.toString()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                note.highlights.forEach { highlight ->
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(highlight.title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = stringResource(highlight.body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("whatsNewDismiss")) {
                    Text(stringResource(Res.string.whats_new_dismiss))
                }
                if (action != null) {
                    Button(
                        onClick = {
                            onAction(action)
                            onDismiss()
                        },
                        modifier = Modifier.testTag("whatsNewAction"),
                    ) {
                        Text(
                            when (action) {
                                HighlightAction.CUSTOMIZE -> stringResource(Res.string.whats_new_open_customize)
                            },
                        )
                    }
                }
            }
        }
    }
}
