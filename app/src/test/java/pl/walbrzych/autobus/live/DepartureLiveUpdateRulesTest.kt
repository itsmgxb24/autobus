package pl.ruby.lubiechowlabs.autobus.live

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.ruby.lubiechowlabs.autobus.data.RealTimeDeparture

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
    fun keepsTrackingThroughANetworkOutageAfterTheScheduledDeparture() {
        assertFalse(shouldEndTracking(scheduledAt.plusSeconds(61), scheduledAt, serverResponded = false, realtime = null))
    }

    @Test
    fun endsWhenTheServerConfirmsNoEtaAfterTheScheduledDeparture() {
        assertTrue(shouldEndTracking(scheduledAt.plusSeconds(61), scheduledAt, serverResponded = true, realtime = realTimeDeparture(displayValue = "08:30")))
    }

    @Test
    fun keepsTrackingAnApproachingDelayedVehiclePastTheFormerHardEnd() {
        val approaching = realTimeDeparture(displayValue = "5 min")

        assertFalse(shouldEndTracking(scheduledAt.plusSeconds(120), scheduledAt, serverResponded = true, realtime = approaching))
        assertFalse(shouldEndTracking(scheduledAt.plusSeconds(601), scheduledAt, serverResponded = true, realtime = approaching))
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
