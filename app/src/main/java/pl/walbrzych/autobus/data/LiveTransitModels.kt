package pl.walbrzych.autobus.data

import java.time.Instant
import java.time.LocalDate

/** Metadata stored with the active, downloaded MyBus SQLite file. */
data class ScheduleVersion(
    val version: Int,
    val validFrom: String,
    val generation: Int,
)

data class ServiceDay(
    val code: String,
    val label: String,
    val order: Int,
)

data class ScheduleSnapshot(
    val stops: List<StopData>,
    val serviceDays: List<ServiceDay>,
    val calendar: Map<LocalDate, String>,
    val version: ScheduleVersion,
    val lastSuccessfulUpdate: Instant,
)

data class RealTimeDeparture(
    val departureId: Int,
    val tripId: Int,
    val line: String,
    val direction: String,
    val directionCode: String?,
    val scheduledSeconds: Int,
    val displayValue: String,
    val status: Int,
    val vehicleNumber: Int?,
) {
    /** The server gives an ETA only when it explicitly sends the minutes form. */
    val etaMinutes: Int? = Regex("^\\s*(\\d+)\\s*min\\s*$", RegexOption.IGNORE_CASE)
        .matchEntire(displayValue)?.groupValues?.get(1)?.toIntOrNull()

    val arrivalLabel: String = etaMinutes?.let { "Przyjazd za $it min" }
        ?: "Przyjazd: ${displayValue.trim()}"
}

data class RealTimeDepartures(
    val serverTime: String,
    val notice: String?,
    val departures: List<RealTimeDeparture>,
)

/** Response of GetDepartureInfo; it is available only with a planner-provided nUqTripId. */
data class DepartureInfo(
    val departureId: Int,
    val scheduledSeconds: Int,
    val routeVariantId: Int,
    val line: String,
    val directionCode: String?,
    val direction: String,
    val displayValue: String,
    val vehicleNumber: Int?,
    val positionStatus: String,
    val vehicleName: String,
    val status: Int,
)

/** Coordinates are supplied directly by GetVehicles: x=longitude and y=latitude. */
data class LiveVehicle(
    val vehicleId: Long,
    val sideNumber: Int,
    val line: String,
    val variant: String,
    val directionCode: String,
    val latitude: Double,
    val longitude: Double,
    val predictedLatitude: Double?,
    val predictedLongitude: Double?,
    val destination: String,
    val reportedAt: String,
)

sealed interface SyncResult {
    data class Downloaded(val snapshot: ScheduleSnapshot) : SyncResult
    data class Current(val snapshot: ScheduleSnapshot) : SyncResult
}
