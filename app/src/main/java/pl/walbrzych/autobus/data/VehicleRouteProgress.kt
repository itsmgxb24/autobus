package pl.walbrzych.autobus.data

/**
 * The complete downloaded, ordered route that can be tied unambiguously to a
 * vehicle returned by GetVehicles.
 */
data class VehicleRouteProgress(
    val directionLabel: String,
    val routeStops: List<StopData>,
    val nextStopId: String,
)

fun vehicleRouteProgress(
    snapshot: ScheduleSnapshot,
    departureStopId: String,
    line: String,
    vehicle: LiveVehicle,
): VehicleRouteProgress? {
    val departureStop = snapshot.stops.firstOrNull { it.id == departureStopId } ?: return null
    val matchingTables = departureStop.timetables.filter { timetable ->
        timetable.line == line &&
            departureStopId in timetable.routeStopIds &&
            (vehicle.directionCode.isBlank() || timetable.directionCode == vehicle.directionCode) &&
            (vehicle.variant.isBlank() || timetable.variant == vehicle.variant)
    }.distinctBy { timetable ->
        listOf(timetable.variant, timetable.directionCode, timetable.direction, timetable.routeStopIds).joinToString("|")
    }
    val timetable = matchingTables.singleOrNull() ?: return null
    val departureStopIndex = timetable.routeStopIds.indexOf(departureStopId)
    if (departureStopIndex < 0) return null

    val stopsById = snapshot.stops.associateBy { it.id }
    val routeStops = timetable.routeStopIds.mapNotNull(stopsById::get)
    if (routeStops.isEmpty()) return null

    val directionLabel = timetable.direction.takeIf(String::isNotBlank)
        ?: vehicle.destinationLabel().takeIf(String::isNotBlank)
        ?: timetable.variant
    return VehicleRouteProgress(
        directionLabel = directionLabel,
        routeStops = routeStops,
        nextStopId = routeStops[nextRouteStopIndex(routeStops, vehicle)].id,
    )
}

/**
 * Finds the next stop from the projection of the vehicle onto the ordered route.
 * It avoids treating the stop opened by the user as "next" after the vehicle has
 * already passed it.
 */
internal fun nextRouteStopIndex(routeStops: List<StopData>, vehicle: LiveVehicle): Int {
    if (routeStops.size <= 1) return 0
    val longitudeScale = kotlin.math.cos(Math.toRadians(vehicle.latitude))
    var bestSegment = 0
    var bestProgress = 0.0
    var bestDistanceSquared = Double.POSITIVE_INFINITY
    routeStops.zipWithNext().forEachIndexed { index, (from, to) ->
        val ax = (from.longitude - vehicle.longitude) * longitudeScale
        val ay = from.latitude - vehicle.latitude
        val bx = (to.longitude - vehicle.longitude) * longitudeScale
        val by = to.latitude - vehicle.latitude
        val lengthSquared = (bx - ax) * (bx - ax) + (by - ay) * (by - ay)
        val progress = if (lengthSquared == 0.0) 0.0 else {
            ((-ax) * (bx - ax) + (-ay) * (by - ay)) / lengthSquared
        }.coerceIn(0.0, 1.0)
        val dx = ax + (bx - ax) * progress
        val dy = ay + (by - ay) * progress
        val distanceSquared = dx * dx + dy * dy
        if (distanceSquared < bestDistanceSquared) {
            bestSegment = index
            bestProgress = progress
            bestDistanceSquared = distanceSquared
        }
    }
    return if (bestProgress <= 0.03) bestSegment else (bestSegment + 1).coerceAtMost(routeStops.lastIndex)
}
