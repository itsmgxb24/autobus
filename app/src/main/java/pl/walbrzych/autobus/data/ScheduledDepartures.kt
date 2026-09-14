package pl.ruby.lubiechowlabs.autobus.data

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
    // Start on the preceding service day: GTFS times may correctly be 24:xx.
    for (offset in -1..7) {
        val date = from.toLocalDate().plusDays(offset.toLong())
        val dayCodes = snapshot.activeServiceCodes(date)
        if (dayCodes.isEmpty()) continue
        stop.timetables
            .asSequence()
            .filter { it.serviceDayCode in dayCodes && (line == null || it.line == line) }
            .flatMap { timetable ->
                timetable.serviceTimes().asSequence().map { time ->
                    ScheduledDeparture(timetable, LocalDateTime.of(date, time.localTime).plusDays(time.dayOffset))
                }
            }
            .filter { !it.scheduledAt.isBefore(from) }
            .forEach(result::add)
        if (result.size >= limit) break
    }
    return result.sortedBy { it.scheduledAt }.take(limit)
}
