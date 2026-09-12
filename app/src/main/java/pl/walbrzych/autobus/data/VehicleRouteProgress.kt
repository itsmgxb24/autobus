package pl.walbrzych.autobus.data

/**
 * The complete downloaded, ordered route that can be tied unambiguously to a
 * vehicle returned by GetVehicles.
 */
data class VehicleRouteProgress(
    val directionLabel: String,
    val routeStops: List<StopData>,
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
    return VehicleRouteProgress(directionLabel, routeStops)
}
