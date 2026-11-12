package io.raylytics.justmyweather.data

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The stored form of [DayRangeHistory]: per place (a [PointCacheKey]), the
 * few recent days' ranges, as JSON.
 *
 * Every field is defaulted and decoding never throws: an older, partial or
 * corrupt blob yields what it can (a day with an unreadable date is dropped)
 * and at worst nothing, which only means "vs. yesterday" shows a dash until
 * tomorrow. Field names are persistence tokens; never rename one.
 */
object DayRangeHistoryCodec {
    @Serializable
    private data class StoredDay(
        val date: String = "",
        val highF: Double? = null,
        val lowF: Double? = null,
        /** Added in 0.5.0 before it shipped; false (absent) is a forecast. */
        val observed: Boolean = false,
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(history: Map<String, List<DayRange>>): String =
        json.encodeToString(
            history.mapValues { (_, days) ->
                days.map { StoredDay(it.date.toString(), it.highF, it.lowF, it.observed) }
            },
        )

    fun decode(stored: String?): Map<String, List<DayRange>> {
        if (stored.isNullOrBlank()) return emptyMap()
        val raw = runCatching { json.decodeFromString<Map<String, List<StoredDay>>>(stored) }.getOrNull()
        return raw.orEmpty().mapValues { (_, days) ->
            days.mapNotNull { day ->
                runCatching {
                    LocalDate.parse(day.date)
                }.getOrNull()?.let { DayRange(it, day.highF, day.lowF, day.observed) }
            }
        }
    }
}
