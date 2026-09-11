package pl.walbrzych.autobus.widget

import java.time.LocalDateTime
import pl.walbrzych.autobus.data.ScheduleSnapshot
import pl.walbrzych.autobus.data.ScheduledDeparture
import pl.walbrzych.autobus.data.StopData
import pl.walbrzych.autobus.data.nextScheduledDepartures

/** Static courses for one line, expanded with the same downloaded calendar as the app. */
internal fun selectLineWidgetDepartures(
    snapshot: ScheduleSnapshot,
    stop: StopData,
    line: String,
    now: LocalDateTime,
    limit: Int = 3,
): List<ScheduledDeparture> =
    nextScheduledDepartures(snapshot, stop, line = line, from = now, limit = limit)

data class FavoriteWidgetDeparture(
    val stop: StopData,
    val departure: ScheduledDeparture,
)

/**
 * Merges courses from each saved favourite and retains only the chronologically
 * nearest rows. Fetching [limit] courses per stop is enough to determine the
 * global first [limit] courses.
 */
internal fun selectFavoriteWidgetDepartures(
    snapshot: ScheduleSnapshot,
    favoriteStopIds: Set<String>,
    now: LocalDateTime,
    limit: Int = 3,
): List<FavoriteWidgetDeparture> {
    require(limit > 0) { "limit musi być dodatni" }
    if (favoriteStopIds.isEmpty()) return emptyList()
    return snapshot.stops
        .asSequence()
        .filter { it.id in favoriteStopIds && it.timetables.isNotEmpty() }
        .flatMap { stop ->
            nextScheduledDepartures(snapshot, stop, from = now, limit = limit)
                .asSequence()
                .map { FavoriteWidgetDeparture(stop, it) }
        }
        .sortedBy { it.departure.scheduledAt }
        .take(limit)
        .toList()
}
