package pl.walbrzych.autobus.widget

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.walbrzych.autobus.data.DayType
import pl.walbrzych.autobus.data.ScheduleSnapshot
import pl.walbrzych.autobus.data.ScheduleVersion
import pl.walbrzych.autobus.data.StopData
import pl.walbrzych.autobus.data.TimetableData

class DepartureWidgetSelectionTest {
    private val date = LocalDate.of(2026, 9, 14)
    private val stop = StopData(
        id = "widget-stop",
        name = "Testowy",
        latitude = 50.0,
        longitude = 16.0,
        timetables = listOf(
            TimetableData("8", "Piaskowa Góra", DayType.WORKING, listOf("08:03", "08:30"), serviceDayCode = "R"),
            TimetableData("EX", "Podzamcze", DayType.WORKING, listOf("08:05", "08:15"), serviceDayCode = "R"),
            TimetableData("4", "Sobęcin", DayType.WORKING, listOf("08:10"), serviceDayCode = "R"),
            TimetableData("9", "Biały Kamień", DayType.WORKING, listOf("08:20"), serviceDayCode = "R"),
        ),
    )
    private val snapshot = ScheduleSnapshot(
        stops = listOf(stop),
        serviceDays = emptyList(),
        calendar = mapOf(date to "R"),
        version = ScheduleVersion(1, "2026-09-14", 1),
        lastSuccessfulUpdate = Instant.EPOCH,
    )

    @Test
    fun skipUsesTheFirstFourDeparturesAcrossAllLines() {
        val departures = selectWidgetScheduledDepartures(snapshot, stop, emptySet(), LocalDateTime.of(date, java.time.LocalTime.of(8, 0)))

        assertEquals(listOf("8", "EX", "4", "EX"), departures.map { it.timetable.line })
        assertEquals(listOf("08:03", "08:05", "08:10", "08:15"), departures.map { it.scheduledAt.toLocalTime().toString() })
    }

    @Test
    fun selectedLinesAreTheOnlyLinesShown() {
        val departures = selectWidgetScheduledDepartures(snapshot, stop, setOf("EX", "9"), LocalDateTime.of(date, java.time.LocalTime.of(8, 0)))

        assertEquals(listOf("EX", "EX", "9"), departures.map { it.timetable.line })
    }

    @Test
    fun lineSeriesUsesOnlyTheChosenLineAndKeepsItsChronologicalCourses() {
        val departures = selectLineWidgetDepartures(
            snapshot,
            stop,
            "EX",
            LocalDateTime.of(date, java.time.LocalTime.of(8, 0)),
        )

        assertEquals(listOf("08:05", "08:15"), departures.map { it.scheduledAt.toLocalTime().toString() })
        assertEquals(listOf("EX", "EX"), departures.map { it.timetable.line })
    }

    @Test
    fun favouritesDashboardMergesStopsAndShowsTheEarliestCoursesGlobally() {
        val secondStop = StopData(
            id = "favourite-stop",
            name = "Drugi ulubiony",
            latitude = 50.1,
            longitude = 16.1,
            timetables = listOf(
                TimetableData("50", "Podzamcze", DayType.WORKING, listOf("08:02", "08:12"), serviceDayCode = "R"),
            ),
        )
        val departures = selectFavoriteWidgetDepartures(
            snapshot.copy(stops = listOf(stop, secondStop)),
            setOf(stop.id, secondStop.id),
            LocalDateTime.of(date, java.time.LocalTime.of(8, 0)),
        )

        assertEquals(listOf("50", "8", "EX"), departures.map { it.departure.timetable.line })
        assertEquals(listOf("Drugi ulubiony", "Testowy", "Testowy"), departures.map { it.stop.name })
    }
}
