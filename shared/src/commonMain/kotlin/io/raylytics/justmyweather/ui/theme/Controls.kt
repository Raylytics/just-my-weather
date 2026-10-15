package io.raylytics.justmyweather.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

// Material 3 1.4 (the Compose Multiplatform line the iOS port needs) re-tuned
// a few defaults toward fainter outlines and greyer disabled states. These pin
// the controls this app uses to the look it shipped with on 1.3 — one place,
// so a future "adopt the new defaults" decision is one file, not a hunt.

/**
 * The outline every [FilterChip] draws. 1.4 moved the unselected default from
 * `outline` to the much fainter `outlineVariant`; the chips are the app's main
 * pickers, so they keep their contrast. Pass as `border = chipBorder(selected)`.
 */
@Composable
fun chipBorder(selected: Boolean): BorderStroke =
    FilterChipDefaults.filterChipBorder(
        enabled = true,
        selected = selected,
        borderColor = MaterialTheme.colorScheme.outline,
    )

/**
 * Colors for a [TextButton] that can be disabled. 1.4 draws the disabled label
 * in `onSurfaceVariant` rather than `onSurface` (both at 38%), which on this
 * palette greys "Add" and the reorder arrows out further than they shipped.
 */
@Composable
fun textButtonColors(): ButtonColors =
    ButtonDefaults.textButtonColors(
        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    )

/** [textButtonColors] for a filled [Button]: 1.4 also thinned its disabled fill (12% → 10%). */
@Composable
fun buttonColors(): ButtonColors =
    ButtonDefaults.buttonColors(
        disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    )

/**
 * The faint outline an in-content button wears, so a word that does something
 * reads as a button rather than as a label (the Hourly/Daily toggle, bare,
 * looked like a caption). Muted ink at low alpha rather than `outlineVariant`,
 * which in Material's baseline is tinted purple against this neutral palette.
 * A header's "Done" stays bare: it is navigation, where a plain word is the
 * platform convention.
 */
@Composable
fun subtleBorder(enabled: Boolean = true): BorderStroke =
    BorderStroke(
        width = 1.dp,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 0.3f else 0.12f),
    )
