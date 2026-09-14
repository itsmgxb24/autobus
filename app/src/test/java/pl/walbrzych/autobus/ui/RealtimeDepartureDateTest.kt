package pl.walbrzych.autobus.ui

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.walbrzych.autobus.data.RealTimeDeparture
import pl.walbrzych.autobus.data.TransitTime

class RealtimeDepartureDateTest {
    @Test
    fun delayedCourseKeepsTodaysDateInsteadOfMovingToTomorrow() {
        val now = LocalDateTime.of(2026, 9, 13, 12, 10)
        val result = requireNotNull(departureAt(11, 55).scheduledInstantOrNull(now))

        assertEquals(
            LocalDateTime.of(2026, 9, 13, 11, 55),
            result.atZone(TransitTime.zone).toLocalDateTime(),
        )
    }

    @Test
    fun shortlyAfterMidnightCanBelongToTomorrow() {
        val now = LocalDateTime.of(2026, 9, 13, 23, 55)
        val result = requireNotNull(departureAt(0, 5).scheduledInstantOrNull(now))

        assertEquals(
            LocalDateTime.of(2026, 9, 14, 0, 5),
            result.atZone(TransitTime.zone).toLocalDateTime(),
        )
    }

    @Test
    fun invalidClockValueCannotProduceATrackingInstant() {
        val now = LocalDateTime.of(2026, 9, 13, 12, 10)

        assertEquals(null, departureAt(39, 37).scheduledInstantOrNull(now))
    }

    private fun departureAt(hour: Int, minute: Int) = RealTimeDeparture(
        departureId = 1,
        tripId = 1,
        line = "C",
        direction = "Centrum",
        directionCode = null,
        scheduledSeconds = hour * 3_600 + minute * 60,
        displayValue = "5 min",
        status = 0,
        vehicleNumber = 1,
        n = 1,
    )
}
