package io.raylytics.justmyweather.ui.home

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/*
 * How long ago a reading was taken, in words. Pure — the caller supplies
 * "now" — so it tests on the JVM and the screen only has to decide when to
 * re-ask.
 */

/**
 * "12 min ago" for the gap between [observedAt] and [now], or null when there
 * is nothing honest to say.
 *
 * Coarse on purpose. The unit shrinks the label as the reading ages — minutes
 * while it is current, hours once it is not — because the question this
 * answers is "is this current?", not "how many seconds exactly". Seconds would
 * also make the line twitch every tick for no added meaning.
 *
 * A reading from the future returns null rather than a negative or a cheerful
 * "just now": NWS timestamps are UTC and the device clock is the user's, so a
 * gap that runs backwards means the two disagree, and any age we printed would
 * be wrong by an unknown amount. Up to [FUTURE_TOLERANCE] of it is ordinary
 * skew and reads as "just now"; past that we show the timestamp alone.
 */
object ObservationAge {
    /** How far ahead of us a station may be before we stop claiming an age. */
    val FUTURE_TOLERANCE: Duration = 2.minutes

    fun label(observedAt: Instant, now: Instant): String? {
        val gap = now - observedAt
        val minutes = gap.inWholeMinutes
        return when {
            gap.isNegative() && gap.absoluteValue > FUTURE_TOLERANCE -> null
            minutes < 1 -> "just now"
            minutes < 60 -> "$minutes min ago"
            gap.inWholeHours < 24 -> "${gap.inWholeHours} hr ago"
            gap.inWholeDays == 1L -> "1 day ago"
            else -> "${gap.inWholeDays} days ago"
        }
    }
}
