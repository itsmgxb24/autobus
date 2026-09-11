package pl.walbrzych.autobus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.walbrzych.autobus.data.CityCatalog

class CityCatalogTest {
    @Test
    fun containsEveryRecoveredMyBusCityWithUniqueNetworkConfiguration() {
        assertEquals(53, CityCatalog.cities.size)
        assertEquals(CityCatalog.cities.size, CityCatalog.cities.map { it.id }.toSet().size)

        CityCatalog.cities.forEach { city ->
            assertTrue(city.name.isNotBlank())
            assertTrue(city.operator.isNotBlank())
            assertTrue(city.cityCode.isNotBlank())
            assertTrue(city.baseUrl.startsWith("http://") || city.baseUrl.startsWith("https://"))
            assertTrue(city.baseUrl.endsWith("SchedulesService.svc"))
        }
    }

    @Test
    fun resolvesThePreviouslySupportedWalbrzychConfigurationByItsStableId() {
        val walbrzych = CityCatalog.byId(10)

        assertNotNull(walbrzych)
        assertEquals("Wałbrzych", walbrzych?.name)
        assertEquals("WALBR", walbrzych?.cityCode)
        assertEquals(
            "http://rozklad.walbrzych.eu/myBusServices/SchedulesService.svc",
            walbrzych?.baseUrl,
        )
    }
}
