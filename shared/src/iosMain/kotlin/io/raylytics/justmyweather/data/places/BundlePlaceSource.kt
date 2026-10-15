package io.raylytics.justmyweather.data.places

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSBundle
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile

/** iOS twin of AssetPlaceSource: the same places.tsv, bundled with the app
 * (iosApp/project.yml copies it from app/src/main/assets). */
object BundlePlaceSource {
    @OptIn(ExperimentalForeignApi::class)
    suspend fun load(): PlaceCatalog =
        withContext(Dispatchers.IO) {
            val path = NSBundle.mainBundle.pathForResource("places", ofType = "tsv") ?: error("places.tsv not bundled")
            val text =
                NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null) ?: error("places.tsv unreadable")
            PlaceCatalog(PlaceCatalog.parse(text.lineSequence()))
        }
}
