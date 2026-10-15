package io.raylytics.justmyweather.alerts

import io.raylytics.justmyweather.data.nws.ActiveAlert

/**
 * Posting a notification, which each platform does its own way: a
 * NotificationManager channel on Android (app/…/alerts/AlertNotifier.kt), the
 * UNUserNotificationCenter on iOS (iosApp/…/Platform/Notifier.swift). The
 * shared [AlertCheck] decides WHAT to say and WHEN; this only delivers it.
 */
interface AlertNotifications {
    /** A personal rule just fired. [silent] during quiet hours: deliver, but
     * without sound or a heads-up. */
    fun notify(rule: AlertRule, decision: FireDecision, silent: Boolean = false)

    /** An official safety alert (tornado, severe storm…) that is new since the
     * last check. These ignore quiet hours. */
    fun notifySafety(alert: ActiveAlert)
}
