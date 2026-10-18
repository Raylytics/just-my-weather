package io.raylytics.justmyweather.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.raylytics.justmyweather.whatsnew.AppVersion
import io.raylytics.justmyweather.whatsnew.ReleaseNote
import io.raylytics.justmyweather.whatsnew.ReleaseNotes
import io.raylytics.justmyweather.whatsnew.WhatsNew
import kotlinx.coroutines.flow.first

/**
 * Which release's "What's new" the user has seen. The decision itself is
 * [WhatsNew.decide]; this reads what it needs from the settings file and
 * writes the answer back.
 */
class WhatsNewRepository(
    private val dataStore: DataStore<Preferences>,
) {
    /**
     * The notes to show for [current] (the host's version label), newest
     * first, or none. Called once at launch. A fresh install is recorded
     * here, before any of this launch's own writes can make it look like an
     * update — see [EARLIER_APP_KEYS].
     */
    suspend fun pending(current: String): List<ReleaseNote> {
        val prefs = dataStore.data.first()
        val decision =
            WhatsNew.decide(
                current = current,
                lastSeen = prefs[SEEN],
                hadEarlierData = EARLIER_APP_KEYS.any { prefs.contains(it) },
                withNotes = ReleaseNotes.versions,
            )
        if (decision.recordNow) markSeen(current)
        return ReleaseNotes.of(decision.show)
    }

    /** The sheet was dismissed: don't show these notes again. */
    suspend fun markSeen(current: String) {
        val version = AppVersion.parse(current) ?: return
        dataStore.edit { it[SEEN] = version.toString() }
    }

    private companion object {
        val SEEN = stringPreferencesKey("whats_new_seen")

        /**
         * Keys only an earlier run of the app can have written by the time
         * this launch first reads the file: things the user set, and caches
         * that need a location and a network round trip. Left out on purpose
         * are the ones a fresh install writes in its first second (the
         * region's clues, the last location, the ads entitlement), which
         * would make every new user look like an upgrading one.
         */
        val EARLIER_APP_KEYS =
            listOf(
                "view_config",
                "theme_config",
                "alert_rules",
                "alert_settings",
                "saved_places",
                "last_snapshot",
                "point_cache",
            ).map(::stringPreferencesKey)
    }
}
