package io.raylytics.justmyweather.ui.home

import io.raylytics.justmyweather.view.ForecastData
import io.raylytics.justmyweather.view.ForecastMode
import io.raylytics.justmyweather.view.TimesIn
import io.raylytics.justmyweather.view.ViewConfig
import kotlinx.datetime.TimeZone

/*
 * What the glance hands the render besides the config, worked out from the
 * screen's state in one place. Two screens draw modules — the glance, and
 * Customize's preview of the module being edited — and the preview is only
 * honest if it reads the weather the way the glance does.
 */

/** The clock the screen reads in: the phone's or the place's, per the
 * user's setting. [placeZone] is the place's own. */
fun ViewConfig.displayZone(placeZone: TimeZone): TimeZone =
    when (timesIn) {
        TimesIn.DEVICE -> TimeZone.currentSystemDefault()
        TimesIn.PLACE -> placeZone
    }

/** The forecast module's data from this state, in [mode]: the session's
 * framing on the glance, the saved default in the preview. */
fun HomeUiState.Ready.forecastData(mode: ForecastMode = forecastMode): ForecastData =
    ForecastData(
        mode = mode,
        hours = hourly,
        periods = daily,
        error = forecastError,
        placeZone = zone,
        extended = extendedDaily,
    )
