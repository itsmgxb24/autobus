package pl.walbrzych.autobus.data

import java.time.LocalDateTime

/** One scheduled vehicle ride in an itinerary produced from the downloaded MyBus data. */
data class PlannedLeg(
    val from: StopData,
    val to: StopData,
    val line: String,
    val direction: String,
    val departureAt: LocalDateTime,
    val arrivalAt: LocalDateTime,
    val stopCount: Int?,
)

/** A trip can contain one automatic interchange; [stageBreakAfter] marks an explicit stopover. */
data class PlannedJourney(
    val legs: List<PlannedLeg>,
    val stageBreakAfter: Int? = null,
) {
    init {
        require(legs.isNotEmpty()) { "Trasa musi zawierać co najmniej jeden etap." }
    }

    val departureAt: LocalDateTime get() = legs.first().departureAt
    val arrivalAt: LocalDateTime get() = legs.last().arrivalAt
}

/**
 * Small, deterministic router backed only by the current local timetable. It does
 * not invent transfer times: an arrival is matched to the corresponding indexed
 * departure at a downstream stop in the same MyBus route variant.
 */
object TransitPlanner {
    private const val MAX_DAYS_TO_SEARCH = 7
    private const val MIN_TRANSFER_MINUTES = 2L
    private const val MAX_TRANSFER_STOPS = 24
    private const val MAX_FIRST_LEGS = 12

    fun findJourneys(
        snapshot: ScheduleSnapshot,
        fromStopId: String,
        toStopId: String,
        departureAt: LocalDateTime,
        stopoverId: String? = null,
        limit: Int = 3,
    ): List<PlannedJourney> {
        require(limit > 0) { "limit musi być dodatni" }
        val from = snapshot.stops.firstOrNull { it.id == fromStopId } ?: return emptyList()
        val to = snapshot.stops.firstOrNull { it.id == toStopId } ?: return emptyList()
        if (from.id == to.id) return emptyList()
        val stopover = stopoverId?.let { id -> snapshot.stops.firstOrNull { it.id == id } } ?: return findSegmentJourneys(
            snapshot = snapshot,
            from = from,
            to = to,
            departureAt = departureAt,
            limit = limit,
        )
        if (stopover.id == from.id || stopover.id == to.id) return emptyList()

        return findSegmentJourneys(snapshot, from, stopover, departureAt, limit)
            .asSequence()
            .flatMap { firstStage ->
                findSegmentJourneys(
                    snapshot = snapshot,
                    from = stopover,
                    to = to,
                    departureAt = firstStage.arrivalAt.plusMinutes(MIN_TRANSFER_MINUTES),
                    limit = limit,
                ).asSequence().map { secondStage ->
                    PlannedJourney(
                        legs = firstStage.legs + secondStage.legs,
                        stageBreakAfter = firstStage.legs.size,
                    )
                }
            }
            .sortedBy(PlannedJourney::arrivalAt)
            .distinctBy { journey -> journey.legs.joinToString { "${it.line}:${it.departureAt}:${it.to.id}" } }
            .take(limit)
            .toList()
    }

    private fun findSegmentJourneys(
        snapshot: ScheduleSnapshot,
        from: StopData,
        to: StopData,
        departureAt: LocalDateTime,
        limit: Int,
    ): List<PlannedJourney> {
        val direct = findDirectLegs(snapshot, from, to, departureAt, limit)
        if (direct.isNotEmpty()) return direct.map { PlannedJourney(listOf(it)) }

        val stopsById = snapshot.stops.associateBy(StopData::id)
        val transferStops = from.timetables
            .asSequence()
            .flatMap { timetable ->
                val fromIndex = timetable.routeStopIds.indexOf(from.id)
                if (fromIndex < 0) emptySequence()
                else timetable.routeStopIds.drop(fromIndex + 1).asSequence()
            }
            .filter { it != to.id && it != from.id }
            .distinct()
            .mapNotNull(stopsById::get)
            .take(MAX_TRANSFER_STOPS)
            .toList()

        val firstLegs = transferStops
            .flatMap { transfer -> findDirectLegs(snapshot, from, transfer, departureAt, limit = 3) }
            .sortedBy(PlannedLeg::departureAt)
            .take(MAX_FIRST_LEGS)

        return firstLegs
            .asSequence()
            .flatMap { firstLeg ->
                findDirectLegs(
                    snapshot,
                    firstLeg.to,
                    to,
                    firstLeg.arrivalAt.plusMinutes(MIN_TRANSFER_MINUTES),
                    limit = 1,
                ).asSequence().map { secondLeg -> PlannedJourney(listOf(firstLeg, secondLeg)) }
            }
            .sortedBy(PlannedJourney::arrivalAt)
            .distinctBy { journey -> journey.legs.joinToString { "${it.line}:${it.departureAt}:${it.to.id}" } }
            .take(limit)
            .toList()
    }

