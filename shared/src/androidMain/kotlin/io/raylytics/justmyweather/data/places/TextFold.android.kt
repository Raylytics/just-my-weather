package io.raylytics.justmyweather.data.places

import java.text.Normalizer

internal actual fun decomposeCanonically(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
