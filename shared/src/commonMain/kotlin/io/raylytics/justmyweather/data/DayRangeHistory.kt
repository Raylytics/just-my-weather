package io.raylytics.justmyweather.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/**
 * The app's own record of each day's forecast high and low, per place — the
 * "yesterday" of the vs. yesterday module, which no free source serves after
 * the fact, in NWS territory or out of it.
 *
 * Small on purpose: only the last few days are kept (yesterday is all the
 * module reads; the spare day absorbs a zone change), and only the most
 * recently seen places. A place the app did not look at yesterday has no
 * yesterday, and the module says so with a dash.
 */
class DayRangeHistory(
    private val dataStore: DataStore<Preferences>,
) {
    /**
     * Fold a fresh forecast's [ranges] for the place [placeKey] into the
     * record, keep only the days around [today], and return today's range
     * against yesterday's — or null when today has no range at all.
     */
    suspend fun record(placeKey: String, ranges: List<DayRange>, today: LocalDate): DayComparison? {
        var result: DayComparison? = null
        dataStore.edit { prefs ->
            val history = LinkedHashMap(DayRangeHistoryCodec.decode(prefs[KEY]))
            val days = merge(history.remove(placeKey).orEmpty(), ranges, today)
            // Re-inserted last, so the least recently seen place is the one
            // dropped when the record is full.
            if (days.isNotEmpty()) history[placeKey] = days
            while (history.size > MAX_PLACES) history.remove(history.keys.first())
            prefs[KEY] = DayRangeHistoryCodec.encode(history)
            result = compare(days, today)
        }
        return result
    }

    companion object {
        private val KEY = stringPreferencesKey("day_ranges")

        /** Days before today kept: yesterday, and one spare. */
        private const val KEEP_DAYS_BEFORE = 2

        private const val MAX_PLACES = 16

        /** Pure: the stored [days] updated by [fresh], trimmed to the days the
         * module can use (from [KEEP_DAYS_BEFORE] ago through today). */
        fun merge(days: List<DayRange>, fresh: List<DayRange>, today: LocalDate): List<DayRange> {
            val byDate = days.associateBy { it.date }.toMutableMap()
            for (range in fresh) byDate[range.date] = byDate[range.date]?.updatedBy(range) ?: range
            val earliest = today.minus(DatePeriod(days = KEEP_DAYS_BEFORE))
            return byDate.values.filter { it.date in earliest..today }.sortedBy { it.date }
        }

        /** Pure: today's range beside yesterday's, from a place's [days]. */
        fun compare(days: List<DayRange>, today: LocalDate): DayComparison? {
            val todays = days.firstOrNull { it.date == today } ?: return null
            val yesterday = today.minus(DatePeriod(days = 1))
            return DayComparison(todays, days.firstOrNull { it.date == yesterday })
        }
    }
}
