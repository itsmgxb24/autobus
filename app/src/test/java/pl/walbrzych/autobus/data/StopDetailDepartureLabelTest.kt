package pl.walbrzych.autobus.data

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class StopDetailDepartureLabelTest {
    @Test
    fun showsEtaOnlyForANonZeroNAndAFutureDepartureInsideThirtyMinutes() {
        val departure = departure(n = 312, scheduledSeconds = 12 * 3_600 + 29 * 60, displayValue = "11 min")

        assertEquals("Odjazd za 11 min", departure.stopDetailDepartureLabel(LocalTime.of(12, 0)))
    }

    @Test
    fun exactlyThirtyMinutesUsesTheClockTime() {
        val departure = departure(n = 312, scheduledSeconds = 12 * 3_600 + 30 * 60, displayValue = "11 min")

        assertEquals("Odjazd: 12:30", departure.stopDetailDepartureLabel(LocalTime.of(12, 0)))
    }

    @Test
    fun zeroNAndPastDeparturesUseTheClockTime() {
        val withoutLiveFlag = departure(n = 0, scheduledSeconds = 12 * 3_600 + 10 * 60, displayValue = "6 min")
        val past = departure(n = 312, scheduledSeconds = 11 * 3_600 + 59 * 60, displayValue = "1 min")

        assertEquals("Odjazd: 12:10", withoutLiveFlag.stopDetailDepartureLabel(LocalTime.of(12, 0)))
        assertEquals("Odjazd: 11:59", past.stopDetailDepartureLabel(LocalTime.of(12, 0)))
    }

    private fun departure(n: Int, scheduledSeconds: Int, displayValue: String) = RealTimeDeparture(
        departureId = 1,
        tripId = 1,
        line = "4",
        direction = "Centrum",
        directionCode = null,
        scheduledSeconds = scheduledSeconds,
        displayValue = displayValue,
        status = 0,
        vehicleNumber = n.takeIf { it > 0 },
        n = n,
    )
}
