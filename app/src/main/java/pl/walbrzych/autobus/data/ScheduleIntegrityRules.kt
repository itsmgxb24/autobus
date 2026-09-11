package pl.walbrzych.autobus.data

/** Database-independent checks shared by SQLite validation and JVM tests. */
object ScheduleIntegrityRules {
    fun requireCompleteCounts(stops: Int, lines: Int, variants: Int, timetables: Int) {
        require(stops > 0 && variants > 0 && timetables > 0 && lines > 0) {
            "Pobrana baza nie zawiera pełnego rozkładu."
        }
    }

    fun requireNoOrphanDepartures(orphanStopReferences: Int) {
        require(orphanStopReferences == 0) { "ODJAZDY ma osierocone przystanki." }
    }

    fun requireKnownRouteStops(routeStopIds: Collection<String>, stopIds: Set<String>) {
        require(routeStopIds.all(stopIds::contains)) {
            "Baza zawiera wariant trasy z nieistniejącym przystankiem."
        }
    }
}
