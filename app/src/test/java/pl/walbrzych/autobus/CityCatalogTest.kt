package pl.ruby.lubiechowlabs.autobus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.ruby.lubiechowlabs.autobus.data.CityCatalog
import pl.ruby.lubiechowlabs.autobus.data.CityDataSource

class CityCatalogTest {
    @Test
    fun containsEveryConfiguredCityWithUniqueNetworkConfiguration() {
        assertEquals(54, CityCatalog.cities.size)
        assertEquals(CityCatalog.cities.size, CityCatalog.cities.map { it.id }.toSet().size)

        CityCatalog.cities.forEach { city ->
            assertTrue(city.name.isNotBlank())
            assertTrue(city.operator.isNotBlank())
            assertTrue(city.cityCode.isNotBlank())
            assertTrue(city.baseUrl.startsWith("http://") || city.baseUrl.startsWith("https://"))
            when (city.dataSource) {
                CityDataSource.MYBUS -> assertTrue(city.baseUrl.endsWith("SchedulesService.svc"))
                CityDataSource.ZDITM_GTFS -> assertTrue(city.baseUrl.endsWith("gtfs.zip"))
            }
        }
    }

    @Test
    fun includesSzczecinWithTheOfficialGtfsSource() {
        val szczecin = requireNotNull(CityCatalog.byId(60))

        assertEquals("Szczecin", szczecin.name)
        assertEquals(CityDataSource.ZDITM_GTFS, szczecin.dataSource)
        assertEquals("https://www.zditm.szczecin.pl/storage/gtfs/gtfs.zip", szczecin.baseUrl)
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

    @Test
    fun canPreferHttpsWithoutDiscardingTheLegacyHttpFallback() {
        val walbrzych = requireNotNull(CityCatalog.byId(10))

        assertEquals(
            "https://rozklad.walbrzych.eu/myBusServices/SchedulesService.svc",
            walbrzych.serviceBaseUrl(useHttps = true),
        )
        assertEquals(walbrzych.baseUrl, walbrzych.serviceBaseUrl(useHttps = false))
    }
}
