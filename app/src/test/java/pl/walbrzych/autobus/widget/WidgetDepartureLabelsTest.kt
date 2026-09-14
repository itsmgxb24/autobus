package pl.walbrzych.autobus.widget

import java.time.LocalDateTime
import org.junit.Test
import pl.walbrzych.autobus.data.RealTimeDeparture
import kotlin.test.assertEquals

class WidgetDepartureLabelsTest {
    private val now = LocalDateTime.of(2026, 9, 13, 23, 45)

    @Test
    fun labelsTomorrowScheduledDepartureExplicitly() {
        assertEquals(
            "Odjazd: 00:15, jutro",
            widgetScheduledDepartureLabel(now.plusMinutes(30), now),
        )
    }

    @Test
    fun doesNotLabelSameDayScheduledDepartureAsTomorrow() {
        assertEquals(
            "Odjazd: 23:55",
            widgetScheduledDepartureLabel(now.plusMinutes(10), now),
        )
    }

    @Test
    fun replacesArrivalWordingForRealtimeEta() {
        val departure = RealTimeDeparture(
            departureId = 1,
            tripId = 2,
            line = "C",
            direction = "Plac Solidarności",
            directionCode = null,
            scheduledSeconds = 0,
            displayValue = "6 min",
            status = 0,
            vehicleNumber = null,
        )

        assertEquals("Odjazd za 6 min", widgetDepartureLabel(departure, null, now))
    }
}
