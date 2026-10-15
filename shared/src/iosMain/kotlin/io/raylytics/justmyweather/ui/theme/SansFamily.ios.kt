package io.raylytics.justmyweather.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import io.raylytics.justmyweather.resources.Res
import io.raylytics.justmyweather.resources.roboto_bold
import io.raylytics.justmyweather.resources.roboto_light
import io.raylytics.justmyweather.resources.roboto_medium
import io.raylytics.justmyweather.resources.roboto_regular
import org.jetbrains.compose.resources.Font

/** Bundled Roboto, in the four weights the type scale uses. */
@Composable
actual fun sansFamily(): FontFamily =
    FontFamily(
        Font(Res.font.roboto_light, FontWeight.Light),
        Font(Res.font.roboto_regular, FontWeight.Normal),
        Font(Res.font.roboto_medium, FontWeight.Medium),
        Font(Res.font.roboto_bold, FontWeight.Bold),
    )
