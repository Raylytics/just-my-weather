package io.raylytics.justmyweather.whatsnew

import io.raylytics.justmyweather.resources.Res
import io.raylytics.justmyweather.resources.whats_new_0_4_1_modules_body
import io.raylytics.justmyweather.resources.whats_new_0_4_1_modules_title
import io.raylytics.justmyweather.resources.whats_new_0_4_1_units_body
import io.raylytics.justmyweather.resources.whats_new_0_4_1_units_title
import org.jetbrains.compose.resources.StringResource

/*
 * The notes the "What's new" sheet shows, one entry per release that has
 * something worth a user's attention. Not every release needs one: a
 * release with no entry updates silently (WhatsNew.decide).
 *
 * TO ADD A RELEASE'S NOTES: add the strings to composeResources/values/
 * strings.xml as whats_new_<version>_<topic>_title / _body, then one entry
 * at the TOP of [ReleaseNotes.all]. Keep it to what a user would notice or
 * want to try, two or three highlights at most, in the app's plain voice.
 * Fixes and internals belong in docs/RELEASES.md, not here.
 */

/** Where a highlight's button goes. Kept to screens the glance can open. */
enum class HighlightAction { CUSTOMIZE, }

data class Highlight(
    val title: StringResource,
    val body: StringResource,
    /** A button that takes the user to try it, or null for none. */
    val action: HighlightAction? = null,
)

data class ReleaseNote(val version: AppVersion, val highlights: List<Highlight>)

object ReleaseNotes {
    val all: List<ReleaseNote> =
        listOf(
            ReleaseNote(
                AppVersion(0, 4, 1),
                listOf(
                    Highlight(
                        Res.string.whats_new_0_4_1_modules_title,
                        Res.string.whats_new_0_4_1_modules_body,
                        HighlightAction.CUSTOMIZE,
                    ),
                    Highlight(Res.string.whats_new_0_4_1_units_title, Res.string.whats_new_0_4_1_units_body),
                ),
            ),
        )

    val versions: List<AppVersion> get() = all.map { it.version }

    fun of(versions: List<AppVersion>): List<ReleaseNote> =
        versions.mapNotNull { v -> all.firstOrNull { it.version == v } }
}
