package io.raylytics.justmyweather.whatsnew

/*
 * When the "What's new" sheet appears, decided as pure data so every case
 * is settled on the JVM: a version string in, the releases to show out.
 *
 * The rule (Evan, 2026-10-18): after an update that carries notes, the
 * glance opens once with a sheet saying what changed; dismissing it records
 * the version, so it never comes back for that release. A fresh install
 * sees nothing here: someone who has never used the app has nothing that
 * changed (the first-run guide, just-my-weather-n61, is for them).
 *
 * 0.4.0 and earlier never stored a "seen" version, so the first launch of a
 * build with this code cannot read one. It tells the two apart by whether
 * the earlier app left settings behind ([WhatsNewRepository]): a phone with
 * them is an update from [BASELINE], one without is a fresh install.
 */

/** A release name, "0.4.1". Only the numbers count: the host's label
 * ("0.4.1 (build 12)") and any suffix after them are ignored. */
data class AppVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(this, other, AppVersion::major, AppVersion::minor, AppVersion::patch)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val LEADING = Regex("""^\s*(\d+)\.(\d+)\.(\d+)""")

        fun parse(text: String?): AppVersion? =
            text?.let(LEADING::find)?.destructured?.let { (major, minor, patch) ->
                AppVersion(major.toInt(), minor.toInt(), patch.toInt())
            }
    }
}

/** What to do at launch: which releases' notes to show (newest first), and
 * whether to record [current] as seen right away. Recording waits for the
 * dismissal when there is something to show. */
data class WhatsNewDecision(val show: List<AppVersion>, val recordNow: Boolean) {
    companion object {
        val NOTHING = WhatsNewDecision(emptyList(), recordNow = false)
    }
}

object WhatsNew {
    /** The last release before this feature: an update with no recorded
     * version came from here. */
    val BASELINE = AppVersion(0, 4, 0)

    /** At most this many releases at once, newest first: someone who skipped
     * a year of updates wants the latest, not a history lesson. */
    const val MAX_RELEASES = 3

    /**
     * @param current the running app's version label.
     * @param lastSeen the version recorded the last time, or null if none was.
     * @param hadEarlierData whether an earlier app left settings behind.
     * @param withNotes every release that has notes, in any order.
     */
    fun decide(
        current: String,
        lastSeen: String?,
        hadEarlierData: Boolean,
        withNotes: Collection<AppVersion>,
    ): WhatsNewDecision {
        val now = AppVersion.parse(current) ?: return WhatsNewDecision.NOTHING
        val seen = AppVersion.parse(lastSeen)
        val since =
            when {
                seen != null -> seen
                // Nothing recorded and nothing left behind: a fresh install.
                // Record silently so this question is never asked again.
                !hadEarlierData -> return WhatsNewDecision(emptyList(), recordNow = true)
                else -> BASELINE
            }
        val show = withNotes.filter { it > since && it <= now }.sortedDescending().take(MAX_RELEASES)
        // Nothing new to say (a release with no notes, or a downgrade): keep
        // the record current so the next comparison starts from here.
        return WhatsNewDecision(show, recordNow = show.isEmpty() && seen != now)
    }
}
