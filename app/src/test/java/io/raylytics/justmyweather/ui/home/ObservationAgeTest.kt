package io.raylytics.justmyweather.ui.home

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** The words the glance puts on a reading's age, at every boundary. */
class ObservationAgeTest {
    private val observedAt: Instant = Instant.parse("2026-08-07T12:40:00Z")

    private fun after(duration: Duration) = ObservationAge.label(observedAt, observedAt.plus(duration))

    @Test
    fun `under a minute reads as just now`() {
        assertEquals("just now", after(Duration.ZERO))
        assertEquals("just now", after((59).seconds))
    }

    @Test
    fun `minutes are counted whole, and floor rather than round`() {
        assertEquals("1 min ago", after((60).seconds))
        // 12m59s is still "12 min ago" — a label that rounded up would claim
        // an age the reading has not reached.
        assertEquals("12 min ago", after((12).minutes + (59).seconds))
        assertEquals("59 min ago", after((59).minutes))
    }

    @Test
    fun `an hour in, the unit gets coarser`() {
        assertEquals("1 hr ago", after((60).minutes))
        assertEquals("1 hr ago", after((119).minutes))
        assertEquals("3 hr ago", after((3).hours))
        assertEquals("23 hr ago", after((23).hours))
    }

    @Test
    fun `a day in, coarser still, and singular reads correctly`() {
        assertEquals("1 day ago", after((24).hours))
        assertEquals("1 day ago", after((47).hours))
        assertEquals("2 days ago", after((2).days))
    }

    @Test
    fun `a small clock disagreement is absorbed, a large one is not claimed`() {
        // The station's timestamp is UTC and the clock is the user's; a gap
        // that runs backwards means the two disagree.
        assertEquals("just now", after((-30).seconds))
        assertEquals("just now", after(-ObservationAge.FUTURE_TOLERANCE))
        // Past ordinary skew, any age we printed would be wrong by an unknown
        // amount, so the screen shows the timestamp alone.
        assertNull(after(-ObservationAge.FUTURE_TOLERANCE - (1).seconds))
        assertNull(after((-5).hours))
    }
}
