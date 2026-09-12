package pl.walbrzych.autobus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.walbrzych.autobus.data.LiveVehicle
import pl.walbrzych.autobus.data.selectVehicleForDeparture

class VehicleSelectionTest {
    @Test
    fun prefersTheMatchingActiveCourse() {
        val selected = selectVehicleForDeparture(
            vehicles = listOf(vehicle(activeCourse = 2874), vehicle(activeCourse = 2873)),
            sideNumber = 312,
            departureId = 2874,
        )

        assertEquals(2874L, selected?.activeCourseId)
    }

    @Test
    fun neverTreatsTheNextCourseAsTheActiveVehicle() {
        assertNull(
            selectVehicleForDeparture(
                vehicles = listOf(vehicle(activeCourse = 2873, nextCourse = 2874)),
                sideNumber = 312,
                departureId = 2874,
            ),
        )
    }

    @Test
    fun rejectsAmbiguousOrInvalidPositions() {
        assertNull(
            selectVehicleForDeparture(
                vehicles = listOf(vehicle(activeCourse = 2874), vehicle(activeCourse = 2874)),
                sideNumber = 312,
                departureId = 2874,
            ),
        )
        assertNull(
            selectVehicleForDeparture(
                vehicles = listOf(vehicle(activeCourse = 2874, latitude = 100.0)),
                sideNumber = 312,
                departureId = 2874,
            ),
        )
    }

    private fun vehicle(
        activeCourse: Long?,
        nextCourse: Long? = null,
        latitude: Double = 50.76,
    ) = LiveVehicle(
        vehicleId = activeCourse ?: 1,
        sideNumber = 312,
        line = "5",
        variant = "",
        directionCode = "",
        latitude = latitude,
        longitude = 16.21,
        predictedLatitude = null,
        predictedLongitude = null,
        destination = "Podzamcze",
        reportedAt = "",
        activeCourseId = activeCourse,
        nextCourseId = nextCourse,
    )
}
