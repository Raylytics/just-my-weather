package io.raylytics.justmyweather.data

import kotlin.math.PI

// java.lang.Math's conversions, for code that runs on both platforms: the
// same constant multiply the JDK uses, so results are bit-for-bit the same.

internal fun toRadians(degrees: Double): Double = degrees * (PI / 180.0)

internal fun toDegrees(radians: Double): Double = radians * (180.0 / PI)
