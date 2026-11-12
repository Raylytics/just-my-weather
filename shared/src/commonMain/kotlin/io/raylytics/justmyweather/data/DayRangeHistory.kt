package io.raylytics.justmyweather.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * The app's own record of each day's high and low, per place — the
 * "yesterday" of the vs. yesterday module. In NWS territory yesterday is
 * what the station measured, fetched once and kept here ([DayRange.observed]);
 * abroad, or when that fetch fails, it is the forecast recorded while
 * yesterday was still ahead, since MET Norway serves no history.
 *
 * The days AHEAD are kept too, not only the past ones, because a fetch does
 * not always say anything about today: abroad, MET Norway's daily fold drops
 * today once the morning has gone, and by the evening NWS lists "Tonight"
 * but no "Today". A day recorded while it was still ahead is still there
 * when it comes round. Small all the same: a few days back (yesterday, and a
 * spare for a zone change), a forecast's worth ahead, and only the most
 * recently seen places. A place the app never saw before yesterday has no
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

    /** Whether the record already holds what a station measured on [date]
     * at [placeKey]: a finished day does not change, so it is fetched once. */
    suspend fun hasObserved(placeKey: String, date: LocalDate): Boolean =
        DayRangeHistoryCodec.decode(dataStore.data.first()[KEY])[placeKey].orEmpty()
            .any { it.date == date && it.observed }

    companion object {
        private val KEY = stringPreferencesKey("day_ranges")

        /** Days before today kept: yesterday, and one spare. */
        private const val KEEP_DAYS_BEFORE = 2

        /** Days after today kept: as far as either source forecasts. */
        private const val KEEP_DAYS_AFTER = 9

        private const val MAX_PLACES = 16

        /** Pure: the stored [days] updated by [fresh], trimmed to the days the
         * module can use, now or once they come round. */
        fun merge(days: List<DayRange>, fresh: List<DayRange>, today: LocalDate): List<DayRange> {
            val byDate = days.associateBy { it.date }.toMutableMap()
            for (range in fresh) byDate[range.date] = byDate[range.date]?.updatedBy(range) ?: range
            val earliest = today.minus(DatePeriod(days = KEEP_DAYS_BEFORE))
            val latest = today.plus(DatePeriod(days = KEEP_DAYS_AFTER))
            return byDate.values.filter { it.date in earliest..latest }.sortedBy { it.date }
        }

        /** Pure: today's range beside yesterday's, from a place's [days]. */
        fun compare(days: List<DayRange>, today: LocalDate): DayComparison? {
            val todays = days.firstOrNull { it.date == today } ?: return null
            val yesterday = today.minus(DatePeriod(days = 1))
            return DayComparison(todays, days.firstOrNull { it.date == yesterday })
        }
    }
}
