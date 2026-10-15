package io.raylytics.justmyweather.ui.support

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.raylytics.justmyweather.resources.Res
import io.raylytics.justmyweather.resources.common_done
import io.raylytics.justmyweather.resources.common_report_bug
import io.raylytics.justmyweather.resources.support_attached_app_info
import io.raylytics.justmyweather.resources.support_attached_diagnostics
import io.raylytics.justmyweather.resources.support_collecting
import io.raylytics.justmyweather.resources.support_couldn_find_what
import io.raylytics.justmyweather.resources.support_describe_what_went_wrong
import io.raylytics.justmyweather.resources.support_email_bug_report
import io.raylytics.justmyweather.resources.support_email_idea
import io.raylytics.justmyweather.resources.support_i_tapped_refresh
import io.raylytics.justmyweather.resources.support_idea
import io.raylytics.justmyweather.resources.support_no_email_app_found
import io.raylytics.justmyweather.resources.support_opens_email_app
import io.raylytics.justmyweather.resources.support_submit_idea
import io.raylytics.justmyweather.resources.support_what_happened
import io.raylytics.justmyweather.resources.support_would_great_if
import io.raylytics.justmyweather.support.SUPPORT_ADDRESS
import io.raylytics.justmyweather.support.SupportKind
import io.raylytics.justmyweather.ui.dismissKeyboardOnTap
import io.raylytics.justmyweather.ui.theme.buttonColors
import org.jetbrains.compose.resources.stringResource

/** The words that make the two forms two forms. Kept together so the
 * difference between them reads at a glance. */
private class FormCopy(
    val title: String,
    val intro: String,
    val field: String,
    val placeholder: String,
    val attachedLabel: String,
    val send: String,
)

@Composable
private fun copyFor(kind: SupportKind) =
    when (kind) {
        SupportKind.BUG ->
            FormCopy(
                title = stringResource(Res.string.common_report_bug),
                intro = stringResource(Res.string.support_describe_what_went_wrong),
                field = stringResource(Res.string.support_what_happened),
                placeholder = stringResource(Res.string.support_i_tapped_refresh),
                attachedLabel = stringResource(Res.string.support_attached_diagnostics),
                send = stringResource(Res.string.support_email_bug_report),
            )
        SupportKind.IDEA ->
            FormCopy(
                title = stringResource(Res.string.support_submit_idea),
                intro = stringResource(Res.string.support_couldn_find_what),
                field = stringResource(Res.string.support_idea),
                placeholder = stringResource(Res.string.support_would_great_if),
                attachedLabel = stringResource(Res.string.support_attached_app_info),
                send = stringResource(Res.string.support_email_idea),
            )
    }

/**
 * One form per [kind] — a bug report from App Settings, an idea from the
 * bottom of Customize — each opening the user's email app with its own mail
 * (see support/SupportMail.kt). What is attached is shown in full before
 * anything is sent: a report the user can read is one they can trust.
 *
 * [onSend] hands the message to the edge and answers whether an email app
 * took it; false means none is installed, and the form says where to write
 * instead.
 */
@Composable
fun SupportScreen(
    kind: SupportKind,
    /** Null while still being gathered; sending waits for it. */
    attached: String?,
    onSend: (message: String) -> Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val copy = copyFor(kind)
    // Saveable: rotating the phone mid-sentence must not eat the sentence.
    var message by rememberSaveable { mutableStateOf("") }
    var noMailApp by rememberSaveable { mutableStateOf(false) }
    Surface(modifier = modifier.fillMaxSize().dismissKeyboardOnTap(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .imePadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(copy.title, style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onDone) { Text(stringResource(Res.string.common_done)) }
            }
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = copy.intro,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text(copy.field) },
                    placeholder = { Text(copy.placeholder) },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth().testTag("support-message"),
                )
                // Disabled until there is something to send, rather than an
                // after-the-fact "message required" — the button explains
                // itself by not being pressable.
                Button(
                    onClick = { noMailApp = !onSend(message) },
                    enabled = message.isNotBlank() && attached != null,
                    colors = buttonColors(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(copy.send) }
                Text(
                    text =
                        if (noMailApp) {
                            stringResource(Res.string.support_no_email_app_found, SUPPORT_ADDRESS)
                        } else {
                            stringResource(Res.string.support_opens_email_app, SUPPORT_ADDRESS)
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (noMailApp) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(copy.attachedLabel, style = MaterialTheme.typography.labelMedium)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(
                        text = attached ?: stringResource(Res.string.support_collecting),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
    }
}
