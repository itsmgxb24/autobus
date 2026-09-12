package pl.walbrzych.autobus.data

/**
 * Chooses a single vehicle for a real-time departure without mistaking the
 * server's next-course field (`nk`) for an active course.
 */
fun selectVehicleForDeparture(
    vehicles: List<LiveVehicle>,
    sideNumber: Int,
    departureId: Int?,
): LiveVehicle? {
    val sideNumberMatches = vehicles.filter { it.sideNumber == sideNumber }
    if (sideNumberMatches.isEmpty()) return null

    if (departureId != null) {
        val activeCourseMatches = sideNumberMatches.filter {
            it.activeCourseId == departureId.toLong()
        }
        if (activeCourseMatches.isNotEmpty()) {
            return activeCourseMatches.singleOrNull()?.takeIf(LiveVehicle::hasUsablePosition)
        }

        // The side number remains a useful fallback when the server has not yet
        // aligned its course identifiers. `nk` is explicitly excluded: it describes
        // a future course, not a vehicle serving this departure right now.
        val activeSideNumberMatches = sideNumberMatches.filter {
            it.nextCourseId != departureId.toLong()
        }
        return activeSideNumberMatches.singleOrNull()?.takeIf(LiveVehicle::hasUsablePosition)
    }

    return sideNumberMatches.singleOrNull()?.takeIf(LiveVehicle::hasUsablePosition)
}

/** Latitude is y and longitude is x in GetVehicles. */
fun LiveVehicle.hasUsablePosition(): Boolean =
    latitude in -90.0..90.0 &&
        longitude in -180.0..180.0 &&
        !(latitude == 0.0 && longitude == 0.0)
