package pl.ruby.lubiechowlabs.autobus

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.ruby.lubiechowlabs.autobus.data.MyBusHttpClient

class MyBusHttpClientTest {
    @Test
    fun sendsRecoveredHeadersAndCompareParameters() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setBody("<int>100</int>"))
            server.enqueue(MockResponse().setResponseCode(200).setBody("<int>1</int>"))
            val client = MyBusHttpClient(server.url("/myBusServices/SchedulesService.svc/"), "WALBR")

            assertEquals(100, client.ping())
            assertTrue(client.compareSchedule(377, 1))

            val ping = server.takeRequest()
            assertEquals("/myBusServices/SchedulesService.svc/PingService", ping.path)
            assertEquals("myBusOnline", ping.getHeader("User-Agent"))
            assertEquals("436", ping.getHeader("Age"))
            val compare = server.takeRequest()
            assertEquals("/myBusServices/SchedulesService.svc/CompareScheduleFile?nIdWersja=377&nGeneracja=1", compare.path)
            assertEquals("476", compare.getHeader("Age"))
        }
    }

    @Test
    fun usesTheSideNumberVehicleQueryAndReusesThePingToken() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setBody("<int>100</int>"))
            server.enqueue(MockResponse().setResponseCode(200).setBody("<VL><V nb=\"312\" ik=\"2874\" x=\"16.2\" y=\"50.7\" /></VL>"))
            server.enqueue(MockResponse().setResponseCode(200).setBody("<VL><V nb=\"312\" ik=\"2874\" x=\"16.2\" y=\"50.7\" /></VL>"))
            val client = MyBusHttpClient(server.url("/myBusServices/SchedulesService.svc/"), "WALBR")

            assertEquals(312, client.vehiclesBySideNumber(312).single().sideNumber)
            assertEquals(312, client.vehiclesBySideNumber(312).single().sideNumber)

            val ping = server.takeRequest()
            assertEquals("/myBusServices/SchedulesService.svc/PingService", ping.path)
            assertEquals("436", ping.getHeader("Age"))
            repeat(2) {
                val vehicles = server.takeRequest()
                assertEquals(
                    "/myBusServices/SchedulesService.svc/GetVehicles?cNbLst=312&cIdLst=&cRouteLst=&cTrackLst=&cDirLst=&cKrsLst=",
                    vehicles.path,
                )
                assertEquals("myBusOnline", vehicles.getHeader("User-Agent"))
                assertEquals("476", vehicles.getHeader("Age"))
            }
        }
    }

    @Test
    fun refreshesTheTokenOnceAfterAnAuthenticationFailure() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setBody("<int>100</int>"))
            server.enqueue(MockResponse().setResponseCode(400).setBody("expired"))
            server.enqueue(MockResponse().setResponseCode(200).setBody("<int>200</int>"))
            server.enqueue(MockResponse().setResponseCode(200).setBody("<VL><V nb=\"312\" ik=\"2874\" x=\"16.2\" y=\"50.7\" /></VL>"))
            val client = MyBusHttpClient(server.url("/myBusServices/SchedulesService.svc/"), "WALBR")

            client.vehiclesBySideNumber(312)

            assertEquals("436", server.takeRequest().getHeader("Age"))
            assertEquals("476", server.takeRequest().getHeader("Age"))
            assertEquals("436", server.takeRequest().getHeader("Age"))
            assertEquals("576", server.takeRequest().getHeader("Age"))
        }
    }
}
