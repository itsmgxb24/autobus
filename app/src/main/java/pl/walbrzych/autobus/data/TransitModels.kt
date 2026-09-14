package pl.ruby.lubiechowlabs.autobus.data

import org.json.JSONObject
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class TransitFile(val stops: List<StopData>)

data class StopData(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val timetables: List<TimetableData>,
    val publicNumber: String? = null,
)

data class TimetableData(
    val line: String,
    val direction: String,
    val dayType: DayType,
    val times: List<String>,
    /** Code and label come from DNI/KALENDARZ in a downloaded MyBus database. */
    val serviceDayCode: String = dayType.name,
    val serviceDayLabel: String = dayType.polishName,
    val variant: String = "",
    val directionCode: String = "",
    /** Ordered stop identifiers from KIERUNKI.trasa. */
    val routeStopIds: List<String> = emptyList(),
    val stopOrder: Int? = null,
    /**
     * Absolute GTFS seconds from the start of the service day.  Unlike the MyBus
     * clock strings, GTFS validly uses values such as 24:05 for after-midnight
     * journeys belonging to the preceding service day.
     */
    val departureSeconds: List<Int> = emptyList(),
)

/** A departure expanded onto a concrete calendar date. */
data class Departure(
    val line: String,
    val direction: String,
    val dayType: DayType,
    val scheduledAt: LocalDateTime,
)

enum class DayType(val polishName: String) {
    WORKING("Roboczy"),
    VACATION("Wakacyjny"),
    SUNDAY_HOLIDAY("Niedziele i święta"),
}

object StopsJsonParser {
    fun parse(source: String): TransitFile {
        val root = JSONObject(source)
        val stops = root.getJSONArray("stops")
        return TransitFile(List(stops.length()) { stopIndex ->
            val stop = stops.getJSONObject(stopIndex)
            val tables = stop.getJSONArray("timetables")
            StopData(
                id = stop.getString("id"),
                name = stop.getString("name"),
                latitude = stop.getDouble("latitude"),
                longitude = stop.getDouble("longitude"),
                timetables = List(tables.length()) { tableIndex ->
                    val table = tables.getJSONObject(tableIndex)
                    val times = table.getJSONArray("times")
                    TimetableData(
                        line = table.getString("line"),
                        direction = table.getString("direction"),
                        dayType = DayType.valueOf(table.getString("dayType")),
                        times = List(times.length()) { timeIndex -> times.getString(timeIndex) },
                    )
                },
            )
        })
    }
}

object TransitDataValidator {
    private val timeFormat = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

    fun validate(data: TransitFile): List<String> = buildList {
        if (data.stops.isEmpty()) add("Brak przystanków w rozkładzie.")
        if (data.stops.map { it.id }.distinct().size != data.stops.size) add("Identyfikatory przystanków nie są unikalne.")
        data.stops.forEach { stop ->
            if (stop.latitude !in -90.0..90.0 || stop.longitude !in -180.0..180.0) {
                add("Przystanek ${stop.id} ma nieprawidłowe współrzędne.")
            }
            if (stop.timetables.isEmpty()) add("Przystanek ${stop.id} nie ma rozkładu.")
            if (!stop.timetables.map { it.dayType }.containsAll(DayType.entries)) {
                add("Przystanek ${stop.id} nie zawiera wszystkich trzech typów dni.")
            }
            stop.timetables.forEach { table ->
                if (table.line.isBlank() || table.direction.isBlank() || table.times.isEmpty()) {
                    add("Niekompletny rozkład ${stop.id}.")
                }
                table.times.filterNot { it.matches(timeFormat) }.forEach {
                    add("Nieprawidłowa godzina $it przy ${stop.id}.")
                }
            }
        }
    }
}

fun distanceMetres(
    firstLatitude: Double,
    firstLongitude: Double,
    secondLatitude: Double,
    secondLongitude: Double,
): Int {
    val earthRadiusMetres = 6_371_000.0
    val latitudeDelta = Math.toRadians(secondLatitude - firstLatitude)
    val longitudeDelta = Math.toRadians(secondLongitude - firstLongitude)
    val a = sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
        cos(Math.toRadians(firstLatitude)) * cos(Math.toRadians(secondLatitude)) *
        sin(longitudeDelta / 2) * sin(longitudeDelta / 2)
    return (earthRadiusMetres * 2 * atan2(sqrt(a), sqrt(1 - a))).toInt()
}

fun StopData.distanceTo(latitude: Double, longitude: Double): Int =
    distanceMetres(this.latitude, this.longitude, latitude, longitude)

data class ServiceTime(val seconds: Int) {
    init {
        require(seconds >= 0) { "Czas kursu nie może być ujemny." }
    }

    val localTime: LocalTime get() = LocalTime.ofSecondOfDay((seconds % SECONDS_PER_DAY).toLong())
    val dayOffset: Long get() = (seconds / SECONDS_PER_DAY).toLong()
}

private const val SECONDS_PER_DAY = 24 * 60 * 60

/** Returns service-day-aware times for both legacy MyBus and standard GTFS schedules. */
fun TimetableData.serviceTimes(): List<ServiceTime> =
    departureSeconds.takeIf { it.isNotEmpty() }
        ?.filter { it >= 0 }
        ?.map(::ServiceTime)
        ?: times.map(LocalTime::parse).map { ServiceTime(it.toSecondOfDay()) }

fun TimetableData.localTimes(): List<LocalTime> = serviceTimes().map(ServiceTime::localTime)

/**
 * Returns the next departures from this stop using only the selected local timetable.
 *
 * Times earlier than [from]'s time are assigned to the following calendar day.  The
 * explicit [dayType] is retained across that midnight boundary; callers should choose
 * the appropriate type when requesting a new service day (holiday calendars are not
 * represented in the local fixture).
 */
fun StopData.nextDepartures(
    from: LocalDateTime,
    dayType: DayType,
    limit: Int = 10,
): List<Departure> {
    require(limit > 0) { "limit musi być dodatni" }
    return timetables
        .asSequence()
        .filter { it.dayType == dayType }
        .flatMap { timetable ->
            timetable.localTimes().asSequence().map { time ->
                val serviceDate = if (time >= from.toLocalTime()) {
                    from.toLocalDate()
                } else {
                    from.toLocalDate().plusDays(1)
                }
                Departure(timetable.line, timetable.direction, dayType, LocalDateTime.of(serviceDate, time))
            }
        }
        .sortedWith(compareBy<Departure> { it.scheduledAt }.thenBy { it.line }.thenBy { it.direction })
        .take(limit)
        .toList()
}

/** Named form useful to data-layer callers that do not use extension syntax. */
fun nearestDepartures(
    stop: StopData,
    from: LocalDateTime,
    dayType: DayType,
    limit: Int = 10,
): List<Departure> = stop.nextDepartures(from, dayType, limit)
