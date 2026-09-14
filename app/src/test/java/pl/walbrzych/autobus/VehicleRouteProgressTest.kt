package pl.walbrzych.autobus

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.walbrzych.autobus.data.DayType
import pl.walbrzych.autobus.data.LiveVehicle
import pl.walbrzych.autobus.data.ScheduleSnapshot
import pl.walbrzych.autobus.data.ScheduleVersion
import pl.walbrzych.autobus.data.StopData
import pl.walbrzych.autobus.data.TimetableData
import pl.walbrzych.autobus.data.vehicleRouteProgress

class VehicleRouteProgressTest {
    @Test
    fun returnsEveryOrderedStopForTheConfirmedVariant() {
        val snapshot = snapshot()

        val progress = vehicleRouteProgress(snapshot, "two", "C", vehicle())

        assertEquals("Podzamcze", progress?.directionLabel)
        assertEquals(listOf("one", "two", "three"), progress?.routeStops?.map(StopData::id))
        assertEquals("two", progress?.nextStopId)
    }

    @Test
    fun refusesToInventARouteWhenTheServerDirectionDoesNotMatch() {
        assertNull(
            vehicleRouteProgress(snapshot(), "two", "C", vehicle(directionCode = "other")),
        )
    }

    private fun snapshot(): ScheduleSnapshot {
        val route = listOf("one", "two", "three")
        val table = TimetableData(
            line = "C",
            direction = "Podzamcze",
            dayType = DayType.WORKING,
            times = listOf("12:00"),
            variant = "X",
            directionCode = "outbound",
            routeStopIds = route,
        )
        return ScheduleSnapshot(
            stops = listOf(
                StopData("one", "Pierwszy", 50.7, 16.2, listOf(table)),
                StopData("two", "Następny", 50.71, 16.21, listOf(table)),
                StopData("three", "Końcowy", 50.72, 16.22, listOf(table)),
            ),
            serviceDays = emptyList(),
            calendar = emptyMap(),
            version = ScheduleVersion(1, "2026-09-12", 1),
            lastSuccessfulUpdate = Instant.EPOCH,
        )
    }

    private fun vehicle(directionCode: String = "outbound") = LiveVehicle(
        vehicleId = 1,
        sideNumber = 312,
        line = "C",
        variant = "X",
        directionCode = directionCode,
        latitude = 50.71,
        longitude = 16.21,
        predictedLatitude = null,
        predictedLongitude = null,
        destination = "Podzamcze",
        reportedAt = "",
    )
}
