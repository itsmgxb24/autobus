package pl.ruby.lubiechowlabs.autobus.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class GtfsScheduleTimeTest {
    @Test
    fun expandsAnAfterMidnightGtfsJourneyOntoTheFollowingCivilDate() {
        val serviceDate = LocalDate.of(2026, 9, 14)
        val table = TimetableData(
            line = "A", direction = "Centrum", dayType = DayType.WORKING, times = listOf("24:05"),
            serviceDayCode = "weekday", departureSeconds = listOf(86_700),
        )
        val stop = StopData("s", "Test", 53.4, 14.5, listOf(table))
        val snapshot = ScheduleSnapshot(
            stops = listOf(stop), serviceDays = listOf(ServiceDay("weekday", "Roboczy", 0)),
            calendar = mapOf(serviceDate to "weekday"),
            version = ScheduleVersion(1, "test", 1), lastSuccessfulUpdate = Instant.EPOCH,
        )

        val departure = nextScheduledDepartures(
            snapshot, stop, from = LocalDateTime.of(2026, 9, 15, 0, 0), limit = 1,
        ).single()

        assertEquals(LocalDateTime.of(2026, 9, 15, 0, 5), departure.scheduledAt)
    }
}
