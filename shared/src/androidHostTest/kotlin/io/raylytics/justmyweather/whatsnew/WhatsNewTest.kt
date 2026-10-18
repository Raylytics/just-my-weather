package io.raylytics.justmyweather.whatsnew

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WhatsNewTest {
    private fun v(text: String) = checkNotNull(AppVersion.parse(text))

    private val notes = listOf(v("0.4.1"), v("0.5.0"), v("0.6.0"), v("0.7.0"))

    @Test
    fun `a version reads from the host's label and orders by number`() {
        assertEquals(AppVersion(0, 4, 1), AppVersion.parse("0.4.1 (build 12)"))
        assertEquals(AppVersion(1, 10, 0), AppVersion.parse("1.10.0-debug"))
        assertNull(AppVersion.parse("dev"))
        assertNull(AppVersion.parse(null))
        assertTrue(v("0.10.0") > v("0.9.9"), "numbers, not text")
    }

    @Test
    fun `a fresh install sees nothing and is recorded at once`() {
        val d = WhatsNew.decide("0.4.1 (build 12)", lastSeen = null, hadEarlierData = false, withNotes = notes)
        assertEquals(emptyList<AppVersion>(), d.show)
        assertTrue(d.recordNow)
    }

    @Test
    fun `an update from 0_4_0, which recorded nothing, sees this release's notes`() {
        val d = WhatsNew.decide("0.4.1 (build 12)", lastSeen = null, hadEarlierData = true, withNotes = notes)
        assertEquals(listOf(v("0.4.1")), d.show)
        assertFalse(d.recordNow, "recorded when the sheet is dismissed, not before")
    }

    @Test
    fun `once seen, the same release shows nothing again`() {
        val d = WhatsNew.decide("0.4.1 (build 13)", lastSeen = "0.4.1", hadEarlierData = true, withNotes = notes)
        assertEquals(emptyList<AppVersion>(), d.show)
        assertFalse(d.recordNow, "already current")
    }

    @Test
    fun `skipped releases show newest first, at most three`() {
        val d = WhatsNew.decide("0.7.0", lastSeen = "0.4.0", hadEarlierData = true, withNotes = notes)
        assertEquals(listOf(v("0.7.0"), v("0.6.0"), v("0.5.0")), d.show)
    }

    @Test
    fun `a release without notes updates quietly and keeps the record current`() {
        val d = WhatsNew.decide("0.4.2", lastSeen = "0.4.1", hadEarlierData = true, withNotes = notes)
        assertEquals(emptyList<AppVersion>(), d.show)
        assertTrue(d.recordNow)
        // Notes written ahead for an unreleased version never show early.
        assertEquals(emptyList<AppVersion>(), WhatsNew.decide("0.4.2", "0.4.2", true, notes).show)
    }

    @Test
    fun `an unreadable version shows and records nothing`() {
        assertEquals(WhatsNewDecision.NOTHING, WhatsNew.decide("dev", null, hadEarlierData = true, withNotes = notes))
    }

    @Test
    fun `every release in the catalog is distinct and says something`() {
        val versions = ReleaseNotes.versions
        assertEquals(versions.toSet().size, versions.size)
        assertTrue(ReleaseNotes.all.all { it.highlights.isNotEmpty() })
        assertTrue(AppVersion(0, 4, 1) in versions)
    }
}
