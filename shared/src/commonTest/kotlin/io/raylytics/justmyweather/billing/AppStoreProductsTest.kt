package io.raylytics.justmyweather.billing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppStoreProductsTest {
    @Test
    fun removeAdsHasItsOwnAppStoreId() {
        // Fixed in App Store Connect and never changeable: a typo here sells nothing.
        assertEquals("remove_ads_just_my_weather", AppStoreProducts.appStoreId(REMOVE_ADS_PRODUCT_ID))
        assertEquals(REMOVE_ADS_PRODUCT_ID, AppStoreProducts.sharedId("remove_ads_just_my_weather"))
    }

    @Test
    fun anotherProductIsNotRemoveAds() {
        // open-frame owns a bare `remove_ads` on the same developer account.
        assertNull(AppStoreProducts.sharedId("remove_ads"))
        assertNull(AppStoreProducts.sharedId("something_else"))
    }
}
