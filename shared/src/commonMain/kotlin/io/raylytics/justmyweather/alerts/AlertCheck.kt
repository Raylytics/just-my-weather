package io.raylytics.justmyweather.alerts

import io.raylytics.justmyweather.data.AlertRulesRepository
import io.raylytics.justmyweather.data.AlertSettingsRepository
import io.raylytics.justmyweather.data.WeatherRepository
import io.raylytics.justmyweather.location.LocationResolver
import io.raylytics.justmyweather.region.RegionRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import okio.IOException
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * One background alert check, the same on both platforms: evaluate every
 * enabled rule against fresh weather and notify only the rules that just
 * *entered* their fired state (transition dedup via the persisted firing set),
 * then any official safety alert that is new. A quiet install has no rules, so
 * this returns immediately — nothing nags.
 *
 * When it runs is the platform's business — WorkManager on Android
 * (alerts/AlertWorker.kt), BGAppRefreshTask on iOS — and so is how a
 * notification is posted ([AlertNotifications]). Pure decisions live in
 * [AlertEvaluator] and [AlertTransitions]; this is the I/O shell around them.
 */
class AlertCheck(
    private val repository: AlertRulesRepository,
    private val settingsRepository: AlertSettingsRepository,
    private val locationResolver: LocationResolver,
    private val weatherRepository: WeatherRepository,
    private val regionRepository: RegionRepository,
    private val notifications: AlertNotifications,
    /** Read at the edge and handed to the pure path; injectable for tests. */
    private val clock: () -> Instant = { Clock.System.now() },
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    /** What the scheduler should do next: nothing special, or try again soon
     * (a transient network failure — WorkManager's retry with backoff). */
    enum class Outcome { DONE, RETRY }

    suspend fun run(): Outcome {
        val rules = repository.rules.first()
        val settings = settingsRepository.current()
        // Safety alerts are independent of personal rules: someone can want
        // tornado warnings and no rules at all. The old early return here
        // would have skipped them entirely.
        if (rules.none { it.enabled }) {
            repository.setFiringIds(emptySet())
            if (settings.safetyNotifications) notifySafetyAlerts()
            return Outcome.DONE
        }

        // Through the resolver, never a bare fallback: this check runs in the
        // background, where the platform hands a foreground-only app no
        // location at all, and the old `?: DEFAULT` here quietly evaluated
        // every rule against New York. remember=false still, because the
        // reading the home screen opens on belongs to the foreground — a poll
        // working from a remembered location should not overwrite it.
        val location = locationResolver.resolve()
        val snapshot =
            try {
                weatherRepository.load(location, remember = false)
            } catch (_: IOException) {
                return Outcome.RETRY // transient network — try again next backoff
            } catch (_: Exception) {
                return Outcome.DONE // don't hammer on a non-transient failure
            }

        // The forecast is only fetched when a rule actually needs it, and is
        // best-effort: if it fails, current-conditions rules still evaluate and
        // forecast-window rules simply hold until the next tick.
        val forecast =
            if (rules.any { it.enabled && it.window.isForecast }) {
                runCatching { weatherRepository.loadForecast(location) }.getOrDefault(emptyList())
            } else {
                emptyList()
            }

        // Pure transition-dedup: notify only rules that just entered fired. The
        // clock and zone are read here at the edge and handed to the pure path.
        val zone = zone()
        val now = clock()
        // The notification reads in the user's units; the comparison never
        // depends on them (thresholds are stored canonical).
        runCatching { regionRepository.refresh(locationResolver.resolveKnown()) }
        val conventions = runCatching { regionRepository.currentConventions() }.getOrNull()
        val context =
            if (conventions != null) {
                WeatherContext(snapshot, now, forecast, zone, conventions)
            } else {
                WeatherContext(snapshot, now, forecast, zone)
            }
        val outcome = AlertTransitions.compute(rules, context, repository.firingIds())
        // Quiet hours hush the delivery (silent channel), they don't drop alerts.
        val silent = settings.isQuietAt(now.toLocalDateTime(zone).hour)
        outcome.toNotify.forEach { notifications.notify(it.rule, it.decision, silent) }
        repository.setFiringIds(outcome.nowFiring)
        // Fire counts moved on, and a rule that just reached its limit is now
        // off. Applied as per-rule deltas inside one atomic update, never as a
        // write-back of the list read before the fetch: that read is seconds
        // stale by now, and saving it wholesale would resurrect a rule the user
        // deleted meanwhile or drop one they added. A rule the user removed or
        // switched off during the fetch is left alone. A rule switching itself
        // off is not re-synced with WorkManager from in here (cancelling the
        // periodic work from inside its own run is not worth the subtlety); the
        // next launch or edit re-syncs, and until then a poll with nothing
        // enabled returns at the top of run().
        val fired = outcome.toNotify.map { it.rule.id }.toSet()
        if (fired.isNotEmpty()) {
            repository.update { current ->
                current.map { if (it.id in fired && it.enabled && !it.isSpent) it.afterFiring() else it }
            }
        }
        if (settings.safetyNotifications) notifySafetyAlerts()
        return Outcome.DONE
    }

    /**
     * Notify official safety alerts that are NEW since the last tick.
     *
     * Same transition idea as personal rules: a tornado warning standing for
     * six hours should interrupt once, not at every poll. Ids that are no
     * longer active drop out of the set, so a warning that clears and is
     * re-issued notifies again — which is correct, that is a new warning.
     *
     * Best-effort throughout: a failed fetch leaves the stored set untouched
     * rather than clearing it, because clearing would make every standing
     * alert look new on the next successful poll and fire a burst of
     * notifications for hazards the user was already told about.
     */
    private suspend fun notifySafetyAlerts() {
        val location = locationResolver.resolve()
        val active =
            runCatching { SafetyAlerts.filter(weatherRepository.loadActiveAlerts(location)) }
                .getOrNull() ?: return
        val previously = repository.notifiedSafetyIds()
        active.filter { it.id !in previously }.forEach { notifications.notifySafety(it) }
        repository.setNotifiedSafetyIds(active.map { it.id }.toSet())
    }
}
