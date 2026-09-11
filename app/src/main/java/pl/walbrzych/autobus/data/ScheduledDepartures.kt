package pl.walbrzych.autobus.data

import java.time.LocalDateTime

/** A static departure expanded using the authoritative KALENDARZ from MyBus. */
data class ScheduledDeparture(
    val timetable: TimetableData,
    val scheduledAt: LocalDateTime,
)

/**
 * Resolves the next static departures with the exact same local MyBus calendar that
 * backs the stop-detail screen. No weekday heuristic is used for downloaded data.
 */
fun nextScheduledDepartures(
    snapshot: ScheduleSnapshot,
    stop: StopData,
    line: String? = null,
    from: LocalDateTime,
    limit: Int = 10,
): List<ScheduledDeparture> {
    require(limit > 0) { "limit musi być dodatni" }
    val result = mutableListOf<ScheduledDeparture>()
    for (offset in 0..7) {
        val date = from.toLocalDate().plusDays(offset.toLong())
        val dayCode = snapshot.calendar[date] ?: continue
        stop.timetables
            .asSequence()
            .filter { it.serviceDayCode == dayCode && (line == null || it.line == line) }
            .flatMap { timetable ->
                timetable.localTimes().asSequence().map { time ->
                    ScheduledDeparture(timetable, LocalDateTime.of(date, time))
                }
            }
            .filter { offset > 0 || !it.scheduledAt.isBefore(from) }
            .forEach(result::add)
        if (result.size >= limit) break
    }
    return result.sortedBy { it.scheduledAt }.take(limit)
}
