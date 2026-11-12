package io.raylytics.justmyweather.view

import io.raylytics.justmyweather.data.WeatherSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import kotlin.time.Instant

/** Customize's module preview: [renderModule] must draw what [render] would. */
class RenderModuleTest {
    private val wind = ModuleKey.Reading(WeatherField.WIND)
    private val snapshot =
        WeatherSnapshot(
            locationLabel = "Test, ST",
            temperatureF = 71.6,
            conditions = "Mostly Clear",
            windMph = 12.0,
            precipitationIn = 0.0,
            pressureInHg = 29.92,
            observedAt = Instant.parse("2026-06-24T18:00:00Z"),
        )

    @Test
    fun `a shown module previews exactly as the glance renders it`() {
        val config = ViewConfig.DEFAULT.toggle(wind).relabel(wind, "Breeze").resize(wind, ModuleSize(3, 2))
        val onGlance = config.render(snapshot, conventions = Conventions.US).modules.single { it.module == wind }
        assertEquals(onGlance, config.renderModule(wind, snapshot, conventions = Conventions.US))
    }

    @Test
    fun `a hidden module still previews, so it can be judged before it is switched on`() {
        val config = ViewConfig.DEFAULT
        assertFalse(config.shows(wind))
        val preview = config.renderModule(wind, snapshot, conventions = Conventions.US)!!
        assertEquals(
            WeatherField.WIND.format(snapshot, Conventions.US),
            (preview.content as ModuleContent.Reading).text,
        )
        assertEquals(wind.defaultSize, preview.size)
    }

    @Test
    fun `a collapsible module previews open, at its own size`() {
        val config = ViewConfig.DEFAULT.setCollapsible(ModuleKey.Forecast, true)
        val preview = config.renderModule(ModuleKey.Forecast, snapshot, conventions = Conventions.US)!!
        assertFalse(preview.collapsed)
        assertEquals(config.items.single { it.module == ModuleKey.Forecast }.size, preview.size)
    }

    @Test
    fun `an edit shows in the next preview`() {
        val before = ViewConfig.DEFAULT.renderModule(ModuleKey.Sun, snapshot, conventions = Conventions.US)!!
        val after =
            ViewConfig.DEFAULT
                .resize(ModuleKey.Sun, ModuleSize(2, 1))
                .renderModule(ModuleKey.Sun, snapshot, conventions = Conventions.US)!!
        assertEquals(ModuleSize(4, 2), before.size)
        assertEquals(ModuleSize(2, 1), after.size)
    }
}
