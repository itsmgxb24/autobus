package pl.ruby.lubiechowlabs.autobus

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.ruby.lubiechowlabs.autobus.data.DayType
import pl.ruby.lubiechowlabs.autobus.data.ScheduleSnapshot
import pl.ruby.lubiechowlabs.autobus.data.ScheduleVersion
import pl.ruby.lubiechowlabs.autobus.data.StopData
import pl.ruby.lubiechowlabs.autobus.data.TimetableData
import pl.ruby.lubiechowlabs.autobus.data.nextScheduledDepartures

class ScheduledDeparturesTest {
    private val monday = LocalDate.of(2026, 9, 14)
    private val stop = StopData(
        id = "42",
        name = "Testowy",
        latitude = 50.0,
        longitude = 16.0,
        timetables = listOf(
            TimetableData("EX", "Centrum", DayType.WORKING, listOf("08:05", "08:20"), serviceDayCode = "R"),
            TimetableData("4", "Dworzec", DayType.WORKING, listOf("08:10"), serviceDayCode = "R"),
            TimetableData("EX", "Centrum", DayType.SUNDAY_HOLIDAY, listOf("08:07"), serviceDayCode = "S"),
        ),
    )
    private val snapshot = ScheduleSnapshot(
        stops = listOf(stop),
        serviceDays = emptyList(),
        calendar = mapOf(monday to "R", monday.plusDays(1) to "S"),
        version = ScheduleVersion(1, "2026-09-14", 1),
        lastSuccessfulUpdate = Instant.EPOCH,
    )

    @Test
    fun resolvesOnlyTheChosenLineUsingTheCalendarCode() {
        val departures = nextScheduledDepartures(
            snapshot = snapshot,
            stop = stop,
            line = "EX",
            from = LocalDateTime.of(monday, java.time.LocalTime.of(8, 0)),
        )

        assertEquals(listOf("08:05", "08:20", "08:07"), departures.map { it.scheduledAt.toLocalTime().toString() })
        assertEquals(listOf("EX", "EX", "EX"), departures.map { it.timetable.line })
    }

    @Test
    fun rollsOverToTheNextCalendarDayAfterTheLastDeparture() {
        val departure = nextScheduledDepartures(
            snapshot = snapshot,
            stop = stop,
            line = "EX",
            from = LocalDateTime.of(monday, java.time.LocalTime.of(8, 21)),
            limit = 1,
        ).single()

        assertEquals(monday.plusDays(1), departure.scheduledAt.toLocalDate())
        assertEquals("08:07", departure.scheduledAt.toLocalTime().toString())
    }
}
