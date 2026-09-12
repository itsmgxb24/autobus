package pl.walbrzych.autobus.live

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.walbrzych.autobus.data.RealTimeDeparture

class DepartureLiveUpdateRulesTest {
    private val departure = DepartureLiveUpdate(
        cityId = 1,
        stopId = "1020",
        stopName = "Aleja Podwale — Kasztelańska",
        line = "C",
        direction = "Podzamcze",
        scheduledAtMillis = 1_800_000L,
        scheduledSeconds = 30_600,
        tripId = 42,
    )
    private val scheduledAt = Instant.ofEpochMilli(departure.scheduledAtMillis)

    @Test
    fun showsAnEtaOnlyWhenTheServerExplicitlyProvidesOne() {
        val realtime = realTimeDeparture(displayValue = "3 min")

        val presentation = liveUpdatePresentation(departure, scheduledAt, realtime)

        assertEquals("C • 3 min", presentation.statusChip)
        assertEquals("Przyjazd za 3 min", presentation.detail)
    }

    @Test
    fun usesTheScheduledTimeWhenTheVehicleHasNoEta() {
        val realtime = realTimeDeparture(displayValue = "08:30")

        val presentation = liveUpdatePresentation(departure, scheduledAt, realtime)

        assertTrue(presentation.statusChip.startsWith("C • "))
        assertTrue(presentation.detail.startsWith("Planowy przyjazd:"))
    }

    @Test
    fun endsWhenNoLiveDataRemainsAfterTheScheduledDeparture() {
        assertTrue(shouldEndTracking(scheduledAt.plusSeconds(61), scheduledAt, null))
    }

    @Test
    fun keepsTrackingAStillApproachingVehicleButAlwaysHasAHardEnd() {
        val approaching = realTimeDeparture(displayValue = "5 min")

        assertFalse(shouldEndTracking(scheduledAt.plusSeconds(120), scheduledAt, approaching))
        assertTrue(shouldEndTracking(scheduledAt.plusSeconds(601), scheduledAt, approaching))
    }

    private fun realTimeDeparture(displayValue: String) = RealTimeDeparture(
        departureId = 1,
        tripId = 42,
        line = "C",
        direction = "Podzamcze",
        directionCode = "P",
        scheduledSeconds = 30_600,
        displayValue = displayValue,
        status = 0,
        vehicleNumber = null,
    )
}
