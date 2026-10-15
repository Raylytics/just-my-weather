package io.raylytics.justmyweather.region

import io.raylytics.justmyweather.data.places.Place
import io.raylytics.justmyweather.data.places.PlaceCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StorefrontsTest {
    @Test
    fun storefrontsReadAsPlayCountries() {
        assertEquals("GB", Storefronts.country("GBR"))
        assertEquals("US", Storefronts.country("usa"))
        assertNull(Storefronts.country("FRA"), "not offered there: fall through to the next clue")
        assertNull(Storefronts.country(null))
    }

    @Test
    fun everyLiveRegionHasItsStorefront() {
        val live = Regions.prepared.filter { it.play == PlayStatus.LIVE }.map { it.code }
        assertTrue(live.isNotEmpty())
        for (code in live) assertTrue(code in Storefronts.known, "$code is live but has no storefront row")
    }

    @Test
    fun aZoneReadsAsItsMostCommonCountry() {
        val catalog =
            PlaceCatalog(
                listOf(
                    Place("London", "", 51.5, -0.1, "GB", "Europe/London"),
                    Place("Leeds", "", 53.8, -1.5, "GB", "Europe/London"),
                    Place("Douglas", "", 54.2, -4.5, "IM", "Europe/London"),
                    Place("Louisville", "KY", 38.2, -85.7),
                ),
            )
        assertEquals("GB", catalog.countryOfZone("Europe/London"))
        assertNull(catalog.countryOfZone("America/New_York"), "US rows carry no zone")
    }
}
