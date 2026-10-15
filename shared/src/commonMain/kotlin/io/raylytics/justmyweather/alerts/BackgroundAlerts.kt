package io.raylytics.justmyweather.alerts

/**
 * How dependably the phone runs the background alert check. Android's
 * WorkManager runs it on the schedule the user picked. iOS decides for itself
 * when an app's background refresh runs — often, rarely, or (with Background
 * App Refresh off) never — so on iPhone the Alerts screen says so (plan
 * decision 2: alerts kept, best effort, with a warning).
 */
enum class BackgroundAlerts {
    /** Runs on schedule; nothing to say. */
    RELIABLE,

    /** Runs when the system allows; checks may be late. */
    BEST_EFFORT,

    /** Background App Refresh is off for the app: no checks until the app is opened. */
    REFRESH_OFF,
}