    private fun findDirectLegs(
        snapshot: ScheduleSnapshot,
        from: StopData,
        to: StopData,
        departureAt: LocalDateTime,
        limit: Int,
    ): List<PlannedLeg> {
        val candidates = mutableListOf<PlannedLeg>()
        for (dayOffset in 0..MAX_DAYS_TO_SEARCH) {
            val serviceDate = departureAt.toLocalDate().plusDays(dayOffset.toLong())
            val serviceDayCode = snapshot.calendar[serviceDate] ?: continue
            from.timetables
                .asSequence()
                .filter { it.serviceDayCode == serviceDayCode }
                .forEach { sourceTable ->
                    val destinationTables = to.timetables.filter { destinationTable ->
                        destinationTable.serviceDayCode == serviceDayCode &&
                            isSameVehicleRun(sourceTable, destinationTable) &&
                            isDownstream(sourceTable, destinationTable, from.id, to.id)
                    }
                    if (destinationTables.isEmpty()) return@forEach
                    sourceTable.localTimes().forEachIndexed { index, sourceTime ->
                        val depart = LocalDateTime.of(serviceDate, sourceTime)
                        if (depart.isBefore(departureAt)) return@forEachIndexed
                        destinationTables.forEach { destinationTable ->
                            val arrivalTime = destinationTable.localTimes().getOrNull(index) ?: return@forEach
                            val arrivalDate = if (arrivalTime < sourceTime) serviceDate.plusDays(1) else serviceDate
                            val stopCount = routeStopCount(sourceTable, destinationTable, from.id, to.id)
                            candidates += PlannedLeg(
                                from = from,
                                to = to,
                                line = sourceTable.line,
                                direction = sourceTable.direction,
                                departureAt = depart,
                                arrivalAt = LocalDateTime.of(arrivalDate, arrivalTime),
                                stopCount = stopCount,
                            )
                        }
                    }
                }
            if (candidates.size >= limit) break
        }
        return candidates
            .sortedWith(compareBy<PlannedLeg> { it.departureAt }.thenBy { it.arrivalAt }.thenBy { it.line })
            .distinctBy { "${it.line}:${it.direction}:${it.departureAt}:${it.arrivalAt}" }
            .take(limit)
    }

    private fun isSameVehicleRun(source: TimetableData, destination: TimetableData): Boolean =
        source.line == destination.line &&
            source.variant == destination.variant &&
            (source.directionCode.isBlank() || destination.directionCode.isBlank() || source.directionCode == destination.directionCode)

    private fun isDownstream(source: TimetableData, destination: TimetableData, fromId: String, toId: String): Boolean {
        val sourceIndex = source.routeStopIds.indexOf(fromId)
        val destinationIndex = source.routeStopIds.indexOf(toId)
        if (sourceIndex >= 0 && destinationIndex > sourceIndex) return true
        val sourceOrder = source.stopOrder
        val destinationOrder = destination.stopOrder
        return sourceOrder != null && destinationOrder != null && destinationOrder > sourceOrder
    }

    private fun routeStopCount(source: TimetableData, destination: TimetableData, fromId: String, toId: String): Int? {
        val sourceIndex = source.routeStopIds.indexOf(fromId)
        val destinationIndex = source.routeStopIds.indexOf(toId)
        if (sourceIndex >= 0 && destinationIndex > sourceIndex) return destinationIndex - sourceIndex
        return source.stopOrder?.let { sourceOrder -> destination.stopOrder?.minus(sourceOrder) }
    }
}
