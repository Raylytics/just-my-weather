package io.raylytics.justmyweather.billing

/**
 * What a store's purchase sheet is shown over: the Activity on Android (Play
 * Billing needs one), the key window's view controller on iOS. The shared
 * code only passes it through from the host to the gateway.
 */
expect open class PurchaseHost
