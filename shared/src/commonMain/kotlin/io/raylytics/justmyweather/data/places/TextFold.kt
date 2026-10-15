package io.raylytics.justmyweather.data.places

/** [text] in Unicode canonical decomposition (NFD): "ó" becomes "o" plus a
 * combining accent, which [PlaceCatalog.normalize] then drops. */
internal expect fun decomposeCanonically(text: String): String
