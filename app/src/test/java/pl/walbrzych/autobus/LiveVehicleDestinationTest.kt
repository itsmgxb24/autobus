package pl.ruby.lubiechowlabs.autobus

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.ruby.lubiechowlabs.autobus.data.LiveVehicle
import pl.ruby.lubiechowlabs.autobus.data.destinationLabel

class LiveVehicleDestinationTest {
    @Test
    fun usesTheHumanDestinationInsteadOfTheTechnicalVariantCode() {
        val vehicle = vehicle(destination = "PLAC SOLIDARNOŚCI przez Starą Kopalnię")

        assertEquals("PLAC SOLIDARNOŚCI", vehicle.destinationLabel())
    }

    @Test
    fun keepsTheDestinationWhenTheServerDoesNotIncludeViaStops() {
        assertEquals("PODZAMCZE", vehicle(destination = "PODZAMCZE").destinationLabel())
    }

    private fun vehicle(destination: String) = LiveVehicle(
        vehicleId = 405,
        sideNumber = 405,
        line = "C",
        variant = "q",
        directionCode = "P",
        latitude = 50.81,
        longitude = 16.26,
        predictedLatitude = null,
        predictedLongitude = null,
        destination = destination,
        reportedAt = "",
    )
}
