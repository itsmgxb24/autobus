package pl.ruby.lubiechowlabs.autobus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.ruby.lubiechowlabs.autobus.data.DayType
import pl.ruby.lubiechowlabs.autobus.data.ScheduleTableMapper

class ScheduleTableMapperTest {
    @Test
    fun mapsOdjazdySecondsAndDirectionRouteIntoUiValues() {
        assertEquals(listOf("00:00", "05:30", "23:59"), ScheduleTableMapper.departureTimes("0,A,19800,,86399,X"))
        assertEquals(listOf("12", "37", "108"), ScheduleTableMapper.routeStopIds("12, 37 ,,108,"))
        assertEquals(DayType.WORKING, ScheduleTableMapper.legacyDayType("RS"))
        assertEquals(DayType.VACATION, ScheduleTableMapper.legacyDayType("SO"))
        assertEquals(DayType.SUNDAY_HOLIDAY, ScheduleTableMapper.legacyDayType("NS"))
    }

    @Test
    fun ignoresInvalidDepartureTimesRatherThanDisplayingThem() {
        assertTrue(ScheduleTableMapper.departureTimes("-1,A,86400,B,wrong,C").isEmpty())
    }
}
