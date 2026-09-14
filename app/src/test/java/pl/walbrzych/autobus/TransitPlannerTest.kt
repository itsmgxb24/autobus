package pl.ruby.lubiechowlabs.autobus

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import pl.ruby.lubiechowlabs.autobus.data.DayType
import pl.ruby.lubiechowlabs.autobus.data.ScheduleSnapshot
import pl.ruby.lubiechowlabs.autobus.data.ScheduleVersion
import pl.ruby.lubiechowlabs.autobus.data.StopData
import pl.ruby.lubiechowlabs.autobus.data.TimetableData
import pl.ruby.lubiechowlabs.autobus.data.TransitPlanner

class TransitPlannerTest {
    private val serviceDate = LocalDate.of(2026, 9, 14)
    private val start = StopData(
        id = "A",
        name = "Start",
        latitude = 50.0,
        longitude = 16.0,
        timetables = listOf(table("1", "Dworzec", listOf("08:00"), listOf("A", "B"))),
    )
    private val interchange = StopData(
        id = "B",
        name = "Przesiadka",
        latitude = 50.1,
        longitude = 16.1,
        timetables = listOf(
            table("1", "Dworzec", listOf("08:10"), listOf("A", "B")),
            table("2", "Podzamcze", listOf("08:15"), listOf("B", "C")),
        ),
    )
    private val destination = StopData(
        id = "C",
        name = "Cel",
        latitude = 50.2,
        longitude = 16.2,
        timetables = listOf(table("2", "Podzamcze", listOf("08:30"), listOf("B", "C"))),
    )
    private val snapshot = ScheduleSnapshot(
        stops = listOf(start, interchange, destination),
        serviceDays = emptyList(),
        calendar = mapOf(serviceDate to "R"),
        version = ScheduleVersion(1, "2026-09-14", 1),
        lastSuccessfulUpdate = Instant.EPOCH,
    )

    @Test
    fun findsAChronologicalDirectJourneyFromTheRouteVariant() {
        val journey = TransitPlanner.findJourneys(snapshot, "A", "B", at("07:50")).firstOrNull()

        assertNotNull(journey)
        assertEquals(listOf("1"), journey!!.legs.map { it.line })
        assertEquals(LocalDateTime.of(serviceDate, LocalTime.of(8, 0)), journey.departureAt)
        assertEquals(LocalDateTime.of(serviceDate, LocalTime.of(8, 10)), journey.arrivalAt)
    }

    @Test
    fun findsOneRealInterchangeWhenThereIsNoDirectVariant() {
        val journey = TransitPlanner.findJourneys(snapshot, "A", "C", at("07:50")).firstOrNull()

        assertNotNull(journey)
        assertEquals(listOf("1", "2"), journey!!.legs.map { it.line })
        assertEquals("Przesiadka", journey.legs.first().to.name)
        assertEquals(LocalDateTime.of(serviceDate, LocalTime.of(8, 30)), journey.arrivalAt)
    }

    @Test
    fun explicitStopoverSplitsTheRouteIntoTwoStages() {
        val journey = TransitPlanner.findJourneys(snapshot, "A", "C", at("07:50"), stopoverId = "B").firstOrNull()

        assertNotNull(journey)
        assertEquals(1, journey!!.stageBreakAfter)
        assertEquals(listOf("1", "2"), journey.legs.map { it.line })
    }

    private fun at(time: String): LocalDateTime = LocalDateTime.of(serviceDate, LocalTime.parse(time))

    private fun table(line: String, direction: String, times: List<String>, route: List<String>): TimetableData =
        TimetableData(
            line = line,
            direction = direction,
            dayType = DayType.WORKING,
            times = times,
            serviceDayCode = "R",
            variant = line,
            directionCode = line,
            routeStopIds = route,
        )
}
