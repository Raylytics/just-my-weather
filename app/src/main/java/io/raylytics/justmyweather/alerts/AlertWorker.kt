package io.raylytics.justmyweather.alerts

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.raylytics.justmyweather.JustMyWeatherApp
import java.util.concurrent.TimeUnit

/**
 * When the alert check runs on Android: periodically through WorkManager while
 * there is anything to watch, and once on launch or a rule change. The check
 * itself — fetch, evaluate, dedupe, notify — is [AlertCheck], shared with the
 * iOS app's background refresh.
 */
class AlertWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        when ((applicationContext as JustMyWeatherApp).container.alertCheck.run()) {
            AlertCheck.Outcome.DONE -> Result.success()
            AlertCheck.Outcome.RETRY -> Result.retry()
        }

    companion object {
        private const val UNIQUE_NAME = "personal-alerts"
        private const val ONCE_NAME = "personal-alerts-once"

        /**
         * Schedule or cancel the periodic check. [hasWork] must be true when
         * ANY reason to poll exists — an enabled personal rule OR safety-alert
         * notifications — so a quiet install does zero background work while a
         * user who wants only tornado warnings still gets them.
         *
         * Taking a single computed flag rather than the rule list is
         * deliberate: it was `hasEnabledRules`, and safety notifications with
         * no personal rules would have cancelled the very worker that delivers
         * them. The worker still self-guards as a safety net.
         */
        fun sync(context: Context, hasWork: Boolean, pollMinutes: Int) {
            if (hasWork) schedule(context, pollMinutes) else cancel(context)
        }

        /** UPDATE (not KEEP) so re-scheduling on launch preserves the existing
         * schedule when the interval is unchanged, but a changed cadence takes
         * effect — the standard way to retune periodic work. */
        fun schedule(context: Context, pollMinutes: Int) {
            val request =
                PeriodicWorkRequestBuilder<AlertWorker>(pollMinutes.toLong(), TimeUnit.MINUTES)
                    .setConstraints(
                        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                    )
                    .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        /** Stop the hourly check — when the last enabled rule is removed or
         * disabled, so background work tracks the user actually wanting it. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }

        /** A single immediate check — on app launch and after a rule is added or
         * enabled — so a freshly-added rule gives timely feedback instead of
         * waiting for the next hourly tick. Unique + KEEP means a burst of
         * changes coalesces into one run (which reads the latest rules) and the
         * one-time check serialises against itself, avoiding the duplicate-fetch
         * and double-notify race that concurrent runs would risk. Transition
         * dedup still applies, so an already-firing condition won't re-notify. */
        fun runOnce(context: Context) {
            val request =
                OneTimeWorkRequestBuilder<AlertWorker>()
                    .setConstraints(
                        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                    )
                    .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(ONCE_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
