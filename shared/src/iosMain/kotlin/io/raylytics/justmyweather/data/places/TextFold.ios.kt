package io.raylytics.justmyweather.data.places

import platform.Foundation.NSString
import platform.Foundation.decomposedStringWithCanonicalMapping

@Suppress("CAST_NEVER_SUCCEEDS")
internal actual fun decomposeCanonically(text: String): String = (text as NSString).decomposedStringWithCanonicalMapping
