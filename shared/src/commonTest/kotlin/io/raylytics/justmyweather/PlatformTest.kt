package io.raylytics.justmyweather

import kotlin.test.Test
import kotlin.test.assertNotNull

class PlatformTest {
    @Test
    fun everyTargetNamesItsPlatform() {
        assertNotNull(currentPlatform)
    }
}
