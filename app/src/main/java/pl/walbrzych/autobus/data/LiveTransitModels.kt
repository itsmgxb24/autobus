package pl.walbrzych.autobus.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.Duration
import java.time.format.DateTimeFormatter

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

/** A date can activate multiple GTFS service IDs, while MyBus has one day code. */
fun ScheduleSnapshot.activeServiceCodes(date: LocalDate): Set<String> =
    calendar[date]
        ?.split(SERVICE_CODE_SEPARATOR)
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        ?.toSet()
        .orEmpty()

const val SERVICE_CODE_SEPARATOR = "|"

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
    /** Raw `n` from GetTimeTableReal; absent, blank and malformed values are zero. */
    val n: Int = 0,
) {
    /** A valid MyBus clock value belongs to one calendar day. */
    val hasInvalidScheduledTime: Boolean
        get() = scheduledSeconds !in 0 until SECONDS_PER_DAY

    /** The server gives an ETA only when it explicitly sends the minutes form. */
    val etaMinutes: Int? = Regex("^\\s*(\\d+)\\s*min\\s*$", RegexOption.IGNORE_CASE)
        .matchEntire(displayValue)?.groupValues?.get(1)?.toIntOrNull()

    /** MyBus uses this literal for a live course that is less than one minute away. */
    val hasSubMinuteEta: Boolean = Regex("^\\s*<\\s*1\\s*min\\s*$", RegexOption.IGNORE_CASE)
        .matches(displayValue)

    val arrivalLabel: String = etaMinutes?.let { "Przyjazd za $it min" }
        ?: "Przyjazd: ${displayValue.trim()}"

    /**
     * Wording used exclusively by the real-time rows on the stop-detail screen.
     * A non-zero `n` marks an API response eligible to show its ETA, but only for
     * a future course strictly closer than thirty minutes.
     */
    fun stopDetailDepartureLabel(serverTime: LocalTime): String {
        val scheduledTime = scheduledTimeOrNull() ?: return "Odjazd: —"
        var secondsUntilDeparture = Duration.between(serverTime, scheduledTime).seconds
        // The API provides only a clock value. Around midnight, a small early-morning
        // time can belong to the following service day rather than a course already gone.
        if (secondsUntilDeparture <= 0 && serverTime.hour >= 18 && scheduledTime.hour < 6) {
            secondsUntilDeparture += 24 * 60 * 60
        }
        if (n != 0 && secondsUntilDeparture in 1 until THIRTY_MINUTES_SECONDS) {
            if (hasSubMinuteEta) return "Odjazd za <1 min"
            etaMinutes?.let { return "Odjazd za $it min" }
        }
        return "Odjazd: ${displayTimeOrScheduledTime(scheduledTime)}"
    }

    /** The UI uses this only for the explicit live '<1 min' state. */
    fun shouldBlinkSubMinuteEta(clock: LocalTime): Boolean =
        n != 0 && hasSubMinuteEta && secondsUntil(clock) in 1..SUB_MINUTE_BLINK_SECONDS

    private fun secondsUntil(clock: LocalTime): Long {
        val scheduledTime = scheduledTimeOrNull() ?: return Long.MIN_VALUE
        var seconds = Duration.between(clock, scheduledTime).seconds
        if (seconds <= 0 && clock.hour >= 18 && scheduledTime.hour < 6) seconds += SECONDS_PER_DAY
        return seconds
    }

    private fun displayTimeOrScheduledTime(scheduledTime: LocalTime): String =
        displayValue.toLocalTimeOrNull()?.format(TIME_FORMAT)
            ?: scheduledTime.format(TIME_FORMAT)

    fun scheduledTimeOrNull(): LocalTime? =
        scheduledSeconds.takeIf { it in 0 until SECONDS_PER_DAY }
            ?.let { LocalTime.ofSecondOfDay(it.toLong()) }

    private fun String.toLocalTimeOrNull(): LocalTime? = runCatching { LocalTime.parse(trim(), TIME_FORMAT) }.getOrNull()

    private companion object {
        const val SECONDS_PER_DAY = 24 * 60 * 60
        const val THIRTY_MINUTES_SECONDS = 30 * 60L
        const val SUB_MINUTE_BLINK_SECONDS = 30L
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
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
    /** Current course (`ik`) reported by GetVehicles, if the server supplies it. */
    val activeCourseId: Long? = null,
    /** Next course (`nk`), which must not be treated as the currently active course. */
    val nextCourseId: Long? = null,
    val status: String = "",
)

/** Human destination from GetVehicles `op`, without MyBus' optional via-stops suffix. */
fun LiveVehicle.destinationLabel(): String = destination.trim()
    .split(Regex("\\s+przez\\s+", RegexOption.IGNORE_CASE), limit = 2)
    .firstOrNull()
    .orEmpty()
    .trim()

sealed interface SyncResult {
    data class Downloaded(val snapshot: ScheduleSnapshot) : SyncResult
    data class Current(val snapshot: ScheduleSnapshot) : SyncResult
}
